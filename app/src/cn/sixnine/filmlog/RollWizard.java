package cn.sixnine.filmlog;

import android.app.*;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;
import java.util.function.Consumer;

/** Two-step draft; only the final transaction creates a roll or consumes inventory. */
final class RollWizard {
    private static final int BG=0xff21170f,CARD=0xff30251e,INK=0xfff4eee2,MUTED=0xffb4a99f,ORANGE=0xffef8249;
    private final Activity activity;
    private final CatalogStore catalog;
    private final LogStore logs;
    private final Bundle draft;
    private final Runnable cancelled;
    private final Consumer<String> saved;
    private Dialog dialog;
    private AlertDialog cameraDialog;
    private Dialog cameraEditorDialog;
    private LinearLayout root,body,filmResults;
    private ScrollView scroll;
    private Button next;
    private TextView capacityHint;
    private boolean saving;

    RollWizard(Activity activity,LogStore logs,Bundle draft,Runnable cancelled,Consumer<String> saved){
        this.activity=activity;this.logs=logs;this.catalog=new CatalogStore(logs);this.draft=draft;this.cancelled=cancelled;this.saved=saved;
        if(!draft.containsKey("request"))draft.putString("request",UUID.randomUUID().toString());
        if(!draft.containsKey("format"))draft.putString("format","35 mm");
        if(!draft.containsKey("iso"))draft.putString("iso","400");
        if(!draft.containsKey("capacity"))draft.putString("capacity","36");
        if(draft.getInt("rollFlow",0)!=2){draft.putInt("step",draft.getInt("step",0)>=2?1:0);draft.putInt("rollFlow",2);}
        if(draft.getBoolean("inventoryPicker",false)){draft.putString("source","inventory");draft.remove("inventoryPicker");draft.putInt("step",0);}
    }
    static int defaultCapacity(String format){String f=format.trim().replace(" ","");if(f.equals("120"))return 12;if(f.equals("110"))return 24;if(f.equalsIgnoreCase("Instant"))return 10;if(f.equals("4x5")||f.equals("5x7")||f.equals("8x10"))return 1;return 36;}
    static int[] capacities(String format){String f=format.trim().replace(" ","");if(f.equals("120"))return new int[]{8,10,12,16};if(f.equals("110"))return new int[]{12,24};if(f.equals("70mm")||f.equals("65mm"))return new int[]{24,36};if(f.equals("4x5")||f.equals("5x7")||f.equals("8x10"))return new int[]{1,2,4,8};if(f.equalsIgnoreCase("Instant"))return new int[]{8,10,20};return new int[]{12,24,36};}
    private String get(String key){return draft.getString(key,"");}
    private int dp(int n){return Math.round(n*activity.getResources().getDisplayMetrics().density);}
    private int color(int value){return UiColors.color(activity.getResources(),value);}
    private LinearLayout column(){LinearLayout view=new LinearLayout(activity);view.setOrientation(1);return view;}
    private GradientDrawable surface(int value,int radius,int border){GradientDrawable shape=new GradientDrawable();shape.setColor(color(value));shape.setCornerRadius(dp(radius));if(border!=0)shape.setStroke(dp(1),color(border));return shape;}
    private TextView text(String value,int size,int tint){TextView view=new TextView(activity);view.setText(value);view.setTextSize(size);view.setTextColor(color(tint));return view;}
    private Button button(String value,boolean primary,Runnable action){
        Button b=new Button(activity);b.setText(value);b.setAllCaps(false);b.setTextSize(15);b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(dp(48));b.setMinimumHeight(dp(48));b.setPadding(dp(12),dp(8),dp(12),dp(8));b.setBackgroundTintList(null);b.setElevation(0);b.setStateListAnimator(null);
        b.setTextColor(primary?UiColors.onAccent(activity.getResources()):color(ORANGE));b.setBackground(primary?surface(ORANGE,12,0):surface(Color.TRANSPARENT,8,0));b.setOnClickListener(v->action.run());return b;
    }
    private void gap(LinearLayout layout,int size){layout.addView(new View(activity),new LinearLayout.LayoutParams(1,dp(size)));}
    private void divider(LinearLayout layout){View line=new View(activity);line.setBackgroundColor(color(0xff554237));layout.addView(line,new LinearLayout.LayoutParams(-1,dp(1)));}
    private void hideKeyboard(){if(root!=null){((android.view.inputmethod.InputMethodManager)activity.getSystemService(Activity.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(root.getWindowToken(),0);root.clearFocus();}}
    private final class Sheet extends LinearLayout {
        Sheet(){super(activity);setOrientation(1);}
        @Override protected void onMeasure(int width,int height){
            int maximum=dp(Math.max(240,(int)(activity.getResources().getConfiguration().screenHeightDp*.86f)));
            if(MeasureSpec.getMode(height)!=MeasureSpec.UNSPECIFIED)maximum=Math.min(maximum,MeasureSpec.getSize(height));
            super.onMeasure(width,MeasureSpec.makeMeasureSpec(maximum,MeasureSpec.EXACTLY));
        }
    }
    void show(){
        dialog=new Dialog(activity,R.style.FilmDialog);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);dialog.setOnCancelListener(d->cancelled.run());
        dialog.setOnKeyListener((d,key,event)->{if(key!=KeyEvent.KEYCODE_BACK)return false;if(event.getAction()==KeyEvent.ACTION_UP){if(draft.getInt("step",0)>0)back();else close();}return true;});
        render();dialog.show();Window window=dialog.getWindow();if(window!=null){window.setBackgroundDrawableResource(android.R.color.transparent);window.setGravity(Gravity.BOTTOM);window.setLayout(-1,-2);window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE|WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);WindowManager.LayoutParams p=window.getAttributes();p.dimAmount=.4f;window.setAttributes(p);}
        if(draft.getBoolean("cameraEditorOpen",false))editCamera();else if(draft.getBoolean("cameraPicker",false)||draft.getBoolean("cameraCatalogOpen",false))chooseCamera();
    }
    private void close(){hideKeyboard();if(cameraEditorDialog!=null)cameraEditorDialog.dismiss();if(cameraDialog!=null)cameraDialog.dismiss();dialog.dismiss();cancelled.run();}
    private void back(){hideKeyboard();draft.putInt("step",0);render();}
    private void updateNext(){if(next!=null){boolean enabled=!get("stock").trim().isEmpty();next.setEnabled(enabled&&!saving);next.setAlpha(enabled?1f:.4f);}}
    private void render(){
        int step=draft.getInt("step",0);root=new Sheet();root.setFocusableInTouchMode(true);root.setPadding(dp(20),dp(10),dp(20),dp(16));root.setBackground(surface(BG,24,0));
        LinearLayout grip=new LinearLayout(activity);grip.setGravity(Gravity.CENTER);View handle=new View(activity);handle.setBackground(surface(0xff554237,3,0));grip.addView(handle,new LinearLayout.LayoutParams(dp(32),dp(4)));root.addView(grip);gap(root,8);
        LinearLayout header=new LinearLayout(activity);header.setGravity(Gravity.CENTER_VERTICAL);
        if(step>0){Button back=button("‹",false,this::back);back.setTextSize(26);back.setTextColor(color(INK));back.setContentDescription("返回选择胶片");header.addView(back,new LinearLayout.LayoutParams(dp(40),-2));}
        TextView title=text("新建胶卷",22,INK);title.setTypeface(null,Typeface.BOLD);header.addView(title,new LinearLayout.LayoutParams(0,-2,1));Button cancel=button("取消",false,this::close);cancel.setTextColor(color(MUTED));cancel.setContentDescription("关闭新建胶卷，保留本次草稿");header.addView(cancel);root.addView(header);
        TextView progress=text("1 选胶片    —    2 确认装卷",12,MUTED);android.text.SpannableString label=new android.text.SpannableString(progress.getText());int start=step==0?0:label.toString().indexOf("2 确认");label.setSpan(new android.text.style.ForegroundColorSpan(color(ORANGE)),start,step==0?5:label.length(),0);progress.setText(label);progress.setContentDescription(step==0?"第1步，共2步，选胶片":"第2步，共2步，确认装卷");root.addView(progress);gap(root,18);
        scroll=new ScrollView(activity);scroll.setFillViewport(false);body=column();scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        if(step==0)film();else confirm();
        gap(root,12);next=button(step==0?"下一步":"创建胶卷",true,()->{hideKeyboard();if(!valid())return;if(draft.getInt("step",0)==0){draft.putInt("step",1);render();}else save();});root.addView(next,new LinearLayout.LayoutParams(-1,-2));updateNext();dialog.setContentView(root);
    }
    private void changeFormat(String format){
        if(!RecentChoices.sameFormat(get("format"),format)){
            draft.putString("capacity",String.valueOf(defaultCapacity(format)));
            if(!get("cameraId").isEmpty()||!get("camera").isEmpty()&&!draft.getBoolean("customCamera",false))draft.remove("camera");
            for(String key:new String[]{"stock","stockId","batch","cameraId"})draft.remove(key);
        }
        draft.putString("format",format);if(!CatalogStore.supportsHalfFrame(format))draft.putBoolean("halfFrame",false);
    }
    private void formats(){
        LinearLayout row=new LinearLayout(activity);String current=get("format");Button probe=button("35 mm",false,()->{});probe.setTextSize(14);int columns=(probe.getPaint().measureText("35 mm")+dp(32))*4>dp(activity.getResources().getConfiguration().screenWidthDp-40)?2:4;int index=0;
        for(String value:new String[]{"35 mm","120","110","其他"}){
            if(index>0&&index%columns==0){body.addView(row);gap(body,6);row=new LinearLayout(activity);}
            boolean other=value.equals("其他"),selected=other?!Arrays.asList("35 mm","120","110").contains(current):RecentChoices.sameFormat(current,value);
            Button b=button(other&&selected?current:value,false,()->{if(other)otherFormat();else{hideKeyboard();changeFormat(value);render();}});b.setTextSize(14);b.setTextColor(color(selected?ORANGE:INK));b.setSelected(selected);b.setContentDescription((other?"其他规格":value)+(selected?"，已选择":""));b.setBackground(surface(selected?0xff503326:CARD,9,selected?ORANGE:0xff554237));
            LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(0,-2,1);if(row.getChildCount()>0)size.leftMargin=dp(6);row.addView(b,size);index++;
        }body.addView(row);gap(body,14);
    }
    private void otherFormat(){
        String[] values={"4x5","5x7","8x10","70mm","65mm","Instant","自定义"};new AlertDialog.Builder(activity).setTitle("其他规格").setItems(values,(d,w)->{
            if(w<values.length-1){changeFormat(values[w]);render();return;}EditText edit=edit("formatDraft","胶片规格",InputType.TYPE_CLASS_TEXT);edit.setText(get("format"));AlertDialog prompt=new AlertDialog.Builder(activity).setTitle("胶片规格").setView(edit).setNegativeButton("取消",null).setPositiveButton("确定",null).create();prompt.setOnShowListener(v->prompt.getButton(-1).setOnClickListener(b->{String value=edit.getText().toString().trim();if(value.isEmpty()||value.length()>60){edit.setError("请填写1至60字的规格");return;}changeFormat(value);prompt.dismiss();render();}));prompt.show();
        }).show();
    }
    private JSONArray covers(String id,String format){return FilmArtwork.covers(activity,id,format);}
    private View cover(String id,int size,String format){
        JSONArray rows=covers(id,format);JSONObject row=rows.optJSONObject(0);Bitmap image=row==null?null:FilmArtwork.image(activity,row.optString(size>64?"image":"thumbnail"));
        if(image!=null){ImageView view=new ImageView(activity);view.setScaleType(ImageView.ScaleType.FIT_CENTER);view.setImageBitmap(image);view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);view.setBackground(surface(CARD,5,0));view.setClipToOutline(true);view.setLayoutParams(new LinearLayout.LayoutParams(dp(size),dp(size)));return view;}
        TextView placeholder=text("暂无封面",12,MUTED);placeholder.setGravity(Gravity.CENTER);placeholder.setBackground(surface(CARD,5,0));placeholder.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);placeholder.setLayoutParams(new LinearLayout.LayoutParams(dp(size),dp(size)));return placeholder;
    }
    private boolean referenceCover(String id,String format){JSONObject row=covers(id,format).optJSONObject(0);return row!=null&&(row.optJSONArray("formats")==null||row.optJSONArray("formats").length()==0);}
    private void row(LinearLayout parent,String id,String format,String name,String caption,boolean selected,Runnable action){
        LinearLayout row=new LinearLayout(activity);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(4),dp(10),dp(4),dp(10));row.setBackground(surface(selected?0xff503326:Color.TRANSPARENT,9,0));row.addView(cover(id,64,format));
        LinearLayout info=column();LinearLayout.LayoutParams space=new LinearLayout.LayoutParams(0,-2,1);space.leftMargin=dp(12);TextView title=text(name,15,INK);title.setTypeface(null,Typeface.BOLD);info.addView(title);gap(info,3);info.addView(text(caption+(referenceCover(id,format)?" · 参考封面":""),12,MUTED));row.addView(info,space);
        TextView mark=text(selected?"✓":"○",19,selected?ORANGE:MUTED);mark.setGravity(Gravity.CENTER);row.addView(mark,new LinearLayout.LayoutParams(dp(30),-2));row.setFocusable(true);row.setClickable(true);row.setSelected(selected);row.setContentDescription(name+"，"+caption+(referenceCover(id,format)?"，参考封面，包装规格未确认":"")+(selected?"，已选择":""));row.setOnClickListener(v->action.run());parent.addView(row,new LinearLayout.LayoutParams(-1,-2));divider(parent);
    }
    private ArrayList<CatalogStore.Batch> inventory(){ArrayList<CatalogStore.Batch> values=catalog.batches(false);values.removeIf(b->b.quantity<1);return values;}
    private void film(){
        ArrayList<CatalogStore.Batch> batches=inventory();
        if(!batches.isEmpty()||!get("batch").isEmpty()){
            LinearLayout tabs=new LinearLayout(activity);for(String source:new String[]{"library","inventory"}){boolean selected=source.equals(get("source"))||source.equals("library")&&get("source").isEmpty();Button tab=button(source.equals("library")?"胶片库":"我的库存",false,()->{hideKeyboard();if(!selected){for(String key:new String[]{"batch","stockId","stock"})draft.remove(key);draft.putBoolean("customFilm",false);draft.putString("capacity",String.valueOf(defaultCapacity(get("format"))));}draft.putBoolean("browsingFilm",true);draft.putString("source",source);render();});tab.setSelected(selected);tab.setTextColor(color(selected?ORANGE:MUTED));tab.setBackground(surface(selected?0xff503326:Color.TRANSPARENT,9,0));tabs.addView(tab,new LinearLayout.LayoutParams(0,-2,1));}body.addView(tabs);gap(body,12);
        }else draft.putString("source","library");
        if(!get("source").equals("inventory"))formats();
        boolean chosen=!get("stock").isEmpty()&&!draft.getBoolean("customFilm",false)&&!draft.getBoolean("browsingFilm",false);
        if(chosen){
            row(body,get("stockId"),get("format"),get("stock"),get("format")+(get("batch").isEmpty()?"":" · 来自库存"),true,()->{draft.putBoolean("browsingFilm",true);render();});
            body.addView(button("更换胶片",false,()->{hideKeyboard();draft.putBoolean("browsingFilm",true);render();}));
        }else{
            EditText search=edit(get("source").equals("inventory")?"inventoryQuery":"filmQuery",get("source").equals("inventory")?"搜索库存胶片":"搜索胶片品牌或型号",InputType.TYPE_CLASS_TEXT);search.setHint(search.getContentDescription());body.addView(search);gap(body,16);filmResults=column();body.addView(filmResults);
            search.addTextChangedListener(watcher(value->updateFilms()));updateFilms();
        }
        if(!chosen){gap(body,8);body.addView(button(draft.getBoolean("customFilm",false)?"收起手动填写":get("batch").isEmpty()?"找不到？手动填写":"改为手动填写",false,()->{hideKeyboard();draft.remove("batch");draft.remove("stockId");draft.putString("source","library");draft.putBoolean("customFilm",!draft.getBoolean("customFilm",false));render();}));}
        if(draft.getBoolean("customFilm",false)){field(body,"胶片名称","stock",InputType.TYPE_CLASS_TEXT);}
        if(!get("stock").isEmpty()||draft.getBoolean("customFilm",false)){
            gap(body,10);if(!get("batch").isEmpty()){body.addView(text("已选库存："+get("stock"),14,INK));body.addView(text(get("format")+" · ISO "+get("iso")+" · "+get("capacity")+" 张",13,MUTED));body.addView(text("创建成功后扣减1卷",12,MUTED));}
            else{field(body,"拍摄 ISO","iso",InputType.TYPE_CLASS_NUMBER);}
            if(CatalogStore.supportsHalfFrame(get("format"))){
                body.addView(text("包装张数",12,MUTED));gap(body,6);LinearLayout halfRow=new LinearLayout(activity);halfRow.setGravity(Gravity.CENTER_VERTICAL);
                if(get("batch").isEmpty())halfRow.addView(edit("capacity","包装张数",InputType.TYPE_CLASS_NUMBER),new LinearLayout.LayoutParams(0,-2,1));else{TextView base=text(get("capacity")+" 张",18,INK);halfRow.addView(base,new LinearLayout.LayoutParams(0,-2,1));}
                CheckBox half=new CheckBox(activity);half.setText("×2 半格");half.setTextColor(color(INK));half.setChecked(draft.getBoolean("halfFrame",false));half.setMinHeight(dp(48));LinearLayout.LayoutParams checkSize=new LinearLayout.LayoutParams(-2,-2);checkSize.leftMargin=dp(12);halfRow.addView(half,checkSize);body.addView(halfRow);gap(body,6);body.addView(text("半格机请点选",12,MUTED));capacityHint=text("",13,MUTED);body.addView(capacityHint);half.setOnCheckedChangeListener((v,on)->{draft.putBoolean("halfFrame",on);updateCapacityHint();});updateCapacityHint();
            }else if(get("batch").isEmpty())field(body,"曝光张数","capacity",InputType.TYPE_CLASS_NUMBER);
            if(get("format").equals("120")){gap(body,6);body.addView(text("曝光张数取决于相机画幅，请按实际确认。",12,MUTED));}
        }
    }
    private void updateFilms(){
        filmResults.removeAllViews();
        if(get("source").equals("inventory")){
            String term=get("inventoryQuery").toLowerCase(Locale.ROOT);int count=0;
            for(CatalogStore.Batch b:inventory())if((b.stock+" "+b.location+" "+b.format).toLowerCase(Locale.ROOT).contains(term)){count++;row(filmResults,ArtworkLinks.get(logs,"batch",b.id),b.format,b.stock,b.format+" · ISO "+b.iso+" · "+b.capacity+" 张 · 库存 "+b.quantity+" 卷",b.id.equals(get("batch")),()->selectBatch(b));}
            if(count==0)filmResults.addView(text("没有符合条件的库存胶片",14,MUTED));return;
        }
        FilmStockStore store=new FilmStockStore(logs);LinkedHashMap<String,FilmStockStore.Stock> ordered=new LinkedHashMap<>();
        for(String id:RecentChoices.stocks(activity,logs,get("format")))try{ordered.put(id,store.choose(activity,id,get("format")));}catch(IllegalArgumentException unavailable){}
        String term=get("filmQuery").trim().toLowerCase(Locale.ROOT);boolean recent=term.isEmpty()&&!ordered.isEmpty();
        if(term.isEmpty())for(String id:new String[]{"original:kodak_gold_200","original:kodak_portra_400","original:ilford_hp5_400"})try{ordered.putIfAbsent(id,store.choose(activity,id,get("format")));}catch(IllegalArgumentException unavailable){}
        for(FilmStockStore.Stock stock:store.all(activity,false))if(FilmStockStore.matches(stock,get("format")))ordered.putIfAbsent(stock.id,stock);
        filmResults.addView(text(term.isEmpty()?(recent?"最近使用与常用胶片":"常用胶片"):"搜索结果",12,MUTED));gap(filmResults,6);int shown=0,matches=0,limit=term.isEmpty()?4:30;
        for(FilmStockStore.Stock stock:ordered.values())if((stock.title()+" "+stock.search+" "+stock.process).toLowerCase(Locale.ROOT).contains(term)){matches++;if(shown++>=limit)continue;row(filmResults,stock.id,get("format"),stock.title(),FilmStockStore.isoText(stock)+(stock.process.isEmpty()?"":" · "+stock.process),stock.id.equals(get("stockId"))&&get("batch").isEmpty(),()->useStock(stock.id,0));}
        if(matches==0)filmResults.addView(text("没有符合条件的胶片，可手动填写",14,MUTED));
        if(matches>limit&&!term.isEmpty())filmResults.addView(text("共 "+matches+" 款，显示前 "+limit+" 款；可细化搜索",12,MUTED));
        filmResults.addView(button("浏览全部胶片  ›",false,this::chooseStock));
    }
    private void chooseStock(){hideKeyboard();activity.startActivityForResult(new android.content.Intent(activity,StockLibraryActivity.class).putExtra("select",true).putExtra("format",get("format")),24);}
    void useStock(String id){useStock(id,0);}
    void useStock(String id,int selectedIso){
        FilmStockStore.Stock stock=new FilmStockStore(logs).choose(activity,id,get("format"));draft.remove("batch");draft.remove("coverImage");draft.putBoolean("browsingFilm",false);draft.putString("source","library");draft.putBoolean("customFilm",false);draft.putString("stockId",stock.id);draft.putString("stock",stock.title());draft.putString("iso",stock.iso==0?(selectedIso>=1&&selectedIso<=102400?String.valueOf(selectedIso):""):String.valueOf(stock.iso));draft.putInt("step",0);RecentChoices.stock(activity,stock.id);render();
    }
    private void selectBatch(CatalogStore.Batch batch){
        changeFormat(batch.format);draft.remove("coverImage");draft.putBoolean("browsingFilm",false);draft.putString("batch",batch.id);draft.putString("stockId",ArtworkLinks.get(logs,"batch",batch.id));draft.putString("stock",batch.stock);draft.putString("iso",String.valueOf(batch.iso));draft.putString("capacity",String.valueOf(batch.capacity));draft.putBoolean("customFilm",false);draft.putString("source","inventory");render();
    }
    private TextWatcher watcher(Consumer<String> action){return new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int b,int c){}public void onTextChanged(CharSequence s,int a,int b,int c){action.accept(s.toString());}public void afterTextChanged(Editable e){}};}
    private EditText edit(String key,String label,int type){
        EditText edit=new EditText(activity);edit.setText(get(key));edit.setTextColor(color(INK));edit.setHintTextColor(color(MUTED));edit.setTextSize(16);edit.setSingleLine(true);edit.setInputType(type);edit.setPadding(dp(12),dp(10),dp(12),dp(10));edit.setBackground(surface(CARD,10,0xff554237));edit.setMinimumHeight(dp(48));edit.setContentDescription(label);
        edit.addTextChangedListener(watcher(value->{draft.putString(key,value);if(key.equals("stock"))draft.remove("stockId");if(key.equals("camera"))draft.remove("cameraId");updateNext();if(key.equals("capacity"))updateCapacityHint();}));return edit;
    }
    private void field(LinearLayout parent,String title,String key,int type){parent.addView(text(title,12,MUTED));gap(parent,6);EditText view=edit(key,title,type);if(key.equals("name"))view.setHint("留空则使用胶片名称");parent.addView(view,new LinearLayout.LayoutParams(-1,-2));gap(parent,12);}
    static int effectiveCapacity(int base,boolean half){if(base<1||base>(half?499:999))throw new IllegalArgumentException("预计张数应为1–999");return base*(half?2:1);}
    private int capacity(){return effectiveCapacity(Integer.parseInt(get("capacity")),draft.getBoolean("halfFrame",false));}
    private void updateCapacityHint(){if(capacityHint!=null)try{capacityHint.setText("预计 "+capacity()+" 张"+(draft.getBoolean("halfFrame",false)?" · 18×24 mm 竖幅":""));}catch(Exception invalid){capacityHint.setText("请填写有效的包装张数");}}
    private void coverChoices(LinearLayout hero){
        JSONArray rows=covers(get("stockId"),get("format"));if(rows.length()<2){hero.addView(cover(get("stockId"),136,get("format")));draft.remove("coverImage");return;}
        JSONObject selected=FilmArtwork.selected(activity,get("stockId"),get("format"),get("coverImage"));draft.putString("coverImage",selected.optString("image"));
        HorizontalScrollView strip=new HorizontalScrollView(activity);strip.setTag("cover-choices");strip.setHorizontalScrollBarEnabled(false);LinearLayout choices=new LinearLayout(activity);strip.addView(choices);ArrayList<LinearLayout> cards=new ArrayList<>();
        int edge=Math.max(64,Math.min(112,(activity.getResources().getConfiguration().screenWidthDp-64)/3-12));
        for(int i=0;i<rows.length();i++){JSONObject row=rows.optJSONObject(i);String image=row.optString("image");final int number=i+1;LinearLayout card=column();cards.add(card);card.setPadding(dp(4),dp(4),dp(4),dp(4));card.setGravity(Gravity.CENTER);ImageView art=new ImageView(activity);art.setScaleType(ImageView.ScaleType.FIT_CENTER);art.setImageBitmap(FilmArtwork.image(activity,row.optString("thumbnail")));card.addView(art,new LinearLayout.LayoutParams(dp(edge),dp(edge)));TextView label=text("封面 "+number,11,MUTED);label.setGravity(Gravity.CENTER);card.addView(label);card.setTag("cover-choice:"+i);card.setFocusable(true);card.setContentDescription("封面 "+number+"，使用时期未确认");card.setOnClickListener(v->{draft.putString("coverImage",image);for(LinearLayout item:cards){boolean active=item==card;item.setSelected(active);item.setBackground(surface(active?0xff503326:Color.TRANSPARENT,10,active?ORANGE:0));}});boolean active=image.equals(get("coverImage"));card.setSelected(active);card.setBackground(surface(active?0xff503326:Color.TRANSPARENT,10,active?ORANGE:0));LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(-2,-2);size.rightMargin=dp(8);choices.addView(card,size);}
        hero.addView(strip,new LinearLayout.LayoutParams(-1,-2));gap(hero,6);TextView note=text("左右滑动，点选封面 · 使用时期未确认",11,MUTED);note.setGravity(Gravity.CENTER);hero.addView(note);
    }
    private void confirm(){
        LinearLayout hero=column();hero.setGravity(Gravity.CENTER_HORIZONTAL);coverChoices(hero);gap(hero,14);TextView title=text(get("stock"),20,INK);title.setTypeface(null,Typeface.BOLD);title.setGravity(Gravity.CENTER);hero.addView(title,new LinearLayout.LayoutParams(-1,-2));gap(hero,4);TextView meta=text(get("format")+(draft.getBoolean("halfFrame",false)?" 半格":"")+" · ISO "+get("iso")+" · "+capacity()+" 张",13,MUTED);meta.setGravity(Gravity.CENTER);hero.addView(meta);
        if(referenceCover(get("stockId"),get("format"))){gap(hero,5);TextView note=text("参考封面 · 包装规格未确认",11,MUTED);note.setGravity(Gravity.CENTER);hero.addView(note);}
        if(!get("batch").isEmpty()){gap(hero,5);TextView note=text("来自库存 · 创建成功后扣减1卷",12,MUTED);note.setGravity(Gravity.CENTER);hero.addView(note);}
        hero.addView(button("更换胶片",false,this::back));body.addView(hero);divider(body);gap(body,18);
        body.addView(text("使用相机（可选）",12,MUTED));gap(body,6);Button camera=button((get("camera").isEmpty()?"暂不选择":get("camera"))+"  ›",false,this::chooseCamera);camera.setTextColor(color(INK));camera.setGravity(Gravity.CENTER_VERTICAL|Gravity.START);camera.setBackground(surface(CARD,10,0xff554237));body.addView(camera,new LinearLayout.LayoutParams(-1,-2));
        if(draft.getBoolean("customCamera",false)){gap(body,10);field(body,"相机型号","camera",InputType.TYPE_CLASS_TEXT);}
        if(get("format").equals("120")){gap(body,8);body.addView(text("请确认曝光张数与相机画幅一致；如需调整，返回上一步。",12,MUTED));}
        gap(body,18);divider(body);
        String projectName="";if(!get("projectId").isEmpty())try{projectName=catalog.entry(get("projectId"),"project").name;}catch(IllegalArgumentException missing){draft.remove("projectId");}
        boolean expanded=draft.getBoolean("options",!get("name").isEmpty()||!projectName.isEmpty());
        Button options=button("名称与项目（可选）  "+(expanded?"⌃":"⌄"),false,()->{hideKeyboard();draft.putBoolean("options",!expanded);render();});options.setTextColor(color(INK));options.setGravity(Gravity.CENTER_VERTICAL|Gravity.START);options.setContentDescription("名称与项目，可选，"+(expanded?"已展开":"已折叠"));body.addView(options,new LinearLayout.LayoutParams(-1,-2));
        if(expanded){gap(body,8);field(body,"胶卷名称","name",InputType.TYPE_CLASS_TEXT);Button project=button(projectName.isEmpty()?"所属项目：不归入项目  ›":"所属项目："+projectName+"  ›",false,this::chooseProject);project.setGravity(Gravity.CENTER_VERTICAL|Gravity.START);project.setTextColor(color(INK));body.addView(project,new LinearLayout.LayoutParams(-1,-2));}divider(body);
    }
    private void chooseCamera(){
        hideKeyboard();draft.putBoolean("cameraPicker",true);draft.remove("cameraCatalogOpen");LinearLayout panel=column();panel.setPadding(dp(16),dp(4),dp(16),0);EditText search=edit("cameraCatalogQuery","搜索相机型号",InputType.TYPE_CLASS_TEXT);search.setHint("搜索相机型号");panel.addView(search);gap(panel,8);ListView list=new ListView(activity);panel.addView(list,new LinearLayout.LayoutParams(-1,dp(280)));
        ArrayList<RecentChoices.Camera> values=RecentChoices.cameras(activity,logs,get("format"));HashSet<String> ids=new HashSet<>();for(RecentChoices.Camera c:values)if(!c.id.isEmpty())ids.add(c.id);
        for(CatalogStore.Entry entry:catalog.entries("camera",false))if(RecentChoices.sameFormat(entry.format,get("format"))&&ids.add(entry.id))values.add(new RecentChoices.Camera(entry.id,entry.name,entry.format));

        ArrayList<RecentChoices.Camera> shown=new ArrayList<>();ArrayList<String> names=new ArrayList<>();ArrayAdapter<String> adapter=new ArrayAdapter<String>(activity,android.R.layout.simple_list_item_1,names){@Override public View getView(int pos,View recycled,ViewGroup parent){RecentChoices.Camera c=shown.get(pos);CatalogStore.Entry entry=c.id.isEmpty()?null:catalog.entry(c.id,"camera");LinearLayout row=new LinearLayout(activity);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(8),dp(12),dp(8),dp(12));String logo=entry==null?CameraLogos.suggest(activity,c.name):entry.cameraLogo;if(!CameraLogos.thumbnail(activity,logo,row,76,32)){TextView generic=text("相机",12,MUTED);row.addView(generic,new LinearLayout.LayoutParams(dp(76),dp(32)));}LinearLayout words=column();words.addView(text(c.name,15,INK));String sub=(entry!=null&&!entry.cameraModel.isEmpty()&&!entry.cameraModel.equals(entry.name)?entry.cameraModel+" · ":"")+c.format;words.addView(text(sub,12,MUTED));row.addView(words,new LinearLayout.LayoutParams(0,-2,1));return row;}};list.setAdapter(adapter);
        Runnable update=()->{shown.clear();names.clear();String term=get("cameraCatalogQuery").toLowerCase(Locale.ROOT);for(int i=0;i<values.size();i++){RecentChoices.Camera camera=values.get(i);CatalogStore.Entry entry=camera.id.isEmpty()?null:catalog.entry(camera.id,"camera");String model=entry==null?camera.name:entry.cameraModel;if(!(camera.name+" "+model+" "+CameraLogos.aliases(CameraLogos.brand(activity,model))).toLowerCase(Locale.ROOT).contains(term))continue;shown.add(camera);names.add(camera.name+"\n"+(camera.id.isEmpty()?"历史填写":"我的器材"));}adapter.notifyDataSetChanged();};search.addTextChangedListener(watcher(value->update.run()));
        AlertDialog picker=new AlertDialog.Builder(activity).setTitle("选择相机").setView(panel).setNegativeButton("返回",null).setNeutralButton("手动填写",(d,w)->{draft.remove("cameraId");draft.putBoolean("customCamera",true);render();}).setPositiveButton("暂不选择",(d,w)->{draft.remove("cameraId");draft.remove("camera");draft.putBoolean("customCamera",false);render();}).create();
        Button addCamera=button("＋ 添加相机",false,()->{picker.dismiss();draft.remove("cameraEditor");editCamera();});panel.addView(addCamera,2);
        cameraDialog=picker;list.setOnItemClickListener((parent,view,index,id)->{RecentChoices.Camera c=shown.get(index);draft.putString("camera",c.name);if(c.id.isEmpty())draft.remove("cameraId");else draft.putString("cameraId",c.id);draft.putBoolean("customCamera",false);picker.dismiss();render();});picker.setOnDismissListener(d->{draft.putBoolean("cameraPicker",false);if(cameraDialog==picker)cameraDialog=null;});update.run();picker.show();if(picker.getWindow()!=null)picker.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE|WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
    }
    private void editCamera(){
        draft.putBoolean("cameraEditorOpen",true);Bundle state=draft.getBundle("cameraEditor");if(state==null){state=new Bundle();draft.putBundle("cameraEditor",state);}
        cameraEditorDialog=CameraEditor.show(activity,logs,state,null,id->{draft.putBoolean("cameraEditorOpen",false);draft.remove("cameraEditor");cameraEditorDialog=null;CatalogStore.Entry entry=catalog.entry(id,"camera");if(RecentChoices.sameFormat(entry.format,get("format"))){draft.putString("cameraId",id);draft.putString("camera",entry.name);draft.putBoolean("customCamera",false);render();}else{render();Toast.makeText(activity,"相机已添加，但与当前胶片规格不符，请另选相机",Toast.LENGTH_LONG).show();chooseCamera();}},()->{draft.putBoolean("cameraEditorOpen",false);draft.remove("cameraEditor");cameraEditorDialog=null;if(dialog.isShowing())chooseCamera();});
    }
    private void chooseProject(){
        ArrayList<CatalogStore.Entry> entries=catalog.entries("project",false);entries.removeIf(e->e.completed);String[] names=new String[entries.size()+1];names[0]="不归入项目";int chosen=0;for(int i=0;i<entries.size();i++){names[i+1]=entries.get(i).name;if(entries.get(i).id.equals(get("projectId")))chosen=i+1;}
        new AlertDialog.Builder(activity).setTitle("所属项目").setSingleChoiceItems(names,chosen,(d,w)->{draft.putString("projectId",w==0?"":entries.get(w-1).id);d.dismiss();render();}).setNegativeButton("返回",null).show();
    }
    private boolean valid(){
        try{CatalogStore.required(get("format"),"胶片规格");CatalogStore.required(get("stock"),"胶片名称");int iso=Integer.parseInt(get("iso")),capacity=Integer.parseInt(get("capacity"));if(iso<1||iso>102400||capacity()<1)throw new IllegalArgumentException("ISO 1–102400，曝光张数1–999");return true;}
        catch(Exception error){Toast.makeText(activity,error instanceof NumberFormatException?"请填写有效的 ISO 和曝光张数":error.getMessage(),Toast.LENGTH_LONG).show();return false;}
    }
    private void save(){
        if(saving||!valid())return;saving=true;updateNext();String roll;
        try{
            android.database.sqlite.SQLiteDatabase db=logs.getWritableDatabase();db.beginTransaction();
            try{
                if(get("batch").isEmpty()){
                    roll=catalog.manual(get("request"),get("name"),get("stock"),get("camera"),Integer.parseInt(get("iso")),capacity(),get("format"),get("cameraId"),get("projectId"),get("stockId"));
                    // Explicit catalogue identity survives a different shooting ISO; names never infer a cover.
                    if(get("stockId").startsWith("original:")||get("stockId").startsWith("filmer:")){
                        FilmStockStore.Stock stock=new FilmStockStore(logs).choose(activity,get("stockId"),get("format"));if(!stock.title().equals(get("stock").trim()))throw new IllegalArgumentException("所选胶片名称已变化，请重新选择");ArtworkLinks.put(db,"roll",roll,stock.id);
                    }
                }else{
                    CatalogStore.Batch batch=catalog.batch(get("batch"));if(!batch.stock.equals(get("stock"))||!batch.format.equals(get("format"))||batch.iso!=Integer.parseInt(get("iso"))||batch.capacity!=Integer.parseInt(get("capacity")))throw new IllegalArgumentException("库存参数已变化，请返回重新选择");
                    roll=catalog.load(get("batch"),get("request"),get("name"),get("cameraId"),get("projectId"),get("camera"));
                }
                if(!get("coverImage").isEmpty())ArtworkLinks.setCover(activity,logs,"roll",roll,get("format"),get("coverImage"));
                catalog.halfFrame(roll,draft.getBoolean("halfFrame",false));
                android.content.ContentValues size=new android.content.ContentValues();size.put("capacity",capacity());db.update("rolls",size,"id=?",new String[]{roll});
                db.setTransactionSuccessful();
            }finally{db.endTransaction();}
            LogStore.Roll committed=logs.roll(roll);CatalogStore.Detail detail=catalog.detail(roll);RecentChoices.camera(activity,detail==null?"":detail.camera,committed.camera,detail==null?"":detail.format);dialog.dismiss();saved.accept(roll);
        }catch(Exception error){saving=false;updateNext();Toast.makeText(activity,"未完成装卷："+error.getMessage(),Toast.LENGTH_LONG).show();}
    }
}
