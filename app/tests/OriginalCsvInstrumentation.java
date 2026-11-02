package cn.sixnine.filmlog;
import android.app.Instrumentation;
import android.os.Bundle;
import java.io.*;
import java.time.*;
import java.util.*;
import org.json.*;

public final class OriginalCsvInstrumentation extends Instrumentation {
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static void bad(String value)throws Exception {try{OriginalCsv.date(value,0,ZoneId.of("UTC"));throw new AssertionError("invalid or ambiguous date accepted: "+value);}catch(IOException expected){}}
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){waitForIdleSync();Bundle out=new Bundle();try{
        check(getTargetContext().getPackageName().equals("cn.sixnine.filmlog.media.tests"),"isolation");ZoneId sh=ZoneId.of("Asia/Shanghai"),utc=ZoneId.of("UTC");
        long time=Instant.parse("2026-10-25T06:30:05Z").toEpochMilli();
        for(String value:new String[]{"2026-10-25 14:30:05","2026/10/25 14:30:05","2026年10月25日 下午 2:30:05","25/10/2026 14:30:05","10/25/2026 14:30:05","25 Oct 2026 14:30:05","October 25, 2026 14:30:05","2026-10-25T14:30:05+08:00"})check(OriginalCsv.date(value,0,sh).millis==time,"date normalization "+value);
        check(OriginalCsv.date("2026-10-25T14:30:05+08:00",0,utc).millis==time&&OriginalCsv.date("2026-10-25T14:30:05+08:00",0,utc).zone==480,"explicit offset wins");
        check(OriginalCsv.date("1792919405",0,sh).millis==1792919405000L&&OriginalCsv.date("1792919405000",0,sh).millis==1792919405000L,"epoch units");
        check(OriginalCsv.date("45292.5",0,sh).millis==LocalDateTime.of(2024,1,1,12,0).atZone(sh).toInstant().toEpochMilli(),"Excel date fraction");
        check(OriginalCsv.date("03/04/2026",1,sh).millis==LocalDate.of(2026,4,3).atStartOfDay(sh).toInstant().toEpochMilli()&&OriginalCsv.date("03/04/2026",2,sh).millis==LocalDate.of(2026,3,4).atStartOfDay(sh).toInstant().toEpochMilli(),"explicit date ordering");
        check(OriginalCsv.date("03/04/99",1,sh).millis==LocalDate.of(1999,4,3).atStartOfDay(sh).toInstant().toEpochMilli(),"two digit year past-century boundary");
        for(String value:new String[]{"","03/04/2026","2026-02-30","2026-10-25 25:10","2026-10","today","2026-10-25 2:10pm garbage"})bad(value);
        for(String value:new String[]{"2026-03-08 02:30","2026-11-01 01:30"})try{OriginalCsv.date(value,0,ZoneId.of("America/New_York"));throw new AssertionError("DST gap/overlap accepted");}catch(IOException expected){}
        JSONObject aliases;try(InputStream in=getTargetContext().getAssets().open("original-csv-headers.json")){ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] block=new byte[4096];int n;while((n=in.read(block))!=-1)bytes.write(block,0,n);aliases=new JSONObject(bytes.toString("UTF-8"));}
        for(int language=0;language<11;language++){StringBuilder header=new StringBuilder();for(String field:new String[]{"roll","film","iso","camera","frame","date"}){if(header.length()>0)header.append(',');header.append('"').append(aliases.getJSONArray(field).getString(language).replace("\"","\"\"")).append('"');}CsvImport.Preview localized=CsvImport.parse(new StringReader(header+"\nTrip,HP5,400,F3,1,2026-10-25 14:30:05\n"),0,sh);check(localized.rows==1,"all eleven original header languages: "+language);}
        CsvImport.Preview quoted=CsvImport.parse(new StringReader("Roll;Film;#;Date;Note\nTrip;HP5;1;2026-10-25;\"line1;part2\nline2\"\n"),0,sh);check(quoted.data.manifest.getJSONArray("shots").getJSONObject(0).getString("memo").equals("line1;part2\nline2"),"quoted semicolon and newline");
        String csv="胶卷;胶片型号;画幅;ISO;相机;序号;日期;光圈;快门速度;曝光补偿 (EV);镜头;备注;纬度;经度;地点;天气;距离 (米);状态;增感/减感 (档);已过期;胶卷编号;冲洗店;送洗日期;取回日期;项目;胶卷费用;冲洗费用;货币;Pellica ID;自定义\n旅途;Gold 200;35 mm;200;M6;1;2026-10-25 14:30:05;f/8;1/125s;+1;35mm;原备注;31.2;121.4;上海;晴;3;拍摄中;+1;否;R2026-125;Lab;2026-10-26;;原项目;50;25;CNY;source-A;保留我\n";
        CsvImport.Preview p=CsvImport.parse(new StringReader(csv),0,sh);check(p.rows==1&&p.data.count("rolls")==1,"original Chinese semicolon file");JSONObject roll=p.data.manifest.getJSONArray("rolls").getJSONObject(0),shot=p.data.manifest.getJSONArray("shots").getJSONObject(0),detail=p.data.manifest.getJSONArray("roll_details").getJSONObject(0);
        check(roll.getString("state").equals("developing")&&roll.getString("lab_number").equals("R2026-125")&&roll.getInt("iso")==200&&roll.getString("film_cost").equals("50")&&detail.getInt("push_pull_stops")==1&&detail.getString("format").equals("35 mm"),"roll metadata conversion");
        check(shot.getLong("tapped_at")==time&&shot.getLong("captured_at")==time&&shot.getInt("offset_seconds")==0&&shot.getInt("zone_offset_minutes")==480,"historical import never applies tap delay");
        check(shot.getDouble("latitude")==31.2&&shot.getString("memo").contains("天气: 晴")&&shot.getString("memo").contains("自定义: 保留我")&&shot.getString("memo").contains("项目: 原项目")&&shot.getString("ev").equals("+1")&&shot.getString("aperture").equals("8")&&shot.getString("shutter").equals("1/125"),"coordinates and unmapped data preserved");
        CsvImport.Preview empty=CsvImport.parse(new StringReader("Roll,Film,Format,ISO,Camera,#,Date\nEmpty,HP5,120,400,F3,,\n"),0,sh);check(empty.rows==0&&empty.data.count("rolls")==1&&empty.data.manifest.getJSONArray("rolls").getJSONObject(0).getInt("capacity")==12,"empty 120 roll keeps zero exposures and existing format capacity rule");
        CsvImport.Preview english=CsvImport.parse(new StringReader("Roll,Film,ISO,Camera,#,Date,Latitude,Longitude,Compensation (EV)\nTrip,HP5,400,F3,1,2026-10-25T06:30:05Z,,,0\n"),0,sh);check(english.rows==1&&english.legacy,"English columns");
        CsvImport.Preview combined=CsvImport.combine(Arrays.asList(p,english));check(combined.data.count("rolls")==2&&combined.data.manifest.getString("import_notice").contains("Asia/Shanghai"),"combined preview retains notice");
        for(String malformed:new String[]{csv.replace("31.2;121.4","31.2;"),csv.replace("31.2;121.4","91;121.4"),csv.replace("2026-10-25 14:30:05","03/04/2026"),csv+csv.substring(csv.indexOf('\n')+1),csv.replace("200;M6","999999;M6")})try{CsvImport.parse(new StringReader(malformed),0,sh);throw new AssertionError("invalid import accepted");}catch(Exception expected){if(expected instanceof RuntimeException&&!(expected instanceof IllegalArgumentException)&&!(expected instanceof JSONException))throw expected;}
        getTargetContext().deleteDatabase("film-log.db");try(LogStore logs=new LogStore(getTargetContext())){String old=logs.addRoll("Existing","Old","Camera",400,36);FilmBackup.restore(getTargetContext(),logs,p.data);check(logs.rolls().size()==2&&logs.roll(old).name.equals("Existing"),"import appends and preserves original identity");LogStore.Roll imported=null;for(LogStore.Roll r:logs.rolls())if(!r.id.equals(old))imported=r;check(imported!=null&&!imported.id.equals(roll.getString("id"))&&logs.shots(imported.id).get(0).captured==time&&new CatalogStore(logs).detail(imported.id).pushPull==1,"IDs remapped and time/push preserved");ByteArrayOutputStream bytes=new ByteArrayOutputStream();FilmBackup.write(getTargetContext(),logs,bytes);try(FilmBackup.Prepared read=FilmBackup.inspect(getTargetContext(),new ByteArrayInputStream(bytes.toByteArray()))){check(read.count("shots")==1,"converted records roundtrip");}}
        out.putString("stream","PASS: original CSV language/semicolon/quoted fields, ISO/epoch/Excel/localized dates, ambiguity and DST refusal, explicit zone/order, metadata and unknown-column preservation, batch validation, append-only IDs, backup roundtrip\n");finish(-1,out);
    }catch(Throwable failure){out.putString("stream",android.util.Log.getStackTraceString(failure));finish(0,out);}}
}
