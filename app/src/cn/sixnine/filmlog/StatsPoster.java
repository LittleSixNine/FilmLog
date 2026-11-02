package cn.sixnine.filmlog;

import android.graphics.*;
import java.io.*;
import java.util.*;

final class StatsPoster {
    static void write(StatsStore.Summary summary,boolean archived,OutputStream out)throws IOException{
        if(summary.year==0)throw new IOException("请先选择一个年份");Bitmap bitmap=Bitmap.createBitmap(1200,1600,Bitmap.Config.ARGB_8888);
        try{Canvas canvas=new Canvas(bitmap);canvas.drawColor(0xff21170f);Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);paint.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));
            line(canvas,paint,"胶片随记",64,88,30,0xffb4a99f);line(canvas,paint,String.valueOf(summary.year),60,280,180,0xffef8249);line(canvas,paint,"年度拍摄回顾",64,353,44,0xfff4eee2);
            metric(canvas,paint,64,480,summary.frames,"张曝光");metric(canvas,paint,446,480,summary.rolls,"卷胶片");metric(canvas,paint,828,480,summary.days,"个拍摄日期");
            line(canvas,paint,"每月曝光",64,660,32,0xfff4eee2);int max=1;for(int n:summary.months)max=Math.max(max,n);
            for(int i=0;i<12;i++){float x=68+i*89;float height=240f*summary.months[i]/max;paint.setColor(0xff453933);canvas.drawRect(x,720,x+56,960,paint);paint.setColor(0xffef8249);canvas.drawRect(x,960-height,x+56,960,paint);fitted(canvas,paint,String.valueOf(summary.months[i]),x,700,22,72,0xfff4eee2);line(canvas,paint,String.valueOf(i+1),x+14,999,22,0xffb4a99f);}
            line(canvas,paint,"常用胶片",64,1090,28,0xffb4a99f);line(canvas,paint,top(summary.stocks),64,1140,34,0xfff4eee2);
            line(canvas,paint,"常用相机",64,1210,28,0xffb4a99f);line(canvas,paint,top(summary.cameras),64,1260,34,0xfff4eee2);
            line(canvas,paint,summary.scanned+" 张已关联扫描  ·  "+summary.located+" 张有位置",64,1350,28,0xfff4eee2);
            line(canvas,paint,"按记录时区及偏移后的拍摄日期统计 · "+(archived?"含归档":"不含归档"),64,1470,22,0xffb4a99f);
            line(canvas,paint,"不含照片、坐标或费用",64,1510,22,0xffb4a99f);
            if(!bitmap.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException("无法生成回顾图片");
        }finally{bitmap.recycle();}
    }
    private static String top(Map<String,Integer> values){Map.Entry<String,Integer> best=null;for(Map.Entry<String,Integer> entry:values.entrySet())if(best==null||entry.getValue()>best.getValue())best=entry;if(best==null)return "未记录";String text=best.getKey();int count=text.codePointCount(0,text.length());if(count>24)text=text.substring(0,text.offsetByCodePoints(0,23))+"…";return text+" · "+best.getValue()+" 张";}
    private static void metric(Canvas canvas,Paint paint,int x,int y,int value,String unit){fitted(canvas,paint,String.valueOf(value),x,y,82,308,0xfff4eee2);line(canvas,paint,unit,x,y+62,28,0xffb4a99f);}
    static float fittedSize(Paint paint,String text,float size,float width){paint.setTextSize(size);float measured=paint.measureText(text);return measured>width?size*width/measured:size;}
    private static void fitted(Canvas canvas,Paint paint,String text,float x,float y,int size,float width,int color){paint.setTextSize(fittedSize(paint,text,size,width));paint.setColor(color);canvas.drawText(text,x,y,paint);}
    private static void line(Canvas canvas,Paint paint,String text,float x,float y,int size,int color){paint.setTextSize(size);paint.setColor(color);canvas.drawText(text,x,y,paint);}
}
