package cn.sixnine.filmlog;

import android.content.Context;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.json.*;

/** Original catalog with read-only local progress; original account awards are not inferred. */
final class AchievementProgress {
    static final class Item {
        String id,group,name,condition,description,metric,rarity;int icon,target,progress;
        boolean reached(){return progress>=target;}
        boolean secret(){return rarity.equals("legendary")&&!reached();}
    }
    final ArrayList<Item> items=new ArrayList<>();final LinkedHashMap<String,String> groups=new LinkedHashMap<>();final JSONObject labels;
    AchievementProgress(Context context,LogStore logs){
        try(InputStream in=context.getAssets().open("pellica-achievements.json");ByteArrayOutputStream bytes=new ByteArrayOutputStream()){
            byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1)bytes.write(buffer,0,n);JSONObject data=new JSONObject(new String(bytes.toByteArray(),StandardCharsets.UTF_8));labels=data.getJSONObject("labels");JSONArray sections=data.getJSONArray("groups");for(int i=0;i<sections.length();i++){JSONObject group=sections.getJSONObject(i);groups.put(group.getString("id"),group.getString("title"));}
            StatsStore.Summary stats=StatsStore.read(logs,0,true);CatalogStore catalog=new CatalogStore(logs);int golden=0,bw=0;Set<Integer> seasons=new HashSet<>();Set<String> returned=new HashSet<>();for(LogStore.Roll roll:logs.rolls()){CatalogStore.Detail detail=catalog.detail(roll.id);if(detail!=null&&detail.returned)returned.add(roll.id);}
            for(LogStore.Roll roll:logs.rolls()){if(returned.contains(roll.id))continue;boolean black=OriginalCatalog.blackAndWhite(context,roll.stock);for(LogStore.Shot shot:logs.shots(roll.id)){java.time.OffsetDateTime date=Instant.ofEpochMilli(shot.captured).atOffset(ZoneOffset.ofTotalSeconds(shot.zone*60));if(goldenHour(date.getHour()))golden++;seasons.add(season(date.getMonthValue()));if(black)bw++;}}
            // shortcut: Local historical streak is available; original server award history and account age are not.
            int cameras=catalog.entries("camera",false).size()+catalog.entries("camera",true).size();JSONArray rows=data.getJSONArray("achievements");Set<String> ids=new HashSet<>();
            for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);Item item=new Item();item.id=row.getString("id");item.group=row.getString("group");item.name=row.getString("name");item.condition=row.getString("condition");item.description=row.getString("description");item.metric=row.getString("metric");item.rarity=row.getString("rarity");item.icon=row.getInt("icon");item.target=row.getInt("target");
                switch(item.metric){case "rolls":item.progress=stats.rolls;break;case "shots":item.progress=stats.frames;break;case "stocks":item.progress=stats.stockRolls.size();break;case "nightShots":item.progress=stats.nightFrames;break;case "goldenHourShots":item.progress=golden;break;case "bwShots":item.progress=bw;break;case "seasons":item.progress=seasons.size();break;case "cameras":item.progress=cameras;break;case "streak":item.progress=stats.longestStreak;break;case "accountAge":item.progress=-1;break;default:throw new JSONException("unknown metric");}
                if(!ids.add(item.id)||!groups.containsKey(item.group)||item.target<1)throw new JSONException("invalid achievement");items.add(item);
            }if(items.size()!=19||groups.size()!=5)throw new JSONException("incomplete catalog");
        }catch(Exception error){throw new IllegalStateException("无法读取成就目录",error);}
    }
    static boolean goldenHour(int hour){return hour>=6&&hour<8||hour>=17&&hour<19;}
    static int season(int month){return month==12||month<=2?0:month<=5?1:month<=8?2:3;}
    String label(String key){return labels.optString(key);}
    int reached(){int n=0;for(Item item:items)if(item.reached())n++;return n;}
}
