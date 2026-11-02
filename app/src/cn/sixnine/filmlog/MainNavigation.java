package cn.sixnine.filmlog;

import android.content.Context;
import android.graphics.Typeface;
import android.view.*;
import android.widget.*;
import java.util.function.Consumer;

/** Shared top-level destinations for the main page and the existing project list. */
final class MainNavigation {
    private static int dp(Context context,int value){return Math.round(value*context.getResources().getDisplayMetrics().density);}
    static LinearLayout tabs(Context context,String selected,Consumer<String> navigate){
        LinearLayout nav=new LinearLayout(context);nav.setGravity(Gravity.CENTER_VERTICAL);nav.setPadding(dp(context,16),0,dp(context,16),dp(context,8));nav.setBackgroundColor(context.getColor(R.color.ui_background));
        String[] keys={"dashboard","home","my"},labels={"随记","胶卷","我的"};int[] icons={R.drawable.nav_journal,R.drawable.nav_roll,R.drawable.nav_person};
        for(int i=0;i<keys.length;i++){final String key=keys[i];boolean active=key.equals(selected);int color=context.getColor(active?R.color.ui_accent:R.color.ui_muted);LinearLayout item=new LinearLayout(context);item.setOrientation(LinearLayout.VERTICAL);item.setGravity(Gravity.CENTER);item.setMinimumHeight(dp(context,64));item.setPadding(0,dp(context,8),0,dp(context,8));item.setTag("main-tab-"+key);item.setFocusable(true);item.setSelected(active);item.setContentDescription(labels[i]+(active?"，当前页":""));item.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(context.getColor(R.color.ui_selected)),null,new android.graphics.drawable.ColorDrawable(android.graphics.Color.WHITE)));
            ImageView icon=new ImageView(context);icon.setImageResource(icons[i]);icon.setSelected(active);icon.setDuplicateParentStateEnabled(true);icon.setImageTintList(android.content.res.ColorStateList.valueOf(color));icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);item.addView(icon,new LinearLayout.LayoutParams(dp(context,24),dp(context,24)));
            TextView caption=new TextView(context);caption.setText(labels[i]);caption.setTextSize(11);caption.setTypeface(Typeface.create(active?"sans-serif-medium":"sans-serif",Typeface.NORMAL));caption.setTextColor(color);caption.setGravity(Gravity.CENTER);caption.setIncludeFontPadding(false);caption.setSingleLine(true);caption.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);LinearLayout.LayoutParams labelSize=new LinearLayout.LayoutParams(-1,-2);labelSize.topMargin=dp(context,5);item.addView(caption,labelSize);item.setOnClickListener(v->navigate.accept(key));nav.addView(item,new LinearLayout.LayoutParams(0,-2,1));}
        return nav;
    }
    static LinearLayout collectionTabs(Context context,boolean projects,Runnable rolls,Runnable project){
        LinearLayout row=new LinearLayout(context);String[] names={"胶卷","项目"};for(int i=0;i<2;i++){boolean selected=projects?i==1:i==0;LinearLayout item=new LinearLayout(context);item.setOrientation(LinearLayout.VERTICAL);Button b=UiSpacing.button(context);b.setText(names[i]);b.setTextSize(20);b.setAllCaps(false);b.setStateListAnimator(null);b.setElevation(0);b.setTranslationZ(0);b.setBackgroundColor(android.graphics.Color.TRANSPARENT);b.setTextColor(context.getColor(selected?R.color.ui_ink:R.color.ui_muted));b.setPadding(0,0,0,0);b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(dp(context,48));b.setSelected(selected);b.setContentDescription(names[i]+(selected?"，当前页":""));b.setOnClickListener(v->{if(b.getText().toString().equals("胶卷"))rolls.run();else project.run();});item.addView(b);View line=new View(context);line.setBackgroundColor(context.getColor(selected?R.color.ui_accent:android.R.color.transparent));item.addView(line,new LinearLayout.LayoutParams(-1,dp(context,2)));LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(-2,-2);size.rightMargin=dp(context,24);row.addView(item,size);}return row;
    }
}
