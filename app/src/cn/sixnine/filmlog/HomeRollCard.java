package cn.sixnine.filmlog;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;

/** C nameplate card. Counts are database snapshots, never optimistic UI increments. */
final class HomeRollCard extends LinearLayout {
    private final LinearLayout coverBox,actions,infoBorder;
    private final Button quick,meter;
    private final ExposureWindows windows;
    private final String rollId;
    private final int count,capacity;

    HomeRollCard(Context context,LogStore logs,LogStore.Roll roll,Runnable open,Runnable record,Runnable measure){
        super(context);rollId=roll.id;count=roll.count;capacity=roll.capacity;int next=logs.nextFrame(roll.id);CatalogStore.Detail detail=new CatalogStore(logs).detail(roll.id);
        setOrientation(VERTICAL);setPadding(0,0,0,0);setBackground(shape(color(R.color.ui_card),color(R.color.ui_outline)));setClipToOutline(true);
        setTag("dashboard-card:"+roll.id);setOnClickListener(v->open.run());setFocusable(true);setContentDescription("打开胶卷 "+roll.stock+"，已记录"+count+"张，标称"+capacity+"张");
        String cameraName=roll.camera;String cameraLogo;boolean showBrand=false;
        if(detail!=null&&!detail.camera.isEmpty()){CatalogStore.Entry cameraEntry=new CatalogStore(logs).entry(detail.camera,"camera");cameraName=cameraEntry.cameraModel.isEmpty()?cameraEntry.name:cameraEntry.cameraModel;cameraLogo=cameraEntry.cameraLogo;showBrand=cameraEntry.cameraShowBrand;}
        else cameraLogo=CameraLogos.suggest(context,cameraName);
        boolean hasCamera=cameraName!=null&&!cameraName.isEmpty()&&!cameraName.equals("未设置相机")&&!cameraName.equals("未选择相机");
        windows=new ExposureWindows(context,count,capacity);windows.setTag("dashboard-windows");addView(windows,new LinearLayout.LayoutParams(-1,dp(18)));
        CameraHeading cameraHeading=new CameraHeading(context,hasCamera?cameraName:"未选择相机",cameraLogo,count,capacity);addView(cameraHeading,new LinearLayout.LayoutParams(-1,-2));
        TextView model=words(hasCamera?CameraLogos.displayModel(context,cameraName,showBrand):"未选择相机",16,true);model.setTag("dashboard-camera-model");LinearLayout.LayoutParams modelSize=new LinearLayout.LayoutParams(-1,-2);modelSize.setMargins(dp(16),dp(4),dp(16),dp(20));addView(model,modelSize);
        LinearLayout identity=new LinearLayout(context);identity.setGravity(Gravity.CENTER_VERTICAL);identity.setBaselineAligned(false);
        coverBox=new LinearLayout(context);coverBox.setGravity(Gravity.CENTER);
        if(!ArtworkLinks.thumbnail(context,logs,"roll",roll.id,coverBox,100)){TextView missing=words("暂无封面",12,false);missing.setGravity(Gravity.CENTER);missing.setTextColor(color(R.color.ui_muted));coverBox.addView(missing);}
        coverBox.getChildAt(0).setLayoutParams(new LinearLayout.LayoutParams(-1,-1));identity.addView(coverBox,new LinearLayout.LayoutParams(dp(100),dp(100)));
        infoBorder=new LinearLayout(context);infoBorder.setBaselineAligned(false);infoBorder.setGravity(Gravity.CENTER_VERTICAL);View separator=new View(context);separator.setBackgroundColor(color(R.color.ui_outline));infoBorder.addView(separator,new LinearLayout.LayoutParams(dp(1),-1));
        LinearLayout info=new LinearLayout(context);info.setOrientation(VERTICAL);info.setPadding(dp(12),0,0,0);
        TextView stock=words(roll.stock,19,true);stock.setTag("dashboard-stock");info.addView(stock);
        String format=detail==null||detail.format.isEmpty()?"":detail.format+(detail.halfFrame?" 半格":"")+"\n";
        TextView spec=words(format+"ISO "+(roll.iso>0?String.valueOf(roll.iso):"未记"),12,false);spec.setTextColor(color(R.color.ui_muted));LinearLayout.LayoutParams specSize=new LinearLayout.LayoutParams(-1,-2);specSize.topMargin=dp(8);info.addView(spec,specSize);
        infoBorder.addView(info,new LinearLayout.LayoutParams(0,-2,1));LinearLayout.LayoutParams infoSize=new LinearLayout.LayoutParams(0,-2,1);infoSize.leftMargin=dp(16);identity.addView(infoBorder,infoSize);LinearLayout.LayoutParams identitySize=new LinearLayout.LayoutParams(-1,-2);identitySize.setMargins(dp(16),0,dp(16),0);addView(identity,identitySize);
        if(RecordRules.nominalReached(next-1,capacity)){TextView warning=words(RecordRules.nominalWarning(capacity,detail==null?"":detail.format),11,false);warning.setTag("roll-capacity-warning");warning.setTextColor(color(R.color.ui_accent));LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(-1,-2);size.setMargins(dp(16),dp(12),dp(16),0);addView(warning,size);}
        actions=new LinearLayout(context);actions.setBaselineAligned(false);
        boolean available=ExposureWidget.available(roll);quick=action(available?"快速记录第"+next+"张":roll.state.equals("finished")?"待送洗":"已结束",true,record);quick.setTag("quick-record");meter=action("测光记录",false,measure);meter.setTag("meter-record");quick.setEnabled(available);meter.setEnabled(available);if(!available){quick.setAlpha(.45f);meter.setAlpha(.45f);}actions.addView(quick);actions.addView(meter);layoutActions(false);LinearLayout.LayoutParams actionSize=new LinearLayout.LayoutParams(-1,-2);actionSize.setMargins(dp(16),dp(16),dp(16),dp(16));addView(actions,actionSize);
    }
    String rollId(){return rollId;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private int color(int id){return getResources().getColor(id,null);}
    private TextView words(String value,int sp,boolean medium){TextView text=new TextView(getContext());text.setText(value);text.setTextSize(sp);text.setTextColor(color(R.color.ui_ink));text.setIncludeFontPadding(false);text.setTypeface(Typeface.create(medium?"sans-serif-medium":"sans-serif",0));return text;}
    private GradientDrawable shape(int fill,int outline){GradientDrawable bg=new GradientDrawable();bg.setColor(fill);bg.setCornerRadius(dp(8));if(outline!=0)bg.setStroke(dp(1),outline);return bg;}
    private Button action(String label,boolean primary,Runnable run){Button b=UiSpacing.button(getContext());b.setStateListAnimator(null);b.setElevation(0);b.setTranslationZ(0);b.setAllCaps(false);b.setText(label);b.setContentDescription(label);b.setTextSize(13);b.setSingleLine(true);b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(dp(48));b.setMinimumHeight(dp(48));b.setPadding(dp(10),dp(8),dp(10),dp(8));b.setTextColor(color(primary?R.color.ui_on_accent:R.color.ui_ink));b.setBackgroundTintList(null);b.setBackground(new RippleDrawable(ColorStateList.valueOf(color(R.color.ui_accent_faint)),shape(color(primary?R.color.ui_accent:R.color.ui_card),primary?0:color(R.color.ui_outline)),null));b.setOnClickListener(v->run.run());return b;}
    private void layoutActions(boolean stacked){actions.setOrientation(stacked?VERTICAL:HORIZONTAL);LinearLayout.LayoutParams a=new LinearLayout.LayoutParams(stacked?-1:0,-2,stacked?0:1.35f),b=new LinearLayout.LayoutParams(stacked?-1:0,-2,stacked?0:1);if(stacked)b.topMargin=dp(12);else a.rightMargin=dp(12);quick.setLayoutParams(a);meter.setLayoutParams(b);}
    @Override protected void onMeasure(int width,int height){int inside=Math.max(1,MeasureSpec.getSize(width)-dp(32));int edge=Math.round(inside*.39f);infoBorder.setMinimumHeight(edge);LinearLayout.LayoutParams coverSize=(LinearLayout.LayoutParams)coverBox.getLayoutParams();if(coverSize.width!=edge){coverSize.width=coverSize.height=edge;coverBox.setLayoutParams(coverSize);}float available=Math.max(0,inside-dp(12));boolean stack=quick.getPaint().measureText(quick.getText().toString())+quick.getPaddingLeft()+quick.getPaddingRight()>available*1.35f/2.35f||meter.getPaint().measureText(meter.getText().toString())+meter.getPaddingLeft()+meter.getPaddingRight()>available/2.35f;if((actions.getOrientation()==VERTICAL)!=stack)layoutActions(stack);super.onMeasure(width,height);}

    private final class CameraHeading extends ViewGroup {
        private final View mark;private final Fraction counter;private final Bitmap bitmap;private int inkBottom;
        CameraHeading(Context context,String name,String logoId,int used,int total){super(context);setTag("dashboard-camera-heading");CameraLogos.Logo logo=CameraLogos.find(context,logoId);bitmap=CameraLogos.image(context,logo);
            if(bitmap!=null){ImageView image=CameraLogos.view(context,logoId);image.setScaleType(ImageView.ScaleType.MATRIX);if((getResources().getConfiguration().uiMode&android.content.res.Configuration.UI_MODE_NIGHT_MASK)==android.content.res.Configuration.UI_MODE_NIGHT_YES)image.setImageTintList(ColorStateList.valueOf(color(R.color.ui_ink)));image.setTag("dashboard-camera-logo");mark=image;inkBottom=bitmap.getHeight();boolean found=false;for(int y=bitmap.getHeight()-1;y>=0&&!found;y--)for(int x=0;x<bitmap.getWidth();x++)if(Color.alpha(bitmap.getPixel(x,y))>32){inkBottom=y+1;found=true;break;}}
            else{TextView fallback=words(String.valueOf((char)0xe10e),24,false);fallback.setTypeface(Typeface.createFromAsset(context.getAssets(),"fonts/Phosphor.ttf"));mark=fallback;}
            mark.setContentDescription(name);addView(mark);counter=new Fraction(context,used,total);addView(counter);
        }
        @Override protected void onMeasure(int width,int height){int w=MeasureSpec.getSize(width),available=Math.max(1,w-dp(32));counter.measure(MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED),MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));int logoWidth=Math.max(1,Math.min(dp(180),available-counter.getMeasuredWidth()-dp(12)));mark.measure(MeasureSpec.makeMeasureSpec(logoWidth,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(dp(28),MeasureSpec.EXACTLY));setMeasuredDimension(w,Math.max(dp(48),counter.getMeasuredHeight()+dp(20)));}
        @Override protected void onLayout(boolean changed,int l,int t,int r,int b){int top=dp(10),bottom=top+mark.getMeasuredHeight();mark.layout(dp(16),top,dp(16)+mark.getMeasuredWidth(),bottom);
            if(bitmap!=null){float scale=Math.min(mark.getWidth()/(float)bitmap.getWidth(),mark.getHeight()/(float)bitmap.getHeight());Matrix matrix=new Matrix();matrix.setScale(scale,scale);matrix.postTranslate(0,(mark.getHeight()-bitmap.getHeight()*scale)/2);((ImageView)mark).setImageMatrix(matrix);bottom=Math.round(top+(mark.getHeight()-bitmap.getHeight()*scale)/2+inkBottom*scale);}
            else if(mark instanceof TextView)bottom=top+((TextView)mark).getBaseline();
            if(bottom<counter.getBaseline()){int shift=counter.getBaseline()-bottom;mark.offsetTopAndBottom(shift);bottom+=shift;}
            int right=getWidth()-dp(16);counter.layout(right-counter.getMeasuredWidth(),bottom-counter.getBaseline(),right,bottom-counter.getBaseline()+counter.getMeasuredHeight());
        }
    }
    static final class Fraction extends TextView {
        private final Paint main=new Paint(Paint.ANTI_ALIAS_FLAG),small=new Paint(Paint.ANTI_ALIAS_FLAG),slash=new Paint(Paint.ANTI_ALIAS_FLAG);private final String used,total;private final Rect ink=new Rect(),totalInk=new Rect();private final float gap,slashHeight,slashBottom;private int baseline;
        Fraction(Context context,int count,int capacity){super(context);used=String.valueOf(count);total=String.valueOf(capacity);float sp=getResources().getDisplayMetrics().scaledDensity,density=getResources().getDisplayMetrics().density;
            Typeface face=new Typeface.Builder(context.getAssets(),"fonts/newsreader/newsreader.ttf").setFontVariationSettings("'wght' 500, 'opsz' 26").build();setTypeface(face);setTextSize(26);setIncludeFontPadding(false);setTextColor(context.getColor(R.color.ui_film_count));setTag("dashboard-count");
            SpannableString value=new SpannableString(used+"/"+total);value.setSpan(new AbsoluteSizeSpan(Math.round(10*sp)),used.length(),value.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);setText(value);setContentDescription("已记录"+count+"张，标称"+capacity+"张");
            main.setTypeface(face);main.setTextSize(26*sp);main.setFontFeatureSettings("'lnum', 'tnum'");small.set(main);small.setTypeface(new Typeface.Builder(context.getAssets(),"fonts/newsreader/newsreader.ttf").setFontVariationSettings("'wght' 500, 'opsz' 10").build());small.setTextSize(10*sp);main.getTextBounds(used,0,used.length(),ink);small.getTextBounds(total,0,total.length(),totalInk);gap=14*density;slashBottom=10*sp*.24f;slashHeight=Math.min(26*sp*.78f,Math.max(0,ink.height()-slashBottom)/(float)Math.cos(Math.toRadians(22)));slash.setStrokeWidth(.65f*density);
        }
        @Override protected void onMeasure(int w,int h){baseline=Math.max(ink.height(),Math.round(slashHeight+slashBottom));setMeasuredDimension((int)Math.ceil(main.measureText(used)+gap+small.measureText(total)),baseline+1);}
        @Override public int getBaseline(){return baseline;}
        @Override protected void onDraw(Canvas canvas){int color=getCurrentTextColor();main.setColor(color);small.setColor(color);slash.setColor(color);slash.setAlpha(140);float x=main.measureText(used)+gap;canvas.drawText(used,0,baseline-ink.bottom,main);canvas.drawText(total,x,baseline-totalInk.bottom,small);float y=baseline-slashBottom;canvas.drawLine(x-8*getResources().getDisplayMetrics().density,y,x-8*getResources().getDisplayMetrics().density+(float)Math.sin(Math.toRadians(22))*slashHeight,y-(float)Math.cos(Math.toRadians(22))*slashHeight,slash);}
    }

    static float boundary(float width,int frame,int total){if(total<=0||frame<0||frame>total||!Float.isFinite(width)||width<0)throw new IllegalArgumentException("进度范围无效");return width*frame/total;}
    static final class ExposureWindows extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final int used,total,actual;
        ExposureWindows(Context context,int count,int capacity){super(context);actual=count;used=Math.max(0,Math.min(capacity,count));total=capacity;setContentDescription("已记录"+count+"张，标称"+capacity+"张");}
        @Override public int getBaseline(){return getMeasuredHeight();}
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);if(total<=0||getWidth()<=0)return;float density=getResources().getDisplayMetrics().density;
            paint.setStyle(Paint.Style.FILL);paint.setColor(color(R.color.ui_film_band));canvas.drawRect(0,0,getWidth(),getHeight(),paint);
            float padding=Math.min(5*density,getWidth()/4f),available=getWidth()-2*padding,pitch=available/total;
            float holeWidth=Math.min(5.4f*density,pitch*.62f),holeHeight=Math.min(11*density,holeWidth*2.2f),top=(getHeight()-holeHeight)/2,radius=holeWidth*.2f;
            for(int i=0;i<total;i++){
                float left=padding+(boundary(available,i,total)+boundary(available,i+1,total))/2-holeWidth/2;
                paint.setStyle(Paint.Style.FILL);paint.setColor(i<used?color(R.color.ui_film_lit):0x1af4e8ce);
                canvas.drawRoundRect(left,top,left+holeWidth,top+holeHeight,radius,radius,paint);
                if(i>=used){paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Math.min(.5f*density,holeWidth/5));paint.setColor(0x26f4e8ce);canvas.drawRoundRect(left,top,left+holeWidth,top+holeHeight,radius,radius,paint);}

            }
        }
        private int color(int id){return getResources().getColor(id,null);}
        @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(info);info.setClassName("android.widget.ProgressBar");info.setRangeInfo(AccessibilityNodeInfo.RangeInfo.obtain(AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT,0,total,used));info.setContentDescription("已记录"+actual+"张，标称"+total+"张");}
    }
}
