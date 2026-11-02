package cn.sixnine.filmlog;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Real dialogs and transactions, in a fresh workspace of the separate UI test package. */
public final class RollWizardInstrumentation extends Instrumentation {
    public static final class Host extends Activity {
        static String space=UUID.randomUUID().toString();
        LogStore logs;
        static android.content.res.Configuration testConfiguration;
        @Override protected void attachBaseContext(Context base){if(testConfiguration!=null)base=base.createConfigurationContext(testConfiguration);super.attachBaseContext(new WorkspaceContext(base,space));}
        @Override public void onCreate(Bundle state){setTheme(R.style.AppTheme);super.onCreate(state);setContentView(new LinearLayout(this));logs=new LogStore(this);}
        @Override public void onDestroy(){logs.close();super.onDestroy();}
    }
    interface Check {void run()throws Exception;}
    private Host host;
    private RollWizard wizard;
    private Bundle draft;
    private String saved="";
    private int cancelled;
    private void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private Object field(String name)throws Exception{java.lang.reflect.Field f=RollWizard.class.getDeclaredField(name);f.setAccessible(true);return f.get(wizard);}
    private Object call(String name,Class<?>[] types,Object... values)throws Exception{java.lang.reflect.Method m=RollWizard.class.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(wizard,values);}
    private void main(Check action)throws Exception{final Throwable[] failure={null};runOnMainSync(()->{try{action.run();}catch(Throwable e){failure[0]=e;}});waitForIdleSync();if(failure[0]!=null)throw new RuntimeException(failure[0]);}
    private View root()throws Exception{return (View)field("root");}
    private void all(View view,List<View> values){values.add(view);if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)all(group.getChildAt(i),values);}}
    private List<View> all(View root){List<View> values=new ArrayList<>();all(root,values);return values;}
    private Button button(View root,String label){for(View view:all(root))if(view instanceof Button&&((Button)view).getText().toString().equals(label))return (Button)view;throw new AssertionError("button missing: "+label);}
    private EditText input(View root,String label){for(View view:all(root))if(view instanceof EditText&&label.contentEquals(view.getContentDescription()))return (EditText)view;throw new AssertionError("input missing: "+label);}
    private int dp(int n){return Math.round(n*host.getResources().getDisplayMetrics().density);}
    private void open(Bundle state){draft=state;wizard=new RollWizard(host,host.logs,draft,()->cancelled++,id->saved=id);wizard.show();}
    private void next()throws Exception{((Button)field("next")).performClick();}
    private void layout()throws Exception{
        View view=root();Button next=(Button)field("next");
        for(int width:new int[]{320,375,414,768})for(int height:new int[]{440,680}){
            view.measure(View.MeasureSpec.makeMeasureSpec(dp(width),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(dp(height),View.MeasureSpec.AT_MOST));view.layout(0,0,view.getMeasuredWidth(),view.getMeasuredHeight());
            check(next.getHeight()>=dp(48)&&next.getBottom()<=view.getHeight()&&next.getTop()>=0,"footer reachable width="+width+" height="+height);
            for(View v:all(view))if(v instanceof ImageView){check(v.getLayoutParams().width==v.getLayoutParams().height,"every cover is square");check(((ImageView)v).getScaleType()==ImageView.ScaleType.FIT_CENTER,"cover never crops packaging");}
        }
        view.requestLayout();
    }
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){Bundle out=new Bundle();try{
        check(getTargetContext().getPackageName().equals("cn.sixnine.filmlog.ui.tests"),"separate UI identity only");
        host=(Host)startActivitySync(new Intent(getTargetContext(),Host.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
        final CatalogStore catalog=new CatalogStore(host.logs);final String[] camera={""};
        main(()->{camera[0]=catalog.saveEntry("","camera","Same-name camera","35 mm","test owned identity");catalog.saveEntry("","camera","Same-name camera","120","wrong format");open(new Bundle());check(!((Button)field("next")).isEnabled(),"no film selected blocks next");for(View v:all(root()))if(v instanceof Button)check(!((Button)v).getText().toString().contains("×"),"no X control");check(all(root()).stream().noneMatch(v->v instanceof Button&&((Button)v).getText().toString().equals("我的库存")),"empty inventory tab hidden");layout();});
        main(()->{wizard.useStock("original:kodak_portra_400");check(draft.getInt("step")==0,"selection stays on editable first step");check(all(root()).stream().noneMatch(v->v instanceof EditText&&"搜索胶片品牌或型号".contentEquals(v.getContentDescription())),"selected card collapses other candidates");input(root(),"拍摄 ISO").setText("800");check(draft.getString("stockId").equals("original:kodak_portra_400"),"ISO edit keeps explicit identity");next();});
        main(()->{check(draft.getInt("step")==1&&host.logs.rolls().isEmpty(),"second step has not saved");check(root().findViewWithTag("cover-choices") instanceof HorizontalScrollView,"multiple covers use a horizontal chooser");layout();Dialog d=(Dialog)field("dialog");d.dispatchKeyEvent(new KeyEvent(0,KeyEvent.KEYCODE_BACK));d.dispatchKeyEvent(new KeyEvent(1,KeyEvent.KEYCODE_BACK));check(draft.getInt("step")==0&&d.isShowing(),"system back goes to first step");next();});
        main(()->{call("chooseCamera",new Class<?>[]{});});
        main(()->{AlertDialog picker=(AlertDialog)field("cameraDialog");input(picker.getWindow().getDecorView(),"搜索相机型号").setText("Same-name");});
        main(()->{AlertDialog picker=(AlertDialog)field("cameraDialog");ListView list=null;for(View v:all(picker.getWindow().getDecorView()))if(v instanceof ListView)list=(ListView)v;check(list!=null&&list.getAdapter().getCount()==1,"unified picker excludes wrong-format gear");list.performItemClick(list.getChildAt(0),0,0);check(draft.getString("cameraId").equals(camera[0]),"owned identity retained");button(root(),"名称与项目（可选）  ⌄").performClick();input(root(),"胶卷名称").setText("两步合成验收");button(root(),"名称与项目（可选）  ⌃").performClick();check(all(root()).stream().noneMatch(v->v instanceof EditText&&"胶卷名称".contentEquals(v.getContentDescription())),"filled optional group can collapse");});
        main(()->{Bundle restore=new Bundle(draft);((Dialog)field("dialog")).dismiss();open(restore);check(draft.getInt("step")==1&&draft.getString("iso").equals("800")&&draft.getString("name").equals("两步合成验收"),"instance draft keeps step/ISO/name/identity");next();check(host.logs.rolls().size()==1&&host.logs.roll(saved).iso==800&&host.logs.roll(saved).camera.equals("Same-name camera"),"one correct roll saved");check(ArtworkLinks.get(host.logs,"roll",saved).equals("original:kodak_portra_400"),"rated ISO saves correct cover link");Bundle replay=new Bundle(draft);open(replay);next();check(host.logs.rolls().size()==1,"same request replay never duplicates");});
        main(()->{open(new Bundle());wizard.useStock("filmer:fujifilmms1001000");check(draft.getString("iso").isEmpty(),"unknown ISO is never fabricated");next();check(draft.getInt("step")==0&&host.logs.rolls().size()==1,"unknown ISO blocks progression");call("close",new Class<?>[]{});check(cancelled==1&&host.logs.rolls().size()==1,"cancel is read only");});
        final CatalogStore.Batch batch=new CatalogStore.Batch();
        main(()->{FilmStockStore.Stock stock=new FilmStockStore(host.logs).choose(host,"original:kodak_portra_400","120");batch.stock=stock.title();batch.stockId=stock.id;batch.format="120";batch.iso=400;batch.capacity=10;batch.quantity=2;batch.threshold=0;batch.location=batch.temperature=batch.expiry=batch.purchaseDate=batch.price=batch.currency=batch.notes="";catalog.saveBatch(batch,0);open(new Bundle());call("selectBatch",new Class<?>[]{CatalogStore.Batch.class},catalog.batch(batch.id));check(draft.getString("capacity").equals("10")&&draft.getString("format").equals("120"),"inventory uses actual capacity and format");check(catalog.batch(batch.id).quantity==2,"selection never consumes stock");button(root(),"胶片库").performClick();check(draft.getString("batch","").isEmpty()&&draft.getString("stock","").isEmpty()&&!((Button)field("next")).isEnabled(),"source switch cannot save a hidden previous batch");call("selectBatch",new Class<?>[]{CatalogStore.Batch.class},catalog.batch(batch.id));next();});
        main(()->{check(catalog.batch(batch.id).quantity==2,"confirmation never consumes stock");Bundle restore=new Bundle(draft);((Dialog)field("dialog")).dismiss();open(restore);next();check(catalog.batch(batch.id).quantity==1&&host.logs.rolls().size()==2,"final save consumes one");check(host.logs.roll(saved).capacity==10&&ArtworkLinks.get(host.logs,"roll",saved).equals(batch.stockId),"inventory capacity and cover exact");open(new Bundle(draft));next();check(catalog.batch(batch.id).quantity==1&&host.logs.rolls().size()==2,"inventory request replay idempotent");});
        main(()->{open(new Bundle());call("selectBatch",new Class<?>[]{CatalogStore.Batch.class},catalog.batch(batch.id));next();CatalogStore.Batch changed=catalog.batch(batch.id);changed.capacity=16;catalog.saveBatch(changed,1);next();check(host.logs.rolls().size()==2&&catalog.batch(batch.id).quantity==1,"changed inventory snapshot rejected without consuming stock");call("close",new Class<?>[]{});});
        main(()->{Bundle legacy=new Bundle();legacy.putInt("step",3);legacy.putString("stock","Old draft");open(legacy);check(draft.getInt("step")==1&&draft.getInt("rollFlow")==2,"four-step draft safely maps to confirmation");call("close",new Class<?>[]{});Bundle oldPicker=new Bundle();oldPicker.putBoolean("inventoryPicker",true);open(oldPicker);check(draft.getInt("step")==0&&draft.getString("source").equals("inventory"),"legacy stock picker maps to integrated inventory");layout();call("close",new Class<?>[]{});host.finish();});
        MainActivity mainActivity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
        main(()->{java.lang.reflect.Method newRoll=MainActivity.class.getDeclaredMethod("newRoll");newRoll.setAccessible(true);newRoll.invoke(mainActivity);java.lang.reflect.Field draftField=MainActivity.class.getDeclaredField("rollDraft"),wizardField=MainActivity.class.getDeclaredField("rollWizard");draftField.setAccessible(true);wizardField.setAccessible(true);RollWizard opened=(RollWizard)wizardField.get(mainActivity);Bundle initial=(Bundle)draftField.get(mainActivity);initial.putString("name","Cancel draft stays");java.lang.reflect.Method close=RollWizard.class.getDeclaredMethod("close");close.setAccessible(true);close.invoke(opened);check(!((Bundle)draftField.get(mainActivity)).getBoolean("wizardOpen",true),"closed main draft marked closed");newRoll.invoke(mainActivity);check(((Bundle)draftField.get(mainActivity)).getString("name").equals("Cancel draft stays"),"main entry reopens same draft");close.invoke((RollWizard)wizardField.get(mainActivity));Bundle instance=new Bundle();mainActivity.onSaveInstanceState(instance);check(!instance.getBundle("rollDraft").getBoolean("wizardOpen",true),"closed status survives instance state");mainActivity.finish();});
        out.putString("stream","PASS: two real steps, square 64/136dp FIT_CENTER covers, no X, disabled empty/unknown ISO, system back, optional collapse, format-safe unified camera picker/owned IDs, draft and legacy mapping, explicit artwork identity at changed ISO, manual/inventory idempotent transactions, stock mutation refusal; footer widths320/375/414/768 heights440/680. Test workspace="+Host.space+"\n");finish(-1,out);
    }catch(Throwable e){out.putString("stream",android.util.Log.getStackTraceString(e));finish(0,out);}}
}
