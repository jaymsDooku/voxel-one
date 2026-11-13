import dev.jayms.*;
import dev.jayms.net.*;
import dev.jayms.net.model.*;
import dev.jayms.net.structure.*;
import static dev.jayms.net.structure.StructurePackage.*;
import java.nio.file.*;
import java.util.*;
/** Small real offline commit/persistence diagnostic, not city/maximum qualification. */
public final class OfflineStampProbe {
    public static void main(String[] args)throws Exception {
        Path directory=Path.of(args[1]);if(Files.exists(directory))throw new IllegalArgumentException("Fresh synthetic output directory required");Files.createDirectories(directory);
        var tree=new SparseVoxelOctree(16);tree.fill(0,0,0,16,16,16,WorldVoxels.encode(Blocks.STONE));var cells=new ArrayList<Cell>();
        for(int z=0;z<=48;z+=16)for(int x=0;x<=48;x+=16)cells.add(new Cell(new Position(x,0,z),tree,-1));
        var asset=new StructurePackage("Offline proof probe",new Bounds(49,1,49,0,0,0),List.of(),cells);double[] times=new double[20];int reads=-1;
        for(int run=-3;run<20;run++) {
            Path file=directory.resolve("world-"+run+".dat");var local=new LocalGame(file,42);var engine=new StructureEngine();
            try(var world=new World(42,local.models){int reads;@Override public SparseVoxelOctree cell(int x,int y,int z){reads++;return super.cell(x,y,z);}}) {
                try(var plan=engine.prepare(asset,new Position(320,180,320),0,world.structureSnapshot(),()->false,p->{})) {
                    world.reads=0;long start=System.nanoTime();plan.commit("synthetic",local.structureAuthority(world));double ms=(System.nanoTime()-start)/1e6;
                    if(run>=0)times[run]=ms;reads=world.reads;
                }
                for(var cell:cells){var p=cell.position();if(world.sample(320+p.x(),180,320+p.z())!=Blocks.STONE)throw new AssertionError("Placed cell missing");}
                var restored=new LocalGame(file,0);var persisted=new WorldVoxels(new Terrain(42));for(var edit:restored.edits.values())persisted.apply(edit);
                for(var cell:cells){var p=cell.position();if(persisted.type(320+p.x(),180,320+p.z())!=Blocks.STONE)throw new AssertionError("Durable geometry missing");}
                if(args[0].equals("parent")?reads<65536:reads!=16)throw new AssertionError("Unexpected authority read count: "+reads);
            }
        }
        Arrays.sort(times);System.out.printf(Locale.ROOT,"{\"implementation\":\"%s\",\"cells\":16,\"chunks\":16,\"runs\":20,\"commit_cell_reads\":%d,\"median_ms\":%.3f,\"p95_ms\":%.3f,\"worst_ms\":%.3f}%n",args[0],reads,(times[9]+times[10])/2,times[18],times[19]);
    }
}
