import dev.jayms.*;
import dev.jayms.net.*;
import dev.jayms.net.model.*;
import dev.jayms.net.structure.*;
import java.util.*;
import java.lang.management.ManagementFactory;

/** CPU validation reuse diagnostic. Authority commit excludes history, save, traffic and rendering. */
public final class PreparedValidationProbe {
    static class Authority implements StructureEngine.Authority {
        final Terrain terrain=new Terrain(42);final WorldVoxels geometry=new WorldVoxels(terrain);final ModelLibrary library=new ModelLibrary();
        final boolean stamps;int reads;
        Authority(boolean stamps){this.stamps=stamps;}
        public Object lock(){return this;}public ModelLibrary models(){return library;}
        public SparseVoxelOctree cell(int x,int y,int z){reads++;return geometry.cell(x,y,z);}
        public Object chunkStamp(ChunkPos p){return stamps?geometry.chunkStamp(p):null;}
        StructurePackage.Source snapshot(){var copy=geometry.snapshot();var models=library.copy();long generation=copy.terrainRevision();return new StructurePackage.Source(){
            public ModelLibrary models(){return models;}public SparseVoxelOctree cell(int x,int y,int z){return copy.cell(x,y,z);}
            public Object chunkStamp(ChunkPos p){return stamps&&copy.terrainRevision()==generation?copy.chunkStamp(p):null;}
        };}
        public void commit(ModelLibrary staged,Map<StructurePackage.Position,SparseVoxelOctree> cells){for(var e:cells.entrySet()){var p=e.getKey();geometry.replaceCell(p.x(),p.y(),p.z(),e.getValue());}}
    }
    public static void main(String[] args)throws Exception {
        if(args.length!=1||!List.of("unknown","stamped").contains(args[0]))throw new IllegalArgumentException("unknown or stamped required");
        boolean stamped=args[0].equals("stamped");var tree=new SparseVoxelOctree(16);
        for(int y=0;y<16;y+=2)for(int z=0;z<16;z+=2)for(int x=0;x<16;x+=2)tree.fill(x,y,z,x+1,y+1,z+1,0xfe123456);
        var cells=new ArrayList<StructurePackage.Cell>();for(int x=0;x<16;x++)cells.add(new StructurePackage.Cell(new StructurePackage.Position(x,0,0),tree,-1));
        var asset=new StructurePackage("Validation diagnostic",new StructurePackage.Bounds(16,1,1,0,0,0),List.of(),cells);
        var bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();if(!bean.isThreadAllocatedMemorySupported())throw new IllegalStateException("Allocation counter unavailable");
        bean.setThreadAllocatedMemoryEnabled(true);long thread=Thread.currentThread().getId();double[] times=new double[20];long[] allocations=new long[20];int reads=-1;
        for(int run=-3;run<20;run++) {
            var authority=new Authority(stamped);var engine=new StructureEngine();
            try(var plan=engine.prepare(asset,new StructurePackage.Position(0,128,0),0,authority.snapshot(),()->false,p->{})) {
                long bytes=bean.getThreadAllocatedBytes(thread),start=System.nanoTime();plan.commit("probe",authority);
                double elapsed=(System.nanoTime()-start)/1e6;bytes=bean.getThreadAllocatedBytes(thread)-bytes;
                if(run>=0){times[run]=elapsed;allocations[run]=bytes;}
                if(reads!=-1&&reads!=authority.reads)throw new AssertionError("Unstable read count");reads=authority.reads;
                for(int x=0;x<16;x++)if(!tree.leaves().equals(authority.geometry.cell(x,128,0).leaves()))throw new AssertionError("Changed geometry");
            }
        }
        Arrays.sort(times);Arrays.sort(allocations);
        System.out.printf(Locale.ROOT,"{\"source\":\"%s\",\"cells\":16,\"leaves_per_cell\":512,\"warmups\":3,\"runs\":20,\"commit_cell_reads\":%d,\"median_ms\":%.6f,\"p95_ms\":%.6f,\"worst_ms\":%.6f,\"median_thread_allocated_bytes\":%d}%n",args[0],reads,(times[9]+times[10])/2,times[18],times[19],(allocations[9]+allocations[10])/2);
    }
}
