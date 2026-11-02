package cn.sixnine.filmlog;
import android.app.Instrumentation;
import android.content.*;
import android.os.Bundle;
import android.widget.RemoteViews;
import java.util.UUID;

public final class WidgetInstrumentation extends Instrumentation {
    private void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){waitForIdleSync();Bundle out=new Bundle();try{Context c=getTargetContext();check(c.getPackageName().equals("cn.sixnine.filmlog.media.tests"),"isolation");Workspace.activate(c,"guest");c.deleteDatabase("film-log.db");SharedPreferences prefs=c.getSharedPreferences(ExposureWidget.PREFS,0);prefs.edit().clear().commit();
        try(LogStore logs=new LogStore(c)){String roll=logs.addRoll("Widget test","Gold","",200,1);check(ExposureWidget.available(logs.roll(roll)),"active available");
            try{ExposureWidget.bind(c,987654,roll);throw new AssertionError("unowned widget accepted");}catch(IllegalArgumentException expected){}
            prefs.edit().putString("17.workspace","guest").putString("17.roll",roll).putString("18.workspace","guest").putString("18.roll","other").commit();check(ExposureWidget.target(c,17).equals(roll),"own binding");
            Workspace.activate(c,UUID.randomUUID().toString());check(ExposureWidget.target(c,17).isEmpty(),"foreign workspace blocked");Workspace.activate(c,"guest");
            logs.insert(roll,20000,-10,480,"denied","8","1/125","50mm","","","");check(ExposureWidget.available(logs.roll(roll)),"nominal capacity remains available");check(logs.roll(roll).count==1&&logs.shots(roll).size()==1,"widget checks don't write shots");logs.archive(roll,true);check(!ExposureWidget.available(logs.roll(roll)),"archived blocked");
            new ExposureWidget().onDeleted(c,new int[]{17});check(!prefs.contains("17.roll")&&prefs.getString("18.roll","").equals("other"),"delete only own settings");
            RemoteViews views=new RemoteViews(c.getPackageName(),R.layout.exposure_widget);runOnMainSync(()->check(views.apply(c,null).findViewById(R.id.widget_body)!=null,"remote layout inflates"));
        }prefs.edit().clear().commit();out.putString("stream","PASS: active/nominal/archive eligibility, unowned widget rejection, per-widget binding/deletion, workspace isolation, no implicit exposure writes, RemoteViews inflation\n");finish(-1,out);
    }catch(Throwable error){out.putString("stream",android.util.Log.getStackTraceString(error));finish(0,out);}}
}
