package cn.sixnine.filmlog;

import android.app.*;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.util.Locale;
import java.util.function.IntConsumer;

/** A local draft: closing the sheet never changes the default or existing shots. */
final class OffsetEditor {
    static Dialog show(Activity a,int initial,IntConsumer save){
        int pad=LibraryUi.dp(a,20);LinearLayout body=LibraryUi.column(a);body.setPadding(pad,0,pad,pad);
        body.addView(LibraryUi.text(a,"让记录时间更接近按下快门的时刻",14,a.getColor(R.color.ui_muted)));
        LinearLayout controls=new LinearLayout(a);controls.setGravity(Gravity.CENTER_VERTICAL);controls.setPadding(0,pad,0,pad);
        EditText value=new EditText(a);value.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_SIGNED);value.setSingleLine(true);value.setText(String.valueOf(initial));value.setTextSize(36);value.setTextColor(a.getColor(R.color.ui_ink));value.setGravity(Gravity.CENTER);value.setContentDescription("记录时间偏移，秒");value.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        Button minus=LibraryUi.action(a,"−",false,()->step(value,-1)),plus=LibraryUi.action(a,"＋",false,()->step(value,1));minus.setContentDescription("提前1秒");plus.setContentDescription("延后1秒");
        controls.addView(minus,new LinearLayout.LayoutParams(LibraryUi.dp(a,48),-2));controls.addView(value,new LinearLayout.LayoutParams(0,-2,1));controls.addView(LibraryUi.text(a,"秒",14,a.getColor(R.color.ui_muted)));controls.addView(plus,new LinearLayout.LayoutParams(LibraryUi.dp(a,48),-2));body.addView(controls);
        LinearLayout presets=new LinearLayout(a);for(int n:new int[]{-30,-10,0,10}){Button b=LibraryUi.action(a,(n>0?"+":"")+n,false,()->value.setText(String.valueOf(n)));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.setMargins(3,0,3,0);presets.addView(b,p);}body.addView(presets);
        TextView meaning=LibraryUi.text(a,"",16,a.getColor(R.color.ui_ink));meaning.setPadding(0,pad,0,LibraryUi.dp(a,8));body.addView(meaning);TextView example=LibraryUi.text(a,"",13,a.getColor(R.color.ui_muted));body.addView(example);
        TextView note=LibraryUi.text(a,"只影响之后的新记录，不会修改已有曝光。",13,a.getColor(R.color.ui_muted));note.setPadding(0,pad,0,0);body.addView(note);
        Dialog[] dialog={null};LinearLayout footer=new LinearLayout(a);Button cancel=LibraryUi.action(a,"取消",false,()->dialog[0].dismiss());Button apply=LibraryUi.action(a,"保存偏移",true,()->{try{save.accept(RecordRules.parseOffset(value.getText().toString()));dialog[0].dismiss();}catch(Exception e){value.setError(e.getMessage());}});footer.addView(cancel,new LinearLayout.LayoutParams(0,-2,1));LinearLayout.LayoutParams right=new LinearLayout.LayoutParams(0,-2,2);right.leftMargin=LibraryUi.dp(a,12);footer.addView(apply,right);
        Runnable refresh=()->{try{int n=RecordRules.parseOffset(value.getText().toString());meaning.setText(n==0?"与点击记录的时间一致":n<0?"提前 "+(-n)+" 秒":"延后 "+n+" 秒");int seconds=14*3600+30*60+n;example.setText(String.format(Locale.ROOT,"例如 14:30:00 点击 → %02d:%02d:%02d",seconds/3600,seconds%3600/60,seconds%60));apply.setEnabled(true);}catch(Exception e){meaning.setText("请输入 −3600 至 +3600 的整数秒");example.setText("尚未保存");apply.setEnabled(false);}};
        value.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){refresh.run();}public void afterTextChanged(Editable e){}});refresh.run();dialog[0]=LibraryUi.sheet(a,"记录时间偏移",body,footer);LinearLayout container=(LinearLayout)((android.view.ViewGroup)dialog[0].findViewById(android.R.id.content)).getChildAt(0);LinearLayout heading=(LinearLayout)container.getChildAt(0);((TextView)heading.getChildAt(0)).setGravity(Gravity.CENTER_VERTICAL);dialog[0].getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE|WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);return dialog[0];
    }
    private static void step(EditText edit,int delta){try{int n=RecordRules.parseOffset(edit.getText().toString())+delta;if(Math.abs(n)<=3600)edit.setText(String.valueOf(n));}catch(Exception e){edit.setError("请先填写有效秒数");}}
}
