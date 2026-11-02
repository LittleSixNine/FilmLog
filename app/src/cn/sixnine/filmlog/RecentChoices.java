package cn.sixnine.filmlog;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.*;
import java.util.*;

/** Shortcuts only; existing roll snapshots and catalog identities remain the source of truth. */
final class RecentChoices {
    static final class Camera {String id,name,format;Camera(String id,String name,String format){this.id=id;this.name=name;this.format=format;}}
    private static SharedPreferences prefs(Context context){return context.getSharedPreferences("recent-choices",0);}
    private static JSONArray read(Context context,String key){try{return new JSONArray(prefs(context).getString(key,"[]"));}catch(JSONException invalid){return new JSONArray();}}
    static boolean sameFormat(String a,String b){return a.isEmpty()||b.isEmpty()||a.replace(" ","").equalsIgnoreCase(b.replace(" ",""));}
    static void stock(Context context,String id){if(id==null||id.isEmpty())return;JSONArray next=new JSONArray().put(id),old=read(context,"stocks");for(int i=0;i<old.length()&&next.length()<16;i++){String value=old.optString(i);if(!value.isEmpty()&&!value.equals(id))next.put(value);}prefs(context).edit().putString("stocks",next.toString()).apply();}
    static ArrayList<String> stocks(Context context,LogStore logs,String format){LinkedHashSet<String> ids=new LinkedHashSet<>();JSONArray chosen=read(context,"stocks");for(int i=0;i<chosen.length();i++)if(!chosen.optString(i).isEmpty())ids.add(chosen.optString(i));CatalogStore catalog=new CatalogStore(logs);for(LogStore.Roll roll:logs.rolls()){CatalogStore.Detail detail=catalog.detail(roll.id);if(detail!=null&&!sameFormat(detail.format,format))continue;String id=ArtworkLinks.get(logs,"roll",roll.id);if(!id.isEmpty())ids.add(id);if(ids.size()>=24)break;}return new ArrayList<>(ids);}
    private static String cameraKey(Camera camera){return camera.id.isEmpty()?camera.format.replace(" ","").toLowerCase(Locale.ROOT)+":"+camera.name.toLowerCase(Locale.ROOT):"id:"+camera.id;}
    private static boolean named(String name){return !name.trim().isEmpty()&&name.length()<=500&&!Arrays.asList("未设置相机","未设置").contains(name.trim());}
    static void camera(Context context,String id,String name,String format){if(!named(name))return;try{Camera current=new Camera(id==null?"":id,name.trim(),format);JSONArray next=new JSONArray().put(new JSONObject().put("id",current.id).put("name",current.name).put("format",format)),old=read(context,"cameras");Set<String> keys=new HashSet<>();keys.add(cameraKey(current));for(int i=0;i<old.length()&&next.length()<16;i++){JSONObject row=old.optJSONObject(i);if(row==null)continue;Camera item=new Camera(row.optString("id"),row.optString("name"),row.optString("format"));if(named(item.name)&&keys.add(cameraKey(item)))next.put(row);}prefs(context).edit().putString("cameras",next.toString()).apply();}catch(JSONException invalid){android.util.Log.w("FilmLog","Could not save camera shortcut",invalid);}}
    private static void addCamera(LinkedHashMap<String,Camera> result,CatalogStore catalog,Camera value,String format){if(!named(value.name))return;if(!value.id.isEmpty()){try{CatalogStore.Entry entry=catalog.entry(value.id,"camera");if(entry.archived||!sameFormat(entry.format,format))return;value=new Camera(entry.id,entry.name,entry.format);}catch(IllegalArgumentException missing){return;}}else if(!sameFormat(value.format,format))return;result.putIfAbsent(cameraKey(value),value);}
    static ArrayList<Camera> cameras(Context context,LogStore logs,String format){CatalogStore catalog=new CatalogStore(logs);LinkedHashMap<String,Camera> result=new LinkedHashMap<>();JSONArray recent=read(context,"cameras");for(int i=0;i<recent.length();i++){JSONObject item=recent.optJSONObject(i);if(item!=null)addCamera(result,catalog,new Camera(item.optString("id"),item.optString("name"),item.optString("format")),format);}
        for(LogStore.Roll roll:logs.rolls()){CatalogStore.Detail detail=catalog.detail(roll.id);addCamera(result,catalog,new Camera(detail==null?"":detail.camera,roll.camera,detail==null?"":detail.format),format);if(result.size()>=8)break;}ArrayList<Camera> values=new ArrayList<>(result.values());return new ArrayList<>(values.subList(0,Math.min(6,values.size())));
    }
}
