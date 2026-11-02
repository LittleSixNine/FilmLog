package cn.sixnine.filmlog;

import android.content.Context;
import android.graphics.*;
import android.util.LruCache;
import android.view.*;
import android.view.accessibility.*;
import java.io.File;
import java.util.*;
import java.util.concurrent.*;

/** Visible rows only; the full roll never becomes a full-resolution bitmap. */
final class ContactSheet extends View {
    private final ArrayList<LogStore.Shot> shots;
    private final Map<String,File> files=new HashMap<>();
    private final Set<String> pending=new HashSet<>(),failed=new HashSet<>();
    private final java.util.function.Consumer<String> open;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final android.os.Handler main=new android.os.Handler(android.os.Looper.getMainLooper());
    private final android.view.ViewTreeObserver.OnScrollChangedListener scrolled=()->invalidate();
    private int columns=5;private boolean film,halfFrame;private boolean perforated;private float filmAspect=1.5f;private int revealFrom=Integer.MAX_VALUE;private float reveal=1;private android.animation.ValueAnimator revealAnimator;
    private final Paint paint=new Paint(3);private final Rect visible=new Rect();private int accessibilityFocus=-1,keyboardFocus=-1;private volatile boolean detached;private float downX,downY;
    private final LruCache<String,Bitmap> images=new LruCache<String,Bitmap>(4*1024*1024){
        @Override protected int sizeOf(String key,Bitmap bitmap){return bitmap.getAllocationByteCount();}
        @Override protected void entryRemoved(boolean evicted,String key,Bitmap old,Bitmap replacement){if(old!=replacement)old.recycle();}
    };
    ContactSheet(Context context,ArrayList<LogStore.Shot> source,ArrayList<ScanStore.Scan> scans,ScanStore store,java.util.function.Consumer<String> open){super(context);shots=new ArrayList<>(source);shots.sort(Comparator.comparingInt(s->s.frame));for(ScanStore.Scan scan:scans)if(!scan.shotId.isEmpty())files.put(scan.shotId,store.file(scan.id));this.open=open;setContentDescription("接触印样，按曝光帧号排列；点击照片打开曝光详情");setFocusable(true);setDefaultFocusHighlightEnabled(false);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);}
    static ContactSheet film(Context context,LogStore logs,String roll,ArrayList<LogStore.Shot> source,java.util.function.Consumer<String> open){
        return film(context,logs,roll,source,photos(context,logs,roll),open);
    }
    static ContactSheet film(Context context,LogStore logs,String roll,ArrayList<LogStore.Shot> source,Map<String,File> pictures,java.util.function.Consumer<String> open){
        ContactSheet sheet=new ContactSheet(context,source,new ArrayList<ScanStore.Scan>(),null,open);sheet.columns=7;sheet.film=true;CatalogStore.Detail detail=new CatalogStore(logs).detail(roll);sheet.filmAspect=filmAspect(detail==null?"35 mm":detail.format);String format=detail==null?"35 mm":detail.format;sheet.perforated=format==null||format.replace(" ","").equalsIgnoreCase("35mm")||format.equals("135");
        sheet.halfFrame=detail!=null&&detail.halfFrame;if(sheet.halfFrame){sheet.columns=14;sheet.filmAspect=.75f;}sheet.files.putAll(pictures);
        sheet.setContentDescription("胶片预览，按帧号排列；扫描照片优先、参考照片补位；点击查看曝光详情");return sheet;
    }
    static Map<String,File> photos(Context context,LogStore logs,String roll){Map<String,File> files=new HashMap<>();try{ScanStore scans=new ScanStore(context,logs);try(android.database.Cursor rows=logs.getReadableDatabase().rawQuery("SELECT p.shot_id,p.id FROM reference_photos p JOIN shots s ON s.id=p.shot_id WHERE s.roll_id=?",new String[]{roll})){while(rows.moveToNext())files.put(rows.getString(0),scans.file(rows.getString(1)));}for(ScanStore.Scan scan:scans.list(roll))if(!scan.shotId.isEmpty()&&scans.file(scan.id).isFile())files.put(scan.shotId,scans.file(scan.id));}catch(java.io.IOException e){android.util.Log.w("FilmLog","Roll preview unavailable",e);}return files;}
    static float filmAspect(String format){String f=format==null?"":format.toLowerCase(Locale.ROOT).replace(" ","").replace('×','x');switch(f){case "120":case "220":case "6x6":return 1f;case "6x4.5":case "4.5x6":case "645":return 4f/3;case "6x7":return 7f/6;case "6x8":return 8f/6;case "6x9":return 1.5f;case "4x5":return 5f/4;case "5x7":return 7f/5;case "8x10":return 10f/8;default:return 1.5f;}}
    static int rows(int count,int columns){return (Math.max(0,count)+columns-1)/columns;}
    void revealAfter(int count){if(count>=shots.size()||!android.animation.ValueAnimator.areAnimatorsEnabled())return;revealFrom=Math.max(0,count);reveal=0;revealAnimator=android.animation.ValueAnimator.ofFloat(0,1);revealAnimator.setDuration(200);revealAnimator.addUpdateListener(v->{reveal=(Float)v.getAnimatedValue();invalidate();});revealAnimator.start();}
    // 135: one frame pitch is 38 mm, image 36×24 mm, strip 35 mm.
    private float stripScale(){return cell()/(halfFrame?19f:38f);}
    private float filmHeight(){return perforated&&filmAspect==1.5f?35*stripScale():filmPhotoHeight()+11*stripScale();}
    private float filmPhotoHeight(){return (halfFrame?18:36)*stripScale()/filmAspect;}
    private float rowHeight(){return film?Math.max(48*getResources().getDisplayMetrics().density,filmHeight()+4*getResources().getDisplayMetrics().density+14*getResources().getDisplayMetrics().scaledDensity):cell();}
    private Rect bounds(int index){float size=cell(),height=rowHeight();return new Rect(Math.round(index%columns*size),Math.round(index/columns*height),Math.round((index%columns+1)*size),Math.round((index/columns+1)*height));}
    private String description(int index){LogStore.Shot shot=shots.get(index);return "曝光 "+shot.frame+(files.containsKey(shot.id)?film?"，测光参考照片":"，已关联扫描":film?"，暂无参考照片":"，尚无扫描");}
    private void openFrame(int index){if(index>=0&&index<shots.size()){performClick();open.accept(shots.get(index).id);}}
    private void announce(int index,int type){AccessibilityEvent event=AccessibilityEvent.obtain(type);event.setPackageName(getContext().getPackageName());event.setClassName("android.widget.Button");event.setSource(this,index+1);event.getText().add(description(index));if(getParent()!=null)getParent().requestSendAccessibilityEvent(this,event);else event.recycle();}
    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(info);info.setClassName("android.widget.GridView");info.setCollectionInfo(AccessibilityNodeInfo.CollectionInfo.obtain(rows(shots.size(),columns),columns,false));}
    private final AccessibilityNodeProvider nodes=new AccessibilityNodeProvider(){
        @Override public AccessibilityNodeInfo createAccessibilityNodeInfo(int id){
            if(id==HOST_VIEW_ID){AccessibilityNodeInfo info=AccessibilityNodeInfo.obtain(ContactSheet.this);ContactSheet.this.onInitializeAccessibilityNodeInfo(info);for(int i=0;i<shots.size();i++)info.addChild(ContactSheet.this,i+1);return info;}
            int index=id-1;if(index<0||index>=shots.size())return null;AccessibilityNodeInfo info=AccessibilityNodeInfo.obtain();info.setSource(ContactSheet.this,id);info.setParent(ContactSheet.this);info.setPackageName(getContext().getPackageName());info.setClassName("android.widget.Button");info.setText(description(index));info.setEnabled(true);info.setClickable(true);info.setFocusable(true);info.setAccessibilityFocused(accessibilityFocus==index);info.setFocused(hasFocus()&&keyboardFocus==index);info.setCollectionItemInfo(AccessibilityNodeInfo.CollectionItemInfo.obtain(index/columns,1,index%columns,1,false));Rect box=bounds(index);info.setBoundsInParent(box);Rect viewport=new Rect();info.setVisibleToUser(isShown()&&getLocalVisibleRect(viewport)&&Rect.intersects(box,viewport));int[] origin=new int[2];getLocationOnScreen(origin);box.offset(origin[0],origin[1]);info.setBoundsInScreen(box);info.addAction(AccessibilityNodeInfo.ACTION_CLICK);info.addAction(accessibilityFocus==index?AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS:AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS);info.addAction(AccessibilityNodeInfo.ACTION_FOCUS);return info;
        }
        @Override public AccessibilityNodeInfo findFocus(int focus){int index=focus==AccessibilityNodeInfo.FOCUS_ACCESSIBILITY?accessibilityFocus:hasFocus()?keyboardFocus:-1;return index>=0?createAccessibilityNodeInfo(index+1):null;}
        @Override public boolean performAction(int id,int action,android.os.Bundle args){if(id==HOST_VIEW_ID)return ContactSheet.this.performAccessibilityAction(action,args);int index=id-1;if(index<0||index>=shots.size())return false;if(action==AccessibilityNodeInfo.ACTION_CLICK){openFrame(index);return true;}if(action==AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS){if(accessibilityFocus>=0&&accessibilityFocus!=index)announce(accessibilityFocus,AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED);accessibilityFocus=index;requestRectangleOnScreen(bounds(index),true);announce(index,AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED);invalidate();return true;}if(action==AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS&&accessibilityFocus==index){accessibilityFocus=-1;announce(index,AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED);invalidate();return true;}if(action==AccessibilityNodeInfo.ACTION_FOCUS){keyboardFocus=index;requestFocus();requestRectangleOnScreen(bounds(index),true);invalidate();return true;}return false;}
    };
    @Override public AccessibilityNodeProvider getAccessibilityNodeProvider(){return nodes;}
    @Override protected void onFocusChanged(boolean focused,int direction,Rect old){super.onFocusChanged(focused,direction,old);if(focused&&!shots.isEmpty()){if(direction==FOCUS_UP||direction==FOCUS_BACKWARD)keyboardFocus=shots.size()-1;else if(keyboardFocus<0)keyboardFocus=0;post(()->{if(hasFocus())requestRectangleOnScreen(bounds(keyboardFocus),true);});}invalidate();}
    @Override public void getFocusedRect(Rect rect){if(hasFocus()&&keyboardFocus>=0)rect.set(bounds(keyboardFocus));else super.getFocusedRect(rect);}
    @Override public boolean onKeyDown(int key,KeyEvent event){if(shots.isEmpty())return super.onKeyDown(key,event);int target=Math.max(0,keyboardFocus);switch(key){case KeyEvent.KEYCODE_DPAD_LEFT:target--;break;case KeyEvent.KEYCODE_DPAD_RIGHT:target++;break;case KeyEvent.KEYCODE_DPAD_UP:target-=columns;break;case KeyEvent.KEYCODE_DPAD_DOWN:target+=columns;break;case KeyEvent.KEYCODE_MOVE_HOME:target=0;break;case KeyEvent.KEYCODE_MOVE_END:target=shots.size()-1;break;case KeyEvent.KEYCODE_PAGE_UP:target-=columns*4;break;case KeyEvent.KEYCODE_PAGE_DOWN:target+=columns*4;break;case KeyEvent.KEYCODE_ENTER:case KeyEvent.KEYCODE_DPAD_CENTER:case KeyEvent.KEYCODE_SPACE:openFrame(target);return true;default:return super.onKeyDown(key,event);}keyboardFocus=Math.max(0,Math.min(shots.size()-1,target));requestRectangleOnScreen(bounds(keyboardFocus),true);invalidate();return true;}
    private int gap(){return Math.max(1,Math.round(2*getResources().getDisplayMetrics().density));}
    private float cell(){return getWidth()/(float)columns;}
    @Override protected void onMeasure(int width,int height){int w=MeasureSpec.getSize(width);if(film)columns=halfFrame?14:7;setMeasuredDimension(w,Math.round(rows(shots.size(),columns)*rowHeightForWidth(w)));}
    private float rowHeightForWidth(int width){if(!film)return width/(float)columns;float scale=width/(float)columns/(halfFrame?19f:38f);return Math.max(48*getResources().getDisplayMetrics().density,(halfFrame?18:36)*scale/filmAspect+11*scale+4*getResources().getDisplayMetrics().density+14*getResources().getDisplayMetrics().scaledDensity);}
    @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);float cell=cell();if(cell<=0||!getLocalVisibleRect(visible))return;if(film){drawFilm(canvas);return;}int first=Math.max(0,(int)(visible.top/rowHeight())*columns),last=Math.min(shots.size(),((int)(visible.bottom/rowHeight())+1)*columns);
        for(int i=first;i<last;i++){LogStore.Shot shot=shots.get(i);float x=i%columns*cell,y=i/columns*cell;RectF box=new RectF(x+gap()/2f,y+gap()/2f,x+cell-gap()/2f,y+cell-gap()/2f);paint.setColor(0xff30251e);canvas.drawRect(box,paint);Bitmap image=images.get(shot.id);
            if(image!=null){float side=Math.min(image.getWidth(),image.getHeight());Rect crop=new Rect(Math.round((image.getWidth()-side)/2),Math.round((image.getHeight()-side)/2),Math.round((image.getWidth()+side)/2),Math.round((image.getHeight()+side)/2));canvas.drawBitmap(image,crop,box,paint);}else request(shot.id);
            paint.setColor(0x99000000);float label=Math.min(cell/3,22*getResources().getDisplayMetrics().density);canvas.drawRect(box.left,box.bottom-label,box.right,box.bottom,paint);paint.setColor(0xfff4eee2);paint.setTextSize(label*.7f);paint.setTextAlign(Paint.Align.CENTER);canvas.drawText(String.valueOf(shot.frame),box.centerX(),box.bottom-label*.2f,paint);
            if(accessibilityFocus==i||(hasFocus()&&keyboardFocus==i)){paint.setColor(UiColors.color(getResources(),0xffef8249));paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Math.max(2,gap()));canvas.drawRect(box,paint);paint.setStyle(Paint.Style.FILL);}
        }
    }
    private void drawFilm(Canvas canvas){float cell=cell(),height=rowHeight(),scale=stripScale(),density=getResources().getDisplayMetrics().density;int first=Math.max(0,(int)(visible.top/height)*columns),last=Math.min(shots.size(),((int)(visible.bottom/height)+1)*columns);
        // One continuous substrate per row; only the image opening receives the selection stroke.
        for(int row=first/columns;row<rows(last,columns);row++){int count=Math.min(columns,shots.size()-row*columns);paint.setColor(0xff4b3023);canvas.drawRect(0,row*height,count*cell,row*height+filmHeight(),paint);}
        for(int i=first;i<last;i++){LogStore.Shot shot=shots.get(i);float x=i%columns*cell,y=i/columns*height;int layer=-1;if(i>=revealFrom&&reveal<1)layer=canvas.saveLayerAlpha(x,y,x+cell,y+height,Math.round(reveal*255));RectF photo=new RectF(x+(halfFrame?.5f:1)*scale,y+5.5f*scale,x+(halfFrame?18.5f:37)*scale,y+5.5f*scale+filmPhotoHeight());
            if(perforated){paint.setColor(UiColors.color(getResources(),0xff21170f));for(int hole=0;hole<(halfFrame?4:8);hole++){float left=x+(hole*4.75f+(4.75f-1.981f)/2)*scale;float top=y+2.01f*scale,bottom=y+filmHeight()-4.804f*scale;canvas.drawRoundRect(left,top,left+1.981f*scale,top+2.794f*scale,.51f*scale,.51f*scale,paint);canvas.drawRoundRect(left,bottom,left+1.981f*scale,bottom+2.794f*scale,.51f*scale,.51f*scale,paint);}}
            paint.setColor(0xff30251e);canvas.drawRect(photo,paint);Bitmap image=images.get(shot.id);
            if(image!=null){float ratio=photo.width()/photo.height();int w=image.getWidth(),h=image.getHeight(),cropW=Math.max(1,Math.min(w,Math.round(h*ratio))),cropH=Math.max(1,Math.min(h,Math.round(w/ratio)));Rect crop=new Rect((w-cropW)/2,(h-cropH)/2,(w+cropW)/2,(h+cropH)/2);canvas.drawBitmap(image,crop,photo,paint);}else request(shot.id);
            if(i==shots.size()-1||accessibilityFocus==i||(hasFocus()&&keyboardFocus==i)){paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(density);paint.setColor(UiColors.color(getResources(),0xffef8249));RectF selection=new RectF(photo);selection.inset(density/2,density/2);canvas.drawRect(selection,paint);paint.setStyle(Paint.Style.FILL);}paint.setColor(i==shots.size()-1?UiColors.color(getResources(),0xffef8249):UiColors.color(getResources(),0xffb4a99f));paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(11*getResources().getDisplayMetrics().scaledDensity);String label=String.format(Locale.ROOT,"%02d",shot.frame);float labelWidth=paint.measureText(label);if(labelWidth>cell-2*density)paint.setTextSize(paint.getTextSize()*(cell-2*density)/labelWidth);Paint.FontMetrics metrics=paint.getFontMetrics();canvas.drawText(label,x+cell/2,y+filmHeight()+4*density-metrics.top,paint);if(layer>=0)canvas.restoreToCount(layer);
        }
    }
    private void request(String id){File file=files.get(id);if(file==null||detached||pending.contains(id)||failed.contains(id))return;pending.add(id);worker.execute(()->{Bitmap decoded=null;try{if(!detached)decoded=ScanImages.read(file,160);}catch(Exception invalid){}final Bitmap ready=decoded;main.post(()->{pending.remove(id);if(detached||!isAttachedToWindow()){if(ready!=null)ready.recycle();return;}if(ready==null)failed.add(id);else images.put(id,ready);invalidate();});});}
    @Override public boolean onTouchEvent(MotionEvent event){if(event.getActionMasked()==MotionEvent.ACTION_DOWN){downX=event.getX();downY=event.getY();return true;}if(event.getActionMasked()==MotionEvent.ACTION_UP){float slop=ViewConfiguration.get(getContext()).getScaledTouchSlop();if(Math.abs(event.getX()-downX)<slop&&Math.abs(event.getY()-downY)<slop){int col=(int)(event.getX()/cell()),row=(int)(event.getY()/rowHeight()),index=row*columns+col;if(col>=0&&col<columns&&index>=0&&index<shots.size())openFrame(index);}return true;}return true;}
    @Override public boolean performClick(){super.performClick();return true;}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();getViewTreeObserver().addOnScrollChangedListener(scrolled);}
    @Override protected void onDetachedFromWindow(){getViewTreeObserver().removeOnScrollChangedListener(scrolled);detached=true;if(revealAnimator!=null)revealAnimator.cancel();worker.shutdownNow();images.evictAll();super.onDetachedFromWindow();}
}
