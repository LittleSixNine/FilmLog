package cn.sixnine.filmlog;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.location.*;
import android.os.*;
import android.util.Log;
import java.util.*;

/** One foreground subscription across activities; no background service or stored coordinates. */
final class ForegroundLocation implements Application.ActivityLifecycleCallbacks, LocationListener {
    enum Status {READY, UPDATING, UNAVAILABLE}
    static final long REFRESH_AGE_MS=8000,REFRESH_INTERVAL_MS=8000,UNAVAILABLE_AFTER_MS=60000;
    interface Observer {void changed();}
    private static ForegroundLocation instance;
    static ForegroundLocation get(Context context){if(instance==null)throw new IllegalStateException("定位尚未初始化");return instance;}
    static void install(Application app){if(instance==null){instance=new ForegroundLocation(app);app.registerActivityLifecycleCallbacks(instance);}}
    private final Context context;
    private final LocationManager manager;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Set<Activity> visible=Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<Observer> observers=new HashSet<>();
    private final Set<String> providers=new HashSet<>();
    private final Map<String,CancellationSignal> requests=new HashMap<>();
    private Location latest;
    private final LinkedHashMap<String,String> observations=new LinkedHashMap<>();
    private String lastDecision="尚未收到位置";
    private long lastRefresh=-REFRESH_INTERVAL_MS,noFixSince=-1;
    private boolean fine;
    private ForegroundLocation(Context c){context=c;manager=c.getSystemService(LocationManager.class);}
    boolean allowed(){return context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED||context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED;}
    static boolean usable(Location l){return LogStore.usableFix(l);}
    Location current(){return allowed()&&watching()&&usable(latest)?new Location(latest):null;}
    boolean watching(){return !providers.isEmpty();}
    Status status(){if(!allowed()||!watching())return Status.UNAVAILABLE;if(usable(latest))return Status.READY;return noFixSince<0||SystemClock.elapsedRealtime()-noFixSince<UNAVAILABLE_AFTER_MS?Status.UPDATING:Status.UNAVAILABLE;}
    void add(Observer o){observers.add(o);refresh(false);}
    void remove(Observer o){observers.remove(o);}
    private void notifyObservers(){for(Observer o:new ArrayList<>(observers))o.changed();}
    private final Runnable pulse=new Runnable(){public void run(){if(visible.isEmpty())return;refresh(false);notifyObservers();handler.postDelayed(this,1000);}};
    void refresh(boolean force){
        if(visible.isEmpty())return;
        boolean precise=context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;
        Set<String> enabled=new HashSet<>();
        if(allowed())for(String p:Arrays.asList("fused",LocationManager.GPS_PROVIDER,LocationManager.NETWORK_PROVIDER))try{
            if((!p.equals(LocationManager.GPS_PROVIDER)||precise)&&manager.getAllProviders().contains(p)&&manager.isProviderEnabled(p))enabled.add(p);
        }catch(SecurityException|IllegalArgumentException error){Log.w("FilmLog","Location provider unavailable: "+p);}
        long now=SystemClock.elapsedRealtime();boolean due=(latest==null||now-latest.getElapsedRealtimeNanos()/1000000>=REFRESH_AGE_MS)&&now-lastRefresh>=REFRESH_INTERVAL_MS;
        boolean changed=!enabled.equals(providers)||precise!=fine;
        if(changed){if(fine&&!precise)latest=null;unsubscribe();}
        if(force||changed||due){
            fine=precise;lastRefresh=now;
            for(String p:enabled)try{
                if(!providers.contains(p)||Build.VERSION.SDK_INT<30){
                    if(Build.VERSION.SDK_INT>=31)manager.requestLocationUpdates(p,new LocationRequest.Builder(1000L).setQuality(LocationRequest.QUALITY_HIGH_ACCURACY).setMinUpdateIntervalMillis(1000L).setMaxUpdateDelayMillis(0L).build(),context.getMainExecutor(),this);
                    else manager.requestLocationUpdates(p,1000L,0f,this,Looper.getMainLooper());
                    providers.add(p);
                }
                Location cached=manager.getLastKnownLocation(p);acceptFix(cached,"系统缓存/"+p);
                // Keep an in-flight request alive through cold starts; repeated taps only share it.
                if(Build.VERSION.SDK_INT>=30&&!requests.containsKey(p)){
                    CancellationSignal token=new CancellationSignal();requests.put(p,token);
                    try{manager.getCurrentLocation(p,token,context.getMainExecutor(),fix->{if(requests.get(p)!=token)return;requests.remove(p);acceptFix(fix,"单次请求/"+p);});}
                    catch(SecurityException|IllegalArgumentException error){requests.remove(p);throw error;}
                }
            }catch(SecurityException|IllegalArgumentException error){Log.w("FilmLog","Location request unavailable: "+p);}
        }
        if(!allowed())latest=null;
        if(usable(latest))noFixSince=-1;else if(noFixSince<0)noFixSince=now;
    }
    private String locationSummary(Location fix){return "点龄="+(SystemClock.elapsedRealtime()-fix.getElapsedRealtimeNanos()/1000000)/1000+"秒，精度="+(fix.hasAccuracy()?fix.getAccuracy()+"米":"无")+"，速度="+(fix.hasSpeed()?fix.getSpeed()+"米/秒":"无");}
    String diagnostics(){StringBuilder out=new StringBuilder("仅含定位状态，不含经纬度或拍摄记录\n");out.append("前台：").append(!visible.isEmpty()).append("\n权限：").append(fine?"精确":allowed()?"粗略":"未授权").append("\n已订阅：").append(providers).append("\n单次请求中：").append(requests.keySet()).append("\n状态：").append(status()).append("\n最近处理：").append(lastDecision).append("\n当前选择：").append(latest==null?"无":locationSummary(latest)).append("\n");for(Map.Entry<String,String> item:observations.entrySet())out.append(item.getKey()).append("：").append(item.getValue()).append("\n");return out.toString();}
    private void unsubscribe(){for(CancellationSignal request:requests.values())request.cancel();requests.clear();try{manager.removeUpdates(this);}catch(SecurityException ignored){}providers.clear();noFixSince=-1;}
    @Override public void onLocationChanged(Location fix){acceptFix(fix,"持续订阅");}
    private void acceptFix(Location fix,String source){
        if(fix!=null){String provider=fix.getProvider()==null?"unknown":fix.getProvider();if(observations.size()>=8&&!observations.containsKey(provider))observations.remove(observations.keySet().iterator().next());observations.put(provider,source+"，收到于启动后"+SystemClock.elapsedRealtime()/1000+"秒，"+locationSummary(fix));}
        if(visible.isEmpty()||!allowed()||!LogStore.recentFix(fix)){lastDecision=source+"：未采用（无结果、权限/前台不可用或时间/数值无效）";return;}
        boolean newer=latest==null||fix.getElapsedRealtimeNanos()>latest.getElapsedRealtimeNanos();
        if(!newer&&!(fix.getElapsedRealtimeNanos()==latest.getElapsedRealtimeNanos()&&fix.getAccuracy()<latest.getAccuracy())){lastDecision=source+"：忽略较早或同龄较差的位置";return;}
        float speed=fix.hasSpeed()?fix.getSpeed():Float.NaN;
        boolean moved=latest!=null&&newer&&(Float.isFinite(speed)&&speed>RecordRules.RECENT_FIX_SPEED_MPS
            ||fix.distanceTo(latest)>Math.max(RecordRules.RECENT_FIX_DISTANCE_M,fix.getAccuracy()+latest.getAccuracy()));
        if(!usable(fix)){if(!moved){lastDecision=source+"：未满足可信条件，保留原选择";return;}latest=new Location(fix);lastDecision=source+"：移动证据使旧位置失效";}
        else if(latest==null||!usable(latest)||moved||fix.getAccuracy()<=Math.max(RecordRules.RECENT_FIX_ACCURACY_M,latest.getAccuracy()*2)){latest=new Location(fix);lastDecision=source+"：采用";}else lastDecision=source+"：保留附近较好的位置";
        // Do not replace a trusted nearby fix with a much coarser network estimate or refresh its timestamp.
        if(usable(latest))noFixSince=-1;else if(noFixSince<0)noFixSince=SystemClock.elapsedRealtime();
        notifyObservers();
    }
    @Override public void onProviderEnabled(String p){refresh(true);notifyObservers();}
    @Override public void onProviderDisabled(String p){refresh(true);notifyObservers();}
    @Override public void onStatusChanged(String p,int status,Bundle extras){}
    @Override public void onActivityStarted(Activity a){visible.add(a);handler.removeCallbacks(pulse);refresh(false);handler.post(pulse);}
    @Override public void onActivityStopped(Activity a){visible.remove(a);if(visible.isEmpty()){handler.removeCallbacks(pulse);unsubscribe();}}
    @Override public void onActivityCreated(Activity a,Bundle b){}
    @Override public void onActivityResumed(Activity a){refresh(false);}
    @Override public void onActivityPaused(Activity a){}
    @Override public void onActivitySaveInstanceState(Activity a,Bundle b){}
    @Override public void onActivityDestroyed(Activity a){visible.remove(a);if(visible.isEmpty()){handler.removeCallbacks(pulse);unsubscribe();}}
}
