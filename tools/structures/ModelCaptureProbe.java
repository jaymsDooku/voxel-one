import dev.jayms.net.*;
import dev.jayms.net.model.*;
import dev.jayms.net.structure.*;
import static dev.jayms.net.structure.StructurePackage.*;
import java.util.*;
/** Repeated-model capture diagnostic only, not city/maximum qualification. */
public final class ModelCaptureProbe {
    public static void main(String[] args)throws Exception {
        var voxels=new SparseVoxelOctree(16);voxels.fill(0,0,0,16,16,16,0xff123456);
        var random=new Random(517);
        for(int y=1;y<15;y++)for(int z=1;z<15;z++)for(int x=1;x<15;x++)voxels.set(x,y,z,0xff000000|random.nextInt(1<<24));
        var library=new ModelLibrary();var trees=new SparseVoxelOctree[4];var definitions=new ArrayList<ModelDefinition>();
        for(int i=0;i<4;i++) {
            var entry=library.stageStructureModel(new ModelDefinition("Repeated model "+i,voxels));definitions.add(entry.definition());
            trees[i]=new SparseVoxelOctree(16);trees[i].fill(0,0,0,16,16,16,WorldVoxels.encode(entry.id()));trees[i].freeze();
        }
        Source source=new Source(){public SparseVoxelOctree cell(int x,int y,int z){return trees[x%4];}public ModelLibrary models(){return library;}};
        var bounds=new Bounds(512,1,20,0,0,0);var expected=new ArrayList<Cell>();
        for(int z=0;z<20;z++)for(int x=0;x<512;x++)expected.add(new Cell(new Position(x,0,z),null,x%4));
        byte[] reference=new StructurePackage("Model capture probe",bounds,definitions,expected).encode();double[] times=new double[20];
        for(int trial=-3;trial<20;trial++) {
            long start=System.nanoTime();var captured=StructurePackage.capture("Model capture probe",bounds,new Position(0,0,0),source,()->false,p->{});double ms=(System.nanoTime()-start)/1e6;
            if(!Arrays.equals(reference,captured.encode()))throw new AssertionError("Reference package differs");
            if(trial>=0)times[trial]=ms;
        }
        Arrays.sort(times);
        System.out.printf(Locale.ROOT,"{\"implementation\":\"%s\",\"instances\":10240,\"models\":4,\"runs\":20,\"compressed_bytes\":%d,\"median_ms\":%.3f,\"p95_ms\":%.3f,\"worst_ms\":%.3f}%n",args[0],reference.length,(times[9]+times[10])/2,times[18],times[19]);
    }
}
