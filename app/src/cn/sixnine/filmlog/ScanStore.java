package cn.sixnine.filmlog;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.BitmapFactory;
import java.io.*;
import java.security.*;
import java.util.*;

/** App-owned copies; importing, replacing or removing a scan never writes its source. */
final class ScanStore {
    static final long MAX_BYTES=512L*1024*1024;
    static class Scan {String id,rollId,name,sha256,shotId;long bytes;boolean deleted;}
    private final LogStore store;
    private final File directory;
    ScanStore(Context context,LogStore store)throws IOException{
        this.store=store;directory=new File(context.getFilesDir(),"scans");
        if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("无法创建扫描文件目录");
    }
    File file(String id){UUID.fromString(id);return new File(directory,id+".jpg");}
    ArrayList<Scan> list(String roll){return list(roll,false);}
    ArrayList<Scan> list(String roll,boolean deleted){
        ArrayList<Scan> result=new ArrayList<>();
        try(Cursor c=store.getReadableDatabase().query("scans",null,"roll_id=? AND deleted_at IS "+(deleted?"NOT NULL":"NULL"),new String[]{roll},null,null,"created_at,rowid")){
            while(c.moveToNext()){Scan s=new Scan();s.id=LogStore.str(c,"id");s.rollId=LogStore.str(c,"roll_id");s.name=LogStore.str(c,"display_name");s.sha256=LogStore.str(c,"sha256");s.shotId=LogStore.str(c,"shot_id");s.deleted=deleted;s.bytes=c.getLong(c.getColumnIndexOrThrow("byte_count"));result.add(s);}
        }return result;
    }
    Scan importJpeg(String roll,String name,InputStream input)throws IOException{return importJpeg(roll,name,input,UUID.randomUUID().toString());}
    Scan importJpeg(String roll,String name,InputStream input,String id)throws IOException{
        UUID.fromString(id);store.roll(roll);
        for(Scan existing:list(roll))if(existing.id.equals(id))return existing;
        try(Cursor c=store.getReadableDatabase().rawQuery("SELECT id FROM scans WHERE id=?",new String[]{id})){
            if(c.moveToFirst())throw new IOException("导入副本已移除或属于其他胶卷，请重新选择文件");
        }
        File staged=new File(directory,id+".part"),target=file(id);boolean committed=false;long length=0;
        try{
            MessageDigest digest;try{digest=MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException e){throw new AssertionError(e);}
            try(FileOutputStream out=new FileOutputStream(staged)){
                byte[] buffer=new byte[65536];int n;
                while((n=input.read(buffer))!=-1){length+=n;if(length>MAX_BYTES)throw new IOException("单个扫描文件不能超过 512 MiB");out.write(buffer,0,n);digest.update(buffer,0,n);}out.getFD().sync();
            }
            // Validate the marker stream without changing the original copy or allocating image pixels.
            try(InputStream in=new FileInputStream(staged)){JpegCopy.clean(in,new OutputStream(){public void write(int b){}public void write(byte[] b,int o,int n){}});}
            BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeFile(staged.toString(),bounds);
            if(bounds.outWidth<=0||bounds.outHeight<=0)throw new IOException("不是可读取的 JPEG 扫描文件");
            if(!staged.renameTo(target))throw new IOException("扫描文件保存失败");
            StringBuilder hash=new StringBuilder();for(byte b:digest.digest())hash.append(String.format(Locale.ROOT,"%02x",b&255));
            ContentValues v=new ContentValues();v.put("id",id);v.put("roll_id",roll);v.put("display_name",name==null||name.trim().isEmpty()?"未命名.jpg":name);v.put("sha256",hash.toString());v.put("byte_count",length);v.put("created_at",System.currentTimeMillis());
            SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();try{store.roll(roll);db.insertOrThrow("scans",null,v);db.setTransactionSuccessful();}finally{db.endTransaction();}committed=true;
            Scan s=new Scan();s.id=id;s.rollId=roll;s.name=v.getAsString("display_name");s.sha256=hash.toString();s.bytes=length;s.shotId="";return s;
        }finally{if(!committed){staged.delete();target.delete();}}
    }
    /** Preview must include all current scans; a changed set rejects a stale save. Empty value means unassigned. */
    void associate(String roll,Map<String,String> assignments){
        SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();try{
            store.roll(roll);HashSet<String> ids=new HashSet<>();for(Scan s:list(roll))ids.add(s.id);
            if(!ids.equals(assignments.keySet()))throw new IllegalArgumentException("扫描列表已变化，请重新预览");
            HashSet<String> shots=new HashSet<>();
            for(String shot:assignments.values()){
                if(shot==null)throw new IllegalArgumentException("无效的关联");if(shot.isEmpty())continue;
                if(!shots.add(shot))throw new IllegalArgumentException("每张曝光只能关联一个扫描文件");
                if(!store.shot(shot).rollId.equals(roll))throw new IllegalArgumentException("不能关联其他胶卷的曝光");
            }
            ContentValues empty=new ContentValues();empty.putNull("shot_id");db.update("scans",empty,"roll_id=? AND deleted_at IS NULL",new String[]{roll});
            for(Map.Entry<String,String> pair:assignments.entrySet())if(!pair.getValue().isEmpty()){
                ContentValues v=new ContentValues();v.put("shot_id",pair.getValue());db.update("scans",v,"id=? AND roll_id=?",new String[]{pair.getKey(),roll});
            }
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    void remove(String roll,String id){
        SQLiteDatabase db=store.getWritableDatabase();db.execSQL("UPDATE scans SET previous_shot_id=shot_id,shot_id=NULL,deleted_at=? WHERE id=? AND roll_id=? AND deleted_at IS NULL",new Object[]{System.currentTimeMillis(),id,roll});
    }
    boolean restore(String roll,String id){
        SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();try{
            String previous;try(Cursor c=db.rawQuery("SELECT previous_shot_id FROM scans WHERE id=? AND roll_id=? AND deleted_at IS NOT NULL",new String[]{id,roll})){if(!c.moveToFirst())throw new IllegalArgumentException("扫描已恢复或不存在");previous=c.isNull(0)?"":c.getString(0);}
            if(!file(id).isFile())throw new IllegalArgumentException("扫描副本缺失，请重新导入原件");
            boolean recovered=false;
            if(!previous.isEmpty())try(Cursor c=db.rawQuery("SELECT id FROM shots WHERE id=? AND roll_id=? AND NOT EXISTS (SELECT 1 FROM scans WHERE shot_id=?)",new String[]{previous,roll,previous})){recovered=c.moveToFirst();}
            ContentValues v=new ContentValues();v.putNull("deleted_at");v.putNull("previous_shot_id");if(recovered)v.put("shot_id",previous);else v.putNull("shot_id");db.update("scans",v,"id=?",new String[]{id});db.setTransactionSuccessful();return recovered;
        }finally{db.endTransaction();}
    }
    void assignImported(String roll,String id,String shot){
        SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();try{
            if(!store.shot(shot).rollId.equals(roll))throw new IllegalArgumentException("曝光不属于这卷胶片");
            try(Cursor c=db.rawQuery("SELECT shot_id FROM scans WHERE id=? AND roll_id=? AND deleted_at IS NULL",new String[]{id,roll})){
                if(!c.moveToFirst()||(!c.isNull(0)&&!c.getString(0).equals(shot)))throw new IllegalArgumentException("扫描关联已变化");
            }
            try(Cursor c=db.rawQuery("SELECT id FROM scans WHERE shot_id=? AND id<>?",new String[]{shot,id})){if(c.moveToFirst())throw new IllegalArgumentException("曝光已有扫描；新副本保留为未关联文件");}
            ContentValues v=new ContentValues();v.put("shot_id",shot);db.update("scans",v,"id=? AND roll_id=? AND deleted_at IS NULL",new String[]{id,roll});db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    void replace(String roll,String oldId,String newId){
        SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();try{
            String shot;try(Cursor c=db.rawQuery("SELECT shot_id FROM scans WHERE id=? AND roll_id=? AND deleted_at IS NULL",new String[]{oldId,roll})){if(!c.moveToFirst())throw new IllegalArgumentException("被替换扫描已变化");shot=c.isNull(0)?"":c.getString(0);}
            try(Cursor c=db.rawQuery("SELECT id FROM scans WHERE id=? AND roll_id=? AND deleted_at IS NULL AND shot_id IS NULL",new String[]{newId,roll})){if(oldId.equals(newId)||!c.moveToFirst())throw new IllegalArgumentException("替换文件不可用");}
            if(!file(newId).isFile())throw new IllegalArgumentException("替换文件缺失");
            remove(roll,oldId);if(!shot.isEmpty()){ContentValues v=new ContentValues();v.put("shot_id",shot);db.update("scans",v,"id=?",new String[]{newId});}
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
}
