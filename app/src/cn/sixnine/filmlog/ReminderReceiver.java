package cn.sixnine.filmlog;
import android.content.*;
public final class ReminderReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){String expected=intent.getStringExtra("workspace");if(expected!=null&&!expected.equals(Workspace.current(context)))return;context=Workspace.context(context);if("android.intent.action.BOOT_COMPLETED".equals(intent.getAction())||"android.intent.action.MY_PACKAGE_REPLACED".equals(intent.getAction())){ExposureReminders.restore(context);return;}if("cn.sixnine.filmlog.EXPOSURE_REMINDER".equals(intent.getAction()))ExposureReminders.deliver(context,intent.getStringExtra("roll"),intent.getStringExtra("token"));}
}
