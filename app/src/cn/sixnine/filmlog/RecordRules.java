package cn.sixnine.filmlog;

public final class RecordRules {
    public static final int DEFAULT_OFFSET = 0;
    public static final long MAX_FIX_AGE_MS = 15000;
    public static final long RECENT_FIX_AGE_MS=60000,UNKNOWN_SPEED_AGE_MS=30000;
    public static final float RECENT_FIX_ACCURACY_M=50,RECENT_FIX_SPEED_MPS=3,RECENT_FIX_DISTANCE_M=100;
    public static final long WAIT_MS = 20000;
    private RecordRules() {}
    public static boolean canRecord(boolean archived,String state){return !archived&&"active".equals(state);}
    public static boolean validFrame(int frame){return frame>0&&frame<Integer.MAX_VALUE;}
    public static boolean nominalReached(int lastFrame,int nominal){return nominal>0&&lastFrame>=nominal;}
    public static boolean rewindFormat(String format){String value=format==null?"":format.replace(" ","").toLowerCase(java.util.Locale.ROOT);return value.equals("35mm")||value.equals("135");}
    public static String nominalWarning(int nominal,String format){String normalized=format==null?"":format.replace(" ","");return "已达标称 "+nominal+" 张，"+((normalized.equals("35mm")||normalized.equals("135"))?"请留意回卷时机。":"请留意剩余胶片。");}
    public static int parseOffset(String text) {
        int seconds = Integer.parseInt(text.trim());
        if (seconds < -3600 || seconds > 3600) throw new IllegalArgumentException("偏移须在 −3600 到 +3600 秒之间");
        return seconds;
    }
    public static long captureTime(long tappedAt, int offsetSeconds) {
        if (offsetSeconds < -3600 || offsetSeconds > 3600) throw new IllegalArgumentException("offset");
        return Math.addExact(tappedAt, offsetSeconds * 1000L);
    }
    public static long editedTime(long millis) {
        if(millis < -62135596800000L || millis > 253402300799999L)throw new IllegalArgumentException("拍摄时间须在公元1至9999年之间");
        return millis;
    }
    public static boolean validFix(long nowElapsedMs,long fixElapsedMs,double lat,double lon,float accuracy){
        return fixElapsedMs>=0&&fixElapsedMs<=nowElapsedMs&&nowElapsedMs-fixElapsedMs<=RECENT_FIX_AGE_MS
            &&Double.isFinite(lat)&&Double.isFinite(lon)&&lat>=-90&&lat<=90&&lon>=-180&&lon<=180
            &&Float.isFinite(accuracy)&&accuracy>=0;
    }
    public static boolean usableFix(long nowElapsedMs,long fixElapsedMs,double lat,double lon,float accuracy){
        return usableFix(nowElapsedMs,fixElapsedMs,lat,lon,accuracy,-1);
    }
    public static boolean usableFix(long nowElapsedMs,long fixElapsedMs,double lat,double lon,float accuracy,float speedUpperBound){
        if(!validFix(nowElapsedMs,fixElapsedMs,lat,lon,accuracy))return false;
        long age=nowElapsedMs-fixElapsedMs;if(age<=MAX_FIX_AGE_MS)return true;
        if(accuracy>RECENT_FIX_ACCURACY_M)return false;
        if(speedUpperBound==-1)return age<=UNKNOWN_SPEED_AGE_MS;
        return Float.isFinite(speedUpperBound)&&speedUpperBound>=0&&speedUpperBound<=RECENT_FIX_SPEED_MPS
            &&accuracy+speedUpperBound*(age/1000f)<=RECENT_FIX_DISTANCE_M;
    }
    public static boolean matchesRecordFix(long tapElapsed,long fixElapsed,double lat,double lon,float accuracy,float speedUpperBound){
        if(tapElapsed<0)return false;
        if(fixElapsed<=tapElapsed)return usableFix(tapElapsed,fixElapsed,lat,lon,accuracy,speedUpperBound);
        long delay=fixElapsed-tapElapsed;
        if(delay>WAIT_MS||!validFix(fixElapsed,fixElapsed,lat,lon,accuracy))return false;
        return speedUpperBound==-1||Float.isFinite(speedUpperBound)&&speedUpperBound>=0
            &&accuracy+speedUpperBound*(delay/1000f)<=RECENT_FIX_DISTANCE_M;
    }

}
