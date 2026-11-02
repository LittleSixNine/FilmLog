package cn.sixnine.filmlog;

import android.app.Instrumentation;
import android.os.Bundle;
import android.net.Uri;
import android.graphics.Bitmap;
import java.io.*;
import java.nio.file.Files;
import java.util.*;

/** Isolated storage checks for preview/confirm/cancel and interrupted association. */
public final class ImportPreviewInstrumentation extends Instrumentation {
    private void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){waitForIdleSync();Bundle out=new Bundle();try{
        android.content.Context c=getTargetContext();check(c.getPackageName().equals("cn.sixnine.filmlog.media.tests"),"isolation");
        c.deleteDatabase("film-log.db");LogStore logs=new LogStore(c);
        String roll=logs.addRoll("Preview","Test","Camera",400,36),other=logs.addRoll("Other","Test","Camera",100,36);
        String first=logs.insert(roll,20000,-10,480,"denied","","","","memo","",""),second=logs.insert(roll,30000,-10,480,"denied","","","","","","");
        String foreign=logs.insert(other,40000,-10,480,"denied","","","","","","");
        File source=new File(c.getCacheDir(),"preview-original.jpg");Bitmap bitmap=Bitmap.createBitmap(64,48,Bitmap.Config.ARGB_8888);
        try(OutputStream stream=new FileOutputStream(source)){bitmap.compress(Bitmap.CompressFormat.JPEG,90,stream);}bitmap.recycle();byte[] original=Files.readAllBytes(source.toPath());
        Uri uri=Uri.fromFile(source);ScanStore files=new ScanStore(c,logs);
        ImportQueue.preview(c,logs,roll,Collections.singletonList(uri),"");
        check(files.list(roll).isEmpty()&&ImportQueue.previews(logs,roll).size()==1,"selection has no scan");
        Bitmap preview=ScanImages.read(c,uri,30);check(preview.getWidth()<=30&&preview.getHeight()<=30,"bounded URI preview");preview.recycle();
        String id=ImportQueue.previews(logs,roll).get(0).id;Map<String,String> links=new LinkedHashMap<>();links.put(id,first);ImportQueue.savePreview(logs,roll,links);
        logs.close();logs=new LogStore(c);files=new ScanStore(c,logs);
        check(ImportQueue.previews(logs,roll).get(0).shotId.equals(first)&&files.list(roll).isEmpty(),"preview survives reopen without scan");
        ImportQueue.run(c,logs,roll,false);check(files.list(roll).isEmpty(),"resume cannot auto-import preview");
        ImportQueue.cancelPreview(c,logs,roll);check(ImportQueue.previews(logs,roll).isEmpty()&&files.list(roll).isEmpty(),"cancel leaves no scan");
        ImportQueue.preview(c,logs,roll,Arrays.asList(uri,uri),"");ArrayList<ScanStore.Scan> draft=ImportQueue.previews(logs,roll);links.clear();for(ScanStore.Scan scan:draft)links.put(scan.id,first);ImportQueue.savePreview(logs,roll,links);
        try{ImportQueue.confirmPreview(logs,roll);throw new AssertionError("duplicate frame accepted");}catch(IllegalArgumentException expected){}
        check(ImportQueue.count(logs,roll,"'preview'")==2&&files.list(roll).isEmpty(),"duplicate validation atomic");
        links.put(draft.get(1).id,foreign);try{ImportQueue.savePreview(logs,roll,links);throw new AssertionError("foreign frame accepted");}catch(IllegalArgumentException expected){}
        links.put(draft.get(1).id,second);ImportQueue.savePreview(logs,roll,links);ImportQueue.confirmPreview(logs,roll);
        check(files.list(roll).isEmpty()&&ImportQueue.count(logs,roll,"'queued'")==2,"confirm schedules before copying");
        ImportQueue.run(c,logs,roll,false);check(files.list(roll).size()==2&&ImportQueue.count(logs,roll,"'done'")==2,"confirmed copies and associates: "+ImportQueue.errors(logs,roll));
        for(ScanStore.Scan scan:files.list(roll))check(scan.shotId.equals(links.get(scan.id)),"chosen association applied");
        ImportQueue.run(c,logs,roll,true);check(files.list(roll).size()==2,"retry doesn't duplicate completed copies");
        ImportQueue.preview(c,logs,roll,Collections.singletonList(uri),"");String conflicting=ImportQueue.previews(logs,roll).get(0).id;links.clear();links.put(conflicting,first);ImportQueue.savePreview(logs,roll,links);
        try{ImportQueue.confirmPreview(logs,roll);throw new AssertionError("occupied frame accepted");}catch(IllegalArgumentException expected){}
        ImportQueue.removePreview(c,logs,roll,conflicting);check(ImportQueue.previews(logs,roll).isEmpty(),"remove preview only");
        // Resume a copied asset whose association transaction hadn't committed yet.
        String third=logs.insert(roll,50000,-10,480,"denied","","","","","","");
        ImportQueue.preview(c,logs,roll,Collections.singletonList(uri),"");String resumed=ImportQueue.previews(logs,roll).get(0).id;links.clear();links.put(resumed,third);ImportQueue.savePreview(logs,roll,links);ImportQueue.confirmPreview(logs,roll);
        try(InputStream in=new FileInputStream(source)){files.importJpeg(roll,"before-death.jpg",in,resumed);}
        logs.getWritableDatabase().execSQL("UPDATE import_jobs SET state='running',uri='content://unavailable/file' WHERE id=?",new Object[]{resumed});
        logs.close();logs=new LogStore(c);files=new ScanStore(c,logs);ImportQueue.run(c,logs,roll,false);
        check(files.list(roll).size()==3&&ImportQueue.count(logs,roll,"'done'")==3,"resume copied file without source access");
        for(ScanStore.Scan scan:files.list(roll))if(scan.id.equals(resumed))check(scan.shotId.equals(third),"resume applies association");
        check(Arrays.equals(original,Files.readAllBytes(source.toPath())),"original bytes untouched");
        // State changed after confirmation: keep both copies and never steal the new owner.
        String fourth=logs.insert(roll,60000,-10,480,"denied","","","","","","");
        ImportQueue.preview(c,logs,roll,Collections.singletonList(uri),"");String raced=ImportQueue.previews(logs,roll).get(0).id;links.clear();links.put(raced,fourth);ImportQueue.savePreview(logs,roll,links);ImportQueue.confirmPreview(logs,roll);
        ScanStore.Scan owner;try(InputStream in=new FileInputStream(source)){owner=files.importJpeg(roll,"owner.jpg",in);}files.assignImported(roll,owner.id,fourth);
        ImportQueue.run(c,logs,roll,false);check(ImportQueue.count(logs,roll,"'failed'")==1&&files.list(roll).size()==5,"raced association retains unassigned copy");
        for(ScanStore.Scan scan:files.list(roll)){if(scan.id.equals(raced))check(scan.shotId.isEmpty(),"new scan unassigned after conflict");if(scan.id.equals(owner.id))check(scan.shotId.equals(fourth),"existing association not stolen");}
        ImportQueue.dismissFailures(c,logs,roll);
        ImportQueue.preview(c,logs,roll,Collections.singletonList(uri),owner.id);String replaced=ImportQueue.previews(logs,roll).get(0).id;
        check(ImportQueue.replacement(logs,roll,replaced).equals(owner.id),"replacement draft identifies original");
        ImportQueue.confirmPreview(logs,roll);ImportQueue.run(c,logs,roll,false);
        check(files.list(roll,true).size()==1&&files.file(owner.id).isFile(),"replacement preserves old copy");
        for(ScanStore.Scan scan:files.list(roll))if(scan.id.equals(replaced))check(scan.shotId.equals(fourth),"replacement inherits association");
        // A v7 database has no target column; upgrade preserves existing records and old jobs.
        logs.getWritableDatabase().execSQL("ALTER TABLE import_jobs RENAME TO import_jobs_v8");
        logs.getWritableDatabase().execSQL("CREATE TABLE import_jobs AS SELECT id,roll_id,uri,name,state,error,replacement_id,created_at FROM import_jobs_v8");
        logs.getWritableDatabase().execSQL("DROP TABLE import_jobs_v8");for(String table:CatalogStore.TABLES)logs.getWritableDatabase().execSQL("DROP TABLE "+table);logs.getWritableDatabase().execSQL("DROP TABLE film_stocks");logs.getWritableDatabase().execSQL("DROP TABLE reference_photos");logs.getWritableDatabase().setVersion(7);logs.close();
        logs=new LogStore(c);check(logs.getReadableDatabase().getVersion()==24&&logs.shots(roll).size()==4&&new ScanStore(c,logs).list(roll).size()==5,"v7 upgrade retains records");
        check(ImportQueue.count(logs,roll,"'done'")==4,"old queue retained after upgrade");logs.close();
        out.putString("stream","PASS: preview/cancel/reopen/no-auto-import/duplicate/foreign/occupied/confirm/retry/interrupted-association/source-preservation/v7-upgrade\n");finish(-1,out);
    }catch(Throwable e){out.putString("stream",android.util.Log.getStackTraceString(e));finish(0,out);}}
}
