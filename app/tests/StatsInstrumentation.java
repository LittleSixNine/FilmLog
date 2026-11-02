package cn.sixnine.filmlog;

import android.app.Instrumentation;
import android.os.Bundle;
import java.time.Instant;
import java.util.Arrays;

public final class StatsInstrumentation extends Instrumentation {
    private void check(boolean ok,String name){if(!ok)throw new AssertionError(name);}
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){waitForIdleSync();Bundle output=new Bundle();try{
        android.content.Context context=getTargetContext();check(context.getPackageName().equals("cn.sixnine.filmlog.media.tests"),"isolated statistics package");context.deleteDatabase("film-log.db");
        StatsStore.Summary mean=new StatsStore.Summary();check(mean.perRoll()==0,"empty mean");mean.rolls=2;mean.frames=1;check(mean.perRoll()==1,"half round up");mean.frames=5;check(mean.perRoll()==3,"nontruncated mean");mean.rolls=3;mean.frames=4;check(mean.perRoll()==1,"nearest mean");
        long now=Instant.parse("2026-10-23T12:00:00Z").toEpochMilli(),day=86400000L;
        check(StatsStore.weekIndex(now+day,now)==6&&StatsStore.weekIndex(now,now)==6,"future clamps to current week");
        check(StatsStore.weekIndex(now-7*day+1,now)==6&&StatsStore.weekIndex(now-7*day,now)==5,"exact rolling seven-day boundary");
        check(StatsStore.weekIndex(now-48*day,now)==0&&StatsStore.weekIndex(now-49*day,now)==-1,"seven weeks limit");
        check(StatsStore.weekIndex(Long.MIN_VALUE,now)==-1,"extreme timestamp cannot wrap into recent week");
        check(StatsStore.focalLengths("35 mm • 35mm • 50.5mm 85MM").equals(new java.util.LinkedHashSet<>(Arrays.asList("35mm","50.5mm"))),"original focal regex and per-shot deduplication");
        try(LogStore logs=new LogStore(context)){
            String empty=logs.addRoll("empty","Film A"," Camera A ",400,36),one=logs.addRoll("one","Film A","Camera A",400,36),many=logs.addRoll("many","Film B","Camera B",400,36);
            logs.insert(one,now,0,480,"denied","","","50mm","","","");
            logs.insert(many,now-7*day,0,-480,"denied","8","1/125","35mm","50 mm • 50mm • 85mm","","");
            logs.insert(many,now-48*day,0,0,"denied","","","","35.0 mm • 85MM","","");
            logs.insert(many,now-49*day,0,0,"denied","","","","","","");
            StatsStore.Summary stats=StatsStore.read(logs,0,true,now);
            check(stats.frames==4&&stats.rolls==3,"manual rows remain recorded even with empty parameters");
            check(stats.week[6]==1&&stats.week[5]==1&&stats.week[0]==1&&stats.weekFrames==1,"rolling weeks use absolute captured timestamp independent of display zone");
            check(stats.stockRolls.get("Film A")==2&&stats.stockRolls.get("Film B")==1,"stock ranks include empty rolls and count rolls");
            check(stats.cameraRolls.get("Camera A")==2&&stats.cameras.get("Camera A")==1&&stats.cameras.get("Camera B")==3,"camera rank roll and exposure counts separate");
            check(stats.lenses.get("50mm")==2&&stats.lenses.get("35mm")==1&&stats.lenses.get("85mm")==1&&stats.lenses.get("35.0mm")==1,"memo and lens field project into one original-style memo");
            logs.archive(empty,true);check(StatsStore.read(logs,0,false,now).stockRolls.get("Film A")==1,"archive filter preserves original local selection");
            check(StatsStore.read(logs,2025,true,now).frames==0,"annual filter unchanged");
            check(AchievementProgress.goldenHour(6)&&AchievementProgress.goldenHour(7)&&AchievementProgress.goldenHour(17)&&AchievementProgress.goldenHour(18),"original golden hour included boundaries");
            for(int hour:new int[]{0,5,8,16,19,23})check(!AchievementProgress.goldenHour(hour),"golden hour excluded "+hour);
            check(AchievementProgress.season(12)==0&&AchievementProgress.season(1)==0&&AchievementProgress.season(2)==0&&AchievementProgress.season(3)==1&&AchievementProgress.season(5)==1&&AchievementProgress.season(6)==2&&AchievementProgress.season(8)==2&&AchievementProgress.season(9)==3&&AchievementProgress.season(11)==3,"meteorological season boundaries");
            check(OriginalCatalog.blackAndWhite(context," Ilford HP5 Plus 400 ")&&OriginalCatalog.blackAndWhite(context,"CUSTOM TRI-X 400")&&OriginalCatalog.blackAndWhite(context,"xp2 custom"),"original catalog process and fallback keywords");
            check(!OriginalCatalog.blackAndWhite(context,"Kodak Portra 400")&&!OriginalCatalog.blackAndWhite(context,"unknown black and white"),"no invented black and white name rule");
            CatalogStore catalog=new CatalogStore(logs);for(int i=0;i<3;i++){String id=catalog.saveEntry("","camera","Achievement camera "+i,"35 mm","");if(i==2)catalog.archiveEntry(id,"camera",true);}catalog.saveEntry("","lens","Achievement lens","","");
            String black=logs.addRoll("black","CUSTOM HP5 400","not a catalog camera",400,36);
            String[] dates={"2026-12-01T22:00:00+08:00","2026-03-01T04:59:59+08:00","2026-06-01T05:00:00+08:00","2026-09-01T06:00:00+08:00","2026-09-01T07:59:59+08:00","2026-09-01T08:00:00+08:00","2026-09-01T17:00:00+08:00","2026-09-01T18:59:59+08:00","2026-09-01T19:00:00+08:00"};
            for(String date:dates)logs.insert(black,java.time.OffsetDateTime.parse(date).toInstant().toEpochMilli(),0,480,"denied","","","","","","");
            long changes=android.database.DatabaseUtils.longForQuery(logs.getReadableDatabase(),"SELECT total_changes()",null);AchievementProgress progress=new AchievementProgress(context,logs);check(progress.items.size()==19&&progress.groups.size()==5,"complete original catalog");
            java.util.Map<String,AchievementProgress.Item> badges=new java.util.HashMap<>();for(AchievementProgress.Item item:progress.items)badges.put(item.id,item);
            check(badges.get("beginner").progress==4&&badges.get("apprentice").progress==13&&badges.get("film_experimenter").progress==3,"local counts include archived and empty rolls for variety");
            check(badges.get("camera_collector").progress==3&&badges.get("camera_collector").reached(),"equipment catalog cameras including archived, not roll snapshots or lenses");
            check(badges.get("golden_hour_expert").progress==4&&badges.get("black_and_white_artisan").progress==9&&badges.get("seasonal_photographer").progress==4,"saved time zone golden hours, black and white exposures, cross-year seasons");
            check(badges.get("night_owl").progress==3,"22 included and 05 excluded in saved time zones");
            check(badges.get("anniversary_celebrator").progress==-1&&badges.get("photography_veteran").progress==-1&&!badges.get("anniversary_celebrator").reached()&&badges.get("master_photographer").secret(),"unknown account age never fabricated and legendary secret retained");
            check(badges.get("apprentice").reached()&&!badges.get("amateur").reached()&&badges.get("night_owl").icon==0xe330,"original thresholds and glyph IDs");
            check(android.database.DatabaseUtils.longForQuery(logs.getReadableDatabase(),"SELECT total_changes()",null)==changes,"achievement progress reads never award or write records");

        }
        String[] formats={"35 mm","120","110","70mm","65mm","4x5","5x7","8x10","Instant","unknown"};int[] capacities={36,12,24,36,36,1,1,1,10,36};
        for(int i=0;i<formats.length;i++)check(RollWizard.defaultCapacity(formats[i])==capacities[i],"original default capacity "+formats[i]);
        check(Arrays.equals(RollWizard.capacities("120"),new int[]{8,10,12,16})&&Arrays.equals(RollWizard.capacities("Instant"),new int[]{8,10,20}),"original capacity quick choices");
        output.putString("stream","PASS: seven rolling weeks/boundaries/future/overflow; roll-based stock/camera ranking; empty manual records retained; focal regex/dedup; archive/year filters; nine format capacities\n");finish(-1,output);
    }catch(Throwable error){output.putString("stream","FAIL: "+android.util.Log.getStackTraceString(error));finish(1,output);}}
}
