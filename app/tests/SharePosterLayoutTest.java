package cn.sixnine.filmlog;

public final class SharePosterLayoutTest {
    private static int checks;
    private static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    public static void main(String[] args){
        int[] counts={0,1,2,4,5,6,7,8,9,10,12,16,17,24,36,38,42,43,72,84,85,1000};
        for(boolean overview:new boolean[]{false,true})for(boolean vertical:new boolean[]{false,true})for(int count:counts){
            SharePosterLayout layout=new SharePosterLayout(count,overview,vertical);int seen=0;
            for(int page=0;page<layout.pages;page++){
                check(layout.start(page)==seen,"continuous pages");check(layout.end(page)-layout.start(page)<=layout.capacity,"capacity");
                SharePosterLayout.Box strip=layout.strip(layout.rows(page)-1,page,2400);check(strip.bottom<=.926f*2400,"footer clearance");
                for(int i=layout.start(page);i<layout.end(page);i++){
                    SharePosterLayout.Box photo=layout.photo(i,page,2400);check(photo.left>=.03f*2400-.01&&photo.right<=.97f*2400+.01,"horizontal bounds");
                    check(Math.abs(photo.width()/photo.height()-(vertical?.75:1.5))<.00001,"aspect");seen++;
                }
            }
            check(seen==count,"no missing or duplicate photos");
        }
        check(new SharePosterLayout(38,true,false).pages==1,"38 frames on one page");
        check(new SharePosterLayout(72,true,false).pages==2,"72 ordinary frames paginate");
        check(new SharePosterLayout(72,true,true).pages==1,"72 half frames fit");
        check(new SharePosterLayout(72,false,true).pages==3,"72 half selected pages");
        SharePosterLayout one=new SharePosterLayout(1,false,true);SharePosterLayout.Box box=one.strip(0,0,2400);check(Math.abs(box.left+box.right-2400)<.01,"single portrait centered");
        check(SharePosterLayout.sampleSize(3000,4000,300,200,true)==8,"portrait fill covers short edge");
        check(SharePosterLayout.sampleSize(3000,4000,300,200,false)==16,"fit uses actual displayed dimensions");
        check(SharePosterLayout.sampleSize(4000,3000,2137,1425,true)==1,"single 12MP requires full decode");
        check(SharePosterLayout.sampleSize(64,48,300,200,true)==1,"small original not oversampled");
        check(SharePosterLayout.decodedBytes(4000,3000,1)==48000000L,"native photo bytes");
        try{SharePosterLayout.decodedBytes(Integer.MAX_VALUE,Integer.MAX_VALUE,1);throw new AssertionError("overflow accepted");}catch(IllegalArgumentException expected){checks++;}
        try{new SharePosterLayout(0,false,false).start(0);throw new AssertionError("empty page accepted");}catch(IllegalArgumentException expected){checks++;}
        try{new SharePosterLayout(-1,false,false);throw new AssertionError("negative count accepted");}catch(IllegalArgumentException expected){checks++;}
        check(new SharePosterLayout(Integer.MAX_VALUE,true,false).pages==51130564,"page arithmetic overflow safe");
        System.out.println("SharePosterLayoutTest: "+checks+" checks passed");
    }
}
