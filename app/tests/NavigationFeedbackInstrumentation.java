package cn.sixnine.filmlog;

import android.app.*;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;

/** Native full-row hit targets and the approved C journal navigation, using synthetic records only. */
public final class NavigationFeedbackInstrumentation extends Instrumentation {
    private MainActivity page;private LogStore logs;private Configuration original;private String owner,other;private final ArrayList<String> shots=new ArrayList<>();private int assertions;
    interface Action{void run()throws Exception;}
    private void check(boolean ok,String label){assertions++;if(!ok)throw new AssertionError(label);}
    private void main(Action action)throws Exception{Throwable[] error={null};runOnMainSync(()->{try{action.run();}catch(Throwable t){error[0]=t;}});waitForIdleSync();if(error[0]!=null)throw new RuntimeException(error[0]);}
    private Object field(String name)throws Exception{java.lang.reflect.Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(page);}
    private void set(String name,Object value)throws Exception{java.lang.reflect.Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);f.set(page,value);}
    private void call(String name)throws Exception{java.lang.reflect.Method m=MainActivity.class.getDeclaredMethod(name);m.setAccessible(true);m.invoke(page);}
    private ViewGroup root()throws Exception{return (ViewGroup)field("root");}
    private int dp(int value){return Math.round(value*page.getResources().getDisplayMetrics().density);}
    private void layout(int width)throws Exception{View v=root();v.measure(View.MeasureSpec.makeMeasureSpec(dp(width),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(dp(740),View.MeasureSpec.EXACTLY));v.layout(0,0,v.getMeasuredWidth(),v.getMeasuredHeight());}
    @SuppressWarnings("deprecation") private void configure(boolean dark,int width,float font)throws Exception{Configuration c=new Configuration(original);c.uiMode=(c.uiMode&~Configuration.UI_MODE_NIGHT_MASK)|(dark?Configuration.UI_MODE_NIGHT_YES:Configuration.UI_MODE_NIGHT_NO);c.screenWidthDp=width;c.fontScale=font;page.getResources().updateConfiguration(c,page.getResources().getDisplayMetrics());}
    private View row(int index)throws Exception{return root().findViewWithTag("exposure-row:"+shots.get(index));}
    private int[] point(View v){int[] p=new int[2];v.getLocationOnScreen(p);return p;}
    private void snap(String name)throws Exception{java.util.concurrent.CountDownLatch drawn=new java.util.concurrent.CountDownLatch(1);main(()->page.getWindow().getDecorView().postOnAnimation(()->page.getWindow().getDecorView().postOnAnimation(drawn::countDown)));check(drawn.await(3,java.util.concurrent.TimeUnit.SECONDS),"latest scene frame presented");Bitmap b=getUiAutomation().takeScreenshot();check(b!=null,"native screenshot");try(OutputStream out=new FileOutputStream(new File(getTargetContext().getFilesDir(),name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();}
    private void nativeTap(float x,float y)throws Exception{long time=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(time,time,MotionEvent.ACTION_DOWN,x,y,0),up=MotionEvent.obtain(time,time+60,MotionEvent.ACTION_UP,x,y,0);try{check(getUiAutomation().injectInputEvent(down,true),"touch down");check(getUiAutomation().injectInputEvent(up,true),"touch up");}finally{down.recycle();up.recycle();}waitForIdleSync();}
    private void navigation(int width)throws Exception{
        String[] keys={"dashboard","home","my"},labels={"随记","胶卷","我的"};
        for(String selected:keys){set("screen",selected);call("render");layout(width);ViewGroup footer=(ViewGroup)field("footer"),nav=(ViewGroup)footer.getChildAt(0);
            check(footer.getBackground() instanceof ColorDrawable&&((ColorDrawable)footer.getBackground()).getColor()==page.getColor(R.color.ui_background),"footer has no outlined drawable");
            check(nav.getChildCount()==3,"three destinations");int x=-1,h=-1;
            for(int i=0;i<3;i++){ViewGroup item=(ViewGroup)nav.getChildAt(i);ImageView icon=(ImageView)item.getChildAt(0);TextView label=(TextView)item.getChildAt(1);boolean active=keys[i].equals(selected);
                check(label.getText().toString().equals(labels[i]),"confirmed tab caption");check(item.isSelected()==active&&icon.isSelected()==active,"selected icon and target agree");check(item.getContentDescription().toString().equals(labels[i]+(active?"，当前页":"")),"accessible caption");
                check(item.getWidth()>=dp(48)&&item.getHeight()>=dp(64),"full navigation touch target");check(icon.getWidth()==dp(24)&&icon.getHeight()==dp(24),"24dp native vector");check(icon.getBackground()==null&&item.getBackground() instanceof RippleDrawable,"no persistent capsule; native bounded touch feedback");check(icon.getDrawable() instanceof StateListDrawable&&((StateListDrawable)icon.getDrawable()).getCurrent() instanceof VectorDrawable,"native vector state resource");
                check(label.getLineCount()==1&&label.getLayout().getEllipsisCount(0)==0,"single caption unclipped");check(label.getCurrentTextColor()==page.getColor(active?R.color.ui_accent:R.color.ui_muted),"day/night selected colors");if(x<0){x=item.getWidth();h=item.getHeight();}else check(Math.abs(x-item.getWidth())<=1&&h==item.getHeight(),"equal navigation cells");
            }
        }
    }
    @Override public void onCreate(Bundle b){super.onCreate(b);start();}
    @Override public void onStart(){Bundle result=new Bundle();boolean success=false;String previous=Workspace.current(getTargetContext());try{
        check(getTargetContext().getPackageName().equals("cn.sixnine.filmlog.ui.tests"),"isolated identity");Workspace.activate(getTargetContext(),UUID.randomUUID().toString());Context c=Workspace.context(getTargetContext());logs=new LogStore(c);owner=logs.addRoll("Feedback fixture","Kodak Gold 200","Nikon FE2",200,36);other=logs.addRoll("Other owner","Test","",100,36);for(int i=0;i<4;i++)shots.add(logs.insert(owner,1780000000000L+i*1000,-10,480,"denied","8","1/125","","fixture "+i,"",""));
        page=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).putExtra("open_roll",owner).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));original=new Configuration(page.getResources().getConfiguration());waitForIdleSync();
        for(boolean dark:new boolean[]{false,true})for(int width:new int[]{320,375,414,768})for(float font:new float[]{1,1.6f}){main(()->{configure(dark,width,font);navigation(width);set("screen","roll");set("rollId",owner);call("render");layout(width);View first=row(3);ViewGroup group=(ViewGroup)first.getParent();check(group.getPaddingLeft()==0&&group.getPaddingRight()==0&&group.getClipToOutline(),"outer card clip, no dead side padding");check(group.getChildCount()==7,"four exposure rows plus three dividers");for(int i=0;i<group.getChildCount();i++){View v=group.getChildAt(i);if(i%2==0){check(v.getLeft()==0&&v.getRight()==group.getWidth(),"full-width row");check(v.getPaddingLeft()==dp(12)&&v.getPaddingRight()==dp(12),"inset content not touch target");check(v.getHeight()>=dp(64),"compact minimum row");v.setPressed(true);v.getBackground().jumpToCurrentState();Bitmap b=Bitmap.createBitmap(v.getWidth(),v.getHeight(),Bitmap.Config.ARGB_8888);v.draw(new Canvas(b));check(b.getPixel(dp(2),b.getHeight()/2)==page.getColor(R.color.ui_selected)&&b.getPixel(b.getWidth()-dp(2)-1,b.getHeight()/2)==page.getColor(R.color.ui_selected),"both side pixels highlight together");b.recycle();v.setPressed(false);}else{LinearLayout.LayoutParams lp=(LinearLayout.LayoutParams)v.getLayoutParams();check(lp.leftMargin==dp(12)&&lp.rightMargin==dp(12),"independent inset separator");}}});}
        for(boolean dark:new boolean[]{false,true}){main(()->{configure(dark,original.screenWidthDp,1);set("screen","roll");call("render");});main(()->{View v=row(3);Rect rect=new Rect();v.getDrawingRect(rect);((ScrollView)field("pageScroll")).offsetDescendantRectToMyCoords(v,rect);((ScrollView)field("pageScroll")).scrollTo(0,Math.max(0,rect.top-dp(12)));v.setPressed(true);});SystemClock.sleep(120);snap(dark?"row-feedback-dark":"row-feedback-light");main(()->row(3).setPressed(false));for(boolean right:new boolean[]{false,true}){final int[][] tap={null};main(()->{View v=row(3);tap[0]=point(v);tap[0][0]+=right?v.getWidth()-dp(2):dp(2);tap[0][1]+=v.getHeight()/2;});nativeTap(tap[0][0],tap[0][1]);main(()->{check(field("screen").equals("detail")&&field("shotId").equals(shots.get(3)),"edge touch opens same stable exposure");page.onBackPressed();check(field("screen").equals("roll"),"detail back returns same roll");});}main(()->{set("screen","dashboard");call("render");});snap(dark?"navigation-C-dark":"navigation-C-light");}
        main(()->{root().findViewWithTag("main-tab-home").performClick();check(field("screen").equals("home"),"roll destination");root().findViewWithTag("main-tab-my").performClick();check(field("screen").equals("my"),"my destination");root().findViewWithTag("main-tab-dashboard").performClick();check(field("screen").equals("dashboard"),"journal retains dashboard route");check(logs.roll(owner).count==4&&logs.roll(other).count==0&&logs.nextFrame(owner)==5,"no exposure or counter changes");});success=true;result.putString("stream","PASS "+assertions+" assertions: native C photo journal, Suiji label, full-row pressed pixels and real left/right edge taps, rounded parent/inset dividers, flat three-page navigation and unchanged records; 320/375/414/768 x light/dark x normal/1.6 fonts.\n");
    }catch(Throwable t){result.putString("stream",android.util.Log.getStackTraceString(t));}finally{try{if(page!=null)main(()->{if(original!=null)page.getResources().updateConfiguration(original,page.getResources().getDisplayMetrics());page.finish();});if(logs!=null)logs.close();Workspace.activate(getTargetContext(),previous);}catch(Exception ignored){}}finish(success?-1:0,result);}
}
