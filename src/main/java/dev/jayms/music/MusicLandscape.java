package dev.jayms.music;

import java.util.Arrays;

/** Fixed-capacity scene state, independent of GL and audio device ownership. */
public final class MusicLandscape {
    public static final int RIPPLE_CAP=8, PARTICLE_CAP=256, MEMORY_CAP=128;
    public static final int RADIUS=7;
    public static final double HEIGHT_RANGE=heightRange(), MAX_DISPLACEMENT=HEIGHT_RANGE*.08;
    private static double heightRange(){
        double low=Double.POSITIVE_INFINITY,high=Double.NEGATIVE_INFINITY;
        for(int x=-RADIUS;x<=RADIUS;x++)for(int z=-RADIUS;z<=RADIUS;z++){
            double height=base(x,z);low=Math.min(low,height);high=Math.max(high,height);
        }
        return high-low;
    }
    public static final class Pool {
        public final double[] age,x,z;
        public final double lifetime;
        private int cursor;
        Pool(int cap,double lifetime){age=new double[cap];x=new double[cap];z=new double[cap];this.lifetime=lifetime;clear();}
        void clear(){Arrays.fill(age,lifetime);cursor=0;}
        void step(double dt){for(int i=0;i<age.length;i++)age[i]=Math.min(lifetime,age[i]+dt);}
        void add(double px,double pz){int i=cursor;cursor=(cursor+1)%age.length;age[i]=0;x[i]=px;z[i]=pz;}
        public int count(){int count=0;for(double a:age)if(a<lifetime)count++;return count;}
    }
    public final Pool ripples=new Pool(RIPPLE_CAP,6),particles=new Pool(PARTICLE_CAP,8),memory=new Pool(MEMORY_CAP,20);
    public double time,bass,mid,high,atmosphere;
    private double emissions;
    private int seed;
    public void reset(){ripples.clear();particles.clear();memory.clear();bass=mid=high=emissions=0;time=0;seed=0;}
    /** Explicit pause freezes all visible state. Completion keeps stepping to a calm scene. */
    public void update(double dt,MusicAnalysis.Bands bands,double motion,boolean paused){
        if(paused)return;
        dt=Math.max(0,Math.min(.1,dt));time+=dt;
        bass=bands.bass();mid=bands.mid();high=bands.treble();
        double energy=(bass+mid+high)/3;
        atmosphere+=(energy-atmosphere)*(1-Math.exp(-dt/(energy>atmosphere?5:10)));
        ripples.step(dt);particles.step(dt);memory.step(dt);
        if(bands.beat()&&motion>0){
            double x=coordinate(),z=coordinate();ripples.add(x,z);
            for(int j=0;j<4;j++)memory.add(x+(j-1.5)*.7,z+j*.4);
        }
        emissions+=dt*high*motion*22;
        while(emissions>=1){particles.add(coordinate(),coordinate());emissions--;}
    }
    private double coordinate(){seed=(seed*1664525+1013904223);return ((seed>>>8)/(double)0x1000000-.5)*12;}
    public static double base(double x,double z){
        double hill=1.65+1.1*Math.sin(x*.22+z*.12)+.75*Math.cos(z*.29-x*.09);
        double lake=2.2*Math.exp(-((x-2)*(x-2)+(z-1)*(z-1))/28);
        return Math.max(.3,Math.min(4.3,hill-lake));
    }
    public double displacement(double x,double z,double motion){
        double wave=bass*.72*Math.sin(x*.32+z*.20-time*1.1);
        double accent=0;
        for(int i=0;i<RIPPLE_CAP;i++)if(ripples.age[i]<6){
            double age=ripples.age[i],distance=Math.hypot(x-ripples.x[i],z-ripples.z[i]);
            accent+=Math.sin(distance*2-age*5)*Math.exp(-Math.pow((distance-age*2)/1.7,2))*(1-age/6)*.18;
        }
        return MAX_DISPLACEMENT*Math.min(1,Math.max(0,motion)/2)*Math.tanh(wave+accent);
    }
    /** Columns and roots both use integer cell centers, including at displaced edges. */
    public double surface(double x,double z,double motion){return base(Math.rint(x),Math.rint(z))+displacement(Math.rint(x),Math.rint(z),motion);}
    public double water(double x,double z,double motion){return .88+displacement(x,z,motion)*.3+Math.sin(time*.45+x*.25+z*.18)*.006*Math.min(1,motion);}
}
