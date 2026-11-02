package cn.sixnine.filmlog;

import android.database.Cursor;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

final class StatsStore {
    static final class Budget {BigDecimal recorded=BigDecimal.ZERO,complete=BigDecimal.ZERO,film=BigDecimal.ZERO,lab=BigDecimal.ZERO;int rolls,completeRolls,frames;}
    static final class Summary {
        int nightFrames;
        long perRoll(){return rolls==0?0:Math.round((double)frames/rolls);}
        int year,rolls,frames,located,scanned,days,longestStreak,unknownCurrencyRolls,missingCostRolls;
        final int[] week=new int[7];int weekFrames;final int[] months=new int[12];String firstDate="",lastDate="";
        final Map<String,Integer> stocks=new TreeMap<>(),cameras=new TreeMap<>(),lenses=new TreeMap<>();
        final Map<String,Integer> stockRolls=new TreeMap<>(),cameraRolls=new TreeMap<>();
        final Map<String,Budget> budgets=new TreeMap<>();final TreeSet<Integer> years=new TreeSet<>(Collections.reverseOrder());
    }
    static int weekIndex(long captured,long now){if(captured>=now)return 6;long days;try{days=Math.subtractExact(now,captured)/86400000L;}catch(ArithmeticException overflow){return -1;}return days>=49?-1:6-(int)(days/7);}
    private static final java.util.regex.Pattern FOCAL=java.util.regex.Pattern.compile("\\b(\\d+(?:\\.\\d+)?)\\s*mm\\b");
    static Set<String> focalLengths(String memo){Set<String> result=new LinkedHashSet<>();java.util.regex.Matcher found=FOCAL.matcher(memo);while(found.find())result.add(found.group(1)+"mm");return result;}
    static Summary read(LogStore logs,int year,boolean archived){return read(logs,year,archived,System.currentTimeMillis());}
    static Summary read(LogStore logs,int year,boolean archived,LocalDate today){return read(logs,year,archived,today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli());}
    static Summary read(LogStore logs,int year,boolean archived,long now){
        Summary value=new Summary();value.year=year;Map<String,LogStore.Roll> rolls=new HashMap<>();
        Set<String> returned=new HashSet<>();try(Cursor c=logs.getReadableDatabase().rawQuery("SELECT roll_id FROM roll_details WHERE returned=1",null)){while(c.moveToNext())returned.add(c.getString(0));}
        for(LogStore.Roll r:logs.rolls())if(!returned.contains(r.id)&&(archived||!r.archived&&!r.state.equals("received")))rolls.put(r.id,r);
        Set<String> includedRolls=new HashSet<>(),includedShots=new HashSet<>();TreeSet<LocalDate> dates=new TreeSet<>();
        try(Cursor c=logs.getReadableDatabase().rawQuery("SELECT id,roll_id,captured_at,zone_offset_minutes,latitude,longitude,lens,memo FROM shots",null)){
            while(c.moveToNext()){String rollId=c.getString(1);LogStore.Roll roll=rolls.get(rollId);if(roll==null)continue;
                LocalDate date=Instant.ofEpochMilli(c.getLong(2)).atOffset(ZoneOffset.ofTotalSeconds(c.getInt(3)*60)).toLocalDate();value.years.add(date.getYear());if(year!=0&&date.getYear()!=year)continue;
                int bucket=weekIndex(c.getLong(2),now);if(bucket>=0){value.week[bucket]++;if(bucket==6)value.weekFrames++;}for(String lens:focalLengths((c.isNull(6)?"":c.getString(6))+" • "+(c.isNull(7)?"":c.getString(7))))value.lenses.put(lens,value.lenses.getOrDefault(lens,0)+1);
                value.frames++;int hour=Instant.ofEpochMilli(c.getLong(2)).atOffset(ZoneOffset.ofTotalSeconds(c.getInt(3)*60)).getHour();if(hour>=22||hour<5)value.nightFrames++;if(!c.isNull(4)&&!c.isNull(5))value.located++;includedShots.add(c.getString(0));includedRolls.add(rollId);dates.add(date);value.months[date.getMonthValue()-1]++;
                String stock=roll.stock.trim();value.stocks.put(stock,value.stocks.getOrDefault(stock,0)+1);String camera=roll.camera.trim().isEmpty()?"未设置相机":roll.camera.trim();value.cameras.put(camera,value.cameras.getOrDefault(camera,0)+1);
            }
        }
        for(LogStore.Roll roll:rolls.values())if(year==0||includedRolls.contains(roll.id)){String stock=roll.stock.trim(),camera=roll.camera.trim();if(!stock.isEmpty())value.stockRolls.put(stock,value.stockRolls.getOrDefault(stock,0)+1);if(!camera.isEmpty())value.cameraRolls.put(camera,value.cameraRolls.getOrDefault(camera,0)+1);}
        value.rolls=year==0?rolls.size():includedRolls.size();value.days=dates.size();if(!dates.isEmpty()){value.firstDate=dates.first().toString();value.lastDate=dates.last().toString();}
        LocalDate previous=null;int streak=0;for(LocalDate date:dates){streak=previous!=null&&previous.plusDays(1).equals(date)?streak+1:1;value.longestStreak=Math.max(value.longestStreak,streak);previous=date;}
        try(Cursor c=logs.getReadableDatabase().rawQuery("SELECT shot_id FROM scans WHERE deleted_at IS NULL AND shot_id IS NOT NULL",null)){while(c.moveToNext())if(includedShots.contains(c.getString(0)))value.scanned++;}
        // Costs have no payment date in legacy records, so annual costs are intentionally not inferred.
        if(year==0)for(LogStore.Roll roll:rolls.values()){
            boolean film=!roll.filmCost.isEmpty(),lab=!roll.labCost.isEmpty();if(!film||!lab)value.missingCostRolls++;
            if(!film&&!lab)continue;if(roll.currency.isEmpty()){value.unknownCurrencyRolls++;continue;}
            Budget budget=value.budgets.get(roll.currency);if(budget==null){budget=new Budget();value.budgets.put(roll.currency,budget);}budget.rolls++;
            BigDecimal total=(film?new BigDecimal(roll.filmCost):BigDecimal.ZERO).add(lab?new BigDecimal(roll.labCost):BigDecimal.ZERO);budget.recorded=budget.recorded.add(total);budget.film=budget.film.add(film?new BigDecimal(roll.filmCost):BigDecimal.ZERO);budget.lab=budget.lab.add(lab?new BigDecimal(roll.labCost):BigDecimal.ZERO);
            if(film&&lab&&roll.count>0){budget.complete=budget.complete.add(total);budget.frames+=roll.count;budget.completeRolls++;}
        }return value;
    }
}
