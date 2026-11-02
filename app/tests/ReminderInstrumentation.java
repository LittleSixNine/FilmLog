package cn.sixnine.filmlog;
import android.app.*;
import android.os.Bundle;
import java.time.*;

public final class ReminderInstrumentation extends Instrumentation {
    private void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){waitForIdleSync();Bundle result=new Bundle();try{
        android.content.Context c=getTargetContext();check(c.getPackageName().equals("cn.sixnine.filmlog.media.tests"),"isolated package");
        ZoneId zone=ZoneId.of("America/New_York");long now=ZonedDateTime.of(2026,3,7,9,0,0,0,zone).toInstant().toEpochMilli();
        check(ExposureReminders.preset(now,zone,0)-now==7200000,"two actual hours");check(ExposureReminders.preset(now,zone,2)-now==23*3600000L,"tomorrow 9 survives daylight saving");
        long late=ZonedDateTime.of(2026,10,22,20,0,0,0,ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();check(Instant.ofEpochMilli(ExposureReminders.preset(late,ZoneId.of("Asia/Shanghai"),1)).atZone(ZoneId.of("Asia/Shanghai")).getDayOfMonth()==23,"evening passed rolls forward");
        ZoneId shanghai=ZoneId.of("Asia/Shanghai");long early=ZonedDateTime.of(2026,10,22,18,30,0,0,shanghai).toInstant().toEpochMilli();ZonedDateTime evening=Instant.ofEpochMilli(ExposureReminders.preset(early,shanghai,1)).atZone(shanghai);check(evening.getDayOfMonth()==22&&evening.getHour()==19,"original evening is today 19");long exact=evening.toInstant().toEpochMilli();check(ExposureReminders.preset(exact,shanghai,1)==exact&&ExposureReminders.preset(exact+1,shanghai,1)==exact+86400000L,"strict original isBefore boundary");long dst=ZonedDateTime.of(2026,3,7,20,0,0,0,zone).toInstant().toEpochMilli();ZonedDateTime dstEvening=Instant.ofEpochMilli(ExposureReminders.preset(dst,zone,1)).atZone(zone);check(dstEvening.getDayOfMonth()==8&&dstEvening.getHour()==20,"original passed-evening adds fixed 24 hours across DST");
        c.deleteDatabase("film-log.db");c.getSharedPreferences("exposure-reminders",0).edit().clear().commit();
        try(LogStore logs=new LogStore(c)){
            String roll=logs.addRoll("Reminder test","Test","Test",400,36);ExposureReminders.Entry e=new ExposureReminders.Entry();e.roll=roll;e.token="old";e.status="pending";e.due=System.currentTimeMillis()+600000;ExposureReminders.save(c,e);
            check(ExposureReminders.claim(c,roll,"old",e.due-1)==null,"cannot fire early");check(ExposureReminders.claim(c,roll,"stale",e.due)==null,"stale token ignored");
            check(ExposureReminders.claim(c,roll,"old",e.due)!=null,"first claim");check(ExposureReminders.claim(c,roll,"old",e.due)==null,"duplicate claim rejected");
            ExposureReminders.restore(c);check("interrupted".equals(ExposureReminders.read(c,roll).status),"uncertain delivery not replayed");
            e.token="new";e.status="pending";e.due=System.currentTimeMillis()-1000;ExposureReminders.save(c,e);boolean enabled=ExposureReminders.allowed(c);ExposureReminders.deliver(c,roll,"new");
            String expected=enabled?"delivered":"blocked";check(expected.equals(ExposureReminders.read(c,roll).status),"notification permission outcome");
            ExposureReminders.deliver(c,roll,"new");check(expected.equals(ExposureReminders.read(c,roll).status),"duplicate broadcast retains terminal status");
            if(enabled){int count=0;for(android.service.notification.StatusBarNotification n:c.getSystemService(NotificationManager.class).getActiveNotifications())if(roll.equals(n.getTag())){count++;check(n.getNotification().getSmallIcon().getResId()==R.drawable.brand_notification,"reminder uses brand monochrome icon");}check(count==1,"single notification published");}
            ExposureReminders.cancel(c,roll);check("cancelled".equals(ExposureReminders.read(c,roll).status),"cancel persisted");
            e.token="scheduled";e.status="pending";e.due=System.currentTimeMillis()+3600000;ExposureReminders.save(c,e);ExposureReminders.restore(c);check("pending".equals(ExposureReminders.read(c,roll).status),"rescheduling preserves identity");ExposureReminders.cancel(c,roll);
        }
        result.putString("stream","PASS: reminder presets and DST; durable claims, early/stale/duplicate guards; notification permission and publication; cancellation, rescheduling and interrupted delivery\n");finish(-1,result);
    }catch(Throwable error){result.putString("stream","FAIL: "+android.util.Log.getStackTraceString(error));finish(0,result);}}
}
