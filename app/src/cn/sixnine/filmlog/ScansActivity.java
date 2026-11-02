package cn.sixnine.filmlog;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.util.LruCache;
import androidx.exifinterface.media.ExifInterface;
import android.net.Uri;
import android.os.*;
import android.provider.OpenableColumns;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Scan association preview, based on the observed import workflow; Android layout still needs reference validation. */
public final class ScansActivity extends WorkspaceActivity {
    private static final int BG=0xff21170f,CARD=0xff30251e,INK=0xfff4eee2,ORANGE=0xffef8249;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final ExecutorService previewWorker=Executors.newFixedThreadPool(2);
    private final LruCache<String,Bitmap> previewCache=new LruCache<String,Bitmap>(4*1024*1024){protected int sizeOf(String key,Bitmap image){return image.getAllocationByteCount();}};
    private final ArrayList<Preview> previews=new ArrayList<>();
    private final Rect visiblePreview=new Rect();
    private void trimPreviews(){for(Preview preview:previews)if(!preview.getLocalVisibleRect(visiblePreview))preview.setImageDrawable(null);else if(preview.getDrawable()==null)preview.invalidate();}
    private final class Preview extends ImageView {
        final File path;final Uri uri;final String key;boolean loading,failed;Future<?> task;
        Preview(ScanStore.Scan scan){super(ScansActivity.this);path=scans.file(scan.id);uri=pending?ImportQueue.previewUri(logs,roll,scan.id):null;key=uri==null?path.getAbsolutePath():uri.toString();}
        @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);if(getDrawable()!=null||loading||failed||isDestroyed()||!getLocalVisibleRect(new Rect()))return;Bitmap cached=previewCache.get(key);if(cached!=null){setImageBitmap(cached);return;}loading=true;
            task=previewWorker.submit(()->{Bitmap image=null;try{if(!Thread.currentThread().isInterrupted())image=uri==null?ScanImages.read(path,600):ScanImages.read(getApplicationContext(),uri,600);}catch(Exception ignored){}final Bitmap ready=image;refresh.post(()->{loading=false;if(isDestroyed()||!isAttachedToWindow()){if(ready!=null)ready.recycle();return;}failed=ready==null;if(ready!=null){previewCache.put(key,ready);if(getLocalVisibleRect(new Rect()))setImageBitmap(ready);}else setContentDescription("扫描文件不可读取");});});
        }
        @Override protected void onAttachedToWindow(){super.onAttachedToWindow();previews.add(this);}
        @Override protected void onDetachedFromWindow(){previews.remove(this);if(task!=null)task.cancel(false);loading=false;setImageDrawable(null);super.onDetachedFromWindow();}
    }
    private final LinkedHashMap<String,String> links=new LinkedHashMap<>();
    private static final class Job {volatile boolean done;volatile String message;volatile int albumCount=-1;volatile boolean albumOperation;}
    private boolean albumPrompt,albumGps;private int completedAlbum=-1;private Job job;private final Handler refresh=new Handler(Looper.getMainLooper());
    private final Runnable poll=new Runnable(){public void run(){if(isDestroyed())return;if(job!=null&&job.done){busy=false;String message=job.message;int count=job.albumCount;job=null;if(!dirty)links.clear();render();if(count>=0){completedAlbum=count;albumFinished();}else toast(message);}else if(job!=null)refresh.postDelayed(this,250);}};
    private LogStore logs;private ScanStore scans;private String roll,exportScan="";private boolean grid,byName,busy,includeGps,dirty,trash,pending;private String replacementId="";
    private LinearLayout root,content;private AbsListView collection;private ArrayList<ScanStore.Scan> items;private ArrayList<LogStore.Shot> shots;
    @Override public void onCreate(Bundle state){super.onCreate(state);roll=getIntent().getStringExtra("roll");logs=new LogStore(this);
        try{logs.roll(roll);scans=new ScanStore(this,logs);}catch(Exception e){Toast.makeText(this,"无法打开扫描，已有记录未更改",1).show();finish();return;}
        if(state!=null){albumPrompt=state.getBoolean("albumPrompt");albumGps=state.getBoolean("albumGps");completedAlbum=state.getInt("completedAlbum",-1);trash=state.getBoolean("trash");replacementId=state.getString("replacement","");grid=state.getBoolean("grid");byName=state.getBoolean("byName");exportScan=state.getString("exportScan","");includeGps=state.getBoolean("gps");dirty=state.getBoolean("dirty");Bundle map=state.getBundle("links");if(map!=null)for(String key:map.keySet())links.put(key,map.getString(key,""));}
        job=(Job)getLastNonConfigurationInstance();busy=job!=null&&!job.done;
        render();
        if(completedAlbum>=0)albumFinished();else if(albumPrompt||(state==null&&getIntent().getBooleanExtra("export_album",false)))album();
        if(job==null&&ImportQueue.count(logs,roll,"'queued','running'")>0)importSources(null,false);
        if(state!=null&&state.getBoolean("busy")&&job==null)toast(state.getBoolean("albumWork")?"上次相册保存已中断，请核对相册后重试。":"上次文件处理已中断；已完成导入保留，请核对后补选未导入文件。");
    }
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;}
    private TextView label(String value,int size){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(UiColors.color(getResources(),INK));t.setPadding(dp(6),dp(6),dp(6),dp(6));return t;}
    private Button button(String name,Runnable run){Button b=UiSpacing.button(this);b.setText(name);b.setAllCaps(false);b.setTextColor(UiColors.color(getResources(),ORANGE));b.setBackgroundTintList(null);GradientDrawable bg=new GradientDrawable();bg.setColor(UiColors.color(getResources(),CARD));bg.setCornerRadius(dp(12));b.setBackground(bg);b.setMinHeight(dp(44));b.setOnClickListener(v->{if(!busy)run.run();});return b;}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private void render(){previews.clear();
        pending=!trash&&ImportQueue.count(logs,roll,"'preview'")>0;
        items=pending?ImportQueue.previews(logs,roll):scans.list(roll,trash);shots=logs.shots(roll);shots.sort(Comparator.comparingInt(s->s.frame));
        if(!trash){HashSet<String> live=new HashSet<>();for(ScanStore.Scan s:items){live.add(s.id);if(pending||!links.containsKey(s.id))links.put(s.id,s.shotId);}links.keySet().retainAll(live);}
        if(byName)items.sort(Comparator.comparing((ScanStore.Scan s)->s.name,String.CASE_INSENSITIVE_ORDER).thenComparing(s->s.id));
        root=column();root.setClipToPadding(false);root.setPadding(dp(14),0,dp(14),0);root.setBackgroundColor(UiColors.color(getResources(),BG));setContentView(root);
        root.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(dp(14)+i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),dp(14)+i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());return i;});root.requestApplyInsets();
        Button change=LibraryUi.icon(this,0xe3d4,"添加扫描",()->{replacementId="";pick();});change.setVisibility(trash?View.INVISIBLE:View.VISIBLE);root.addView(LibraryUi.header(this,trash?"最近删除":pending?"导入预览":"扫描",this::onBackPressed,change,14));

        root.addView(label(logs.roll(roll).name+" · "+items.size()+" 个扫描 · "+shots.size()+" 张曝光",14));
        
        LinearLayout tools=new LinearLayout(this);tools.addView(button(byName?"文件名 ✓":"选择顺序 ✓",()->{byName=!byName;render();}),new LinearLayout.LayoutParams(0,-2,1));tools.addView(button(grid?"网格 ✓":"列表 ✓",()->{grid=!grid;render();}),new LinearLayout.LayoutParams(0,-2,1));tools.addView(button("更多",()->scanMenu()),new LinearLayout.LayoutParams(0,-2,1));root.addView(tools);
        if(busy&&job!=null&&job.albumOperation){root.addView(label("正在保存到手机相册…",14));ProgressBar progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setIndeterminate(true);progress.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(UiColors.color(getResources(),ORANGE)));root.addView(progress,new LinearLayout.LayoutParams(-1,dp(6)));}
        root.addView(label("扫描只保存在本机。请保留原始 JPEG；导入不会修改原图。",12));
        int failures=ImportQueue.count(logs,roll,"'failed'");if(failures>0)root.addView(button("重试未完成导入（"+failures+"）",()->new AlertDialog.Builder(this).setTitle("未完成的导入").setMessage(ImportQueue.errors(logs,roll)+"\n若文件权限失效或原件移动，请重新选择原件。替换失败时新副本可能已作为未关联扫描保留。").setNegativeButton("关闭",null).setNeutralButton("取消失败任务",(d,w)->{ImportQueue.dismissFailures(this,logs,roll);render();toast("失败任务已取消，已导入扫描保留，可重新选择原件");}).setPositiveButton("重试",(d,w)->importSources(null,true)).show()));
        final ArrayList<ScanStore.Scan> pageItems=new ArrayList<>(items);
        if(items.isEmpty()){collection=null;ScrollView scroll=new ScrollView(this);content=column();content.addView(label(trash?"最近删除为空。移除或替换的扫描副本会保留在这里。":"还没有扫描文件。选择 JPEG 后先预览并选择对应曝光，确认后导入。",16));scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));}
        else{
            if(grid){GridView tiles=new GridView(this);tiles.setNumColumns(2);collection=tiles;}else{ListView list=new ListView(this);list.setDivider(null);list.setDividerHeight(0);list.setItemsCanFocus(true);collection=list;}
            collection.setSelector(android.R.color.transparent);collection.setCacheColorHint(Color.TRANSPARENT);collection.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
            collection.setOnScrollListener(new AbsListView.OnScrollListener(){public void onScrollStateChanged(AbsListView view,int state){}public void onScroll(AbsListView view,int first,int shown,int total){trimPreviews();}});
            collection.setAdapter(new BaseAdapter(){public int getCount(){return pageItems.size();}public Object getItem(int position){return pageItems.get(position);}public long getItemId(int position){return position;}public View getView(int position,View recycled,ViewGroup parent){return scanCard(pageItems.get(position));}});
            root.addView(collection,new LinearLayout.LayoutParams(-1,0,1));
        }
        if(!trash&&!pending){root.addView(button(busy?"正在处理…":"＋ 导入扫描",()->{replacementId="";pick();}));Button save=button(dirty?"保存关联 · 有未保存修改":"保存关联",()->{try{scans.associate(roll,new LinkedHashMap<>(links));dirty=false;render();toast("关联已保存");}catch(Exception e){toast("未保存："+e.getMessage());}});save.setTextColor(UiColors.onAccent(getResources()));GradientDrawable primary=new GradientDrawable();primary.setColor(UiColors.color(getResources(),ORANGE));primary.setCornerRadius(dp(12));save.setBackground(primary);root.addView(save);}
        if(pending){root.addView(label("选择对应曝光后确认导入；预览阶段不会改动已有扫描和曝光。",12));
            Button commit=button(busy?"正在处理…":"确认导入 · "+items.size()+" 张",()->{try{ImportQueue.savePreview(logs,roll,links);ImportQueue.confirmPreview(logs,roll);dirty=false;links.clear();importSources(null,false);}catch(Exception e){toast("尚未导入："+e.getMessage());}});
            commit.setTextColor(UiColors.onAccent(getResources()));GradientDrawable primary=new GradientDrawable();primary.setColor(UiColors.color(getResources(),ORANGE));primary.setCornerRadius(dp(12));commit.setBackground(primary);root.addView(commit);
            root.addView(button("取消本次导入",()->cancelPreview()));}

    }
    private View scanCard(ScanStore.Scan item){
        LinearLayout card=column();GradientDrawable bg=new GradientDrawable();bg.setColor(UiColors.color(getResources(),CARD));bg.setCornerRadius(dp(12));card.setBackground(bg);card.setPadding(dp(6),dp(6),dp(6),dp(6));
            Preview preview=new Preview(item);preview.setScaleType(ImageView.ScaleType.FIT_CENTER);preview.setContentDescription("扫描预览 "+item.name);preview.setOnClickListener(v->{if(!busy)startActivity(new Intent(this,ScanViewerActivity.class).putExtra("scan",item.id).putExtra("preview_roll",pending?roll:"").putExtra("viewer_roll",roll).putExtra("by_name",byName).putExtra("trash",trash));});
            LinearLayout info=column();TextView name=label(item.name,13);name.setMaxLines(2);info.addView(name);
            if(trash)info.addView(button("恢复扫描",()->{try{boolean restored=scans.restore(roll,item.id);render();toast(restored?"已恢复扫描和原关联":"已恢复扫描；原曝光已占用或无原关联，请重新选择");}catch(Exception e){toast(e.getMessage());}}));
            else{Button target=button(association(item.id)+"  ›",()->chooseShot(item));target.setTextSize(13);target.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);target.setPadding(dp(6),0,dp(6),0);info.addView(target,new LinearLayout.LayoutParams(-1,dp(36)));}
            if(grid){card.addView(preview,new LinearLayout.LayoutParams(-1,dp(120)));card.addView(info);}else{LinearLayout scanRow=new LinearLayout(this);scanRow.setGravity(Gravity.CENTER_VERTICAL);scanRow.addView(preview,new LinearLayout.LayoutParams(dp(52),dp(64)));scanRow.addView(info,new LinearLayout.LayoutParams(0,-2,1));if(!trash)scanRow.addView(button("⋯",()->{if(pending)removePreview(item);else itemMenu(item);}),new LinearLayout.LayoutParams(dp(44),dp(48)));card.addView(scanRow);}
            if(grid&&!trash)card.addView(button("扫描操作",()->{if(pending)removePreview(item);else itemMenu(item);}));
        FrameLayout holder=new FrameLayout(this);FrameLayout.LayoutParams size=new FrameLayout.LayoutParams(-1,-2);size.setMargins(dp(3),dp(3),dp(3),dp(5));holder.addView(card,size);return holder;
    }
    private void scanMenu(){if(pending){new AlertDialog.Builder(this).setItems(new String[]{"按当前顺序建议关联","取消本次导入"},(d,w)->{if(w==0)proposeAssociations();else cancelPreview();}).show();return;}new AlertDialog.Builder(this).setItems(new String[]{"整卷扫描 ZIP","接触印样 PDF","保存整卷到相册",trash?"返回扫描":"最近删除","按当前顺序建议关联","制作分享图"},(d,w)->{if(busy)return;if(dirty&&w!=4){toast("请先保存关联");return;}if(w<2)collection(w==0?12:13);else if(w==2)album();else if(w==3){trash=!trash;render();}else if(w==5){if(trash)toast("请先返回有效扫描列表");else startActivity(new Intent(this,SharePosterActivity.class).putExtra("roll",roll));}else if(!trash)proposeAssociations();}).show();}
    private void proposeAssociations(){new AlertDialog.Builder(this).setTitle("重新排列关联预览？").setMessage("按当前文件顺序对应从第 1 张开始的已有曝光。多余文件保持未关联；确认保存前可逐张调整。").setNegativeButton("取消",null).setPositiveButton("生成预览",(d,w)->{if(busy)return;for(int i=0;i<items.size();i++)links.put(items.get(i).id,pending&&!ImportQueue.replacement(logs,roll,items.get(i).id).isEmpty()?"":i<shots.size()?shots.get(i).id:"");if(pending)ImportQueue.savePreview(logs,roll,links);else dirty=true;render();}).show();}
    private void itemMenu(ScanStore.Scan item){new AlertDialog.Builder(this).setTitle(item.name).setItems(new String[]{"导出扫描","替换扫描","移入最近删除"},(d,w)->{if(busy)return;if(w==0)export(item);else if(w==2)remove(item);else{if(dirty){toast("请先保存关联");return;}replacementId=item.id;pick();}}).show();}
    private String association(String id){if(pending&&!ImportQueue.replacement(logs,roll,id).isEmpty())return "替换扫描 · 保留原关联";String shot=links.get(id);for(LogStore.Shot s:shots)if(s.id.equals(shot))return "曝光 "+s.frame;return "未关联 · 选择曝光";}
    private void chooseShot(ScanStore.Scan scan){if(pending&&!ImportQueue.replacement(logs,roll,scan.id).isEmpty()){toast("确认导入后替换原扫描，保留其曝光关联；原副本进入最近删除");return;}String[] names=new String[shots.size()+1];names[0]="未关联";for(int i=0;i<shots.size();i++)names[i+1]="曝光 "+shots.get(i).frame;new AlertDialog.Builder(this).setTitle(scan.name).setItems(names,(d,w)->{links.put(scan.id,w==0?"":shots.get(w-1).id);if(pending)ImportQueue.savePreview(logs,roll,links);else dirty=true;render();}).show();}

    private void pick(){if(dirty&&!pending){toast("请先保存关联，再添加扫描");return;}Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/jpeg").addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,replacementId.isEmpty());try{startActivityForResult(i,10);}catch(ActivityNotFoundException e){toast("没有可用的文件选择器");}}
    private void cancelPreview(){new AlertDialog.Builder(this).setTitle("取消本次导入？").setMessage("所选文件及对应曝光的预览将放弃，已有扫描和原始照片保持不变。").setNegativeButton("继续预览",null).setPositiveButton("取消导入",(d,w)->{ImportQueue.cancelPreview(this,logs,roll);links.clear();dirty=false;render();}).show();}
    private void removePreview(ScanStore.Scan item){new AlertDialog.Builder(this).setTitle(item.name).setItems(new String[]{"从本次导入中移除"},(d,w)->{ImportQueue.removePreview(this,logs,roll,item.id);links.remove(item.id);render();}).show();}
    private void remove(ScanStore.Scan item){new AlertDialog.Builder(this).setTitle("移除这张扫描？").setMessage("扫描副本移入最近删除，可以恢复。曝光记录及原始文件保留。").setNegativeButton("取消",null).setPositiveButton("移除",(d,w)->{try{scans.remove(roll,item.id);links.remove(item.id);render();}catch(Exception e){toast("移除失败，重新打开后核对");}}).show();}
    private void export(ScanStore.Scan scan){if(dirty){toast("请先保存关联，再导出");return;}if(scan.shotId.isEmpty()){toast("请先关联一张曝光");return;}
        final boolean[] gps={false};new AlertDialog.Builder(this).setTitle("导出扫描照片").setMultiChoiceItems(new String[]{"包含 GPS 坐标"},gps,(d,w,checked)->gps[w]=checked).setNegativeButton("取消",null).setPositiveButton("选择保存位置",(d,w)->{includeGps=gps[0];exportScan=scan.id;Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("image/jpeg").addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_TITLE,"曝光-"+logs.shot(scan.shotId).frame+".jpg");try{startActivityForResult(i,11);}catch(ActivityNotFoundException e){toast("没有可用的文件保存器");}}).show();
    }
    private void stageSources(ArrayList<Uri> sources){
        final Context app=getApplicationContext();final String selectedRoll=roll,replace=replacementId;replacementId="";busy=true;job=new Job();final Job running=job;refresh.post(poll);render();
        worker.execute(()->{String message;try(LogStore db=new LogStore(app)){
            for(Uri uri:sources)try{app.getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(SecurityException ignored){}
            ImportQueue.preview(app,db,selectedRoll,sources,replace);message="已建立导入预览，请选择对应曝光后确认导入";
        }catch(Exception e){message="无法建立预览："+e.getMessage();}running.message=message;running.done=true;});
    }
    private void importSources(ArrayList<Uri> sources,boolean retry){final Context app=getApplicationContext();final String selectedRoll=roll,replace=replacementId;replacementId="";busy=true;job=new Job();final Job running=job;refresh.post(poll);render();worker.execute(()->{String message;try(LogStore db=new LogStore(app)){if(sources!=null)ImportQueue.enqueue(app,db,selectedRoll,sources,replace);message=ImportQueue.run(app,db,selectedRoll,retry);}catch(Exception e){message="导入未完成："+e.getMessage();}running.message=message;running.done=true;});}
    private void collection(int request){if(dirty){toast("请先保存关联，再导出");return;}final boolean[] gps={false};AlertDialog.Builder dialog=new AlertDialog.Builder(this).setTitle(request==12?"导出全部已关联扫描":"生成整卷接触印样");
        if(request==12)dialog.setMultiChoiceItems(new String[]{"包含 GPS 坐标"},gps,(d,w,checked)->gps[w]=checked);else dialog.setMessage("包含所有曝光、缩略图和偏移后的拍摄时间，不含坐标。未关联的曝光保留空位。");
        dialog.setNegativeButton("取消",null).setPositiveButton("选择保存位置",(d,w)->{includeGps=gps[0];Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(request==12?"application/zip":"application/pdf");i.putExtra(Intent.EXTRA_TITLE,request==12?"整卷扫描.zip":"接触印样.pdf");try{startActivityForResult(i,request);}catch(ActivityNotFoundException e){toast("没有可用的保存器");}}).show();
    }
    private void bottom(AlertDialog dialog){dialog.show();Window window=dialog.getWindow();if(window!=null){window.setGravity(Gravity.BOTTOM);window.setLayout(-1,-2);}}
    private void primary(Button button){button.setTextColor(UiColors.onAccent(getResources()));GradientDrawable background=new GradientDrawable();background.setColor(UiColors.color(getResources(),ORANGE));background.setCornerRadius(dp(12));button.setBackground(background);}
    private void album(){
        if(busy||dirty||pending){albumPrompt=false;toast(pending?"请先完成或取消导入预览":busy?"请等待文件处理完成":"请先保存关联，再导出");return;}
        int count=0;for(ScanStore.Scan scan:scans.list(roll))if(!scan.shotId.isEmpty())count++;if(count==0){toast("请先导入并关联扫描，再导出到相册");return;}
        if(Build.VERSION.SDK_INT<29){toast("Android 8–9 请使用整卷 ZIP，再解压到手机相册目录");collection(12);return;}
        if(!albumPrompt)albumGps=false;albumPrompt=true;final int total=count;LinearLayout body=column();body.setPadding(dp(16),dp(6),dp(16),dp(12));body.addView(label(count+" 张扫描 · "+logs.roll(roll).stock,14));Switch location=new Switch(this);location.setText("包含 GPS 坐标");location.setTextColor(UiColors.color(getResources(),INK));location.setTextSize(16);location.setMinHeight(dp(48));location.setChecked(albumGps);body.addView(location,new LinearLayout.LayoutParams(-1,-2));TextView warning=label("分享这些照片时，也会分享拍摄地点。",13);warning.setTextColor(UiColors.color(getResources(),ORANGE));warning.setVisibility(albumGps?View.VISIBLE:View.GONE);body.addView(warning);location.setOnCheckedChangeListener((v,checked)->{albumGps=checked;warning.setVisibility(checked?View.VISIBLE:View.GONE);});
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("导出到手机相册").setView(body).setNegativeButton("取消",null).create();Button save=button("导出 "+total+" 张扫描",()->{final boolean gps=location.isChecked();dialog.dismiss();startAlbum(gps);});primary(save);body.addView(save,new LinearLayout.LayoutParams(-1,dp(48)));dialog.setOnDismissListener(d->albumPrompt=false);bottom(dialog);
    }
    private void startAlbum(boolean gps){
        final Context app=getApplicationContext();final String selectedRoll=roll;busy=true;job=new Job();job.albumOperation=true;final Job running=job;refresh.post(poll);render();
        worker.execute(()->{try(LogStore db=new LogStore(app)){int count=AlbumOutput.save(app,db,selectedRoll,gps);running.albumCount=count;running.message="已保存 "+count+" 张照片到相册：胶片随记";}catch(Exception error){running.message="相册导出未完成："+error.getMessage();}running.done=true;});
    }
    private void albumFinished(){
        LinearLayout body=column();body.setPadding(dp(16),dp(6),dp(16),dp(12));body.addView(label(completedAlbum+" 张扫描已导出",22));body.addView(label("已保存到手机相册：胶片随记",14));Button photos=button("打开相册",()->{try{startActivity(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN,Intent.CATEGORY_APP_GALLERY));}catch(ActivityNotFoundException unavailable){toast("没有可用相册应用，照片已保存在 Pictures/胶片随记");}});primary(photos);body.addView(photos,new LinearLayout.LayoutParams(-1,dp(48)));AlertDialog dialog=new AlertDialog.Builder(this).setTitle("导出完成").setView(body).setNegativeButton("完成",null).create();dialog.setOnDismissListener(d->completedAlbum=-1);bottom(dialog);
    }
    private void writeCollection(int request,Uri uri){final Context app=getApplicationContext();final String selectedRoll=roll;final boolean gps=includeGps;busy=true;job=new Job();final Job running=job;refresh.post(poll);render();worker.execute(()->{String message;try(LogStore db=new LogStore(app);OutputStream out=app.getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new IOException("无法写入目标文件");int count=request==12?RollOutput.zip(app,db,selectedRoll,gps,out):RollOutput.contactSheet(app,db,selectedRoll,out);message="导出完成，共 "+count+(request==12?" 个已关联扫描":" 张曝光");}catch(Exception e){message="导出失败："+e.getMessage()+"。目标可能有不完整文件，请核对后重试。";}running.message=message;running.done=true;});}
    @Override protected void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data);if(result!=RESULT_OK||data==null)return;
        if((req==12||req==13)&&data.getData()!=null){writeCollection(req,data.getData());return;}
        if(req==10){ArrayList<Uri> uris=new ArrayList<>();if(data.getClipData()!=null){for(int i=0;i<data.getClipData().getItemCount();i++)uris.add(data.getClipData().getItemAt(i).getUri());}else if(data.getData()!=null)uris.add(data.getData());if(uris.isEmpty())return;
            stageSources(uris);
        }else if(req==11&&data.getData()!=null){final Uri destination=data.getData();final String id=exportScan;final boolean gps=includeGps;final Context app=getApplicationContext();final String selectedRoll=roll;busy=true;job=new Job();final Job running=job;refresh.post(poll);render();worker.execute(()->{String message;File output=null;try(LogStore db=new LogStore(app)){ScanStore files=new ScanStore(app,db);ScanStore.Scan chosen=null;for(ScanStore.Scan s:files.list(selectedRoll))if(s.id.equals(id))chosen=s;if(chosen==null||chosen.shotId.isEmpty())throw new IOException("关联已变化");output=ScanExport.create(app,files.file(id),db.roll(selectedRoll),db.shot(chosen.shotId),gps);try(InputStream in=new FileInputStream(output);OutputStream out=app.getContentResolver().openOutputStream(destination,"wt")){if(out==null)throw new IOException("无法写入");byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}message="已导出独立照片，"+(gps?"包含记录的 GPS（如有）":"不包含 GPS");}catch(Exception e){message="导出失败，目标位置可能有不完整文件，请核对后重试";}finally{if(output!=null)output.delete();}running.message=message;running.done=true;});}
    }
    @Override public void onBackPressed(){if(busy){toast("正在处理文件，请等待完成");return;}if(pending){new AlertDialog.Builder(this).setTitle("保留导入预览？").setMessage("稍后进入这卷扫描时可继续。未确认前不会导入或更改已有扫描。").setNegativeButton("继续预览",null).setNeutralButton("放弃预览",(d,w)->{ImportQueue.cancelPreview(this,logs,roll);finish();}).setPositiveButton("保留并返回",(d,w)->finish()).show();return;}if(dirty)new AlertDialog.Builder(this).setTitle("放弃尚未保存的关联？").setMessage("已导入文件保留；只放弃关联预览的调整。").setNegativeButton("继续编辑",null).setPositiveButton("放弃",(d,w)->finish()).show();else super.onBackPressed();}
    @Override protected void onSaveInstanceState(Bundle out){out.putBoolean("albumWork",job!=null&&job.albumOperation);out.putBoolean("albumPrompt",albumPrompt);out.putBoolean("albumGps",albumGps);out.putInt("completedAlbum",completedAlbum);out.putBoolean("trash",trash);out.putString("replacement",replacementId);out.putBoolean("busy",busy);out.putBoolean("grid",grid);out.putBoolean("byName",byName);out.putBoolean("dirty",dirty);out.putBoolean("gps",includeGps);out.putString("exportScan",exportScan);Bundle map=new Bundle();for(Map.Entry<String,String> e:links.entrySet())map.putString(e.getKey(),e.getValue());out.putBundle("links",map);super.onSaveInstanceState(out);}
    @Override public Object onRetainNonConfigurationInstance(){return job;}
    @Override protected void onResume(){super.onResume();if(job!=null)refresh.post(poll);}
    @Override protected void onPause(){refresh.removeCallbacks(poll);super.onPause();}
    @Override protected void onDestroy(){refresh.removeCallbacksAndMessages(null);worker.shutdown();previewWorker.shutdownNow();previewCache.evictAll();previews.clear();if(logs!=null)logs.close();super.onDestroy();}
}
