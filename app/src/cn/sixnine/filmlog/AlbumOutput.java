package cn.sixnine.filmlog;

import android.content.*;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import java.io.*;
import java.util.*;

/** Only touches MediaStore rows created by this export; never edits imported originals. */
final class AlbumOutput {
    private static final String JOURNAL="album-export";
    static synchronized int save(Context context,LogStore logs,String roll,boolean gps)throws Exception{
        if(Build.VERSION.SDK_INT<29)throw new IOException("此系统请使用整卷 ZIP 保存扫描");
        recover(context);
        ScanStore files=new ScanStore(context,logs);LogStore.Roll record=logs.roll(roll);
        ArrayList<ScanStore.Scan> selected=new ArrayList<>();
        for(ScanStore.Scan scan:files.list(roll))if(!scan.shotId.isEmpty())selected.add(scan);
        if(selected.isEmpty())throw new IOException("没有已关联扫描可保存");
        selected.sort(Comparator.comparingInt(s->logs.shot(s.shotId).frame));
        ContentResolver resolver=context.getContentResolver();
        SharedPreferences journal=context.getSharedPreferences(JOURNAL,0);
        Set<String> created=new HashSet<>();
        String batch=UUID.randomUUID().toString().substring(0,8);
        try{
            for(ScanStore.Scan scan:selected){
                LogStore.Shot shot=logs.shot(scan.shotId);
                File image=ScanExport.create(context,files.file(scan.id),record,shot,gps);
                try{
                    ContentValues values=new ContentValues();
                    values.put(MediaStore.Images.Media.DISPLAY_NAME,String.format(Locale.ROOT,"胶片随记-%s-%03d.jpg",batch,shot.frame));
                    values.put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg");
                    values.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/胶片随记");
                    values.put(MediaStore.Images.Media.DATE_TAKEN,shot.captured);
                    values.put(MediaStore.Images.Media.IS_PENDING,1);
                    Uri uri=resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);
                    if(uri==null)throw new IOException("无法建立相册照片");
                    created.add(uri.toString());
                    if(!journal.edit().putStringSet("uris",new HashSet<>(created)).commit()){
                        resolver.delete(uri,null,null);throw new IOException("无法记录导出进度");
                    }
                    try(InputStream in=new FileInputStream(image);OutputStream out=resolver.openOutputStream(uri,"w")){
                        if(out==null)throw new IOException("无法写入相册");
                        byte[] buffer=new byte[65536];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);
                    }
                }finally{image.delete();}
            }
            ContentValues published=new ContentValues();published.put(MediaStore.Images.Media.IS_PENDING,0);
            for(String value:created)if(resolver.update(Uri.parse(value),published,null,null)!=1)throw new IOException("无法完成相册保存");
            if(!journal.edit().remove("uris").commit())throw new IOException("无法完成导出记录");
            return created.size();
        }catch(Exception error){
            try{recover(context);}catch(Exception cleanup){error.addSuppressed(cleanup);}
            throw error;
        }
    }
    static synchronized void recover(Context context)throws IOException{
        if(Build.VERSION.SDK_INT<29)return;
        SharedPreferences journal=context.getSharedPreferences(JOURNAL,0);
        Set<String> pending=new HashSet<>(journal.getStringSet("uris",Collections.emptySet()));
        for(String value:new HashSet<>(pending)){
            try{context.getContentResolver().delete(Uri.parse(value),null,null);pending.remove(value);}
            catch(RuntimeException error){/* Retain the URI so cleanup can be retried. */}
        }
        if(!journal.edit().putStringSet("uris",pending).commit()||!pending.isEmpty())throw new IOException("上次相册导出尚未清理，请稍后重试");
    }
}
