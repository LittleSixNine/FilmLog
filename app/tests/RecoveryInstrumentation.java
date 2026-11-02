package cn.sixnine.filmlog;

import android.app.Instrumentation;
import android.os.Bundle;
import android.graphics.Bitmap;
import java.io.*;
import java.util.UUID;

/** Two invocations with force-stop between them exercise a fresh application process. */
public final class RecoveryInstrumentation extends Instrumentation {
    private String phase;
    @Override public void onCreate(Bundle args){super.onCreate(args);phase=args.getString("phase","");start();}
    @Override public void onStart(){waitForIdleSync();Bundle result=new Bundle();try{
        if(!getTargetContext().getPackageName().equals("cn.sixnine.filmlog.media.tests"))throw new AssertionError("isolation");
        android.content.Context context=Workspace.context(getTargetContext());
        if(phase.equals("prepare")){
            context.deleteDatabase("film-log.db");
            try(LogStore logs=new LogStore(context)){
                String roll=logs.addRoll("Process interruption","Test","Camera",100,36),id=UUID.randomUUID().toString();
                ByteArrayOutputStream jpeg=new ByteArrayOutputStream();Bitmap b=Bitmap.createBitmap(24,24,Bitmap.Config.ARGB_8888);b.compress(Bitmap.CompressFormat.JPEG,90,jpeg);b.recycle();
                new ScanStore(context,logs).importJpeg(roll,"committed-before-death.jpg",new ByteArrayInputStream(jpeg.toByteArray()),id);
                logs.getWritableDatabase().execSQL("INSERT INTO import_jobs(id,roll_id,uri,name,state,created_at) VALUES (?,?,?,?,?,?)",new Object[]{id,roll,"content://unavailable/file","committed-before-death.jpg","running",1});
                File orphan=new File(context.getFilesDir(),"scans/00000000-0000-0000-0000-000000000001.jpg");try(FileOutputStream out=new FileOutputStream(orphan)){out.write(jpeg.toByteArray());}
                context.getSharedPreferences("recovery-test",0).edit().putString("roll",roll).putString("id",id).commit();
            }
        }else if(phase.equals("resume")){
            String roll=context.getSharedPreferences("recovery-test",0).getString("roll",""),id=context.getSharedPreferences("recovery-test",0).getString("id","");
            if(new File(context.getFilesDir(),"scans/00000000-0000-0000-0000-000000000001.jpg").exists())throw new AssertionError("startup orphan not cleaned");
            try(LogStore logs=new LogStore(context)){
                ImportQueue.run(context,logs,roll,false);ScanStore scans=new ScanStore(context,logs);
                if(scans.list(roll).size()!=1||!scans.file(id).exists()||ImportQueue.count(logs,roll,"'done'")!=1)throw new AssertionError("resume without duplicate");
            }
        }else throw new AssertionError("phase required");
        result.putString("stream","PASS: process recovery "+phase+"\n");finish(-1,result);
    }catch(Throwable error){result.putString("stream","FAIL: "+error+"\n");finish(0,result);}}
}
