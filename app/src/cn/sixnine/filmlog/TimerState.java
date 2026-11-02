package cn.sixnine.filmlog;

/** Monotonic deadlines survive process death, but never a device reboot. */
final class TimerState {
    String status="idle";long remaining,due,total;int boot;
    void start(long duration,long now,int bootId){if(duration<1000||duration>604800000L)throw new IllegalArgumentException("计时须为1秒至7天");remaining=duration;total=duration;due=Math.addExact(now,duration);boot=bootId;status="running";}
    boolean update(long now,int bootId){if(!status.equals("running")&&!status.equals("paused"))return false;if(boot!=bootId||now<0){status="interrupted";return true;}if(status.equals("paused"))return false;remaining=Math.max(0,due-now);if(remaining==0){status="finished";return true;}return false;}
    void pause(long now,int bootId){update(now,bootId);if(status.equals("running")){status="paused";}}
    void resume(long now,int bootId){if(!status.equals("paused"))throw new IllegalStateException("计时未暂停");due=Math.addExact(now,remaining);boot=bootId;status="running";}
    void cancel(){status="cancelled";remaining=0;}
    static String duration(long millis){long seconds=millis<=0?0:millis/1000+(millis%1000==0?0:1);return seconds<3600?String.format(java.util.Locale.ROOT,"%d:%02d",seconds/60,seconds%60):String.format(java.util.Locale.ROOT,"%d:%02d:%02d",seconds/3600,seconds/60%60,seconds%60);}
}
