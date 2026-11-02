package cn.sixnine.filmlog;
import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.*;
import java.time.ZoneId;

public final class RemindersActivity extends WorkspaceActivity {
    private LogStore logs;
    @Override public void onCreate(Bundle state){super.onCreate(state);logs=new LogStore(this);}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(1);return v;}
    private TextView label(String value,int size){TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextColor(UiColors.color(getResources(),0xfff4eee2));v.setPadding(dp(8),dp(10),dp(8),dp(10));return v;}
    private Button button(String value,Runnable action){Button b=UiSpacing.button(this);b.setText(value);b.setTextColor(UiColors.color(getResources(),0xffef8249));b.setAllCaps(false);b.setBackgroundTintList(null);android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();bg.setColor(UiColors.color(getResources(),0xff30251e));bg.setCornerRadius(dp(12));b.setBackground(bg);b.setMinHeight(dp(48));b.setOnClickListener(v->action.run());return b;}
    private void show(){LinearLayout root=column();root.setClipToPadding(false);root.setPadding(dp(16),0,dp(16),0);root.setBackgroundColor(UiColors.color(getResources(),0xff21170f));setContentView(root);root.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(dp(16),i.getSystemWindowInsetTop(),dp(16),i.getSystemWindowInsetBottom());return i;});root.requestApplyInsets();root.addView(LibraryUi.header(this,"曝光记录提醒",this::finish,null,16));ScrollView scroll=new ScrollView(this);LinearLayout list=column();scroll.addView(list);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        list.addView(label("仅在你设置后提醒一次。系统省电可能延迟通知；强行停止应用会停止提醒，重新打开后恢复。时间偏移只影响曝光记录，不改变提醒时刻。",14));
        if(!ExposureReminders.allowed(this))list.addView(button("开启曝光提醒通知",()->{if(android.os.Build.VERSION.SDK_INT>=33&&checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=android.content.pm.PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},30);else startActivity(new Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,getPackageName()).putExtra(android.provider.Settings.EXTRA_CHANNEL_ID,ExposureReminders.CHANNEL));}));
        int shown=0;for(LogStore.Roll roll:logs.rolls()){ExposureReminders.Entry entry=ExposureReminders.read(this,roll.id);boolean active=!roll.archived&&"active".equals(roll.state);if(!active&&entry==null)continue;shown++;list.addView(label(roll.name+" · "+roll.count+" / "+roll.capacity,19));list.addView(label(ExposureReminders.status(entry),14));if(active)list.addView(button("设置 / 更改提醒",()->choose(roll.id)));else list.addView(label("胶卷已归档或结束拍摄，不再发送曝光提醒",13));if(entry!=null&&("pending".equals(entry.status)||"delivered".equals(entry.status)))list.addView(button("取消提醒",()->{ExposureReminders.cancel(this,roll.id);show();}));}
        if(shown==0)list.addView(label("暂无拍摄中的胶卷，装上一卷后可设置提醒。",16));
    }
    private void choose(String roll){String evening=java.time.LocalTime.now().isAfter(java.time.LocalTime.of(19,0))?"明晚 19:00":"今晚 19:00";new AlertDialog.Builder(this).setTitle("何时提醒记录曝光？").setItems(new String[]{"两小时后",evening,"明早 09:00"},(d,which)->{try{ExposureReminders.set(this,roll,ExposureReminders.preset(System.currentTimeMillis(),ZoneId.systemDefault(),which));show();}catch(Exception error){Toast.makeText(this,error.getMessage(),1).show();}}).show();}
    @Override protected void onResume(){super.onResume();ExposureReminders.restore(this);show();}
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);if(r==30)show();}
    @Override protected void onDestroy(){logs.close();super.onDestroy();}
}
