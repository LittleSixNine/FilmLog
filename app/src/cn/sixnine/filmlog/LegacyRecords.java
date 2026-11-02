package cn.sixnine.filmlog;
import android.content.*;
import org.json.*;
import java.util.*;
/** Offline access to retained pre-removal namespaces; no account or network operations. */
final class LegacyRecords {
 static boolean available(Context c){return !Workspace.current(c).equals("guest")||!Workspace.registry(c).getString("profiles","[]").trim().equals("[]")||Workspace.registry(c).contains("invalidActive");}
 private static void add(LinkedHashMap<String,String> result,String id,String label){id=Workspace.valid(id);if(!result.containsKey(id))result.put(id,label);}
 static LinkedHashMap<String,String> spaces(Context c)throws Exception{LinkedHashMap<String,String> result=new LinkedHashMap<>();add(result,Workspace.current(c),"当前记录");add(result,"guest","本机记录");JSONArray profiles=new JSONArray(Workspace.registry(c).getString("profiles","[]"));if(profiles.length()>50)throw new IllegalArgumentException("旧资料索引超出范围，原文件保留");for(int i=0;i<profiles.length();i++){JSONObject profile=profiles.getJSONObject(i);add(result,profile.getString("workspace"),"旧版资料 "+(i+1));JSONArray old=profile.optJSONArray("previous");if(old==null)continue;if(old.length()>100)throw new IllegalArgumentException("历史副本索引超出范围，原文件保留");for(int j=0;j<old.length();j++)add(result,old.getJSONObject(j).getString("workspace"),"旧版资料 "+(i+1)+" · 历史副本 "+(j+1));}return result;}
 static void activate(Context c,String id)throws Exception{if(!spaces(c).containsKey(id))throw new IllegalArgumentException("资料不在已保存索引内");TimerState timer=ExposureClock.read(Workspace.context(Workspace.root(c)));if(timer.status.equals("running")||timer.status.equals("paused"))throw new IllegalStateException("请先完成或取消长曝光计时，再切换资料");Workspace.activate(c,id);}
}
