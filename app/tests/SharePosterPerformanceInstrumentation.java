package cn.sixnine.filmlog;

import android.app.Instrumentation;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.*;

/** 12MP synthetic stress evidence; this is not a real-photo phone benchmark. */
public final class SharePosterPerformanceInstrumentation extends Instrumentation {
    Context context;File folder;int checks;
    void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){Bundle result=new Bundle();boolean passed=false;try{
        check(getTargetContext().getPackageName().endsWith(".tests"),"isolated package");check(Build.VERSION.SDK_INT>=29,"MediaStore stress requires API29+");
        context=new WorkspaceContext(getTargetContext(),UUID.randomUUID().toString());folder=new File(context.getCacheDir(),"stress-sources");check(folder.mkdirs(),"source folder");File first=create();
        ArrayList<File> sources=new ArrayList<>();sources.add(first);for(int n=1;n<36;n++){File copy=new File(folder,n+".jpg");Files.copy(first.toPath(),copy.toPath());sources.add(copy);}
        JSONObject report=new JSONObject().put("synthetic",true).put("real_photo",false).put("cold_storage",false).put("device",Build.MODEL).put("sdk",Build.VERSION.SDK_INT).put("input_width",4000).put("input_height",3000).put("source_file_bytes",first.length()).put("distinct_files",sources.size());JSONArray cases=new JSONArray();
        for(int[] config:new int[][]{{36,1440,0,20},{36,2400,0,20},{72,2400,0,3},{72,2400,1,3},{1,2400,0,3}}){int count=config[0],width=config[1],runs=config[3];boolean vertical=config[2]==1;ArrayList<SharePosterData.Item> items=new ArrayList<>();for(int n=0;n<count;n++){SharePosterData.Item i=new SharePosterData.Item("stress-"+n,"12MP测试照片",sources.get(n%sources.size()),null,n+1,0,0,false);i.fill=true;items.add(i);}SharePosterData.Spec spec=new SharePosterData.Spec(items,count>1,vertical,true,"12MP STRESS","","","",width);
            long[] times=new long[runs];JSONArray runsJson=new JSONArray();long nativeBefore=Debug.getNativeHeapAllocatedSize(),pssBefore=Debug.getPss();
            for(int n=0;n<runs;n++){SharePosterOutput.Job job=SharePosterOutput.start(context,spec,null,false);long until=SystemClock.uptimeMillis()+120000;while(!job.done&&SystemClock.uptimeMillis()<until)SystemClock.sleep(5);check(job.done&&job.success,"stress output success: "+job.message);times[n]=job.elapsedMillis;runsJson.put(job.elapsedMillis);for(Uri uri:job.results)check(context.getContentResolver().delete(uri,null,null)==1,"stress output cleanup");}
            final AtomicBoolean sampling=new AtomicBoolean(true);final long[] peaks={Debug.getPss(),Debug.getNativeHeapAllocatedSize()};Thread sampler=new Thread(()->{while(sampling.get()){peaks[0]=Math.max(peaks[0],Debug.getPss());peaks[1]=Math.max(peaks[1],Debug.getNativeHeapAllocatedSize());SystemClock.sleep(20);}},"poster-test-memory");sampler.start();
            SharePosterOutput.Job measured=SharePosterOutput.start(context,spec,null,false);while(!measured.done)SystemClock.sleep(5);sampling.set(false);sampler.join();check(measured.success,"memory sample output success");for(Uri uri:measured.results)context.getContentResolver().delete(uri,null,null);
            long nativeAfter=Debug.getNativeHeapAllocatedSize(),pssAfter=Debug.getPss();Arrays.sort(times);JSONObject entry=new JSONObject().put("count",count).put("width",width).put("vertical",vertical).put("fill",true).put("warm_runs_ms",runsJson).put("p50_ms",times[(int)Math.ceil(runs*.50)-1]).put("p95_ms",times[(int)Math.ceil(runs*.95)-1]).put("max_ms",times[runs-1]).put("native_before_bytes",nativeBefore).put("native_peak_bytes",peaks[1]).put("native_after_bytes",nativeAfter).put("pss_before_kib",pssBefore).put("pss_peak_kib",peaks[0]).put("pss_after_kib",pssAfter).put("memory_sampling_ms",20).put("memory_run_elapsed_ms",measured.elapsedMillis);cases.put(entry);
        }
        report.put("cases",cases);File evidence=new File(getTargetContext().getFilesDir(),"share-poster-evidence");evidence.mkdirs();try(Writer out=new OutputStreamWriter(new FileOutputStream(new File(evidence,"stress-12mp.json")),"UTF-8")){out.write(report.toString(2));}
        result.putString("stream","PASS "+checks+" synthetic12MP stress assertions; 20 warm runs per36-photo size, extra72-photo/single-photo runs and separately sampled PSS/native peaks. No real-photo device claims.\n");passed=true;
    }catch(Throwable e){result.putString("stream",android.util.Log.getStackTraceString(e));}finally{if(folder!=null){File[] files=folder.listFiles();if(files!=null)for(File f:files)f.delete();folder.delete();}}finish(passed?-1:0,result);}
    File create()throws Exception{File file=new File(folder,"0.jpg");Bitmap b=Bitmap.createBitmap(4000,3000,Bitmap.Config.ARGB_8888);int seed=0x69fa123;int[] row=new int[4000];for(int y=0;y<3000;y++){for(int x=0;x<4000;x++){seed^=seed<<13;seed^=seed>>>17;seed^=seed<<5;int noise=(seed&63)-32;int r=Math.max(0,Math.min(255,40+x*180/4000+noise)),g=Math.max(0,Math.min(255,40+y*180/3000+noise)),blue=Math.max(0,Math.min(255,100+noise));row[x]=0xff000000|r<<16|g<<8|blue;}b.setPixels(row,0,4000,0,y,4000,1);}try(FileOutputStream out=new FileOutputStream(file)){check(b.compress(Bitmap.CompressFormat.JPEG,93,out),"12MP grain fixture");}finally{b.recycle();}return file;}
}
