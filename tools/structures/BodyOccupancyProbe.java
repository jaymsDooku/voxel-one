import dev.jayms.net.Protocol;
import dev.jayms.net.Blocks;
import dev.jayms.net.WorldVoxels;
import dev.jayms.net.model.SparseVoxelOctree;
import dev.jayms.net.structure.StructureOccupancy;
import dev.jayms.net.structure.StructurePackage.Position;
import java.util.*;

/** CPU collision microbenchmark only; no package admission, world mutation, GPU or network. */
public final class BodyOccupancyProbe {
    public static void main(String[] args) throws Exception {
        var solid=new SparseVoxelOctree(16);solid.fill(0,0,0,16,16,16,WorldVoxels.encode(Blocks.STONE));solid.freeze();
        var cells=new HashMap<Position,SparseVoxelOctree>(1_400_000);
        for(int z=0;z<1000;z++)for(int x=0;x<1000;x++)cells.put(new Position(x,128,z),solid);
        var players=new ArrayList<Protocol.Pose>();
        for(int i=0;i<32;i++)players.add(new Protocol.Pose(i+1,2000+i,128,2000,0,0));
        for(int i=0;i<3;i++)StructureOccupancy.validate(cells,null,players);
        var ns=new long[20];
        for(int i=0;i<ns.length;i++){long start=System.nanoTime();StructureOccupancy.validate(cells,null,players);ns[i]=System.nanoTime()-start;}
        Arrays.sort(ns);
        System.out.printf(Locale.ROOT,"{\"cells\":1000000,\"bodies\":32,\"warmups\":3,\"runs\":20,\"median_ms\":%.6f,\"p95_ms\":%.6f,\"worst_ms\":%.6f,\"qualification\":\"CPU occupancy microbenchmark only; not city placement or distributed clients\"}%n",(ns[9]+ns[10])/2e6,ns[18]/1e6,ns[19]/1e6);
    }
}
