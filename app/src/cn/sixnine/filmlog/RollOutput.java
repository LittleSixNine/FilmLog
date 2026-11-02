package cn.sixnine.filmlog;

import android.content.Context;
import android.graphics.*;
import android.graphics.pdf.PdfDocument;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.*;

final class RollOutput {
    static int zip(Context context,LogStore logs,String roll,boolean gps,OutputStream target)throws Exception{
        ScanStore files=new ScanStore(context,logs);LogStore.Roll r=logs.roll(roll);ArrayList<ScanStore.Scan> selected=new ArrayList<>();for(ScanStore.Scan s:files.list(roll))if(!s.shotId.isEmpty())selected.add(s);
        if(selected.isEmpty())throw new IOException("没有已关联扫描可导出");selected.sort(Comparator.comparingInt(s->logs.shot(s.shotId).frame));
        StringBuilder index=new StringBuilder("file,frame,source_name,captured_at_utc,offset_seconds,gps_included\r\n");
        try(ZipOutputStream out=new ZipOutputStream(target)){
            for(ScanStore.Scan s:selected){LogStore.Shot shot=logs.shot(s.shotId);String name=String.format(Locale.ROOT,"frame-%03d.jpg",shot.frame);File image=ScanExport.create(context,files.file(s.id),r,shot,gps);
                try{out.putNextEntry(new ZipEntry(name));try(InputStream in=new FileInputStream(image)){byte[] buffer=new byte[65536];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);}out.closeEntry();}finally{image.delete();}
                index.append(name).append(',').append(shot.frame).append(',').append('"').append(s.name.replace("\"","\"\"")).append("\",").append(Instant.ofEpochMilli(shot.captured)).append(',').append(shot.offset).append(',').append(gps&&shot.lat!=null).append("\r\n");
            }
            out.putNextEntry(new ZipEntry("index.csv"));out.write(index.toString().getBytes(StandardCharsets.UTF_8));out.closeEntry();
        }return selected.size();
    }
    static int contactSheet(Context context,LogStore logs,String roll,OutputStream target)throws Exception{
        LogStore.Roll r=logs.roll(roll);ArrayList<LogStore.Shot> shots=logs.shots(roll);shots.sort(Comparator.comparingInt(s->s.frame));if(shots.isEmpty())throw new IOException("没有曝光可生成接触印样");
        CatalogStore.Detail metadata=new CatalogStore(logs).detail(roll);String iso=PhotoMath.isoEiLabel(r.iso,metadata==null?0:metadata.pushPull);
        ScanStore files=new ScanStore(context,logs);Map<String,ScanStore.Scan> scans=new HashMap<>();for(ScanStore.Scan s:files.list(roll))if(!s.shotId.isEmpty())scans.put(s.shotId,s);
        DateTimeFormatter time=DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss",Locale.ROOT);
        PdfDocument document=new PdfDocument();try{
            for(int start=0,page=1;start<shots.size();start+=12,page++){
                PdfDocument.Page sheet=document.startPage(new PdfDocument.PageInfo.Builder(595,842,page).create());try{Canvas c=sheet.getCanvas();c.drawColor(Color.WHITE);android.text.TextPaint p=new android.text.TextPaint(Paint.ANTI_ALIAS_FLAG);p.setColor(Color.BLACK);p.setTextSize(18);c.drawText(android.text.TextUtils.ellipsize(r.name,p,547,android.text.TextUtils.TruncateAt.END).toString(),24,32,p);p.setTextSize(10);String suffix=" · "+iso;c.drawText(android.text.TextUtils.ellipsize(r.stock+" · "+r.camera,p,547-p.measureText(suffix),android.text.TextUtils.TruncateAt.END)+suffix,24,51,p);
                for(int i=start;i<Math.min(start+12,shots.size());i++){LogStore.Shot s=shots.get(i);int cell=i-start;float x=24+(cell%3)*184,y=70+(cell/3)*182;RectF bounds=new RectF(x,y,x+174,y+132);p.setColor(0xffeeeeee);c.drawRect(bounds,p);ScanStore.Scan scan=scans.get(s.id);
                    if(scan!=null){Bitmap b=ScanImages.read(files.file(scan.id),720);try{float scale=Math.min(bounds.width()/b.getWidth(),bounds.height()/b.getHeight());float w=b.getWidth()*scale,h=b.getHeight()*scale;c.drawBitmap(b,null,new RectF(x+(174-w)/2,y+(132-h)/2,x+(174+w)/2,y+(132+h)/2),null);}finally{b.recycle();}}
                    else{p.setColor(Color.DKGRAY);p.setTextSize(11);c.drawText("未关联扫描",x+45,y+70,p);}
                    p.setColor(Color.BLACK);p.setTextSize(11);c.drawText(String.format(Locale.ROOT,"%02d",s.frame)+"  "+shorten(s.memo,16),x,y+148,p);p.setTextSize(8);c.drawText(time.format(Instant.ofEpochMilli(s.captured).atOffset(ZoneOffset.ofTotalSeconds(s.zone*60))),x,y+161,p);
                }
                p.setTextSize(9);c.drawText("胶片随记 · "+page+" / "+((shots.size()+11)/12)+" · 不含坐标",24,824,p);}finally{document.finishPage(sheet);}
            }document.writeTo(target);
        }finally{document.close();}return shots.size();
    }
    private static String shorten(String value,int size){return value.length()<=size?value:value.substring(0,size-1)+"…";}
}
