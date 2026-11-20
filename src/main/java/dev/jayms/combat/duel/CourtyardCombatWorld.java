package dev.jayms.combat.duel;

import dev.jayms.combat.*;
import dev.jayms.combat.arena.BasaltCourtyard;
import dev.jayms.physics.VoxelQueries;
import java.util.*;

/** Server geometry over the actual resident arena, including partial voxel edits. */
public final class CourtyardCombatWorld implements CombatWorld {
    private final BasaltCourtyard.Fixture arena;
    public CourtyardCombatWorld(BasaltCourtyard.Fixture arena) { this.arena = Objects.requireNonNull(arena); }
    private static VoxelQueries.Box box(double x0,double y0,double z0,double x1,double y1,double z1) {
        return new VoxelQueries.Box((float)x0,(float)y0,(float)z0,(float)x1,(float)y1,(float)z1);
    }
    @Override public OptionalDouble firstSolid(Vec3 from, Vec3 to, double radius) {
        if (!Double.isFinite(radius) || radius < 0 || radius > 2 || from.distance(to) > 16)
            throw new IllegalArgumentException("Sweep bound");
        double e=radius+.0001, nearest=Double.POSITIVE_INFINITY;
        var query=box(Math.min(from.x(),to.x())-e,Math.min(from.y(),to.y())-e,Math.min(from.z(),to.z())-e,
                Math.max(from.x(),to.x())+e,Math.max(from.y(),to.y())+e,Math.max(from.z(),to.z())+e);
        for (var b:VoxelQueries.boxes(arena.world(),query)) nearest=Math.min(nearest,sphere(from,to,b,radius));
        return nearest<=1 ? OptionalDouble.of(nearest) : OptionalDouble.empty();
    }
    // Squared point/box distance is quadratic between slab crossings. This handles rounded
    // sphere corners exactly, rather than treating the projectile as an expanded cube.
    private static double sphere(Vec3 a,Vec3 b,VoxelQueries.Box box,double r) {
        double[] s={a.x(),a.y(),a.z()}, d={b.x()-a.x(),b.y()-a.y(),b.z()-a.z()};
        double[] lo={box.x0(),box.y0(),box.z0()}, hi={box.x1(),box.y1(),box.z1()};
        TreeSet<Double> cuts=new TreeSet<>(List.of(0d,1d));
        for(int i=0;i<3;i++) if(d[i]!=0) for(double face:new double[]{lo[i],hi[i]}) {
            double t=(face-s[i])/d[i]; if(t>0 && t<1) cuts.add(t);
        }
        var times=new ArrayList<>(cuts);
        for(int k=0;k<times.size()-1;k++) {
            double start=times.get(k),end=times.get(k+1),mid=(start+end)*.5;
            double aa=0,bb=0,cc=-r*r;
            for(int i=0;i<3;i++) {
                double p=s[i]+d[i]*mid;
                if(p<lo[i] || p>hi[i]) {
                    double offset=s[i]-(p<lo[i]?lo[i]:hi[i]);
                    aa+=d[i]*d[i]; bb+=2*offset*d[i]; cc+=offset*offset;
                }
            }
            if((aa*start+bb)*start+cc<=1e-12) return start;
            double disc=bb*bb-4*aa*cc;
            if(aa>0 && disc>=0) {
                double root=(-bb-Math.sqrt(disc))/(2*aa);
                if(root>=start-1e-10 && root<=end+1e-10) return Math.max(start,Math.min(end,root));
            }
        }
        return Double.POSITIVE_INFINITY;
    }
    @Override public boolean safeLanding(Vec3 feet,double halfWidth,double height) {
        return arena.safeLanding(feet,halfWidth,height);
    }
    @Override public Motion move(Vec3 feet,Vec3 velocity,double halfWidth,double height) {
        if(halfWidth<=0 || halfWidth>2 || height<=0 || height>4 || !Double.isFinite(halfWidth+height)
                || velocity.length()>8 || Math.abs(feet.x())>33 || Math.abs(feet.z())>41 || feet.y()<.9 || feet.y()>24)
            throw new IllegalArgumentException("Body sweep bound");
        double[] p={feet.x(),feet.y(),feet.z()}, v={velocity.x(),velocity.y(),velocity.z()};
        double[] low={halfWidth,0,halfWidth}, high={halfWidth,height,halfWidth};
        double[] min={-31+halfWidth,1,-39+halfWidth}, max={31-halfWidth,24-height,39-halfWidth};
        for(int axis:new int[]{1,0,2}) {
            double move=Math.max(min[axis]-p[axis],Math.min(max[axis]-p[axis],v[axis]));
            double[] q0={p[0]-low[0],p[1],p[2]-low[2]},q1={p[0]+high[0],p[1]+high[1],p[2]+high[2]};
            q0[axis]+=Math.min(0,move)-.00001; q1[axis]+=Math.max(0,move)+.00001;
            for(var b:VoxelQueries.boxes(arena.world(),box(q0[0],q0[1],q0[2],q1[0],q1[1],q1[2]))) {
                double[] bl={b.x0(),b.y0(),b.z0()},bh={b.x1(),b.y1(),b.z1()}; boolean overlap=true;
                for(int other=0;other<3;other++) if(other!=axis)
                    overlap &= p[other]+high[other]>bl[other]+1e-6 && p[other]-low[other]<bh[other]-1e-6;
                if(!overlap) continue;
                if(move>0 && p[axis]+high[axis]<=bl[axis]+1e-6)
                    move=Math.min(move,Math.max(0,bl[axis]-p[axis]-high[axis]));
                if(move<0 && p[axis]-low[axis]>=bh[axis]-1e-6)
                    move=Math.max(move,Math.min(0,bh[axis]-p[axis]+low[axis]));
            }
            if(Math.abs(move-v[axis])>1e-8) v[axis]=0;
            p[axis]+=move;
        }
        Vec3 result=new Vec3(p[0],p[1],p[2]);
        boolean grounded=arena.collides(box(p[0]-halfWidth,p[1]-.002,p[2]-halfWidth,
                p[0]+halfWidth,p[1]-.0001,p[2]+halfWidth));
        return new Motion(result,new Vec3(v[0],v[1],v[2]),grounded);
    }
}
