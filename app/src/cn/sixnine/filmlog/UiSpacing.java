package cn.sixnine.filmlog;

import android.content.Context;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

/** Custom button backgrounds have no platform inset; keep a visible gap without changing their shape. */
final class UiSpacing {
    static Button button(Context context) {
        Button button=new Button(context);
        button.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){
            public void onViewAttachedToWindow(View view){apply(view);}
            public void onViewDetachedFromWindow(View view){}
        });
        return button;
    }
    static boolean buttonRow(View view){
        if(!(view instanceof LinearLayout))return false;LinearLayout row=(LinearLayout)view;
        if(row.getOrientation()!=LinearLayout.HORIZONTAL||row.getChildCount()==0)return false;
        for(int i=0;i<row.getChildCount();i++)if(!(row.getChildAt(i) instanceof Button))return false;return true;
    }
    static void apply(View view) {
        if(!(view.getParent() instanceof LinearLayout)||!(view.getLayoutParams() instanceof LinearLayout.LayoutParams))return;
        LinearLayout parent=(LinearLayout)view.getParent();
        LinearLayout.LayoutParams size=(LinearLayout.LayoutParams)view.getLayoutParams();
        int gap=Math.round((parent.getOrientation()==LinearLayout.HORIZONTAL?4:6)*view.getResources().getDisplayMetrics().density);
        int left=size.leftMargin,top=size.topMargin,right=size.rightMargin,bottom=size.bottomMargin;
        if(parent.getOrientation()==LinearLayout.HORIZONTAL){size.leftMargin=Math.max(left,gap);size.rightMargin=Math.max(right,gap);}
        else{size.topMargin=Math.max(top,gap);size.bottomMargin=Math.max(bottom,gap);}
        if(left!=size.leftMargin||top!=size.topMargin||right!=size.rightMargin||bottom!=size.bottomMargin)view.setLayoutParams(size);
        if(buttonRow(parent)&&parent.getParent() instanceof LinearLayout&&parent.getLayoutParams() instanceof LinearLayout.LayoutParams){
            LinearLayout group=(LinearLayout)parent.getParent();int index=group.indexOfChild(parent);
            boolean adjacent=group.getOrientation()==LinearLayout.VERTICAL&&(index>0&&buttonRow(group.getChildAt(index-1))||index+1<group.getChildCount()&&buttonRow(group.getChildAt(index+1)));
            if(adjacent){LinearLayout.LayoutParams rowSize=(LinearLayout.LayoutParams)parent.getLayoutParams();int rowGap=Math.round(4*view.getResources().getDisplayMetrics().density);
                if(rowSize.topMargin<rowGap||rowSize.bottomMargin<rowGap){rowSize.topMargin=Math.max(rowSize.topMargin,rowGap);rowSize.bottomMargin=Math.max(rowSize.bottomMargin,rowGap);parent.setLayoutParams(rowSize);}}
        }
    }
}
