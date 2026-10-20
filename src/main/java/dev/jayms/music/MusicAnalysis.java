package dev.jayms.music;

/** Fixed-size radix-2 FFT, evaluated only when the audio clock advances by one hop. */
public final class MusicAnalysis {
    public record Bands(double bass,double mid,double treble,boolean beat) { }
    private static final int N=2048,HOP=512;
    private final double[] real=new double[N],imag=new double[N],window=new double[N],previous=new double[N/2];
    private final double[] target=new double[3],smooth=new double[3];
    private long lastHop=-1;
    private double lastBeat=-1,meanFlux=.002;
    public MusicAnalysis(){for(int i=0;i<N;i++)window[i]=.5-.5*Math.cos(2*Math.PI*i/(N-1));}
    public void reset(){java.util.Arrays.fill(previous,0);java.util.Arrays.fill(target,0);java.util.Arrays.fill(smooth,0);lastHop=-1;lastBeat=-1;meanFlux=.002;}
    public Bands sample(float[] mono,int rate,double seconds,double dt,boolean playing,double sensitivity){
        boolean beat=false;long hop=(long)(seconds*rate/HOP);
        if(playing&&mono.length>0&&hop!=lastHop){
            lastHop=hop;int end=(int)Math.min(mono.length,Math.max(0,seconds*rate));
            double rms=0;
            for(int i=0;i<N;i++){int p=end-N+1+i;double v=p>=0&&p<mono.length?mono[p]:0;real[i]=v*window[i];imag[i]=0;rms+=v*v;}
            fft();java.util.Arrays.fill(target,0);double flux=0;
            for(int i=1;i<N/2;i++){
                double hz=i*(double)rate/N,mag=Math.hypot(real[i],imag[i])*4/N;
                if(hz>=40&&hz<=Math.min(12000,rate/2.0)){
                    int band=hz<250?0:hz<2500?1:2;target[band]+=mag*mag;
                    flux+=Math.max(0,mag-previous[i]);
                }
                previous[i]=mag;
            }
            boolean audible=rms/N>1e-7;
            beat=audible&&flux>Math.max(.04,meanFlux*2.0)/Math.max(.25,sensitivity)&&seconds-lastBeat>=.23;
            if(beat)lastBeat=seconds;
            meanFlux+=.08*(flux-meanFlux);
            for(int b=0;b<3;b++)target[b]=audible?Math.min(1,Math.sqrt(target[b])*sensitivity*1.6):0;
        }
        if(!playing)java.util.Arrays.fill(target,0);
        for(int b=0;b<3;b++){double tau=target[b]>smooth[b]?.055:.30;smooth[b]+=(target[b]-smooth[b])*(1-Math.exp(-Math.max(0,dt)/tau));if(smooth[b]<.0001)smooth[b]=0;}
        return new Bands(smooth[0],smooth[1],smooth[2],beat);
    }
    private void fft(){
        for(int i=1,j=0;i<N;i++){int bit=N>>1;for(;(j&bit)!=0;bit>>=1)j^=bit;j^=bit;if(i<j){double v=real[i];real[i]=real[j];real[j]=v;}}
        for(int len=2;len<=N;len<<=1){double angle=-2*Math.PI/len,wr0=Math.cos(angle),wi0=Math.sin(angle);
            for(int start=0;start<N;start+=len){double wr=1,wi=0;for(int j=0;j<len/2;j++){
                int a=start+j,b=a+len/2;double tr=wr*real[b]-wi*imag[b],ti=wr*imag[b]+wi*real[b];
                real[b]=real[a]-tr;imag[b]=imag[a]-ti;real[a]+=tr;imag[a]+=ti;
                double next=wr*wr0-wi*wi0;wi=wr*wi0+wi*wr0;wr=next;
            }}
        }
    }
}
