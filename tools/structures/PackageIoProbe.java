import dev.jayms.net.*;
import dev.jayms.net.model.SparseVoxelOctree;
import dev.jayms.net.structure.*;
import java.util.*;
/** Admitted geometry-only package codec probe, not city/maximum qualification. */
public final class PackageIoProbe {
    public static void main(String[] args)throws Exception {
        var trees=new SparseVoxelOctree[2];
        for(int i=0;i<2;i++) {trees[i]=new SparseVoxelOctree(16);trees[i].fill(0,0,0,16,16,16,WorldVoxels.encode(i==0?Blocks.BRICKS:Blocks.STONE));}
        var cells=new ArrayList<StructurePackage.Cell>();var random=new Random(613);
        for(int z=0;z<256;z++)for(int x=0;x<256;x++)cells.add(new StructurePackage.Cell(new StructurePackage.Position(x,0,z),trees[random.nextInt(2)],-1));
        var asset=new StructurePackage("Codec probe",new StructurePackage.Bounds(256,1,256,0,0,0),List.of(),cells);
        byte[] bytes=asset.encode();double[][] times=new double[2][20];
        for(int trial=-3;trial<20;trial++) {
            long start=System.nanoTime();byte[] encoded=asset.encode();double encode=(System.nanoTime()-start)/1e6;
            if(!Arrays.equals(bytes,encoded))throw new AssertionError("Unstable encoding");
            start=System.nanoTime();var decoded=StructurePackage.decode(bytes);double decode=(System.nanoTime()-start)/1e6;
            if(decoded.cells.size()!=65536||decoded.leafCount!=65536||!decoded.bounds.equals(asset.bounds))throw new AssertionError("Decode mismatch");
            if(trial>=0){times[0][trial]=encode;times[1][trial]=decode;}
        }
        for(int mode=0;mode<2;mode++) {
            Arrays.sort(times[mode]);var t=times[mode];
            System.out.printf(Locale.ROOT,"{\"implementation\":\"%s\",\"operation\":\"%s\",\"cells\":65536,\"models\":0,\"compressed_bytes\":%d,\"runs\":20,\"median_ms\":%.3f,\"p95_ms\":%.3f,\"worst_ms\":%.3f}%n",args[0],mode==0?"encode":"decode",bytes.length,(t[9]+t[10])/2,t[18],t[19]);
        }
    }
}
