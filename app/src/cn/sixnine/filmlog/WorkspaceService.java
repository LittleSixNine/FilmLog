package cn.sixnine.filmlog;
import android.app.Service;
import android.content.Context;
abstract class WorkspaceService extends Service {
    @Override protected void attachBaseContext(Context base){super.attachBaseContext(Workspace.context(base));}
}
