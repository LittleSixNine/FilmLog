package cn.sixnine.filmlog;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;

/** Native horizontal interception cancels card clicks when a swipe takes over. */
final class HomeRollCarousel extends HorizontalScrollView {
    private final LinearLayout strip;private final BooleanSupplier canSwitch;private final IntConsumer selected;private final PositionIndicator indicator;
    private int index,downIndex,step;private float downX,downY;private boolean flung;
    HomeRollCarousel(Context context,BooleanSupplier canSwitch,IntConsumer selected){super(context);this.canSwitch=canSwitch;this.selected=selected;indicator=new PositionIndicator(context);setHorizontalScrollBarEnabled(false);setClipToPadding(false);strip=new LinearLayout(context);strip.setGravity(Gravity.TOP);super.addView(strip,new LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.WRAP_CONTENT));}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    void addCard(HomeRollCard card){strip.addView(card,new LinearLayout.LayoutParams(1,LayoutParams.WRAP_CONTENT));}
    int currentIndex(){return index;}
    View indicator(){indicator.setVisibility(strip.getChildCount()>1?VISIBLE:GONE);return indicator;}
    private void describePosition(){setContentDescription("胶卷，第"+(index+1)+"卷，共"+strip.getChildCount()+"卷");}
    void initialIndex(int value){index=Math.max(0,Math.min(strip.getChildCount()-1,value));post(()->scrollTo(index*step,0));describePosition();}
    void select(int value,boolean smooth){if(!canSwitch.getAsBoolean()||strip.getChildCount()==0)return;index=Math.max(0,Math.min(strip.getChildCount()-1,value));if(smooth&&android.animation.ValueAnimator.areAnimatorsEnabled())smoothScrollTo(index*step,0);else scrollTo(index*step,0);describePosition();selected.accept(index);}
    @Override protected void onMeasure(int width,int height){int count=strip.getChildCount(),available=MeasureSpec.getSize(width),edge=dp(count>1?24:16),card=Math.max(dp(96),available-2*edge);step=card+dp(12);strip.setPadding(edge,0,edge,0);for(int i=0;i<count;i++){LinearLayout.LayoutParams p=(LinearLayout.LayoutParams)strip.getChildAt(i).getLayoutParams();p.width=card;p.rightMargin=i<count-1?dp(12):0;}super.onMeasure(width,height);}
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);post(()->scrollTo(index*step,0));}
    @Override public boolean onInterceptTouchEvent(MotionEvent event){if(!canSwitch.getAsBoolean())return false;if(event.getActionMasked()==MotionEvent.ACTION_DOWN){downX=event.getX();downY=event.getY();downIndex=index;flung=false;}if(event.getActionMasked()==MotionEvent.ACTION_MOVE&&Math.abs(event.getX()-downX)>Math.abs(event.getY()-downY)&&Math.abs(event.getX()-downX)>ViewConfiguration.get(getContext()).getScaledTouchSlop())getParent().requestDisallowInterceptTouchEvent(true);return super.onInterceptTouchEvent(event);}
    @Override public boolean onTouchEvent(MotionEvent event){if(!canSwitch.getAsBoolean())return false;if(event.getActionMasked()==MotionEvent.ACTION_DOWN){downX=event.getX();downY=event.getY();downIndex=index;flung=false;}boolean handled=super.onTouchEvent(event);if(event.getActionMasked()==MotionEvent.ACTION_UP&&!flung){float delta=downX-event.getX();select(Math.abs(delta)>dp(40)?downIndex+(delta>0?1:-1):nearest(getScrollX(),step,strip.getChildCount()),true);}else if(event.getActionMasked()==MotionEvent.ACTION_CANCEL)select(index,true);return handled;}
    @Override public void fling(int velocity){flung=true;select(downIndex+(velocity>0?1:-1),true);}
    @Override public boolean dispatchKeyEvent(KeyEvent event){if(event.getAction()==KeyEvent.ACTION_DOWN&&(event.getKeyCode()==KeyEvent.KEYCODE_DPAD_LEFT||event.getKeyCode()==KeyEvent.KEYCODE_DPAD_RIGHT)){select(index+(event.getKeyCode()==KeyEvent.KEYCODE_DPAD_RIGHT?1:-1),true);return true;}return super.dispatchKeyEvent(event);}
    @Override public boolean performAccessibilityAction(int action,android.os.Bundle args){if(action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD||action==AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD){select(index+(action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD?1:-1),true);return true;}return super.performAccessibilityAction(action,args);}
    @Override protected void onScrollChanged(int x,int y,int oldX,int oldY){super.onScrollChanged(x,y,oldX,oldY);if(indicator!=null)indicator.invalidate();}
    static float scrollFraction(int scroll,int step,int count){return step<=0||count<=1?0:Math.max(0,Math.min(1,scroll/(float)step/(count-1)));}
    private final class PositionIndicator extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        PositionIndicator(Context context){super(context);setTag("dashboard-position-rail");setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);int count=strip.getChildCount();if(count<=1)return;float width=dp(60),height=dp(2),left=(getWidth()-width)/2,top=dp(18),thumb=width/count;
            paint.setColor(getContext().getColor(R.color.ui_outline));canvas.drawRoundRect(left,top,left+width,top+height,height/2,height/2,paint);
            float offset=scrollFraction(HomeRollCarousel.this.getScrollX(),step,count)*(width-thumb);paint.setColor(getContext().getColor(R.color.ui_accent));canvas.drawRoundRect(left+offset,top,left+offset+thumb,top+height,height/2,height/2,paint);
        }
    }
    static int nearest(int scroll,int step,int count){if(step<=0||count<=0)return 0;return Math.max(0,Math.min(count-1,Math.round(scroll/(float)step)));}
}
