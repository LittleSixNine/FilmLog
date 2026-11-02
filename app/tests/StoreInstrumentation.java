package cn.sixnine.filmlog;
import android.app.Instrumentation;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.os.Bundle;

/** Runs only in a separate test package; never opens the installed app's database. */
public class StoreInstrumentation extends Instrumentation {
    private void check(boolean ok,String what){if(!ok)throw new AssertionError(what);}
    private void historicalRolls(SQLiteDatabase db){
        db.execSQL("DROP TABLE IF EXISTS film_stocks");
        db.execSQL("DROP TABLE IF EXISTS reference_photos");
        for(String table:CatalogStore.TABLES)db.execSQL("DROP TABLE IF EXISTS "+table);
        db.execSQL("ALTER TABLE rolls RENAME TO rolls_with_costs");db.execSQL(LogStore.CREATE_ROLLS);
        db.execSQL("ALTER TABLE rolls ADD COLUMN state TEXT NOT NULL DEFAULT 'active'");db.execSQL("ALTER TABLE rolls ADD COLUMN archived INTEGER NOT NULL DEFAULT 0");
        db.execSQL("ALTER TABLE rolls ADD COLUMN lab TEXT NOT NULL DEFAULT ''");db.execSQL("ALTER TABLE rolls ADD COLUMN lab_number TEXT NOT NULL DEFAULT ''");db.execSQL("ALTER TABLE rolls ADD COLUMN lab_cost TEXT NOT NULL DEFAULT ''");
        db.execSQL("INSERT INTO rolls SELECT id,name,stock,camera,iso,capacity,created_at,state,archived,lab,lab_number,lab_cost FROM rolls_with_costs");db.execSQL("DROP TABLE rolls_with_costs");
    }
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){Bundle result=new Bundle();try{
        Context c=getTargetContext();check(c.getPackageName().equals("cn.sixnine.filmlog.tests"),"isolated test package required");
        c.deleteDatabase("film-log.db");
        SQLiteDatabase old=c.openOrCreateDatabase("film-log.db",0,null);old.execSQL(LogStore.CREATE_SHOTS);
        old.execSQL("INSERT INTO shots VALUES ('old',20000,10000,-10,480,'acquired',31,121,5,20010,'gps')");
        old.execSQL("INSERT INTO shots VALUES ('pending',20000,20000,0,480,'pending',NULL,NULL,NULL,NULL,NULL)");old.setVersion(1);old.close();
        LogStore s=new LogStore(c);LogStore.Shot before=s.shot("old");
        check(s.getReadableDatabase().getVersion()==24,"schema upgrade");
        check(before.frame==1&&s.shot("pending").frame==2,"stable migration ordering");
        check(before.tapped==20000&&before.captured==10000&&before.offset==-10&&before.zone==480,"preserve times");
        check(before.lat==31&&before.lon==121&&before.accuracy==5&&before.locationTime==20010,"preserve GPS");
        check(s.roll("legacy").iso==0&&s.roll("legacy").count==2,"unknown ISO and count");
        s.recover();check(s.shot("pending").status.equals("interrupted"),"pending recovery");
        s.edit(before,-20,"8","1/125","50mm","memo","+1","yellow");
        LogStore.Shot edited=s.shot("old");check(edited.captured==0&&edited.tapped==20000&&edited.lat==31&&edited.frame==1&&edited.memo.equals("memo"),"edit original anchor and preserve GPS");
        String roll=s.addRoll("Test roll","Portra 400","Test camera",400,1);
        String id=s.insert(roll,100000,-10,480,"pending","5.6","1/250","","Street","","");
        check(s.shot(id).captured==90000&&s.shot(id).frame==1,"new exposure offset");
        String extra=s.insert(roll,100000,-10,480,"pending","","","","","","");
        check(s.roll(roll).count==2&&s.shot(extra).frame==2&&s.roll(roll).capacity==1,"nominal capacity does not truncate exposures");
        Location l=new Location("test");l.setLatitude(30);l.setLongitude(120);l.setAccuracy(8);l.setTime(100000);
        s.finish(id,"acquired",l);s.finish(id,"timeout",null);check(s.shot(id).status.equals("acquired")&&s.shot(id).lat==30,"terminal update guard");
        check(s.roll(roll).state.equals("active"),"capacity does not finish roll");
        s.changeState(roll,"active","finished","","","");s.changeState(roll,"finished","active","","","");check(s.roll(roll).count==2,"explicit resume beyond nominal");s.changeState(roll,"active","finished","","","");
        try{s.changeState(roll,"finished","developing","Lab","N1","-1");throw new AssertionError("negative cost");}catch(IllegalArgumentException expected){}
        check(s.roll(roll).state.equals("finished")&&s.roll(roll).lab.isEmpty(),"invalid metadata rollback");
        s.changeState(roll,"finished","developing","Lab","N1","14.50");
        check(s.roll(roll).labCost.equals("14.50"),"decimal cost accepted");
        try{s.changeState(roll,"finished","developing","Other","N2","1");throw new AssertionError("stale action accepted");}catch(IllegalArgumentException expected){}
        s.archive(roll,true);check(s.roll(roll).archived&&s.roll(roll).state.equals("developing"),"archive preserves state");
        try{s.changeState(roll,"developing","received","","","");throw new AssertionError("archived transition");}catch(IllegalArgumentException expected){}
        s.archive(roll,false);s.changeState(roll,"developing","finished","","","");
        check(s.roll(roll).labNumber.equals("N1"),"cancel send preserves lab info");
        s.changeState(roll,"finished","developing","Lab","N1","14.50");s.changeState(roll,"developing","received","","","");
        check(s.shot(id).lat==30&&s.shot(id).captured==90000&&s.roll(roll).state.equals("received"),"lifecycle preserves exposures");
        String partial=s.addRoll("Partial","Gold","Camera",200,36);
        s.insert(partial,200000,-10,480,"denied","","","","","","");
        s.changeState(partial,"active","finished","","","");s.changeState(partial,"finished","active","","","");
        s.insert(partial,300000,-10,480,"denied","","","","","","");
        s.editRoll(partial,"x","Gold","Camera",200,1);
        check(s.roll(partial).capacity==1&&s.roll(partial).count==2&&s.roll(partial).state.equals("active"),"nominal capacity edit preserves extra exposures");
        s.editRoll(partial,"Renamed","Gold","Camera",200,24);check(s.roll(partial).count==2&&s.nextFrame(partial)==3,"edit preserves frame numbering");
        try(android.database.Cursor exported=s.exportRows()){check(exported.getColumnIndex("lab_cost")>=0&&exported.getColumnIndex("roll_state")>=0,"export includes lifecycle");}
        s.close();s=new LogStore(c);check(s.shot(id).captured==90000&&s.shot("old").memo.equals("memo"),"reopen persistence");s.close();
        c.deleteDatabase("film-log.db");old=c.openOrCreateDatabase("film-log.db",0,null);old.execSQL(LogStore.CREATE_SHOTS);old.execSQL(LogStore.CREATE_ROLLS);for(String sql:LogStore.ADD_COLUMNS)old.execSQL(sql);
        old.execSQL("CREATE UNIQUE INDEX shot_frame ON shots(roll_id,frame_number)");
        old.execSQL("INSERT INTO rolls VALUES ('v2','V2','Portra','Camera',400,1,1)");
        old.execSQL("INSERT INTO shots(id,tapped_at,captured_at,offset_seconds,zone_offset_minutes,status,roll_id,frame_number) VALUES ('v2shot',10000,0,-10,480,'denied','v2',1)");old.setVersion(2);old.close();
        s=new LogStore(c);check(s.roll("v2").state.equals("active")&&!s.roll("v2").archived&&s.shot("v2shot").tapped==10000,"v2 full roll migration preserves exposure");s.close();
        old=c.openOrCreateDatabase("film-log.db",0,null);old.execSQL("DROP TABLE scans");old.execSQL("DROP TABLE import_jobs");historicalRolls(old);old.setVersion(3);old.close();
        s=new LogStore(c);check(s.getReadableDatabase().getVersion()==24&&s.roll("v2").state.equals("active")&&s.shot("v2shot").tapped==10000,"v3 upgrade preserves records");
        try(android.database.Cursor scans=s.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM scans",null)){scans.moveToFirst();check(scans.getInt(0)==0,"new scan table empty");}s.close();
        old=c.openOrCreateDatabase("film-log.db",0,null);old.execSQL("DROP TABLE scans");old.execSQL("CREATE TABLE scans(id TEXT PRIMARY KEY,roll_id TEXT NOT NULL,display_name TEXT NOT NULL,sha256 TEXT NOT NULL,byte_count INTEGER NOT NULL,created_at INTEGER NOT NULL,shot_id TEXT UNIQUE)");
        old.execSQL("INSERT INTO scans VALUES ('scan-old','v2','original.jpg','hash',123,1,'v2shot')");old.execSQL("DROP TABLE import_jobs");historicalRolls(old);old.setVersion(4);old.close();
        s=new LogStore(c);try(android.database.Cursor migrated=s.getReadableDatabase().rawQuery("SELECT display_name,shot_id,deleted_at,previous_shot_id FROM scans WHERE id='scan-old'",null)){check(migrated.moveToFirst()&&migrated.getString(0).equals("original.jpg")&&migrated.getString(1).equals("v2shot")&&migrated.isNull(2)&&migrated.isNull(3),"v4 active scan migration");}s.close();
        old=c.openOrCreateDatabase("film-log.db",0,null);old.execSQL("UPDATE scans SET shot_id=NULL,deleted_at=12345,previous_shot_id='v2shot' WHERE id='scan-old'");old.execSQL("DROP TABLE import_jobs");historicalRolls(old);old.setVersion(5);old.close();
        s=new LogStore(c);try(android.database.Cursor migrated=s.getReadableDatabase().rawQuery("SELECT deleted_at,previous_shot_id FROM scans WHERE id='scan-old'",null)){check(migrated.moveToFirst()&&migrated.getLong(0)==12345&&migrated.getString(1).equals("v2shot"),"v5 trash migration");}
        check(s.getReadableDatabase().getVersion()==24,"v5 to v12");try(android.database.Cursor jobs=s.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM import_jobs",null)){jobs.moveToFirst();check(jobs.getInt(0)==0,"v6 queue created");}s.close();
        old=c.openOrCreateDatabase("film-log.db",0,null);historicalRolls(old);old.execSQL("DROP TABLE import_jobs");old.execSQL("CREATE TABLE import_jobs(id TEXT PRIMARY KEY,roll_id TEXT NOT NULL,uri TEXT NOT NULL,name TEXT NOT NULL,state TEXT NOT NULL,error TEXT NOT NULL DEFAULT '',replacement_id TEXT NOT NULL DEFAULT '',created_at INTEGER NOT NULL)");old.execSQL("UPDATE rolls SET lab_cost='14.50' WHERE id='v2'");old.setVersion(6);old.close();
        s=new LogStore(c);check(s.getReadableDatabase().getVersion()==24&&s.roll("v2").labCost.equals("14.50")&&s.roll("v2").currency.isEmpty()&&s.roll("v2").filmCost.isEmpty(),"v6 cost migration preserves unknown currency");
        s.costs("v2","20.10","14.50","cny");check(s.roll("v2").currency.equals("CNY")&&s.roll("v2").filmCost.equals("20.10"),"cost save");
        try{s.costs("v2","20.1","14","JPY");throw new AssertionError("fractional yen accepted");}catch(IllegalArgumentException expected){}
        check(s.roll("v2").currency.equals("CNY"),"invalid cost atomic");s.costs("v2","1.234","0","KWD");check(s.roll("v2").filmCost.equals("1.234"),"three digit currency");s.close();
        c.deleteDatabase("film-log.db");s=new LogStore(c);String literal=s.addRoll("100%_真实","Gold","Camera",200,36),plain=s.addRoll("100ab真实","Gold","Camera",200,36);
        String note=s.insert(plain,10000,-10,480,"denied","","","50mm","街头 雨夜","","yellow");
        check(ManagementStore.search(s,"%_","all","all",false,100).equals(java.util.Arrays.asList(literal)),"literal wildcard search");
        check(ManagementStore.search(s,"街头","all","all",false,100).equals(java.util.Arrays.asList(plain)),"roll search includes exposure memo");
        check(ManagementStore.search(s,"雨夜","current","active",true,100).equals(java.util.Arrays.asList(note)),"exposure search filters");
        java.util.LinkedHashMap<String,Boolean> batch=new java.util.LinkedHashMap<>();batch.put(literal,false);batch.put(plain,false);ManagementStore.archive(s,batch,true);
        check(ManagementStore.search(s,"","current","all",false,100).isEmpty()&&ManagementStore.search(s,"","archived","all",false,100).size()==2,"batch archive filters");
        check(s.shot(note).captured==0&&s.roll(plain).count==1,"archive preserves records");
        batch.clear();batch.put(literal,true);batch.put(plain,false);try{ManagementStore.archive(s,batch,false);throw new AssertionError("stale batch accepted");}catch(IllegalArgumentException expected){}
        check(s.roll(literal).archived&&s.roll(plain).archived,"stale batch whole transaction rollback");
        batch.put(plain,true);ManagementStore.archive(s,batch,false);check(!s.roll(literal).archived&&!s.roll(plain).archived,"batch restore");s.close();
        c.deleteDatabase("film-log.db");s=new LogStore(c);String china=s.addRoll("CNY","Gold","A",200,36),usd=s.addRoll("USD","Portra","B",400,36),unknown=s.addRoll("Unknown","Gold","A",200,36);s.addRoll("Empty","Gold","A",200,36);
        String dec=s.insert(china,java.time.Instant.parse("2025-12-31T16:00:05Z").toEpochMilli(),-10,480,"denied","","","","","","");
        s.insert(china,java.time.Instant.parse("2025-12-31T16:00:15Z").toEpochMilli(),-10,480,"denied","","","","","","");
        s.insert(usd,java.time.Instant.parse("2026-01-02T12:00:00Z").toEpochMilli(),0,0,"denied","","","","","","");
        s.costs(china,"20.10","14.50","CNY");s.costs(usd,"10","","USD");s.costs(unknown,"","14.50","");
        s.getWritableDatabase().execSQL("INSERT INTO scans(id,roll_id,display_name,sha256,byte_count,created_at,shot_id) VALUES ('stats-scan',?,'sample','test',1,1,?)",new Object[]{china,dec});
        StatsStore.Summary total=StatsStore.read(s,0,true);check(total.nightFrames==2,"night counts use adjusted local times");
        check(total.rolls==4&&total.frames==3&&total.days==3&&total.longestStreak==3&&total.scanned==1,"stats totals and streak");
        check(total.budgets.get("CNY").recorded.toPlainString().equals("34.60")&&total.budgets.get("CNY").frames==2&&total.budgets.get("USD").frames==0&&total.unknownCurrencyRolls==1&&total.missingCostRolls==3,"currency and complete-cost denominator");
        check(StatsStore.read(s,2025,true).frames==1&&StatsStore.read(s,2026,true).frames==2&&StatsStore.read(s,2025,true).months[11]==1,"offset crosses local year");
        check(StatsStore.read(s,2026,true).budgets.isEmpty(),"no invented annual cost allocation");
        s.archive(china,true);check(StatsStore.read(s,0,false).frames==1&&StatsStore.read(s,0,false).rolls==3,"archive filter stats");
        s.getWritableDatabase().execSQL("UPDATE scans SET deleted_at=1,shot_id=NULL WHERE id='stats-scan'");check(StatsStore.read(s,0,true).scanned==0&&StatsStore.read(s,0,true).frames==3,"trash scan excludes scan not exposure");s.close();
        c.deleteDatabase("film-log.db");s=new LogStore(c);String blank=s.addRoll("Blank","Gold","   ",200,36);s.insert(blank,java.time.Instant.parse("2026-10-19T00:00:05Z").toEpochMilli(),-10,0,"denied","","","35mm","","","");s.insert(blank,java.time.Instant.parse("2026-10-23T12:00:00Z").toEpochMilli(),0,0,"denied","","","50mm","","","");StatsStore.Summary blankStats=StatsStore.read(s,0,true,java.time.LocalDate.of(2026,10,23));check(blankStats.frames==2&&blankStats.cameras.size()==1&&blankStats.cameras.get("未设置相机")==2,"blank camera counted with visible label, not an empty ranking");check(blankStats.weekFrames==2&&blankStats.week[6]==2&&blankStats.lenses.size()==2&&blankStats.lenses.get("50mm")==1,"rolling week includes both dates across Monday and lens rows");check(StatsStore.read(s,2025,true,java.time.LocalDate.of(2026,10,23)).weekFrames==0,"week respects selected year");s.close();
        c.deleteDatabase("film-log.db");s=new LogStore(c);String homeRoll=s.addRoll("Home","Gold","A",200,3);s.insert(homeRoll,1000,0,0,"denied","","","","","","");HomeCards.Summary home=HomeCards.read(s);check(home.continueRoll.equals(homeRoll)&&home.nearlyFinished.size()==1,"home continue and last frames");s.changeState(homeRoll,"active","finished","","","");check(HomeCards.read(s).readyForLab==1&&HomeCards.read(s).continueRoll.isEmpty(),"finished home card");s.changeState(homeRoll,"finished","developing","Test Lab","","");s.changeState(homeRoll,"developing","received","","","");check(HomeCards.read(s).missingScans==1,"received unassociated scan card");s.archive(homeRoll,true);check(HomeCards.read(s).missingScans==0,"archived omitted from home cards");String direct=s.addRoll("Direct partial","Film","Camera",400,36);try{s.changeState(direct,"active","developing","","","");throw new AssertionError("empty active roll sent");}catch(IllegalArgumentException expected){}check(s.roll(direct).state.equals("active"),"empty send rolls back");s.insert(direct,100000,0,0,"denied","8","1/125","","","","");try{s.changeState(direct,"active","developing","  ","","");throw new AssertionError("missing lab accepted");}catch(IllegalArgumentException expected){}check(s.roll(direct).state.equals("active"),"missing lab preserves state");s.changeState(direct,"active","developing","Lab","Direct","1.25");check(s.roll(direct).count==1&&s.roll(direct).state.equals("developing")&&s.roll(direct).labNumber.equals("Direct"),"partially shot roll goes straight to lab");try{s.changeState(direct,"active","developing","Other","","");throw new AssertionError("stale direct send");}catch(IllegalArgumentException expected){}String emptyFinished=s.addRoll("Empty finished","Film","",400,36);s.changeState(emptyFinished,"active","finished","","","");try{s.changeState(emptyFinished,"finished","developing","","","");throw new AssertionError("empty legacy finished sent");}catch(IllegalArgumentException expected){}s.close();
        result.putString("stream","PASS: partial-roll direct send, empty-roll rejection, stale-action rollback; home cards and night milestones; v1/v2/v3/v4/v5/v6 migrations; lifecycle, archive/restore, cancel send, stale-state guard, costs, capacity edits and export; Android SQLite migration, original time/GPS preservation, edit anchor, advisory capacity, location guard, reopen persistence; literal search, exposure filters, batch archive/restore and stale batch rollback; cross-year offset statistics, archive filters, separate currencies and complete-cost denominator\n");finish(-1,result);
    }catch(Throwable t){result.putString("stream","FAIL: "+t);finish(0,result);}}
}
