package cn.sixnine.filmlog;

import android.graphics.*;
import androidx.exifinterface.media.ExifInterface;
import java.io.*;

final class ScanImages {
    static Bitmap read(File file,int maximum)throws IOException{return read(file,maximum,false);}
    static Bitmap read(File file,int maximum,boolean srgb)throws IOException{
        BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.toString(),options);
        if(options.outWidth<=0||options.outHeight<=0)throw new IOException("扫描图片无法读取");
        options.inSampleSize=1;while(options.outWidth/options.inSampleSize>maximum||options.outHeight/options.inSampleSize>maximum)options.inSampleSize*=2;
        options.inJustDecodeBounds=false;if(srgb){options.inPreferredColorSpace=ColorSpace.get(ColorSpace.Named.SRGB);options.inMutable=true;}Bitmap original=BitmapFactory.decodeFile(file.toString(),options);if(original==null)throw new IOException("扫描图片解码失败");
        return orient(original,new ExifInterface(file.toString()).getAttributeInt("Orientation",1));
    }
    static Bitmap read(android.content.Context context,android.net.Uri uri,int maximum)throws IOException{
        try(android.os.ParcelFileDescriptor descriptor=context.getContentResolver().openFileDescriptor(uri,"r")){
            if(descriptor==null)throw new IOException("扫描图片无法读取");
            BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeFileDescriptor(descriptor.getFileDescriptor(),null,options);
            if(options.outWidth<=0||options.outHeight<=0)throw new IOException("扫描图片无法读取");
            options.inSampleSize=1;while(options.outWidth/options.inSampleSize>maximum||options.outHeight/options.inSampleSize>maximum)options.inSampleSize*=2;
            options.inJustDecodeBounds=false;Bitmap original=BitmapFactory.decodeFileDescriptor(descriptor.getFileDescriptor(),null,options);
            if(original==null)throw new IOException("扫描图片解码失败");
            return orient(original,new ExifInterface(descriptor.getFileDescriptor()).getAttributeInt("Orientation",1));
        }
    }
    private static Bitmap orient(Bitmap original,int orientation){Matrix m=new Matrix();
        switch(orientation){case 2:m.setScale(-1,1);break;case 3:m.setRotate(180);break;case 4:m.setScale(1,-1);break;case 5:m.setRotate(90);m.postScale(-1,1);break;case 6:m.setRotate(90);break;case 7:m.setRotate(90);m.postScale(1,-1);break;case 8:m.setRotate(270);break;}
        if(m.isIdentity())return original;Bitmap rotated=Bitmap.createBitmap(original,0,0,original.getWidth(),original.getHeight(),m,true);if(rotated!=original)original.recycle();return rotated;
    }
}
