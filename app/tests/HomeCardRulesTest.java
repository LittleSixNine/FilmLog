package cn.sixnine.filmlog;

/** Host checks for the native C card's physical cell boundaries and snap decisions. */
public final class HomeCardRulesTest {
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static void near(float actual,float expected){check(Math.abs(actual-expected)<.0001f,actual+" != "+expected);}
    private static void rejects(Runnable call){try{call.run();throw new AssertionError("invalid ruler accepted");}catch(IllegalArgumentException expected){}}
    public static void main(String[] args){
        for(int width:new int[]{100,240,272,327,366})for(int total:new int[]{1,12,36,999}){near(HomeRollCard.boundary(width,0,total),0);near(HomeRollCard.boundary(width,total,total),width);float previous=0;for(int i=1;i<=total;i++){float end=HomeRollCard.boundary(width,i,total);check(end>=previous,"ordered equal capacity intervals");previous=end;}}
        rejects(()->HomeRollCard.boundary(100,1,0));rejects(()->HomeRollCard.boundary(100,-1,36));rejects(()->HomeRollCard.boundary(100,37,36));rejects(()->HomeRollCard.boundary(Float.NaN,1,36));
        check(HomeRollCarousel.nearest(0,339,2)==0,"first roll");check(HomeRollCarousel.nearest(169,339,2)==0,"before midpoint");check(HomeRollCarousel.nearest(170,339,2)==1,"after midpoint");check(HomeRollCarousel.nearest(339,339,2)==1,"second roll stop");check(HomeRollCarousel.nearest(-100,339,2)==0&&HomeRollCarousel.nearest(10000,339,2)==1,"clamped ends");check(HomeRollCarousel.nearest(100,0,0)==0,"empty layout");
        near(HomeRollCarousel.scrollFraction(0,339,3),0);near(HomeRollCarousel.scrollFraction(339,339,3),.5f);near(HomeRollCarousel.scrollFraction(678,339,3),1);near(HomeRollCarousel.scrollFraction(170,339,3),170/678f);near(HomeRollCarousel.scrollFraction(-40,339,3),0);near(HomeRollCarousel.scrollFraction(900,339,3),1);near(HomeRollCarousel.scrollFraction(10,0,3),0);near(HomeRollCarousel.scrollFraction(10,339,1),0);
        System.out.println("PASS: equal capacity boundaries, alternate capacity, invalid ranges, carousel midpoint/end clamps and continuous rail interpolation; no native UI constructed");
    }
}
