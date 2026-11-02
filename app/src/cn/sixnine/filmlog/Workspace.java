package cn.sixnine.filmlog;
import android.content.*;
import java.util.UUID;
final class Workspace {
    static String valid(String id){if(id.equals("guest"))return id;if(!UUID.fromString(id).toString().equals(id))throw new IllegalArgumentException("资料空间标识无效");return id;}
    static Context root(Context c){Context result=c.getApplicationContext();while(result instanceof WorkspaceContext)result=((WorkspaceContext)result).getBaseContext();return result;}
    static android.content.SharedPreferences registry(Context c){return root(c).getSharedPreferences("workspace-registry",0);}
    static String current(Context c){String id=registry(c).getString("active","guest");try{return valid(id);}catch(Exception invalid){registry(c).edit().putString("invalidActive",String.valueOf(id)).putString("active","guest").putString("activeProfile","").commit();return "guest";}}
    static Context context(Context c){return new WorkspaceContext(c,current(c));}
    static String identity(Context c){if(c instanceof WorkspaceContext)return ((WorkspaceContext)c).workspace;Context app=c.getApplicationContext();return app instanceof WorkspaceContext?((WorkspaceContext)app).workspace:current(c);}
    static void activate(Context c,String id){if(!registry(c).edit().putString("active",valid(id)).commit())throw new IllegalStateException("无法切换本地资料空间");}
}
