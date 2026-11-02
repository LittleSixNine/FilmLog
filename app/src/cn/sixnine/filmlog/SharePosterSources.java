package cn.sixnine.filmlog;

import android.content.Context;
import android.database.Cursor;
import java.io.*;
import java.time.*;
import java.util.*;

/** Read-only session sources. Unlinked scans stay usable without inventing exposures. */
final class SharePosterSources {
    static final class Result {
        final ArrayList<SharePosterData.Item> items=new ArrayList<>();
        String title="",details="",date="",summary="";
        boolean vertical;
    }
    static Result loadRoll(Context context,String roll)throws IOException {
        Result result=new Result();
        try(LogStore logs=new LogStore(context)){
            LogStore.Roll record=logs.roll(roll);
            result.title=record.name.isEmpty()?record.stock:record.name;
            result.details=record.stock+(record.camera.isEmpty()?"":" · "+record.camera);
            CatalogStore.Detail detail=new CatalogStore(logs).detail(roll);
            result.vertical=detail!=null&&detail.halfFrame;
            HashMap<String,LogStore.Shot> shots=new HashMap<>();
            for(LogStore.Shot shot:logs.shots(roll))shots.put(shot.id,shot);
            ScanStore scans=new ScanStore(context,logs);
            HashSet<String> scannedShots=new HashSet<>(),ids=new HashSet<>();
            int available=0,unlinked=0,unreadable=0,references=0;
            for(ScanStore.Scan scan:scans.list(roll)){
                LogStore.Shot shot=shots.get(scan.shotId);File file=scans.file(scan.id);
                SharePosterData.Item item=new SharePosterData.Item(scan.id,scan.name,file,null,shot==null?0:shot.frame,shot==null?0:shot.captured,shot==null?0:shot.zone,false);
                item.selected=file.isFile()&&file.length()>0;
                if(item.selected){available++;if(shot==null)unlinked++;else scannedShots.add(shot.id);}else unreadable++;
                result.items.add(item);ids.add(item.id);
            }
            result.items.sort((a,b)->Integer.compare(a.frame>0?a.frame:Integer.MAX_VALUE,b.frame>0?b.frame:Integer.MAX_VALUE));
            try(Cursor rows=logs.getReadableDatabase().rawQuery("SELECT p.id,p.shot_id FROM reference_photos p JOIN shots s ON s.id=p.shot_id WHERE s.roll_id=? ORDER BY s.frame_number",new String[]{roll})){
                while(rows.moveToNext()){
                    String id=rows.getString(0);LogStore.Shot shot=shots.get(rows.getString(1));
                    if(shot==null||!ids.add(id))continue;
                    File file=scans.file(id);
                    SharePosterData.Item item=new SharePosterData.Item(id,"第 "+shot.frame+" 张参考图",file,null,shot.frame,shot.captured,shot.zone,true);
                    item.selected=false;result.items.add(item);references++;
                }
            }
            result.date=date(result.items);
            result.summary=available+" 张可用扫描 · "+Math.max(0,shots.size()-scannedShots.size())+" 条曝光缺少扫描 · "+unlinked+" 张未关联扫描";
            if(references>0)result.summary+="\n另有 "+references+" 张参考图，需手动选择";
            if(unreadable>0)result.summary+="\n"+unreadable+" 张扫描文件缺失，已取消选择，请重新导入";
        }
        return result;
    }
    static String date(List<SharePosterData.Item> items){
        LocalDate first=null,last=null;
        for(SharePosterData.Item item:items)if(item.selected){
            if(item.frame<=0)return "";
            LocalDate day;
            try{day=Instant.ofEpochMilli(item.captured).atOffset(ZoneOffset.ofTotalSeconds(Math.multiplyExact(item.zone,60))).toLocalDate();}
            catch(DateTimeException|ArithmeticException invalid){return "";}
            if(first==null||day.isBefore(first))first=day;
            if(last==null||day.isAfter(last))last=day;
        }
        return first==null?"":first.equals(last)?first.toString():first+" — "+last;
    }
    private SharePosterSources(){}
}
