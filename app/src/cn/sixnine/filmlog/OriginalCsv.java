package cn.sixnine.filmlog;

import java.io.IOException;
import java.text.Normalizer;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.regex.*;
import org.json.*;

/** Original exported column names; converts into the existing validated, append-only preview. */
final class OriginalCsv {
    private static final List<Map<String,String>> LANGUAGES=new ArrayList<>();
    private static final Map<String,Set<String>> HEADERS=new HashMap<>();
    private static void language(String... pairs){Map<String,String> names=new HashMap<>();for(int i=0;i<pairs.length;i+=2){String normalized=key(pairs[i+1]);names.put(normalized,pairs[i]);HEADERS.computeIfAbsent(normalized,k->new HashSet<>()).add(pairs[i]);}LANGUAGES.add(names);}
    static String key(String value){return Normalizer.normalize(value.toLowerCase(Locale.ROOT).replace('ё','е'),Normalizer.Form.NFD).replaceAll("\\p{M}+","").replaceAll("\\([^)]*\\)|\\[[^\\]]*\\]","").replaceAll("[º°]","o").replaceAll("[^\\p{L}\\p{N}#№]+","");}

    static {
        language("roll","Roll","film","Film","format","Format","iso","ISO","camera","Camera","frame","#","date","Date","aperture","Aperture","shutter","Shutter","compensation","Compensation (EV)","lens","Lens","note","Note","latitude","Latitude","longitude","Longitude","place","Place","weather","Weather","distance","Distance (m)","status","Status","pushPull","Push/Pull (stops)","expired","Expired","rollNumber","Roll Number","lab","Lab","sentToLab","Sent to Lab","received","Received","project","Project","filmCost","Film Cost","labCost","Lab Cost","currency","Currency","pellicaId","Pellica ID");
        language("roll","Pellicule","film","Film","format","Format","iso","ISO","camera","Appareil","frame","N°","date","Date","aperture","Ouverture","shutter","Vitesse","compensation","Correction (EV)","lens","Objectif","note","Note","latitude","Latitude","longitude","Longitude","place","Lieu","weather","Météo","distance","Distance (m)","status","Statut","pushPull","Pousser/tirer (IL)","expired","Périmée","rollNumber","N° de pellicule","lab","Labo","sentToLab","Envoyée au labo","received","Reçue","project","Projet","filmCost","Prix de la pellicule","labCost","Prix du labo","currency","Devise","pellicaId","ID Pellica");
        language("roll","Film","film","Filmsorte","format","Format","iso","ISO","camera","Kamera","frame","Nr.","date","Datum","aperture","Blende","shutter","Verschlusszeit","compensation","Belichtungskorrektur (EV)","lens","Objektiv","note","Notiz","latitude","Breitengrad","longitude","Längengrad","place","Ort","weather","Wetter","distance","Entfernung (m)","status","Status","pushPull","Push/Pull (Blenden)","expired","Abgelaufen","rollNumber","Filmnummer","lab","Labor","sentToLab","Ans Labor geschickt","received","Zurückerhalten","project","Projekt","filmCost","Filmkosten","labCost","Laborkosten","currency","Währung","pellicaId","Pellica-ID");
        language("roll","Rollo","film","Película","format","Formato","iso","ISO","camera","Cámara","frame","N.º","date","Fecha","aperture","Apertura","shutter","Velocidad","compensation","Compensación (EV)","lens","Objetivo","note","Nota","latitude","Latitud","longitude","Longitud","place","Lugar","weather","Clima","distance","Distancia (m)","status","Estado","pushPull","Forzado/subrevelado (pasos)","expired","Caducada","rollNumber","N.º de rollo","lab","Laboratorio","sentToLab","Enviado al laboratorio","received","Recibido","project","Proyecto","filmCost","Costo del rollo","labCost","Costo del laboratorio","currency","Moneda","pellicaId","ID de Pellica");
        language("roll","Rullino","film","Pellicola","format","Formato","iso","ISO","camera","Fotocamera","frame","N.","date","Data","aperture","Apertura","shutter","Tempo","compensation","Compensazione (EV)","lens","Obiettivo","note","Nota","latitude","Latitudine","longitude","Longitudine","place","Luogo","weather","Meteo","distance","Distanza (m)","status","Stato","pushPull","Push/pull (stop)","expired","Scaduta","rollNumber","N. rullino","lab","Laboratorio","sentToLab","Inviato al laboratorio","received","Ricevuto","project","Progetto","filmCost","Costo della pellicola","labCost","Costo del laboratorio","currency","Valuta","pellicaId","ID Pellica");
        language("roll","Rolo","film","Filme","format","Formato","iso","ISO","camera","Câmera","frame","Nº","date","Data","aperture","Abertura","shutter","Velocidade","compensation","Compensação (EV)","lens","Lente","note","Anotação","latitude","Latitude","longitude","Longitude","place","Local","weather","Tempo","distance","Distância (m)","status","Status","pushPull","Push/pull (pontos)","expired","Vencido","rollNumber","Nº do rolo","lab","Laboratório","sentToLab","Enviado ao laboratório","received","Recebido","project","Projeto","filmCost","Custo do filme","labCost","Custo do laboratório","currency","Moeda","pellicaId","ID Pellica");
        language("roll","Плёнка","film","Тип плёнки","format","Формат","iso","ISO","camera","Камера","frame","№","date","Дата","aperture","Диафрагма","shutter","Выдержка","compensation","Экспокоррекция (EV)","lens","Объектив","note","Заметка","latitude","Широта","longitude","Долгота","place","Место","weather","Погода","distance","Расстояние (м)","status","Статус","pushPull","Пуш/пулл (ступени)","expired","Просрочена","rollNumber","Номер плёнки","lab","Лаборатория","sentToLab","Отправлена в лабораторию","received","Получена","project","Проект","filmCost","Стоимость плёнки","labCost","Стоимость проявки","currency","Валюта","pellicaId","ID Pellica");
        language("roll","Rulo","film","Film","format","Format","iso","ISO","camera","Kamera","frame","No.","date","Tarih","aperture","Diyafram","shutter","Enstantane","compensation","Pozlama telafisi (EV)","lens","Objektif","note","Not","latitude","Enlem","longitude","Boylam","place","Yer","weather","Hava durumu","distance","Mesafe (m)","status","Durum","pushPull","Push/pull (stop)","expired","Süresi geçmiş","rollNumber","Rulo numarası","lab","Laboratuvar","sentToLab","Laboratuvara gönderildi","received","Teslim alındı","project","Proje","filmCost","Film ücreti","labCost","Laboratuvar ücreti","currency","Para birimi","pellicaId","Pellica kimliği");
        language("roll","ロール","film","フィルム","format","フォーマット","iso","ISO","camera","カメラ","frame","コマ","date","日付","aperture","絞り","shutter","シャッター速度","compensation","露出補正 (EV)","lens","レンズ","note","メモ","latitude","緯度","longitude","経度","place","場所","weather","天気","distance","距離 (m)","status","状態","pushPull","増感/減感 (段)","expired","期限切れ","rollNumber","ロール番号","lab","ラボ","sentToLab","ラボに出した日","received","受け取った日","project","プロジェクト","filmCost","フィルム代","labCost","ラボ代","currency","通貨","pellicaId","Pellica ID");
        language("roll","롤","film","필름","format","포맷","iso","ISO","camera","카메라","frame","컷","date","날짜","aperture","조리개","shutter","셔터 속도","compensation","노출 보정 (EV)","lens","렌즈","note","메모","latitude","위도","longitude","경도","place","장소","weather","날씨","distance","거리 (m)","status","상태","pushPull","푸시/풀 (스톱)","expired","유통기한 지남","rollNumber","롤 번호","lab","현상소","sentToLab","현상소에 보낸 날","received","받은 날","project","프로젝트","filmCost","필름 비용","labCost","현상 비용","currency","통화","pellicaId","Pellica ID");
        language("roll","胶卷","film","胶片型号","format","画幅","iso","ISO","camera","相机","frame","序号","date","日期","aperture","光圈","shutter","快门速度","compensation","曝光补偿 (EV)","lens","镜头","note","备注","latitude","纬度","longitude","经度","place","地点","weather","天气","distance","距离 (米)","status","状态","pushPull","增感/减感 (档)","expired","已过期","rollNumber","胶卷编号","lab","冲洗店","sentToLab","送洗日期","received","取回日期","project","项目","filmCost","胶卷费用","labCost","冲洗费用","currency","货币","pellicaId","Pellica ID");
        language("roll","roll","film","film","format","format","iso","iso","camera","camera","frame","frame","date","date","aperture","aperture","shutter","shutter","compensation","compensation","lens","lens","note","note","latitude","latitude","longitude","longitude","place","place","weather","weather","distance","distance","status","status","pushPull","pushPull","expired","expired","rollNumber","rollNumber","lab","lab","sentToLab","sentToLab","received","received","project","project","filmCost","filmCost","labCost","labCost","currency","currency","pellicaId","pellicaId");
    }
    static final class DateValue {final long millis;final int zone;DateValue(ZonedDateTime value)throws IOException{if(value.getOffset().getTotalSeconds()%60!=0)throw new IOException("此历史时区包含秒偏移，请在源文件明确转换为UTC");millis=value.toInstant().toEpochMilli();zone=value.getOffset().getTotalSeconds()/60;}}
    static DateValue date(String raw,int order,ZoneId zone)throws IOException {
        String value=raw.trim();if(value.isEmpty())throw new IOException("曝光缺少日期，请在源文件补全后导入");
        try {
            if(value.matches("\\d{10}|\\d{13}"))return new DateValue(Instant.ofEpochMilli(Math.multiplyExact(Long.parseLong(value),value.length()==10?1000:1)).atZone(zone));
            try{return new DateValue(OffsetDateTime.parse(value.replaceFirst("^(\\d{4}-\\d{2}-\\d{2}) ","$1T")).toZonedDateTime());}catch(DateTimeParseException ignored){}
            value=value.toLowerCase(Locale.ROOT).replace("上午","am ").replace("下午","pm ").replace("午前","am ").replace("午後","pm ").replace("오전","am ").replace("오후","pm ").replaceAll("[年月]","-").replaceAll("[日号]"," ").replaceAll("\\s+(at|à|um|a las|alle|às|в)\\s+"," ").trim();
            if(value.matches("\\d{5}(?:[.,]\\d+)?")){
                long seconds=new java.math.BigDecimal(value.replace(',','.')).multiply(java.math.BigDecimal.valueOf(86400)).setScale(0,java.math.RoundingMode.HALF_UP).longValueExact();
                return local(LocalDateTime.of(1899,12,30,0,0).plusSeconds(seconds),zone);
            }
            Matcher m=Pattern.compile("^(\\d{4})\\s*[-/:.]\\s*(\\d{1,2})\\s*[-/:.]\\s*(\\d{1,2})(.*)$").matcher(value);
            int year,month,day;String time;
            if(m.matches()){year=Integer.parseInt(m.group(1));month=Integer.parseInt(m.group(2));day=Integer.parseInt(m.group(3));time=m.group(4);}
            else {
                m=Pattern.compile("^(\\d{1,2})\\s*[/.-]\\s*(\\d{1,2})\\s*[/.-]\\s*(\\d{4}|\\d{2})(.*)$").matcher(value);
                if(m.matches()){
                    int a=Integer.parseInt(m.group(1)),b=Integer.parseInt(m.group(2));year=Integer.parseInt(m.group(3));if(m.group(3).length()==2){year+=2000;if(year>LocalDate.now(zone).getYear()+1)year-=100;}
                    if(a<=12&&b<=12&&a!=b&&order==0)throw new IOException("日期 "+raw+" 的日/月顺序不明确，请重新选择文件并指定日/月顺序");
                    boolean dayFirst=a>12||(b<=12&&order!=2);day=dayFirst?a:b;month=dayFirst?b:a;time=m.group(4);
                }else{
                    String[] patterns={"d MMM uuuu","d MMMM uuuu","MMM d, uuuu","MMMM d, uuuu","MMM d uuuu","MMMM d uuuu"};
                    for(Locale locale:new Locale[]{Locale.ENGLISH,Locale.FRENCH,Locale.GERMAN,new Locale("es"),Locale.ITALIAN,new Locale("pt"),new Locale("ru"),new Locale("tr")})for(String pattern:patterns){
                        Matcher named=Pattern.compile("^(.+?\\d{4})(.*)$").matcher(value);if(!named.matches())continue;
                        try{LocalDate date=LocalDate.parse(named.group(1),new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(pattern).toFormatter(locale).withResolverStyle(ResolverStyle.STRICT));return local(LocalDateTime.of(date,time(named.group(2))),zone);}catch(DateTimeException ignored){}
                    }
                    throw new IOException("无法识别完整日期："+raw+"，请使用 YYYY-MM-DD HH:mm:ss 或 ISO 8601");
                }
            }
            return local(LocalDateTime.of(LocalDate.of(year,month,day),time(time)),zone);
        }catch(DateTimeException|ArithmeticException|NumberFormatException invalid){throw new IOException("无效日期："+raw,invalid);}
    }
    private static LocalTime time(String text)throws IOException {
        String value=text.trim().replaceFirst("^[tT, ]+","");if(value.isEmpty())return LocalTime.MIDNIGHT;
        boolean am=value.contains("am"),pm=value.contains("pm");value=value.replace("am","").replace("pm","").trim();
        Matcher m=Pattern.compile("^(\\d{1,2}):(\\d{2})(?::(\\d{2})(?:[.,](\\d{1,9}))?)?$").matcher(value);if(!m.matches())throw new IOException("无法识别时间："+text);
        int h=Integer.parseInt(m.group(1));if(am||pm){if(h<1||h>12)throw new IOException("12小时制时间无效");h=h%12+(pm?12:0);}
        String fraction=m.group(4);int nano=fraction==null?0:Integer.parseInt((fraction+"000000000").substring(0,9));return LocalTime.of(h,Integer.parseInt(m.group(2)),m.group(3)==null?0:Integer.parseInt(m.group(3)),nano);
    }
    private static DateValue local(LocalDateTime value,ZoneId zone)throws IOException {
        List<ZoneOffset> valid=zone.getRules().getValidOffsets(value);if(valid.size()!=1)throw new IOException("日期处于夏令时重复或不存在的时段，请在源文件使用带 UTC 偏移的 ISO 时间："+value);return new DateValue(value.atZone(zone));
    }
    static CsvImport.Preview parse(List<List<String>> lines,int order,ZoneId zone)throws Exception {
        if(lines.size()<2)throw new IOException("文件没有曝光记录");if(order<0||order>2)throw new IOException("日期顺序无效");
        List<String> header=lines.get(0);Set<String> normalized=new HashSet<>();for(String label:header){String k=key(label);if(k.isEmpty()||!normalized.add(k))throw new IOException("列名为空或重复："+label);}
        Map<String,Integer> columns=null;int best=-1;boolean ambiguous=false;
        // "Film" is a roll in German and a stock in English. Resolve a whole header language before mapping cells.
        for(Map<String,String> language:LANGUAGES){Map<String,Integer> candidate=new LinkedHashMap<>();boolean duplicate=false;for(int i=0;i<header.size();i++){String k=key(header.get(i)),field=language.get(k);Set<String> choices=HEADERS.get(k);if(field==null&&choices!=null&&choices.size()==1)field=choices.iterator().next();if(field!=null&&candidate.put(field,i)!=null){duplicate=true;break;}}if(duplicate)continue;if(candidate.size()>best){best=candidate.size();columns=candidate;ambiguous=false;}else if(candidate.size()==best&&!candidate.equals(columns))ambiguous=true;}
        if(columns==null||ambiguous)throw new IOException("列名语言存在歧义或同一字段重复，请保留完整的胶卷、胶片型号、序号和日期表头");
        Map<Integer,String> fields=new HashMap<>();for(Map.Entry<String,Integer> column:columns.entrySet())fields.put(column.getValue(),column.getKey());
        if(!columns.containsKey("frame")||!columns.containsKey("date")||(!columns.containsKey("film")&&!columns.containsKey("roll")))throw new IOException("未识别导入字段；支持胶片随记导出文件与 Pellica 的胶卷／胶片型号、序号、日期等列");
        CsvImport.Preview result=new CsvImport.Preview();result.data=new FilmBackup.Prepared();result.legacy=!columns.containsKey("pellicaId");JSONArray rolls=new JSONArray(),shots=new JSONArray(),details=new JSONArray();Map<String,JSONObject> groups=new LinkedHashMap<>();Map<String,JSONObject> groupDetails=new HashMap<>();Set<String> datedGroups=new HashSet<>();long importTime=System.currentTimeMillis();
        for(int row=1;row<lines.size();row++){
            List<String> values=lines.get(row);if(values.size()==1&&values.get(0).isEmpty())continue;if(values.size()!=header.size())throw new IOException("第"+(row+1)+"行列数不一致");
            Map<String,String> v=new HashMap<>();for(String field:columns.keySet())v.put(field,values.get(columns.get(field)).trim());
            String film=v.getOrDefault("film",""),camera=v.getOrDefault("camera",""),name=v.getOrDefault("roll","");if(name.isEmpty())name=film.isEmpty()?"导入胶卷":film;
            String sourceId=v.getOrDefault("pellicaId","");if(sourceId.isEmpty()){result.legacy=true;sourceId=name+"\u0000"+film+"\u0000"+camera;}
            int iso=number(v.getOrDefault("iso",""),0);String frameText=v.getOrDefault("frame","");boolean empty=frameText.isEmpty()&&v.getOrDefault("date","").isEmpty();int frame=number(frameText,0);if(!empty&&(frame<1||frame>999))throw new IOException("曝光序号必须为1至999");
            DateValue captured;try{captured=empty?null:date(v.getOrDefault("date",""),order,zone);}catch(IOException invalid){throw new IOException("第"+(row+1)+"行："+invalid.getMessage(),invalid);}
            String sent=v.getOrDefault("sentToLab",""),received=v.getOrDefault("received","");if(!sent.isEmpty())date(sent,order,zone);if(!received.isEmpty())date(received,order,zone);
            JSONObject candidate=new JSONObject().put("name",name).put("stock",film).put("camera",camera).put("iso",iso).put("state",!received.isEmpty()?"received":!sent.isEmpty()?"developing":"active").put("archived",0).put("lab",v.getOrDefault("lab","")).put("lab_number",v.getOrDefault("rollNumber","")).put("lab_cost",cost(v.getOrDefault("labCost",""))).put("film_cost",cost(v.getOrDefault("filmCost",""))).put("cost_currency",v.getOrDefault("currency","").toUpperCase(Locale.ROOT));
            JSONObject roll=groups.get(sourceId);String format=format(v.getOrDefault("format",""));int push=number(v.getOrDefault("pushPull",""),0);CatalogStore.pushPullLabel(push);
            if(roll==null){roll=candidate;roll.put("id",UUID.randomUUID().toString()).put("capacity",Math.max(RollWizard.defaultCapacity(format),frame)).put("created_at",empty?importTime:captured.millis);groups.put(sourceId,roll);rolls.put(roll);JSONObject detail=new JSONObject().put("id",UUID.randomUUID().toString()).put("roll_id",roll.getString("id")).put("format",format).put("camera_id",JSONObject.NULL).put("project_id",JSONObject.NULL).put("batch_id",JSONObject.NULL).put("returned",0).put("push_pull_stops",push);details.put(detail);groupDetails.put(sourceId,detail);}
            else {for(Iterator<String> keys=candidate.keys();keys.hasNext();){String k=keys.next();if(!candidate.get(k).equals(roll.get(k)))throw new IOException("同一胶卷资料不一致："+name);}JSONObject detail=groupDetails.get(sourceId);if(!detail.getString("format").equals(format)||detail.getInt("push_pull_stops")!=push)throw new IOException("同一胶卷画幅或增减感不一致");roll.put("capacity",Math.max(roll.getInt("capacity"),frame));if(!empty)roll.put("created_at",datedGroups.contains(sourceId)?Math.min(roll.getLong("created_at"),captured.millis):captured.millis);}
            if(empty){Set<String> rollFields=new HashSet<>(Arrays.asList("roll","film","format","iso","camera","frame","date","pushPull","rollNumber","lab","filmCost","labCost","currency","pellicaId"));for(int i=0;i<header.size();i++)if(!values.get(i).trim().isEmpty()&&!rollFields.contains(fields.get(i)))throw new IOException("第"+(row+1)+"行空卷含附加资料，无法保存在曝光备注；请补完整曝光或先保留源文件，不会丢弃该列");continue;}datedGroups.add(sourceId);
            StringBuilder memo=new StringBuilder(v.getOrDefault("note",""));
            Set<String> mapped=new HashSet<>(Arrays.asList("roll","film","format","iso","camera","frame","date","aperture","shutter","compensation","lens","note","latitude","longitude","pushPull","rollNumber","lab","filmCost","labCost","currency"));
            for(int i=0;i<header.size();i++){String field=fields.get(i);if((field==null||!mapped.contains(field))&&!values.get(i).trim().isEmpty()){if(memo.length()>0)memo.append('\n');memo.append(header.get(i)).append(": ").append(values.get(i));}}
            JSONObject shot=new JSONObject().put("id",UUID.randomUUID().toString()).put("roll_id",roll.getString("id")).put("frame_number",frame).put("tapped_at",captured.millis).put("captured_at",captured.millis).put("offset_seconds",0).put("zone_offset_minutes",captured.zone).put("memo",memo.toString());
            for(String[] pair:new String[][]{{"aperture","aperture"},{"shutter","shutter"},{"lens","lens"},{"compensation","ev"}})shot.put(pair[1],parameter(pair[0],v.getOrDefault(pair[0],"")));shot.put("filters","").put("provider","").put("accuracy_m",JSONObject.NULL).put("location_time",JSONObject.NULL);
            for(String coordinate:new String[]{"latitude","longitude"}){String text=v.getOrDefault(coordinate,"");shot.put(coordinate,text.isEmpty()?JSONObject.NULL:Double.parseDouble(text.replace(',','.')));}shot.put("status",shot.isNull("latitude")?"disabled":"acquired");shots.put(shot);
        }
        if(rolls.length()==0)throw new IOException("文件没有胶卷记录");result.rows=shots.length();result.data.manifest=new JSONObject().put("format",1).put("schema",13).put("rolls",rolls).put("shots",shots).put("scans",new JSONArray()).put("catalog_entries",new JSONArray()).put("film_batches",new JSONArray()).put("stock_history",new JSONArray()).put("roll_details",details).put("reference_photos",new JSONArray()).put("film_stocks",new JSONArray());
        // shortcut: Non-model fields stay verbatim in exposure notes; add dedicated columns only with an agreed editing workflow.
        result.data.manifest.put("import_notice","无时区日期按 "+zone.getId()+"，"+(order==1?"日/月/年":order==2?"月/日/年":"日/月有歧义时停止")+"；仅日期记为00:00。两位年份以当前年+1为界归入1900或2000年代，请核对下方时间。保留文件拍摄时间，不应用记录偏移。按送洗/取回日期识别状态，额外字段保留在备注。重复导入会新增重复胶卷。");FilmBackup.normalizeCatalog(result.data.manifest);FilmBackup.validate(result.data);return result;
    }
    private static String parameter(String field,String raw){
        if(!field.equals("aperture")&&!field.equals("shutter"))return raw;if(raw.isEmpty())return "";String value=raw.replace(',','.');if(value.equalsIgnoreCase("auto")||value.equals("自动"))return "Auto";if(field.equals("shutter")&&(value.equalsIgnoreCase("bulb")||value.equalsIgnoreCase("b")))return "B";
        try{return field.equals("aperture")?PhotoMath.apertureValue(value):PhotoMath.shutterChoice(PhotoMath.seconds(value.replaceAll("[\"″”]$","s")));}catch(IllegalArgumentException unknown){return raw;}
    }
    private static int number(String text,int fallback)throws IOException {if(text.isEmpty())return fallback;try{return new java.math.BigDecimal(text.replaceFirst("(?i)^iso\\s*","").replaceFirst("^\\+","")).intValueExact();}catch(Exception invalid){throw new IOException("无效整数："+text,invalid);}}
    private static String cost(String text){return text.replace(',','.');}
    private static String format(String raw)throws IOException {if(raw.isEmpty())return "";String value=raw.replace(" ","").toLowerCase(Locale.ROOT);for(String allowed:new String[]{"35 mm","120","110","70 mm","65 mm","4x5","5x7","8x10","Instant"})if(allowed.replace(" ","").toLowerCase(Locale.ROOT).equals(value))return allowed;if(value.equals("135")||value.equals("35mmfilm"))return "35 mm";throw new IOException("未识别画幅："+raw);}
}
