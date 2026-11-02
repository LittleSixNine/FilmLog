package cn.sixnine.filmlog;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.*;
import org.json.*;

final class PresetStore {
    static final String[] APERTURES={"1.4","1.8","2","2.8","4","5.6","8","11","16","22"};
    static final String[] SHUTTERS={"1/1000","1/500","1/250","1/125","1/60","1/30","1/15","1/8","1s","2s"};
    static final String[] LENSES={"24mm","28mm","35mm","50mm","85mm","135mm"};
    static final String[] ICONS={"camera","sun","mountains","moon","flower","buildings","filmStrip","lightning","heart","star"};
    static final class Item {
        String id=UUID.randomUUID().toString(),name="",description="",icon="camera",aperture="",shutter="",lens="";long created=System.currentTimeMillis();
        ExposurePresets preset(){return new ExposurePresets(id,name,aperture,shutter,lens,iconCode(icon),iconColor(icon));}
    }
    private final LogStore logs;
    PresetStore(LogStore logs){this.logs=logs;}
    static void create(SQLiteDatabase db){db.execSQL("CREATE TABLE IF NOT EXISTS exposure_presets(id TEXT PRIMARY KEY,name TEXT NOT NULL,description TEXT NOT NULL,icon_key TEXT NOT NULL,aperture TEXT NOT NULL,shutter TEXT NOT NULL,lens TEXT NOT NULL,created_at INTEGER NOT NULL)");}
    ArrayList<Item> list(){ArrayList<Item> result=new ArrayList<>();try(Cursor c=logs.getReadableDatabase().rawQuery("SELECT * FROM exposure_presets ORDER BY created_at,rowid",null)){while(c.moveToNext()){Item i=new Item();i.id=LogStore.str(c,"id");i.name=LogStore.str(c,"name");i.description=LogStore.str(c,"description");i.icon=LogStore.str(c,"icon_key");i.aperture=LogStore.str(c,"aperture");i.shutter=LogStore.str(c,"shutter");i.lens=LogStore.str(c,"lens");i.created=c.getLong(c.getColumnIndexOrThrow("created_at"));result.add(i);}}return result;}
    Item get(String id){for(Item i:list())if(i.id.equals(id))return i;throw new IllegalArgumentException("预设已删除，请重新选择");}
    static void validate(Item i){if(!UUID.fromString(i.id).toString().equals(i.id))throw new IllegalArgumentException("预设ID无效");if(i.name.trim().isEmpty()||i.name.length()>500||i.description.length()>4000)throw new IllegalArgumentException("请填写名称（最多500字），备注最多4000字");if(!Arrays.asList(ICONS).contains(i.icon))throw new IllegalArgumentException("预设图标无效");for(String[] pair:new String[][]{{i.aperture,"a"},{i.shutter,"s"},{i.lens,"l"}})if(!pair[0].isEmpty()&&!Arrays.asList(pair[1].equals("a")?APERTURES:pair[1].equals("s")?SHUTTERS:LENSES).contains(pair[0]))throw new IllegalArgumentException("请选择有效预设参数");}
    void save(Item item,boolean editing){
        validate(item);SQLiteDatabase db=logs.getWritableDatabase();db.beginTransaction();try{
            for(Item existing:list())if(!existing.id.equals(item.id)&&existing.name.toLowerCase(Locale.ROOT).equals(item.name.trim().toLowerCase(Locale.ROOT)))throw new IllegalArgumentException("模板名称已存在");
            if(editing){Item old=get(item.id);item.created=old.created;
                // Original copyWith treats null/empty editor values as retaining the prior optional value.
                if(item.aperture.isEmpty())item.aperture=old.aperture;if(item.shutter.isEmpty())item.shutter=old.shutter;if(item.lens.isEmpty())item.lens=old.lens;if(item.description.trim().isEmpty())item.description=old.description;
            }
            ContentValues v=new ContentValues();v.put("name",item.name.trim());v.put("description",item.description.trim());v.put("icon_key",item.icon);v.put("aperture",item.aperture);v.put("shutter",item.shutter);v.put("lens",item.lens);v.put("created_at",item.created);
            if(editing){if(db.update("exposure_presets",v,"id=?",new String[]{item.id})!=1)throw new IllegalArgumentException("预设不存在");}else{v.put("id",item.id);db.insertOrThrow("exposure_presets",null,v);}db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    void delete(String id){get(id);logs.getWritableDatabase().delete("exposure_presets","id=?",new String[]{id});}
    static Item fromJson(JSONObject row)throws JSONException{Item i=new Item();i.id=row.getString("id");i.name=row.getString("name");i.description=row.getString("description");i.icon=row.getString("icon_key");i.aperture=row.getString("aperture");i.shutter=row.getString("shutter");i.lens=row.getString("lens");i.created=row.getLong("created_at");return i;}
    static int iconCode(String key){return ICON_CODES[Arrays.asList(ICONS).indexOf(key)];}
    static int iconColor(String key){return ICON_COLORS[Arrays.asList(ICONS).indexOf(key)];}
    static final int[] ICON_CODES={0xe10e,0xe472,0xe7ae,0xe330,0xe75e,0xe102,0xe792,0xe2de,0xe2a8,0xe46a};
    static final int[] ICON_COLORS={0xff9e9e9e,0xff4caf50,0xff4caf50,0xff3f51b5,0xff8bc34a,0xff607d8b,0xffff5722,0xffff5722,0xffe91e63,0xffe91e63};
}
