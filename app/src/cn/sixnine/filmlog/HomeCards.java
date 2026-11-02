package cn.sixnine.filmlog;
import android.database.Cursor;
import java.util.*;
final class HomeCards {
    static final class Summary {String continueRoll="";int readyForLab,missingScans;final ArrayList<LogStore.Roll> nearlyFinished=new ArrayList<>();}
    static Summary read(LogStore logs){return read(logs,logs.rolls());}
    static Summary read(LogStore logs,Collection<LogStore.Roll> source){Summary value=new Summary();Map<String,LogStore.Roll> rolls=new HashMap<>();for(LogStore.Roll roll:source){if(roll.archived)continue;rolls.put(roll.id,roll);if("finished".equals(roll.state))value.readyForLab++;if("active".equals(roll.state)){if(value.continueRoll.isEmpty())value.continueRoll=roll.id;if(roll.capacity-roll.count>=1&&roll.capacity-roll.count<=3)value.nearlyFinished.add(roll);}}
        try(Cursor c=logs.getReadableDatabase().rawQuery("SELECT roll_id FROM shots ORDER BY tapped_at DESC,id DESC",null)){while(c.moveToNext()){LogStore.Roll roll=rolls.get(c.getString(0));if(roll!=null&&"active".equals(roll.state)){value.continueRoll=roll.id;break;}}}
        try(Cursor c=logs.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM shots s JOIN rolls r ON r.id=s.roll_id WHERE r.archived=0 AND r.state='received' AND NOT EXISTS(SELECT 1 FROM scans a WHERE a.shot_id=s.id AND a.deleted_at IS NULL)",null)){c.moveToFirst();value.missingScans=c.getInt(0);}return value;
    }
}
