package cn.sixnine.filmlog;
import android.app.Instrumentation;
import android.graphics.*;
import android.os.Bundle;
import android.view.View;
public final class PresentationInstrumentation extends Instrumentation {
    private static java.lang.reflect.Field field(Class<?> type,String name)throws Exception{java.lang.reflect.Field f=type.getDeclaredField(name);f.setAccessible(true);return f;}
    private void brandIcons()throws Exception{
        android.content.res.Resources resources=getTargetContext().getResources();
        for(int density:new int[]{160,240,320,480,640}){
            android.graphics.drawable.Drawable fg=resources.getDrawableForDensity(R.mipmap.pellica_ic_launcher_foreground,density,null);Bitmap image=Bitmap.createBitmap(108,108,Bitmap.Config.ARGB_8888);fg.setBounds(0,0,108,108);fg.draw(new Canvas(image));int amber=0,dark=0;
            for(int y=0;y<108;y++)for(int x=0;x<108;x++){int pixel=image.getPixel(x,y);if(Color.alpha(pixel)<32)continue;if(Math.hypot(x-53.5,y-53.5)>33)throw new AssertionError("adaptive artwork outside safe circle "+density);if(Color.red(pixel)>150&&Color.green(pixel)>70&&Color.green(pixel)<220&&Color.blue(pixel)<130)amber++;if(Color.red(pixel)<110&&Color.green(pixel)<110)dark++;}
            if(amber<3||dark<400||Color.alpha(image.getPixel(0,0))!=0)throw new AssertionError("smoke film/dates/alpha at density "+density+" amber="+amber+" dark="+dark+" edge="+Color.alpha(image.getPixel(0,0)));image.recycle();
        }
        for(int icon:new int[]{R.mipmap.pellica_ic_launcher,R.mipmap.pellica_ic_launcher_round}){
            android.graphics.drawable.Drawable drawable=resources.getDrawable(icon,null);if(!(drawable instanceof android.graphics.drawable.AdaptiveIconDrawable))throw new AssertionError("adaptive launcher resource missing");
            android.graphics.drawable.AdaptiveIconDrawable adaptive=(android.graphics.drawable.AdaptiveIconDrawable)drawable;
            if(adaptive.getBackground()==null||adaptive.getForeground()==null)throw new AssertionError("adaptive layers");
            if(android.os.Build.VERSION.SDK_INT>=33&&adaptive.getMonochrome()==null)throw new AssertionError("themed monochrome icon missing");
            Bitmap image=Bitmap.createBitmap(96,96,Bitmap.Config.ARGB_8888);drawable.setBounds(0,0,96,96);drawable.draw(new Canvas(image));if(Color.alpha(image.getPixel(48,48))<240)throw new AssertionError("launcher center must render");image.recycle();
        }
        for(int resource:new int[]{R.drawable.brand_notification,R.mipmap.pellica_ic_launcher_monochrome}){
            android.graphics.drawable.Drawable drawable=resources.getDrawable(resource,null);Bitmap image=Bitmap.createBitmap(108,108,Bitmap.Config.ARGB_8888);drawable.setBounds(0,0,108,108);drawable.draw(new Canvas(image));int opaque=0;/* Android masks use alpha; premultiplied scaling can darken neutral-white edges. */for(int y=0;y<108;y++)for(int x=0;x<108;x++){int pixel=image.getPixel(x,y);if(Color.alpha(pixel)>128){opaque++;if(Color.red(pixel)!=Color.green(pixel)||Color.red(pixel)!=Color.blue(pixel))throw new AssertionError("monochrome must use alpha only resource="+resource+" pixel="+Integer.toHexString(pixel));}}if(opaque<300||Color.alpha(image.getPixel(0,0))!=0)throw new AssertionError("readable monochrome silhouette");image.recycle();
        }
        Bitmap launch=Bitmap.createBitmap(400,400,Bitmap.Config.ARGB_8888);android.graphics.drawable.Drawable splash=resources.getDrawable(R.drawable.pellica_launch,null);splash.setBounds(0,0,400,400);splash.draw(new Canvas(launch));if(Color.alpha(launch.getPixel(200,200))<240)throw new AssertionError("shared launch layer renders");launch.recycle();
    }
    private void viewerGestures()throws Exception{
        ScanViewerActivity viewer=new ScanViewerActivity();viewer.attachBaseContext(getTargetContext());Bitmap photo=Bitmap.createBitmap(200,100,Bitmap.Config.ARGB_8888);field(ScanViewerActivity.class,"bitmap").set(viewer,photo);
        Class<?> type=Class.forName("cn.sixnine.filmlog.ScanViewerActivity$ZoomImage");java.lang.reflect.Constructor<?> ctor=type.getDeclaredConstructor(ScanViewerActivity.class);ctor.setAccessible(true);View image=(View)ctor.newInstance(viewer);image.layout(0,0,400,400);java.lang.reflect.Method fit=type.getDeclaredMethod("fit");fit.setAccessible(true);fit.invoke(image);java.lang.reflect.Field scale=field(type,"scale"),base=field(type,"base");
        android.view.accessibility.AccessibilityNodeInfo node=android.view.accessibility.AccessibilityNodeInfo.obtain();image.onInitializeAccessibilityNodeInfo(node);if(node.getActionList().stream().noneMatch(a->a.getId()==R.id.zoom_in))throw new AssertionError("accessible zoom action missing");node.recycle();
        image.performAccessibilityAction(R.id.zoom_in,null);if(scale.getFloat(image)!=base.getFloat(image)*2)throw new AssertionError("accessible zoom");image.onKeyDown(android.view.KeyEvent.KEYCODE_MINUS,new android.view.KeyEvent(0,android.view.KeyEvent.KEYCODE_MINUS));if(scale.getFloat(image)!=base.getFloat(image))throw new AssertionError("keyboard zoom out");
        android.view.MotionEvent.PointerProperties[] properties={new android.view.MotionEvent.PointerProperties(),new android.view.MotionEvent.PointerProperties()};for(int i=0;i<2;i++){properties[i].id=i;properties[i].toolType=android.view.MotionEvent.TOOL_TYPE_FINGER;}
        long now=android.os.SystemClock.uptimeMillis();int[] actions={0,5|(1<<8),2,2,2,2,2,2,2,2,6|(1<<8),1};float[] spans={200,200,240,280,360,440,600,800,1000,1200,1200,1200};for(int step=0;step<actions.length;step++){int count=step==0||step==actions.length-1?1:2;android.view.MotionEvent.PointerCoords[] coords=new android.view.MotionEvent.PointerCoords[count];for(int i=0;i<count;i++){coords[i]=new android.view.MotionEvent.PointerCoords();coords[i].x=200+(i==0?-1:1)*spans[step]/2;coords[i].y=200;coords[i].pressure=1;coords[i].size=1;}android.view.MotionEvent event=android.view.MotionEvent.obtain(now,now+16*step,actions[step],count,properties,coords,0,0,1,1,0,0,android.view.InputDevice.SOURCE_TOUCHSCREEN,0);image.onTouchEvent(event);event.recycle();}
        if(scale.getFloat(image)<=base.getFloat(image)*1.1f||!field(type,"multiTouch").getBoolean(image))throw new AssertionError("real two-pointer detector must scale and suppress page fling: scale="+scale.getFloat(image)+", base="+base.getFloat(image)+", multi="+field(type,"multiTouch").getBoolean(image));
        for(int i=0;i<5;i++)image.performAccessibilityAction(R.id.zoom_in,null);if(scale.getFloat(image)!=base.getFloat(image)*8)throw new AssertionError("zoom upper bound");image.performAccessibilityAction(R.id.zoom_reset,null);if(scale.getFloat(image)!=base.getFloat(image))throw new AssertionError("zoom reset");((java.util.concurrent.ExecutorService)field(ScanViewerActivity.class,"worker").get(viewer)).shutdownNow();photo.recycle();
    }
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){waitForIdleSync();Bundle out=new Bundle();try{
        if(!getTargetContext().getPackageName().equals("cn.sixnine.filmlog.media.tests"))throw new AssertionError("isolation");
        final Throwable[] failure={null};runOnMainSync(()->{BackdropView backdrop=null;Bitmap result=Bitmap.createBitmap(200,80,Bitmap.Config.ARGB_8888);try{
            View source=new View(getTargetContext()){@Override protected void onDraw(Canvas c){Paint p=new Paint();p.setColor(Color.RED);c.drawRect(0,0,100,80,p);p.setColor(Color.BLUE);c.drawRect(100,0,200,80,p);}};source.layout(0,0,200,80);backdrop=new BackdropView(getTargetContext(),source);backdrop.layout(0,0,200,80);backdrop.draw(new Canvas(result));int left=result.getPixel(20,40),right=result.getPixel(180,40),a=result.getPixel(98,40),b=result.getPixel(102,40);
            if(Color.red(left)<=Color.red(right)||Color.blue(right)<=Color.blue(left)||Math.abs(Color.red(a)-Color.red(b))>=Math.abs(Color.red(left)-Color.red(right)))throw new AssertionError("backdrop must blur the actual sibling pattern, not flatten or blur its button");
            viewerGestures();brandIcons();backdrop.layout(0,0,120,70);backdrop.draw(new Canvas(result));android.view.animation.Animation enter=android.view.animation.AnimationUtils.loadAnimation(getTargetContext(),R.anim.film_page_enter),exit=android.view.animation.AnimationUtils.loadAnimation(getTargetContext(),R.anim.film_page_exit);enter.initialize(200,80,200,80);exit.initialize(200,80,200,80);if(enter.computeDurationHint()!=200||exit.computeDurationHint()!=200)throw new AssertionError("window motion resources");
        }catch(Throwable e){failure[0]=e;}finally{if(backdrop!=null)backdrop.onDetachedFromWindow();result.recycle();}});if(failure[0]!=null)throw failure[0];
        out.putString("stream","PASS: real backdrop capture/blur/tint, source pattern retained, resize and resource cleanup, full-screen animation resources; real two-pointer gesture detector, accessible zoom, keyboard, max/reset; five-density brand alpha/dates/adaptive-safe-circle, round/adaptive/monochrome and launch layers\n");finish(-1,out);
    }catch(Throwable e){out.putString("stream",android.util.Log.getStackTraceString(e));finish(0,out);}}
}
