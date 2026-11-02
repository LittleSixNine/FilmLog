package cn.sixnine.filmlog;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.widget.*;

/** Compact identity shared by the chooser and launcher images. */
final class WidgetCard {
    static String logo(Context c,LogStore logs,LogStore.Roll roll){CatalogStore.Detail d=new CatalogStore(logs).detail(roll.id);return d!=null&&!d.camera.isEmpty()?new CatalogStore(logs).entry(d.camera,"camera").cameraLogo:CameraLogos.suggest(c,roll.camera);}
    static ImageView logoView(Context c,String id){ImageView mark=CameraLogos.view(c,id);if((c.getResources().getConfiguration().uiMode&android.content.res.Configuration.UI_MODE_NIGHT_MASK)==android.content.res.Configuration.UI_MODE_NIGHT_YES)mark.setImageTintList(android.content.res.ColorStateList.valueOf(c.getColor(R.color.ui_ink)));return mark;}
    static Bitmap render(View view,int width,int height){view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));view.layout(0,0,width,height);Bitmap b=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);view.draw(new Canvas(b));return b;}
    static Bitmap cover(Context c,LogStore logs,LogStore.Roll roll,int size){CatalogStore.Detail d=new CatalogStore(logs).detail(roll.id);org.json.JSONObject cover=ArtworkLinks.cover(c,logs,"roll",roll.id,d==null?"":d.format);Bitmap source=cover==null?null:FilmArtwork.image(c,cover.optString(size>192?"image":"thumbnail"));if(source==null&&cover!=null&&size>192)source=FilmArtwork.image(c,cover.optString("thumbnail"));if(source==null){TextView empty=LibraryUi.text(c,"暂无封面",12,c.getColor(R.color.ui_muted));empty.setGravity(Gravity.CENTER);return render(empty,size,size);}int edge=Math.max(source.getWidth(),source.getHeight());if(edge<=size)return source;float scale=size/(float)edge;return Bitmap.createScaledBitmap(source,Math.max(1,Math.round(source.getWidth()*scale)),Math.max(1,Math.round(source.getHeight()*scale)),true);}

    static View choice(Context c,LogStore logs,LogStore.Roll roll,boolean selected,Runnable choose){
        LinearLayout card=LibraryUi.column(c);android.graphics.drawable.GradientDrawable bg=LibraryUi.surface(c,c.getColor(R.color.ui_card),12);bg.setStroke(LibraryUi.dp(c,selected?2:1),c.getColor(selected?R.color.ui_accent:R.color.ui_outline));card.setBackground(bg);card.setClipToOutline(true);card.setFocusable(true);card.setSelected(selected);card.setContentDescription(roll.stock+"，"+roll.camera+"，"+roll.count+"/"+roll.capacity+(selected?"，已选择":""));card.setOnClickListener(v->choose.run());
        card.addView(new HomeRollCard.ExposureWindows(c,roll.count,roll.capacity),new LinearLayout.LayoutParams(-1,LibraryUi.dp(c,14)));
        LinearLayout content=LibraryUi.column(c);int p=LibraryUi.dp(c,14);content.setPadding(p,p,p,p);card.addView(content);
        LinearLayout heading=new LinearLayout(c);heading.setGravity(Gravity.CENTER_VERTICAL);String id=logo(c,logs,roll);ImageView mark=logoView(c,id);mark.setScaleType(ImageView.ScaleType.FIT_START);heading.addView(mark,new LinearLayout.LayoutParams(0,LibraryUi.dp(c,26),1));TextView radio=LibraryUi.text(c,selected?"●":"○",20,c.getColor(selected?R.color.ui_accent:R.color.ui_muted));radio.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);heading.addView(radio);content.addView(heading);
        LinearLayout row=new LinearLayout(c);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,LibraryUi.dp(c,10),0,0);if(!ArtworkLinks.thumbnail(c,logs,"roll",roll.id,row,62)){TextView empty=LibraryUi.text(c,"暂无封面",11,c.getColor(R.color.ui_muted));row.addView(empty,new LinearLayout.LayoutParams(LibraryUi.dp(c,62),LibraryUi.dp(c,62)));}
        LinearLayout info=LibraryUi.column(c);TextView stock=LibraryUi.text(c,roll.stock,16,c.getColor(R.color.ui_ink));stock.setTypeface(Typeface.DEFAULT,Typeface.BOLD);info.addView(stock);TextView camera=LibraryUi.text(c,roll.camera,12,c.getColor(R.color.ui_muted));camera.setPadding(0,LibraryUi.dp(c,6),0,0);info.addView(camera);CatalogStore.Detail d=new CatalogStore(logs).detail(roll.id);info.addView(LibraryUi.text(c,(d==null?"":d.format+(d.halfFrame?" 半格":"")+" · ")+"ISO "+roll.iso,11,c.getColor(R.color.ui_muted)));row.addView(info,new LinearLayout.LayoutParams(0,-2,1));row.addView(new HomeRollCard.Fraction(c,roll.count,roll.capacity));content.addView(row);return card;
    }
}
