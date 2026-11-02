package cn.sixnine.filmlog;
import android.content.*;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import java.io.File;

/** Frozen identity: an old worker keeps writing its original workspace after a profile switch. */
final class WorkspaceContext extends ContextWrapper {
    final String workspace;
    WorkspaceContext(Context base,String workspace){super(base instanceof WorkspaceContext?((WorkspaceContext)base).getBaseContext():base);this.workspace=Workspace.valid(workspace);}
    private String name(String name){return workspace.equals("guest")?name:"workspace-"+workspace+"-"+name;}
    private File directory(File root){if(workspace.equals("guest"))return root;File result=new File(new File(root,"workspaces"),workspace);if(!result.isDirectory()&&!result.mkdirs())throw new IllegalStateException("无法打开本地资料空间");return result;}
    @Override public Context getApplicationContext(){Context app=getBaseContext().getApplicationContext();if(app==getBaseContext())return this;return new WorkspaceContext(app,workspace);}
    @Override public File getFilesDir(){return directory(getBaseContext().getFilesDir());}
    @Override public File getCacheDir(){return directory(getBaseContext().getCacheDir());}
    @Override public SharedPreferences getSharedPreferences(String name,int mode){return getBaseContext().getSharedPreferences(name.equals("meter-calibration")?name:name(name),mode);}
    @Override public File getDatabasePath(String name){return getBaseContext().getDatabasePath(name(name));}
    @Override public SQLiteDatabase openOrCreateDatabase(String name,int mode,SQLiteDatabase.CursorFactory factory){return getBaseContext().openOrCreateDatabase(name(name),mode,factory);}
    @Override public SQLiteDatabase openOrCreateDatabase(String name,int mode,SQLiteDatabase.CursorFactory factory,DatabaseErrorHandler errors){return getBaseContext().openOrCreateDatabase(name(name),mode,factory,errors);}
    @Override public boolean deleteDatabase(String name){return getBaseContext().deleteDatabase(name(name));}
}
