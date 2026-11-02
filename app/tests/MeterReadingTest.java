package cn.sixnine.filmlog;
import java.util.*;
public final class MeterReadingTest {
 static int checks;static void check(boolean ok,String reason){checks++;if(!ok)throw new AssertionError(reason);}
 static void reject(Runnable r){try{r.run();throw new AssertionError("invalid accepted");}catch(IllegalArgumentException expected){checks++;}}
 public static void main(String[] args){
  MeterReading m=new MeterReading();check(!m.add(12,0)&&!m.add(12.1,100)&&m.add(11.95,200),"three distinct samples");check(m.usable(200)&&m.value()==12,"median");check(!m.add(16,210)&&m.value()==12,"frame rate limit");
  check(!m.add(16,300)&&!m.usable(300),"large change not used prematurely");check(!m.add(16.05,400)&&m.add(16,500)&&m.value()==16,"scene settles in 200 ms");
  check(!m.add(3,600)&&m.value()==16,"outlier never displayed");m.add(16,700);m.add(16,800);check(m.add(16,900),"recovers from outlier");check(m.usable(2899)&&!m.usable(2900),"expires even with no callbacks");
  check(!m.add(10,3000)&&!m.usable(3000),"long gap starts a new window");m.add(10,3100);check(m.add(10,3200),"new window");m.interrupt();check(!m.usable(3200)&&!m.add(10,3300),"bad AE interrupts stable sequence");m.add(10,3400);check(m.add(10,3500),"reconverges");m.reset();check(!m.usable(3500)&&Double.isNaN(m.value()),"reset");check(!m.add(Double.NaN,4000),"invalid input");
  List<String> s=Arrays.asList("Auto","B","1/60","1/125","1/250");double boundary=Math.sqrt(1.0/125/250);check(MeterReading.stop(boundary*.99,s,false,"1/125",true).equals("1/125"),"stop hysteresis");check(MeterReading.stop(boundary*.85,s,false,"1/125",true).equals("1/250"),"real change crosses hysteresis");
  check(MeterReading.stop(8.1,Arrays.asList("Auto","5.6","8","11"),true,"5.6",false).equals("8"),"aperture recommendation");reject(()->MeterReading.stop(40,s,false,"1/60",true));reject(()->MeterReading.stop(Double.NaN,s,false,"1/60",true));
  double ev=PhotoMath.ev100(1.0/125,400,8);check(Math.abs(MeterReading.deviation(ev,400,"8","1/125",0))<1e-9,"matched manual");check(Math.abs(MeterReading.deviation(ev,400,"8","1/125",1)+1)<1e-9,"compensation sign");check(Math.abs(PhotoMath.aperture(ev,400,1.0/125,0)-8)<1e-9,"shutter priority inverse");check(MeterReading.deviation(ev,400,"8","1/60",0)>.99,"slower shutter overexposes");
  System.out.println("PASS MeterReading "+checks+" assertions: stability, scene changes, stale data, gaps, stop hysteresis, limits and manual EV semantics");
 }
}
