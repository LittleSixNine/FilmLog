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

/** Actual native views and intercepted tool intents in a private test workspace. */
public final class PrimaryNavigationInstrumentation extends Instrumentation {
    private MainActivity activity;private LogStore logs;private Configuration original;
    interface Action{void run()throws Exception;}
    private void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private void main(Action action)throws Exception{Throwable[] error={null};runOnMainSync(()->{try{action.run();}catch(Throwable e){error[0]=e;}});waitForIdleSync();if(error[0]!=null)throw new RuntimeException(error[0]);}
    private Object get(Object owner,String name)throws Exception{java.lang.reflect.Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
    private Object call(String name,Class<?>[] types,Object... args)throws Exception{java.lang.reflect.Method m=MainActivity.class.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(activity,args);}
    private ViewGroup root()throws Exception{return (ViewGroup)get(activity,"root");}
    private String screen()throws Exception{return (String)get(activity,"screen");}
    private List<View> views(View view){ArrayList<View> result=new ArrayList<>();result.add(view);if(view instanceof ViewGroup){ViewGroup g=(ViewGroup)view;for(int i=0;i<g.getChildCount();i++)result.addAll(views(g.getChildAt(i)));}return result;}
    private View tagged(String tag)throws Exception{View result=root().findViewWithTag(tag);check(result!=null,"missing "+tag);return result;}
    private View entry(String title)throws Exception{for(View v:views(root()))if(v.isClickable()&&v.getContentDescription()!=null&&(v.getContentDescription().toString().equals(title)||v.getContentDescription().toString().startsWith(title+"，")))return v;throw new AssertionError("entry "+title);}
    private void tab(String name)throws Exception{tagged("main-tab-"+name).performClick();check(screen().equals(name),"switch to "+name);}
    @SuppressWarnings("deprecation") private void configuration(boolean dark,float font,int width){Configuration c=new Configuration(original);c.uiMode=(c.uiMode&~Configuration.UI_MODE_NIGHT_MASK)|(dark?Configuration.UI_MODE_NIGHT_YES:Configuration.UI_MODE_NIGHT_NO);c.fontScale=font;c.screenWidthDp=width;activity.getResources().updateConfiguration(c,activity.getResources().getDisplayMetrics());}
    private int dp(int n){return Math.round(n*activity.getResources().getDisplayMetrics().density);}
    private void layout(int width)throws Exception{View root=root();root.measure(View.MeasureSpec.makeMeasureSpec(dp(width),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(dp(740),View.MeasureSpec.EXACTLY));root.layout(0,0,root.getMeasuredWidth(),root.getMeasuredHeight());}
    private Rect rect(View view)throws Exception{Rect r=new Rect();view.getDrawingRect(r);root().offsetDescendantRectToMyCoords(view,r);return r;}
    private int runs(Bitmap image,boolean litOnly){int runs=0;boolean previous=false;for(int x=0;x<image.getWidth();x++){int p=image.getPixel(x,image.getHeight()/2);boolean active=litOnly?Math.min(Color.red(p),Math.min(Color.green(p),Color.blue(p)))>130:Color.red(p)>Color.red(image.getPixel(1,image.getHeight()/2))+2||Color.green(p)>Color.green(image.getPixel(1,image.getHeight()/2))+2; if(active&&!previous)runs++;previous=active;}return runs;}
    private void saveBitmap(Bitmap image,String name)throws Exception{File dir=new File(activity.getExternalFilesDir(null),"primary-pages");dir.mkdirs();try(FileOutputStream stream=new FileOutputStream(new File(dir,name+".png"))){image.compress(Bitmap.CompressFormat.PNG,100,stream);}}
    private void opticalInk(HomeRollCard card)throws Exception{
        ImageView logo=card.findViewWithTag("dashboard-camera-logo");TextView count=card.findViewWithTag("dashboard-count");if(logo==null)return;
        Bitmap src=((BitmapDrawable)logo.getDrawable()).getBitmap();int last=-1,firstX=src.getWidth();for(int y=0;y<src.getHeight();y++)for(int x=0;x<src.getWidth();x++)if(Color.alpha(src.getPixel(x,y))>32){last=Math.max(last,y);firstX=Math.min(firstX,x);}
        float[] edge={firstX,last+1};logo.getImageMatrix().mapPoints(edge);Bitmap digits=Bitmap.createBitmap(count.getWidth(),count.getHeight(),Bitmap.Config.ARGB_8888);count.draw(new Canvas(digits));int bottom=-1;for(int y=0;y<digits.getHeight();y++)for(int x=0;x<digits.getWidth();x++)if(Color.alpha(digits.getPixel(x,y))>100)bottom=y+1;digits.recycle();
        check(Math.abs(logo.getTop()+edge[1]-count.getTop()-bottom)<=1.5f,"raster digit ink aligns logo ink");check(edge[0]<dp(2),"logo left aligned rather than centered in reserved width");check(count.getLeft()>=logo.getRight()+dp(10),"logo and count never collide");
    }
    private void progressPixels()throws Exception{
        String fixture=new CatalogStore(logs).manual(UUID.randomUUID().toString(),"Sprocket fixture","Kodak Gold 200","Nikon FE2",200,36,"35 mm","","","original:kodak_gold_200");
        int inserted=0;
        for(int actual:new int[]{0,1,18,35,36,38}){
            while(inserted<actual){logs.insert(fixture,System.currentTimeMillis(),0,480,"denied","","","","","","");inserted++;}
            for(boolean dark:new boolean[]{false,true})for(int width:new int[]{320,375,414,768}){
                configuration(dark,dark?1.6f:1f,width);HomeRollCard card=new HomeRollCard(activity,logs,logs.roll(fixture),()->{},()->{},()->{});int pixels=dp(width-48);card.measure(View.MeasureSpec.makeMeasureSpec(pixels,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));card.layout(0,0,pixels,card.getMeasuredHeight());
                opticalInk(card);View band=card.findViewWithTag("dashboard-windows");TextView count=card.findViewWithTag("dashboard-count");check(count.getText().toString().equals(actual+"/36"),"actual count survives nominal threshold");check(count.getCurrentTextColor()==(dark?0xfff2e9d8:0xff552e21),"confirmed day/night count colors");check(band.getTop()==0&&band.getWidth()==card.getWidth(),"sprocket integrated at full card top");check(count.getParent()==card.findViewWithTag("dashboard-camera-heading"),"fraction shares logo row");check(Math.abs(count.getTextSize()-android.util.TypedValue.applyDimension(2,26,activity.getResources().getDisplayMetrics()))<1,"26sp Newsreader");
                Bitmap image=Bitmap.createBitmap(band.getWidth(),band.getHeight(),Bitmap.Config.ARGB_8888);band.draw(new Canvas(image));int lit=Math.min(actual,36);check(image.getPixel(1,image.getHeight()/2)==(dark?0xff252a26:0xff552e21),"confirmed graphite/dark and brown/light band");check(runs(image,true)==lit,"native filled hole count "+actual+" "+width);check(runs(image,false)==36,"all36 holes with faint solid fill "+actual+" "+width);if(actual==18&&width==375)saveBitmap(image,dark?"dark-large-sprocket18":"light-sprocket18");image.recycle();
                check(((Button)card.findViewWithTag("quick-record")).isEnabled(),"beyond nominal still recordable");
            }
        }
        logs.deleteRoll(fixture,inserted);String medium=logs.addRoll("Capacity fixture","Test120","",100,12);for(int i=0;i<6;i++)logs.insert(medium,System.currentTimeMillis(),0,480,"denied","","","","","","");configuration(false,1,375);HomeRollCard card=new HomeRollCard(activity,logs,logs.roll(medium),()->{},()->{},()->{});card.measure(View.MeasureSpec.makeMeasureSpec(dp(327),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));card.layout(0,0,card.getMeasuredWidth(),card.getMeasuredHeight());View band=card.findViewWithTag("dashboard-windows");Bitmap image=Bitmap.createBitmap(band.getWidth(),band.getHeight(),Bitmap.Config.ARGB_8888);band.draw(new Canvas(image));check(runs(image,true)==6&&runs(image,false)==12,"12-frame rolls have12 apertures, six filled");image.recycle();logs.deleteRoll(medium,6);configuration(false,1,original.screenWidthDp);call("activateTab",new Class<?>[]{String.class},"dashboard");
    }
    private void headersAndFab()throws Exception{
        for(boolean dark:new boolean[]{false,true})for(int width:new int[]{320,375,414,768}){
            float font=dark?1.6f:1f;configuration(dark,font,width);int[] expected=null;
            for(String page:new String[]{"dashboard","home","my"}){
                call("activateTab",new Class<?>[]{String.class},page);layout(width);View header=tagged("primary-header");TextView title=(TextView)tagged("primary-title");Button settings=(Button)tagged("primary-settings");
                check(title.getText().toString().equals(page.equals("dashboard")?"继续拍摄":page.equals("home")?"胶卷列表":"我的"),"title "+page);
                check(Math.abs(title.getTextSize()-android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP,24,activity.getResources().getDisplayMetrics()))<1,"24sp primary title");check(!settings.getText().toString().contains("设置")&&settings.getContentDescription().toString().equals("设置"),"icon only, accessible settings");check(settings.getWidth()==dp(48)&&settings.getHeight()==dp(48),"48dp settings target");
                ViewGroup content=(ViewGroup)((ScrollView)get(activity,"pageScroll")).getChildAt(0);Rect h=rect(header),t=rect(title),s=rect(settings);int start=rect(content.getChildAt(1)).top;int[] positions={h.top,t.left,t.top+title.getBaseline(),s.left,s.top,start};if(expected==null)expected=positions;else check(Arrays.equals(expected,positions),"aligned header/title/icon/content "+page+" "+width+" dark="+dark+" expected="+Arrays.toString(expected)+" actual="+Arrays.toString(positions));
                check(views(root()).stream().noneMatch(v->v instanceof TextView&&Arrays.asList("我的拍摄","胶片随记","器材、胶片与拍摄积累").contains(((TextView)v).getText().toString())),"removed duplicate chrome "+page);
                if(page.equals("my")){ViewGroup inset=(ViewGroup)content.getChildAt(1),first=(ViewGroup)inset.getChildAt(0);if(first.getChildCount()==2&&first.getChildAt(0).isClickable()&&first.getChildAt(1).isClickable())check(rect(first.getChildAt(0)).top==rect(first.getChildAt(1)).top&&first.getChildAt(0).getHeight()==first.getChildAt(1).getHeight(),"gear/stock cards align under large text "+width);}
                if(page.equals("home")){
                    View fab=tagged("new-roll-fab");Rect before=rect(fab),footer=rect((View)get(activity,"footer"));check(fab.getWidth()==dp(56)&&fab.getHeight()==dp(56),"56dp round FAB");check(before.right==root().getWidth()-root().getPaddingRight()-dp(16)&&before.bottom==footer.top-dp(16),"FAB right and nav clearance");check(fab.getBackground() instanceof RippleDrawable&&((GradientDrawable)((RippleDrawable)fab.getBackground()).getDrawable(0)).getShape()==GradientDrawable.OVAL,"round material ripple");check(Math.abs(fab.getElevation()-dp(6))<1,"FAB elevation");
                    ScrollView scroll=(ScrollView)get(activity,"pageScroll");scroll.scrollTo(0,scroll.getChildAt(0).getHeight());check(rect(fab).equals(before),"FAB fixed during list scroll");ViewGroup results=(ViewGroup)get(activity,"rollResults");View last=null;for(View item:views(results))if(item.getTag() instanceof String&&((String)item.getTag()).startsWith("roll-list-item:"))last=item;if(last!=null)check(rect(last).bottom<=before.top-dp(8),"last roll can scroll clear of FAB "+width);scroll.scrollTo(0,0);
                }else check(root().findViewWithTag("new-roll-fab")==null,"FAB limited to roll list");
            }
            call("activateTab",new Class<?>[]{String.class},"tools");layout(width);check(((TextView)entry("返回")).getLineCount()==1,"secondary back never wraps "+width);
            for(View v:views(tagged("tools-directory")))if(v instanceof TextView){TextView text=(TextView)v;check(text.getLayout()==null||text.getLayout().getHeight()+text.getTotalPaddingTop()+text.getTotalPaddingBottom()<=text.getHeight()+1,"tool text not vertically clipped "+width+" "+text.getText());}
            ScrollView tools=(ScrollView)get(activity,"pageScroll");tools.scrollTo(0,tools.getChildAt(0).getHeight());check(rect(entry("曝光记录提醒")).bottom<=rect(tools).bottom,"reminder reachable at end of directory");
        }
        configuration(false,1,original.screenWidthDp);call("activateTab",new Class<?>[]{String.class},"dashboard");root().requestLayout();
    }
    private void settingsReturns()throws Exception{
        for(String page:new String[]{"dashboard","home","my"}){
            call("activateTab",new Class<?>[]{String.class},page);tagged("primary-settings").performClick();check(screen().equals("settings")&&get(activity,"settingsBackScreen").equals(page),"settings origin "+page);
            Bundle saved=new Bundle();callActivityOnSaveInstanceState(activity,saved);check(saved.getString("settingsBackScreen").equals(page),"settings origin persisted");entry("返回").performClick();check(screen().equals(page),"toolbar returns to origin");
            tagged("primary-settings").performClick();entry("权限状态").performClick();activity.onBackPressed();check(screen().equals("settings"),"permissions back to settings");activity.onBackPressed();check(screen().equals(page),"system back returns to origin");
        }
    }
    private void toolRoutes()throws Exception{
        final Intent[] launched={null};ActivityMonitor monitor=new ActivityMonitor(){@Override public ActivityResult onStartActivity(Intent intent){launched[0]=new Intent(intent);return new ActivityResult(Activity.RESULT_CANCELED,null);}};addMonitor(monitor);
        try{
            call("activateTab",new Class<?>[]{String.class},"dashboard");entry("全部工具 ›").performClick();check(screen().equals("tools")&&launched[0]==null,"all tools opens directory rather than default calculator");
            String[] labels={"独立测光","曝光换算","景深","ND / 倒易律","手动闪光","照片分享图","长曝光计时","曝光记录提醒"};Class<?>[] types={MeterActivity.class,PhotoToolsActivity.class,PhotoToolsActivity.class,PhotoToolsActivity.class,PhotoToolsActivity.class,SharePosterActivity.class,ExposureTimerActivity.class,RemindersActivity.class};int[] modes={-1,0,2,1,3,-1,-1,-1};
            for(int i=0;i<labels.length;i++){launched[0]=null;entry(labels[i]).performClick();check(launched[0]!=null&&launched[0].getComponent().getClassName().equals(types[i].getName()),"tool route "+labels[i]);if(modes[i]>=0)check(launched[0].getIntExtra("tool_mode",-1)==modes[i],"correct calculator mode "+labels[i]);check(!launched[0].getBooleanExtra("return_result",false),"standalone tool does not write exposure");}
            activity.onBackPressed();check(screen().equals("dashboard"),"directory system back");entry("全部工具 ›").performClick();entry("返回").performClick();check(screen().equals("dashboard"),"directory toolbar back");
            tab("home");launched[0]=null;tagged("roll-projects").performClick();check(launched[0]!=null&&launched[0].getStringExtra("tab").equals("project"),"project entry preserved");
        }finally{removeMonitor(monitor);}
        int count=logs.rolls().size();tagged("new-roll-fab").performClick();Object wizard=get(activity,"rollWizard");check(wizard!=null&&((Dialog)get(wizard,"dialog")).isShowing(),"FAB opens existing roll wizard");((Dialog)get(wizard,"dialog")).cancel();check(logs.rolls().size()==count,"cancel wizard never creates a roll");
    }
    private void snapshot(String name)throws Exception{waitForIdleSync();SystemClock.sleep(350);Bitmap image=getUiAutomation().takeScreenshot();check(image!=null,"screenshot");File dir=new File(activity.getExternalFilesDir(null),"primary-pages");dir.mkdirs();try(FileOutputStream stream=new FileOutputStream(new File(dir,name+".png"))){image.compress(Bitmap.CompressFormat.PNG,100,stream);}finally{image.recycle();}}
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){Bundle out=new Bundle();Context context=getTargetContext();String previous=Workspace.current(context);boolean passed=false;try{
        check(context.getPackageName().equals("cn.sixnine.filmlog.ui.tests"),"isolated package");String space=UUID.randomUUID().toString();Workspace.activate(context,space);Context isolated=new WorkspaceContext(Workspace.root(context),space);
        try(LogStore fixture=new LogStore(isolated)){CatalogStore c=new CatalogStore(fixture);String camera=c.saveCamera("","Olympus 35 DC","","35 mm","","",false,"Olympus-history-4");c.manual(UUID.randomUUID().toString(),"Navigation fixture","Kodak Ultramax 400","Olympus 35 DC",400,36,"35 mm",camera,"","original:kodak_ultramax_400");}
        activity=(MainActivity)startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));waitForIdleSync();original=new Configuration(activity.getResources().getConfiguration());main(()->{logs=(LogStore)get(activity,"store");settingsReturns();toolRoutes();progressPixels();String demo=logs.rolls().get(0).id;for(int i=0;i<18;i++)logs.insert(demo,System.currentTimeMillis(),0,480,"denied","","","","","","");activity.getPreferences(0).edit().putString("dashboard_roll",demo).commit();});
        for(boolean dark:new boolean[]{false,true}){
            main(()->configuration(dark,dark?1.6f:1f,original.screenWidthDp));
            for(String page:new String[]{"dashboard","home","my","tools"}){main(()->{call("activateTab",new Class<?>[]{String.class},page);root().requestLayout();});snapshot((dark?"dark-large-":"light-")+page);if(page.equals("tools")){main(()->((ScrollView)get(activity,"pageScroll")).fullScroll(View.FOCUS_DOWN));snapshot((dark?"dark-large-":"light-")+page+"-bottom");}}
        }
        main(()->{for(int i=0;i<12;i++)logs.addRoll("Layout fixture "+i,"Test film "+i,"",400,36);headersAndFab();activity.finish();});
        out.putString("stream","PASS: native top-edge36 sprockets/Newsreader26-10/0,1,18,35,36,38 filled states/12-capacity mapping/exact colors and baseline; shared headers/content starts at320/375/414/768, warm and dark1.6; icon-only settings and both back paths, persisted settings origin; all8 tool intents/modes, directory back, preserved projects, FAB shape/56dp/ripple/nav clearance/fixed scrolling/last-row clearance, existing wizard cancel no save. Synthetic screenshots and intercepted intents, not phone acceptance\n");passed=true;
    }catch(Throwable error){out.putString("stream",android.util.Log.getStackTraceString(error));}finally{try{if(activity!=null)main(()->{if(original!=null)activity.getResources().updateConfiguration(original,activity.getResources().getDisplayMetrics());if(!activity.isFinishing())activity.finish();});}catch(Exception ignored){}Workspace.activate(context,previous);}finish(passed?-1:0,out);}
}
