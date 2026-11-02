package cn.sixnine.filmlog;

import android.content.Context;
import android.graphics.*;
import android.util.LruCache;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Read-only detail fields keyed by stable catalogue IDs; unknown production is not false. */
final class FilmDetails {
    private static JSONObject rows;
    private static final LruCache<String,Bitmap> images=new LruCache<String,Bitmap>(4*1024*1024){protected int sizeOf(String k,Bitmap b){return b.getByteCount();}};
    static synchronized JSONObject get(Context c,String id){if(rows==null){try(InputStream in=c.getAssets().open("film-details.json");ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);rows=new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8)).getJSONObject("items");}catch(IOException|JSONException e){rows=new JSONObject();}}return rows.optJSONObject(id);}
    static String type(Context c,String id){JSONObject row=get(c,id);return row==null?"未填写":row.optString("type","未知");}
    static String production(Context c,String id){JSONObject row=get(c,id);return row==null||row.isNull("in_production")?"未知":row.optBoolean("in_production")?"在产":"已停产";}
    static Bitmap logo(Context c,String id){JSONObject row=get(c,id);return image(c,row==null?"":row.optString("logo"));}
    static Bitmap themedLogo(Context c,String id){JSONObject row=get(c,id);boolean night=(c.getResources().getConfiguration().uiMode&android.content.res.Configuration.UI_MODE_NIGHT_MASK)==android.content.res.Configuration.UI_MODE_NIGHT_YES;return image(c,row==null?"":row.optString(night?"logo_dark":"logo_light"));}
    private static Bitmap image(Context c,String path){if(!path.matches("film-brands/[0-9a-f]{20}\\.png"))return null;Bitmap ready=images.get(path);if(ready!=null)return ready;try(InputStream in=c.getAssets().open(path)){BitmapFactory.Options opt=new BitmapFactory.Options();opt.inJustDecodeBounds=true;BitmapFactory.decodeStream(in,null,opt);opt.inSampleSize=1;while(opt.outWidth/opt.inSampleSize>1024||opt.outHeight/opt.inSampleSize>1024)opt.inSampleSize*=2;opt.inJustDecodeBounds=false;try(InputStream input=c.getAssets().open(path)){ready=BitmapFactory.decodeStream(input,null,opt);if(ready!=null)images.put(path,ready);return ready;}}catch(IOException e){return null;}}
    static String model(FilmStockStore.Stock s){String name=s.name.trim(),brand=s.brand.trim();if(!brand.isEmpty()&&name.length()>brand.length()&&name.regionMatches(true,0,brand,0,brand.length())&&Character.isWhitespace(name.charAt(brand.length())))return name.substring(brand.length()).trim();return name;}
}
