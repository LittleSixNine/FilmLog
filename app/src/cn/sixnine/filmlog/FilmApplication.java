package cn.sixnine.filmlog;

import android.app.Application;
import android.content.Context;
import android.database.Cursor;
import android.util.Log;
import java.io.File;
import java.util.*;

public final class FilmApplication extends Application {
    @Override public void onCreate(){super.onCreate();ForegroundLocation.install(this);
        // Startup only: no import/export worker can still be writing in this new process.
        try(LogStore logs=new LogStore(Workspace.context(this))){cleanupFiles(Workspace.context(this),logs);PendingExposure.cleanup(Workspace.context(this));}catch(Exception error){Log.w("FilmLog","Private file recovery deferred",error);}
        getSystemService(android.app.NotificationManager.class).cancel(73); // Retired darkroom notice; saved data stays untouched.
        try{AlbumOutput.recover(Workspace.context(this));}catch(Exception error){Log.w("FilmLog","Album recovery deferred",error);}
    }
    static void cleanupFiles(Context context,LogStore logs){
        Set<String> retained=new HashSet<>();
        try(Cursor cursor=logs.getReadableDatabase().rawQuery("SELECT id FROM scans UNION SELECT id FROM reference_photos",null)){while(cursor.moveToNext())retained.add(cursor.getString(0)+".jpg");}
        File[] files=new File(context.getFilesDir(),"scans").listFiles();
        if(files!=null)for(File file:files){String name=file.getName();if(name.matches("[0-9a-fA-F-]{36}\\.(jpg|part)")&&!retained.contains(name))file.delete();}
        File[] cache=context.getCacheDir().listFiles();
        if(cache!=null)for(File file:cache){String name=file.getName();if((name.matches("restore-[0-9a-fA-F-]{36}")||name.matches("computer-import-[0-9a-fA-F-]{36}"))&&file.isDirectory()){
                File[] children=file.listFiles();if(children!=null)for(File child:children)if(child.isFile())child.delete();file.delete();
            }else if(file.isFile()&&(name.startsWith("scan-export-")||name.startsWith("scan-xmp-"))&&name.endsWith(".jpg"))file.delete();}
    }
}
