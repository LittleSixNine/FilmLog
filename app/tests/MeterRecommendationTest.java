package cn.sixnine.filmlog;
import java.util.*;

/** Pure recommendation selection: no Activity, camera or UI is created. */
public final class MeterRecommendationTest {
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static void rejects(Runnable call){try{call.run();throw new AssertionError("invalid recommendation accepted");}catch(IllegalArgumentException expected){}}
    public static void main(String[] args){
        List<String> stops=ExposureChoices.values(false,"");
        check(MeterActivity.closestShutter(1/4.1718,stops).equals("1/4"),"fractional computed time chooses useful stop");
        check(MeterActivity.closestShutter(1.0/125,stops).equals("1/125"),"exact stop stable");
        check(MeterActivity.closestShutter(15,stops).equals("15s"),"whole seconds retain canonical value");
        check(MeterActivity.closestShutter(1.0/90,ExposureChoices.values(false,"1/90")).equals("1/90"),"custom camera stop remains selectable");
        check(MeterActivity.closestShutter(1.0/4,Arrays.asList("","Auto","B","bad","1/2","1/4")).equals("1/4"),"non-numeric wheel labels never become recommendations");
        rejects(()->MeterActivity.closestShutter(Double.NaN,stops));rejects(()->MeterActivity.closestShutter(0,stops));rejects(()->MeterActivity.closestShutter(1,Arrays.asList("Auto","B","")));
        System.out.println("PASS: useful shutter stops, exact/custom values, non-numeric labels and invalid-input rejection; no UI constructed");
    }
}
