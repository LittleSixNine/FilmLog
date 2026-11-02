package cn.sixnine.filmlog;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.Build;
import org.json.JSONObject;
import java.time.*;
import java.util.*;

final class ExposureReminders {
    static final String CHANNEL="exposure-reminders";
    static final class Entry {String roll,token,status;long due;}
    static android.content.SharedPreferences prefs(Context c){return c.getSharedPreferences("exposure-reminders",0);}
    static Entry read(Context c,String roll){try{String raw=prefs(c).getString(roll,null);if(raw==null)return null;JSONObject j=new JSONObject(raw);Entry e=new Entry();e.roll=roll;e.token=j.getString("token");e.status=j.getString("status");e.due=j.getLong("due");return e;}catch(Exception e){return null;}}
    static void save(Context c,Entry e){try{JSONObject j=new JSONObject();j.put("token",e.token);j.put("status",e.status);j.put("due",e.due);if(!prefs(c).edit().putString(e.roll,j.toString()).commit())throw new IllegalStateException("无法保存提醒");}catch(org.json.JSONException error){throw new IllegalStateException(error);}}
    static long preset(long now,ZoneId zone,int choice){ZonedDateTime current=Instant.ofEpochMilli(now).atZone(zone);if(choice==0)return Math.addExact(now,7200000L);if(choice==1){ZonedDateTime evening=current.toLocalDate().atTime(19,0).atZone(zone);if(evening.isBefore(current))evening=evening.plusHours(24);return evening.toInstant().toEpochMilli();}if(choice==2)return current.toLocalDate().plusDays(1).atTime(9,0).atZone(zone).toInstant().toEpochMilli();throw new IllegalArgumentException("未知提醒时间");}
    static void channel(Context c){NotificationManager manager=c.getSystemService(NotificationManager.class);NotificationChannel channel=new NotificationChannel(CHANNEL,"曝光记录提醒",NotificationManager.IMPORTANCE_DEFAULT);channel.setDescription("你为胶卷手动设置的拍摄记录提醒");manager.createNotificationChannel(channel);}
    static boolean allowed(Context c){channel(c);NotificationManager manager=c.getSystemService(NotificationManager.class);return manager.areNotificationsEnabled()&&manager.getNotificationChannel(CHANNEL).getImportance()!=NotificationManager.IMPORTANCE_NONE;}
    private static PendingIntent alarmIntent(Context c,Entry e){Intent intent=new Intent(c,ReminderReceiver.class).setAction("cn.sixnine.filmlog.EXPOSURE_REMINDER").setData(Uri.parse("filmlog-reminder:"+(Workspace.identity(c).equals("guest")?"":Workspace.identity(c)+":")+e.roll)).putExtra("roll",e.roll).putExtra("token",e.token).putExtra("workspace",Workspace.identity(c));return PendingIntent.getBroadcast(c,0,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    static void schedule(Context c,Entry e){c.getSystemService(AlarmManager.class).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,e.due,alarmIntent(c,e));}
    static synchronized void set(Context c,String roll,long due){if(due<=System.currentTimeMillis())throw new IllegalArgumentException("提醒时间必须在未来");if(!allowed(c))throw new IllegalStateException("系统通知未开启，请先允许曝光记录提醒");try(LogStore logs=new LogStore(c)){LogStore.Roll r=logs.roll(roll);if(r==null||r.archived||!"active".equals(r.state))throw new IllegalArgumentException("只可为未归档且拍摄中的胶卷设置提醒");}Entry e=new Entry();e.roll=roll;e.token=UUID.randomUUID().toString();e.due=due;e.status="pending";save(c,e);try{schedule(c,e);}catch(RuntimeException error){e.status="failed";save(c,e);throw error;}}
    static synchronized void cancel(Context c,String roll){Entry e=read(c,roll);if(e==null)return;c.getSystemService(AlarmManager.class).cancel(alarmIntent(c,e));e.status="cancelled";save(c,e);c.getSystemService(NotificationManager.class).cancel(roll,1);}
    static synchronized void remove(Context c,String roll){cancel(c,roll);if(!prefs(c).edit().remove(roll).commit())throw new IllegalStateException("提醒清理未完成");}
    static synchronized Entry claim(Context c,String roll,String token,long now){Entry e=read(c,roll);if(e==null||!"pending".equals(e.status)||!e.token.equals(token)||now<e.due)return null;e.status="delivering";save(c,e);return e;}
    static synchronized void deliver(Context c,String roll,String token){Entry e=read(c,roll);if(e==null||!"pending".equals(e.status)||!e.token.equals(token))return;if(e.due>System.currentTimeMillis()){schedule(c,e);return;}try(LogStore logs=new LogStore(c)){LogStore.Roll r=logs.roll(roll);if(r==null||r.archived||!"active".equals(r.state)){cancel(c,roll);return;}if(!allowed(c)){e.status="blocked";save(c,e);return;}e=claim(c,roll,token,System.currentTimeMillis());if(e==null)return;
        // At-most-once: process death between claiming and notifying is reported in the reminders page, never replayed.
        Intent open=new Intent().setClassName(c,"cn.sixnine.filmlog.MainActivity").setData(Uri.parse("filmlog-roll:"+roll)).putExtra("open_roll",roll).putExtra("widget_workspace",Workspace.identity(c)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent action=PendingIntent.getActivity(c,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification notification=new Notification.Builder(c,CHANNEL).setSmallIcon(R.drawable.brand_notification).setContentTitle("记录你的下一张曝光 🎞️").setContentText("在 "+r.name+" 上记录你的下一张曝光").setContentIntent(action).setAutoCancel(true).setOnlyAlertOnce(true).setVisibility(Notification.VISIBILITY_PRIVATE).build();c.getSystemService(NotificationManager.class).notify(roll,1,notification);e.status="delivered";save(c,e);
    }catch(RuntimeException error){e=read(c,roll);if(e!=null){e.status="failed";save(c,e);}}}
    static synchronized void restore(Context c){for(String roll:new ArrayList<>(prefs(c).getAll().keySet())){Entry e=read(c,roll);if(e==null)continue;if("delivering".equals(e.status)){e.status="interrupted";save(c,e);}else if("pending".equals(e.status)){try{schedule(c,e);}catch(RuntimeException error){e.status="failed";save(c,e);}}}}
    static String status(Entry e){if(e==null)return "未设置提醒";String time=java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm").format(Instant.ofEpochMilli(e.due).atZone(ZoneId.systemDefault()));switch(e.status){case "pending":return time+" · 等待提醒";case "delivered":return time+" · 已交给系统通知";case "blocked":return "通知被关闭，未能提醒，请重新设置";case "cancelled":return "提醒已取消";case "delivering":return "正在发送提醒";case "interrupted":return "发送中断，结果未知；不会自动重复，请重新设置";default:return "提醒失败，请重新设置";}}
}
