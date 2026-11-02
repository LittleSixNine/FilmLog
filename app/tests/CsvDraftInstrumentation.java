package cn.sixnine.filmlog;
import android.app.Instrumentation;
import android.content.Context;
import android.os.Bundle;
import java.io.*;
import java.util.*;
import org.json.*;

public final class CsvDraftInstrumentation extends Instrumentation {
    private String phase;
    private boolean legacy;
    @Override public void onCreate(Bundle args){super.onCreate(args);phase=args.getString("phase","");legacy=args.getString("legacy","").equals("true");start();}
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static String csv(String id){return "id,roll_name,stock,camera,iso,frame_number,tapped_at_utc,captured_at_utc,offset_seconds,zone_offset_minutes,status,memo,roll_id,roll_capacity\n"+id+",卷,Gold,Camera,200,1,2026-10-22T00:00:05Z,2026-10-21T23:59:55Z,-10,480,denied,\"街头,夜景\n第二行\",same-roll,36\n";}
    private static String normalized(String text)throws Exception{JSONObject value=new JSONObject(text);FilmBackup.normalizeCatalog(value);return value.toString();}
    @Override public void onStart(){waitForIdleSync();Bundle result=new Bundle();try{
        check(getTargetContext().getPackageName().equals("cn.sixnine.filmlog.media.tests"),"isolated package");Context c=Workspace.context(getTargetContext());
        if(phase.equals("prepare")){
            CsvDraft.clear(c);CsvImport.Preview first=CsvImport.parse(new StringReader(csv("one"))),second=CsvImport.parse(new StringReader(csv("two")));
            // Only delimiters outside quotes change for TSV; comma in quoted text remains text.
            String tsv=csv("tab").replace("id,roll_name,stock,camera,iso,frame_number,tapped_at_utc,captured_at_utc,offset_seconds,zone_offset_minutes,status,memo,roll_id,roll_capacity","id\troll_name\tstock\tcamera\tiso\tframe_number\ttapped_at_utc\tcaptured_at_utc\toffset_seconds\tzone_offset_minutes\tstatus\tmemo\troll_id\troll_capacity").replace("tab,卷,Gold,Camera,200,1,2026-10-22T00:00:05Z,2026-10-21T23:59:55Z,-10,480,denied,","tab\t卷\tGold\tCamera\t200\t1\t2026-10-22T00:00:05Z\t2026-10-21T23:59:55Z\t-10\t480\tdenied\t").replace("\",same-roll,36","\"\tsame-roll\t36");
            CsvImport.Preview tab=CsvImport.parse(new StringReader("\ufeff"+tsv));check(tab.data.manifest.getJSONArray("shots").getJSONObject(0).getString("memo").equals("街头,夜景\n第二行"),"TSV BOM multiline quoted comma preserved");
            CsvImport.Preview batch=CsvImport.combine(Arrays.asList(first,second,tab));check(batch.data.count("rolls")==3&&batch.rows==3,"same original roll across files stays separate");
            String before=batch.data.manifest.toString();try{CsvImport.combine(Arrays.asList(first,first));throw new AssertionError("duplicate accepted");}catch(IOException expected){}check(before.equals(batch.data.manifest.toString()),"duplicate batch leaves preview intact");
            CsvImport.review(batch,1,"Reviewed","640","Edited camera","120");JSONObject editor=new JSONObject().put("index",2).put("film","Unsaved").put("iso","bad").put("camera","Draft camera").put("format","110");if(legacy)batch.data.manifest.put("schema",15);CsvDraft.save(c,batch,editor,false);
            getTargetContext().getSharedPreferences("csv-test",0).edit().putString("manifest",batch.data.manifest.toString()).commit();
        }else if(phase.equals("resume")){
            CsvDraft.Saved restored=CsvDraft.load(c);check(restored!=null&&restored.preview.data.manifest.toString().equals(normalized(getTargetContext().getSharedPreferences("csv-test",0).getString("manifest",""))),"fresh process preview and stable IDs");check(restored.review.getString("iso").equals("bad")&&restored.review.getInt("index")==2,"unsaved invalid editor preserved without applying");
            Context other=new WorkspaceContext(getTargetContext(),"00000000-0000-0000-0000-000000000090");check(CsvDraft.load(other)==null,"draft not visible in another workspace");
            CsvDraft.save(c,restored.preview,null,true);try{CsvDraft.load(c);throw new AssertionError("uncertain import offered again");}catch(IOException expected){check(expected.getMessage().contains("核对"),"interrupted commit guidance");}
            CsvDraft.clear(c);check(CsvDraft.load(c)==null,"cancel clears durable preview");
            try(FileOutputStream out=new FileOutputStream(new File(c.getFilesDir(),"csv-import-preview.json"))){out.write("broken".getBytes("UTF-8"));}try{CsvDraft.load(c);throw new AssertionError("corrupt draft accepted");}catch(JSONException expected){}CsvDraft.clear(c);
        }else throw new AssertionError("phase required");result.putString("stream","PASS: CSV batch/draft "+phase+"\n");finish(-1,result);
    }catch(Throwable error){result.putString("stream","FAIL: "+error+"\n");finish(0,result);}}
}
