package cn.sixnine.filmlog;

import android.app.*;
import android.content.*;
import android.os.*;

public final class ExposureTimerService extends WorkspaceService {
    static final String CHANNEL="exposure-timer",COMPLETION_CHANNEL="exposure-timer-complete";private final Handler handler=new Handler(Looper.getMainLooper());private PowerManager.WakeLock wake;private String lastNotice="";
    private Notification notice(String message,boolean finished){Intent open=new Intent(this,ExposureTimerActivity.class);PendingIntent tap=PendingIntent.getActivity(this,71,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);Notification.Builder b=new Notification.Builder(this,finished?COMPLETION_CHANNEL:CHANNEL).setSmallIcon(R.drawable.brand_notification).setContentTitle(finished?"长曝光计时结束":"长曝光计时").setContentText(message).setContentIntent(tap).setOngoing(!finished).setOnlyAlertOnce(!finished).setVisibility(Notification.VISIBILITY_PRIVATE);return b.build();}
    static void channels(Context context){NotificationManager manager=context.getSystemService(NotificationManager.class);manager.createNotificationChannel(new NotificationChannel(CHANNEL,"长曝光计时",NotificationManager.IMPORTANCE_LOW));manager.createNotificationChannel(new NotificationChannel(COMPLETION_CHANNEL,"长曝光计时结束",NotificationManager.IMPORTANCE_HIGH));}
    @Override public void onCreate(){super.onCreate();channels(this);wake=getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,getPackageName()+":exposure-timer");wake.setReferenceCounted(false);}
    private final Runnable tick=new Runnable(){public void run(){try{TimerState s=ExposureClock.read(ExposureTimerService.this);if(!s.status.equals("running")){boolean completed=s.status.equals("finished")&&ExposureClock.claimCompletion(ExposureTimerService.this);if(completed)getSystemService(NotificationManager.class).notify(71,notice("计时已结束，请手动关闭相机快门。",true));stopForeground(completed?STOP_FOREGROUND_DETACH:STOP_FOREGROUND_REMOVE);stopSelf();return;}if(!wake.isHeld())wake.acquire(Math.min(s.remaining+60000,3600000));String message=TimerState.duration(s.remaining);if(!message.equals(lastNotice)){lastNotice=message;getSystemService(NotificationManager.class).notify(71,notice(message,false));}handler.postDelayed(this,250);}catch(Exception error){stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}}};
    @Override public int onStartCommand(Intent intent,int flags,int startId){startForeground(71,notice("恢复计时…",false));handler.removeCallbacks(tick);handler.post(tick);return START_STICKY;}
    @Override public void onDestroy(){handler.removeCallbacksAndMessages(null);if(wake!=null&&wake.isHeld())wake.release();super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}
