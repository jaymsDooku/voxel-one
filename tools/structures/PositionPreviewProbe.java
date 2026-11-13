import dev.jayms.net.*;
import dev.jayms.net.model.*;
import dev.jayms.net.structure.*;
import java.lang.management.ManagementFactory;
import java.util.*;
import static dev.jayms.net.structure.StructurePackage.*;

/** Calls production preview over an admitted fixture. Not full city/system qualification. */
public final class PositionPreviewProbe {
    static volatile boolean consumed;
    static final class Snapshot implements Source {
        final boolean hinted,blocked;int reads,hints;
        final SparseVoxelOctree air=new SparseVoxelOctree(16).freeze(),stone;
        Snapshot(boolean hinted,boolean blocked){this.hinted=hinted;this.blocked=blocked;stone=new SparseVoxelOctree(16);stone.fill(0,0,0,16,16,16,WorldVoxels.encode(Blocks.STONE));stone.freeze();}
        public boolean emptyCell(int x,int y,int z){hints++;return hinted&&!blocked;}
        public SparseVoxelOctree cell(int x,int y,int z){reads++;return blocked?stone:air;}
        public ModelLibrary models(){throw new AssertionError("No dependency access in preview");}
    }
    static boolean legacy(StructurePackage asset,Snapshot source) {
        var box=StructureEngine.footprint(asset,new Position(0,80,0),0);boolean blocked=false;
        for(var cell:asset.cells) {
            var p=StructureEngine.rotate(cell.position(),asset.bounds.x(),asset.bounds.z(),0);
            if(source.cell(box.minX()+p.x(),box.minY()+p.y(),box.minZ()+p.z()).occupied()!=0)blocked=true;
        }
        return blocked;
    }
    static double percentile(double[] values,int percentile){var copy=values.clone();Arrays.sort(copy);if(percentile==50)return (copy[copy.length/2-1]+copy[copy.length/2])/2;return copy[(int)Math.ceil(copy.length*percentile/100.0)-1];}
    public static void main(String[] args)throws Exception {
        var asset=PackedGeometryProbe.fixture(true);if(asset.cells.size()!=50000||asset.leafCount!=100000)throw new AssertionError("Fixture count");
        var bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
        if(!bean.isThreadAllocatedMemorySupported())throw new IllegalStateException("Allocation counters unavailable");bean.setThreadAllocatedMemoryEnabled(true);
        System.out.println("{\"scope\":\"Production CPU preview microbenchmark; admitted 50000-cell/100000-leaf fractional fixture; no city/GPU/network qualification\",\"runs\":20,\"warmups\":3,\"statistics\":\"Median averages the middle pair; p95 uses nearest rank\",\"cases\":[");
        boolean first=true;
        for(String scenario:new String[]{"clear_hinted","clear_unknown","first_conflict"})for(boolean optimized:new boolean[]{false,true}) {
            double[] ms=new double[20],allocated=new double[20];int reads=0,hints=0;
            for(int run=-3;run<20;run++) {
                var source=new Snapshot(scenario.equals("clear_hinted"),scenario.equals("first_conflict"));
                long bytes=bean.getCurrentThreadAllocatedBytes(),start=System.nanoTime();
                consumed=optimized?StructurePreview.blocked(asset,new Position(0,80,0),0,source,()->false,p->{}):legacy(asset,source);
                double elapsed=(System.nanoTime()-start)/1e6;long used=bean.getCurrentThreadAllocatedBytes()-bytes;
                if(consumed!=source.blocked)throw new AssertionError("Conflict result");
                if(optimized&&source.reads!=(source.blocked?1:source.hinted?0:50000))throw new AssertionError("Unexpected geometry reads");
                if(run>=0){ms[run]=elapsed;allocated[run]=used;}reads=source.reads;hints=source.hints;
            }
            if(!first)System.out.println(",");first=false;
            System.out.printf(Locale.ROOT,"{\"scenario\":\"%s\",\"path\":\"%s\",\"medianMs\":%.6f,\"p95Ms\":%.6f,\"worstMs\":%.6f,\"medianAllocatedBytes\":%.0f,\"worstAllocatedBytes\":%.0f,\"destinationGeometryReads\":%d,\"emptyHints\":%d}",scenario,optimized?"production":"previous_ui_loop",percentile(ms,50),percentile(ms,95),percentile(ms,100),percentile(allocated,50),percentile(allocated,100),reads,hints);
        }
        System.out.println("]}");
    }
}
