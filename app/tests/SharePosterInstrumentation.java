package cn.sixnine.filmlog;

import android.app.Instrumentation;
import android.content.*;
import android.database.Cursor;
import android.graphics.*;
import android.net.Uri;
import android.os.Bundle;
import android.os.Debug;
import android.os.SystemClock;
import android.provider.MediaStore;
import androidx.exifinterface.media.ExifInterface;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import org.json.*;

/** Isolated synthetic rendering fixtures; timings are smoke evidence, never a phone benchmark. */
public final class SharePosterInstrumentation extends Instrumentation {
    private int checks;
    private Context context;
    private File evidence;
    private final JSONArray timings=new JSONArray();
    private static final SharePosterRenderer.Control READY=new SharePosterRenderer.Control(){public void check(){}public void progress(int done,int total){}};
    private void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){
        Bundle result=new Bundle();boolean passed=false;
        try{
            check(getTargetContext().getPackageName().endsWith(".tests"),"isolated test package required");
            context=new WorkspaceContext(getTargetContext(),UUID.randomUUID().toString());
            evidence=new File(getTargetContext().getFilesDir(),"share-poster-evidence");
            check(evidence.isDirectory()||evidence.mkdirs(),"evidence directory");
            File source=fixture("source.jpg",960,640,false);
            ExifInterface originalExif=new ExifInterface(source.toString());originalExif.setAttribute(ExifInterface.TAG_GPS_LATITUDE,"31/1,0/1,0/1");originalExif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF,"N");originalExif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL,"2026:10:27 12:34:56");originalExif.saveAttributes();
            byte[] hash=hash(source);
            sources(source);
            matrix(source);
            orientation();
            edgeCases(source);
            check(Arrays.equals(hash,hash(source)),"input source hash unchanged");
            try(Writer out=new OutputStreamWriter(new FileOutputStream(new File(evidence,"render-smoke.json")),"UTF-8")){out.write(new JSONObject().put("synthetic",true).put("device_benchmark",false).put("runs",timings).toString(2));}
            result.putString("stream","PASS "+checks+" share poster assertions: source selection/date/half-frame/isolation; layout and native render matrix; EXIF 1–8 and mirror; JPEG/PNG/content URI; source preservation; cancellation/missing image/invalid text. Synthetic timings only.\n");passed=true;
        }catch(Throwable error){result.putString("stream",android.util.Log.getStackTraceString(error));}
        finish(passed?-1:0,result);
    }
    private File fixture(String name,int width,int height,boolean transparent)throws Exception{
        File target=new File(context.getCacheDir(),name);Bitmap bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(bitmap);try{Paint paint=new Paint(3);
            canvas.drawColor(transparent?Color.TRANSPARENT:0xffeee4ca);
            paint.setShader(new LinearGradient(0,0,width,height,new int[]{0xffa53e32,0xffe7c983,0xff3e6759},null,Shader.TileMode.CLAMP));
            canvas.drawRect(transparent?width*.25f:0,transparent?height*.25f:0,transparent?width*.75f:width,transparent?height*.75f:height,paint);paint.setShader(null);
            if(!transparent){for(int y=0;y<height;y+=47)for(int x=0;x<width;x+=61){paint.setColor(0xff000000|(((x*31+y*19)&255)<<16)|(((x*7+y*13)&255)<<8)|((x+y*11)&255));canvas.drawRect(x,y,x+18,y+13,paint);}paint.setColor(Color.WHITE);paint.setTextSize(width*.12f);canvas.drawText("FILM",width*.12f,height*.55f,paint);}
            try(OutputStream out=new FileOutputStream(target)){check(bitmap.compress(transparent?Bitmap.CompressFormat.PNG:Bitmap.CompressFormat.JPEG,95,out),"write synthetic source");}
        }finally{canvas.setBitmap(null);bitmap.recycle();}
        return target;
    }
    private void sources(File source)throws Exception{
        try(LogStore logs=new LogStore(context)){
            String roll=new CatalogStore(logs).manual(UUID.randomUUID().toString(),"分享图核验","Lucky 200","测试相机",200,72,"35 mm","","");
            new CatalogStore(logs).halfFrame(roll,true);
            long t=Instant.parse("2026-10-27T23:30:00Z").toEpochMilli();
            String first=logs.insert(roll,t,0,120,"denied","","","","","",""),second=logs.insert(roll,t,0,-480,"denied","","","","","","");
            logs.getWritableDatabase().execSQL("UPDATE shots SET frame_number=1000 WHERE id=?",new Object[]{second});
            ScanStore store=new ScanStore(context,logs);ScanStore.Scan a,b,unlinked;
            try(InputStream in=new FileInputStream(source)){a=store.importJpeg(roll,"first.jpg",in);}
            try(InputStream in=new FileInputStream(source)){b=store.importJpeg(roll,"second.jpg",in);}
            try(InputStream in=new FileInputStream(source)){unlinked=store.importJpeg(roll,"unlinked.jpg",in);}
            LinkedHashMap<String,String> links=new LinkedHashMap<>();links.put(a.id,first);links.put(b.id,second);links.put(unlinked.id,"");store.associate(roll,links);
            String token=UUID.randomUUID().toString();Bitmap reference=BitmapFactory.decodeFile(source.toString());
            try{ReferencePhotos.capture(context,token,reference);}finally{reference.recycle();}
            String referenceShot=ReferencePhotos.record(context,logs,token,roll,t+1000,0,120,"denied","","","","","","");
            byte[] beforeA=hash(store.file(a.id));int beforeCount=logs.roll(roll).count;
            SharePosterSources.Result loaded=SharePosterSources.loadRoll(context,roll);
            check(loaded.vertical&&loaded.title.equals("分享图核验")&&loaded.details.equals("Lucky 200 · 测试相机"),"roll presentation metadata");
            check(loaded.items.size()==4,"three scans plus optional reference");
            check(loaded.items.get(0).id.equals(a.id)&&loaded.items.get(1).id.equals(b.id)&&loaded.items.get(2).id.equals(unlinked.id),"frame order then unlinked");
            check(loaded.items.get(0).frame==1&&loaded.items.get(1).frame==1000,"frame gaps are not padded");
            check(loaded.items.get(2).frame==0&&loaded.items.get(2).selected,"unlinked scan selected with unknown frame");
            check(loaded.items.get(3).reference&&!loaded.items.get(3).selected&&loaded.items.get(3).id.equals(token),"reference requires explicit selection");
            check(loaded.date.isEmpty(),"unknown date hides group date");
            loaded.items.get(2).selected=false;
            check(SharePosterSources.date(loaded.items).equals("2026-10-27 — 2026-10-28"),"each photo saved timezone determines date");
            loaded.items.get(1).selected=false;
            check(SharePosterSources.date(loaded.items).equals("2026-10-28"),"single date after selection");
            check(logs.roll(roll).count==beforeCount&&logs.shot(referenceShot).frame==1001,"reading sources preserves records");
            check(Arrays.equals(beforeA,hash(store.file(a.id))),"reading sources preserves scan bytes");
            File held=new File(store.file(b.id)+".held");check(store.file(b.id).renameTo(held),"simulate missing scan");
            try{SharePosterSources.Result missing=SharePosterSources.loadRoll(context,roll);boolean found=false;for(SharePosterData.Item item:missing.items)if(item.id.equals(b.id)){found=true;check(!item.selected,"missing file not silently exported");}check(found&&missing.summary.contains("文件缺失"),"missing source retains identifiable candidate");}
            finally{check(held.renameTo(store.file(b.id)),"restore fixture source");}
            store.remove(roll,unlinked.id);SharePosterSources.Result deleted=SharePosterSources.loadRoll(context,roll);
            for(SharePosterData.Item item:deleted.items)check(!item.id.equals(unlinked.id),"recently deleted scan excluded");
            check(deleted.date.equals("2026-10-27 — 2026-10-28"),"reference not selected does not hide scan dates");
            Context other=new WorkspaceContext(getTargetContext(),UUID.randomUUID().toString());
            try{SharePosterSources.loadRoll(other,roll);throw new AssertionError("cross workspace roll accepted");}catch(IllegalStateException expected){checks++;}
            try(Writer out=new OutputStreamWriter(new FileOutputStream(new File(evidence,"source-fixture.json")),"UTF-8")){out.write(new JSONObject().put("workspace",((WorkspaceContext)context).workspace).put("roll",roll).put("real_user_data",false).toString());}
        }
    }
    private ArrayList<SharePosterData.Item> items(File source,int count){ArrayList<SharePosterData.Item> result=new ArrayList<>();for(int i=0;i<count;i++)result.add(new SharePosterData.Item("fixture-"+i,"照片 "+(i+1),source,null,i+1,1793143800000L,480,false));return result;}
    private SharePosterData.Spec spec(List<SharePosterData.Item> items,boolean overview,boolean vertical,int width){return new SharePosterData.Spec(items,overview,vertical,true,"Lucky 200","SIXNINE","相机 · 旅行","2026-10-28",width);}
    private void matrix(File source)throws Exception{
        for(boolean vertical:new boolean[]{false,true})for(boolean overview:new boolean[]{false,true})for(int count:new int[]{1,6,36,38,72}){
            SharePosterData.Spec spec=spec(items(source,count),overview,vertical,1440);SharePosterLayout layout=new SharePosterLayout(count,overview,vertical);int drawn=0;
            for(int page=0;page<layout.pages;page++){
                final int[] reported={-1};SharePosterRenderer.Control control=new SharePosterRenderer.Control(){public void check(){}public void progress(int done,int total){if(total!=count)throw new AssertionError("whole group progress total");reported[0]=done;}};
                Bitmap bitmap=SharePosterRenderer.render(context,spec,page,720,control);
                try{check(bitmap.getWidth()==720&&bitmap.getHeight()==900,"preview dimensions");check(bitmap.getPixel(0,0)==Color.WHITE,"paper white");
                    for(int i=layout.start(page);i<layout.end(page);i++){SharePosterLayout.Box box=layout.photo(i,page,720);int pixel=bitmap.getPixel(Math.round((box.left+box.right)/2),Math.round((box.top+box.bottom)/2));check((pixel&0xffffff)!=0x191919,"photo drawn "+i);drawn++;}
                    check(reported[0]==layout.end(page),"progress absolute photo index");
                }finally{bitmap.recycle();}
            }
            check(drawn==count,"all selected photos rendered exactly once");
        }
        for(int[] config:new int[][]{{38,0,1,1440},{38,0,1,2400},{6,0,0,1440},{72,1,1,1440},{1,1,0,1440}}){
            int count=config[0],width=config[3];boolean vertical=config[1]==1,overview=config[2]==1;SharePosterData.Spec spec=spec(items(source,count),overview,vertical,width);
            long begin=SystemClock.elapsedRealtimeNanos();Bitmap bitmap;try{bitmap=SharePosterRenderer.render(context,spec,0,width,READY);}catch(IOException lowMemory){check(width==2400&&Runtime.getRuntime().maxMemory()<80L*1024*1024&&lowMemory.getMessage().contains("内存"),"small-heap device explicitly rejects oversized output");continue;}File output=new File(evidence,"poster-"+count+"-"+(vertical?"vertical":"horizontal")+"-"+(overview?"overview":"selected")+"-"+width+".jpg");
            try{check(bitmap.getWidth()==width&&bitmap.getHeight()==width*5/4,"final output dimensions");try(OutputStream out=new FileOutputStream(output)){check(bitmap.compress(Bitmap.CompressFormat.JPEG,92,out),"encode final JPEG");}}
            finally{bitmap.recycle();}
            long millis=(SystemClock.elapsedRealtimeNanos()-begin)/1000000;
            BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeFile(output.toString(),bounds);check(bounds.outWidth==width&&bounds.outHeight==width*5/4,"encoded dimensions");
            ExifInterface exif=new ExifInterface(output.toString());check(exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE)==null&&exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)==null,"poster does not inherit source metadata");
            timings.put(new JSONObject().put("count",count).put("width",width).put("vertical",vertical).put("overview",overview).put("elapsed_ms",millis).put("native_after_bytes",Debug.getNativeHeapAllocatedSize()).put("jpeg_bytes",output.length()));
        }
    }
    private void orientation()throws Exception{
        int[] colors={0xffe52f24,0xff29bd4c,0xff315ed8,0xffe6d326};
        int[][] mapping={{0,1,2,3},{1,0,3,2},{3,2,1,0},{2,3,0,1},{0,2,1,3},{2,0,3,1},{3,1,2,0},{1,3,0,2}};
        for(int orientation=1;orientation<=8;orientation++){
            File file=new File(context.getCacheDir(),"orientation-"+orientation+".jpg");Bitmap source=Bitmap.createBitmap(120,80,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(source);Paint p=new Paint();
            for(int quadrant=0;quadrant<4;quadrant++){p.setColor(colors[quadrant]);c.drawRect((quadrant%2)*60,(quadrant/2)*40,(quadrant%2+1)*60,(quadrant/2+1)*40,p);}
            try(OutputStream out=new FileOutputStream(file)){source.compress(Bitmap.CompressFormat.JPEG,100,out);}finally{source.recycle();}
            ExifInterface exif=new ExifInterface(file.toString());exif.setAttribute(ExifInterface.TAG_ORIENTATION,String.valueOf(orientation));exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE,"31/1,0/1,0/1");exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF,"N");exif.saveAttributes();byte[] original=hash(file);
            SharePosterData.Spec spec=spec(items(file,1),false,false,1440);SharePosterLayout layout=new SharePosterLayout(1,false,false);SharePosterLayout.Box box=layout.photo(0,0,720);Bitmap result=SharePosterRenderer.render(context,spec,0,720,READY);
            try{float sourceW=orientation>=5?80:120,sourceH=orientation>=5?120:80,scale=Math.min(box.width()/sourceW,box.height()/sourceH),w=sourceW*scale,h=sourceH*scale,x=(box.left+box.right-w)/2,y=(box.top+box.bottom-h)/2;
                for(int quadrant=0;quadrant<4;quadrant++){int px=result.getPixel(Math.round(x+w*(quadrant%2==0?.25f:.75f)),Math.round(y+h*(quadrant/2==0?.25f:.75f)));check(closeColor(px,colors[mapping[orientation-1][quadrant]]),"EXIF "+orientation+" quadrant "+quadrant);}
            }finally{result.recycle();}
            check(Arrays.equals(original,hash(file)),"EXIF source untouched "+orientation);
        }
    }
    private boolean closeColor(int a,int b){return Math.abs(Color.red(a)-Color.red(b))<30&&Math.abs(Color.green(a)-Color.green(b))<30&&Math.abs(Color.blue(a)-Color.blue(b))<30;}
    private void edgeCases(File source)throws Exception{
        try{SharePosterRenderer.render(context,spec(Collections.emptyList(),false,false,1440),0,720,READY);throw new AssertionError("empty poster accepted");}catch(IOException expected){checks++;}
        ArrayList<SharePosterData.Item> list=items(source,2);list.get(0).fill=true;list.get(0).cropX=0;list.get(1).fill=true;list.get(1).cropX=1;
        SharePosterData.Spec frozen=spec(list,false,true,1440);list.get(0).cropX=1;Collections.reverse(list);check(frozen.items.get(0).id.equals("fixture-0")&&frozen.items.get(0).cropX==0,"snapshot owns order and crop copies");
        Bitmap crop=SharePosterRenderer.render(context,frozen,0,720,READY);crop.recycle();checks++;
        try{SharePosterRenderer.render(context,spec(items(source,6),false,false,1440),0,720,new SharePosterRenderer.Control(){public void check()throws IOException{throw new IOException("cancel-test");}public void progress(int d,int t){}});throw new AssertionError("cancel ignored");}catch(IOException expected){checks++;}
        File missing=new File(context.getCacheDir(),"missing-"+UUID.randomUUID()+".jpg");
        try{SharePosterRenderer.render(context,spec(items(missing,1),false,false,1440),0,720,READY);throw new AssertionError("missing source accepted");}catch(IOException expected){checks++;}
        File invalid=new File(context.getCacheDir(),"bad.jpg");try(OutputStream out=new FileOutputStream(invalid)){out.write(new byte[]{1,2,3});}
        try{SharePosterRenderer.render(context,spec(items(invalid,1),false,false,1440),0,720,READY);throw new AssertionError("corrupt source accepted");}catch(IOException expected){checks++;}
        File png=fixture("transparent.png",640,640,true);SharePosterData.Spec transparent=spec(items(png,1),false,false,1440);Bitmap composited=SharePosterRenderer.render(context,transparent,0,720,READY);
        try{SharePosterLayout.Box box=new SharePosterLayout(1,false,false).photo(0,0,720);check((composited.getPixel(Math.round(box.left+box.width()*.4f),Math.round(box.top+box.height()*.1f))&0xffffff)==0x191919,"transparent PNG composites on film background");}finally{composited.recycle();}
        File wide=fixture("panorama.jpg",2400,240,false);Bitmap panorama=SharePosterRenderer.render(context,spec(items(wide,1),false,false,1440),0,720,READY);panorama.recycle();checks++;
        File large=fixture("12mp-fast.jpg",4000,3000,false);Bitmap fast=SharePosterRenderer.render(context,spec(items(large,1),false,false,1440),0,1440,READY);check(fast.getWidth()==1440,"normal12MP single photo remains available in fast mode");fast.recycle();
        StringBuilder longTitle=new StringBuilder();for(int i=0;i<500;i++)longTitle.append("超长");
        try{SharePosterRenderer.render(context,new SharePosterData.Spec(items(source,1),false,false,true,longTitle.toString(),"","","",1440),0,720,READY);throw new AssertionError("overflowing title accepted");}catch(IOException|IllegalArgumentException expected){checks++;}
        if(android.os.Build.VERSION.SDK_INT>=29){ContentValues values=new ContentValues();values.put(MediaStore.Images.Media.DISPLAY_NAME,"poster-fixture-"+UUID.randomUUID()+".png");values.put(MediaStore.Images.Media.MIME_TYPE,"image/png");values.put(MediaStore.Images.Media.IS_PENDING,1);Uri uri=context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);check(uri!=null,"content URI fixture");
            try{try(InputStream in=new FileInputStream(png);OutputStream out=context.getContentResolver().openOutputStream(uri)){if(out==null)throw new IOException("fixture stream");byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);}
                SharePosterData.Item item=new SharePosterData.Item("uri-fixture","PNG URI",null,uri,0,0,0,false);Bitmap decoded=SharePosterRenderer.render(context,spec(Collections.singletonList(item),false,false,1440),0,720,READY);decoded.recycle();checks++;
            }finally{context.getContentResolver().delete(uri,null,null);}
        }
    }
    private byte[] hash(File file)throws Exception{MessageDigest digest=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(file)){byte[] bytes=new byte[8192];int n;while((n=in.read(bytes))!=-1)digest.update(bytes,0,n);}return digest.digest();}
}
