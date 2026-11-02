package cn.sixnine.filmlog;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.widget.*;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import java.io.*;
import java.time.LocalDate;
import org.json.*;
import android.view.*;
import android.text.InputType;

public final class BackupActivity extends WorkspaceActivity {
    private static class State {boolean busy,disposed;String message="";FilmBackup.Prepared prepared;CsvImport.Preview csv;BackupActivity activity;JSONObject review;}
    private State state;
    private Runnable captureReview;
    private final Handler handler=new Handler(Looper.getMainLooper());
    @Override public void onCreate(Bundle saved){super.onCreate(saved);state=(State)getLastNonConfigurationInstance();if(state==null){state=new State();try{CsvDraft.Saved draft=CsvDraft.load(this);if(draft!=null){state.csv=draft.preview;state.prepared=state.csv.data;state.review=draft.review;state.message="已恢复未确认的导入预览，已有资料未更改。";}}catch(Exception invalid){state.message=invalid.getMessage();CsvDraft.clear(this);}}state.activity=this;render();if(state.review!=null)try{reviewCsv(state.review.getInt("index"));}catch(Exception invalid){state.review=null;}if(saved==null&&state.prepared==null&&state.csv==null){String operation=getIntent().getStringExtra("operation");if("export".equals(operation))pick(true);else if("restore".equals(operation))pick(false);else if("csv".equals(operation))pickCsv();}}
    private void render(){LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setClipToPadding(false);int pad=(int)(20*getResources().getDisplayMetrics().density);root.setPadding(pad,pad,pad,pad);root.setBackgroundColor(UiColors.color(getResources(),0xff1f1612));ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.addView(root);setContentView(scroll);
        root.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(pad+i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),pad+i.getSystemWindowInsetRight(),pad+i.getSystemWindowInsetBottom());return i;});root.requestApplyInsets();
        root.addView(LibraryUi.header(this,state.csv!=null&&state.prepared!=null?"导入胶卷":"备份与恢复",this::onBackPressed,null,20));
        if(state.csv!=null&&state.prepared!=null){renderCsv(root);return;}

        TextView info=new TextView(this);info.setText("备份包含全部胶卷、曝光、参考照片、扫描原始副本及最近删除，以及自定义片种、曝光预设、封面关联、库存流水、器材和项目。文件内有拍摄时间与坐标，请保存在可信位置。\n\n恢复会追加为新资料，保留本机已有数据。重复恢复会产生重复胶卷、库存批次、器材和项目。当前应用设置保持不变；未完成的导入任务不包含在备份中。\n\n"+(state.busy?"正在处理，请勿清除应用数据…":state.message));info.setTextSize(16);info.setTextColor(UiColors.color(getResources(),0xfff4eee2));root.addView(info);
        add(root,"导出完整记录和扫描备份",()->pick(true));add(root,"选择备份并校验",()->pick(false));add(root,"导入 CSV / TSV / TXT",()->pickCsv());
        if(state.prepared!=null){try{TextView counts=new TextView(this);counts.setText("已校验："+state.prepared.count("rolls")+" 卷 / "+state.prepared.count("shots")+" 张曝光 / "+state.prepared.count("scans")+" 个扫描（含最近删除） / "+state.prepared.count("reference_photos")+" 张参考照片 / "+state.prepared.count("film_batches")+" 个库存批次 / "+state.prepared.count("catalog_entries")+" 个器材或项目 / "+state.prepared.count("film_stocks")+" 个自定义片种 / "+state.prepared.count("exposure_presets")+" 个曝光预设 / "+state.prepared.count("artwork_links")+" 个封面关联");counts.setTextColor(UiColors.color(getResources(),0xfff4eee2));root.addView(counts);}catch(Exception e){state.message=e.getMessage();}
            add(root,"确认追加导入资料",()->restore());add(root,"取消本次恢复",()->{state.prepared.close();state.prepared=null;state.message="已取消本次恢复，已有资料未更改。";render();});}

    }
    // Full ZIP backup is local functionality; button styling follows the original import DSButton.medium.
    private void add(LinearLayout root,String label,Runnable task){
        boolean primary=label.equals("导出完整记录和扫描备份")||label.equals("确认追加导入资料")||label.equals("确认导入胶卷");
        float density=getResources().getDisplayMetrics().density;
        Button b=UiSpacing.button(this);b.setText(label);b.setTextSize(15);b.setTypeface(Build.VERSION.SDK_INT>=28?Typeface.create(Typeface.create("sans-serif",Typeface.NORMAL),600,false):Typeface.create("sans-serif-medium",Typeface.NORMAL));b.setAllCaps(false);
        b.setMinHeight((int)(48*density));b.setMinimumHeight((int)(48*density));b.setPadding((int)(16*density),(int)(12*density),(int)(16*density),(int)(12*density));
        GradientDrawable background=primary?new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{UiColors.color(getResources(),0xfff07f4a),UiColors.color(getResources(),0xffd35f2e)}):new GradientDrawable();
        if(!primary)background.setColor(android.graphics.Color.TRANSPARENT);background.setCornerRadius(16*density);
        b.setBackgroundTintList(null);b.setBackground(background);b.setTextColor(primary?UiColors.color(getResources(),0xff1a0f00):UiColors.color(getResources(),0xfff07f4a));
        if(state.busy){android.graphics.ColorMatrix gray=new android.graphics.ColorMatrix();gray.setSaturation(0);background.setColorFilter(new android.graphics.ColorMatrixColorFilter(gray));b.setAlpha(.5f);}
        b.setEnabled(!state.busy);b.setOnClickListener(v->task.run());
        LinearLayout.LayoutParams layout=new LinearLayout.LayoutParams(-1,-2);layout.topMargin=(int)(12*density);root.addView(b,layout);
    }
    private TextView words(LinearLayout root,String text,int size){TextView view=new TextView(this);view.setText(text);view.setTextSize(size);view.setTextColor(UiColors.color(getResources(),0xfff2eadf));root.addView(view);return view;}
    private void renderCsv(LinearLayout root){
        words(root,"支持 UTF-8 CSV 或 TSV/TXT：胶片随记和 Pellica 导出列名。每批最多32个文件；先检查胶卷，确认后追加。照片需单独导入。",15);
        words(root,state.busy?"正在处理…":state.message,13);String notice=state.prepared.manifest.optString("import_notice","");if(!notice.isEmpty())words(root,notice,13);
        try{
            JSONArray rolls=state.prepared.manifest.getJSONArray("rolls"),shots=state.prepared.manifest.getJSONArray("shots");
            java.util.Map<String,Integer> counts=new java.util.HashMap<>();java.util.Map<String,JSONObject> first=new java.util.HashMap<>(),last=new java.util.HashMap<>();for(int i=0;i<shots.length();i++){JSONObject shot=shots.getJSONObject(i);String id=shot.getString("roll_id");counts.put(id,counts.getOrDefault(id,0)+1);if(!first.containsKey(id)||shot.getLong("captured_at")<first.get(id).getLong("captured_at"))first.put(id,shot);if(!last.containsKey(id)||shot.getLong("captured_at")>last.get(id).getLong("captured_at"))last.put(id,shot);}
            LinearLayout rows=new LinearLayout(this);rows.setOrientation(1);GradientDrawable panel=new GradientDrawable();panel.setColor(UiColors.color(getResources(),0xff261d18));panel.setCornerRadius(18*getResources().getDisplayMetrics().density);rows.setBackground(panel);root.addView(rows);
            for(int i=0;i<rolls.length();i++){final int index=i;JSONObject roll=rolls.getJSONObject(i);String stock=roll.getString("stock");
                add(rows,roll.getString("name")+"\n"+(stock.trim().isEmpty()?"缺少胶卷":stock)+" · ISO "+roll.getInt("iso")+"\n"+roll.getString("camera")+" · "+counts.getOrDefault(roll.getString("id"),0)+" 张曝光 · 检查"+(first.containsKey(roll.getString("id"))?"\n"+csvTime(first.get(roll.getString("id")))+(first.get(roll.getString("id"))==last.get(roll.getString("id"))?"":" 至 "+csvTime(last.get(roll.getString("id")))):""),()->reviewCsv(index));
                Button row=(Button)rows.getChildAt(rows.getChildCount()-1);row.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);row.setTextColor(UiColors.color(getResources(),0xfff2eadf));
            }
            add(root,"确认导入胶卷",()->restore());View confirm=root.getChildAt(root.getChildCount()-1);boolean enabled=!state.busy&&CsvImport.ready(state.csv);confirm.setEnabled(enabled);confirm.setAlpha(enabled?1f:.5f);if(!enabled){android.graphics.ColorMatrix gray=new android.graphics.ColorMatrix();gray.setSaturation(0);confirm.getBackground().setColorFilter(new android.graphics.ColorMatrixColorFilter(gray));}
        }catch(Exception invalid){words(root,"无法显示预览："+invalid.getMessage(),15);}
        add(root,"选择其他文件",()->pickCsv());
        add(root,"取消本次导入",()->{CsvDraft.clear(this);state.review=null;captureReview=null;state.prepared.close();state.prepared=null;state.csv=null;state.message="已取消本次导入，已有资料未更改。";render();});
    }
    private static String csvTime(JSONObject shot)throws JSONException{return java.time.Instant.ofEpochMilli(shot.getLong("captured_at")).atOffset(java.time.ZoneOffset.ofTotalSeconds(shot.getInt("zone_offset_minutes")*60)).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss XXX"));}
    private EditText reviewField(LinearLayout root,String name,String value,boolean numeric){
        words(root,name,13);EditText input=new EditText(this);input.setText(value);input.setTextSize(15);input.setTextColor(UiColors.color(getResources(),0xfff2eadf));input.setSingleLine(true);input.setInputType(numeric?InputType.TYPE_CLASS_NUMBER:InputType.TYPE_CLASS_TEXT);input.setContentDescription(name);root.addView(input);return input;
    }
    private void reviewCsv(int index){try{
        JSONObject roll=state.prepared.manifest.getJSONArray("rolls").getJSONObject(index);
        LinearLayout content=new LinearLayout(this);content.setOrientation(1);int pad=(int)(20*getResources().getDisplayMetrics().density);content.setPadding(pad,0,pad,pad);
        EditText film=reviewField(content,"胶卷",roll.getString("stock"),false),iso=reviewField(content,"ISO",String.valueOf(roll.getInt("iso")),true),camera=reviewField(content,"相机",roll.getString("camera"),false);
        words(content,"画幅",13);String[] format={""};JSONArray details=state.prepared.manifest.getJSONArray("roll_details");for(int i=0;i<details.length();i++)if(details.getJSONObject(i).getString("roll_id").equals(roll.getString("id")))format[0]=details.getJSONObject(i).getString("format");
        String[] values={"35 mm","120","110","70 mm","65 mm","4x5","5x7","8x10","Instant"};java.util.ArrayList<Button> chips=new java.util.ArrayList<>();
        int columns=Math.max(1,Math.min(3,(int)(getResources().getConfiguration().screenWidthDp/(104*getResources().getConfiguration().fontScale))));
        for(int first=0;first<values.length;first+=columns){LinearLayout line=new LinearLayout(this);content.addView(line);
            for(int j=first;j<Math.min(first+columns,values.length);j++){String value=values[j];Button chip=UiSpacing.button(this);chip.setAllCaps(false);chip.setText(value.replace(" ",""));chip.setTextColor(value.equals(format[0])?UiColors.color(getResources(),0xff1a0f00):UiColors.color(getResources(),0xfff2eadf));chip.setBackgroundTintList(android.content.res.ColorStateList.valueOf(value.equals(format[0])?UiColors.color(getResources(),0xfff07f4a):UiColors.color(getResources(),0xff352820)));chip.setContentDescription("画幅 "+value);chip.setSelected(value.equals(format[0]));chips.add(chip);
                chip.setOnClickListener(v->{format[0]=value;for(Button choice:chips){boolean selected=choice==chip;choice.setSelected(selected);choice.setTextColor(selected?UiColors.color(getResources(),0xff1a0f00):UiColors.color(getResources(),0xfff2eadf));choice.setBackgroundTintList(android.content.res.ColorStateList.valueOf(selected?UiColors.color(getResources(),0xfff07f4a):UiColors.color(getResources(),0xff352820)));}});line.addView(chip,new LinearLayout.LayoutParams(0,-2,1));}}
        if(state.review!=null&&state.review.optInt("index",-1)==index){film.setText(state.review.optString("film"));iso.setText(state.review.optString("iso"));camera.setText(state.review.optString("camera"));format[0]=state.review.optString("format");for(Button choice:chips){boolean selected=choice.getContentDescription().toString().equals("画幅 "+format[0]);choice.setSelected(selected);choice.setTextColor(selected?UiColors.color(getResources(),0xff1a0f00):UiColors.color(getResources(),0xfff2eadf));choice.setBackgroundTintList(android.content.res.ColorStateList.valueOf(selected?UiColors.color(getResources(),0xfff07f4a):UiColors.color(getResources(),0xff352820)));}}
        captureReview=()->{try{state.review=new JSONObject().put("index",index).put("film",film.getText().toString()).put("iso",iso.getText().toString()).put("camera",camera.getText().toString()).put("format",format[0]);}catch(JSONException impossible){throw new IllegalStateException(impossible);}};captureReview.run();
        ScrollView scroll=new ScrollView(this);scroll.addView(content);AlertDialog dialog=new AlertDialog.Builder(this).setTitle("检查这卷胶卷").setView(scroll).setNegativeButton("取消",(d,w)->cancelReview()).setPositiveButton("保存",null).create();dialog.setOnCancelListener(d->cancelReview());dialog.show();
        dialog.getWindow().setGravity(Gravity.BOTTOM);dialog.getWindow().setLayout(-1,-2);dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try{CsvImport.Preview candidate=new CsvImport.Preview();candidate.data=new FilmBackup.Prepared();candidate.data.manifest=new JSONObject(state.csv.data.manifest.toString());candidate.legacy=state.csv.legacy;candidate.rows=state.csv.rows;
            CsvImport.review(candidate,index,film.getText().toString(),iso.getText().toString(),camera.getText().toString(),format[0]);CsvDraft.save(this,candidate,null,false);state.csv=candidate;state.prepared=candidate.data;state.review=null;captureReview=null;dialog.dismiss();render();}catch(Exception invalid){Toast.makeText(this,invalid.getMessage(),Toast.LENGTH_LONG).show();}});
    }catch(Exception invalid){Toast.makeText(this,invalid.getMessage(),Toast.LENGTH_LONG).show();}}
    private void cancelReview(){state.review=null;captureReview=null;saveDraft();}
    private void saveDraft(){if(state.csv==null||state.busy)return;try{if(captureReview!=null)captureReview.run();CsvDraft.save(this,state.csv,state.review,false);}catch(Exception error){state.message="无法保存导入草稿："+error.getMessage();Toast.makeText(this,state.message,Toast.LENGTH_LONG).show();}}
    @Override protected void onPause(){saveDraft();super.onPause();}
    private void pick(boolean write){Intent i=new Intent(write?Intent.ACTION_CREATE_DOCUMENT:Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/zip");if(write)i.putExtra(Intent.EXTRA_TITLE,"胶片随记-完整备份-"+LocalDate.now()+".zip");try{startActivityForResult(i,write?1:2);}catch(ActivityNotFoundException e){state.message="没有可用的文件选择器";render();}}
    private void pickCsv(){
        LinearLayout body=new LinearLayout(this);body.setOrientation(1);int pad=(int)(20*getResources().getDisplayMetrics().density);body.setPadding(pad,0,pad,pad);
        words(body,"无时区日期使用以下时区；日/月不明确时请指定顺序。带UTC偏移的时间沿用文件。仅日期按00:00处理。",15);
        Spinner order=new Spinner(this);order.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"自动（日/月有歧义时停止）","日/月/年","月/日/年"}));order.setSelection(getPreferences(0).getInt("csv_date_order",0));order.setContentDescription("导入日期顺序");body.addView(order);
        EditText zone=reviewField(body,"拍摄地时区",getPreferences(0).getString("csv_zone",java.time.ZoneId.systemDefault().getId()),false);zone.setHint("例如 Asia/Shanghai 或 +08:00");
        ScrollView scroll=new ScrollView(this);scroll.addView(body);AlertDialog dialog=new AlertDialog.Builder(this).setTitle("导入日期设置").setView(scroll).setNegativeButton("取消",null).setPositiveButton("选择文件",null).create();dialog.show();dialog.getButton(-1).setOnClickListener(v->{try{java.time.ZoneId selected=java.time.ZoneId.of(zone.getText().toString().trim());getPreferences(0).edit().putInt("csv_date_order",order.getSelectedItemPosition()).putString("csv_zone",selected.getId()).apply();dialog.dismiss();openCsvPicker();}catch(java.time.DateTimeException invalid){zone.setError("请输入有效时区，如 Asia/Shanghai 或 +08:00");}});
    }
    private void openCsvPicker(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*").putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);try{startActivityForResult(i,3);}catch(ActivityNotFoundException e){state.message="没有可用的文件选择器";render();}}
    private interface Work {void run(State state,Context context)throws Exception;}
    private void execute(Work work){execute(work,false);}
    private void execute(Work work,boolean exporting){State current=state;current.busy=true;current.message="";render();Context app=getBaseContext();new Thread(()->{try{work.run(current,app);}catch(Exception e){current.message="处理失败："+e.getMessage()+"。已有资料未被覆盖。"+(exporting?"目标位置可能有不完整的导出文件。":"");}finally{handler.post(()->{current.busy=false;if(current.disposed&&current.prepared!=null){current.prepared.close();current.prepared=null;}if(current.activity!=null)current.activity.render();});}},"film-backup").start();}
    @Override protected void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data);if(result!=RESULT_OK||data==null)return;Uri uri=data.getData();
        if(req==3){java.util.ArrayList<Uri> uris=new java.util.ArrayList<>();if(data.getClipData()!=null){for(int i=0;i<data.getClipData().getItemCount();i++)uris.add(data.getClipData().getItemAt(i).getUri());}else if(uri!=null)uris.add(uri);if(uris.isEmpty())return;
            final int dateOrder=getPreferences(0).getInt("csv_date_order",0);final java.time.ZoneId importZone=java.time.ZoneId.of(getPreferences(0).getString("csv_zone",java.time.ZoneId.systemDefault().getId()));
            execute((s,c)->{if(uris.size()>32)throw new IOException("一次最多选择32个文件");java.util.ArrayList<CsvImport.Preview> parts=new java.util.ArrayList<>();long total=0;int rows=0;for(Uri selected:uris)try(InputStream in=c.getContentResolver().openInputStream(selected)){if(in==null)throw new IOException("无法读取文件");CsvImport.Preview part=CsvImport.parse(new InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)),dateOrder,importZone);total+=part.data.manifest.toString().length();rows+=part.rows;if(total>16*1024*1024||rows>100000)throw new IOException("整批预览过大，请减少文件数量");parts.add(part);}
                CsvImport.Preview preview=CsvImport.combine(parts);CsvDraft.save(c,preview,null,false);if(s.prepared!=null)s.prepared.close();s.csv=preview;s.prepared=preview.data;s.review=null;s.message="已校验 "+uris.size()+" 个文件。"+(preview.legacy?"旧文件没有胶卷 ID，同文件内相同资料可能合并，请核对卷数。":"按文件分别保留胶卷。")+"不包含照片文件；确认前不会写入记录。";});return;}
        if(uri==null)return;
        if(req==1)execute((s,c)->{try(LogStore logs=new LogStore(c);OutputStream out=c.getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new IOException("无法写入目标位置");FilmBackup.write(c,logs,out);}s.message="备份已保存，包含参考照片、扫描与最近删除。";},true);
        if(req==2)execute((s,c)->{FilmBackup.Prepared next;try(InputStream in=c.getContentResolver().openInputStream(uri)){if(in==null)throw new IOException("无法读取备份");next=FilmBackup.inspect(c,in);}CsvDraft.clear(c);s.csv=null;s.review=null;if(s.prepared!=null)s.prepared.close();s.prepared=next;s.message="备份文件完整性校验通过。核对数量后确认导入。";});
    }
    private void restore(){execute((s,c)->{boolean csv=s.csv!=null;if(csv)CsvDraft.save(c,s.csv,null,true);try{try(LogStore logs=new LogStore(c)){FilmBackup.restore(c,logs,s.prepared);}CsvDraft.clear(c);s.message="资料已追加恢复，原有数据保留。";}finally{if(csv){s.prepared.close();s.prepared=null;s.csv=null;s.review=null;}}if(s.prepared!=null){s.prepared.close();s.prepared=null;}});}
    @Override public Object onRetainNonConfigurationInstance(){return state;}
    @Override public void onBackPressed(){if(state.busy){Toast.makeText(this,"正在处理备份，请等待完成",1).show();return;}super.onBackPressed();}
    @Override protected void onDestroy(){if(state!=null){if(state.activity==this)state.activity=null;if(isFinishing()){state.disposed=true;if(!state.busy&&state.prepared!=null){state.prepared.close();state.prepared=null;}}}super.onDestroy();}
}
