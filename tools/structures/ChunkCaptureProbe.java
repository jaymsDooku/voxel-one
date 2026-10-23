import dev.jayms.World;
import dev.jayms.ChunkPos;
import dev.jayms.net.*;
import dev.jayms.net.model.*;
import dev.jayms.net.structure.*;
import java.nio.file.*;
import java.util.*;
import static dev.jayms.net.structure.StructurePackage.*;

/** Sparse bounding-volume capture diagnostic, not the populated city/maximum fixtures. */
public final class ChunkCaptureProbe {
    static final class View implements Source {
        final Source snapshot;final boolean hints,count;long cellChecks,chunkChecks,treeReads;
        View(Source snapshot,boolean hints,boolean count){this.snapshot=snapshot;this.hints=hints;this.count=count;}
        public SparseVoxelOctree cell(int x,int y,int z){if(count)treeReads++;return snapshot.cell(x,y,z);}
        public boolean emptyCell(int x,int y,int z){if(count)cellChecks++;return snapshot.emptyCell(x,y,z);}
        public boolean emptyChunk(ChunkPos p){if(count)chunkChecks++;return hints&&snapshot.emptyChunk(p);}
        public ModelLibrary models(){return snapshot.models();}
    }
    public static void main(String[] args)throws Exception {
        if(args.length!=1||Files.exists(Path.of(args[0])))throw new IllegalArgumentException("Fresh result path required");
        var rows=new ArrayList<String>();
        try(var world=new World(42)) {
            world.apply(new Protocol.Edit(257,180,257,Blocks.BRICKS));var snapshot=world.structureSnapshot();
            for(var bounds:List.of(new Bounds(512,128,512,0,0,0),new Bounds(1024,256,1024,0,0,0))) {
                byte[] reference=null;
                for(boolean hints:new boolean[]{false,true}) {
                    var counted=new View(snapshot,hints,true);var sample=capture(bounds,counted);byte[] encoded=sample.encode();
                    if(hints){if(!Arrays.equals(reference,encoded)||counted.cellChecks!=4096||counted.treeReads!=1)throw new AssertionError("Hint semantics/read counts");}
                    else{reference=encoded;if(counted.cellChecks!=(long)bounds.x()*bounds.y()*bounds.z()||counted.treeReads!=1)throw new AssertionError("Fallback counts");}
                    var source=new View(snapshot,hints,false);double[] times=new double[20];
                    for(int run=-3;run<20;run++) {
                        long start=System.nanoTime();var asset=capture(bounds,source);double ms=(System.nanoTime()-start)/1e6;
                        if(!Arrays.equals(reference,asset.encode()))throw new AssertionError("Package changed");
                        if(run>=0)times[run]=ms;
                    }
                    rows.add("{\"width\":"+bounds.x()+",\"height\":"+bounds.y()+",\"depth\":"+bounds.z()+",\"chunk_hints\":"+hints+",\"untimed_cell_checks\":"+counted.cellChecks+",\"untimed_chunk_checks\":"+counted.chunkChecks+",\"untimed_tree_reads\":"+counted.treeReads+",\"capture_ms\":"+stats(times)+"}");
                }
            }
        }
        Files.writeString(Path.of(args[0]),"{\"scope\":\"Actual World snapshot and capture, one whole-cell edit at y180 inside otherwise air bounding boxes. Not the populated city/max fixtures. Untimed counters separate from timing. Three warmups and 20 measured runs per mode, hint-disabled control first; both modes use current chunk-run capture code. Encode/byte validation outside timing. No placement, save, GPU, network, memory or system qualification.\",\"results\":["+String.join(",",rows)+"]}\n");
    }
    static StructurePackage capture(Bounds bounds,Source source)throws Exception {
        var asset=StructurePackage.capture("Sparse capture diagnostic",bounds,new Position(0,128,0),source,()->false,p->{});
        if(asset.cells.size()!=1||!asset.cells.get(0).position().equals(new Position(257,52,257)))throw new AssertionError("Capture content");return asset;
    }
    static String stats(double[] v){Arrays.sort(v);return String.format(Locale.ROOT,"{\"median\":%.6f,\"p95\":%.6f,\"worst\":%.6f}",(v[9]+v[10])/2,v[18],v[19]);}
}
