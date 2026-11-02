package cn.sixnine.filmlog;

import android.content.Context;
import android.graphics.*;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.system.Os;
import android.system.OsConstants;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import androidx.exifinterface.media.ExifInterface;
import java.io.*;
import java.util.*;

/** One software canvas and one sampled photo at a time; sources are never rewritten. */
final class SharePosterRenderer {
    interface Control {void check()throws IOException;void progress(int done,int total);}
    private static final Set<File> STAGED=Collections.synchronizedSet(new HashSet<File>());
    private static final long MIB=1024L*1024,MAX_SOURCE=512*MIB;
    private static final int FILM=0xff191919,INK=0xff111111,MUTED=0xff676767,NUMBER=0xffd6b34c;
    static Bitmap render(Context context,SharePosterData.Spec spec,int page,int width,Control control)throws IOException{
        if(width!=720&&width!=1440&&width!=2400)throw new IOException("请选择支持的输出尺寸");
        SharePosterLayout layout=new SharePosterLayout(spec.items.size(),spec.overview,spec.vertical);
        if(layout.pages==0)throw new IOException("请先选择照片");
        layout.start(page);control.check();
        long canvasBytes=(long)width*(width*5/4)*4;
        long budget=Math.min((width==720?16:width==1440?(layout.columns<=2?32:20):(layout.columns<=2?96:40))*MIB,Runtime.getRuntime().maxMemory()*2/3);
        if(canvasBytes+4*MIB>budget)throw new IOException("当前内存不足，请使用快速尺寸后重试");
        Bitmap output=null;Canvas canvas=null;
        try{
            output=Bitmap.createBitmap(width,width*5/4,Bitmap.Config.ARGB_8888,true,ColorSpace.get(ColorSpace.Named.SRGB));
            canvas=new Canvas(output);canvas.drawColor(Color.WHITE);
            Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
            strips(canvas,paint,layout,page,width);
            for(int i=layout.start(page);i<layout.end(page);i++){
                control.check();SharePosterData.Item item=spec.items.get(i);SharePosterLayout.Box box=layout.photo(i,page,width);
                try(Source source=open(context,item,control)){
                    BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;source.decode(options);
                    if(options.outWidth<=0||options.outHeight<=0)throw new IOException("无法解码这张照片，请重新选择");
                    String mime=options.outMimeType;
                    if(!"image/jpeg".equals(mime)&&!"image/png".equals(mime)&&!(Build.VERSION.SDK_INT>=28&&("image/heif".equals(mime)||"image/heic".equals(mime))))throw new IOException("此图片格式暂不支持，请选择 JPEG 或 PNG");
                    int orientation=source.orientation();boolean swapped=orientation>=5&&orientation<=8;
                    int orientedW=swapped?options.outHeight:options.outWidth,orientedH=swapped?options.outWidth:options.outHeight;
                    options.inSampleSize=SharePosterLayout.sampleSize(orientedW,orientedH,box.width(),box.height(),item.fill);
                    long photoBytes=SharePosterLayout.decodedBytes(options.outWidth,options.outHeight,options.inSampleSize);
                    if(photoBytes>budget-canvasBytes-4*MIB)throw new IOException("这张照片的高清裁切超出内存预算，请改为完整显示或快速尺寸");
                    control.check();options.inJustDecodeBounds=false;options.inPreferredConfig=Bitmap.Config.ARGB_8888;options.inPreferredColorSpace=ColorSpace.get(ColorSpace.Named.SRGB);options.inMutable=true;
                    Bitmap photo=source.decode(options);
                    if(photo==null)throw new IOException("照片解码失败，请重新选择");
                    try{control.check();if(photo.getAllocationByteCount()>budget-canvasBytes-4*MIB)throw new IOException("图片实际占用超过预算，请使用快速尺寸");drawPhoto(canvas,paint,photo,orientation,item,box);}
                    finally{photo.recycle();}
                }catch(IOException e){throw new IOException("第 "+(i+1)+" 张（"+shortName(item.name)+"）："+e.getMessage(),e);}
                if(spec.numbers)number(canvas,paint,layout,page,width,i,spec.overview?item.frame:i+1);
                control.progress(i+1,spec.items.size());
            }
            control.check();footer(canvas,spec,layout,page,width);control.check();
            return output;
        }catch(OutOfMemoryError e){if(output!=null)output.recycle();throw new IOException("当前内存不足，请改用快速尺寸或关闭其他应用后重试",e);}
        catch(IOException|RuntimeException e){if(output!=null)output.recycle();if(e instanceof IOException)throw(IOException)e;throw new IOException("分享图生成失败："+e.getMessage(),e);}
        finally{if(canvas!=null)canvas.setBitmap(null);}
    }
    private static String shortName(String name){if(name==null||name.isEmpty())return "未命名";return name.length()>40?name.substring(0,40)+"…":name;}
    private static void strips(Canvas canvas,Paint paint,SharePosterLayout layout,int page,int width){
        float scale=layout.scale(width);
        for(int row=0;row<layout.rows(page);row++){
            SharePosterLayout.Box strip=layout.strip(row,page,width);paint.setColor(FILM);canvas.drawRect(strip.left,strip.top,strip.right,strip.bottom,paint);
            int holes=Math.round(strip.width()/(4.75f*scale));paint.setColor(Color.WHITE);
            for(int n=0;n<holes;n++){
                float x=strip.left+(n*4.75f+(4.75f-1.981f)/2)*scale;
                float y=strip.top+2.01f*scale,bottom=strip.bottom-4.804f*scale;
                canvas.drawRoundRect(x,y,x+1.981f*scale,y+2.794f*scale,.51f*scale,.51f*scale,paint);
                canvas.drawRoundRect(x,bottom,x+1.981f*scale,bottom+2.794f*scale,.51f*scale,.51f*scale,paint);
            }
        }
    }
    private static void number(Canvas canvas,Paint paint,SharePosterLayout layout,int page,int width,int index,int number)throws IOException{
        SharePosterLayout.Box photo=layout.photo(index,page,width),strip=layout.strip((index-layout.start(page))/layout.columns,page,width);float scale=layout.scale(width);
        String label=number>0?String.valueOf(number):"—";
        paint.setTypeface(Typeface.MONOSPACE);paint.setTextAlign(Paint.Align.LEFT);paint.setColor(NUMBER);paint.setTextSize(Math.min(.012f*width,1.65f*scale));
        if(paint.measureText(label)>photo.width())throw new IOException("原帧号过长，请关闭编号后再生成");
        canvas.drawText(label,photo.left,strip.bottom-.15f*scale-paint.descent(),paint);
    }
    private static void drawPhoto(Canvas canvas,Paint paint,Bitmap bitmap,int orientation,SharePosterData.Item item,SharePosterLayout.Box box)throws IOException{
        if(!Float.isFinite(item.cropX)||!Float.isFinite(item.cropY))throw new IOException("裁切位置无效");
        float w=bitmap.getWidth(),h=bitmap.getHeight(),ow=orientation>=5&&orientation<=8?h:w,oh=orientation>=5&&orientation<=8?w:h;
        float scale=item.fill?Math.max(box.width()/ow,box.height()/oh):Math.min(box.width()/ow,box.height()/oh);
        float x=box.left+(box.width()-ow*scale)*(item.fill?clamp(item.cropX):.5f),y=box.top+(box.height()-oh*scale)*(item.fill?clamp(item.cropY):.5f);
        Matrix matrix=orientationMatrix(orientation,w,h);matrix.postScale(scale,scale);matrix.postTranslate(x,y);
        int save=canvas.save();canvas.clipRect(box.left,box.top,box.right,box.bottom);paint.setColor(Color.WHITE);canvas.drawBitmap(bitmap,matrix,paint);canvas.restoreToCount(save);
    }
    static Matrix orientationMatrix(int orientation,float width,float height){
        float[] values;
        switch(orientation){
            case 2:values=new float[]{-1,0,width,0,1,0,0,0,1};break;
            case 3:values=new float[]{-1,0,width,0,-1,height,0,0,1};break;
            case 4:values=new float[]{1,0,0,0,-1,height,0,0,1};break;
            case 5:values=new float[]{0,1,0,1,0,0,0,0,1};break;
            case 6:values=new float[]{0,-1,height,1,0,0,0,0,1};break;
            case 7:values=new float[]{0,-1,height,-1,0,width,0,0,1};break;
            case 8:values=new float[]{0,1,0,-1,0,width,0,0,1};break;
            default:values=new float[]{1,0,0,0,1,0,0,0,1};
        }
        Matrix result=new Matrix();result.setValues(values);return result;
    }
    private static float clamp(float value){return Math.max(0,Math.min(1,value));}
    private static void footer(Canvas canvas,SharePosterData.Spec spec,SharePosterLayout layout,int page,int width)throws IOException{
        int save=canvas.save();canvas.scale(width/2400f,width/2400f);
        try{footerAtWidth(canvas,spec,layout,page,2400);}finally{canvas.restoreToCount(save);}
    }
    private static void footerAtWidth(Canvas canvas,SharePosterData.Spec spec,SharePosterLayout layout,int page,int width)throws IOException{
        float rightX=.33f*width,rightWidth=.64f*width,bottom=1.215f*width;
        TextPaint titlePaint=font(.03f*width,INK,Typeface.create("sans-serif",Typeface.BOLD));
        StaticLayout title=lines(spec.title,titlePaint,rightWidth,2,"标题");
        TextPaint detailPaint=font(.014f*width,MUTED,Typeface.MONOSPACE);
        StaticLayout details=lines(spec.details,detailPaint,rightWidth,1,"胶片／相机"),date=lines(spec.date,detailPaint,rightWidth,1,"日期");
        String count=spec.items.size()+" 张"+(layout.pages>1?"  ·  "+(page+1)+" / "+layout.pages:"");
        StaticLayout amount=lines(count,detailPaint,rightWidth,1,"照片数");
        float gap=.008f*width,total=height(title)+height(details)+height(date)+height(amount);
        int nonempty=(title==null?0:1)+(details==null?0:1)+(date==null?0:1)+1;total+=gap*(nonempty-1);
        if(total>.215f*width)throw new IOException("底部文字过多，请缩短标题或资料");
        float y=bottom-total;
        for(StaticLayout text:new StaticLayout[]{title,details,date,amount})if(text!=null){drawText(canvas,text,rightX,y);y+=text.getHeight()+gap;}
        StaticLayout signature=lines(spec.signature,font(.018f*width,INK,Typeface.create("sans-serif",Typeface.BOLD)),.27f*width,2,"署名",Layout.Alignment.ALIGN_NORMAL);
        if(signature!=null)drawText(canvas,signature,.03f*width,bottom-signature.getHeight());
    }
    private static TextPaint font(float size,int color,Typeface type){TextPaint paint=new TextPaint(Paint.ANTI_ALIAS_FLAG);paint.setTextSize(size);paint.setColor(color);paint.setTypeface(type);return paint;}
    private static StaticLayout lines(String value,TextPaint paint,float width,int maximum,String name)throws IOException{
        return lines(value,paint,width,maximum,name,Layout.Alignment.ALIGN_OPPOSITE);
    }
    private static StaticLayout lines(String value,TextPaint paint,float width,int maximum,String name,Layout.Alignment alignment)throws IOException{
        if(value.isEmpty())return null;
        StaticLayout text=StaticLayout.Builder.obtain(value,0,value.length(),paint,(int)width).setAlignment(alignment).setIncludePad(false).setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE).setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE).build();
        if(text.getLineCount()>maximum)throw new IOException(name+"过长，请缩短后再生成");
        return text;
    }
    private static float height(StaticLayout text){return text==null?0:text.getHeight();}
    private static void drawText(Canvas canvas,StaticLayout text,float x,float y){int save=canvas.save();canvas.translate(x,y);text.draw(canvas);canvas.restoreToCount(save);}
    private static Source open(Context context,SharePosterData.Item item,Control control)throws IOException{
        if(item.file!=null){if(!item.file.isFile()||!item.file.canRead())throw new IOException("照片已移除或无法读取");if(item.file.length()>MAX_SOURCE)throw new IOException("单张图片不能超过 512 MiB");return new Source(item.file,null,false);}
        if(item.uri==null||!"content".equals(item.uri.getScheme()))throw new IOException("照片来源无效，请重新选择");
        ParcelFileDescriptor descriptor=null;
        try{
            descriptor=context.getContentResolver().openFileDescriptor(item.uri,"r");
            if(descriptor!=null){
                long length=descriptor.getStatSize();if(length>MAX_SOURCE)throw new IOException("单张图片不能超过 512 MiB");
                try{if(length<0)length=Os.lseek(descriptor.getFileDescriptor(),0,OsConstants.SEEK_END);if(length>MAX_SOURCE)throw new IOException("单张图片不能超过 512 MiB");Os.lseek(descriptor.getFileDescriptor(),0,OsConstants.SEEK_SET);return new Source(null,descriptor,false);}
                catch(android.system.ErrnoException nonseekable){descriptor.close();descriptor=null;}
            }
        }catch(RuntimeException e){if(descriptor!=null)descriptor.close();throw new IOException("图片读取权限已失效，请重新选择",e);}
        catch(IOException e){if(descriptor!=null)descriptor.close();throw e;}
        File directory=new File(context.getCacheDir(),"share-posters");if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("无法建立临时目录");
        if(directory.getUsableSpace()<8*MIB)throw new IOException("临时空间不足，请清理空间后重试");
        File staged;synchronized(STAGED){staged=File.createTempFile("source-",".tmp",directory);STAGED.add(staged);}boolean complete=false;
        try{try(InputStream input=context.getContentResolver().openInputStream(item.uri);FileOutputStream output=new FileOutputStream(staged)){
            if(input==null)throw new IOException("照片无法读取");long bytes=0;byte[] buffer=new byte[65536];int n;
            while((n=input.read(buffer))!=-1){control.check();bytes+=n;if(bytes>MAX_SOURCE)throw new IOException("单张图片不能超过 512 MiB");output.write(buffer,0,n);}
            }complete=true;return new Source(staged,null,true);
        }catch(RuntimeException e){throw new IOException("图片读取权限已失效，请重新选择",e);}
        finally{if(!complete){staged.delete();STAGED.remove(staged);}}
    }
    private static final class Source implements Closeable {
        final File file;final ParcelFileDescriptor descriptor;final boolean temporary;
        Source(File file,ParcelFileDescriptor descriptor,boolean temporary){this.file=file;this.descriptor=descriptor;this.temporary=temporary;}
        private void rewind()throws IOException{try{Os.lseek(descriptor.getFileDescriptor(),0,OsConstants.SEEK_SET);}catch(android.system.ErrnoException e){throw new IOException("图片流无法重新读取",e);}}
        Bitmap decode(BitmapFactory.Options options)throws IOException{if(file!=null)return BitmapFactory.decodeFile(file.toString(),options);rewind();return BitmapFactory.decodeFileDescriptor(descriptor.getFileDescriptor(),null,options);}
        int orientation()throws IOException{if(file!=null)return new ExifInterface(file).getAttributeInt(ExifInterface.TAG_ORIENTATION,1);rewind();return new ExifInterface(descriptor.getFileDescriptor()).getAttributeInt(ExifInterface.TAG_ORIENTATION,1);}
        @Override public void close()throws IOException{try{if(descriptor!=null)descriptor.close();}finally{if(temporary){file.delete();STAGED.remove(file);}}}
    }
    static void cleanStagedSources(Context context){File[] files=new File(context.getCacheDir(),"share-posters").listFiles((d,n)->n.startsWith("source-")&&n.endsWith(".tmp"));if(files==null)return;int checked=0;for(File file:files){if(++checked>32)break;synchronized(STAGED){if(!STAGED.contains(file)&&file.isFile())file.delete();}}}
    private SharePosterRenderer(){}
}
