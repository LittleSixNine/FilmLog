package cn.sixnine.filmlog;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.zip.*;

/** Logical archive. Restore imports new identities, preserving all existing data. */
final class FilmBackup {
    static final String[] TABLES={"catalog_entries","film_batches","rolls","shots","scans","roll_details","stock_history","reference_photos","film_stocks","exposure_presets","artwork_links"};
    private static JSONArray rows(SQLiteDatabase db,String table)throws JSONException{
        JSONArray result=new JSONArray();try(Cursor c=db.query(table,null,null,null,null,null,"rowid")){
            while(c.moveToNext()){JSONObject row=new JSONObject();for(int i=0;i<c.getColumnCount();i++){
                Object value;switch(c.getType(i)){case Cursor.FIELD_TYPE_NULL:value=JSONObject.NULL;break;case Cursor.FIELD_TYPE_INTEGER:value=c.getLong(i);break;case Cursor.FIELD_TYPE_FLOAT:value=c.getDouble(i);break;default:value=c.getString(i);}row.put(c.getColumnName(i),value);
            }result.put(row);}
        }return result;
    }
    static void write(Context context,LogStore store,OutputStream destination)throws Exception{
        SQLiteDatabase db=store.getWritableDatabase();JSONObject manifest=new JSONObject().put("format",1).put("schema",25);
        // Snapshot rows quickly; immutable media copies can be streamed after releasing the database lock.
        db.beginTransaction();try{for(String table:TABLES)manifest.put(table,rows(db,table));db.setTransactionSuccessful();}finally{db.endTransaction();}
        try(ZipOutputStream zip=new ZipOutputStream(new BufferedOutputStream(destination))){
            zip.putNextEntry(new ZipEntry("manifest.json"));zip.write(manifest.toString().getBytes(StandardCharsets.UTF_8));zip.closeEntry();
            ScanStore scans=new ScanStore(context,store);JSONArray assets=manifest.getJSONArray("scans");
            for(int i=0;i<assets.length();i++){JSONObject asset=assets.getJSONObject(i);File source=scans.file(asset.getString("id"));zip.putNextEntry(new ZipEntry("scans/"+asset.getString("id")+".jpg"));
                try(InputStream in=new FileInputStream(source)){if(!copyHash(in,zip,asset.getLong("byte_count")).equals(asset.getString("sha256")))throw new IOException("扫描副本摘要不一致");}zip.closeEntry();
            }
            JSONArray references=manifest.getJSONArray("reference_photos");for(int i=0;i<references.length();i++){JSONObject photo=references.getJSONObject(i);File source=scans.file(photo.getString("id"));zip.putNextEntry(new ZipEntry("scans/"+photo.getString("id")+".jpg"));try(InputStream input=new FileInputStream(source)){if(!copyHash(input,zip,photo.getLong("byte_count")).equals(photo.getString("sha256")))throw new IOException("参考照片摘要不一致");}zip.closeEntry();}
        }
    }
    static final class Prepared implements Closeable {
        File directory;JSONObject manifest;final Map<String,File> files=new HashMap<>();
        int count(String table)throws JSONException{return manifest.getJSONArray(table).length();}
        public void close(){if(directory!=null){File[] children=directory.listFiles();if(children!=null)for(File f:children)f.delete();directory.delete();}}
    }
    static Prepared inspect(Context context,InputStream source)throws Exception{
        Prepared p=new Prepared();p.directory=new File(context.getCacheDir(),"restore-"+UUID.randomUUID());if(!p.directory.mkdirs())throw new IOException("无法暂存备份");boolean ok=false;
        try{
            HashSet<String> seen=new HashSet<>();long total=0;
            try(ZipInputStream zip=new ZipInputStream(new BufferedInputStream(source))){ZipEntry e;while((e=zip.getNextEntry())!=null){
                String name=e.getName();if(!seen.add(name)||seen.size()>10000)throw new IOException("备份条目重复或过多");
                if(e.isDirectory()||(!name.equals("manifest.json")&&!name.matches("scans/[0-9a-fA-F-]{36}\\.jpg")))throw new IOException("备份包含不安全路径");
                long limit=name.equals("manifest.json")?32L*1024*1024:ScanStore.MAX_BYTES;
                File file=new File(p.directory,name.equals("manifest.json")?name:name.substring(6));long bytes=0;
                try(OutputStream out=new FileOutputStream(file)){byte[] b=new byte[65536];int n;while((n=zip.read(b))!=-1){bytes+=n;total+=n;if(bytes>limit||total>8L*1024*1024*1024)throw new IOException("备份超出恢复容量限制");out.write(b,0,n);}}
                if(name.equals("manifest.json")){p.manifest=new JSONObject(new String(readSmall(file),StandardCharsets.UTF_8));}else p.files.put(name.substring(6,name.length()-4),file);
                zip.closeEntry();
            }}
            if(p.manifest==null||p.manifest.getInt("format")!=1||(p.manifest.getInt("schema")<5||p.manifest.getInt("schema")>25))throw new IOException("不支持的备份版本");
            if(p.manifest.getInt("schema")<7){JSONArray legacy=p.manifest.getJSONArray("rolls");for(int i=0;i<legacy.length();i++){JSONObject row=legacy.getJSONObject(i);row.put("film_cost","").put("cost_currency","");}p.manifest.put("schema",7);}
            normalizeCatalog(p.manifest);validate(p);validateExtras(context,p.manifest);ok=true;return p;
        }finally{if(!ok)p.close();}
    }
    static void normalizeCatalog(JSONObject manifest)throws JSONException{
        if(manifest.getInt("schema")<9){for(String table:CatalogStore.TABLES)if(!manifest.has(table))manifest.put(table,new JSONArray());manifest.put("schema",9);}
        if(manifest.getInt("schema")<10){if(!manifest.has("reference_photos"))manifest.put("reference_photos",new JSONArray());manifest.put("schema",10);}
        if(manifest.getInt("schema")<11){if(!manifest.has("film_stocks"))manifest.put("film_stocks",new JSONArray());manifest.put("schema",11);}
        if(manifest.getInt("schema")<12){JSONArray entries=manifest.getJSONArray("catalog_entries");for(int i=0;i<entries.length();i++)entries.getJSONObject(i).put("favorite",0).put("condition","");manifest.put("schema",12);}
        if(manifest.getInt("schema")<13){JSONArray details=manifest.getJSONArray("roll_details");for(int i=0;i<details.length();i++)details.getJSONObject(i).put("push_pull_stops",0);manifest.put("schema",13);}
        if(manifest.getInt("schema")<14){manifest.put("exposure_presets",new JSONArray());manifest.put("artwork_links",new JSONArray());manifest.put("schema",14);}
        if(manifest.getInt("schema")<15){JSONArray entries=manifest.getJSONArray("catalog_entries");for(int i=0;i<entries.length();i++)entries.getJSONObject(i).put("project_color","").put("project_completed",0);manifest.put("schema",15);}
        if(manifest.getInt("schema")<16){JSONArray entries=manifest.getJSONArray("catalog_entries");for(int i=0;i<entries.length();i++)entries.getJSONObject(i).put("project_completed_at",JSONObject.NULL);manifest.put("schema",16);}
        if(manifest.getInt("schema")<17){JSONArray entries=manifest.getJSONArray("catalog_entries");for(int i=0;i<entries.length();i++)entries.getJSONObject(i).put("project_archived_at",JSONObject.NULL);manifest.put("schema",17);}
        if(manifest.getInt("schema")<18){JSONArray entries=manifest.getJSONArray("catalog_entries");for(int i=0;i<entries.length();i++)entries.getJSONObject(i).put("project_started_at",JSONObject.NULL);manifest.put("schema",18);}
        if(manifest.getInt("schema")<19)manifest.put("schema",19); // Read-only Filmer IDs require a compatible catalogue.
        if(manifest.getInt("schema")<20){JSONArray entries=manifest.getJSONArray("catalog_entries");for(int i=0;i<entries.length();i++)entries.getJSONObject(i).put("camera_model","").put("camera_logo","");manifest.put("schema",20);}
        if(manifest.getInt("schema")<21){JSONArray rows=manifest.getJSONArray("rolls");for(int i=0;i<rows.length();i++)for(String field:new String[]{"lab_sent_date","lab_received_date","lab_notes"})if(!rows.getJSONObject(i).has(field))rows.getJSONObject(i).put(field,"");manifest.put("schema",21);}
        if(manifest.getInt("schema")<22){JSONArray rows=manifest.getJSONArray("shots");for(int i=0;i<rows.length();i++)if(!rows.getJSONObject(i).has("time_edited"))rows.getJSONObject(i).put("time_edited",0);manifest.put("schema",22);}
        if(manifest.getInt("schema")<23){JSONArray entries=manifest.getJSONArray("catalog_entries");for(int i=0;i<entries.length();i++)entries.getJSONObject(i).put("camera_show_brand",0);manifest.put("schema",23);}
        if(manifest.getInt("schema")<24){JSONArray rows=manifest.getJSONArray("roll_details");for(int i=0;i<rows.length();i++)rows.getJSONObject(i).put("half_frame",0);manifest.put("schema",24);}
        if(manifest.getInt("schema")<25){JSONArray rows=manifest.getJSONArray("artwork_links");for(int i=0;i<rows.length();i++)rows.getJSONObject(i).put("cover_image","");manifest.put("schema",25);}
    }
    private static void validateExtras(Context context,JSONObject manifest)throws Exception {
        JSONArray cameras=manifest.getJSONArray("catalog_entries");for(int i=0;i<cameras.length();i++)try{CameraLogos.validate(context,cameras.getJSONObject(i).getString("camera_logo"));}catch(IllegalArgumentException invalid){throw new IOException(invalid.getMessage(),invalid);}
        JSONArray presets=manifest.getJSONArray("exposure_presets");ids(presets);for(int i=0;i<presets.length();i++)PresetStore.validate(PresetStore.fromJson(presets.getJSONObject(i)));
        JSONArray links=manifest.getJSONArray("artwork_links");ids(links);Set<String> owners=new HashSet<>();Set<String> batches=ids(manifest.getJSONArray("film_batches")),rolls=ids(manifest.getJSONArray("rolls"));
        for(int i=0;i<links.length();i++){JSONObject row=links.getJSONObject(i);String kind=row.getString("owner_kind"),owner=row.getString("owner_id");if(!(kind.equals("batch")?batches.contains(owner):kind.equals("roll")&&rolls.contains(owner))||!owners.add(kind+":"+owner))throw new IOException("封面关联对象无效或重复");FilmStockStore.Stock linked=ArtworkLinks.original(context,row.getString("stock_id"));String format="";JSONArray records=manifest.getJSONArray(kind.equals("batch")?"film_batches":"roll_details");for(int n=0;n<records.length();n++){JSONObject record=records.getJSONObject(n);if(record.getString(kind.equals("batch")?"id":"roll_id").equals(owner)){format=record.getString("format");break;}}FilmArtwork.validateCover(context,row.getString("stock_id"),format,row.getString("cover_image"));if(!FilmStockStore.matches(linked,format))throw new IOException("封面片种规格与关联记录不符");}
    }
    private static byte[] readSmall(File file)throws IOException{try(InputStream in=new FileInputStream(file);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toByteArray();}}
    private static HashSet<String> ids(JSONArray rows)throws Exception{HashSet<String> ids=new HashSet<>();for(int i=0;i<rows.length();i++){String id=rows.getJSONObject(i).getString("id");if(id.isEmpty()||!ids.add(id))throw new IOException("备份 ID 重复");}return ids;}
    static void validate(Prepared p)throws Exception{
        HashSet<String> rolls=ids(p.manifest.getJSONArray("rolls")),shots=ids(p.manifest.getJSONArray("shots")),scans=ids(p.manifest.getJSONArray("scans"));
        HashSet<String> references=ids(p.manifest.getJSONArray("reference_photos")),assets=new HashSet<>(scans);for(String reference:references)if(!assets.add(reference))throw new IOException("参考照片与扫描标识冲突");if(!assets.equals(p.files.keySet()))throw new IOException("照片文件和清单不一致");
        validateCatalog(p.manifest,rolls);
        JSONArray rollRows=p.manifest.getJSONArray("rolls");
        for(int i=0;i<rollRows.length();i++){JSONObject r=rollRows.getJSONObject(i);for(String field:new String[]{"lab_sent_date","lab_received_date","lab_notes"})if(!(r.get(field) instanceof String))throw new IOException("冲洗字段无效");if(r.getString("lab_notes").length()>4000)throw new IOException("冲洗备注过长");LogStore.labDates(r.getString("lab_sent_date"),r.getString("lab_received_date"));int capacity=r.getInt("capacity"),iso=r.getInt("iso"),archived=r.getInt("archived");
            if(capacity<1||iso<0||iso>102400||(archived!=0&&archived!=1)||!Arrays.asList("active","finished","developing","received").contains(r.getString("state")))throw new IOException("胶卷属性无效");LogStore.validateCost(r.getString("film_cost"),r.getString("cost_currency"));LogStore.validateCost(r.getString("lab_cost"),r.getString("cost_currency"));
        }
        Map<String,String> shotRoll=new HashMap<>();HashSet<String> frames=new HashSet<>(),associated=new HashSet<>();JSONArray exposures=p.manifest.getJSONArray("shots");
        for(int i=0;i<exposures.length();i++){JSONObject shot=exposures.getJSONObject(i);String roll=shot.getString("roll_id");if(!rolls.contains(roll)||shot.getInt("frame_number")<1||!frames.add(roll+":"+shot.getInt("frame_number")))throw new IOException("曝光帧号无效");shotRoll.put(shot.getString("id"),roll);
            Object zoneValue=shot.get("zone_offset_minutes");int zone=shot.getInt("zone_offset_minutes");if(!(zoneValue instanceof Number)||((Number)zoneValue).doubleValue()!=zone||zone< -1080||zone>1080)throw new IOException("曝光时区必须为−1080至1080的整数分钟");
            Object frameValue=shot.get("frame_number");int frame=shot.getInt("frame_number");if(!(frameValue instanceof Number)||((Number)frameValue).doubleValue()!=frame||!RecordRules.validFrame(frame))throw new IOException("曝光帧号无效");
            if(!Arrays.asList("pending","denied","disabled","timeout","interrupted","acquired").contains(shot.getString("status")))throw new IOException("定位状态无效");
            if(shot.isNull("latitude")!=shot.isNull("longitude"))throw new IOException("坐标不完整");
            if(!shot.isNull("latitude")){double lat=shot.getDouble("latitude"),lon=shot.getDouble("longitude");if(!Double.isFinite(lat)||!Double.isFinite(lon)||Math.abs(lat)>90||Math.abs(lon)>180)throw new IOException("坐标无效");}

            int offset=shot.getInt("offset_seconds"),edited=shot.getInt("time_edited");Object flag=shot.get("time_edited");if(!(flag instanceof Number)||((Number)flag).doubleValue()!=edited||(edited!=0&&edited!=1))throw new IOException("手动时间标记无效");
            if(offset<-3600||offset>3600||(edited==0&&shot.getLong("captured_at")!=Math.addExact(shot.getLong("tapped_at"),offset*1000L)))throw new IOException("时间偏移无效");
            if(edited==1)try{Object value=shot.get("captured_at");if(!(value instanceof Number)||((Number)value).doubleValue()!=shot.getLong("captured_at"))throw new IllegalArgumentException();RecordRules.editedTime(shot.getLong("captured_at"));}catch(IllegalArgumentException invalid){throw new IOException("手动拍摄时间无效");}
        }
        JSONArray scanRows=p.manifest.getJSONArray("scans");for(int i=0;i<scanRows.length();i++){JSONObject a=scanRows.getJSONObject(i);String id=a.getString("id"),roll=a.getString("roll_id");UUID.fromString(id);if(!rolls.contains(roll))throw new IOException("扫描所属胶卷无效");
            if(!a.isNull("shot_id")){String shot=a.getString("shot_id");if(!shots.contains(shot)||!roll.equals(shotRoll.get(shot))||!associated.add(shot)||!a.isNull("deleted_at"))throw new IOException("扫描关联冲突");}
            if(!a.isNull("previous_shot_id")&&!roll.equals(shotRoll.get(a.getString("previous_shot_id"))))throw new IOException("最近删除关联无效");
            File file=p.files.get(id);long bytes=a.getLong("byte_count");if(bytes<1||bytes>ScanStore.MAX_BYTES||file.length()!=bytes)throw new IOException("扫描长度不一致");
            try(InputStream in=new FileInputStream(file)){if(!copyHash(in,new OutputStream(){public void write(int b){}public void write(byte[] b,int o,int n){}},bytes).equals(a.getString("sha256")))throw new IOException("扫描摘要不一致");}
        }
        JSONArray stockRows=p.manifest.getJSONArray("film_stocks");ids(stockRows);for(int i=0;i<stockRows.length();i++){JSONObject row=stockRows.getJSONObject(i);FilmStockStore.Stock stock=new FilmStockStore.Stock();stock.id=row.getString("id");stock.brand=row.getString("brand");stock.name=row.getString("name");stock.iso=row.getInt("iso");Object isoValue=row.get("iso");if(!(isoValue instanceof Number)||((Number)isoValue).doubleValue()!=stock.iso)throw new IOException("片种ISO必须为整数");stock.process=row.getString("process");stock.power=row.getString("reciprocity_p");stock.formats=row.getString("formats");int archived=row.getInt("archived");Object archivedValue=row.get("archived");row.getLong("created_at");if(!(archivedValue instanceof Number)||((Number)archivedValue).doubleValue()!=archived||(archived!=0&&archived!=1))throw new IOException("片种归档状态无效");try{FilmStockStore.validate(stock);}catch(IllegalArgumentException invalid){throw new IOException("片种数据无效："+invalid.getMessage(),invalid);}}
        HashSet<String> referenceShots=new HashSet<>();JSONArray photos=p.manifest.getJSONArray("reference_photos");for(int i=0;i<photos.length();i++){JSONObject photo=photos.getJSONObject(i);String id=photo.getString("id"),shot=photo.getString("shot_id");if(!UUID.fromString(id).toString().equals(id)||!shots.contains(shot)||!referenceShots.add(shot))throw new IOException("参考照片曝光关联无效");long bytes=photo.getLong("byte_count");File file=p.files.get(id);if(bytes<1||bytes>ReferencePhotos.MAX_BYTES||file.length()!=bytes)throw new IOException("参考照片长度无效");photo.getLong("created_at");try(InputStream input=new FileInputStream(file)){if(!copyHash(input,new OutputStream(){public void write(int b){}public void write(byte[] b,int o,int n){}},bytes).equals(photo.getString("sha256")))throw new IOException("参考照片摘要不一致");}try(InputStream input=new FileInputStream(file)){JpegCopy.clean(input,new OutputStream(){public void write(int b){}public void write(byte[] b,int o,int n){}});}}
    }
    private static void validateCatalog(JSONObject manifest,Set<String> rolls)throws Exception{
        Set<String> entries=ids(manifest.getJSONArray("catalog_entries")),batches=ids(manifest.getJSONArray("film_batches"));ids(manifest.getJSONArray("roll_details"));ids(manifest.getJSONArray("stock_history"));
        Map<String,String> kinds=new HashMap<>();JSONArray catalog=manifest.getJSONArray("catalog_entries");
        for(int i=0;i<catalog.length();i++){JSONObject row=catalog.getJSONObject(i);String kind=row.getString("kind");if(!Arrays.asList("camera","lens","project").contains(kind))throw new IOException("器材/项目分类无效");CatalogStore.required(row.getString("name"),"名称");row.getString("format");row.getString("notes");Object showBrand=row.get("camera_show_brand");if(!(showBrand instanceof Number)||((Number)showBrand).doubleValue()!=((Number)showBrand).intValue()||(((Number)showBrand).intValue()!=0&&((Number)showBrand).intValue()!=1)||(!kind.equals("camera")&&((Number)showBrand).intValue()!=0))throw new IOException("相机名称展示字段无效");Object cameraModel=row.get("camera_model"),cameraLogo=row.get("camera_logo");if(!(cameraModel instanceof String)||!(cameraLogo instanceof String)||((String)cameraModel).length()>500||((String)cameraLogo).length()>200||(!kind.equals("camera")&&(!((String)cameraModel).isEmpty()||!((String)cameraLogo).isEmpty())))throw new IOException("相机型号或Logo字段无效");if(!(row.get("favorite") instanceof Number)||row.getDouble("favorite")!=row.getInt("favorite")||(row.getInt("favorite")!=0&&row.getInt("favorite")!=1)||!(row.get("condition") instanceof String))throw new IOException("器材收藏或成色字段无效");if(row.getInt("archived")!=0&&row.getInt("archived")!=1)throw new IOException("归档状态无效");Object color=row.get("project_color"),completed=row.get("project_completed");if(!(color instanceof String)||!(completed instanceof Number)||((Number)completed).doubleValue()!=((Number)completed).intValue()||(((Number)completed).intValue()!=0&&((Number)completed).intValue()!=1))throw new IOException("项目样式或状态字段无效");try{CatalogStore.validateProjectColor((String)color);}catch(IllegalArgumentException invalid){throw new IOException(invalid.getMessage(),invalid);}if(!kind.equals("project")&&(!((String)color).isEmpty()||((Number)completed).intValue()!=0))throw new IOException("非项目条目包含项目属性");for(String date:new String[]{"project_started_at","project_completed_at","project_archived_at"}){Object value=row.get(date);if(value!=JSONObject.NULL&&(!(value instanceof Integer||value instanceof Long)||((Number)value).longValue() < -8640000000000000L||((Number)value).longValue()>8640000000000000L||!kind.equals("project")||(date.equals("project_completed_at")&&row.getInt("project_completed")!=1)))throw new IOException("项目日期无效");}row.getLong("created_at");kinds.put(row.getString("id"),kind);}
        Map<String,Integer> quantities=new HashMap<>();JSONArray stock=manifest.getJSONArray("film_batches");
        for(int i=0;i<stock.length();i++){JSONObject row=stock.getJSONObject(i);CatalogStore.Batch b=new CatalogStore.Batch();b.stock=row.getString("stock");b.format=row.getString("format");b.iso=row.getInt("iso");b.capacity=row.getInt("capacity");b.quantity=row.getInt("quantity");b.threshold=row.getInt("threshold");b.expiry=row.getString("expiry");b.purchaseDate=row.getString("purchase_date");b.price=row.getString("price");b.currency=row.getString("currency");CatalogStore.validate(b);for(String key:new String[]{"location","temperature","notes"})row.getString(key);if(row.getInt("archived")!=0&&row.getInt("archived")!=1)throw new IOException("库存归档状态无效");row.getLong("created_at");quantities.put(row.getString("id"),b.quantity);}
        Map<String,String> loadedExpected=new HashMap<>();Set<String> returnedExpected=new HashSet<>();Map<String,JSONObject> rollRows=new HashMap<>();JSONArray savedRolls=manifest.getJSONArray("rolls");for(int i=0;i<savedRolls.length();i++)rollRows.put(savedRolls.getJSONObject(i).getString("id"),savedRolls.getJSONObject(i));
        Set<String> assignedRolls=new HashSet<>();JSONArray details=manifest.getJSONArray("roll_details");
        for(int i=0;i<details.length();i++){JSONObject row=details.getJSONObject(i);String roll=row.getString("roll_id");if(!rolls.contains(roll)||!assignedRolls.add(roll))throw new IOException("胶卷资料引用冲突");Object half=row.get("half_frame");if(!(half instanceof Number)||row.getDouble("half_frame")!=row.getInt("half_frame")||(row.getInt("half_frame")!=0&&row.getInt("half_frame")!=1)||row.getInt("half_frame")==1&&!CatalogStore.supportsHalfFrame(row.getString("format")))throw new IOException("半格画幅字段无效");row.getString("format");Object push=row.get("push_pull_stops");if(!(push instanceof Number)||((Number)push).doubleValue()!=((Number)push).intValue())throw new IOException("增减感必须是整数档");try{CatalogStore.pushPullLabel(((Number)push).intValue());}catch(IllegalArgumentException invalid){throw new IOException("增减感档数无效");}int returned=row.getInt("returned");if(returned!=0&&returned!=1)throw new IOException("装卷返还状态无效");for(String key:new String[]{"camera_id","project_id","batch_id"})if(!row.isNull(key)){String id=row.getString(key);if(key.equals("batch_id")?!batches.contains(id):!Objects.equals(kinds.get(id),key.equals("camera_id")?"camera":"project"))throw new IOException("胶卷器材/项目/库存引用无效");}if(returned==1&&row.isNull("batch_id"))throw new IOException("返还缺少库存批次");if(!row.isNull("batch_id"))loadedExpected.put(roll,row.getString("batch_id"));if(returned==1){returnedExpected.add(roll);if(rollRows.get(roll).getInt("archived")!=1||!rollRows.get(roll).getString("state").equals("active"))throw new IOException("撤销装卷必须保留为空卷归档");JSONArray shots=manifest.getJSONArray("shots");for(int n=0;n<shots.length();n++)if(shots.getJSONObject(n).getString("roll_id").equals(roll))throw new IOException("已拍摄胶卷不能标记库存已返还");}}
        Map<String,String> loaded=new HashMap<>();Set<String> returned=new HashSet<>();Map<String,Long> totals=new HashMap<>();JSONArray history=manifest.getJSONArray("stock_history");
        for(int i=0;i<history.length();i++){JSONObject row=history.getJSONObject(i);String batch=row.getString("batch_id"),reason=row.getString("reason");int delta=row.getInt("delta");if(!batches.contains(batch)||(!row.isNull("roll_id")&&!rolls.contains(row.getString("roll_id")))||!Arrays.asList("added","adjusted","loaded","returned","roll_deleted").contains(reason)||Math.abs((long)delta)>999999)throw new IOException("库存历史引用或数量无效");if(reason.equals("roll_deleted")&&(!row.isNull("roll_id")||Math.abs(delta)!=1))throw new IOException("已删胶卷流水无效");if((reason.equals("loaded")&&(delta!=-1||row.isNull("roll_id")))||(reason.equals("returned")&&(delta!=1||row.isNull("roll_id"))))throw new IOException("装卷流水无效");if(reason.equals("loaded")){String roll=row.getString("roll_id");if(loaded.put(roll,batch)!=null)throw new IOException("装卷扣减流水重复");}else if(reason.equals("returned")){String roll=row.getString("roll_id");if(!returned.add(roll)||!batch.equals(loadedExpected.get(roll)))throw new IOException("返还流水重复或批次不符");}else if(!row.isNull("roll_id"))throw new IOException("普通库存调整不能绑定胶卷");row.getLong("created_at");totals.put(batch,totals.getOrDefault(batch,0L)+delta);}if(!loaded.equals(loadedExpected)||!returned.equals(returnedExpected))throw new IOException("装卷资料与库存流水不一致");
        for(String batch:batches)if(totals.getOrDefault(batch,0L)!=quantities.get(batch).longValue())throw new IOException("库存数量与历史流水不一致");
    }
    static void restore(Context context,LogStore store,Prepared p)throws Exception{
        validate(p);validateExtras(context,p.manifest);SQLiteDatabase db=store.getWritableDatabase();Map<String,Map<String,String>> maps=new HashMap<>();
        for(String table:TABLES){Map<String,String> map=new HashMap<>();maps.put(table,map);JSONArray rows=p.manifest.getJSONArray(table);for(int i=0;i<rows.length();i++)map.put(rows.getJSONObject(i).getString("id"),UUID.randomUUID().toString());}
        ScanStore scans=new ScanStore(context,store);ArrayList<File> installed=new ArrayList<>();boolean successful=false,committed=false,transaction=false;
        try{
            for(String table:new String[]{"scans","reference_photos"})for(Map.Entry<String,String> entry:maps.get(table).entrySet()){
                File target=scans.file(entry.getValue());if(target.exists())throw new IOException("恢复文件冲突");installed.add(target);
                try(InputStream in=new FileInputStream(p.files.get(entry.getKey()));FileOutputStream out=new FileOutputStream(target)){copyHash(in,out,p.files.get(entry.getKey()).length());out.getFD().sync();}
            }
            db.beginTransaction();transaction=true;
            for(String table:TABLES){HashSet<String> columns=new HashSet<>();try(Cursor c=db.rawQuery("SELECT * FROM "+table+" LIMIT 0",null)){columns.addAll(Arrays.asList(c.getColumnNames()));}
                JSONArray rows=p.manifest.getJSONArray(table);for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);if(row.length()!=columns.size())throw new IOException("备份字段不完整");ContentValues values=new ContentValues();
                    for(String column:columns){if(!row.has(column))throw new IOException("备份字段缺失");Object value=row.get(column);
                        if(column.equals("id"))value=maps.get(table).get((String)value);
                        else if(table.equals("artwork_links")&&column.equals("owner_id"))value=maps.get(row.getString("owner_kind").equals("batch")?"film_batches":"rolls").get((String)value);
                        else if(column.equals("roll_id")&&value!=JSONObject.NULL)value=maps.get("rolls").get((String)value);
                        else if((column.equals("camera_id")||column.equals("project_id"))&&value!=JSONObject.NULL)value=maps.get("catalog_entries").get((String)value);
                        else if(column.equals("batch_id")&&value!=JSONObject.NULL)value=maps.get("film_batches").get((String)value);
                        else if((column.equals("shot_id")||column.equals("previous_shot_id"))&&value!=JSONObject.NULL)value=maps.get("shots").get((String)value);
                        if(value==null||value==JSONObject.NULL)values.putNull(column);else if(value instanceof Float||value instanceof Double)values.put(column,((Number)value).doubleValue());else if(value instanceof Number)values.put(column,((Number)value).longValue());else if(value instanceof String)values.put(column,(String)value);else throw new IOException("备份字段类型无效");
                    }db.insertOrThrow(table,null,values);
                }
            }db.setTransactionSuccessful();successful=true;
        }finally{try{if(transaction)db.endTransaction();committed=successful;}finally{if(!committed)for(File file:installed)file.delete();}}
    }
    private static String copyHash(InputStream in,OutputStream out,long expected)throws Exception{
        MessageDigest hash=MessageDigest.getInstance("SHA-256");byte[] b=new byte[65536];long size=0;int n;while((n=in.read(b))!=-1){size+=n;if(size>expected)throw new IOException("文件长度变化");hash.update(b,0,n);out.write(b,0,n);}if(size!=expected)throw new IOException("文件不完整");StringBuilder result=new StringBuilder();for(byte v:hash.digest())result.append(String.format(Locale.ROOT,"%02x",v&255));return result.toString();
    }
}
