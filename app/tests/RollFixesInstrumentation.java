package cn.sixnine.filmlog;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.util.*;

/** Private synthetic records only, in the existing isolated UI test identity. */
public final class RollFixesInstrumentation extends Instrumentation {
    int checks;MainActivity activity;LogStore logs;Context context;String prior,roll;RollWizard wizard;Bundle draft;
    interface Action{void run()throws Exception;}
    void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
    void main(Action a)throws Exception{Throwable[] failure={null};runOnMainSync(()->{try{a.run();}catch(Throwable t){failure[0]=t;}});waitForIdleSync();if(failure[0]!=null)throw new RuntimeException(failure[0]);}
    Object field(Object o,String n)throws Exception{java.lang.reflect.Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
    void set(String n,Object value)throws Exception{java.lang.reflect.Field f=MainActivity.class.getDeclaredField(n);f.setAccessible(true);f.set(activity,value);}
    Object call(Object o,String n,Class<?>[] types,Object...args)throws Exception{java.lang.reflect.Method m=o.getClass().getDeclaredMethod(n,types);m.setAccessible(true);return m.invoke(o,args);}
    void snap(View v,String name)throws Exception{Bitmap b=Bitmap.createBitmap(v.getWidth(),v.getHeight(),Bitmap.Config.ARGB_8888);v.draw(new Canvas(b));try(FileOutputStream out=new FileOutputStream(new File(context.getFilesDir(),name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();}
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){Bundle out=new Bundle();context=getTargetContext();prior=Workspace.current(context);try{
        check(context.getPackageName().equals("cn.sixnine.filmlog.ui.tests"),"isolated identity");Workspace.activate(context,UUID.randomUUID().toString());
        activity=(MainActivity)startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();logs=(LogStore)field(activity,"store");
        final String stock="original:kodak_ultramax_400";JSONArray covers=FilmArtwork.covers(activity,stock,"35 mm");check(covers.length()==3,"three UltraMax covers");String third=covers.getJSONObject(2).getString("image");
        main(()->{draft=new Bundle();draft.putString("stockId",stock);draft.putString("stock","Kodak Ultramax 400");draft.putString("iso","400");draft.putString("capacity","1");draft.putInt("rollFlow",2);draft.putInt("step",1);wizard=new RollWizard(activity,logs,draft,()->{},id->roll=id);wizard.show();});
        main(()->{View root=(View)field(wizard,"root");View strip=root.findViewWithTag("cover-choices");check(strip instanceof HorizontalScrollView,"native horizontal strip");check(root.findViewWithTag("cover-choice:2").performClick(),"select third cover");check(draft.getString("coverImage").equals(third),"draft chosen image");check(logs.rolls().isEmpty(),"selection writes no roll");snap(((Dialog)field(wizard,"dialog")).getWindow().getDecorView(),"cover-picker");call(wizard,"save",new Class<?>[]{});check(roll!=null,"wizard saves");check(ArtworkLinks.coverImage(logs,"roll",roll).equals(third),"chosen cover persisted in transaction");check(ArtworkLinks.cover(activity,logs,"roll",roll,"35 mm").getString("image").equals(third),"shared renderer chosen image");check(WidgetCard.cover(activity,logs,logs.roll(roll),128)!=null,"widget renderer");try{ArtworkLinks.setCover(activity,logs,"roll",roll,"120",third);throw new AssertionError("wrong format accepted");}catch(IllegalArgumentException expected){}check(ArtworkLinks.coverImage(logs,"roll",roll).equals(third),"invalid selection preserves cover");});
        main(()->{logs.insert(roll,10000,0,480,"denied","","","","","","");set("rollId",roll);set("screen","roll");call(activity,"render",new Class<?>[]{});});
        main(()->{View root=activity.getWindow().getDecorView();Button quick=root.findViewWithTag("quick-record"),meter=root.findViewWithTag("meter-record"),rewind=root.findViewWithTag("rewind-roll");check(quick.getParent()==meter.getParent()&&quick.getParent()==rewind.getParent(),"three buttons share row");check(((LinearLayout)quick.getParent()).getOrientation()==LinearLayout.HORIZONTAL,"horizontal row");check(quick.getLeft()<meter.getLeft()&&meter.getLeft()<rewind.getLeft(),"rewind on right");check(quick.getTop()==rewind.getTop(),"aligned tops");check(quick.getCurrentTextColor()==activity.getColor(R.color.ui_accent),"quick secondary");check(rewind.getCurrentTextColor()==activity.getColor(R.color.ui_on_accent),"rewind primary");for(Button b:new Button[]{quick,meter,rewind})check(b.getWidth()>0&&b.getLayout().getEllipsisCount(0)==0&&b.getLayout().getHeight()<=b.getHeight()-b.getCompoundPaddingTop()-b.getCompoundPaddingBottom(),"visible action text without vertical clipping");snap(root,"full-roll-actions");});
        main(()->{ByteArrayOutputStream archive=new ByteArrayOutputStream();FilmBackup.write(activity,logs,archive);try(FilmBackup.Prepared p=FilmBackup.inspect(activity,new ByteArrayInputStream(archive.toByteArray()))){check(p.manifest.getInt("schema")==25,"new backup schema");FilmBackup.restore(activity,logs,p);int selected=0;for(LogStore.Roll r:logs.rolls())if(ArtworkLinks.coverImage(logs,"roll",r.id).equals(third))selected++;check(selected==2,"backup roundtrip keeps selected cover");JSONObject link=p.manifest.getJSONArray("artwork_links").getJSONObject(0);link.put("cover_image","film-artwork/invalid.png");int before=logs.rolls().size();try{FilmBackup.restore(activity,logs,p);throw new AssertionError("invalid backup cover accepted");}catch(IllegalArgumentException expected){}check(logs.rolls().size()==before,"invalid restore atomic");link.remove("cover_image");p.manifest.put("schema",24);FilmBackup.normalizeCatalog(p.manifest);check(link.getString("cover_image").isEmpty(),"old backup defaults cover");FilmBackup.restore(activity,logs,p);check(logs.rolls().size()==3,"old backup restores");}});
        main(()->{CatalogStore catalog=new CatalogStore(logs);catalog.details(roll,"120","","");check(ArtworkLinks.coverImage(logs,"roll",roll).isEmpty(),"format change clears incompatible selection");catalog.details(roll,"35 mm","","");ArtworkLinks.setCover(activity,logs,"roll",roll,"35 mm",third);});
        // A separate workspace exercises an actual v23 SQLite upgrade with a populated link.
        Context old=new WorkspaceContext(Workspace.root(context),UUID.randomUUID().toString());
        try(LogStore migration=new LogStore(old)){String id=migration.addRoll("Legacy","Kodak Ultramax 400","",400,36);new CatalogStore(migration).details(id,"35 mm","","");ArtworkLinks.set(old,migration,"roll",id,stock);android.database.sqlite.SQLiteDatabase db=migration.getWritableDatabase();db.execSQL("ALTER TABLE artwork_links RENAME TO old_artwork_links");ArtworkLinks.create(db);db.execSQL("INSERT INTO artwork_links SELECT id,owner_kind,owner_id,stock_id FROM old_artwork_links");db.execSQL("DROP TABLE old_artwork_links");db.setVersion(23);}
        try(LogStore migrated=new LogStore(old)){check(migrated.getReadableDatabase().getVersion()==24,"v23 upgrade");LogStore.Roll legacy=migrated.rolls().get(0);check(ArtworkLinks.coverImage(migrated,"roll",legacy.id).isEmpty(),"legacy default");check(ArtworkLinks.cover(old,migrated,"roll",legacy.id,"35 mm").getString("image").equals(covers.getJSONObject(0).getString("image")),"legacy keeps first cover");}
        // A real PendingIntent click must open details, while record still opens its draft.
        context.getSharedPreferences(ExposureWidget.PREFS,0).edit().putString("931.workspace",Workspace.current(context)).putString("931.roll",roll).putString("931.style",ExposureWidget.FROST).commit();
        for(int target:new int[]{R.id.widget_configure,R.id.widget_body,R.id.widget_action}){
            ActivityMonitor monitor=addMonitor(MainActivity.class.getName(),null,false);
            main(()->{ExposureWidget.views(activity,931,320,140).apply(activity,null).findViewById(target).performClick();});MainActivity launched=(MainActivity)waitForMonitorWithTimeout(monitor,4000);removeMonitor(monitor);check(launched!=null,"widget launches MainActivity");waitForIdleSync();
            main(()->{check(roll.equals(launched.getIntent().getStringExtra("open_roll")),"widget opens bound roll");check(launched.getIntent().getBooleanExtra("open_editor",false)==(target==R.id.widget_action),"only record opens editor");launched.finish();});
        }
        main(()->activity.finish());out.putString("stream","PASS "+checks+" cover choice, persistence, backup, migration, action geometry checks\n");finish(-1,out);
    }catch(Throwable t){out.putString("stream",android.util.Log.getStackTraceString(t));finish(0,out);}finally{Workspace.activate(context,prior);}}
}
