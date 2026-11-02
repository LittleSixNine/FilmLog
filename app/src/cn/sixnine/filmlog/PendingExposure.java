package cn.sixnine.filmlog;

import android.content.Context;
import android.location.Location;
import android.os.*;
import android.provider.Settings;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** One private, workspace-scoped attempt. Only a located database transaction counts as an exposure. */
final class PendingExposure {
    private static final String PROCESS=UUID.randomUUID().toString();
    private static final String[] TEXT={"request","recordRoll","referenceToken","returnScreen","recordProcess","locationAction","aperture","shutter","lens","memo","ev","filters","offset"};
    private static final String[] FLAGS={"newRecord","quick","returnDashboard","advanced"};
    static final class Saved{Bundle draft;String screen;}
    private static AtomicFile file(Context c){return new AtomicFile(new File(c.getFilesDir(),"pending-exposure.json"));}
    static String processId(){return PROCESS;}
    static int boot(Context c){try{return Settings.Global.getInt(c.getContentResolver(),Settings.Global.BOOT_COUNT,-1);}catch(SecurityException unavailable){return -1;}}
    static void markTime(Context c,Bundle draft,long elapsed){draft.putLong("recordElapsed",elapsed);draft.putInt("recordBoot",boot(c));draft.putString("recordProcess",PROCESS);}
    static void inferLegacyTime(Context c,Bundle draft){if(draft.containsKey("recordElapsed"))return;long age=System.currentTimeMillis()-draft.getLong("recordTap",0);markTime(c,draft,age>=0&&age<=RecordRules.WAIT_MS?SystemClock.elapsedRealtime()-age:-1);}
    static void confirmHere(Context c,Bundle draft){draft.putBoolean("locationConfirmed",true);draft.putLong("locationAnchorElapsed",SystemClock.elapsedRealtime());draft.putInt("locationAnchorBoot",boot(c));draft.putString("locationAnchorProcess",PROCESS);}
    static boolean matches(Context c,Bundle draft,Location fix){
        if(!LogStore.usableFix(fix))return false;boolean confirmed=draft.getBoolean("locationConfirmed",false);
        long elapsed=draft.getLong(confirmed?"locationAnchorElapsed":"recordElapsed",-1);int savedBoot=draft.getInt(confirmed?"locationAnchorBoot":"recordBoot",-1),currentBoot=boot(c);
        boolean sameBoot=savedBoot>=0&&currentBoot>=0?savedBoot==currentBoot:PROCESS.equals(draft.getString(confirmed?"locationAnchorProcess":"recordProcess",""));
        return sameBoot&&elapsed>=0&&elapsed<=SystemClock.elapsedRealtime()
            &&RecordRules.matchesRecordFix(elapsed,fix.getElapsedRealtimeNanos()/1000000,fix.getLatitude(),fix.getLongitude(),fix.getAccuracy(),LogStore.fixSpeed(fix));
    }
    static boolean needsConfirmation(Context c,Bundle draft){
        int old=draft.getInt("recordBoot",-1),now=boot(c);boolean same=old>=0&&now>=0?old==now:PROCESS.equals(draft.getString("recordProcess",""));long event=draft.getLong("recordElapsed",-1),elapsed=SystemClock.elapsedRealtime();return !same||event<0||event>elapsed||elapsed-event>RecordRules.WAIT_MS;
    }
    private static byte[] read(Context c)throws IOException{
        try(InputStream in=file(c).openRead();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(out.size()+n>1024*1024)throw new IOException("待定位草稿过大");out.write(b,0,n);}return out.toByteArray();}catch(FileNotFoundException absent){return null;}
    }
    static void save(Context c,Bundle draft,String screen)throws Exception{
        validate(draft);JSONObject value=new JSONObject();for(String key:TEXT)value.put(key,draft.getString(key,""));for(String key:FLAGS)value.put(key,draft.getBoolean(key,false));
        for(String key:new String[]{"recordTap","recordElapsed"})value.put(key,draft.getLong(key,-1));for(String key:new String[]{"recordZone","recordBoot"})value.put(key,draft.getInt(key,0));if(draft.containsKey("meterEV"))value.put("meterEV",draft.getDouble("meterEV"));
        byte[] bytes=new JSONObject().put("version",1).put("screen",screen).put("draft",value).toString().getBytes(StandardCharsets.UTF_8);if(bytes.length>1024*1024)throw new IOException("待定位草稿过大");
        String token=draft.getString("referenceToken","");if(!token.isEmpty())ReferencePhotos.retainDraft(c,token);
        if(Arrays.equals(read(c),bytes))return;
        AtomicFile target=file(c);FileOutputStream out=null;try{out=target.startWrite();out.write(bytes);target.finishWrite(out);}catch(Exception error){if(out!=null)target.failWrite(out);throw error;}
    }
    static Saved load(Context c)throws Exception{
        byte[] bytes=read(c);if(bytes==null)return null;
        JSONObject json=new JSONObject(new String(bytes,StandardCharsets.UTF_8));if(json.getInt("version")!=1)throw new IOException("待定位草稿版本不支持");Saved saved=new Saved();saved.screen=json.getString("screen");if(!Arrays.asList("dashboard","roll","editor").contains(saved.screen))throw new IOException("待定位草稿页面无效");JSONObject value=json.getJSONObject("draft");Bundle b=new Bundle();
        for(String key:TEXT){Object v=value.get(key);if(!(v instanceof String))throw new IOException("草稿文字无效");b.putString(key,(String)v);}for(String key:FLAGS){Object v=value.get(key);if(!(v instanceof Boolean))throw new IOException("草稿状态无效");b.putBoolean(key,(Boolean)v);}
        for(String key:new String[]{"recordTap","recordElapsed","recordZone","recordBoot"}){Object v=value.get(key);if(!(v instanceof Number)||((Number)v).doubleValue()!=value.getLong(key))throw new IOException("草稿时间无效");if(key.equals("recordTap")||key.equals("recordElapsed"))b.putLong(key,value.getLong(key));else{long n=value.getLong(key);if(n<Integer.MIN_VALUE||n>Integer.MAX_VALUE)throw new IOException("草稿数值超界");b.putInt(key,(int)n);}}
        if(value.has("meterEV")){double ev=value.getDouble("meterEV");if(!Double.isFinite(ev))throw new IOException("草稿测光值无效");b.putDouble("meterEV",ev);}validate(b);b.putBoolean("pendingPersisted",true);saved.draft=b;return saved;
    }
    private static void validate(Bundle b)throws Exception{
        for(String key:new String[]{"recordRoll","request"}){String id=b.getString(key,"");if(!UUID.fromString(id).toString().equals(id))throw new IOException("待定位草稿标识无效");}
        String token=b.getString("referenceToken","");if(!token.isEmpty()&&!UUID.fromString(token).toString().equals(token))throw new IOException("参考照片标识无效");
        if(b.getLong("recordTap",0)<=0||b.getLong("recordElapsed",-1)<-1||Math.abs((long)b.getInt("recordZone",0))>1080)throw new IOException("待定位草稿时间无效");RecordRules.parseOffset(b.getString("offset",""));
        if(!b.getBoolean("newRecord",false)&&!b.getString("locationAction","").equals("editor"))throw new IOException("不是待定位曝光草稿");
    }
    static void clear(Context c)throws IOException{AtomicFile target=file(c);target.delete();if(target.getBaseFile().exists()||new File(target.getBaseFile()+".bak").exists())throw new IOException("待定位草稿尚未清理，请重开核对");}
    static void cleanup(Context c)throws Exception{Saved saved=load(c);String keep=saved==null?"":saved.draft.getString("referenceToken","");File[] files=c.getFilesDir().listFiles();if(files!=null)for(File f:files)if(f.getName().matches("pending-reference-[0-9a-f-]{36}\\.jpg(?:\\.part)?")&&!f.getName().equals("pending-reference-"+keep+".jpg"))f.delete();}
    static boolean alreadySaved(Context c,LogStore logs,Bundle draft)throws Exception{
        String token=draft.getString("referenceToken",""),id=token.isEmpty()?draft.getString("request",""):token;
        try(android.database.Cursor rows=logs.getReadableDatabase().query("shots",new String[]{"id"},"id=?",new String[]{id},null,null,null)){if(!rows.moveToFirst())return false;}
        LogStore.Shot shot=logs.shot(id);if(!shot.rollId.equals(draft.getString("recordRoll"))||shot.tapped!=draft.getLong("recordTap")||shot.offset!=RecordRules.parseOffset(draft.getString("offset"))||shot.zone!=draft.getInt("recordZone")||shot.lat==null||shot.lon==null)throw new IOException("待定位草稿与已有记录不一致，请保留资料核对");
        if(!token.isEmpty()){if(!ReferencePhotos.forShot(logs,id).equals(token))throw new IOException("参考照片关联不一致");ReferencePhotos.file(c,logs,token);}clear(c);ReferencePhotos.discard(c,token);return true;
    }
}
