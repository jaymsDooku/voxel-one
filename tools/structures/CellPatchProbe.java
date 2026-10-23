import dev.jayms.ChunkPos;
import dev.jayms.net.*;
import dev.jayms.net.model.SparseVoxelOctree;
import java.nio.file.*;
import java.util.*;

/** Geometry-phase diagnostic only; no history/save, mesh, network or city qualification. */
public final class CellPatchProbe {
    static final int WIDTH=128,HEIGHT=16,DEPTH=64;
    static SparseVoxelOctree tree(){var t=new SparseVoxelOctree(16);t.fill(0,0,0,16,16,16,WorldVoxels.encode(Blocks.STONE));return t;}
    static void fill(WorldVoxels world,SparseVoxelOctree tree){for(int y=128;y<128+HEIGHT;y++)for(int z=0;z<DEPTH;z++)for(int x=0;x<WIDTH;x++)world.replaceCell(x,y,z,tree);}
    static void fill(WorldVoxels.CellPatch patch,SparseVoxelOctree tree){for(int y=128;y<128+HEIGHT;y++)for(int z=0;z<DEPTH;z++)for(int x=0;x<WIDTH;x++)patch.replaceCell(x,y,z,tree);}
    static void check(WorldVoxels candidate,WorldVoxels source){
        for(int y=128;y<128+HEIGHT;y++)for(int z=0;z<DEPTH;z++)for(int x=0;x<WIDTH;x++)if(candidate.type(x,y,z)!=Blocks.STONE||source.type(x,y,z)!=0)throw new AssertionError("Placement isolation");
        for(int i=0;i<40000;i++)if(candidate.type(2000+i%500,128,2000+i/500)!=Blocks.BRICKS)throw new AssertionError("Unrelated geometry lost");
    }
    public static void main(String[] args)throws Exception {
        if(args.length!=1||Files.exists(Path.of(args[0])))throw new IllegalArgumentException("Fresh sanitized result path required");
        var source=new WorldVoxels(new Terrain(42));for(int i=0;i<40000;i++)source.apply(new Protocol.Edit(2000+i%500,128,2000+i/500,Blocks.BRICKS));
        var chunks=new ArrayList<ChunkPos>();for(int z=0;z<DEPTH;z+=16)for(int x=0;x<WIDTH;x+=16)chunks.add(ChunkPos.fromBlock(x,128,z));
        var tree=tree();double[] legacy=new double[20],capture=new double[20],stage=new double[20],merge=new double[20],guarded=new double[20];
        for(int run=-3;run<20;run++) {
            long begin=System.nanoTime();WorldVoxels baseline;
            synchronized(source){baseline=source.snapshot();fill(baseline,tree);}long baselineTime=System.nanoTime()-begin;check(baseline,source);
            WorldVoxels.CellPatch patch;begin=System.nanoTime();synchronized(source){patch=source.captureCellPatch(chunks);}long captureTime=System.nanoTime()-begin;
            begin=System.nanoTime();fill(patch,tree);long stageTime=System.nanoTime()-begin;
            begin=System.nanoTime();WorldVoxels candidate;synchronized(source){candidate=patch.merge(source);}long mergeTime=System.nanoTime()-begin;if(candidate==null)throw new AssertionError("Unexpected fallback");check(candidate,source);
            if(run>=0){legacy[run]=baselineTime/1e6;capture[run]=captureTime/1e6;stage[run]=stageTime/1e6;merge[run]=mergeTime/1e6;guarded[run]=(captureTime+mergeTime)/1e6;}
        }
        String result="{\"scope\":\"Geometry phase only, 131072 uniform cells in 32 chunks, 40000 unrelated overrides. Three warmups, 20 measured iterations; same live source, baseline first. Not city/max, whole placement, peak memory, GPU or network qualification.\",\"legacy_guarded_ms\":"+stats(legacy)+",\"capture_guarded_ms\":"+stats(capture)+",\"cell_staging_outside_lock_ms\":"+stats(stage)+",\"merge_guarded_ms\":"+stats(merge)+",\"combined_guarded_ms\":"+stats(guarded)+"}";
        Files.writeString(Path.of(args[0]),result+"\n");
    }
    static String stats(double[] values){Arrays.sort(values);return String.format(Locale.ROOT,"{\"median\":%.6f,\"p95\":%.6f,\"worst\":%.6f}",(values[9]+values[10])/2,values[18],values[19]);}
}
