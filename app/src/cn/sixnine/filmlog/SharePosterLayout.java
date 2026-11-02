package cn.sixnine.filmlog;

/** Output coordinates are pixels, independent of device density and theme. */
final class SharePosterLayout {
    final int count,columns,capacity,pages;
    final boolean overview,vertical;
    SharePosterLayout(int count,boolean overview,boolean vertical){
        if(count<0)throw new IllegalArgumentException("照片数量无效");
        this.count=count;this.overview=overview;this.vertical=vertical;
        if(overview){columns=vertical?14:7;capacity=columns*6;}
        else if(vertical){columns=count<=2?2:count<=8?4:count<=18?6:8;capacity=columns*columns/2;}
        else{columns=count<=1?1:count<=4?2:count<=9?3:4;capacity=columns*columns;}
        pages=(int)(((long)count+capacity-1)/capacity);
    }
    int start(int page){page(page);return page*capacity;}
    int end(int page){return (int)Math.min(count,(long)start(page)+capacity);}
    int rows(int page){return (end(page)-start(page)+columns-1)/columns;}
    private void page(int page){if(page<0||page>=pages)throw new IllegalArgumentException("分享图页码无效");}
    float scale(int width){checkWidth(width);return .94f*width/columns/(vertical?19f:38f);}
    float stripHeight(int width){return 35*scale(width);}
    Box strip(int row,int page,int width){
        if(row<0||row>=rows(page))throw new IllegalArgumentException("分享图行号无效");
        float left=.03f*width,right=.97f*width;
        if(vertical&&!overview&&count==1){left=.265f*width;right=.735f*width;}
        float top=.025f*width+row*(stripHeight(width)+.01f*width);
        return new Box(left,top,right,top+stripHeight(width));
    }
    Box photo(int index,int page,int width){
        if(index<start(page)||index>=end(page))throw new IllegalArgumentException("照片不属于此页");
        int position=index-start(page);Box row=strip(position/columns,page,width);float scale=scale(width);
        float x=row.left+position%columns*(vertical?19:38)*scale;
        return new Box(x+(vertical?.5f:1f)*scale,row.top+5.5f*scale,x+(vertical?18.5f:37f)*scale,row.top+29.5f*scale);
    }
    static int sampleSize(int width,int height,float targetWidth,float targetHeight,boolean fill){
        if(width<=0||height<=0||!Float.isFinite(targetWidth)||!Float.isFinite(targetHeight)||targetWidth<=0||targetHeight<=0)throw new IllegalArgumentException("图片尺寸无效");
        double x=(double)targetWidth/width,y=(double)targetHeight/height,scale=fill?Math.max(x,y):Math.min(x,y);
        int sample=1;while(sample<=1<<29&&sample*2.0*scale<=1)sample*=2;
        return sample;
    }
    static long decodedBytes(int width,int height,int sample){
        if(width<=0||height<=0||sample<=0)throw new IllegalArgumentException("图片尺寸无效");
        long w=((long)width+sample-1)/sample,h=((long)height+sample-1)/sample;
        if(w>Long.MAX_VALUE/4/h)throw new IllegalArgumentException("图片尺寸过大");
        return w*h*4;
    }
    private static void checkWidth(int width){if(width<=0||width>2400)throw new IllegalArgumentException("分享图尺寸无效");}
    static final class Box {
        final float left,top,right,bottom;
        Box(float left,float top,float right,float bottom){this.left=left;this.top=top;this.right=right;this.bottom=bottom;}
        float width(){return right-left;}float height(){return bottom-top;}
    }
}
