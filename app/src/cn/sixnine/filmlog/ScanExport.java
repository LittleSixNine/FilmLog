package cn.sixnine.filmlog;

import android.content.Context;
import androidx.exifinterface.media.ExifInterface;
import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Returns a new private JPEG; never modifies or publishes the input. */
final class ScanExport {
    static File create(Context context,File source,LogStore.Roll roll,LogStore.Shot shot,boolean gps)throws IOException{
        ExifInterface input=new ExifInterface(source.getAbsolutePath());
        int orientation=input.getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL);
        if(orientation<1||orientation>8)orientation=ExifInterface.ORIENTATION_NORMAL;
        File output=File.createTempFile("scan-export-",".jpg",context.getCacheDir());boolean ok=false;
        try{
            try(InputStream in=new FileInputStream(source);OutputStream out=new FileOutputStream(output)){JpegCopy.clean(in,out);}
            ExifInterface exif=new ExifInterface(output.getAbsolutePath());
            exif.setAttribute(ExifInterface.TAG_ORIENTATION,Integer.toString(orientation));
            OffsetDateTime capture=Instant.ofEpochMilli(shot.captured).atOffset(ZoneOffset.ofTotalSeconds(shot.zone*60));
            String date=capture.format(DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss",Locale.ROOT));
            exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL,date);
            exif.setAttribute("OffsetTimeOriginal",capture.getOffset().getId().equals("Z")?"+00:00":capture.getOffset().getId());
            exif.setAttribute("SubSecTimeOriginal",String.format(Locale.ROOT,"%03d",Math.floorMod(shot.captured,1000)));
            ascii(exif,ExifInterface.TAG_MODEL,roll.camera);
            ascii(exif,"LensModel",shot.lens);
            String description=roll.stock+(shot.memo.isEmpty()?"":" | "+shot.memo);
            ascii(exif,ExifInterface.TAG_IMAGE_DESCRIPTION,description);
            exif.setAttribute(ExifInterface.TAG_SOFTWARE,"FilmLog independent reconstruction");
            if(roll.iso>0)exif.setAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS,Integer.toString(roll.iso));
            putNumber(exif,ExifInterface.TAG_F_NUMBER,shot.aperture);
            putNumber(exif,ExifInterface.TAG_EXPOSURE_TIME,shot.shutter);
            if(gps&&shot.lat!=null&&shot.lon!=null){
                if(!Double.isFinite(shot.lat)||!Double.isFinite(shot.lon)||Math.abs(shot.lat)>90||Math.abs(shot.lon)>180)throw new IOException("Invalid GPS coordinate");
                exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE,coordinate(shot.lat));exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF,shot.lat<0?"S":"N");
                exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE,coordinate(shot.lon));exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF,shot.lon<0?"W":"E");
                // GPS timestamp describes the actual fix, not the time-offset exposure.
                if(shot.locationTime>0){OffsetDateTime fix=Instant.ofEpochMilli(shot.locationTime).atOffset(ZoneOffset.UTC);exif.setAttribute(ExifInterface.TAG_GPS_DATESTAMP,fix.format(DateTimeFormatter.ofPattern("yyyy:MM:dd",Locale.ROOT)));exif.setAttribute(ExifInterface.TAG_GPS_TIMESTAMP,fix.format(DateTimeFormatter.ofPattern("HH:mm:ss",Locale.ROOT)));}
            }
            exif.saveAttributes();writeXmp(output,roll.camera,shot.lens,description);ok=true;return output;
        }finally{if(!ok&&!output.delete())output.deleteOnExit();}
    }
    // EXIF ASCII strings cannot represent Chinese; retain Unicode in standard XMP fields.
    private static void ascii(ExifInterface exif,String tag,String value){if(value!=null&&value.chars().allMatch(c->c>=32&&c<127))exif.setAttribute(tag,value);}
    private static String xml(String text){StringBuilder result=new StringBuilder();for(int i=0;i<text.length();){int c=text.codePointAt(i);i+=Character.charCount(c);if(c==9||c==10||c==13||(c>=32&&c<=0xd7ff)||(c>=0xe000&&c<=0xfffd)||(c>=0x10000&&c<=0x10ffff))result.appendCodePoint(c);}return result.toString().replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");}
    private static void writeXmp(File output,String camera,String lens,String description)throws IOException{
        String packet="http://ns.adobe.com/xap/1.0/\0"+"<x:xmpmeta xmlns:x=\"adobe:ns:meta/\"><rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\"><rdf:Description rdf:about=\"\" xmlns:tiff=\"http://ns.adobe.com/tiff/1.0/\" xmlns:aux=\"http://ns.adobe.com/exif/1.0/aux/\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\" tiff:Model=\""+xml(camera)+"\" aux:Lens=\""+xml(lens)+"\"><dc:description><rdf:Alt><rdf:li xml:lang=\"x-default\">"+xml(description)+"</rdf:li></rdf:Alt></dc:description></rdf:Description></rdf:RDF></x:xmpmeta>";
        byte[] bytes=packet.getBytes(java.nio.charset.StandardCharsets.UTF_8);if(bytes.length>65533)throw new IOException("照片文字信息过长，无法写入 JPEG");
        File staged=File.createTempFile("scan-xmp-",".jpg",output.getParentFile());try{
            try(DataInputStream in=new DataInputStream(new FileInputStream(output));DataOutputStream out=new DataOutputStream(new FileOutputStream(staged))){if(in.readUnsignedShort()!=0xffd8)throw new IOException("Invalid JPEG");out.writeShort(0xffd8);out.writeShort(0xffe1);out.writeShort(bytes.length+2);out.write(bytes);byte[] buffer=new byte[65536];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);}
            if(!staged.renameTo(output))throw new IOException("无法完成照片元数据写入");
        }finally{staged.delete();}
    }
    private static String coordinate(double value)throws IOException{if(!Double.isFinite(value)||Math.abs(value)>180)throw new IOException("Invalid coordinate");double x=Math.abs(value);int deg=(int)x;double min=(x-deg)*60;int m=(int)min;return deg+"/1,"+m+"/1,"+Math.round((min-m)*60000000)+"/1000000";}
    private static void putNumber(ExifInterface e,String tag,String value){
        if(value==null||value.trim().isEmpty())return;
        try{value=value.trim();if(value.endsWith("s"))value=value.substring(0,value.length()-1);String[] fraction=value.split("/");double n=fraction.length==2?Double.parseDouble(fraction[0])/Double.parseDouble(fraction[1]):Double.parseDouble(value);if(Double.isFinite(n)&&n>0)e.setAttribute(tag,Double.toString(n));}catch(NumberFormatException ignored){}
    }
}
