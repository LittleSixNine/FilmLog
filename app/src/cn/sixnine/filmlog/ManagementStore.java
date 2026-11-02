package cn.sixnine.filmlog;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.*;
import java.time.LocalDate;
import android.os.Bundle;

final class ManagementStore {
    private static String pattern(String text){return "%"+text.trim().replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%";}
    private static String filters(String archive,String state,List<String> args){
        String sql="1=1";
        if(!archive.equals("all")){if(!archive.equals("current")&&!archive.equals("archived"))throw new IllegalArgumentException("无效归档筛选");sql+=" AND r.archived=?";args.add(archive.equals("archived")?"1":"0");}
        if(!state.equals("all")){if(!Arrays.asList("active","finished","developing","received").contains(state))throw new IllegalArgumentException("无效状态筛选");sql+=" AND r.state=?";args.add(state);}return sql;
    }
    private static String rollText(){return "r.name LIKE ? ESCAPE '\\' OR r.stock LIKE ? ESCAPE '\\' OR r.camera LIKE ? ESCAPE '\\' OR r.lab LIKE ? ESCAPE '\\' OR r.lab_number LIKE ? ESCAPE '\\'";}
    private static String shotText(){return "s.memo LIKE ? ESCAPE '\\' OR s.lens LIKE ? ESCAPE '\\' OR s.filters LIKE ? ESCAPE '\\'";}
    static ArrayList<String> search(LogStore logs,String text,String archive,String state,boolean shots,int limit){
        if(limit<1||limit>10000)throw new IllegalArgumentException("结果数量无效");ArrayList<String> args=new ArrayList<>();String where=filters(archive,state,args),term=pattern(text);
        if(!text.trim().isEmpty()){where+=" AND ("+rollText()+" OR "+(shots?shotText():"EXISTS (SELECT 1 FROM shots s WHERE s.roll_id=r.id AND ("+shotText()+"))")+")";for(int i=0;i<8;i++)args.add(term);}
        String sql=shots?"SELECT s.id FROM shots s JOIN rolls r ON r.id=s.roll_id WHERE "+where+" ORDER BY s.captured_at DESC,s.id":"SELECT r.id FROM rolls r WHERE "+where+" ORDER BY r.created_at DESC,r.id";
        ArrayList<String> result=new ArrayList<>();try(Cursor c=logs.getReadableDatabase().rawQuery(sql+" LIMIT "+limit,args.toArray(new String[0]))){while(c.moveToNext())result.add(c.getString(0));}return result;
    }
    static final class Criteria {
        String camera="",stock="",from="",to="";
        Criteria(){}
        Criteria(Bundle b){if(b!=null){camera=b.getString("camera","");stock=b.getString("stock","");from=b.getString("from","");to=b.getString("to","");}}
        Bundle save(){Bundle b=new Bundle();b.putString("camera",camera);b.putString("stock",stock);b.putString("from",from);b.putString("to",to);return b;}
        int count(){return (camera.isEmpty()?0:1)+(stock.isEmpty()?0:1)+(from.isEmpty()&&to.isEmpty()?0:1);}
        void validate(){try{for(String day:new String[]{from,to})if(!day.isEmpty()&&(!LocalDate.parse(day).toString().equals(day)||day.length()!=10))throw new IllegalArgumentException();}catch(Exception e){throw new IllegalArgumentException("请选择有效日期");}if(!from.isEmpty()&&!to.isEmpty()&&from.compareTo(to)>0)throw new IllegalArgumentException("结束日期不能早于起始日期");}
    }
    static ArrayList<String> search(LogStore logs,String text,Criteria filter,boolean shots){
        filter.validate();ArrayList<String> args=new ArrayList<>();String where="1=1",term=pattern(text);
        if(!text.trim().isEmpty()){where+=" AND ("+rollText()+" OR "+(shots?shotText():"EXISTS (SELECT 1 FROM shots s WHERE s.roll_id=r.id AND ("+shotText()+"))")+")";for(int i=0;i<8;i++)args.add(term);}
        if(!filter.camera.isEmpty()){where+=" AND r.camera=?";args.add(filter.camera);}if(!filter.stock.isEmpty()){where+=" AND r.stock=?";args.add(filter.stock);}
        // Exposure dates use the recorded time zone, not today's device time zone.
        String date=shots?"strftime('%Y-%m-%d',s.captured_at/1000.0+s.zone_offset_minutes*60,'unixepoch')":"strftime('%Y-%m-%d',r.created_at/1000.0,'unixepoch','localtime')";
        if(!filter.from.isEmpty()){where+=" AND "+date+">=?";args.add(filter.from);}if(!filter.to.isEmpty()){where+=" AND "+date+"<=?";args.add(filter.to);}
        String sql=shots?"SELECT s.id FROM shots s JOIN rolls r ON r.id=s.roll_id WHERE ":"SELECT r.id FROM rolls r WHERE ";sql+=where+(shots?" ORDER BY s.captured_at DESC,s.id":" ORDER BY r.created_at DESC,r.id");
        ArrayList<String> result=new ArrayList<>();try(Cursor c=logs.getReadableDatabase().rawQuery(sql,args.toArray(new String[0]))){while(c.moveToNext())result.add(c.getString(0));}return result;
    }
    static String version(LogStore.Roll r){return r.state+":"+r.archived;}
    static boolean selectable(LogStore logs,LogStore.Roll r,boolean target){if(r.state.equals("received")||r.archived==target)return false;CatalogStore.Detail d=new CatalogStore(logs).detail(r.id);return d==null||!d.returned;}
    static void archiveSelected(LogStore logs,Map<String,String> expected,boolean target){
        if(expected.isEmpty())throw new IllegalArgumentException("请先选择胶卷");SQLiteDatabase db=logs.getWritableDatabase();db.beginTransaction();try{
            for(Map.Entry<String,String> e:expected.entrySet()){LogStore.Roll r=logs.roll(e.getKey());if(!version(r).equals(e.getValue())||!selectable(logs,r,target))throw new IllegalArgumentException("所选胶卷状态已变化，请重新选择后重试");logs.archive(r.id,target);}db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    static void archive(LogStore logs,Map<String,Boolean> expected,boolean target){
        if(expected.isEmpty())throw new IllegalArgumentException("请先选择胶卷");SQLiteDatabase db=logs.getWritableDatabase();db.beginTransaction();try{
            for(Map.Entry<String,Boolean> entry:expected.entrySet()){
                if(logs.roll(entry.getKey()).archived!=entry.getValue())throw new IllegalArgumentException("所选胶卷状态已变化，请刷新后重试");
                logs.archive(entry.getKey(),target);
            }db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
}
