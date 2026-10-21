import dev.jayms.World;
import dev.jayms.net.*;
import dev.jayms.net.model.*;
import dev.jayms.net.structure.*;
import java.util.*;
import static dev.jayms.net.structure.StructurePackage.*;

/** Bounded CPU capture integration; does not qualify city/GPU/network budgets. */
public final class SparseCaptureProbe {
    public static void main(String[] args)throws Exception {
        try(var world=new World(42)) {
            world.apply(new Protocol.Edit(512,180,512,Blocks.STONE));var snapshot=world.structureSnapshot();
            byte[] reference=null;
            for(boolean fast:new boolean[]{false,true}) {
                int[] reads={0};
                Source source=new Source(){
                    public SparseVoxelOctree cell(int x,int y,int z){reads[0]++;return snapshot.cell(x,y,z);}
                    public boolean emptyCell(int x,int y,int z){return fast&&snapshot.emptyCell(x,y,z);}
                    public ModelLibrary models(){return snapshot.models();}
                };
                long start=System.nanoTime();
                var asset=StructurePackage.capture("Sparse probe",new Bounds(1024,1,1024,0,0,0),new Position(0,180,0),source,()->false,p->{});
                double ms=(System.nanoTime()-start)/1e6;byte[] bytes=asset.encode();
                if(asset.cells.size()!=1||!asset.cells.get(0).position().equals(new Position(512,0,512)))throw new AssertionError("Sparse capture content");
                if(fast){if(!Arrays.equals(reference,bytes)||reads[0]!=1)throw new AssertionError("Fast capture must preserve package bytes with one octree read");}
                else {reference=bytes;if(reads[0]!=1048576)throw new AssertionError("Fallback must read each coordinate exactly once");}
                System.out.printf(Locale.ROOT,"{\"fast_hint\":%s,\"volume\":1048576,\"occupied\":1,\"octree_reads\":%d,\"capture_ms\":%.3f,\"qualification\":\"single CPU integration run only\"}%n",fast,reads[0],ms);
            }
        }
    }
}
