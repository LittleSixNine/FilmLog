package cn.sixnine.filmlog;

import android.app.Instrumentation;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import java.io.*;
import java.util.UUID;

/** Prepared for an isolated Android test package; never operates on the user's database. */
public final class HomeCInstrumentation extends Instrumentation {
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){Bundle result=new Bundle();try{
        Context base=getTargetContext();check(base.getPackageName().equals("cn.sixnine.filmlog.media.tests"),"isolated package required");
        Context context=new WorkspaceContext(Workspace.root(base),UUID.randomUUID().toString());
        try(LogStore logs=new LogStore(context)){
            String roll=new CatalogStore(logs).manual(UUID.randomUUID().toString(),"Advisory 36","Test 400","Test camera",400,36,"35 mm","","");
            String other=logs.addRoll("Other independent roll","Test 200","",200,36);
            for(int frame=1;frame<=38;frame++){
                String request=UUID.randomUUID().toString(),id=logs.insertWithId(request,roll,100000+frame*1000,0,480,"denied","8","1/125","","","","");
                check(logs.shot(id).frame==frame&&logs.roll(roll).count==frame,"consecutive actual frame "+frame);
                check(logs.insertWithId(request,roll,100000+frame*1000,0,480,"denied","8","1/125","","","","").equals(id),"idempotent retry "+frame);
            }
            check(logs.roll(roll).state.equals("active")&&logs.roll(roll).capacity==36&&logs.nextFrame(roll)==39,"38/36 remains active with next39");
            check(logs.roll(other).count==0&&ExposureWidget.available(logs.roll(roll)),"independent count and widget availability beyond36");
            String third=logs.addRoll("Third geometry fixture","Test 100","",100,36);
            logs.editRoll(roll,"Renamed","Test 400","Test camera",400,36);
            logs.changeState(roll,"active","finished","","","");check(!ExposureWidget.available(logs.roll(roll)),"manual finish blocks records");
            logs.changeState(roll,"finished","active","","","");check(ExposureWidget.available(logs.roll(roll)),"manual resume above nominal");
            final Throwable[] failure={null};runOnMainSync(()->{try{
                for(int width:new int[]{320,375,414}){
                    float density=context.getResources().getDisplayMetrics().density;int viewport=Math.round(width*density),edge=Math.round(24*density),gap=Math.round(12*density);
                    HomeRollCarousel carousel=new HomeRollCarousel(context,()->true,i->{});
                    for(String id:new String[]{other,roll,third})carousel.addCard(new HomeRollCard(context,logs,logs.roll(id),()->{},()->{},()->{}));
                    carousel.measure(View.MeasureSpec.makeMeasureSpec(viewport,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));carousel.layout(0,0,viewport,carousel.getMeasuredHeight());carousel.select(1,false);
                    android.view.ViewGroup strip=(android.view.ViewGroup)carousel.getChildAt(0);View middle=strip.getChildAt(1);int left=strip.getLeft()+middle.getLeft()-carousel.getScrollX(),right=left+middle.getWidth();check(left==edge&&viewport-right==edge,"symmetric centered card "+width);
                    check(strip.getChildAt(0).getRight()-carousel.getScrollX()==edge-gap&&viewport-(strip.getChildAt(2).getLeft()-carousel.getScrollX())==edge-gap,"equal neighboring slivers "+width);
                    View indicator=carousel.indicator();indicator.measure(View.MeasureSpec.makeMeasureSpec(viewport,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(Math.round(32*density),View.MeasureSpec.EXACTLY));indicator.layout(0,0,viewport,indicator.getMeasuredHeight());
                    android.graphics.Bitmap rendered=android.graphics.Bitmap.createBitmap(viewport,indicator.getHeight(),android.graphics.Bitmap.Config.ARGB_8888);indicator.draw(new android.graphics.Canvas(rendered));check(rendered.getPixel(viewport/2,Math.round(19*density))==context.getColor(R.color.ui_accent),"continuous thumb reflects middle card "+width);rendered.recycle();

                    final int[] calls={0,0,0};HomeRollCard card=new HomeRollCard(context,logs,logs.roll(roll),()->calls[0]++,()->calls[1]++,()->calls[2]++);
                    int pixels=Math.round((width-48)*context.getResources().getDisplayMetrics().density);card.measure(View.MeasureSpec.makeMeasureSpec(pixels,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));card.layout(0,0,pixels,card.getMeasuredHeight());
                    TextView counter=card.findViewWithTag("dashboard-count");check(counter.getText().toString().equals("38/36"),"actual counter "+width);
                    TextView warning=card.findViewWithTag("roll-capacity-warning");check(warning!=null&&warning.getText().toString().contains("留意回卷时机"),"visible advisory "+width);
                    Button quick=card.findViewWithTag("quick-record"),meter=card.findViewWithTag("meter-record");check(quick.isEnabled()&&meter.isEnabled()&&quick.getText().toString().equals("快速记录第39张"),"actions remain available "+width);
                    quick.performClick();meter.performClick();check(calls[0]==0&&calls[1]==1&&calls[2]==1,"record buttons do not open details");card.performClick();check(calls[0]==1,"card opens its bound roll");
                    check(logs.roll(roll).count==38,"UI callbacks never invent a successful record");
                }
                HomeRollCarousel single=new HomeRollCarousel(context,()->true,i->{});single.addCard(new HomeRollCard(context,logs,logs.roll(roll),()->{},()->{},()->{}));check(single.indicator().getVisibility()==View.GONE,"single roll hides position rail");
            }catch(Throwable error){failure[0]=error;}});if(failure[0]!=null)throw failure[0];
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();FilmBackup.write(context,logs,bytes);
            try(FilmBackup.Prepared archive=FilmBackup.inspect(context,new ByteArrayInputStream(bytes.toByteArray()))){
                check(archive.count("shots")==38,"backup accepts beyond nominal");FilmBackup.restore(context,logs,archive);
            }
            check(logs.roll(roll).count==38&&logs.roll(roll).capacity==36,"restore preserves original IDs and counts");
            int copies=0;for(LogStore.Roll item:logs.rolls())if(item.count==38&&item.capacity==36)copies++;check(copies==2,"restored copy retains nominal36 and actual38");
            String csv="id,roll_name,stock,camera,iso,frame_number,tapped_at_utc,captured_at_utc,offset_seconds,zone_offset_minutes,status,roll_id,roll_capacity\n"+
                "beyond,CSV 37,Test,Camera,400,37,2026-10-26T00:00:00Z,2026-10-26T00:00:00Z,0,480,denied,csv-roll,36\n";
            CsvImport.Preview imported=CsvImport.parse(new StringReader(csv));FilmBackup.validate(imported.data);check(imported.data.manifest.getJSONArray("shots").getJSONObject(0).getInt("frame_number")==37,"CSV retains frame37");
            check(imported.data.manifest.getJSONArray("rolls").getJSONObject(0).getInt("capacity")==36,"CSV retains nominal36");
            org.json.JSONObject shot=imported.data.manifest.getJSONArray("shots").getJSONObject(0);for(double invalid:new double[]{0,-1,1.5,Integer.MAX_VALUE}){shot.put("frame_number",invalid);try{FilmBackup.validate(imported.data);throw new AssertionError("invalid backup frame accepted");}catch(IOException expected){}}imported.data.close();
        }
        result.putString("stream","PASS: isolated actual38/nominal36 storage, idempotence, independent rolls, manual finish/resume, native C actions, symmetric carousel/slivers and position rail at320/375/414, backup append and CSV beyond nominal, invalid imported frame guard\n");finish(-1,result);
    }catch(Throwable error){result.putString("stream",android.util.Log.getStackTraceString(error));finish(0,result);}}
}
