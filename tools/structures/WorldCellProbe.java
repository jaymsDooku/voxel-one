import dev.jayms.*;
import dev.jayms.net.*;
import dev.jayms.net.model.*;
import java.nio.file.*;
import java.util.*;

/** Actual owner-thread World geometry/history phase; no GL meshes, save or city qualification. */
public final class WorldCellProbe {
    public static void main(String[] args)throws Exception {
        if(args.length!=1||Files.exists(Path.of(args[0])))throw new IllegalArgumentException("Fresh sanitized result path required");
        var tree=new SparseVoxelOctree(16);for(int i=0;i<4096;i++){int x=i%16,y=i/256,z=i/16%16;tree.fill(x,y,z,x+1,y+1,z+1,0xfe000000|i);}
        var state=new Protocol.CellState(-17,128,-1,tree);var position=ChunkPos.fromBlock(-17,128,-1);var rows=new ArrayList<String>();
        for(boolean resident:new boolean[]{false,true}) {
            double[] replay=new double[20],whole=new double[20];
            for(int run=-3;run<20;run++)try(var before=new World(42);var after=new World(42)) {
                var a=new Chunk();var b=new Chunk();if(resident){before.addChunk(position,a);after.addChunk(position,b);}
                var neighbour=new Protocol.Edit(-18,128,-1,Blocks.STONE);before.apply(neighbour);after.apply(neighbour);
                long begin=System.nanoTime();for(var edit:state.edits())before.apply(edit);long replayTime=System.nanoTime()-begin;
                begin=System.nanoTime();after.replaceCell(-17,128,-1,tree);long wholeTime=System.nanoTime()-begin;
                if(!before.cell(-17,128,-1).leaves().equals(after.cell(-17,128,-1).leaves())||!tree.leaves().equals(after.cell(-17,128,-1).leaves()))throw new AssertionError("Geometry mismatch");
                if(!new ArrayList<>(before.editsSnapshot().entrySet()).equals(new ArrayList<>(after.editsSnapshot().entrySet()))||before.editsVersion()!=after.editsVersion())throw new AssertionError("History/revision mismatch");
                if(after.cell(-18,128,-1).uniform(0,0,0,16)!=WorldVoxels.encode(Blocks.STONE))throw new AssertionError("Neighbour lost");
                if(resident&&(!a.snapshot().leaves().equals(b.snapshot().leaves())||!a.models().equals(b.models())||a.hasFractional()!=b.hasFractional()))throw new AssertionError("Resident metadata mismatch");
                if(run>=0){replay[run]=replayTime/1e6;whole[run]=wholeTime/1e6;}
            }
            rows.add("{\"resident\":"+resident+",\"legacy_leaf_replay_ms\":"+stats(replay)+",\"whole_cell_ms\":"+stats(whole)+"}");
        }
        Files.writeString(Path.of(args[0]),"{\"scope\":\"Actual World owner-thread cell update, 4096 coloured LED leaves, one unchanged neighbour, loaded/unloaded cases. Three warmups, 20 measured runs each; legacy first, state verified outside timing. Not whole placement, save, GL upload, peak memory, city/max or system qualification.\",\"results\":["+String.join(",",rows)+"]}\n");
    }
    static String stats(double[] v){Arrays.sort(v);return String.format(Locale.ROOT,"{\"median\":%.6f,\"p95\":%.6f,\"worst\":%.6f}",(v[9]+v[10])/2,v[18],v[19]);}
}
