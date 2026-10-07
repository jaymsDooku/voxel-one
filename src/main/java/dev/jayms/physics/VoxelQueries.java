package dev.jayms.physics;

import dev.jayms.World;
import dev.jayms.net.Blocks;
import dev.jayms.net.model.SparseVoxelOctree;
import java.util.*;

/** Collision geometry shared by walkers, vehicles and debris, including partial/model voxels. */
public final class VoxelQueries {
    public record Box(float x0,float y0,float z0,float x1,float y1,float z1) {
        public Box {new Aabb(x0,y0,z0,x1,y1,z1);if(x0<=Integer.MIN_VALUE+256f||y0<=Integer.MIN_VALUE+256f||z0<=Integer.MIN_VALUE+256f||x1>=Integer.MAX_VALUE-256f||y1>=Integer.MAX_VALUE-256f||z1>=Integer.MAX_VALUE-256f)throw new IllegalArgumentException("Voxel bounds exceed integer grid");}
        public boolean intersects(Box b) {return x0<b.x1&&x1>b.x0&&y0<b.y1&&y1>b.y0&&z0<b.z1&&z1>b.z0;}
    }
    public static List<Box> boxes(World world,Box bounds) {
        List<Box> result=new ArrayList<>();
        if(Math.ceil(bounds.x1)-Math.floor(bounds.x0)>262144||Math.ceil(bounds.y1)-Math.floor(bounds.y0)>262144||Math.ceil(bounds.z1)-Math.floor(bounds.z0)>262144)throw new IllegalArgumentException("Collision query exceeds 262144 cells");
        long volume=(long)(Math.ceil(bounds.x1)-Math.floor(bounds.x0))
                *(long)(Math.ceil(bounds.y1)-Math.floor(bounds.y0))*(long)(Math.ceil(bounds.z1)-Math.floor(bounds.z0));
        if(volume>262144) throw new IllegalArgumentException("Collision query exceeds 262144 cells");
        for(int x=(int)Math.floor(bounds.x0);x<Math.ceil(bounds.x1);x++)
            for(int y=(int)Math.floor(bounds.y0);y<Math.ceil(bounds.y1);y++)
                for(int z=(int)Math.floor(bounds.z0);z<Math.ceil(bounds.z1);z++) {
                    if(!world.isLoaded(x,y,z)) {result.add(new Box(x,y,z,x+1,y+1,z+1));continue;}
                    int material=world.getBlock(x,y,z);
                    if(material==Blocks.AIR||material==Blocks.WATER) continue;
                    var model=world.models().get(material);
                    SparseVoxelOctree octree=material==Blocks.PARTIAL?world.cell(x,y,z):model==null?null:model.definition().voxels();
                    if(octree==null) result.add(new Box(x,y,z,x+1,y+1,z+1));
                    else for(var leaf:octree.leaves()) {
                        float scale=1f/octree.size();
                        Box b=new Box(x+leaf.x()*scale,y+leaf.y()*scale,z+leaf.z()*scale,
                                x+(leaf.x()+leaf.side())*scale,y+(leaf.y()+leaf.side())*scale,z+(leaf.z()+leaf.side())*scale);
                        if(b.intersects(bounds)) result.add(b);
                    }
                }
        return result;
    }
    public static boolean collides(World w,Box b) { return boxes(w,b).stream().anyMatch(b::intersects); }
    private VoxelQueries() {}
}
