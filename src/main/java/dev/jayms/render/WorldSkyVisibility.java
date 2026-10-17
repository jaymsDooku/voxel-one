package dev.jayms.render;

import dev.jayms.*;
import dev.jayms.net.*;
import java.util.*;
import dev.jayms.net.model.*;

/** Sparse vertical sky paths for loaded geometry beyond the local GI volume.
 * Exact leaf footprints block roofs without allocating a planet-wide light grid. */
public final class WorldSkyVisibility {
    public static final int MAX_COLUMN_SEGMENTS=128,MAX_INTS=8_000_000;
    private record Column(int x,int z) implements Comparable<Column>{
        public int compareTo(Column b){int c=Integer.compare(x,b.x);return c==0?Integer.compare(z,b.z):c;}
    }
    private record Segment(float x0,float y0,float z0,float x1,float y1,float z1,float transmission){}
    public record Geometry(ChunkPos position,SparseVoxelOctree root) {}
    public record EditedCell(int x,int y,int z,SparseVoxelOctree root) {}
    public record Input(List<Geometry> chunks,List<EditedCell> edits,ModelLibrary models) {}
    public final int[] data;
    public final int columns;
    /** Optional bounded GPU directory; headers/segments keep their reference encoding. */
    public final int indexOrigin,indexSpan,indexOffset;
    private WorldSkyVisibility(int[] payload,int columns){
        this.columns=columns;
        long span=columns==0?0:(long)payload[(columns-1)*4]-payload[0]+1;
        if(span<=0||span>512||payload.length+span*3>MAX_INTS){data=payload;indexOrigin=indexSpan=indexOffset=0;return;}
        indexOrigin=payload[0];indexSpan=(int)span;indexOffset=payload.length;
        data=Arrays.copyOf(payload,payload.length+indexSpan*3);
        int cursor=0;
        while(cursor<columns){
            int first=cursor,x=payload[cursor*4],z=payload[cursor*4+1];
            while(cursor<columns&&payload[cursor*4]==x)cursor++;
            int count=cursor-first,lastZ=payload[(cursor-1)*4+1],p=indexOffset+(x-indexOrigin)*3;
            data[p]=z;data[p+1]=(long)lastZ-z+1==count?count:-1;data[p+2]=first;
        }
    }
    private int payloadLength(){return indexSpan==0?data.length:indexOffset;}
    public static WorldSkyVisibility build(World world){
        var paths=new TreeMap<Column,List<Segment>>();
        for(var entry:world.getLoadedChunks().entrySet()){
            var p=entry.getKey();
            // Empty loaded chunks still establish known outdoor columns.
            for(int x=0;x<16;x++)for(int z=0;z<16;z++)paths.computeIfAbsent(new Column(p.chunkX()*16+x,p.chunkZ()*16+z),k->new ArrayList<>());
            for(var leaf:entry.getValue().snapshot().leaves()){
                int type=WorldVoxels.decode(leaf.color());if(type==Blocks.AIR)continue;
                float x=p.chunkX()*16+leaf.x()/16f,y=p.chunkY()*16+leaf.y()/16f,z=p.chunkZ()*16+leaf.z()/16f,size=leaf.side()/16f;
                expand(paths,world.models(),type,x,y,z,size);
            }
        }
        // Unloaded edited roofs must also occlude the loaded geometry below them.
        var cells=new HashSet<String>();
        for(var edit:world.editsSnapshot().values())if(!world.isLoaded(edit.x(),edit.y(),edit.z())&&cells.add(edit.x()+","+edit.y()+","+edit.z())){
            for(var leaf:world.cell(edit.x(),edit.y(),edit.z()).leaves()){
                int type=WorldVoxels.decode(leaf.color());if(type==Blocks.AIR)continue;
                float x=edit.x()+leaf.x()/16f,y=edit.y()+leaf.y()/16f,z=edit.z()+leaf.z()/16f,size=leaf.side()/16f;
                expand(paths,world.models(),type,x,y,z,size);
            }
        }
        return pack(paths);
    }
    /** Worker entry point. Inputs contain immutable roots and no live World or Chunk. */
    public static WorldSkyVisibility build(Input input) {
        var paths=new TreeMap<Column,List<Segment>>();
        for(var geometry:input.chunks){
            var p=geometry.position;
            for(int x=0;x<16;x++)for(int z=0;z<16;z++)paths.computeIfAbsent(new Column(p.chunkX()*16+x,p.chunkZ()*16+z),k->new ArrayList<>());
            for(var leaf:geometry.root.leaves()) {
                int type=WorldVoxels.decode(leaf.color());if(type==Blocks.AIR)continue;
                expand(paths,input.models,type,p.chunkX()*16+leaf.x()/16f,p.chunkY()*16+leaf.y()/16f,p.chunkZ()*16+leaf.z()/16f,leaf.side()/16f);
            }
        }
        for(var cell:input.edits)for(var leaf:cell.root.leaves()) {
            int type=WorldVoxels.decode(leaf.color());if(type==Blocks.AIR)continue;
            expand(paths,input.models,type,cell.x+leaf.x()/16f,cell.y+leaf.y()/16f,cell.z+leaf.z()/16f,leaf.side()/16f);
        }
        return pack(paths);
    }
    private static WorldSkyVisibility pack(TreeMap<Column,List<Segment>> paths) {
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
    /** Merge immutable, disjoint 16x16 column tiles without rebuilding their geometry. */
    public static WorldSkyVisibility merge(List<WorldSkyVisibility> parts) {
        int count=0,length=0;for(var p:parts){count=Math.addExact(count,p.columns);if(p.columns!=0)length=Math.addExact(length,p.payloadLength());}
        if(length>MAX_INTS)throw new IllegalStateException("Loaded sky visibility exceeds bounded buffer capacity");
        int[] data=new int[Math.max(1,length)];
        final class Cursor { final WorldSkyVisibility p;int i;Cursor(WorldSkyVisibility p){this.p=p;} }
        var queue=new PriorityQueue<Cursor>(Comparator.<Cursor>comparingInt(c->c.p.data[c.i]).thenComparingInt(c->c.p.data[c.i+1]));
        for(var p:parts)if(p.columns!=0)queue.add(new Cursor(p));int header=0,offset=count*4;
        while(!queue.isEmpty()) {
            if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException();
            var c=queue.remove();int n=c.p.data[c.i+3];data[header++]=c.p.data[c.i];data[header++]=c.p.data[c.i+1];
            data[header++]=n<0?c.p.data[c.i+2]:offset;data[header++]=n;
            if(n>=0){System.arraycopy(c.p.data,c.p.data[c.i+2],data,offset,n*7);offset+=n*7;}
            c.i+=4;if(c.i<c.p.columns*4)queue.add(c);
        }
        return new WorldSkyVisibility(data,count);
    }
    /** Same model footprint contract for streamed chunks and persisted edits. */
    private static void expand(Map<Column,List<Segment>> paths,ModelLibrary models,int type,float x,float y,float z,float size){
        if(Blocks.isModel(type)){
            var model=models.get(type);
            if(model==null){add(paths,new Segment(x,y,z,x+size,y+size,z+size,0));return;}
            // A merged uniform octree leaf can contain several repeated model cells.
            if(size<1){add(paths,new Segment(x,y,z,x+size,y+size,z+size,0));return;}
            float scale=1f/model.definition().voxels().size();
            var leaves=model.definition().voxels().leaves();
            for(int dx=0;dx<(int)size;dx++)for(int dy=0;dy<(int)size;dy++)for(int dz=0;dz<(int)size;dz++)
                for(var cell:leaves)add(paths,new Segment(x+dx+cell.x()*scale,y+dy+cell.y()*scale,z+dz+cell.z()*scale,x+dx+(cell.x()+cell.side())*scale,y+dy+(cell.y()+cell.side())*scale,z+dz+(cell.z()+cell.side())*scale,0));
        }else add(paths,new Segment(x,y,z,x+size,y+size,z+size,type==Blocks.GLASS||type==Blocks.WATER?.7f:0));
    }
    private static List<Segment> compact(List<Segment> source){
        // Octrees split a 1/32 planar roof into many cubes. Merge only touching
        // rectangles with identical height/transmission; retain every real gap.
        // A full-column opaque ceiling blocks every lower sky ray, even across
        // caves. Keep layers and fractional footprints above it exactly.
        Segment ceiling=null;
        for(var a:source)if(a.transmission==0&&a.x1-a.x0==1&&a.z1-a.z0==1&&(ceiling==null||a.y1>ceiling.y1))ceiling=a;
        List<Segment> result=source;
        if(ceiling!=null){result=new ArrayList<>();for(var a:source)if(a==ceiling||a.y1>ceiling.y1)result.add(a);}
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
        if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException();
        for(int x=(int)Math.floor(a.x0);x<(int)Math.ceil(a.x1);x++)for(int z=(int)Math.floor(a.z0);z<(int)Math.ceil(a.z1);z++)
            paths.computeIfAbsent(new Column(x,z),k->new ArrayList<>()).add(new Segment(Math.max(x,a.x0),a.y0,Math.max(z,a.z0),Math.min(x+1,a.x1),a.y1,Math.min(z+1,a.z1),a.transmission));
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
