package cn.sixnine.filmlog;

import android.net.Uri;
import java.io.File;
import java.util.*;

/** Session-only edits; original photos and exposure records remain read-only. */
final class SharePosterData {
    static final class Item {
        final String id,name;
        final File file;
        final Uri uri;
        final int frame;
        final long captured;
        final int zone;
        final boolean reference;
        boolean selected=true,fill;
        float cropX=.5f,cropY=.5f;
        Item(String id,String name,File file,Uri uri,int frame,long captured,int zone,boolean reference){
            this.id=id;this.name=name;this.file=file;this.uri=uri;this.frame=frame;this.captured=captured;this.zone=zone;this.reference=reference;
        }
        Item copy(){Item x=new Item(id,name,file,uri,frame,captured,zone,reference);x.selected=selected;x.fill=fill;x.cropX=cropX;x.cropY=cropY;return x;}
    }
    static final class Spec {
        final List<Item> items;
        final boolean overview,vertical,numbers;
        final String title,signature,details,date;
        final int width;
        Spec(List<Item> source,boolean overview,boolean vertical,boolean numbers,String title,String signature,String details,String date,int width){
            ArrayList<Item> picked=new ArrayList<>();HashSet<String> ids=new HashSet<>();
            for(Item i:source)if(i.selected){if(!ids.add(i.id))throw new IllegalArgumentException("同一照片不能重复选择");picked.add(i.copy());}
            this.items=Collections.unmodifiableList(picked);this.overview=overview;this.vertical=vertical;this.numbers=numbers;
            this.title=clean(title);this.signature=clean(signature);this.details=clean(details);this.date=clean(date);this.width=width;
        }
        private static String clean(String s){return s==null?"":s.trim();}
    }
    private SharePosterData(){}
}
