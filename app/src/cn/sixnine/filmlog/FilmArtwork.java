package cn.sixnine.filmlog;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Optional, read-only presentation overlay keyed by an existing stock ID. */
final class FilmArtwork {
    private static Map<String,JSONObject> entries;
    private static final LruCache<String,Bitmap> images=new LruCache<String,Bitmap>(8*1024*1024){protected int sizeOf(String key,Bitmap image){return image.getByteCount();}};
    static synchronized JSONObject get(Context context,String stockId){if(entries==null){Map<String,JSONObject> loaded=new HashMap<>();try(InputStream in=context.getAssets().open("film-artwork.json");ByteArrayOutputStream bytes=new ByteArrayOutputStream()){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1)bytes.write(buffer,0,n);JSONArray items=new JSONObject(new String(bytes.toByteArray(),StandardCharsets.UTF_8)).getJSONArray("items");for(int i=0;i<items.length();i++){JSONObject item=items.getJSONObject(i);String id=item.getString("stock_id");if(!(id.startsWith("original:")||id.matches("filmer:[a-zA-Z0-9_-]+"))||loaded.put(id,item)!=null)throw new IOException("Duplicate artwork identity");}}catch(IOException|JSONException invalid){loaded.clear();}entries=loaded;}return entries.get(stockId);}
    static JSONArray covers(Context context,String stockId,String format){
        JSONArray result=new JSONArray();JSONObject art=get(context,stockId);if(art==null)return result;JSONArray rows=art.optJSONArray("covers");if(rows==null)return result;
        JSONArray reference=new JSONArray();String wanted=canonicalFormat(format);for(int i=0;i<rows.length();i++){JSONObject row=rows.optJSONObject(i);if(row==null)continue;boolean matches=wanted.isEmpty();JSONArray formats=row.optJSONArray("formats");if(formats==null||formats.length()==0)reference.put(row);if(formats!=null)for(int j=0;j<formats.length();j++)if(wanted.equals(canonicalFormat(formats.optString(j))))matches=true;if(matches)result.put(row);}
        // Unknown packaging can illustrate the model; a known different format never substitutes.
        return result.length()>0?result:reference;
    }
    static JSONObject selected(Context context,String stock,String format,String image){JSONArray rows=covers(context,stock,format);for(int i=0;i<rows.length();i++)if(rows.optJSONObject(i).optString("image").equals(image))return rows.optJSONObject(i);return rows.optJSONObject(0);}
    static void validateCover(Context context,String stock,String format,String image){if(image.isEmpty())return;JSONObject row=selected(context,stock,format,image);if(row==null||!image.equals(row.optString("image")))throw new IllegalArgumentException("封面与胶片或规格不符");}
    private static String canonicalFormat(String value){String format=value==null?"":value.replace(" ","").toLowerCase(Locale.ROOT);return format.equals("35mm")?"135":format;}
    static String caption(JSONObject cover){if(cover==null)return "";JSONArray formats=cover.optJSONArray("formats");String labels=formats==null||formats.length()==0?"规格未确认":formats.toString().replace("[", "").replace("]", "").replace("\"", "");return labels+(cover.optString("variant").isEmpty()?"":" · "+cover.optString("variant"))+" · "+cover.optString("format_basis");}
    static Bitmap image(Context context,String path){if(!path.matches("film-artwork/[a-zA-Z0-9_-]+\\.(png|jpg|webp)"))return null;Bitmap cached=images.get(path);if(cached!=null)return cached;try{BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;try(InputStream in=context.getAssets().open(path)){BitmapFactory.decodeStream(in,null,options);}if(options.outWidth<1||options.outHeight<1)return null;options.inSampleSize=1;while(Math.max(options.outWidth,options.outHeight)/options.inSampleSize>1024)options.inSampleSize*=2;options.inJustDecodeBounds=false;try(InputStream in=context.getAssets().open(path)){Bitmap bitmap=BitmapFactory.decodeStream(in,null,options);if(bitmap!=null)images.put(path,bitmap);return bitmap;}}catch(IOException invalid){return null;}}
}
