package cn.sixnine.filmlog;

import java.io.*;
import java.nio.charset.StandardCharsets;

/** Copies JPEG image data without decoding, dropping metadata before writing our own EXIF. */
final class JpegCopy {
    private static int byteOf(InputStream in)throws IOException{int x=in.read();if(x<0)throw new EOFException("Incomplete JPEG");return x;}
    private static boolean prefix(byte[] b,String p){byte[] a=p.getBytes(StandardCharsets.US_ASCII);if(b.length<a.length)return false;for(int n=0;n<a.length;n++)if(a[n]!=b[n])return false;return true;}
    static void clean(InputStream source,OutputStream destination)throws IOException{
        BufferedInputStream in=new BufferedInputStream(source);BufferedOutputStream out=new BufferedOutputStream(destination);
        if(byteOf(in)!=255||byteOf(in)!=216)throw new IOException("Only JPEG scans are supported by this export probe");
        out.write(255);out.write(216);int pending=-1;boolean sawScan=false;
        for(;;){
            int marker;
            if(pending>=0){marker=pending;pending=-1;}else{if(byteOf(in)!=255)throw new IOException("Invalid JPEG marker");do{marker=byteOf(in);}while(marker==255);}
            if(marker==217){if(!sawScan)throw new IOException("JPEG has no image scan");out.write(255);out.write(marker);out.flush();return;}
            if(marker==0||marker==216||marker==1||(marker>=208&&marker<=215))throw new IOException("Unexpected JPEG marker");
            int length=(byteOf(in)<<8)|byteOf(in);if(length<2)throw new IOException("Invalid JPEG segment length");
            byte[] payload=new byte[length-2];new DataInputStream(in).readFully(payload);
            boolean app=marker>=224&&marker<=239;
            boolean keep=!app&&marker!=254;
            // Only color/rendering metadata survives; EXIF, XMP, IPTC, comments and unknown APP data do not.
            if(marker==224)keep=prefix(payload,"JFIF\0");
            if(marker==226)keep=prefix(payload,"ICC_PROFILE\0");
            if(marker==238)keep=prefix(payload,"Adobe");
            if(keep){out.write(255);out.write(marker);out.write(length>>8);out.write(length);out.write(payload);}
            if(marker==218){
                sawScan=true;
                for(;;){int v=byteOf(in);if(v!=255){out.write(v);continue;}
                    int m=byteOf(in);while(m==255)m=byteOf(in);
                    if(m==0||(m>=208&&m<=215)){out.write(255);out.write(m);}else{pending=m;break;}
                }
            }
        }
    }
}
