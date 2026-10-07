package dev.jayms.render;

import dev.jayms.net.Blocks;
import dev.jayms.net.WorldVoxels;
import java.util.Arrays;

/** Bounded voxel radiance, signed distance and three nested angular-radiance probe intervals. */
public final class TransportField {
    public final int width,height,length;
    public final float[] radiance,distance;
    public final float[][] probes=new float[3][];
    public final int[][] probeSize=new int[3][3];
    private int[] material;
    private byte[] light;
    private static final int[][] DIRECTIONS={{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1},
            {1,1,1},{-1,1,1},{1,1,-1},{-1,1,-1},{1,-1,1},{-1,-1,1},{1,-1,-1},{-1,-1,-1}};
    public TransportField(int width,int height,int length,int[] material,byte[] light){
        this.width=width;this.height=height;this.length=length;this.material=material;this.light=light;
        int n=width*height*length;boolean[] solid=new boolean[n];radiance=new float[n*4];
        for(int i=0;i<n;i++){
            int type=WorldVoxels.decode(material[i]);solid[i]=type!=Blocks.AIR&&type!=Blocks.GLASS&&type!=Blocks.WATER;
            if(!solid[i])continue;
            int color=material[i]==-1?0xff888888:WorldVoxels.surfaceColor(material[i]);float emission=type==Blocks.LED?4:.18f;
            for(int c=0;c<3;c++)radiance[i*4+c]=((color>>(16-c*8))&255)/255f*emission+(light[i*4+c]&255)/127f*.15f;
            radiance[i*4+3]=1;
        }
        float[] outside=distances(solid,true),inside=distances(solid,false);distance=new float[n];
        for(int i=0;i<n;i++)distance[i]=solid[i]?-(inside[i]-.5f):outside[i]-.5f;
        for(int cascade=2;cascade>=0;cascade--)bakeProbes(cascade,solid);
        this.material=null;this.light=null;
    }
    private int index(int x,int y,int z){return x+width*(y+height*z);}
    private float[] distances(boolean[] solid,boolean target){
        int n=solid.length;float[] d=new float[n];Arrays.fill(d,Math.max(width,Math.max(height,length)));
        int[] queue=new int[n];int head=0,tail=0;
        for(int i=0;i<n;i++)if(solid[i]==target){d[i]=0;queue[tail++]=i;}
        while(head<tail){int i=queue[head++],x=i%width,y=i/width%height,z=i/(width*height);
            for(int direction=0;direction<6;direction++){
                int[] dir=DIRECTIONS[direction];
                int a=x+dir[0],b=y+dir[1],c=z+dir[2];if(a<0||b<0||c<0||a>=width||b>=height||c>=length)continue;
                int j=index(a,b,c);if(d[j]>d[i]+1){d[j]=d[i]+1;queue[tail++]=j;}
            }
        }
        // Manhattan distance divided by sqrt(3) is a conservative Euclidean lower bound.
        for(int i=0;i<n;i++)d[i]/=1.7320508f;
        return d;
    }
    private static float[][] directions(int count){
        float[][] d=new float[count][3];
        for(int i=0;i<count;i++){
            double y=1-2*(i+.5)/count,r=Math.sqrt(1-y*y),angle=i*2.399963229728653;
            d[i]=new float[]{(float)(Math.cos(angle)*r),(float)y,(float)(Math.sin(angle)*r)};
        }
        return d;
    }
    private void bakeProbes(int cascade,boolean[] solid){
        int spacing=8<<cascade,w=(width+spacing-1)/spacing,h=(height+spacing-1)/spacing,l=(length+spacing-1)/spacing;
        int angles=16<<(2*cascade);float[][] dirs=directions(angles);
        probeSize[cascade]=new int[]{w,h,l*angles};float[] data=new float[w*h*l*angles*4];probes[cascade]=data;
        int start=cascade==0?1:(8<<(cascade-1)),end=8<<cascade;
        int[] parentAngle=new int[angles];
        if(cascade<2){
            float[][] parentDirs=directions(angles*4);
            for(int i=0;i<angles;i++){
                float best=-2;
                for(int j=0;j<parentDirs.length;j++){
                    float dot=dirs[i][0]*parentDirs[j][0]+dirs[i][1]*parentDirs[j][1]+dirs[i][2]*parentDirs[j][2];
                    if(dot>best){best=dot;parentAngle[i]=j;}
                }
            }
        }
        for(int z=0;z<l;z++)for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int ox=Math.min(width-1,x*spacing+spacing/2),oy=Math.min(height-1,y*spacing+spacing/2),oz=Math.min(length-1,z*spacing+spacing/2);
            boolean valid=!solid[index(ox,oy,oz)];
            for(int angle=0;angle<angles;angle++){
                int base=(x+w*(y+h*(z+angle*l)))*4;boolean blocked=false;
                float[] dir=dirs[angle];
                for(int step=start;step<=end;step++){
                    int a=ox+Math.round(dir[0]*step),b=oy+Math.round(dir[1]*step),c=oz+Math.round(dir[2]*step);
                    if(a<0||b<0||c<0||a>=width||b>=height||c>=length)break;
                    int i=index(a,b,c);if(solid[i]){
                        if(valid)for(int channel=0;channel<3;channel++)data[base+channel]=radiance[i*4+channel];blocked=true;break;
                    }
                }
                // Far radiance is merged only when the near interval is transparent. Angular
                // resolution grows fourfold as spatial probe density drops eightfold.
                if(!blocked&&valid&&cascade<2){
                    int[] dims=probeSize[cascade+1];int parentL=dims[2]/(angles*4);
                    int parent=(x/2+dims[0]*(y/2+dims[1]*(z/2+parentAngle[angle]*parentL)))*4;
                    for(int channel=0;channel<3;channel++)data[base+channel]+=probes[cascade+1][parent+channel];
                }
                data[base+3]=valid?1:0;
            }
        }
    }
}
