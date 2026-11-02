package cn.sixnine.filmlog;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.provider.OpenableColumns;
import java.io.*;
import java.util.*;

final class ImportQueue {
    private static final Object LOCK=new Object();
    static void enqueue(Context context,LogStore logs,String roll,List<Uri> sources,String replacement)throws Exception{
        enqueue(context,logs,roll,sources,replacement,"queued");
    }
    private static void enqueue(Context context,LogStore logs,String roll,List<Uri> sources,String replacement,String state)throws Exception{
        if(!replacement.isEmpty()&&sources.size()!=1)throw new IOException("替换只能选择一张扫描");logs.roll(roll);ArrayList<ContentValues> records=new ArrayList<>();
        for(Uri uri:sources){String name="未命名.jpg";try(Cursor c=context.getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst())name=c.getString(0);}ContentValues v=new ContentValues();v.put("id",UUID.randomUUID().toString());v.put("roll_id",roll);v.put("uri",uri.toString());v.put("name",name==null?"未命名.jpg":name);v.put("state",state);v.put("replacement_id",replacement);v.put("created_at",System.currentTimeMillis());records.add(v);}
        SQLiteDatabase db=logs.getWritableDatabase();db.beginTransaction();try{logs.roll(roll);for(ContentValues record:records)db.insertOrThrow("import_jobs",null,record);db.setTransactionSuccessful();}finally{db.endTransaction();}
    }
    /** Draft jobs retain source access but create neither scans nor exposure associations. */
    static void preview(Context context,LogStore logs,String roll,List<Uri> sources,String replacement)throws Exception{
        synchronized(LOCK){enqueue(context,logs,roll,sources,replacement,"preview");}
    }
    static ArrayList<ScanStore.Scan> previews(LogStore logs,String roll){
        ArrayList<ScanStore.Scan> result=new ArrayList<>();
        try(Cursor c=logs.getReadableDatabase().rawQuery("SELECT id,name,target_shot_id FROM import_jobs WHERE roll_id=? AND state='preview' ORDER BY created_at,rowid",new String[]{roll})){
            while(c.moveToNext()){ScanStore.Scan s=new ScanStore.Scan();s.id=c.getString(0);s.rollId=roll;s.name=c.getString(1);s.shotId=c.getString(2);result.add(s);}
        }return result;
    }
    static String replacement(LogStore logs,String roll,String id){try(Cursor c=logs.getReadableDatabase().rawQuery("SELECT replacement_id FROM import_jobs WHERE id=? AND roll_id=? AND state='preview'",new String[]{id,roll})){return c.moveToFirst()?c.getString(0):"";}}
    static Uri previewUri(LogStore logs,String roll,String id){try(Cursor c=logs.getReadableDatabase().rawQuery("SELECT uri FROM import_jobs WHERE id=? AND roll_id=? AND state='preview'",new String[]{id,roll})){if(!c.moveToFirst())throw new IllegalArgumentException("待导入文件已变化");return Uri.parse(c.getString(0));}}
    static void savePreview(LogStore logs,String roll,Map<String,String> links){
        SQLiteDatabase db=logs.getWritableDatabase();db.beginTransaction();try{
            HashSet<String> ids=new HashSet<>();for(ScanStore.Scan s:previews(logs,roll))ids.add(s.id);
            if(!ids.equals(links.keySet()))throw new IllegalArgumentException("待导入列表已变化，请重新打开");
            for(Map.Entry<String,String> link:links.entrySet()){
                if(link.getValue()==null||(!link.getValue().isEmpty()&&!logs.shot(link.getValue()).rollId.equals(roll)))throw new IllegalArgumentException("曝光不属于这卷胶片");
                ContentValues v=new ContentValues();v.put("target_shot_id",link.getValue());db.update("import_jobs",v,"id=? AND state='preview'",new String[]{link.getKey()});
            }db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    static void removePreview(Context context,LogStore logs,String roll,String id){
        synchronized(LOCK){Uri uri=previewUri(logs,roll,id);logs.getWritableDatabase().delete("import_jobs","id=? AND roll_id=? AND state='preview'",new String[]{id,roll});releaseIfFinished(context,logs.getWritableDatabase(),uri.toString());}
    }
    static void cancelPreview(Context context,LogStore logs,String roll){
        synchronized(LOCK){SQLiteDatabase db=logs.getWritableDatabase();Set<String> uris=new HashSet<>();
            try(Cursor c=db.rawQuery("SELECT uri FROM import_jobs WHERE roll_id=? AND state='preview'",new String[]{roll})){while(c.moveToNext())uris.add(c.getString(0));}
            db.delete("import_jobs","roll_id=? AND state='preview'",new String[]{roll});
            for(String uri:uris)releaseIfFinished(context,db,uri);
        }
    }
    static void confirmPreview(LogStore logs,String roll){
        synchronized(LOCK){SQLiteDatabase db=logs.getWritableDatabase();db.beginTransaction();try{
            logs.roll(roll);HashSet<String> used=new HashSet<>();
            try(Cursor c=db.rawQuery("SELECT shot_id FROM scans WHERE roll_id=? AND deleted_at IS NULL AND shot_id IS NOT NULL",new String[]{roll})){while(c.moveToNext())used.add(c.getString(0));}
            try(Cursor c=db.rawQuery("SELECT target_shot_id FROM import_jobs WHERE roll_id=? AND state IN ('queued','running','failed') AND target_shot_id<>''",new String[]{roll})){while(c.moveToNext())used.add(c.getString(0));}
            for(ScanStore.Scan scan:previews(logs,roll))if(!scan.shotId.isEmpty()){
                if(!logs.shot(scan.shotId).rollId.equals(roll)||!used.add(scan.shotId))throw new IllegalArgumentException("曝光已有关联或重复选择，请调整后再导入");
            }
            db.execSQL("UPDATE import_jobs SET state='queued' WHERE roll_id=? AND state='preview'",new Object[]{roll});db.setTransactionSuccessful();
        }finally{db.endTransaction();}}
    }
    static void dismissFailures(Context context,LogStore logs,String roll){
        synchronized(LOCK){SQLiteDatabase db=logs.getWritableDatabase();Set<String> uris=new HashSet<>();
            try(Cursor c=db.rawQuery("SELECT uri FROM import_jobs WHERE roll_id=? AND state='failed'",new String[]{roll})){while(c.moveToNext())uris.add(c.getString(0));}
            db.execSQL("UPDATE import_jobs SET state='cancelled' WHERE roll_id=? AND state='failed'",new Object[]{roll});
            for(String uri:uris)releaseIfFinished(context,db,uri);
        }
    }
    static void releaseIfFinished(Context context,SQLiteDatabase db,String uri){
        try(Cursor c=db.rawQuery("SELECT COUNT(*) FROM import_jobs WHERE uri=? AND state IN ('preview','queued','running','failed')",new String[]{uri})){c.moveToFirst();if(c.getInt(0)==0)try{context.getContentResolver().releasePersistableUriPermission(Uri.parse(uri),Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(RuntimeException ignored){}}
    }

    static int count(LogStore logs,String roll,String states){try(Cursor c=logs.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM import_jobs WHERE roll_id=? AND state IN ("+states+")",new String[]{roll})){c.moveToFirst();return c.getInt(0);}}
    static String run(Context context,LogStore logs,String roll,boolean retry)throws Exception{
        synchronized(LOCK){SQLiteDatabase db=logs.getWritableDatabase();if(retry)db.execSQL("UPDATE import_jobs SET state='queued',error='' WHERE roll_id=? AND state='failed'",new Object[]{roll});
            ArrayList<String[]> jobs=new ArrayList<>();try(Cursor c=db.rawQuery("SELECT id,uri,name,replacement_id,target_shot_id FROM import_jobs WHERE roll_id=? AND state IN ('queued','running') ORDER BY created_at,rowid",new String[]{roll})){while(c.moveToNext())jobs.add(new String[]{c.getString(0),c.getString(1),c.getString(2),c.getString(3),c.getString(4)});}
            ScanStore files=new ScanStore(context,logs);int completed=0,failed=0;
            for(String[] job:jobs){String id=job[0];db.execSQL("UPDATE import_jobs SET state='running',error='' WHERE id=?",new Object[]{id});
                try{
                    boolean exists=false;for(ScanStore.Scan scan:files.list(roll))if(scan.id.equals(id)){exists=true;break;}
                    if(!exists)try(InputStream in=context.getContentResolver().openInputStream(Uri.parse(job[1]))){if(in==null)throw new IOException("文件不可读取");files.importJpeg(roll,job[2],in,id);}
                    db.beginTransaction();try{if(!job[3].isEmpty())files.replace(roll,job[3],id);else if(!job[4].isEmpty())files.assignImported(roll,id,job[4]);db.execSQL("UPDATE import_jobs SET state='done',error='' WHERE id=?",new Object[]{id});db.setTransactionSuccessful();}finally{db.endTransaction();}completed++;
                    releaseIfFinished(context,db,job[1]);

                }catch(Exception e){ContentValues v=new ContentValues();v.put("state","failed");v.put("error",e.getMessage()==null?e.getClass().getSimpleName():e.getMessage());db.update("import_jobs",v,"id=?",new String[]{id});failed++;}
            }return "本次完成 "+completed+" 个，失败 "+failed+" 个。已完成文件保留；失败任务可重试或重新选择原件。";
        }
    }
    static String errors(LogStore logs,String roll){StringBuilder text=new StringBuilder();try(Cursor c=logs.getReadableDatabase().rawQuery("SELECT name,error FROM import_jobs WHERE roll_id=? AND state='failed' ORDER BY created_at LIMIT 8",new String[]{roll})){while(c.moveToNext())text.append(c.getString(0)).append("：").append(c.getString(1)).append('\n');}return text.toString();}
}
