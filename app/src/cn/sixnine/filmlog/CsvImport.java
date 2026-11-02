package cn.sixnine.filmlog;

import org.json.*;
import java.io.*;
import java.time.Instant;
import java.util.*;

final class CsvImport {
    static final class Preview {FilmBackup.Prepared data;boolean legacy;int rows;}
    static Preview parse(Reader source)throws Exception{return parse(source,0,java.time.ZoneId.systemDefault());}
    static Preview parse(Reader source,int dateOrder,java.time.ZoneId zone)throws Exception{
        List<List<String>> lines=CsvRecords.readAuto(source);if(!lines.isEmpty()&&!lines.get(0).contains("tapped_at_utc"))return OriginalCsv.parse(lines,dateOrder,zone);if(lines.size()<2)throw new IOException("CSV 没有曝光记录");List<String> header=lines.get(0);Map<String,Integer> columns=new HashMap<>();for(int i=0;i<header.size();i++)if(columns.put(header.get(i),i)!=null)throw new IOException("CSV 列名重复");
        for(String required:new String[]{"id","roll_name","stock","camera","iso","frame_number","tapped_at_utc","captured_at_utc","offset_seconds","zone_offset_minutes","status"})if(!columns.containsKey(required))throw new IOException("缺少列："+required);
        Preview result=new Preview();result.legacy=!columns.containsKey("roll_id");result.data=new FilmBackup.Prepared();JSONArray shots=new JSONArray(),rolls=new JSONArray();LinkedHashMap<String,JSONObject> groups=new LinkedHashMap<>();HashSet<String> ids=new HashSet<>();
        for(int i=1;i<lines.size();i++){List<String> line=lines.get(i);if(line.size()==1&&line.get(0).isEmpty())continue;if(line.size()!=header.size())throw new IOException("第 "+(i+1)+" 行列数不一致");Map<String,String> v=new HashMap<>();for(String key:columns.keySet())v.put(key,line.get(columns.get(key)));
            String id=get(v,"id","");if(id.isEmpty()||!ids.add(id))throw new IOException("曝光 ID 缺失或重复");
            long tapped=Instant.parse(get(v,"tapped_at_utc","")).toEpochMilli(),captured=Instant.parse(get(v,"captured_at_utc","")).toEpochMilli();
            JSONObject r=new JSONObject().put("name",v.get("roll_name")).put("stock",v.get("stock")).put("camera",v.get("camera")).put("iso",Integer.parseInt(v.get("iso"))).put("state",get(v,"roll_state","active")).put("archived",Integer.parseInt(get(v,"roll_archived","0"))).put("lab",get(v,"lab","")).put("lab_number",get(v,"lab_number","")).put("lab_cost",get(v,"lab_cost","")).put("film_cost",get(v,"film_cost","")).put("cost_currency",get(v,"cost_currency","")).put("lab_sent_date",get(v,"lab_sent_date","")).put("lab_received_date",get(v,"lab_received_date","")).put("lab_notes",get(v,"lab_notes",""));
            String key=result.legacy?r.toString():get(v,"roll_id","");if(key.isEmpty())throw new IOException("胶卷 ID 为空");JSONObject previous=groups.get(key);String roll;
            int frame=Integer.parseInt(v.get("frame_number"));int capacity=Integer.parseInt(get(v,"roll_capacity",String.valueOf(Math.max(36,frame))));
            if(previous==null){roll=UUID.randomUUID().toString();r.put("id",roll).put("capacity",capacity).put("created_at",tapped);groups.put(key,r);rolls.put(r);}else{
                for(Iterator<String> keys=r.keys();keys.hasNext();){String k=keys.next();if(!r.get(k).equals(previous.get(k)))throw new IOException("同一胶卷的资料不一致");}
                if(columns.containsKey("roll_capacity")&&previous.getInt("capacity")!=capacity)throw new IOException("同一胶卷容量不一致");
                if(!columns.containsKey("roll_capacity"))previous.put("capacity",Math.max(previous.getInt("capacity"),capacity));previous.put("created_at",Math.min(tapped,previous.getLong("created_at")));roll=previous.getString("id");
            }
            JSONObject s=new JSONObject().put("id",id).put("roll_id",roll).put("frame_number",frame).put("tapped_at",tapped).put("captured_at",captured).put("offset_seconds",Integer.parseInt(v.get("offset_seconds"))).put("zone_offset_minutes",Integer.parseInt(v.get("zone_offset_minutes"))).put("status",get(v,"status","denied"));
            for(String text:new String[]{"aperture","shutter","lens","memo","ev","filters","provider"})s.put(text,get(v,text,""));
            for(String number:new String[]{"latitude","longitude","accuracy_m"}){String text=get(v,number,"");s.put(number,text.isEmpty()?JSONObject.NULL:Double.parseDouble(text));}
            s.put("time_edited",Integer.parseInt(get(v,"time_edited","0")));
            String fix=get(v,"location_time_utc","");s.put("location_time",fix.isEmpty()?JSONObject.NULL:Instant.parse(fix).toEpochMilli());shots.put(s);
        }
        if(shots.length()==0)throw new IOException("CSV 没有曝光记录");result.rows=shots.length();result.data.manifest=new JSONObject().put("format",1).put("schema",7).put("rolls",rolls).put("shots",shots).put("scans",new JSONArray());FilmBackup.normalizeCatalog(result.data.manifest);FilmBackup.validate(result.data);return result;
    }
    static Preview combine(List<Preview> previews)throws Exception{
        if(previews.isEmpty()||previews.size()>32)throw new IOException("请选择1至32个文件");
        Preview result=new Preview();result.data=new FilmBackup.Prepared();result.data.manifest=new JSONObject(previews.get(0).data.manifest.toString());
        for(int i=0;i<previews.size();i++){Preview part=previews.get(i);result.legacy|=part.legacy;result.rows+=part.rows;
            if(result.rows>100000)throw new IOException("整批曝光超过100000条");
            if(i>0)for(String table:FilmBackup.TABLES){JSONArray target=result.data.manifest.getJSONArray(table),rows=part.data.manifest.getJSONArray(table);for(int j=0;j<rows.length();j++)target.put(new JSONObject(rows.getJSONObject(j).toString()));}
        }
        // Separate files keep separate rolls; duplicate exposure IDs reject the whole batch.
        java.util.LinkedHashSet<String> notices=new java.util.LinkedHashSet<>();for(Preview part:previews){String notice=part.data.manifest.optString("import_notice","");if(!notice.isEmpty())notices.add(notice);}if(!notices.isEmpty())result.data.manifest.put("import_notice",String.join("\n",notices));
        FilmBackup.validate(result.data);return result;
    }
    static void review(Preview preview,int index,String film,String isoText,String camera,String format)throws Exception{
        if(film.trim().isEmpty())throw new IllegalArgumentException("请填写胶卷");
        if(!isoText.trim().matches("[0-9]+"))throw new IllegalArgumentException("ISO 必须为1至25600的整数");
        int iso;try{iso=Integer.parseInt(isoText.trim());}catch(NumberFormatException invalid){throw new IllegalArgumentException("ISO 必须为1至25600的整数");}
        if(iso<1||iso>25600)throw new IllegalArgumentException("ISO 必须为1至25600的整数");
        if(!Arrays.asList("","35 mm","120","110","70 mm","65 mm","4x5","5x7","8x10","Instant").contains(format))throw new IllegalArgumentException("画幅无效");
        FilmBackup.Prepared candidate=new FilmBackup.Prepared();candidate.manifest=new JSONObject(preview.data.manifest.toString());
        JSONObject roll=candidate.manifest.getJSONArray("rolls").getJSONObject(index);roll.put("stock",film.trim()).put("iso",iso).put("camera",camera.trim());
        JSONArray details=candidate.manifest.getJSONArray("roll_details");JSONObject detail=null;
        for(int i=0;i<details.length();i++)if(details.getJSONObject(i).getString("roll_id").equals(roll.getString("id")))detail=details.getJSONObject(i);
        if(detail!=null){detail.put("format",format);if(!CatalogStore.supportsHalfFrame(format))detail.put("half_frame",0);}
        else if(!format.isEmpty())details.put(new JSONObject().put("id",UUID.randomUUID().toString()).put("roll_id",roll.getString("id")).put("format",format).put("camera_id",JSONObject.NULL).put("project_id",JSONObject.NULL).put("batch_id",JSONObject.NULL).put("returned",0).put("push_pull_stops",0).put("half_frame",0));
        FilmBackup.validate(candidate);preview.data.manifest=candidate.manifest;
    }
    static boolean ready(Preview preview)throws JSONException{
        JSONArray rolls=preview.data.manifest.getJSONArray("rolls");if(rolls.length()==0)return false;
        for(int i=0;i<rolls.length();i++){JSONObject roll=rolls.getJSONObject(i);if(roll.getString("stock").trim().isEmpty()||roll.getInt("iso")<=0)return false;}return true;
    }
    private static String get(Map<String,String> row,String key,String fallback){String value=row.get(key);return value==null||value.isEmpty()?fallback:value;}
}
