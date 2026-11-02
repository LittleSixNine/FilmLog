package cn.sixnine.filmlog;

import android.content.*;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Publishes only new composite images. The process-wide slot survives Activity changes. */
final class SharePosterOutput {
    private static final String ALBUM="Pictures/胶片随记/分享图/";
    private static final String PREFIX="胶片随记-分享图-";
    private static Job running;

    static synchronized Job active(){return running!=null&&!running.done?running:null;}

    static synchronized Job start(Context context,SharePosterData.Spec spec,Uri destination,boolean tree)throws IOException{
        if(running!=null&&!running.done)throw new IOException("上一次图片任务仍在处理，请稍候");
        if(spec.items.isEmpty())throw new IOException("请先选择照片");
        if(spec.width!=1440&&spec.width!=2400)throw new IOException("请选择快速或高清尺寸");
        if(destination==null&&Build.VERSION.SDK_INT<29)throw new IOException("请先选择保存位置");
        if(destination!=null&&!"content".equals(destination.getScheme()))throw new IOException("保存位置不是可用的系统文档");
        if(tree&&destination==null)throw new IOException("请选择输出文件夹");
        SharePosterLayout layout=new SharePosterLayout(spec.items.size(),spec.overview,spec.vertical);
        if(destination!=null&&!tree&&layout.pages!=1)throw new IOException("多页图片需要选择输出文件夹");
        for(SharePosterData.Item item:spec.items)if(destination!=null&&destination.equals(item.uri))throw new IOException("不能覆盖原照片，请另存为新图片");
        Job job=new Job(context.getApplicationContext(),spec,destination,tree,layout);
        running=job;
        try{new Thread(job::run,"share-poster-output").start();}catch(RuntimeException failure){running=null;throw failure;}
        return job;
    }

    static final class Job implements SharePosterRenderer.Control {
        final Context context;
        final SharePosterData.Spec spec;
        final Uri destination;
        final boolean tree;
        final SharePosterLayout layout;
        final String batch=UUID.randomUUID().toString();
        final long started=android.os.SystemClock.elapsedRealtime();
        final int total,pages;
        volatile int progress,page;
        volatile boolean done,success,cancelled;
        volatile String message="准备生成图片";
        volatile List<Uri> results=Collections.emptyList(),partialResults=Collections.emptyList();
        volatile long elapsedMillis;
        private JSONObject journal;
        private File journalFile,staging;
        private final ArrayList<Uri> complete=new ArrayList<>();

        Job(Context c,SharePosterData.Spec s,Uri d,boolean t,SharePosterLayout l){context=c;spec=s;destination=d;tree=t;layout=l;total=s.items.size();pages=l.pages;}
        void cancel(){if(!done){cancelled=true;message="正在停止，等待当前图片处理结束";}}
        @Override public void check()throws IOException{if(cancelled)throw new IOException("已取消生成");}
        @Override public void progress(int done,int count){progress=Math.min(total,done);if(!cancelled)message="正在绘制第 "+(page+1)+" / "+pages+" 页 · "+progress+" / "+total+" 张";}

        private void run(){
            Throwable failure=null;
            try{
                check();
                if(destination!=null&&!tree)requireEmpty(context,destination);
                File journalDir=journalDirectory(context);
                journalFile=new File(journalDir,batch+".json");
                journal=new JSONObject().put("version",1).put("batch",batch).put("mode",destination==null?"media":tree?"tree":"document")
                    .put("destination",destination==null?"":destination.toString()).put("width",spec.width).put("pages",new JSONArray()).put("finished",false);
                JSONArray entries=journal.getJSONArray("pages");
                for(int p=0;p<pages;p++)entries.put(new JSONObject().put("name",name(batch,p)).put("state","planned").put("uri",destination!=null&&!tree?destination.toString():""));
                writeJournal(journalFile,journal);
                if(destination!=null){
                    File root=cacheDirectory(context);
                    staging=new File(root,batch);
                    if(!staging.mkdir())throw new IOException("无法建立图片暂存目录");
                    for(page=0;page<pages;page++){
                        check();requireSpace(staging,Math.max(16L*1024*1024,(long)spec.width*spec.width*5));
                        File local=new File(staging,page+".jpg");
                        try(FileOutputStream out=new FileOutputStream(local)){writePage(out);out.getFD().sync();}
                        verifyFile(local,spec.width);
                    }
                    for(page=0;page<pages;page++){
                        check();message="正在保存第 "+(page+1)+" / "+pages+" 页";
                        JSONObject entry=entries.getJSONObject(page);
                        Uri target;
                        if(tree){
                            Uri parent=DocumentsContract.buildDocumentUriUsingTree(destination,DocumentsContract.getTreeDocumentId(destination));
                            target=DocumentsContract.createDocument(context.getContentResolver(),parent,"image/jpeg",entry.getString("name"));
                            if(target==null)throw new IOException("所选文件夹无法创建图片");
                        }else target=destination;
                        try{requireEmpty(context,target);}catch(Exception protectedTarget){entry.put("uri",target.toString()).put("state","protected");writeJournal(journalFile,journal);throw protectedTarget;}
                        entry.put("uri",target.toString()).put("state","created");writeJournal(journalFile,journal);
                        entry.put("state","writing");writeJournal(journalFile,journal);
                        try(InputStream in=new FileInputStream(new File(staging,page+".jpg"));OutputStream out=openOutput(context,target)){
                            byte[] buffer=new byte[65536];int n;while((n=in.read(buffer))!=-1){check();out.write(buffer,0,n);}out.flush();
                        }
                        verifyUri(context,target,spec.width);
                        entry.put("state","written");writeJournal(journalFile,journal);
                        complete.add(target);partialResults=immutable(complete);
                    }
                }else{
                    for(page=0;page<pages;page++){
                        check();requireSpace(android.os.Environment.getExternalStorageDirectory(),Math.max(16L*1024*1024,(long)spec.width*spec.width*5));JSONObject entry=entries.getJSONObject(page);
                        ContentValues values=new ContentValues();values.put(MediaStore.Images.Media.DISPLAY_NAME,entry.getString("name"));
                        values.put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg");values.put(MediaStore.Images.Media.RELATIVE_PATH,ALBUM);values.put(MediaStore.Images.Media.IS_PENDING,1);
                        Uri uri=context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);
                        if(uri==null)throw new IOException("无法创建相册图片");
                        entry.put("uri",uri.toString()).put("state","writing");writeJournal(journalFile,journal);
                        try(OutputStream out=openOutput(context,uri)){writePage(out);out.flush();}
                        verifyUri(context,uri,spec.width);
                        entry.put("state","written");writeJournal(journalFile,journal);
                    }
                    check();message="正在完成相册保存";
                    for(page=0;page<pages;page++){
                        check();JSONObject entry=entries.getJSONObject(page);Uri uri=Uri.parse(entry.getString("uri"));
                        ContentValues values=new ContentValues();values.put(MediaStore.Images.Media.IS_PENDING,0);
                        if(context.getContentResolver().update(uri,values,null,null)!=1)throw new IOException("相册发布未完成");
                        // The persisted writing state may lag publication; recovery always checks IS_PENDING.
                        entry.put("state","published");writeJournal(journalFile,journal);
                        complete.add(uri);partialResults=immutable(complete);
                    }
                }
                check();
                journal.put("finished",true);writeJournal(journalFile,journal);
                results=immutable(complete);progress=total;success=true;message="已保存 "+pages+" 张分享图";
                new AtomicFile(journalFile).delete();
            }catch(Throwable error){
                failure=error;
                if(journal!=null){
                    try{String cleanup=reconcile(context,journal,journalFile);partialResults=verifiedResults(journal);if(!cleanup.isEmpty())message=cleanup;}catch(Exception cleanup){error.addSuppressed(cleanup);}
                }
                String detail=cancelled?"已取消生成":safeError(error);
                if(!partialResults.isEmpty())detail+="；已完整保存 "+partialResults.size()+" 页，请检查保存位置";
                if(journalFile!=null&&journalFile.isFile())detail+="；有输出状态待核对，下次进入会再次检查";
                message=detail;
            }finally{
                cleanStaging(staging);
                elapsedMillis=android.os.SystemClock.elapsedRealtime()-started;
                synchronized(SharePosterOutput.class){done=true;if(running==this)running=null;}
                if(failure instanceof ThreadDeath)throw (ThreadDeath)failure;
            }
        }

        private void writePage(OutputStream destination)throws IOException{
            check();Bitmap bitmap=SharePosterRenderer.render(context,spec,page,spec.width,this);
            try{
                check();message="正在编码第 "+(page+1)+" / "+pages+" 页";
                OutputStream cancellable=new FilterOutputStream(destination){
                    @Override public void write(int b)throws IOException{check();out.write(b);}
                    @Override public void write(byte[] b,int offset,int length)throws IOException{check();out.write(b,offset,length);}
                };
                if(!bitmap.compress(Bitmap.CompressFormat.JPEG,92,cancellable))throw new IOException("图片编码未完成");
                check();
            }finally{bitmap.recycle();}
        }
    }

    /** Call off the main thread. Never restarts an interrupted render or deletes a published page. */
    static String recover(Context context)throws IOException{
        File directory=journalDirectory(context);File[] files=directory.listFiles((d,n)->n.endsWith(".json")||n.endsWith(".json.bak"));
        if(files==null)throw new IOException("无法读取分享图保存记录");
        ArrayList<String> messages=new ArrayList<>();int visited=0;Set<File> seen=new HashSet<>();
        for(File candidate:files){
            File file=candidate.getName().endsWith(".bak")?new File(directory,candidate.getName().substring(0,candidate.getName().length()-4)):candidate;if(!seen.add(file))continue;
            Job live=active();if(live!=null&&file.getName().equals(live.batch+".json"))continue;
            if(++visited>32){messages.add("另有较早的保存记录待核对");break;}
            try{
                JSONObject j=readJournal(file);String message=reconcile(context,j,file);if(!message.isEmpty())messages.add(message);
            }catch(Exception error){messages.add("有一批分享图的保存状态无法核对，请检查原保存位置；旧照片未作更改");}
        }
        cleanOldCache(context);
        return android.text.TextUtils.join("\n",messages);
    }

    private static String reconcile(Context context,JSONObject j,File file)throws Exception{
        validateJournal(j,file);
        String mode=j.getString("mode");boolean media=mode.equals("media");JSONArray pages=j.getJSONArray("pages");
        int retained=0,unknown=0;ContentResolver resolver=context.getContentResolver();
        for(int index=0;index<pages.length();index++){
            JSONObject entry=pages.getJSONObject(index);String state=entry.getString("state");
            if(state.equals("removed"))continue;
            entry.put("verified",false);
            if(state.equals("protected")){unknown++;continue;}
            Uri uri;
            try{uri=entry.optString("uri").isEmpty()?findCreated(context,j,entry):Uri.parse(entry.getString("uri"));}catch(Exception inaccessible){unknown++;continue;}
            if(uri==null){entry.put("state","removed");continue;}
            entry.put("uri",uri.toString());
            try{
                if(media){
                    String[] columns=Build.VERSION.SDK_INT>=30?new String[]{MediaStore.Images.Media.DISPLAY_NAME,MediaStore.Images.Media.IS_PENDING,MediaStore.Images.Media.RELATIVE_PATH,MediaStore.Images.Media.OWNER_PACKAGE_NAME}:new String[]{MediaStore.Images.Media.DISPLAY_NAME,MediaStore.Images.Media.IS_PENDING,MediaStore.Images.Media.RELATIVE_PATH};
                    try(Cursor c=resolver.query(uri,columns,null,null,null)){
                        if(c==null)throw new IOException("无法读取相册状态");
                        if(!c.moveToFirst()){entry.put("state","removed");continue;}
                        if(!entry.getString("name").equals(c.getString(0))||!ALBUM.equals(c.getString(2))||Build.VERSION.SDK_INT>=30&&!context.getPackageName().equals(c.getString(3)))throw new IOException("无法确认输出归属");
                        if(c.getInt(1)==0){verifyUri(context,uri,j.getInt("width"));entry.put("state","published").put("verified",true);retained++;continue;}
                    }
                    if(resolver.delete(uri,null,null)!=1)throw new IOException("暂存相册项未清理");
                    entry.put("state","removed");
                }else{
                    if(state.equals("written")||state.equals("published")){
                        verifyUri(context,uri,j.getInt("width"));entry.put("verified",true);retained++;continue;
                    }
                    if(!exists(context,uri)){entry.put("state","removed");continue;}
                    if(!DocumentsContract.deleteDocument(resolver,uri))throw new IOException("提供器不支持删除未完成图片");
                    entry.put("state","removed");
                }
            }catch(Exception error){entry.put("state",state);unknown++;}
        }
        if(retained==0&&unknown==0){new AtomicFile(file).delete();return "";}
        if(j.optBoolean("finished")&&retained==pages.length()&&unknown==0){new AtomicFile(file).delete();return "上次分享图已保存 "+retained+" 页，可在原保存位置查看。";}
        writeJournal(file,j);
        return "上次分享图：保留 "+retained+" 页完整输出"+(unknown>0?"，另有 "+unknown+" 页状态未知，请重新授权或检查原保存位置":"；其余未完成页已清理")+"。不会自动重复生成。";
    }

    private static Uri findCreated(Context context,JSONObject j,JSONObject page)throws Exception{
        String mode=j.getString("mode");String name=page.getString("name");ContentResolver resolver=context.getContentResolver();Uri found=null;
        if(mode.equals("media")){
            if(Build.VERSION.SDK_INT<29)throw new IOException("相册状态暂不可核对");
            String selection=MediaStore.Images.Media.DISPLAY_NAME+"=? AND "+MediaStore.Images.Media.RELATIVE_PATH+"=?";
            String[] arguments={name,ALBUM};if(Build.VERSION.SDK_INT>=30){selection+=" AND "+MediaStore.Images.Media.OWNER_PACKAGE_NAME+"=?";arguments=new String[]{name,ALBUM,context.getPackageName()};}
            Cursor foundRows;
            if(Build.VERSION.SDK_INT>=30){Bundle query=new Bundle();query.putString(ContentResolver.QUERY_ARG_SQL_SELECTION,selection);query.putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,arguments);query.putInt(MediaStore.QUERY_ARG_MATCH_PENDING,MediaStore.MATCH_INCLUDE);foundRows=resolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,new String[]{MediaStore.Images.Media._ID},query,null);}
            else foundRows=resolver.query(MediaStore.setIncludePending(MediaStore.Images.Media.EXTERNAL_CONTENT_URI),new String[]{MediaStore.Images.Media._ID},selection,arguments,null);
            try(Cursor c=foundRows){
                if(c==null)throw new IOException("相册未返回结果");while(c.moveToNext()){if(found!=null)throw new IOException("批次文件重名，无法安全清理");found=ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,c.getLong(0));}
            }
        }else if(mode.equals("tree")){
            Uri tree=Uri.parse(j.getString("destination"));Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree));
            try(Cursor c=resolver.query(children,new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME},null,null,null)){
                if(c==null)throw new IOException("文件夹未返回结果");while(c.moveToNext())if(name.equals(c.getString(1))){if(found!=null)throw new IOException("批次文件重名，无法安全清理");found=DocumentsContract.buildDocumentUriUsingTree(tree,c.getString(0));}
            }
        }else found=Uri.parse(j.getString("destination"));
        return found;
    }

    static void retainDestinationPermission(Context context,Uri uri,int resultFlags){
        if(uri==null||(resultFlags&Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)==0)return;
        int flags=resultFlags&(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        if(flags==0)return;
        try{context.getContentResolver().takePersistableUriPermission(uri,flags);}catch(SecurityException unsupported){/* The current session may still have a temporary grant. */}
    }

    static Intent shareIntent(List<Uri> images)throws IOException{
        if(images==null||images.isEmpty())throw new IOException("没有完整的分享图");
        ArrayList<Uri> uris=new ArrayList<>();for(Uri u:images){if(u==null||!"content".equals(u.getScheme()))throw new IOException("图片尚未保存到可分享的位置");uris.add(u);}
        Intent intent=new Intent(uris.size()==1?Intent.ACTION_SEND:Intent.ACTION_SEND_MULTIPLE).setType("image/jpeg").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if(uris.size()==1)intent.putExtra(Intent.EXTRA_STREAM,uris.get(0));else intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM,uris);
        ClipData clip=new ClipData("胶片分享图",new String[]{"image/jpeg"},new ClipData.Item(uris.get(0)));
        for(int i=1;i<uris.size();i++)clip.addItem(new ClipData.Item(uris.get(i)));intent.setClipData(clip);return intent;
    }

    private static String name(String batch,int page){return PREFIX+batch+String.format(Locale.ROOT,"-%03d.jpg",page+1);}
    private static List<Uri> immutable(List<Uri> values){return Collections.unmodifiableList(new ArrayList<>(values));}
    private static List<Uri> verifiedResults(JSONObject journal)throws JSONException{ArrayList<Uri> result=new ArrayList<>();JSONArray pages=journal.getJSONArray("pages");for(int i=0;i<pages.length();i++){JSONObject p=pages.getJSONObject(i);if(p.optBoolean("verified")&&(p.optString("state").equals("written")||p.optString("state").equals("published")))result.add(Uri.parse(p.getString("uri")));}return immutable(result);}
    private static File journalDirectory(Context c)throws IOException{return directory(new File(c.getFilesDir(),"share-poster-journals"));}
    private static File cacheDirectory(Context c)throws IOException{return directory(new File(c.getCacheDir(),"share-posters"));}
    private static File directory(File f)throws IOException{if(!f.isDirectory()&&!f.mkdirs())throw new IOException("无法建立分享图目录");return f;}
    private static void requireSpace(File where,long bytes)throws IOException{long available=where.getUsableSpace();if(available>0&&available<bytes)throw new IOException("可用空间不足，请腾出空间后重试");}
    private static OutputStream openOutput(Context c,Uri uri)throws IOException{OutputStream out=c.getContentResolver().openOutputStream(uri,"wt");if(out==null)throw new IOException("无法写入保存位置");return out;}
    private static boolean exists(Context c,Uri uri)throws IOException{try(Cursor rows=c.getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(rows==null)throw new IOException("提供器未返回文件状态");return rows.moveToFirst();}}
    private static void requireEmpty(Context c,Uri uri)throws IOException{
        try(Cursor rows=c.getContentResolver().query(uri,new String[]{OpenableColumns.SIZE},null,null,null)){
            if(rows!=null&&rows.moveToFirst()&&!rows.isNull(0)){if(rows.getLong(0)>0)throw new IOException("保存位置已有内容，请另存为新图片");if(rows.getLong(0)==0)return;}
        }
        try(InputStream in=c.getContentResolver().openInputStream(uri)){if(in==null)throw new IOException("无法确认保存位置是否为空");if(in.read()!=-1)throw new IOException("保存位置已有内容，请另存为新图片");}
    }
    private static void verifyUri(Context c,Uri uri,int width)throws IOException{try(InputStream in=c.getContentResolver().openInputStream(uri)){verify(in,width);}}
    private static void verifyFile(File f,int width)throws IOException{try(InputStream in=new FileInputStream(f)){verify(in,width);}}
    private static void verify(InputStream in,int width)throws IOException{
        if(in==null)throw new IOException("无法读回保存的图片");BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeStream(in,null,bounds);
        if(bounds.outWidth!=width||bounds.outHeight!=width*5/4||!"image/jpeg".equals(bounds.outMimeType))throw new IOException("保存的图片未通过完整尺寸校验");
    }
    private static void writeJournal(File file,JSONObject data)throws IOException{
        AtomicFile atomic=new AtomicFile(file);FileOutputStream out=null;
        try{out=atomic.startWrite();out.write(data.toString().getBytes(StandardCharsets.UTF_8));atomic.finishWrite(out);}catch(IOException error){if(out!=null)atomic.failWrite(out);throw error;}
    }
    private static JSONObject readJournal(File file)throws Exception{
        if(file.length()>4L*1024*1024)throw new IOException("保存记录过大");
        try(InputStream in=new AtomicFile(file).openRead();ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>4*1024*1024)throw new IOException("保存记录过大");out.write(buffer,0,n);}return new JSONObject(out.toString("UTF-8"));
        }
    }
    private static void validateJournal(JSONObject j,File file)throws Exception{
        if(j.getInt("version")!=1)throw new IOException("未知保存记录");String batch=j.getString("batch");if(!UUID.fromString(batch).toString().equals(batch)||!file.getName().equals(batch+".json"))throw new IOException("保存记录标识无效");
        String mode=j.getString("mode");if(!mode.equals("media")&&!mode.equals("tree")&&!mode.equals("document"))throw new IOException("保存方式无效");
        if(j.getInt("width")!=1440&&j.getInt("width")!=2400)throw new IOException("图片尺寸无效");
        if(!mode.equals("media")&&!"content".equals(Uri.parse(j.getString("destination")).getScheme()))throw new IOException("保存位置无效");
        JSONArray pages=j.getJSONArray("pages");for(int i=0;i<pages.length();i++){JSONObject p=pages.getJSONObject(i);if(!name(batch,i).equals(p.getString("name")))throw new IOException("文件名与批次不符");if(!p.optString("uri").isEmpty()&&!"content".equals(Uri.parse(p.getString("uri")).getScheme()))throw new IOException("图片位置无效");if(!Arrays.asList("planned","created","writing","written","published","removed","protected").contains(p.getString("state")))throw new IOException("图片状态无效");}
    }
    private static void cleanStaging(File directory){if(directory==null)return;File[] files=directory.listFiles();if(files!=null)for(File f:files)if(f.getName().matches("[0-9]+\\.jpg"))f.delete();directory.delete();}
    private static void cleanOldCache(Context c){
        SharePosterRenderer.cleanStagedSources(c);
        File root=new File(c.getCacheDir(),"share-posters");File[] files=root.listFiles();if(files==null)return;int visited=0;
        for(File f:files){if(++visited>32)break;Job live=active();if(live!=null&&f.getName().equals(live.batch))continue;try{UUID.fromString(f.getName());if(f.lastModified()<System.currentTimeMillis()-24L*60*60*1000)cleanStaging(f);}catch(IllegalArgumentException ignored){}}
    }
    private static String safeError(Throwable error){String value=error.getMessage();if(error instanceof OutOfMemoryError)return "内存余量不足，请改用快速尺寸或减少单张裁切";if(value==null||value.isEmpty())return "图片保存未完成，请检查来源与保存位置";return value.replace('\n',' ').substring(0,Math.min(160,value.length()));}
    private SharePosterOutput(){}
}
