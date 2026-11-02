package cn.sixnine.filmlog;

import android.content.Context;
import android.graphics.*;
import android.renderscript.*;
import android.view.*;

/** Samples only the sibling form, never its own overlay. Buffers remain in memory. */
@SuppressWarnings("deprecation")
final class BackdropView extends View {
    private final View source;
    private final Paint paint=new Paint(Paint.FILTER_BITMAP_FLAG);
    private final ViewTreeObserver.OnScrollChangedListener scroll=this::invalidate;
    private Bitmap input,output;
    private RenderScript runtime;
    private ScriptIntrinsicBlur blur;
    private Allocation from,to;
    private boolean unavailable;
    private final int[] origin=new int[2],target=new int[2];
    BackdropView(Context context,View source){super(context);this.source=source;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);setFocusable(false);}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();source.getViewTreeObserver().addOnScrollChangedListener(scroll);}
    @Override protected void onDetachedFromWindow(){if(source.getViewTreeObserver().isAlive())source.getViewTreeObserver().removeOnScrollChangedListener(scroll);release();super.onDetachedFromWindow();}
    private void release(){if(from!=null)from.destroy();if(to!=null)to.destroy();if(blur!=null)blur.destroy();if(runtime!=null)runtime.destroy();from=to=null;blur=null;runtime=null;if(input!=null)input.recycle();if(output!=null)output.recycle();input=output=null;}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);if(getWidth()==0||getHeight()==0)return;
        if(unavailable){canvas.drawColor(UiColors.color(getResources(),0xff1f1612));return;}
        try{
        int width=Math.max(1,(getWidth()+3)/4),height=Math.max(1,(getHeight()+3)/4);
        if(input==null||input.getWidth()!=width||input.getHeight()!=height){release();input=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);output=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);runtime=RenderScript.create(getContext());blur=ScriptIntrinsicBlur.create(runtime,Element.U8_4(runtime));from=Allocation.createFromBitmap(runtime,input);to=Allocation.createTyped(runtime,from.getType());}
        input.eraseColor(UiColors.color(getResources(),0xff1f1612));Canvas sample=new Canvas(input);sample.scale(width/(float)getWidth(),height/(float)getHeight());source.getLocationInWindow(origin);getLocationInWindow(target);sample.translate(origin[0]-target[0],origin[1]-target[1]);source.draw(sample);
        from.copyFrom(input);blur.setRadius(Math.min(25,18*getResources().getDisplayMetrics().density/4));blur.setInput(from);blur.forEach(to);to.copyTo(output);canvas.drawBitmap(output,null,new Rect(0,0,getWidth(),getHeight()),paint);canvas.drawColor(UiColors.color(getResources(),0xcc1f1612));
        }catch(RSRuntimeException unsupported){unavailable=true;release();canvas.drawColor(UiColors.color(getResources(),0xff1f1612));android.util.Log.w("FilmLog","Backdrop blur unavailable",unsupported);}
    }
}
