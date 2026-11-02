package cn.sixnine.filmlog;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import android.util.LruCache;
import java.io.File;
import java.util.concurrent.*;

/** Decode only when the row is drawn; never load an original at full resolution. */
final class ExposureThumbnail extends View {
    private static final ExecutorService worker=Executors.newFixedThreadPool(2);
    private static final LruCache<String,Bitmap> cache=new LruCache<String,Bitmap>(4*1024*1024){protected int sizeOf(String k,Bitmap b){return b.getByteCount();}};
    private final File file;private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);private Bitmap image;private boolean pending,failed;
    ExposureThumbnail(Context c,File file,String id){super(c);this.file=file;setTag("exposure-thumb:"+id);setContentDescription(file==null?"暂无照片":"曝光预览");setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
    @Override protected void onDraw(Canvas c){super.onDraw(c);float d=getResources().getDisplayMetrics().density;paint.setColor(getContext().getColor(R.color.ui_film_band));c.drawRoundRect(0,0,getWidth(),getHeight(),3*d,3*d,paint);if(file!=null&&image==null&&!pending&&!failed){image=cache.get(file.getAbsolutePath());if(image==null){pending=true;worker.execute(()->{Bitmap decoded=null;try{decoded=ScanImages.read(file,192);}catch(Exception ignored){}final Bitmap ready=decoded;post(()->{pending=false;failed=ready==null;image=ready;if(ready!=null)cache.put(file.getAbsolutePath(),ready);if(isAttachedToWindow())invalidate();});});}}if(image!=null){float scale=Math.max((float)getWidth()/image.getWidth(),(float)getHeight()/image.getHeight());float w=image.getWidth()*scale,h=image.getHeight()*scale;int count=c.save();c.clipRect(0,0,getWidth(),getHeight());c.drawBitmap(image,null,new RectF((getWidth()-w)/2,(getHeight()-h)/2,(getWidth()+w)/2,(getHeight()+h)/2),paint);c.restoreToCount(count);}else{paint.setColor(getContext().getColor(R.color.ui_film_pending));paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(d);c.drawRect(7*d,7*d,getWidth()-7*d,getHeight()-7*d,paint);paint.setStyle(Paint.Style.FILL);for(int x=3;x<getWidth()/d-2;x+=6){c.drawRect(x*d,2*d,(x+2)*d,4*d,paint);c.drawRect(x*d,getHeight()-4*d,(x+2)*d,getHeight()-2*d,paint);}}}
}
