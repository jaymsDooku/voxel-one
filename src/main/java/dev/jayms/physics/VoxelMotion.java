package dev.jayms.physics;

import dev.jayms.World;
import org.joml.Vector3f;

/** Axis sweep against exact voxel leaves; a fast body cannot skip a thin obstacle. */
public final class VoxelMotion {
    public static float allowed(World world,VoxelQueries.Box b,float delta,int axis) {
        if(!Float.isFinite(delta)||axis<0||axis>2) throw new IllegalArgumentException("Invalid sweep");
        if(delta==0) return 0;
        float[] lo={b.x0(),b.y0(),b.z0()},hi={b.x1(),b.y1(),b.z1()};
        float[] qlo=lo.clone(),qhi=hi.clone();
        qlo[axis]+=Math.min(0,delta);qhi[axis]+=Math.max(0,delta);
        var query=new VoxelQueries.Box(qlo[0]-.00001f,qlo[1]-.00001f,qlo[2]-.00001f,qhi[0]+.00001f,qhi[1]+.00001f,qhi[2]+.00001f);
        float allowed=delta;
        for(var o:VoxelQueries.boxes(world,query)) {
            float[] ol={o.x0(),o.y0(),o.z0()},oh={o.x1(),o.y1(),o.z1()};
            boolean cross=true;
            for(int a=0;a<3;a++) if(a!=axis&&(hi[a]<=ol[a]+.000001f||lo[a]>=oh[a]-.000001f)) cross=false;
            if(!cross) continue;
            if(delta>0&&hi[axis]<=ol[axis]+.00001f) allowed=Math.min(allowed,Math.max(0,ol[axis]-hi[axis]-.00001f));
            if(delta<0&&lo[axis]>=oh[axis]-.00001f) allowed=Math.max(allowed,Math.min(0,oh[axis]-lo[axis]+.00001f));
        }
        return allowed;
    }
    public static Vector3f groundNormal(World w,Vector3f feet,float radius,float maxDrop) {
        float[] height=new float[3];int i=0;
        for(var offset:new Vector3f[]{new Vector3f(-radius,0,0),new Vector3f(radius,0,0),new Vector3f(0,0,radius)}) {
            Vector3f p=new Vector3f(feet).add(offset);
            float move=allowed(w,new VoxelQueries.Box(p.x-.01f,p.y,p.z-.01f,p.x+.01f,p.y+.01f,p.z+.01f),-maxDrop,1);
            if(move<=-maxDrop+.0001f) return new Vector3f();height[i++]=p.y+move;
        }
        Vector3f a=new Vector3f(2*radius,height[1]-height[0],0),b=new Vector3f(radius,height[2]-height[0],radius);
        Vector3f n=b.cross(a).normalize();if(n.y<0)n.negate();return n;
    }
    private VoxelMotion() {}
}
