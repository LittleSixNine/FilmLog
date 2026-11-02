package cn.sixnine.filmlog;

import android.content.*;
import android.os.SystemClock;
import android.provider.Settings;

final class ExposureClock {
    static int boot(Context c){return Settings.Global.getInt(c.getContentResolver(),Settings.Global.BOOT_COUNT,-1);}
    static synchronized TimerState read(Context c){android.content.SharedPreferences p=c.getSharedPreferences("exposure-clock",0);TimerState s=new TimerState();s.status=p.getString("status","idle");s.remaining=p.getLong("remaining",0);s.total=p.getLong("total",s.remaining);s.due=p.getLong("due",0);s.boot=p.getInt("boot",-2);if(s.update(SystemClock.elapsedRealtime(),boot(c)))save(c,s);return s;}
    static synchronized boolean needsService(Context c){TimerState s=read(c);return s.status.equals("running")||completionPending(c,s);}
    private static boolean completionPending(Context c,TimerState s){return s.status.equals("finished")&&s.total>=1000&&s.due>0&&s.boot==boot(c)&&c.getSharedPreferences("exposure-clock",0).getLong("alertedDue",-1)!=s.due;}
    static synchronized boolean claimCompletion(Context c){TimerState s=read(c);return completionPending(c,s)&&c.getSharedPreferences("exposure-clock",0).edit().putLong("alertedDue",s.due).commit();}
    static synchronized void save(Context c,TimerState s){if(!c.getSharedPreferences("exposure-clock",0).edit().putString("status",s.status).putLong("remaining",s.remaining).putLong("total",s.total).putLong("due",s.due).putInt("boot",s.boot).commit())throw new IllegalStateException("无法保存计时状态");}
    static synchronized void begin(Context c,long duration){TimerState old=read(c);if(old.status.equals("running")||old.status.equals("paused"))throw new IllegalStateException("已有计时，请先取消或继续");TimerState s=new TimerState();int boot=boot(c);if(boot<0)throw new IllegalStateException("设备未提供重启标识，无法安全恢复计时");s.start(duration,SystemClock.elapsedRealtime(),boot);save(c,s);}
    static synchronized void pause(Context c){TimerState s=read(c);s.pause(SystemClock.elapsedRealtime(),boot(c));save(c,s);}
    static synchronized void resume(Context c){TimerState s=read(c);s.resume(SystemClock.elapsedRealtime(),boot(c));save(c,s);}
    static synchronized void acknowledgeCompletion(Context c){if(read(c).status.equals("finished"))save(c,new TimerState());}
    static synchronized void cancel(Context c){TimerState s=read(c);s.cancel();save(c,s);}
}
