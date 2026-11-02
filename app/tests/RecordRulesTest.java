import cn.sixnine.filmlog.RecordRules;
public class RecordRulesTest {
 public static void main(String[] args) {
        if(!RecordRules.rewindFormat("35 mm")||!RecordRules.rewindFormat("135")||RecordRules.rewindFormat("4x5")||RecordRules.rewindFormat("120")||RecordRules.rewindFormat(null))throw new AssertionError("finish wording follows confirmed film format");
  if(RecordRules.DEFAULT_OFFSET!=0||RecordRules.captureTime(10000,RecordRules.DEFAULT_OFFSET)!=10000)throw new AssertionError("new record default offset must be zero");

  check(RecordRules.canRecord(false,"active"),"active roll accepts frames beyond nominal capacity");
  for(String state:new String[]{"finished","developing","received","unknown"})check(!RecordRules.canRecord(false,state),"manual lifecycle remains guarded "+state);
  check(!RecordRules.canRecord(true,"active"),"archive blocks recording");
  check(!RecordRules.nominalReached(35,36)&&RecordRules.nominalReached(36,36)&&RecordRules.nominalReached(38,36),"nominal threshold is advisory");
  check(RecordRules.validFrame(37)&&RecordRules.validFrame(38)&&!RecordRules.validFrame(0)&&!RecordRules.validFrame(-1)&&!RecordRules.validFrame(Integer.MAX_VALUE),"positive frame and overflow guard");
  check(RecordRules.nominalWarning(36,"35 mm").equals("已达标称 36 张，请留意回卷时机。"),"135 rewind warning");
  check(RecordRules.nominalWarning(12,"120").equals("已达标称 12 张，请留意剩余胶片。"),"120 warning avoids rewinding advice");
  for(long valid:new long[]{-62135596800000L,0,253402300799999L})check(RecordRules.editedTime(valid)==valid,"manual time bound");
  for(long bad:new long[]{Long.MIN_VALUE,-62135596800001L,253402300800000L,Long.MAX_VALUE}){boolean failed=false;try{RecordRules.editedTime(bad);}catch(IllegalArgumentException expected){failed=true;}check(failed,"manual time out of range");}
  long t=1700000000000L;
  check(RecordRules.captureTime(t,-10)==t-10000,"minus ten");
  check(RecordRules.captureTime(t,0)==t,"zero");
  check(RecordRules.captureTime(t,20)==t+20000,"positive");
  check(RecordRules.parseOffset(" -10 ")==-10,"parse");
  for(String bad:new String[]{"","abc","1.5","3601","-3601","999999999999"}) {
   boolean failed=false;try {RecordRules.parseOffset(bad);} catch(IllegalArgumentException e) {failed=true;}
   check(failed,"invalid "+bad);
  }
  check(RecordRules.usableFix(30000,15000,31,121,25),"fresh boundary");
  check(RecordRules.usableFix(30000,14999,31,121,25),"recent precise position survives 15-second boundary");
  check(RecordRules.usableFix(60000,30000,31,121,50),"unknown-speed30 boundary");
  check(!RecordRules.usableFix(60000,29999,31,121,50),"unknown-speed expires after30");
  check(!RecordRules.usableFix(60000,40000,31,121,50.1f),"coarse older estimate rejected");
  check(RecordRules.usableFix(90000,30000,31,121,8,0),"stationary60 boundary");
  check(!RecordRules.usableFix(90001,30000,31,121,8,0),"all estimates expire after60");
  check(RecordRules.usableFix(90000,30000,31,121,10,1.5f),"slow movement inside100m budget");
  check(!RecordRules.usableFix(90000,30000,31,121,11,1.5f),"drift plus accuracy outside100m rejected");
  check(!RecordRules.usableFix(40000,20000,31,121,8,4),"moving fast cannot extend stale fix");
  for(float speed:new float[]{Float.NaN,Float.POSITIVE_INFINITY,-2})check(!RecordRules.usableFix(40000,20000,31,121,8,speed),"invalid speed cannot extend position");
  check(RecordRules.usableFix(40000,30000,31,121,1000,10),"fresh approximate estimate retains existing semantics");
  check(!RecordRules.usableFix(40000,-1,31,121,8,0)&&!RecordRules.usableFix(-1,0,31,121,8,0),"negative monotonic timestamps rejected");
  check(!RecordRules.usableFix(30000,30001,31,121,25),"future");
  check(!RecordRules.usableFix(30000,29000,Double.NaN,121,25),"NaN");
  check(!RecordRules.usableFix(30000,29000,91,121,25),"latitude");
  check(!RecordRules.usableFix(30000,29000,31,181,25),"longitude");
  check(!RecordRules.usableFix(30000,29000,31,121,-1),"accuracy");
  check(RecordRules.usableFix(30000,29000,0,0,10),"real zero coordinate");
  check(RecordRules.matchesRecordFix(100000,75000,31,121,8,-1),"recent point before tap matches");
  check(RecordRules.matchesRecordFix(100000,120000,31,121,8,-1),"post-tap20 boundary matches");
  check(!RecordRules.matchesRecordFix(100000,120001,31,121,8,-1),"later location cannot silently fill original tap");
  check(!RecordRules.matchesRecordFix(100000,115000,31,121,8,15),"later moving location beyond uncertainty budget rejected");
  check(!RecordRules.matchesRecordFix(-1,100000,31,121,8,0),"missing event anchor rejected");
  System.out.println("RecordRules: all checks passed");
 }
 static void check(boolean pass,String name) {if(!pass)throw new AssertionError(name);}
}
