package cn.sixnine.filmlog;

import java.util.ArrayList;

public final class ExposureChoicesTest {
    public static void main(String[] args) {
        check(java.util.Collections.frequency(ExposureChoices.values(false,PhotoMath.shutterChoice(2)),"2s")==1&&!ExposureChoices.values(false,PhotoMath.shutterChoice(2)).contains("2"),"calculated whole seconds reuse the existing stop");
        check(ExposureChoices.meterValues(true,"3.2").contains("0.7")&&ExposureChoices.meterValues(true,"3.2").contains("256")&&ExposureChoices.meterValues(true,"3.2").indexOf("3.2")<ExposureChoices.meterValues(true,"3.2").indexOf("3.5"),"meter preserves .7–256 plus original and custom aperture stops");
        ArrayList<String> apertures=ExposureChoices.values(true,"");
        ArrayList<String> shutters=ExposureChoices.values(false,"");
        check(apertures.size()==20&&apertures.get(0).equals("Auto")&&apertures.get(19).equals("64"),"original twenty aperture stops");
        check(shutters.size()==20&&shutters.get(0).equals("Auto")&&shutters.get(1).equals("B")&&shutters.get(19).equals("1/4000"),"original twenty shutter stops");
        check(ExposureChoices.values(true,"3.2").indexOf("3.2")==8,"custom aperture numeric order");
        check(ExposureChoices.values(true,"0.7").indexOf("0.7")==1,"custom aperture before first numeric");
        check(ExposureChoices.values(true,"256").get(20).equals("256"),"custom aperture after last numeric");
        check(ExposureChoices.values(false,"1/90").indexOf("1/90")==14,"custom shutter between 1/60 and 1/125");
        check(ExposureChoices.values(false,"45s").indexOf("45s")==2,"long custom shutter follows Auto and B");
        check(ExposureChoices.values(false,"1/8000").get(20).equals("1/8000"),"fast custom shutter appended");
        check(ExposureChoices.values(true,"2.80").indexOf("2.80")==8,"equal numeric aperture retains distinct original spelling after base");
        check(ExposureChoices.values(false,"0.5s").indexOf("0.5s")==9,"equal numeric shutter retains distinct spelling after 1/2");
        for(String invalid:new String[]{"Unknown","NaN","0","-1"}) {
            check(ExposureChoices.values(true,invalid).get(20).equals(invalid),"opaque current aperture remains visible at end");
            check(ExposureChoices.values(false,invalid).get(20).equals(invalid),"opaque current shutter remains visible at end");
        }
        for(String special:new String[]{"Auto","B"})check(ExposureChoices.values(false,special).size()==20,"special stop not duplicated");
        check(ExposureChoices.toggle("Auto","Auto").isEmpty()&&ExposureChoices.toggle("B","B").isEmpty(),"special stops deselect");
        check(ExposureChoices.toggle("8","8").isEmpty()&&ExposureChoices.toggle("8","11").equals("11"),"numeric stops deselect or switch");
        check(ExposureChoices.apertureLabel("Auto").equals("Auto")&&ExposureChoices.apertureLabel("").equals("—")&&ExposureChoices.apertureLabel("3.2").equals("f/3.2"),"labels do not fabricate f/Auto");
        check(apertures.size()==20&&shutters.size()==20,"custom lists do not mutate base");
        System.out.println("ExposureChoices: original stops, numeric insertion, Auto/B and repeat deselection passed");
    }
    private static void check(boolean pass,String message){if(!pass)throw new AssertionError(message);}
}
