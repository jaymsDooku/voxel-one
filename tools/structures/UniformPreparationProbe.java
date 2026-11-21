import dev.jayms.net.*;
import dev.jayms.net.model.*;
import dev.jayms.net.structure.*;
import static dev.jayms.net.structure.StructurePackage.*;
import java.io.*;
import java.lang.management.ManagementFactory;
import java.security.*;
import java.util.*;

/** Actual preparation CPU/allocation only; no commit, renderer, memory peak or city qualification. */
public final class UniformPreparationProbe {
    static final int COUNT=100_000;
    static volatile Object retained;
    static SparseVoxelOctree uniform(int color){var t=new SparseVoxelOctree(16);t.fill(0,0,0,16,16,16,color);return t.freeze();}
    static StructurePackage fixture(String kind)throws Exception {
        var cells=new ArrayList<Cell>();var repeated=uniform(WorldVoxels.encode(Blocks.STONE));
        int count=kind.equals("mixed")?10_000:COUNT;
        for(int i=0;i<count;i++) {
            SparseVoxelOctree t=repeated;
            if(kind.equals("unique")||kind.equals("palette"))t=uniform(0xfe000000|(kind.equals("palette")?i%4096:i));
            if(kind.equals("mixed")&&i%10==0){t=new SparseVoxelOctree(16);t.fill(0,0,0,8,8,8,WorldVoxels.encode(Blocks.BRICKS));t.fill(8,8,8,16,16,16,0xfe000000|i);t.freeze();}
            cells.add(new Cell(new Position(i%128,(i/128)%8,i/(128*8)),t,-1));
        }
        return new StructurePackage("Preparation probe",new Bounds(128,8,128,0,0,0),List.of(),cells);
    }
    static String digest(Map<Position,SparseVoxelOctree> cells)throws Exception {
        var digest=MessageDigest.getInstance("SHA-256");
        try(var out=new DataOutputStream(new BufferedOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(),digest),65536))) {
            for(var e:cells.entrySet()){var p=e.getKey();new Protocol.CellState(p.x(),p.y(),p.z(),e.getValue()).write(out);}
        }
        return HexFormat.of().formatHex(digest.digest());
    }
    static final class Authority implements StructureEngine.Authority {
        final WorldVoxels world=new WorldVoxels(new Terrain(42));final ModelLibrary models=new ModelLibrary();
        public Object lock(){return this;}public ModelLibrary models(){return models;}
        public SparseVoxelOctree cell(int x,int y,int z){return world.cell(x,y,z);}
        public void commit(ModelLibrary next,Map<Position,SparseVoxelOctree> cells) {
            models.replaceWith(next);for(var e:cells.entrySet()){var p=e.getKey();world.replaceCell(p.x(),p.y(),p.z(),e.getValue());}
        }
    }
    static long heap()throws Exception {System.gc();Thread.sleep(100);System.gc();Thread.sleep(100);return Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory();}
    static void liveSample(StructurePackage asset,String label,int run)throws Exception {
        long baseline=heap();var authority=new Authority();var engine=new StructureEngine();
        try(var prepared=engine.prepare(asset,new Position(320,180,320),0,authority,()->false,p->{})) {
            prepared.commit("probe",authority);retained=new Object[]{authority,engine};long bytes=heap()-baseline;
            if(authority.world.cell(320,180,320).get(0,0,0)!=0xfe000000)throw new AssertionError("Committed world changed");
            var identities=new IdentityHashMap<SparseVoxelOctree,Boolean>();for(var tree:prepared.cells().values())identities.put(tree,Boolean.TRUE);
            if(run>=0)System.out.printf(Locale.ROOT,"{\"implementation\":\"%s\",\"fixture\":\"palette\",\"run\":%d,\"cells\":100000,\"materials\":4096,\"retained_world_and_undo_heap_bytes\":%d,\"distinct_after_tree_objects\":%d}%n",label,run,bytes,identities.size());
            engine.undo("probe",authority);if(!authority.world.cell(320,180,320).isEmpty())throw new AssertionError("Undo failed");
        }finally{retained=null;}
    }
    public static void main(String[] args)throws Exception {
        if(args.length!=2||!List.of("cpu","heap").contains(args[0]))throw new IllegalArgumentException("cpu|heap label required");
        if(args[0].equals("heap")){var asset=fixture("palette");liveSample(asset,args[1],-1);for(int run=0;run<3;run++)liveSample(asset,args[1],run);return;}
        var empty=new SparseVoxelOctree(16).freeze();var models=new ModelLibrary();
        Source source=new Source(){public SparseVoxelOctree cell(int x,int y,int z){return empty;}public ModelLibrary models(){return models;}};
        var bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
        if(!bean.isThreadAllocatedMemorySupported())throw new IllegalStateException("Allocation counter unavailable");bean.setThreadAllocatedMemoryEnabled(true);
        for(String kind:List.of("repeated","unique","palette","mixed")) {
            var asset=fixture(kind);String expected=null;
            for(int run=-3;run<20;run++) {
                var engine=new StructureEngine();long before=bean.getCurrentThreadAllocatedBytes(),start=System.nanoTime();
                try(var prepared=engine.prepare(asset,new Position(320,180,320),0,source,()->false,p->{})) {
                    long duration=System.nanoTime()-start,allocated=bean.getCurrentThreadAllocatedBytes()-before;
                    String hash=digest(prepared.cells());if(expected==null)expected=hash;else if(!expected.equals(hash))throw new AssertionError("Preparation bytes changed");
                    retained=prepared.cells();
                    if(run>=0)System.out.printf(Locale.ROOT,"{\"implementation\":\"%s\",\"fixture\":\"%s\",\"run\":%d,\"cells\":%d,\"leaves\":%d,\"prepare_ms\":%.6f,\"thread_allocated_bytes\":%d,\"cell_wire_sha256\":\"%s\"}%n",args[1],kind,run,asset.cells.size(),asset.leafCount,duration/1e6,allocated,hash);
                }
                retained=null;
            }
        }
    }
}
