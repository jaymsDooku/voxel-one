package dev.jayms.render;

import dev.jayms.*;
import dev.jayms.net.*;
import java.util.*;

/** Sparse vertical sky paths for loaded geometry beyond the local GI volume.
 * Exact leaf footprints block roofs without allocating a planet-wide light grid. */
public final class WorldSkyVisibility {
    public static final int MAX_COLUMN_SEGMENTS=128,MAX_INTS=8_000_000;
    private record Column(int x,int z) implements Comparable<Column>{
        public int compareTo(Column b){int c=Integer.compare(x,b.x);return c==0?Integer.compare(z,b.z):c;}
    }
    private record Segment(float x0,float y0,float z0,float x1,float y1,float z1,float transmission){}
    public final int[] data;
    public final int columns;
    private WorldSkyVisibility(int[] data,int columns){this.data=data;this.columns=columns;}
    public static WorldSkyVisibility build(World world){
        var paths=new TreeMap<Column,List<Segment>>();
        for(var entry:world.getLoadedChunks().entrySet()){
            var p=entry.getKey();
            // Empty loaded chunks still establish known outdoor columns.
            for(int x=0;x<16;x++)for(int z=0;z<16;z++)paths.computeIfAbsent(new Column(p.chunkX()*16+x,p.chunkZ()*16+z),k->new ArrayList<>());
            for(var leaf:entry.getValue().snapshot().leaves()){
                int type=WorldVoxels.decode(leaf.color());if(type==Blocks.AIR)continue;
                float x=p.chunkX()*16+leaf.x()/16f,y=p.chunkY()*16+leaf.y()/16f,z=p.chunkZ()*16+leaf.z()/16f,size=leaf.side()/16f;
                if(Blocks.isModel(type)){
                    var model=world.models().get(type);
                    if(model==null){add(paths,new Segment(x,y,z,x+size,y+size,z+size,0));continue;}
                    // A merged uniform octree leaf can contain several repeated model cells.
                    if(size<1){add(paths,new Segment(x,y,z,x+size,y+size,z+size,0));continue;}
                    float scale=1f/model.definition().voxels().size();
                    var leaves=model.definition().voxels().leaves();
                    for(int dx=0;dx<(int)size;dx++)for(int dy=0;dy<(int)size;dy++)for(int dz=0;dz<(int)size;dz++)
                        for(var cell:leaves)add(paths,new Segment(x+dx+cell.x()*scale,y+dy+cell.y()*scale,z+dz+cell.z()*scale,x+dx+(cell.x()+cell.side())*scale,y+dy+(cell.y()+cell.side())*scale,z+dz+(cell.z()+cell.side())*scale,0));
                }else add(paths,new Segment(x,y,z,x+size,y+size,z+size,type==Blocks.GLASS||type==Blocks.WATER?.7f:0));
            }
        }
        // Unloaded edited roofs must also occlude the loaded geometry below them.
        var cells=new HashSet<String>();
        for(var edit:world.editsSnapshot().values())if(!world.isLoaded(edit.x(),edit.y(),edit.z())&&cells.add(edit.x()+","+edit.y()+","+edit.z())){
            for(var leaf:world.cell(edit.x(),edit.y(),edit.z()).leaves()){
                int type=WorldVoxels.decode(leaf.color());if(type==Blocks.AIR)continue;
                float x=edit.x()+leaf.x()/16f,y=edit.y()+leaf.y()/16f,z=edit.z()+leaf.z()/16f,size=leaf.side()/16f;
                add(paths,new Segment(x,y,z,x+size,y+size,z+size,type==Blocks.GLASS||type==Blocks.WATER?.7f:0));
            }
        }
        paths.replaceAll((column,segments)->compact(segments));
        int count=paths.size(),length=count*4;
        for(var segments:paths.values())length=Math.addExact(length,segments.size()<=MAX_COLUMN_SEGMENTS?segments.size()*7:0);
        if(length>MAX_INTS)throw new IllegalStateException("Loaded sky visibility exceeds bounded buffer capacity");
        int[] packed=new int[Math.max(1,length)];int header=0,offset=count*4;
        for(var entry:paths.entrySet()){
            var segments=entry.getValue();segments.sort(Comparator.comparingDouble(Segment::y1).reversed());
            packed[header++]=entry.getKey().x;packed[header++]=entry.getKey().z;packed[header++]=offset;
            if(segments.size()>MAX_COLUMN_SEGMENTS){packed[header-1]=Float.floatToRawIntBits(segments.get(0).y1);packed[header++]=-1;continue;} // Conservative overflow, never an open roof.
            packed[header++]=segments.size();
            for(var a:segments)for(float value:new float[]{a.x0,a.y0,a.z0,a.x1,a.y1,a.z1,a.transmission})packed[offset++]=Float.floatToRawIntBits(value);
        }
        return new WorldSkyVisibility(packed,count);
    }
    private static List<Segment> compact(List<Segment> source){
        // Octrees split a 1/32 planar roof into many cubes. Merge only touching
        // rectangles with identical height/transmission; retain every real gap.
        List<Segment> result=source;
        for(boolean axisX:new boolean[]{true,false}){
            result.sort(Comparator.comparingDouble(Segment::y0).thenComparingDouble(Segment::y1).thenComparingDouble(Segment::transmission)
                .thenComparingDouble(a->axisX?a.z0:a.x0).thenComparingDouble(a->axisX?a.z1:a.x1).thenComparingDouble(a->axisX?a.x0:a.z0));
            var merged=new ArrayList<Segment>();
            for(var a:result){
                if(!merged.isEmpty()){
                    var b=merged.get(merged.size()-1);
                    if(a.y0==b.y0&&a.y1==b.y1&&a.transmission==b.transmission&&(axisX?a.z0==b.z0&&a.z1==b.z1&&a.x0==b.x1:a.x0==b.x0&&a.x1==b.x1&&a.z0==b.z1)){
                        merged.set(merged.size()-1,new Segment(b.x0,b.y0,b.z0,axisX?a.x1:b.x1,b.y1,axisX?b.z1:a.z1,b.transmission));continue;
                    }
                }merged.add(a);
            }result=merged;
        }return result;
    }
    private static void add(Map<Column,List<Segment>> paths,Segment a){
        for(int x=(int)Math.floor(a.x0);x<(int)Math.ceil(a.x1);x++)for(int z=(int)Math.floor(a.z0);z<(int)Math.ceil(a.z1);z++)paths.computeIfAbsent(new Column(x,z),k->new ArrayList<>()).add(a);
    }
    /** CPU mirror for coverage and fine-geometry regression checks. */
    public float sample(float x,float y,float z){return sampleKnown(x,y,z,0);}
    public float sampleKnown(float x,float y,float z,float unknown){
        int lo=0,hi=columns-1,cx=(int)Math.floor(x),cz=(int)Math.floor(z);
        while(lo<=hi){int m=(lo+hi)>>>1,i=m*4,c=Integer.compare(cx,data[i]);if(c==0)c=Integer.compare(cz,data[i+1]);
            if(c<0)hi=m-1;else if(c>0)lo=m+1;else{
                int count=data[i+3];if(count<0)return y>=Float.intBitsToFloat(data[i+2])?1:0;float t=1;
                for(int s=0;s<count;s++){int a=data[i+2]+s*7;float[] v=new float[7];for(int k=0;k<7;k++)v[k]=Float.intBitsToFloat(data[a+k]);
                    if(y>=v[4])break;
                    if(x>=v[0]&&x<v[3]&&z>=v[2]&&z<v[5])t*=v[6];if(t==0)return 0;
                }return t;
            }
        }return unknown;
    }
}
