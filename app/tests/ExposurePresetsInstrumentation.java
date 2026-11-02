package cn.sixnine.filmlog;
import android.app.Instrumentation;
import android.os.Bundle;
import java.util.HashSet;

public final class ExposurePresetsInstrumentation extends Instrumentation {
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){waitForIdleSync();Bundle out=new Bundle();try{
        check(getTargetContext().getPackageName().equals("cn.sixnine.filmlog.media.tests"),"isolated test package");
        HashSet<String> ids=new HashSet<>();Bundle draft=new Bundle();draft.putString("memo","Original note • #Portrait • #Night");draft.putString("filters","ND 3");draft.putString("offset","-10");draft.putLong("recordTap",1792769062000L);draft.putInt("recordZone",480);draft.putString("referenceToken","existing-reference");
        for(ExposurePresets preset:ExposurePresets.ALL){check(ids.add(preset.id),"unique preset");draft.putString("lens","50 mm");draft.putString("ev","+1/3");preset.apply(draft);check(draft.getString("preset").equals(preset.id),"selection");check(draft.getString("lens").isEmpty()&&draft.getString("ev").isEmpty(),"preset clears lens and EV");check(Double.parseDouble(PhotoMath.apertureValue(draft.getString("aperture")))>0&&PhotoMath.seconds(draft.getString("shutter"))>0,"valid numerical exposure");draft.putString("aperture","64");preset.apply(draft);check(draft.getString("aperture").equals(preset.aperture),"repeat reapplies rather than deselects");check(draft.getString("memo").equals("Original note • #Portrait • #Night")&&draft.getString("filters").equals("ND 3"),"memo tags and filters retained");check(draft.getString("offset").equals("-10")&&draft.getLong("recordTap")==1792769062000L&&draft.getInt("recordZone")==480&&draft.getString("referenceToken").equals("existing-reference"),"capture identity and media retained");}
        check(ids.size()==7,"seven builtins");out.putString("stream","PASS: seven presets, repeat apply, clearing and capture preservation");finish(-1,out);
    }catch(Throwable error){out.putString("stream","FAIL: "+error);finish(0,out);}}
}
