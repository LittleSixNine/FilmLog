package cn.sixnine.filmlog;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.widget.*;
import java.util.function.IntConsumer;

/** Finite ISO wheel: scrolling previews a row; only an explicit click commits. */
final class MeterIsoWheel extends ScrollView {
    private final int itemHeight;private final IntConsumer moved;private final LinearLayout rows;private int centerIndex;private boolean positioned,touching;private int lastScroll;
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    MeterIsoWheel(Context context,int initial,IntConsumer moved,IntConsumer picked){
        super(context);centerIndex=Math.max(0,Math.min(PhotoMath.METER_ISOS.length-1,initial));this.moved=moved;itemHeight=dp(44);
        setPadding(0,0,0,0);setBackgroundColor(Color.TRANSPARENT);setVerticalScrollBarEnabled(false);setClipToPadding(false);setOverScrollMode(OVER_SCROLL_NEVER);rows=new LinearLayout(context);rows.setOrientation(LinearLayout.VERTICAL);rows.setBaselineAligned(false);rows.setClipChildren(false);rows.setClipToPadding(false);addView(rows,new LayoutParams(-1,-2));
        for(int i=0;i<PhotoMath.METER_ISOS.length;i++){final int index=i;TextView row=new TextView(context);row.setText("ISO "+PhotoMath.METER_ISOS[i]);row.setGravity(Gravity.CENTER);row.setTextSize(20);row.setTextColor(UiColors.color(getResources(),0xfff4eee2));row.setTypeface(android.os.Build.VERSION.SDK_INT>=28?Typeface.create(Typeface.DEFAULT,600,false):Typeface.create("sans-serif-medium",Typeface.NORMAL));row.setContentDescription(row.getText()+"，点按选择");row.setOnClickListener(v->{row.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);picked.accept(index);});rows.addView(row,new LinearLayout.LayoutParams(-1,itemHeight));}
    }
    @Override protected void onMeasure(int widthSpec,int heightSpec){int height=View.MeasureSpec.getSize(heightSpec);if(rows!=null&&height>0){int padding=Math.max(0,(height-itemHeight)/2);rows.setPadding(0,padding,0,padding);}super.onMeasure(widthSpec,heightSpec);}
    @Override protected void onSizeChanged(int w,int h,int oldW,int oldH){super.onSizeChanged(w,h,oldW,oldH);if(h<=0)return;final int target=centerIndex;positioned=false;post(()->{scrollTo(0,target*itemHeight);positioned=true;transformRows(getScrollY());});}
    @Override protected void onScrollChanged(int x,int y,int oldX,int oldY){super.onScrollChanged(x,y,oldX,oldY);if(rows!=null){transformRows(y);if(positioned&&!touching){lastScroll=y;removeCallbacks(settle);postDelayed(settle,100);}}}
    @Override public boolean dispatchTouchEvent(MotionEvent event){int action=event.getActionMasked();if(action==MotionEvent.ACTION_DOWN){touching=true;removeCallbacks(settle);}boolean result=super.dispatchTouchEvent(event);if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){touching=false;lastScroll=getScrollY();removeCallbacks(settle);postDelayed(settle,100);}return result;}
    private final Runnable settle=new Runnable(){public void run(){if(touching||!positioned)return;if(lastScroll!=getScrollY()){lastScroll=getScrollY();postDelayed(this,100);return;}int target=Math.max(0,Math.min(rows.getChildCount()-1,Math.round(getScrollY()/(float)itemHeight)))*itemHeight;if(target!=getScrollY())scrollTo(0,target);}};
    @Override protected void onDetachedFromWindow(){removeCallbacks(settle);super.onDetachedFromWindow();}
    private void transformRows(int scrollY){if(getHeight()==0)return;float density=getResources().getDisplayMetrics().density;
        for(int i=0;i<rows.getChildCount();i++){View row=rows.getChildAt(i);float delta=rows.getPaddingTop()+i*itemHeight+itemHeight/2f-scrollY-getHeight()/2f;double[] projection=MeterGeometry.wheelProjection(delta/density,getHeight()/density);row.setVisibility(Math.abs(delta)<=getHeight()/2f&&Math.abs(projection[3])<=Math.PI/2?View.VISIBLE:View.INVISIBLE);row.setPivotX(row.getWidth()/2f);row.setPivotY(itemHeight/2f);row.setTranslationY((float)(projection[0]*density-delta));row.setScaleX((float)projection[1]);
            // shortcut: affine glyph scaling; use projective glyph painting only if device comparison shows a visible mismatch.
            row.setScaleY((float)(projection[1]*Math.max(0,projection[2])));
        }
        int index=Math.max(0,Math.min(rows.getChildCount()-1,Math.round(scrollY/(float)itemHeight)));if(positioned&&index!=centerIndex){centerIndex=index;performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);moved.accept(index);}
    }
}
