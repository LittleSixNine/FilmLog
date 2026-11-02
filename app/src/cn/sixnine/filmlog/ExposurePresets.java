package cn.sixnine.filmlog;

import android.os.Bundle;

/** Built-in MemoTemplate values recovered from Pellica's ARM32 snapshot. */
final class ExposurePresets {
    final String id,label,aperture,shutter,lens;
    final int icon,color;
    private ExposurePresets(String id,String label,String aperture,String shutter,int icon,int color){this(id,label,aperture,shutter,"",icon,color);}
    ExposurePresets(String id,String label,String aperture,String shutter,String lens,int icon,int color){this.lens=lens;this.id=id;this.label=label;this.aperture=aperture;this.shutter=shutter;this.icon=icon;this.color=color;}
    static final ExposurePresets[] ALL={
        new ExposurePresets("portrait","人像","2.8","1/125",0xe4c2,0xffe91e63),
        new ExposurePresets("landscape","风景","11","1/60",0xe7ae,0xff4caf50),
        new ExposurePresets("street","街头","8","1/250",0xe3a8,0xff607d8b),
        new ExposurePresets("night","夜景","2","1/30",0xe330,0xff3f51b5),
        new ExposurePresets("action","运动","4","1/500",0xe730,0xffff5722),
        new ExposurePresets("macro","微距","16","1/60",0xe75e,0xff8bc34a),
        new ExposurePresets("architecture","建筑","8","1/125",0xe102,0xff795548)
    };
    void apply(Bundle draft){draft.putString("preset",id);draft.putString("aperture",aperture);draft.putString("shutter",shutter);draft.putString("lens",lens);draft.putString("ev","");}
}
