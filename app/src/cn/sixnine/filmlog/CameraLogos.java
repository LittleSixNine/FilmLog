package cn.sixnine.filmlog;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.util.LruCache;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Reviewed, offline presentation assets. A similar model is never an exact match. */
final class CameraLogos {
    static final class Logo {String id,brand,label,kind,model,image;boolean monochrome;}
    private static final java.util.regex.Pattern NON_KEY=java.util.regex.Pattern.compile("[^a-z0-9]");
    private static Map<String,Logo> assets;
    private static Map<String,String> brandKeys;
    private static final LruCache<String,Bitmap> images=new LruCache<String,Bitmap>(4*1024*1024){protected int sizeOf(String key,Bitmap image){return image.getByteCount();}};
    static synchronized Collection<Logo> all(Context context){
        if(assets==null){Map<String,Logo> loaded=new LinkedHashMap<>();try(InputStream in=context.getAssets().open("camera-logos.json");ByteArrayOutputStream bytes=new ByteArrayOutputStream()){
            byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1)bytes.write(buffer,0,n);JSONArray rows=new JSONObject(new String(bytes.toByteArray(),StandardCharsets.UTF_8)).getJSONArray("items");
            for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);Logo l=new Logo();l.id=row.getString("id");l.brand=row.getString("brand");l.label=row.getString("label");l.kind=row.getString("kind");l.model=row.getString("model");l.image=row.getString("image");l.monochrome=row.getBoolean("monochrome");if(!l.image.matches("camera-logos/[a-f0-9]{20}\\.png")||loaded.put(l.id,l)!=null)throw new IOException("相机图形索引无效");}
        }catch(IOException|JSONException error){throw new IllegalStateException("无法读取相机Logo",error);}assets=loaded;brandKeys=new LinkedHashMap<>();ArrayList<String> brands=new ArrayList<>();for(Logo l:loaded.values())if(!brands.contains(l.brand))brands.add(l.brand);brands.sort((a,b)->Integer.compare(key(b).length(),key(a).length()));for(String b:brands)brandKeys.put(key(b),b);}
        return Collections.unmodifiableCollection(assets.values());
    }
    static Logo find(Context context,String id){all(context);return assets.get(id==null?"":id);}
    static void validate(Context context,String id){if(id==null||!id.isEmpty()&&find(context,id)==null)throw new IllegalArgumentException("请选择有效的相机Logo");}
    static String key(String value){return value==null?"":NON_KEY.matcher(value.toLowerCase(Locale.ROOT)).replaceAll("");}
    static boolean exact(Logo logo,String model){return logo!=null&&!logo.model.isEmpty()&&key(logo.model).equals(key(model));}
    static String brand(Context context,String model){all(context);String name=key(model);for(Map.Entry<String,String> b:brandKeys.entrySet())if(!name.isEmpty()&&name.startsWith(b.getKey()))return b.getValue();
        if(name.startsWith("fuji"))return "Fujifilm";if(name.startsWith("nikomat")||name.startsWith("nikkormat"))return "Nikon";if(name.startsWith("rolleiflex"))return "Rollei";return "";}
    static String displayModel(Context context,String model,boolean showBrand){
        if(showBrand)return model;String brand=brand(context,model);if(!brand.isEmpty()&&model.length()>brand.length()&&model.regionMatches(true,0,brand,0,brand.length())&&Character.isWhitespace(model.charAt(brand.length()))){String shortName=model.substring(brand.length()).trim();if(!shortName.isEmpty())return shortName;}return model;
    }
    static String aliases(String brand){switch(brand){case "Nikon":return "尼康";case "Olympus":return "奥林巴斯 奥巴";case "Canon":return "佳能";case "Ricoh":return "理光";case "Leica":return "徕卡";case "Fujifilm":return "富士";case "Pentax":return "宾得";case "Minolta":return "美能达";case "Rollei":return "禄来";default:return "";}}
    static ArrayList<Logo> recommended(Context context,String model){ArrayList<Logo> result=new ArrayList<>();for(Logo l:all(context))if(exact(l,model))result.add(l);String brand=brand(context,model);for(Logo l:all(context))if(l.brand.equals(brand)&&(l.kind.equals("品牌标志")||l.kind.equals("历史品牌标志")))result.add(l);return result;}
    static String suggest(Context context,String model){for(Logo l:all(context))if(exact(l,model))return l.id;String brand=brand(context,model);Logo primary=find(context,"initial-"+brand);if(primary!=null)return primary.id;for(Logo l:recommended(context,model))return l.id;return "";}
    static Bitmap image(Context context,Logo logo){if(logo==null)return null;Bitmap cached=images.get(logo.id);if(cached!=null)return cached;try(InputStream in=context.getAssets().open(logo.image)){Bitmap result=BitmapFactory.decodeStream(in);if(result!=null)images.put(logo.id,result);return result;}catch(IOException missing){return null;}}
    static ImageView view(Context context,String id){Logo logo=find(context,id);ImageView image=new ImageView(context);image.setImageBitmap(image(context,logo));image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setContentDescription(logo==null?"通用相机图标":logo.label);if(logo!=null&&logo.monochrome)image.setImageTintList(ColorStateList.valueOf(context.getColor(R.color.ui_ink)));return image;}
    static boolean thumbnail(Context context,String id,LinearLayout parent,int width,int height){Logo logo=find(context,id);if(logo==null)return false;ImageView image=view(context,id);image.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO);float density=context.getResources().getDisplayMetrics().density;LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(Math.round(width*density),Math.round(height*density));size.rightMargin=Math.round(8*density);parent.addView(image,size);return true;}
}
