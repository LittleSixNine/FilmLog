package cn.sixnine.filmlog;
import java.util.Arrays;
import java.util.List;

/** Camera AE metadata is sampled at 100 ms; three consistent frames establish a reading. */
final class MeterReading {
    static final long MAX_AGE=2000;
    private final double[] window=new double[3];
    private int count,index;
    private long last=-1,acceptedAt=-1;
    private double value=Double.NaN;
    private boolean ready;
    void reset(){count=index=0;last=acceptedAt=-1;value=Double.NaN;ready=false;}
    void interrupt(){count=index=0;ready=false;}
    boolean add(double ev,long now){
        if(!Double.isFinite(ev)||ev < -20||ev > 30){interrupt();return false;}
        if(last>=0&&now-last<100)return false;
        if(last>=0&&now-last>500)interrupt();
        last=now;window[index]=ev;index=(index+1)%3;count=Math.min(3,count+1);
        if(count<3)return false;
        double[] sorted=window.clone();Arrays.sort(sorted);
        if(sorted[2]-sorted[0]>.20){if(sorted[2]-sorted[0]>.7)ready=false;return false;}
        value=sorted[1];acceptedAt=now;ready=true;return true;
    }
    double value(){return value;}
    long acceptedAt(){return acceptedAt;}
    boolean usable(long now){return ready&&acceptedAt>=0&&now>=acceptedAt&&now-acceptedAt<MAX_AGE;}
    static double numeric(String value,boolean aperture){return aperture?Double.parseDouble(PhotoMath.apertureValue(value)):PhotoMath.seconds(value);}
    static String stop(double target,List<String> choices,boolean aperture,String current,boolean hysteresis){
        if(!Double.isFinite(target)||target<=0)throw new IllegalArgumentException("无有效的推荐档位");
        double bestDistance=Double.POSITIVE_INFINITY,min=Double.POSITIVE_INFINITY,max=0;String best=null;
        for(String choice:choices)try{double n=numeric(choice,aperture);min=Math.min(min,n);max=Math.max(max,n);double d=Math.abs(Math.log(n/target)/Math.log(2))*(aperture?2:1);if(d<bestDistance){bestDistance=d;best=choice;}}catch(IllegalArgumentException ignored){}
        if(best==null)throw new IllegalArgumentException("暂无可用档位");
        if(target<min/1.01||target>max*1.01)throw new IllegalArgumentException(aperture?"所需光圈超出滚轮范围，请调整快门或 ISO":"所需快门超出滚轮范围，请调整光圈或 ISO");
        if(hysteresis)try{double d=Math.abs(Math.log(numeric(current,aperture)/target)/Math.log(2))*(aperture?2:1);if(d<=bestDistance+.12)return current;}catch(IllegalArgumentException ignored){}
        return best;
    }
    static double deviation(double ev,double iso,String aperture,String shutter,double compensation){return ev-compensation-PhotoMath.ev100(numeric(shutter,false),iso,numeric(aperture,true));}
}
