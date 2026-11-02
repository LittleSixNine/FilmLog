package cn.sixnine.filmlog;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.widget.*;
import org.json.*;
import java.util.*;

/** Explicit display associations; historical stock names, ISO and quantities remain snapshots. */
final class ArtworkLinks {
    static void create(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS artwork_links(id TEXT PRIMARY KEY,owner_kind TEXT NOT NULL CHECK(owner_kind IN ('batch','roll')),owner_id TEXT NOT NULL,stock_id TEXT NOT NULL,UNIQUE(owner_kind,owner_id))");
    }
    static String get(LogStore logs,String kind,String owner) {
        try(Cursor c=logs.getReadableDatabase().rawQuery("SELECT stock_id FROM artwork_links WHERE owner_kind=? AND owner_id=?",new String[]{kind,owner})) {return c.moveToFirst()?c.getString(0):"";}
    }
    static String coverImage(LogStore logs,String kind,String owner){try(Cursor c=logs.getReadableDatabase().rawQuery("SELECT cover_image FROM artwork_links WHERE owner_kind=? AND owner_id=?",new String[]{kind,owner})){return c.moveToFirst()?c.getString(0):"";}}
    static JSONObject cover(Context context,LogStore logs,String kind,String owner,String format){return FilmArtwork.selected(context,get(logs,kind,owner),format,coverImage(logs,kind,owner));}
    static void setCover(Context context,LogStore logs,String kind,String owner,String format,String image){FilmArtwork.validateCover(context,get(logs,kind,owner),format,image);ContentValues value=new ContentValues();value.put("cover_image",image);int changed=logs.getWritableDatabase().update("artwork_links",value,"owner_kind=? AND owner_id=?",new String[]{kind,owner});if(changed!=1&&!image.isEmpty())throw new IllegalArgumentException("请先选择胶片");}
    static FilmStockStore.Stock original(Context context,String id) {
        return FilmStockStore.builtin(context,id);
    }
    static void set(Context context,LogStore logs,String kind,String owner,String stockId) {
        String format;
        if(kind.equals("batch"))format=new CatalogStore(logs).batch(owner).format;
        else if(kind.equals("roll")){logs.roll(owner);CatalogStore.Detail detail=new CatalogStore(logs).detail(owner);format=detail==null?"":detail.format;}
        else throw new IllegalArgumentException("无效的素材关联对象");
        if(!stockId.isEmpty()&&!FilmStockStore.matches(original(context,stockId),format))throw new IllegalArgumentException("片种规格与记录不符");
        put(logs.getWritableDatabase(),kind,owner,stockId);
    }
    static void put(SQLiteDatabase db,String kind,String owner,String stockId) {
        db.beginTransaction();try{
            if(stockId.isEmpty())db.delete("artwork_links","owner_kind=? AND owner_id=?",new String[]{kind,owner});
            else{ContentValues value=new ContentValues();value.put("stock_id",stockId);try(Cursor old=db.rawQuery("SELECT stock_id FROM artwork_links WHERE owner_kind=? AND owner_id=?",new String[]{kind,owner})){if(old.moveToFirst()&&!stockId.equals(old.getString(0)))value.put("cover_image","");}if(db.update("artwork_links",value,"owner_kind=? AND owner_id=?",new String[]{kind,owner})==0){value.put("id",UUID.randomUUID().toString());value.put("owner_kind",kind);value.put("owner_id",owner);db.insertOrThrow("artwork_links",null,value);}}
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    static String selected(Context context,String id,String name,int iso,String format) {
        if(id==null||!(id.startsWith("original:")||id.startsWith("filmer:")))return "";
        FilmStockStore.Stock stock=original(context,id);
        return iso>=1&&iso<=102400&&stock.title().equals(name.trim())&&(stock.iso==iso||stock.iso==0&&iso>=1&&iso<=102400)&&FilmStockStore.matches(stock,format)?id:"";
    }
    static boolean thumbnail(Context context,LogStore logs,String kind,String owner,LinearLayout row,int size) {
        JSONObject art=FilmArtwork.get(context,get(logs,kind,owner));if(art==null)return false;
        String format="";if(kind.equals("batch"))format=new CatalogStore(logs).batch(owner).format;else{CatalogStore.Detail detail=new CatalogStore(logs).detail(owner);if(detail!=null)format=detail.format;}
        JSONObject cover=cover(context,logs,kind,owner,format);if(cover==null)return false;
        Bitmap bitmap=FilmArtwork.image(context,cover.optString("thumbnail"));if(bitmap==null)return false;
        ImageView image=new ImageView(context);image.setImageBitmap(bitmap);image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        int pixels=Math.round(size*context.getResources().getDisplayMetrics().density);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(pixels,pixels);p.rightMargin=Math.round(8*context.getResources().getDisplayMetrics().density);row.addView(image,p);return true;
    }
}
