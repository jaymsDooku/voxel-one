package dev.jayms.physics;

/** Finite-volume shallow water on a closed basin. Rusanov flux preserves conservative transport. */
public final class ShallowWater {
    public final int width,height;public final float[] depth,momentumX,momentumZ,bed;
    public float gravity=9.81f,cellSize=1;
    public ShallowWater(int width,int height){if(width<2||height<2||(long)width*height>65536)throw new IllegalArgumentException("Invalid basin");this.width=width;this.height=height;int n=width*height;depth=new float[n];momentumX=new float[n];momentumZ=new float[n];bed=new float[n];}
    public void step(float dt) {
        if(!Float.isFinite(dt)||dt<0||dt>.25f)throw new IllegalArgumentException("Invalid shallow water step");
        float remaining=dt;
        while(remaining>1e-7f) {
            float speed=.01f;
            for(int i=0;i<depth.length;i++)speed=Math.max(speed,(float)Math.sqrt(gravity*Math.max(0,depth[i]))+(Math.abs(momentumX[i])+Math.abs(momentumZ[i]))/Math.max(.001f,depth[i]));
            float h=Math.min(remaining,.2f*cellSize/speed);remaining-=h;
            float[] dh=new float[depth.length],dx=new float[depth.length],dz=new float[depth.length];
            for(int z=0;z<height;z++)for(int x=0;x<width;x++) {
                int a=z*width+x;
                if(x+1<width)flux(a,a+1,true,h,dh,dx,dz);
                if(z+1<height)flux(a,a+width,false,h,dh,dx,dz);
            }
            for(int i=0;i<depth.length;i++) {
                depth[i]=Math.max(0,depth[i]+dh[i]);momentumX[i]=(momentumX[i]+dx[i])*.999f;momentumZ[i]=(momentumZ[i]+dz[i])*.999f;
                int x=i%width,z=i/width;
                if(x==0||x==width-1)momentumX[i]=0;if(z==0||z==height-1)momentumZ[i]=0;
                if(depth[i]<.00001f){momentumX[i]=0;momentumZ[i]=0;}
            }
        }
    }
    private void flux(int a,int b,boolean x,float dt,float[] dh,float[] dx,float[] dz) {
        float ha=depth[a],hb=depth[b],qa=x?momentumX[a]:momentumZ[a],qb=x?momentumX[b]:momentumZ[b];
        float ua=qa/Math.max(.001f,ha),ub=qb/Math.max(.001f,hb);
        float c=Math.max(Math.abs(ua)+(float)Math.sqrt(gravity*ha),Math.abs(ub)+(float)Math.sqrt(gravity*hb));
        float mass=(qa+qb-c*(hb-ha))*.5f*dt/cellSize;
        mass=Math.max(-hb*.24f,Math.min(ha*.24f,mass));dh[a]-=mass;dh[b]+=mass;
        float force=(qa*ua+qb*ub+gravity*.5f*(ha*ha+hb*hb)-c*(qb-qa))*.5f*dt/cellSize;
        float bedForce=-gravity*(ha+hb)*.5f*(bed[b]-bed[a])*dt/cellSize;
        if(x){dx[a]-=force;dx[b]+=force;dx[a]+=bedForce*.5f;dx[b]+=bedForce*.5f;}
        else {dz[a]-=force;dz[b]+=force;dz[a]+=bedForce*.5f;dz[b]+=bedForce*.5f;}
    }
}
