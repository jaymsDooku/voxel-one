package dev.jayms.physics;

/** Conservative bounded cellular water, shallow-water transport, heat and gas on a regular grid. */
public final class FluidGrid {
    public final int nx,ny,nz;
    public final float[] water,heat,fuel,smoke,pressure;
    public final boolean[] solid;
    public FluidGrid(int nx,int ny,int nz) {
        if(nx<1||ny<1||nz<1||(long)nx*ny*nz>1_000_000) throw new IllegalArgumentException("Invalid grid size");
        this.nx=nx;this.ny=ny;this.nz=nz;int n=nx*ny*nz;
        water=new float[n];heat=new float[n];fuel=new float[n];smoke=new float[n];pressure=new float[n];solid=new boolean[n];
    }
    public int index(int x,int y,int z) {if(x<0||y<0||z<0||x>=nx||y>=ny||z>=nz) throw new IndexOutOfBoundsException();return (y*nz+z)*nx+x;}
    private void flow(int a,int b,float fraction,boolean down) {
        if(solid[a]||solid[b]) return;
        float amount=down?Math.min(water[a],1-water[b]):Math.max(0,(water[a]-water[b])*.5f);
        amount=Math.min(Math.max(0,amount*fraction),Math.min(water[a],Math.max(0,1-water[b])));
        water[a]-=amount;water[b]+=amount;
    }
    public void step(float dt) {
        if(!Float.isFinite(dt)||dt<0) throw new IllegalArgumentException("Invalid fluid time");
        int steps=Math.max(1,(int)Math.ceil(Math.min(dt,.25f)/.02f));float h=Math.min(dt,.25f)/steps;
        for(int s=0;s<steps;s++) {
            float fraction=Math.min(1,h*12);
            for(int y=1;y<ny;y++)for(int z=0;z<nz;z++)for(int x=0;x<nx;x++)flow(index(x,y,z),index(x,y-1,z),fraction,true);
            for(int y=0;y<ny;y++)for(int z=0;z<nz;z++)for(int x=0;x<nx;x++) {
                int a=index(x,y,z);
                if(x+1<nx){int b=index(x+1,y,z);if(water[a]>=water[b])flow(a,b,fraction,false);else flow(b,a,fraction,false);}
                if(z+1<nz){int b=index(x,y,z+1);if(water[a]>=water[b])flow(a,b,fraction,false);else flow(b,a,fraction,false);}
            }
            diffuse(heat,h*.8f);diffuse(smoke,h*.3f);
            for(int y=0;y<ny;y++)for(int z=0;z<nz;z++)for(int x=0;x<nx;x++) {
                int i=index(x,y,z);
                if(heat[i]>1&&fuel[i]>0&&!solid[i]&&water[i]<.4f) {
                    float burn=Math.min(fuel[i],h*(heat[i]-1));fuel[i]-=burn;heat[i]+=burn*3;smoke[i]+=burn;
                }
                heat[i]*=(float)Math.exp(-h*(.05f+water[i]*5));
                if(y+1<ny&&!solid[index(x,y+1,z)]) {
                    float rise=smoke[i]*Math.min(1,h*2);smoke[i]-=rise;smoke[index(x,y+1,z)]+=rise;
                }
                pressure[i]=smoke[i]*Math.max(.1f,1+heat[i]);
            }
        }
    }
    private void diffuse(float[] field,float rate) {
        float[] next=field.clone();
        for(int y=0;y<ny;y++)for(int z=0;z<nz;z++)for(int x=0;x<nx;x++) {
            int a=index(x,y,z);
            for(int[] d:new int[][]{{1,0,0},{0,1,0},{0,0,1}}) {
                int bx=x+d[0],by=y+d[1],bz=z+d[2];if(bx>=nx||by>=ny||bz>=nz)continue;
                int b=index(bx,by,bz);if(solid[a]||solid[b])continue;
                float change=(field[a]-field[b])*Math.min(rate,1f/6);next[a]-=change;next[b]+=change;
            }
        }
        System.arraycopy(next,0,field,0,field.length);
    }
    /** Height-field hydraulic erosion and talus relaxation. Boundary sediment stays in the basin. */
    public static void erode(float[] terrain,float[] depth,float[] sediment,int width,float dt,float talus) {
        if(width<1||terrain.length%width!=0||depth.length!=terrain.length||sediment.length!=terrain.length||dt<0)throw new IllegalArgumentException("Invalid erosion grid");
        int height=terrain.length/width;
        for(int z=0;z<height;z++)for(int x=0;x<width;x++) {
            int a=z*width+x;
            for(int b:new int[]{x+1<width?a+1:-1,z+1<height?a+width:-1}) if(b>=0) {
                int high=terrain[a]>terrain[b]?a:b,low=high==a?b:a;
                float slide=Math.max(0,terrain[high]-terrain[low]-talus)*Math.min(.25f,dt);
                terrain[high]-=slide;terrain[low]+=slide;
                float flow=Math.max(0,terrain[high]+depth[high]-terrain[low]-depth[low])*Math.min(.25f,dt);
                flow=Math.min(flow,depth[high]);depth[high]-=flow;depth[low]+=flow;
                float cut=Math.min(Math.max(0,terrain[high]),flow*.05f);terrain[high]-=cut;sediment[low]+=cut;
            }
            float deposit=sediment[a]*Math.min(1,dt*.1f);sediment[a]-=deposit;terrain[a]+=deposit;
        }
    }
}
