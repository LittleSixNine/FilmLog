package cn.sixnine.filmlog;
import android.app.*;
import android.content.*;
import android.os.Bundle;
import java.util.*;
/** Compatibility screen appears only when old namespaces remain on the device. */
public final class LocalRecordsActivity extends Activity {
 @Override public void onCreate(Bundle state){super.onCreate(state);try{LinkedHashMap<String,String> saved=LegacyRecords.spaces(this);ArrayList<String> ids=new ArrayList<>(saved.keySet());new AlertDialog.Builder(this).setTitle("旧版资料（离线）").setMessage("旧版资料仍保留在手机中，可打开后使用本地备份保存。不提供账号登录或同步。").setPositiveButton("查看资料",(d,w)->new AlertDialog.Builder(this).setTitle("选择本机资料").setItems(saved.values().toArray(new String[0]),(dd,index)->{try{LegacyRecords.activate(this,ids.get(index));startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));finish();}catch(Exception error){android.widget.Toast.makeText(this,error.getMessage(),1).show();finish();}}).setNegativeButton("取消",(dd,w2)->finish()).setOnCancelListener(dd->finish()).show()).setNegativeButton("返回",(d,w)->finish()).setOnCancelListener(d->finish()).show();}catch(Exception error){new AlertDialog.Builder(this).setTitle("无法读取旧资料索引").setMessage("已有记录和照片保留。"+error.getMessage()).setPositiveButton("返回",(d,w)->finish()).setOnCancelListener(d->finish()).show();}}
}
