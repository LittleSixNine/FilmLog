package cn.sixnine.filmlog;

import java.util.ArrayList;
import java.util.Arrays;

final class ExposureChoices {
    private static final String[] APERTURES={"Auto","1","1.2","1.4","1.8","2","2.5","2.8","3.5","4","4.5","5.6","6.3","8","11","16","22","32","45","64"};
    private static final String[] SHUTTERS={"Auto","B","30s","15s","8s","4s","2s","1s","1/2","1/4","1/8","1/15","1/30","1/60","1/125","1/250","1/500","1/1000","1/2000","1/4000"};
    private ExposureChoices() {}

    static ArrayList<String> values(boolean aperture,String selected) {
        ArrayList<String> values=new ArrayList<>(Arrays.asList(aperture?APERTURES:SHUTTERS));
        if(selected.isEmpty()||values.contains(selected))return values;
        double number;
        try {number=numeric(aperture,selected);} catch(IllegalArgumentException nonnumeric) {values.add(selected);return values;}
        int position=values.size();
        for(int i=0;i<values.size();i++) {
            try {
                double candidate=numeric(aperture,values.get(i));
                if(aperture?candidate>number:candidate<number){position=i;break;}
            } catch(IllegalArgumentException special) { /* Auto and B remain before numeric stops. */ }
        }
        values.add(position,selected);
        return values;
    }

    static ArrayList<String> meterValues(boolean aperture,String selected){ArrayList<String> result=values(aperture,selected);if(aperture){for(double number:PhotoMath.APERTURES){String value=PhotoMath.decimal(number);if(!result.contains(value))result.add(value);}result.subList(1,result.size()).sort((a,b)->Double.compare(numeric(true,a),numeric(true,b)));}return result;}
    private static double numeric(boolean aperture,String value) {
        return aperture?Double.parseDouble(PhotoMath.apertureValue(value)):PhotoMath.seconds(value);
    }
    static String toggle(String current,String clicked){return current.equals(clicked)?"":clicked;}
    static String apertureLabel(String value){return value.isEmpty()?"—":value.equals("Auto")?value:"f/"+value;}
}
