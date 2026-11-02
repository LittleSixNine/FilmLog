package cn.sixnine.filmlog;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.time.LocalDate;
import java.util.*;

/** Inventory and reusable names. Loading a roll and consuming stock share one transaction. */
final class CatalogStore {
    static final String[] TABLES={"catalog_entries","film_batches","roll_details","stock_history"};
    static void createTables(SQLiteDatabase db){
        db.execSQL("CREATE TABLE catalog_entries(id TEXT PRIMARY KEY,kind TEXT NOT NULL,name TEXT NOT NULL,format TEXT NOT NULL DEFAULT '',notes TEXT NOT NULL DEFAULT '',archived INTEGER NOT NULL DEFAULT 0,created_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE film_batches(id TEXT PRIMARY KEY,stock TEXT NOT NULL,format TEXT NOT NULL,iso INTEGER NOT NULL,capacity INTEGER NOT NULL,quantity INTEGER NOT NULL,threshold INTEGER NOT NULL,location TEXT NOT NULL DEFAULT '',temperature TEXT NOT NULL DEFAULT '',expiry TEXT NOT NULL DEFAULT '',purchase_date TEXT NOT NULL DEFAULT '',price TEXT NOT NULL DEFAULT '',currency TEXT NOT NULL DEFAULT '',notes TEXT NOT NULL DEFAULT '',archived INTEGER NOT NULL DEFAULT 0,created_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE roll_details(id TEXT PRIMARY KEY,roll_id TEXT NOT NULL UNIQUE,format TEXT NOT NULL DEFAULT '',camera_id TEXT,project_id TEXT,batch_id TEXT,returned INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE stock_history(id TEXT PRIMARY KEY,batch_id TEXT NOT NULL,roll_id TEXT,delta INTEGER NOT NULL,reason TEXT NOT NULL,created_at INTEGER NOT NULL)");
    }
    static final class Entry {String id,kind,name,format,notes,condition,color,cameraModel,cameraLogo;boolean archived,favorite,completed,cameraShowBrand;long createdAt;Long startedAt,completedAt,archivedAt;}
    static final class Batch {String stockId;String id,stock,format,location,temperature,expiry,purchaseDate,price,currency,notes;int iso,capacity,quantity,threshold;boolean archived;}
    static final class StockSummary {
        long rolls,missingPrice,unknownCurrency;int low,expiring;
        final SortedMap<String,java.math.BigDecimal> values=new TreeMap<>();
    }
    static boolean expiring(Batch b,LocalDate today){return b.quantity>0&&!b.expiry.isEmpty()&&!LocalDate.parse(b.expiry).isAfter(today.plusDays(90));}
    static StockSummary summarize(List<Batch> batches,LocalDate today){
        StockSummary result=new StockSummary();
        for(Batch b:batches){result.rolls+=b.quantity;if(b.quantity<b.threshold)result.low++;
            if(expiring(b,today))result.expiring++;
            if(b.quantity==0)continue;
            if(b.price.isEmpty()){result.missingPrice+=b.quantity;continue;}
            if(b.currency.isEmpty()){result.unknownCurrency+=b.quantity;continue;}
            java.math.BigDecimal value=new java.math.BigDecimal(b.price).multiply(java.math.BigDecimal.valueOf(b.quantity));
            result.values.put(b.currency,result.values.getOrDefault(b.currency,java.math.BigDecimal.ZERO).add(value));
        }return result;
    }
    static final class Detail {String id,roll,format,camera,project,batch;boolean returned,halfFrame;int pushPull;}
    private final LogStore logs;
    CatalogStore(LogStore logs){this.logs=logs;}
    static String required(String text,String label){if(text==null||text.trim().isEmpty()||text.length()>500)throw new IllegalArgumentException(label+"不能为空且不能超过500字");return text.trim();}
    static String expiryMonth(int year,int month){if(year<1||year>9999||month<1||month>12)throw new IllegalArgumentException("请选择有效的到期年月");return java.time.YearMonth.of(year,month).atEndOfMonth().toString();}
    static void date(String text){if(!text.isEmpty())try{LocalDate.parse(text);}catch(Exception e){throw new IllegalArgumentException("日期请按 YYYY-MM-DD 填写");}}
    static void validate(Batch b){
        required(b.stock,"胶片名称");required(b.format,"胶片规格");
        if(b.iso<1||b.iso>102400||b.capacity<1||b.capacity>999||b.quantity<0||b.quantity>999999||b.threshold<0||b.threshold>999999)throw new IllegalArgumentException("ISO 1–102400，张数1–999，库存和提醒阈值0–999999");
        date(b.expiry);date(b.purchaseDate);LogStore.validateCost(b.price,b.currency);
    }
    ArrayList<Entry> entries(String kind,boolean archived){ArrayList<Entry> result=new ArrayList<>();try(Cursor c=logs.getReadableDatabase().query("catalog_entries",null,"kind=? AND archived=?",new String[]{kind,archived?"1":"0"},null,null,"name COLLATE NOCASE,created_at")){while(c.moveToNext()){Entry e=new Entry();e.id=LogStore.str(c,"id");e.kind=LogStore.str(c,"kind");e.name=LogStore.str(c,"name");e.format=LogStore.str(c,"format");e.notes=LogStore.str(c,"notes");e.cameraModel=LogStore.str(c,"camera_model");e.cameraLogo=LogStore.str(c,"camera_logo");e.cameraShowBrand=LogStore.num(c,"camera_show_brand")!=0;e.archived=LogStore.num(c,"archived")!=0;e.favorite=LogStore.num(c,"favorite")!=0;e.condition=LogStore.str(c,"condition");e.color=LogStore.str(c,"project_color");e.completed=LogStore.num(c,"project_completed")!=0;e.createdAt=LogStore.lng(c,"created_at");e.completedAt=c.isNull(c.getColumnIndexOrThrow("project_completed_at"))?null:LogStore.lng(c,"project_completed_at");e.startedAt=c.isNull(c.getColumnIndexOrThrow("project_started_at"))?null:LogStore.lng(c,"project_started_at");e.archivedAt=c.isNull(c.getColumnIndexOrThrow("project_archived_at"))?null:LogStore.lng(c,"project_archived_at");result.add(e);}}return result;}
    Entry entry(String id,String kind){for(boolean archived:new boolean[]{false,true})for(Entry e:entries(kind,archived))if(e.id.equals(id))return e;throw new IllegalArgumentException("所选器材或项目已变化，请重新选择");}
    String saveEntry(String id,String kind,String name,String format,String notes){
        Entry old=id.isEmpty()?null:entry(id,kind);return saveEntry(id,kind,name,format,notes,old==null?"":old.condition,old!=null&&old.favorite);
    }
    String saveEntry(String id,String kind,String name,String format,String notes,String condition,boolean favorite){
        if(!Arrays.asList("camera","lens","project").contains(kind))throw new IllegalArgumentException("无效分类");name=required(name,"名称");ContentValues v=new ContentValues();v.put("kind",kind);v.put("name",name);v.put("format",format.trim());v.put("notes",notes.trim());v.put("condition",condition.trim());v.put("favorite",favorite?1:0);
        if(id.isEmpty()){id=UUID.randomUUID().toString();v.put("id",id);v.put("created_at",System.currentTimeMillis());logs.getWritableDatabase().insertOrThrow("catalog_entries",null,v);}else{entry(id,kind);logs.getWritableDatabase().update("catalog_entries",v,"id=?",new String[]{id});}return id;
    }
    String saveCamera(String id,String model,String nickname,String format,String notes,String condition,boolean favorite,String logo){
        return saveCamera(id,model,nickname,format,notes,condition,favorite,logo,!id.isEmpty()&&entry(id,"camera").cameraShowBrand);
    }
    String saveCamera(String id,String model,String nickname,String format,String notes,String condition,boolean favorite,String logo,boolean showBrand){
        model=required(model,"相机型号");if(model.length()>500)throw new IllegalArgumentException("相机型号不能超过500字");CameraLogos.validate(logs.context,logo);SQLiteDatabase db=logs.getWritableDatabase();db.beginTransaction();try{
            String saved=saveEntry(id,"camera",nickname.trim().isEmpty()?model:nickname,format,notes,condition,favorite);
            ContentValues values=new ContentValues();values.put("camera_show_brand",showBrand?1:0);values.put("camera_model",model);values.put("camera_logo",logo);db.update("catalog_entries",values,"id=?",new String[]{saved});db.setTransactionSuccessful();return saved;
        }finally{db.endTransaction();}
    }
    static void validateProjectColor(String color){if(color==null||(!color.isEmpty()&&!color.matches("#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?")))throw new IllegalArgumentException("项目颜色无效");}
    String saveProject(String id,String name,String description,String color){
        validateProjectColor(color);SQLiteDatabase db=logs.getWritableDatabase();db.beginTransaction();try{
            Entry old=id.isEmpty()?null:entry(id,"project");String saved=saveEntry(id,"project",name,old==null?"":old.format,description,old==null?"":old.condition,old!=null&&old.favorite);
            ContentValues value=new ContentValues();value.put("project_color",color);if(old==null)value.put("project_started_at",entry(saved,"project").createdAt);db.update("catalog_entries",value,"id=?",new String[]{saved});db.setTransactionSuccessful();return saved;
        }finally{db.endTransaction();}
    }
    void completeProject(String id,boolean completed){SQLiteDatabase db=logs.getWritableDatabase();db.beginTransaction();try{
        Entry old=entry(id,"project");if(completed&&old.archived)throw new IllegalArgumentException("项目已归档，请先重新开启");ContentValues value=new ContentValues();value.put("project_completed",completed?1:0);
        if(!completed){value.putNull("project_completed_at");value.put("archived",0);}else if(!old.completed)value.put("project_completed_at",System.currentTimeMillis());
        if(db.update("catalog_entries",value,"id=? AND kind='project'",new String[]{id})!=1)throw new IllegalArgumentException("项目已变化");db.setTransactionSuccessful();
    }finally{db.endTransaction();}}

    void deleteProject(String id){SQLiteDatabase db=logs.getWritableDatabase();db.beginTransaction();try{
        entry(id,"project");ContentValues clear=new ContentValues();clear.putNull("project_id");db.update("roll_details",clear,"project_id=?",new String[]{id});if(db.delete("catalog_entries","id=? AND kind='project'",new String[]{id})!=1)throw new IllegalArgumentException("项目已变化");db.setTransactionSuccessful();
    }finally{db.endTransaction();}}
    void favorite(String id,String kind,boolean favorite){entry(id,kind);ContentValues values=new ContentValues();values.put("favorite",favorite?1:0);logs.getWritableDatabase().update("catalog_entries",values,"id=?",new String[]{id});}
    void archiveEntry(String id,String kind,boolean archived){SQLiteDatabase db=logs.getWritableDatabase();db.beginTransaction();try{Entry old=entry(id,kind);ContentValues value=new ContentValues();value.put("archived",archived?1:0);if(kind.equals("project")){if(archived&&!old.archived)value.put("project_archived_at",System.currentTimeMillis());}if(db.update("catalog_entries",value,"id=?",new String[]{id})!=1)throw new IllegalArgumentException("条目已变化");db.setTransactionSuccessful();}finally{db.endTransaction();}}

    ArrayList<Batch> batches(boolean archived){ArrayList<Batch> result=new ArrayList<>();try(Cursor c=logs.getReadableDatabase().query("film_batches",null,"archived=?",new String[]{archived?"1":"0"},null,null,"created_at DESC,rowid DESC")){while(c.moveToNext()){Batch b=new Batch();b.id=LogStore.str(c,"id");b.stock=LogStore.str(c,"stock");b.format=LogStore.str(c,"format");b.iso=LogStore.num(c,"iso");b.capacity=LogStore.num(c,"capacity");b.quantity=LogStore.num(c,"quantity");b.threshold=LogStore.num(c,"threshold");b.location=LogStore.str(c,"location");b.temperature=LogStore.str(c,"temperature");b.expiry=LogStore.str(c,"expiry");b.purchaseDate=LogStore.str(c,"purchase_date");b.price=LogStore.str(c,"price");b.currency=LogStore.str(c,"currency");b.notes=LogStore.str(c,"notes");b.archived=LogStore.num(c,"archived")!=0;result.add(b);}}return result;}
    Batch batch(String id){for(boolean archived:new boolean[]{false,true})for(Batch b:batches(archived))if(b.id.equals(id))return b;throw new IllegalArgumentException("库存批次不存在");}
    private void history(String batch,String roll,int delta,String reason){ContentValues v=new ContentValues();v.put("id",UUID.randomUUID().toString());v.put("batch_id",batch);if(roll.isEmpty())v.putNull("roll_id");else v.put("roll_id",roll);v.put("delta",delta);v.put("reason",reason);v.put("created_at",System.currentTimeMillis());logs.getWritableDatabase().insertOrThrow("stock_history",null,v);}
    String saveBatch(Batch b,int expectedQuantity){
        String originalId=b.id;boolean committed=false;validate(b);SQLiteDatabase db=logs.getWritableDatabase();db.beginTransaction();try{
            boolean fresh=b.id==null||b.id.isEmpty();Batch old=fresh?null:batch(b.id);
            if(old!=null&&old.quantity!=expectedQuantity)throw new IllegalArgumentException("库存数量已变化，请重新打开后编辑");
            ContentValues v=new ContentValues();v.put("stock",b.stock.trim());v.put("format",b.format.trim());v.put("iso",b.iso);v.put("capacity",b.capacity);v.put("quantity",b.quantity);v.put("threshold",b.threshold);v.put("location",b.location.trim());v.put("temperature",b.temperature.trim());v.put("expiry",b.expiry);v.put("purchase_date",b.purchaseDate);v.put("price",b.price);v.put("currency",b.currency);v.put("notes",b.notes.trim());
            if(fresh){b.id=UUID.randomUUID().toString();v.put("id",b.id);v.put("created_at",System.currentTimeMillis());db.insertOrThrow("film_batches",null,v);}else db.update("film_batches",v,"id=?",new String[]{b.id});
            String link=b.stockId==null?(fresh?"":ArtworkLinks.get(logs,"batch",b.id)):ArtworkLinks.selected(logs.context,b.stockId,b.stock,b.iso,b.format);if(b.stockId==null&&old!=null&&(!old.stock.equals(b.stock)||!old.format.equals(b.format)||old.iso!=b.iso))link="";ArtworkLinks.put(db,"batch",b.id,link);if(old!=null&&!old.format.equals(b.format))ArtworkLinks.setCover(logs.context,logs,"batch",b.id,b.format,"");
            int delta=b.quantity-(fresh?0:old.quantity);if(fresh||delta!=0)history(b.id,"",delta,fresh?"added":"adjusted");db.setTransactionSuccessful();committed=true;return b.id;
        }finally{db.endTransaction();if(!committed)b.id=originalId;}
    }
    void archiveBatch(String id,boolean archived){batch(id);ContentValues v=new ContentValues();v.put("archived",archived?1:0);logs.getWritableDatabase().update("film_batches",v,"id=?",new String[]{id});}
    Detail detail(String roll){try(Cursor c=logs.getReadableDatabase().query("roll_details",null,"roll_id=?",new String[]{roll},null,null,null)){if(!c.moveToFirst())return null;Detail d=new Detail();d.id=LogStore.str(c,"id");d.roll=roll;d.format=LogStore.str(c,"format");d.camera=LogStore.str(c,"camera_id");d.project=LogStore.str(c,"project_id");d.batch=LogStore.str(c,"batch_id");d.returned=LogStore.num(c,"returned")!=0;d.halfFrame=LogStore.num(c,"half_frame")!=0;d.pushPull=LogStore.num(c,"push_pull_stops");return d;}}
    static String pushPullLabel(int stops){if(stops< -2||stops>3)throw new IllegalArgumentException("增减感请选择−2至+3的整数档");return stops==0?"正常":(stops>0?"+":"")+stops;}
    void pushPull(String roll,int stops){pushPullLabel(stops);android.database.sqlite.SQLiteDatabase db=logs.getWritableDatabase();db.beginTransaction();try{logs.roll(roll);Detail detail=detail(roll);if(detail!=null&&detail.returned)throw new IllegalArgumentException("已撤销装卷不能修改增减感");if(detail==null)details(roll,"","","");ContentValues value=new ContentValues();value.put("push_pull_stops",stops);db.update("roll_details",value,"roll_id=?",new String[]{roll});db.setTransactionSuccessful();}finally{db.endTransaction();}}
    private void selectable(String id,String kind){if(!id.isEmpty()&&entry(id,kind).archived)throw new IllegalArgumentException("已归档器材或项目不能用于新装卷");}
    static boolean supportsHalfFrame(String format){return format!=null&&(format.replace(" ","").equalsIgnoreCase("35mm")||format.equals("135"));}
    void halfFrame(String roll,boolean enabled){Detail d=detail(roll);if(d==null||enabled&&!supportsHalfFrame(d.format))throw new IllegalArgumentException("半格仅适用于35 mm胶卷");ContentValues v=new ContentValues();v.put("half_frame",enabled?1:0);logs.getWritableDatabase().update("roll_details",v,"roll_id=?",new String[]{roll});}
    void details(String roll,String format,String camera,String project){
        logs.roll(roll);Detail existing=detail(roll);if(existing==null||!existing.camera.equals(camera))selectable(camera,"camera");if(existing==null||!existing.project.equals(project))selectable(project,"project");ContentValues v=new ContentValues();v.put("format",format.trim());if(!supportsHalfFrame(format))v.put("half_frame",0);if(camera.isEmpty())v.putNull("camera_id");else v.put("camera_id",camera);if(project.isEmpty())v.putNull("project_id");else v.put("project_id",project);
        if(existing==null){v.put("id",UUID.randomUUID().toString());v.put("roll_id",roll);logs.getWritableDatabase().insertOrThrow("roll_details",null,v);}else logs.getWritableDatabase().update("roll_details",v,"roll_id=?",new String[]{roll});if(existing!=null&&!existing.format.equals(format.trim()))ArtworkLinks.setCover(logs.context,logs,"roll",roll,format,"");
    }
    void assignFreeRoll(String roll,String project){SQLiteDatabase db=logs.getWritableDatabase();db.beginTransaction();try{
        Entry target=entry(project,"project");if(target.archived||target.completed)throw new IllegalArgumentException("项目已关闭，请重新核对");logs.roll(roll);Detail existing=detail(roll);
        if(existing!=null&&!existing.project.isEmpty()){if(existing.project.equals(project)){db.setTransactionSuccessful();return;}throw new IllegalArgumentException("胶卷已归入其他项目，请重新核对");}
        details(roll,existing==null?"":existing.format,existing==null?"":existing.camera,project);db.setTransactionSuccessful();
    }finally{db.endTransaction();}}
    String manual(String requestId,String name,String stock,String cameraName,int iso,int capacity,String format,String camera,String project){return manual(requestId,name,stock,cameraName,iso,capacity,format,camera,project,"");}
    String manual(String requestId,String name,String stock,String cameraName,int iso,int capacity,String format,String camera,String project,String stockId){
        UUID.fromString(requestId);required(stock,"胶片名称");required(format,"胶片规格");if(iso<1||iso>102400||capacity<1||capacity>999)throw new IllegalArgumentException("ISO或曝光张数无效");
        SQLiteDatabase db=logs.getWritableDatabase();db.beginTransaction();try{
            try(Cursor c=db.rawQuery("SELECT roll_id FROM roll_details WHERE id=?",new String[]{requestId})){if(c.moveToFirst()){String id=c.getString(0);logs.roll(id);db.setTransactionSuccessful();return id;}}
            if(!camera.isEmpty()){String cameraFormat=entry(camera,"camera").format;if(!cameraFormat.isEmpty()&&!cameraFormat.replace(" ","").equalsIgnoreCase(format.replace(" ","")))throw new IllegalArgumentException("相机规格与胶片不一致");}
            String id=logs.addRoll(name.trim().isEmpty()?stock.trim():name.trim(),stock.trim(),cameraName.trim().isEmpty()?"未设置相机":cameraName.trim(),iso,capacity);details(id,format,camera,project);ContentValues v=new ContentValues();v.put("id",requestId);db.update("roll_details",v,"roll_id=?",new String[]{id});ArtworkLinks.put(db,"roll",id,ArtworkLinks.selected(logs.context,stockId,stock,iso,format));db.setTransactionSuccessful();return id;
        }finally{db.endTransaction();}
    }
    String load(String batchId,String requestId,String name,String camera,String project){return load(batchId,requestId,name,camera,project,"");}
    String load(String batchId,String requestId,String name,String camera,String project,String cameraName){
        UUID.fromString(requestId);SQLiteDatabase db=logs.getWritableDatabase();db.beginTransaction();try{
            try(Cursor c=db.rawQuery("SELECT roll_id,batch_id FROM roll_details WHERE id=?",new String[]{requestId})){if(c.moveToFirst()){if(!batchId.equals(c.getString(1)))throw new IllegalArgumentException("装卷请求与批次不一致");String roll=c.getString(0);logs.roll(roll);db.setTransactionSuccessful();return roll;}}
            Batch b=batch(batchId);validate(b);if(b.archived||b.quantity<1)throw new IllegalArgumentException("库存不足或批次已归档，未创建胶卷");selectable(camera,"camera");selectable(project,"project");if(!camera.isEmpty()){String format=entry(camera,"camera").format;if(!format.isEmpty()&&!format.replace(" ","").equalsIgnoreCase(b.format.replace(" ","")))throw new IllegalArgumentException("相机规格与库存胶片不一致，请重新选择");}
            String id=logs.addRoll(name.trim().isEmpty()?b.stock:name.trim(),b.stock,camera.isEmpty()?(cameraName.trim().isEmpty()?"未设置相机":cameraName.trim()):entry(camera,"camera").name,b.iso,b.capacity);logs.costs(id,b.price,"",b.currency);
            details(id,b.format,camera,project);ContentValues metadata=new ContentValues();metadata.put("id",requestId);metadata.put("batch_id",batchId);db.update("roll_details",metadata,"roll_id=?",new String[]{id});ArtworkLinks.put(db,"roll",id,ArtworkLinks.get(logs,"batch",batchId));
            ContentValues count=new ContentValues();count.put("quantity",b.quantity-1);db.update("film_batches",count,"id=?",new String[]{batchId});history(batchId,id,-1,"loaded");db.setTransactionSuccessful();return id;
        }finally{db.endTransaction();}
    }
    boolean returnUnused(String roll){
        SQLiteDatabase db=logs.getWritableDatabase();db.beginTransaction();try{
            Detail d=detail(roll);if(d==null||d.batch.isEmpty())throw new IllegalArgumentException("这卷没有库存装卷记录");if(d.returned){db.setTransactionSuccessful();return false;}
            LogStore.Roll r=logs.roll(roll);if(r.count!=0||!r.state.equals("active")||r.archived)throw new IllegalArgumentException("仅未拍摄、未归档的拍摄中空卷可撤销装卷");Batch b=batch(d.batch);if(b.quantity>=999999)throw new IllegalArgumentException("库存超过数量上限");
            ContentValues count=new ContentValues();count.put("quantity",b.quantity+1);db.update("film_batches",count,"id=?",new String[]{b.id});db.execSQL("UPDATE roll_details SET returned=1 WHERE roll_id=?",new Object[]{roll});logs.archive(roll,true);history(b.id,roll,1,"returned");db.setTransactionSuccessful();return true;
        }finally{db.endTransaction();}
    }
    String history(String batch){StringBuilder result=new StringBuilder();try(Cursor c=logs.getReadableDatabase().rawQuery("SELECT delta,reason,roll_id,created_at FROM stock_history WHERE batch_id=? ORDER BY created_at DESC,rowid DESC LIMIT 100",new String[]{batch})){while(c.moveToNext()){int delta=c.getInt(0);String reason=c.getString(1);result.append(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(java.time.Instant.ofEpochMilli(c.getLong(3)).atZone(java.time.ZoneId.systemDefault()))).append(" · ").append(delta>0?"+":"").append(delta).append(" · ").append(reason.equals("loaded")?"装卷":reason.equals("returned")?"返还":reason.equals("added")?"入库":reason.equals("roll_deleted")?(delta<0?"装卷 · 胶卷已删除":"返还 · 胶卷已删除"):"数量调整");if(!c.isNull(2))result.append(" · ").append(logs.roll(c.getString(2)).name);result.append('\n');}}return result.toString();}
}
