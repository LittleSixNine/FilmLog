package cn.sixnine.filmlog;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import java.io.*;
import java.util.*;

/** Camera references stay separate from lab scans, even though their immutable files share storage. */
final class ReferencePhotos {
    static final long MAX_BYTES=20L*1024*1024;
    private static void id(String value){if(!UUID.fromString(value).toString().equals(value))throw new IllegalArgumentException("参考照片标识无效");}
    private static File retained(Context context,String token){id(token);return new File(context.getFilesDir(),"pending-reference-"+token+".jpg");}
    static File draft(Context context,String token){File durable=retained(context,token);return durable.isFile()?durable:new File(context.getCacheDir(),"meter-reference-"+token+".jpg");}
    static void retainDraft(Context context,String token)throws IOException{
        File target=retained(context,token);if(target.isFile()){if(target.length()<1||target.length()>MAX_BYTES)throw new IOException("已保留的参考照片草稿无效");return;}File source=draft(context,token),part=new File(target+".part");if(!source.isFile()||source.length()<1||source.length()>MAX_BYTES)throw new IOException("参考照片草稿不可用，未保存曝光");boolean ok=false;
        try{try(InputStream in=new FileInputStream(source);FileOutputStream out=new FileOutputStream(part)){byte[] b=new byte[65536];int n;long bytes=0;while((n=in.read(b))!=-1){bytes+=n;if(bytes>MAX_BYTES)throw new IOException("参考照片过大");out.write(b,0,n);}out.getFD().sync();}if(part.length()!=source.length()||!part.renameTo(target))throw new IOException("参考照片草稿无法保留");ok=true;}finally{if(!ok)part.delete();}
    }
    static void capture(Context context,String token,Bitmap bitmap)throws IOException{
        if(bitmap==null)throw new IOException("未取得参考照片，请重试或返回手动记录");File target=draft(context,token),part=new File(target+".part");if(target.exists()||part.exists())throw new IOException("参考照片草稿标识冲突");boolean kept=false;
        try{try(FileOutputStream out=new FileOutputStream(part)){if(!bitmap.compress(Bitmap.CompressFormat.JPEG,90,out))throw new IOException("参考照片写入失败");out.getFD().sync();}if(part.length()<1||part.length()>MAX_BYTES||!part.renameTo(target))throw new IOException("参考照片无法保存");kept=true;}finally{if(!kept)part.delete();}
    }
    static void discard(Context context,String token){if(token==null||token.isEmpty())return;for(File file:new File[]{retained(context,token),new File(context.getCacheDir(),"meter-reference-"+token+".jpg")}){file.delete();new File(file+".part").delete();}}
    static String forShot(LogStore logs,String shot){try(Cursor rows=logs.getReadableDatabase().query("reference_photos",new String[]{"id"},"shot_id=?",new String[]{shot},null,null,null)){return rows.moveToFirst()?rows.getString(0):"";}}
    static File file(Context context,LogStore logs,String token)throws IOException{id(token);try(Cursor row=logs.getReadableDatabase().query("reference_photos",new String[]{"shot_id"},"id=?",new String[]{token},null,null,null)){if(!row.moveToFirst())throw new IOException("参考照片记录不存在");logs.shot(row.getString(0));}File image=new ScanStore(context,logs).file(token);if(!image.isFile())throw new IOException("参考照片副本不可读取");return image;}
    static String record(Context context,LogStore logs,String token,String roll,long tap,int delta,int zone,String status,String aperture,String shutter,String lens,String memo,String ev,String filters)throws Exception{
        return record(context,logs,token,roll,tap,delta,zone,status,aperture,shutter,lens,memo,ev,filters,null);
    }
    static String record(Context context,LogStore logs,String token,String roll,long tap,int delta,int zone,String status,String aperture,String shutter,String lens,String memo,String ev,String filters,android.location.Location fix)throws Exception{
        if(fix!=null)LogStore.requireFix(fix);
        id(token);SQLiteDatabase db=logs.getWritableDatabase();File installed=null;boolean successful=false,committed=false;String result;
        db.beginTransaction();try{
            String existing="";try(Cursor row=db.query("reference_photos",new String[]{"shot_id"},"id=?",new String[]{token},null,null,null)){if(row.moveToFirst())existing=row.getString(0);}
            if(!existing.isEmpty()){LogStore.Shot old=logs.shot(existing);if(!old.rollId.equals(roll)||old.tapped!=tap||old.offset!=delta||old.zone!=zone||!old.aperture.equals(aperture)||!old.shutter.equals(shutter)||!old.lens.equals(lens)||!old.memo.equals(memo)||!old.ev.equals(ev)||!old.filters.equals(filters))throw new IllegalArgumentException("这次测光已经保存，参数已变化，请返回胶卷核对已有曝光");if(fix!=null&&old.lat==null)throw new IllegalArgumentException("已有曝光缺少定位，不能用当前位置覆盖");file(context,logs,token);result=existing;}
            else{
                ScanStore scans=new ScanStore(context,logs);File target=scans.file(token);try(Cursor collision=db.rawQuery("SELECT id FROM scans WHERE id=?",new String[]{token})){if(collision.moveToFirst()||target.exists())throw new IOException("参考照片标识已占用，已有照片未修改");}
                File pending=draft(context,token);if(!pending.isFile()||pending.length()<1||pending.length()>MAX_BYTES)throw new IOException("参考照片草稿不可用，请重新拍摄");
                result=fix==null?logs.insert(roll,tap,delta,zone,status,aperture,shutter,lens,memo,ev,filters):logs.recordLocated(token,roll,tap,delta,zone,aperture,shutter,lens,memo,ev,filters,fix);installed=target;
                ScanStore.Scan photo;try(InputStream input=new FileInputStream(pending)){photo=scans.importJpeg(roll,"参考照片.jpg",input,token);}
                // The temporary scan row is never visible outside this transaction.
                db.delete("scans","id=?",new String[]{token});ContentValues values=new ContentValues();values.put("id",token);values.put("shot_id",result);values.put("sha256",photo.sha256);values.put("byte_count",photo.bytes);values.put("created_at",tap);db.insertOrThrow("reference_photos",null,values);
            }db.setTransactionSuccessful();successful=true;
        }finally{try{db.endTransaction();committed=successful;}finally{if(!committed&&installed!=null)installed.delete();}}
        discard(context,token);return result;
    }
}
