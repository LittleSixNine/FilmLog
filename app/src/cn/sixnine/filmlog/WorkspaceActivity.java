package cn.sixnine.filmlog;
import android.app.Activity;
import android.content.*;
import android.view.MotionEvent;
abstract class WorkspaceActivity extends Activity implements SharedPreferences.OnSharedPreferenceChangeListener {
    private boolean rebinding;
    @Override protected void attachBaseContext(Context base){super.attachBaseContext(Workspace.context(base));}
    private boolean rebind(){if(rebinding)return true;if(!Workspace.identity(getBaseContext()).equals(Workspace.current(this))){rebinding=true;startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));finish();return true;}return false;}
    @Override protected void onStart(){super.onStart();Workspace.registry(this).registerOnSharedPreferenceChangeListener(this);rebind();}
    @Override protected void onStop(){Workspace.registry(this).unregisterOnSharedPreferenceChangeListener(this);super.onStop();}
    @Override public void onSharedPreferenceChanged(SharedPreferences prefs,String name){if("active".equals(name))rebind();}
    @Override public boolean dispatchTouchEvent(MotionEvent event){return rebind()||super.dispatchTouchEvent(event);}
}
