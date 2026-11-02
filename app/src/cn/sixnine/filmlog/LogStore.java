package cn.sixnine.filmlog;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import android.location.Location;
import java.util.*;

final class LogStore extends SQLiteOpenHelper {
    static final String CREATE_SHOTS="CREATE TABLE shots (id TEXT PRIMARY KEY,tapped_at INTEGER NOT NULL,captured_at INTEGER NOT NULL,offset_seconds INTEGER NOT NULL,zone_offset_minutes INTEGER NOT NULL,status TEXT NOT NULL,latitude REAL,longitude REAL,accuracy_m REAL,location_time INTEGER,provider TEXT)";
    static final String CREATE_ROLLS="CREATE TABLE rolls (id TEXT PRIMARY KEY,name TEXT NOT NULL,stock TEXT NOT NULL,camera TEXT NOT NULL,iso INTEGER NOT NULL,capacity INTEGER NOT NULL,created_at INTEGER NOT NULL)";
    static final String[] ADD_COLUMNS={"ALTER TABLE shots ADD COLUMN roll_id TEXT","ALTER TABLE shots ADD COLUMN frame_number INTEGER","ALTER TABLE shots ADD COLUMN aperture TEXT NOT NULL DEFAULT ''","ALTER TABLE shots ADD COLUMN shutter TEXT NOT NULL DEFAULT ''","ALTER TABLE shots ADD COLUMN lens TEXT NOT NULL DEFAULT ''","ALTER TABLE shots ADD COLUMN memo TEXT NOT NULL DEFAULT ''","ALTER TABLE shots ADD COLUMN ev TEXT NOT NULL DEFAULT ''","ALTER TABLE shots ADD COLUMN filters TEXT NOT NULL DEFAULT ''"};
    static class Roll {String id,name,stock,camera,state,lab,labNumber,labCost,filmCost,currency,sentDate,receivedDate,labNotes;boolean archived;int iso,capacity,count;long created;}
    static class Shot {String id,rollId,status,provider,aperture,shutter,lens,memo,ev,filters;int frame,offset,zone;long tapped,captured,locationTime;Double lat,lon,accuracy;boolean timeEdited;}
    final Context context;
    LogStore(Context c) {super(c,"film-log.db",null,24);context=c;}
    @Override public void onCreate(SQLiteDatabase db){db.execSQL(CREATE_SHOTS);upgradeTo2(db);upgradeTo3(db);upgradeTo4(db);upgradeTo5(db);upgradeTo6(db);upgradeTo7(db);upgradeTo8(db);upgradeTo9(db);upgradeTo10(db);upgradeTo11(db);upgradeTo12(db);upgradeTo13(db);upgradeTo14(db);upgradeTo15(db);upgradeTo16(db);upgradeTo17(db);upgradeTo18(db);upgradeTo19(db);upgradeTo20(db);upgradeTo21(db);upgradeTo22(db);upgradeTo23(db);upgradeTo24(db);}
    @Override public void onUpgrade(SQLiteDatabase db,int from,int to){if(from<1||from>23)throw new IllegalStateException("Unsupported upgrade");if(from<2)upgradeTo2(db);if(from<3)upgradeTo3(db);if(from<4)upgradeTo4(db);if(from<5)upgradeTo5(db);if(from<6)upgradeTo6(db);if(from<7)upgradeTo7(db);if(from<8)upgradeTo8(db);if(from<9)upgradeTo9(db);if(from<10)upgradeTo10(db);if(from<11)upgradeTo11(db);if(from<12)upgradeTo12(db);if(from<13)upgradeTo13(db);if(from<14)upgradeTo14(db);if(from<15)upgradeTo15(db);if(from<16)upgradeTo16(db);if(from<17)upgradeTo17(db);if(from<18)upgradeTo18(db);if(from<19)upgradeTo19(db);upgradeTo20(db);upgradeTo21(db);upgradeTo22(db);upgradeTo23(db);upgradeTo24(db);}
    private void upgradeTo2(SQLiteDatabase db){
        db.execSQL(CREATE_ROLLS);for(String sql:ADD_COLUMNS)db.execSQL(sql);
        long count;try(Cursor c=db.rawQuery("SELECT COUNT(*) FROM shots",null)){c.moveToFirst();count=c.getLong(0);}
        if(count>0){
            db.execSQL("INSERT INTO rolls VALUES ('legacy','早期记录','早期记录','未设置',0,?,?)",new Object[]{Math.max(36,count),System.currentTimeMillis()});
            int frame=1;try(Cursor c=db.rawQuery("SELECT id FROM shots ORDER BY tapped_at,rowid",null)){
                while(c.moveToNext())db.execSQL("UPDATE shots SET roll_id='legacy',frame_number=? WHERE id=?",new Object[]{frame++,c.getString(0)});
            }
        }
        db.execSQL("CREATE UNIQUE INDEX shot_frame ON shots(roll_id,frame_number)");
    }
    private void upgradeTo3(SQLiteDatabase db){
        db.execSQL("ALTER TABLE rolls ADD COLUMN state TEXT NOT NULL DEFAULT 'active'");
        db.execSQL("ALTER TABLE rolls ADD COLUMN archived INTEGER NOT NULL DEFAULT 0");
        db.execSQL("ALTER TABLE rolls ADD COLUMN lab TEXT NOT NULL DEFAULT ''");
        db.execSQL("ALTER TABLE rolls ADD COLUMN lab_number TEXT NOT NULL DEFAULT ''");
        db.execSQL("ALTER TABLE rolls ADD COLUMN lab_cost TEXT NOT NULL DEFAULT ''");
    }
    private void upgradeTo4(SQLiteDatabase db){
        db.execSQL("CREATE TABLE scans (id TEXT PRIMARY KEY,roll_id TEXT NOT NULL,display_name TEXT NOT NULL,sha256 TEXT NOT NULL,byte_count INTEGER NOT NULL,created_at INTEGER NOT NULL,shot_id TEXT UNIQUE)");
        db.execSQL("CREATE INDEX scan_roll ON scans(roll_id)");
    }
    private void upgradeTo5(SQLiteDatabase db){
        db.execSQL("ALTER TABLE scans ADD COLUMN deleted_at INTEGER");
        db.execSQL("ALTER TABLE scans ADD COLUMN previous_shot_id TEXT");
    }
    private void upgradeTo6(SQLiteDatabase db){
        db.execSQL("CREATE TABLE import_jobs(id TEXT PRIMARY KEY,roll_id TEXT NOT NULL,uri TEXT NOT NULL,name TEXT NOT NULL,state TEXT NOT NULL,error TEXT NOT NULL DEFAULT '',replacement_id TEXT NOT NULL DEFAULT '',created_at INTEGER NOT NULL)");
    }
    private void upgradeTo7(SQLiteDatabase db){db.execSQL("ALTER TABLE rolls ADD COLUMN film_cost TEXT NOT NULL DEFAULT ''");db.execSQL("ALTER TABLE rolls ADD COLUMN cost_currency TEXT NOT NULL DEFAULT ''");}
    private void upgradeTo8(SQLiteDatabase db){db.execSQL("ALTER TABLE import_jobs ADD COLUMN target_shot_id TEXT NOT NULL DEFAULT ''");}
    private void upgradeTo9(SQLiteDatabase db){CatalogStore.createTables(db);}
    private void upgradeTo24(SQLiteDatabase db){try(Cursor c=db.rawQuery("PRAGMA table_info(artwork_links)",null)){while(c.moveToNext())if("cover_image".equals(c.getString(c.getColumnIndexOrThrow("name"))))return;}db.execSQL("ALTER TABLE artwork_links ADD COLUMN cover_image TEXT NOT NULL DEFAULT ''");}
    private void upgradeTo23(SQLiteDatabase db){try(Cursor c=db.rawQuery("PRAGMA table_info(roll_details)",null)){while(c.moveToNext())if("half_frame".equals(c.getString(c.getColumnIndexOrThrow("name"))))return;}db.execSQL("ALTER TABLE roll_details ADD COLUMN half_frame INTEGER NOT NULL DEFAULT 0 CHECK(half_frame IN (0,1))");}
    private void upgradeTo22(SQLiteDatabase db){try(Cursor c=db.rawQuery("PRAGMA table_info(catalog_entries)",null)){while(c.moveToNext())if("camera_show_brand".equals(c.getString(c.getColumnIndexOrThrow("name"))))return;}db.execSQL("ALTER TABLE catalog_entries ADD COLUMN camera_show_brand INTEGER NOT NULL DEFAULT 0");}
    private void upgradeTo21(SQLiteDatabase db){try(Cursor c=db.rawQuery("PRAGMA table_info(shots)",null)){while(c.moveToNext())if("time_edited".equals(c.getString(c.getColumnIndexOrThrow("name"))))return;}db.execSQL("ALTER TABLE shots ADD COLUMN time_edited INTEGER NOT NULL DEFAULT 0");}
    private void upgradeTo20(SQLiteDatabase db){Set<String> columns=new HashSet<>();try(Cursor c=db.rawQuery("PRAGMA table_info(rolls)",null)){while(c.moveToNext())columns.add(c.getString(c.getColumnIndexOrThrow("name")));}for(String field:new String[]{"lab_sent_date","lab_received_date","lab_notes"})if(!columns.contains(field))db.execSQL("ALTER TABLE rolls ADD COLUMN "+field+" TEXT NOT NULL DEFAULT ''");}
    private void upgradeTo19(SQLiteDatabase db){java.util.Set<String> columns=new java.util.HashSet<>();try(Cursor c=db.rawQuery("PRAGMA table_info(catalog_entries)",null)){while(c.moveToNext())columns.add(c.getString(c.getColumnIndexOrThrow("name")));}for(String field:new String[]{"camera_model","camera_logo"})if(!columns.contains(field))db.execSQL("ALTER TABLE catalog_entries ADD COLUMN "+field+" TEXT NOT NULL DEFAULT ''");}
    private void upgradeTo18(SQLiteDatabase db){db.execSQL("ALTER TABLE catalog_entries ADD COLUMN project_started_at INTEGER CHECK(project_started_at IS NULL OR project_started_at BETWEEN -8640000000000000 AND 8640000000000000)");}
    private void upgradeTo17(SQLiteDatabase db){db.execSQL("ALTER TABLE catalog_entries ADD COLUMN project_archived_at INTEGER CHECK(project_archived_at IS NULL OR project_archived_at BETWEEN -8640000000000000 AND 8640000000000000)");}
    private void upgradeTo16(SQLiteDatabase db){db.execSQL("ALTER TABLE catalog_entries ADD COLUMN project_completed_at INTEGER CHECK(project_completed_at IS NULL OR project_completed_at BETWEEN -8640000000000000 AND 8640000000000000)");}
    private void upgradeTo15(SQLiteDatabase db){
        java.util.Set<String> columns=new java.util.HashSet<>();try(Cursor c=db.rawQuery("PRAGMA table_info(catalog_entries)",null)){while(c.moveToNext())columns.add(c.getString(c.getColumnIndexOrThrow("name")));}
        if(!columns.contains("project_color"))db.execSQL("ALTER TABLE catalog_entries ADD COLUMN project_color TEXT NOT NULL DEFAULT ''");
        if(!columns.contains("project_completed"))db.execSQL("ALTER TABLE catalog_entries ADD COLUMN project_completed INTEGER NOT NULL DEFAULT 0 CHECK(project_completed IN(0,1))");
    }
    private void upgradeTo14(SQLiteDatabase db){ArtworkLinks.create(db);PresetStore.create(db);}
    private void upgradeTo13(SQLiteDatabase db){db.execSQL("ALTER TABLE roll_details ADD COLUMN push_pull_stops INTEGER NOT NULL DEFAULT 0 CHECK(push_pull_stops IN(-2,-1,0,1,2,3))");}
    private void upgradeTo12(SQLiteDatabase db){db.execSQL("ALTER TABLE catalog_entries ADD COLUMN favorite INTEGER NOT NULL DEFAULT 0 CHECK(favorite IN (0,1))");db.execSQL("ALTER TABLE catalog_entries ADD COLUMN condition TEXT NOT NULL DEFAULT ''");}
    private void upgradeTo11(SQLiteDatabase db){db.execSQL("CREATE TABLE film_stocks(id TEXT PRIMARY KEY,brand TEXT NOT NULL,name TEXT NOT NULL,iso INTEGER NOT NULL,process TEXT NOT NULL DEFAULT '',reciprocity_p TEXT NOT NULL DEFAULT '',formats TEXT NOT NULL DEFAULT '',archived INTEGER NOT NULL DEFAULT 0,created_at INTEGER NOT NULL)");}
    private void upgradeTo10(SQLiteDatabase db){db.execSQL("CREATE TABLE reference_photos(id TEXT PRIMARY KEY,shot_id TEXT NOT NULL UNIQUE,sha256 TEXT NOT NULL,byte_count INTEGER NOT NULL CHECK(byte_count>0),created_at INTEGER NOT NULL)");}
    static void validateCost(String amount,String currency){
        int decimals=3;if(!currency.isEmpty()){if(!currency.matches("[A-Z]{3}"))throw new IllegalArgumentException("币种请填 CNY、USD、JPY 等三位代码");try{decimals=Math.max(0,java.util.Currency.getInstance(currency).getDefaultFractionDigits());}catch(IllegalArgumentException error){throw new IllegalArgumentException("无法识别币种");}}
        if(amount.isEmpty())return;if(!amount.matches("[0-9]{1,9}(\\.[0-9]{1,3})?")||new java.math.BigDecimal(amount).scale()>decimals)throw new IllegalArgumentException("金额必须非负，当前币种最多 "+decimals+" 位小数");
    }
    void costs(String id,String film,String lab,String currency){currency=currency.trim().toUpperCase(Locale.ROOT);film=film.trim();lab=lab.trim();validateCost(film,currency);validateCost(lab,currency);roll(id);ContentValues values=new ContentValues();values.put("film_cost",film);values.put("lab_cost",lab);values.put("cost_currency",currency);if(getWritableDatabase().update("rolls",values,"id=?",new String[]{id})!=1)throw new IllegalStateException("Roll missing");}
    void editRoll(String id,String name,String stock,String camera,int iso,int capacity){
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{
            Roll r=roll(id);if(stock.trim().isEmpty()||capacity<1||capacity>999||iso<0||iso>102400||(iso==0&&r.iso!=0))throw new IllegalArgumentException("胶片名不能为空，标称张数须为1至999，ISO必须有效");
            ContentValues v=new ContentValues();v.put("name",name);v.put("stock",stock);v.put("camera",camera);v.put("iso",iso);v.put("capacity",capacity);
            db.update("rolls",v,"id=?",new String[]{id});if(!r.stock.equals(stock)||r.iso!=iso)ArtworkLinks.put(db,"roll",id,"");db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    void changeState(String id,String expected,String target,String lab,String number,String cost){
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{
            Roll r=roll(id);boolean allowed=(expected.equals("active")&&(target.equals("finished")||target.equals("developing")))||(expected.equals("finished")&&(target.equals("active")||target.equals("developing")))||(expected.equals("developing")&&(target.equals("finished")||target.equals("received")));
            if(r.archived||!r.state.equals(expected)||!allowed)throw new IllegalArgumentException("当前胶卷状态不允许此操作");
            ContentValues v=new ContentValues();v.put("state",target);
            if(target.equals("developing")){
                if(r.count==0)throw new IllegalArgumentException("无法将空胶卷送去冲洗");
                if(lab.trim().isEmpty())throw new IllegalArgumentException("请选择冲洗店");
                validateCost(cost,r.currency);
                v.put("lab",lab.trim());v.put("lab_number",number.trim());v.put("lab_cost",cost);
            }
            db.update("rolls",v,"id=?",new String[]{id});db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    static void labDates(String sent,String received){for(String date:new String[]{sent,received})if(!date.isEmpty()){if(!date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))throw new IllegalArgumentException("日期请按 YYYY-MM-DD 填写");CatalogStore.date(date);}if(!sent.isEmpty()&&!received.isEmpty()&&received.compareTo(sent)<0)throw new IllegalArgumentException("取回日期不能早于送洗日期");}
    void saveLab(String id,String expected,String action,String lab,String number,String sent,String received,String notes,String cost,String currency){
        lab=lab.trim();number=number.trim();notes=notes.trim();cost=cost.trim();currency=currency.trim().toUpperCase(Locale.ROOT);labDates(sent,received);validateCost(cost,currency);
        if(action.equals("send")&&lab.isEmpty()||lab.length()>500||number.length()>500||notes.length()>4000)throw new IllegalArgumentException("请核对冲洗店、卷号和备注长度");
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{Roll r=roll(id);boolean send=action.equals("send"),take=action.equals("receive"),edit=action.equals("edit");
            if(r.archived||!r.state.equals(expected)||!(send&&r.state.equals("finished")&&r.count>0||take&&r.state.equals("developing")||edit&&(r.state.equals("developing")||r.state.equals("received"))))throw new IllegalArgumentException("当前胶卷状态已变化，请重新打开");
            if(send&&sent.isEmpty()||take&&received.isEmpty())throw new IllegalArgumentException("请选择日期");
            if(!r.filmCost.isEmpty()&&!currency.equals(r.currency))throw new IllegalArgumentException("胶片费用已记入原币种；请先在胶卷设置的费用信息中统一修改币种");validateCost(r.filmCost,currency);
            ContentValues v=new ContentValues();v.put("state",send?"developing":take?"received":r.state);v.put("lab",lab);v.put("lab_number",number);v.put("lab_sent_date",sent);v.put("lab_received_date",received);v.put("lab_notes",notes);v.put("lab_cost",cost);v.put("cost_currency",currency);db.update("rolls",v,"id=?",new String[]{id});db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    /** Delete the owning rows atomically, then remove only their private photo copies. */
    boolean deleteRoll(String id,int expectedCount){
        ArrayList<String> photos=new ArrayList<>();Set<String> uris=new HashSet<>();SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{if(roll(id).count!=expectedCount)throw new IllegalArgumentException("曝光数量已变化，请重新确认删除");
            try(Cursor c=db.rawQuery("SELECT id FROM scans WHERE roll_id=? UNION SELECT p.id FROM reference_photos p JOIN shots s ON p.shot_id=s.id WHERE s.roll_id=?",new String[]{id,id})){while(c.moveToNext())photos.add(c.getString(0));}
            try(Cursor c=db.rawQuery("SELECT uri FROM import_jobs WHERE roll_id=?",new String[]{id})){while(c.moveToNext())uris.add(c.getString(0));}
            db.delete("reference_photos","shot_id IN (SELECT id FROM shots WHERE roll_id=?)",new String[]{id});db.delete("scans","roll_id=?",new String[]{id});db.delete("import_jobs","roll_id=?",new String[]{id});db.delete("shots","roll_id=?",new String[]{id});db.delete("roll_details","roll_id=?",new String[]{id});db.delete("artwork_links","owner_kind='roll' AND owner_id=?",new String[]{id});
            ContentValues detached=new ContentValues();detached.putNull("roll_id");detached.put("reason","roll_deleted");db.update("stock_history",detached,"roll_id=?",new String[]{id});if(db.delete("rolls","id=?",new String[]{id})!=1)throw new IllegalStateException("胶卷已变化");db.setTransactionSuccessful();
        }finally{db.endTransaction();}
        boolean cleaned=true;for(String photo:photos){if(!photo.matches("[0-9a-fA-F-]{36}")){cleaned=false;continue;}try{UUID.fromString(photo);}catch(IllegalArgumentException invalid){cleaned=false;continue;}java.io.File file=new java.io.File(new java.io.File(context.getFilesDir(),"scans"),photo+".jpg");if(file.exists()&&!file.delete())cleaned=false;}
        for(String uri:uris)ImportQueue.releaseIfFinished(context,db,uri);try{ExposureReminders.remove(context,id);ExposureWidget.updateAll(context);}catch(RuntimeException error){android.util.Log.w("FilmLog","Deleted roll auxiliary cleanup deferred",error);}return cleaned;
    }
    boolean deleteShot(String id,String expectedRoll,int expectedFrame){ArrayList<String> references=new ArrayList<>();SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{Shot shot=shot(id);if(!shot.rollId.equals(expectedRoll)||shot.frame!=expectedFrame)throw new IllegalArgumentException("曝光已变化，请重新打开后确认");try(Cursor c=db.rawQuery("SELECT id FROM reference_photos WHERE shot_id=?",new String[]{id})){while(c.moveToNext())references.add(c.getString(0));}ContentValues empty=new ContentValues();empty.putNull("shot_id");db.update("scans",empty,"shot_id=?",new String[]{id});empty.clear();empty.putNull("previous_shot_id");db.update("scans",empty,"previous_shot_id=?",new String[]{id});db.delete("reference_photos","shot_id=?",new String[]{id});if(db.delete("shots","id=? AND roll_id=?",new String[]{id,expectedRoll})!=1)throw new IllegalStateException("曝光已不存在");db.setTransactionSuccessful();}finally{db.endTransaction();}boolean clean=true;for(String token:references)try{UUID.fromString(token);java.io.File copy=new java.io.File(new java.io.File(context.getFilesDir(),"scans"),token+".jpg");if(copy.exists()&&!copy.delete())clean=false;}catch(Exception e){clean=false;}try{ExposureWidget.updateAll(context);}catch(RuntimeException e){android.util.Log.w("FilmLog","Deleted exposure widget refresh deferred",e);}return clean;}
    void archive(String id,boolean archived){roll(id);if(!archived){CatalogStore.Detail d=new CatalogStore(this).detail(id);if(d!=null&&d.returned)throw new IllegalArgumentException("这卷已撤销并返还库存，请从库存重新装卷");}ContentValues v=new ContentValues();v.put("archived",archived?1:0);getWritableDatabase().update("rolls",v,"id=?",new String[]{id});}
    void recover(){getWritableDatabase().execSQL("UPDATE shots SET status='interrupted' WHERE status='pending'");}
    String addRoll(String name,String stock,String camera,int iso,int capacity){
        String id=UUID.randomUUID().toString();ContentValues v=new ContentValues();v.put("id",id);v.put("name",name);v.put("stock",stock);v.put("camera",camera);v.put("iso",iso);v.put("capacity",capacity);v.put("created_at",System.currentTimeMillis());getWritableDatabase().insertOrThrow("rolls",null,v);return id;
    }
    ArrayList<Roll> rolls(){ArrayList<Roll> result=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT r.*,COUNT(s.id) AS shot_count FROM rolls r LEFT JOIN shots s ON s.roll_id=r.id GROUP BY r.id ORDER BY r.created_at DESC",null)){while(c.moveToNext()){Roll r=new Roll();r.created=lng(c,"created_at");r.id=str(c,"id");r.name=str(c,"name");r.stock=str(c,"stock");r.camera=str(c,"camera");r.iso=num(c,"iso");r.capacity=num(c,"capacity");r.count=num(c,"shot_count");r.state=str(c,"state");r.archived=num(c,"archived")!=0;r.lab=str(c,"lab");r.labNumber=str(c,"lab_number");r.labCost=str(c,"lab_cost");r.filmCost=str(c,"film_cost");r.currency=str(c,"cost_currency");r.sentDate=str(c,"lab_sent_date");r.receivedDate=str(c,"lab_received_date");r.labNotes=str(c,"lab_notes");result.add(r);}}return result;}
    Roll roll(String id){try(Cursor c=getReadableDatabase().rawQuery("SELECT r.*,(SELECT COUNT(s.id) FROM shots s WHERE s.roll_id=r.id) AS shot_count FROM rolls r WHERE r.id=?",new String[]{id})){if(c.moveToFirst()){Roll r=new Roll();r.created=lng(c,"created_at");r.id=str(c,"id");r.name=str(c,"name");r.stock=str(c,"stock");r.camera=str(c,"camera");r.iso=num(c,"iso");r.capacity=num(c,"capacity");r.count=num(c,"shot_count");r.state=str(c,"state");r.archived=num(c,"archived")!=0;r.lab=str(c,"lab");r.labNumber=str(c,"lab_number");r.labCost=str(c,"lab_cost");r.filmCost=str(c,"film_cost");r.currency=str(c,"cost_currency");r.sentDate=str(c,"lab_sent_date");r.receivedDate=str(c,"lab_received_date");r.labNotes=str(c,"lab_notes");return r;}}throw new IllegalStateException("Roll missing");}
    int nextFrame(String rollId){try(Cursor c=getReadableDatabase().rawQuery("SELECT COALESCE(MAX(frame_number),0)+1 FROM shots WHERE roll_id=?",new String[]{rollId})){c.moveToFirst();return c.getInt(0);}}
    String insert(String rollId,long tap,int delta,int zone,String status,String aperture,String shutter,String lens,String memo,String ev,String filters){return insertWithId(UUID.randomUUID().toString(),rollId,tap,delta,zone,status,aperture,shutter,lens,memo,ev,filters);}
    String insertWithId(String id,String rollId,long tap,int delta,int zone,String status,String aperture,String shutter,String lens,String memo,String ev,String filters){
        return insertWithId(id,rollId,tap,delta,zone,status,aperture,shutter,lens,memo,ev,filters,null);
    }
    static boolean recentFix(Location fix){
        return fix!=null&&fix.hasAccuracy()&&fix.getTime()>0&&fix.getElapsedRealtimeNanos()>0
            &&RecordRules.validFix(android.os.SystemClock.elapsedRealtime(),fix.getElapsedRealtimeNanos()/1000000,fix.getLatitude(),fix.getLongitude(),fix.getAccuracy());
    }
    static float fixSpeed(Location fix){
        if(!fix.hasSpeed())return -1;
        float speed=fix.getSpeed();if(!Float.isFinite(speed)||speed<0)return Float.POSITIVE_INFINITY;
        if(fix.hasSpeedAccuracy()){float error=fix.getSpeedAccuracyMetersPerSecond();if(!Float.isFinite(error)||error<0)return Float.POSITIVE_INFINITY;speed+=error;}
        return speed;
    }
    static boolean usableFix(Location fix){
        return recentFix(fix)&&RecordRules.usableFix(android.os.SystemClock.elapsedRealtime(),fix.getElapsedRealtimeNanos()/1000000,fix.getLatitude(),fix.getLongitude(),fix.getAccuracy(),fixSpeed(fix));
    }
    static void requireFix(Location fix){
        if(!usableFix(fix))throw new IllegalArgumentException("尚未取得有效定位，曝光未保存");
    }
    String recordLocated(String id,String rollId,long tap,int delta,int zone,String aperture,String shutter,String lens,String memo,String ev,String filters,Location fix){
        requireFix(fix);return insertWithId(id,rollId,tap,delta,zone,"acquired",aperture,shutter,lens,memo,ev,filters,new Location(fix));
    }
    private String insertWithId(String id,String rollId,long tap,int delta,int zone,String status,String aperture,String shutter,String lens,String memo,String ev,String filters,Location fix){
        if(!UUID.fromString(id).toString().equals(id))throw new IllegalArgumentException("曝光标识无效");
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try{
            try(Cursor existing=db.query("shots",new String[]{"id"},"id=?",new String[]{id},null,null,null)){if(existing.moveToFirst()){Shot old=shot(id);if(!old.rollId.equals(rollId)||old.tapped!=tap||old.offset!=delta||old.zone!=zone||!old.aperture.equals(aperture)||!old.shutter.equals(shutter)||!old.lens.equals(lens)||!old.memo.equals(memo)||!old.ev.equals(ev)||!old.filters.equals(filters))throw new IllegalArgumentException("曝光请求已保存，参数不一致");if(fix!=null&&old.lat==null)throw new IllegalArgumentException("已有曝光缺少定位，不能用当前位置覆盖");db.setTransactionSuccessful();return id;}}
            Roll r=roll(rollId);if(!RecordRules.canRecord(r.archived,r.state))throw new IllegalArgumentException("请先恢复到拍摄中再记录曝光");int frame=nextFrame(rollId);if(!RecordRules.validFrame(frame))throw new IllegalArgumentException("帧号超出可记录范围");
            ContentValues v=parameters(aperture,shutter,lens,memo,ev,filters);v.put("id",id);v.put("roll_id",rollId);v.put("frame_number",frame);v.put("tapped_at",tap);v.put("captured_at",RecordRules.captureTime(tap,delta));v.put("offset_seconds",delta);v.put("zone_offset_minutes",zone);v.put("status",status);
            if(fix!=null){v.put("latitude",fix.getLatitude());v.put("longitude",fix.getLongitude());v.put("accuracy_m",fix.getAccuracy());v.put("location_time",fix.getTime());v.put("provider",fix.getProvider());}
            db.insertOrThrow("shots",null,v);db.setTransactionSuccessful();return id;
        }finally{db.endTransaction();}
    }
    void edit(Shot shot,int delta,String aperture,String shutter,String lens,String memo,String ev,String filters){
        ContentValues v=parameters(aperture,shutter,lens,memo,ev,filters);v.put("offset_seconds",delta);v.put("captured_at",shot.timeEdited?shot.captured:RecordRules.captureTime(shot.tapped,delta));
        if(getWritableDatabase().update("shots",v,"id=?",new String[]{shot.id})!=1)throw new IllegalStateException("Shot missing");
    }
    void editCaptured(Shot shot,long captured,String aperture,String shutter,String lens,String memo,String ev,String filters){
        RecordRules.editedTime(captured);Shot current=shot(shot.id);
        ContentValues values=parameters(aperture,shutter,lens,memo,ev,filters);values.put("captured_at",captured);values.put("time_edited",captured==RecordRules.captureTime(current.tapped,current.offset)?0:1);
        if(getWritableDatabase().update("shots",values,"id=?",new String[]{current.id})!=1)throw new IllegalStateException("曝光已不存在");
    }
    private ContentValues parameters(String aperture,String shutter,String lens,String memo,String ev,String filters){ContentValues v=new ContentValues();v.put("aperture",aperture);v.put("shutter",shutter);v.put("lens",lens);v.put("memo",memo);v.put("ev",ev);v.put("filters",filters);return v;}
    void finish(String id,String status,Location fix){ContentValues v=new ContentValues();v.put("status",status);if(fix!=null){v.put("latitude",fix.getLatitude());v.put("longitude",fix.getLongitude());v.put("accuracy_m",fix.getAccuracy());v.put("location_time",fix.getTime());v.put("provider",fix.getProvider());}getWritableDatabase().update("shots",v,"id=? AND status='pending'",new String[]{id});}
    ArrayList<Shot> shots(String rollId){ArrayList<Shot> a=new ArrayList<>();try(Cursor c=getReadableDatabase().query("shots",null,"roll_id=?",new String[]{rollId},null,null,"frame_number DESC")){while(c.moveToNext())a.add(readShot(c));}return a;}
    Shot shot(String id){try(Cursor c=getReadableDatabase().query("shots",null,"id=?",new String[]{id},null,null,null)){if(!c.moveToFirst())throw new IllegalStateException("Shot missing");return readShot(c);}}
    ArrayList<Shot> recent(){ArrayList<Shot> a=new ArrayList<>();try(Cursor c=getReadableDatabase().query("shots",null,"aperture<>'' OR shutter<>''",null,null,null,"tapped_at DESC","6")){while(c.moveToNext())a.add(readShot(c));}return a;}
    Cursor exportRows(){return getReadableDatabase().rawQuery("SELECT s.*,r.name AS roll_name,r.stock,r.camera,r.iso,r.state AS roll_state,r.archived AS roll_archived,r.lab,r.lab_number,r.lab_cost,r.capacity AS roll_capacity,r.film_cost,r.cost_currency,r.lab_sent_date,r.lab_received_date,r.lab_notes FROM shots s LEFT JOIN rolls r ON r.id=s.roll_id ORDER BY s.tapped_at DESC,s.rowid DESC",null);}
    private Shot readShot(Cursor c){Shot s=new Shot();s.id=str(c,"id");s.rollId=str(c,"roll_id");s.frame=num(c,"frame_number");s.tapped=lng(c,"tapped_at");s.captured=lng(c,"captured_at");s.timeEdited=num(c,"time_edited")==1;s.offset=num(c,"offset_seconds");s.zone=num(c,"zone_offset_minutes");s.status=str(c,"status");s.provider=str(c,"provider");s.locationTime=lng(c,"location_time");s.lat=real(c,"latitude");s.lon=real(c,"longitude");s.accuracy=real(c,"accuracy_m");s.aperture=str(c,"aperture");s.shutter=str(c,"shutter");s.lens=str(c,"lens");s.memo=str(c,"memo");s.ev=str(c,"ev");s.filters=str(c,"filters");return s;}
    static String str(Cursor c,String key){int i=c.getColumnIndexOrThrow(key);return c.isNull(i)?"":c.getString(i);}static int num(Cursor c,String key){return c.getInt(c.getColumnIndexOrThrow(key));}static long lng(Cursor c,String key){return c.getLong(c.getColumnIndexOrThrow(key));}static Double real(Cursor c,String key){int i=c.getColumnIndexOrThrow(key);return c.isNull(i)?null:c.getDouble(i);}
}
