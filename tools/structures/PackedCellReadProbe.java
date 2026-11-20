package dev.jayms.net.structure;

import dev.jayms.net.*;
import dev.jayms.net.model.SparseVoxelOctree;
import static dev.jayms.net.structure.StructurePackage.*;
import java.io.*;
import java.lang.management.ManagementFactory;
import java.security.MessageDigest;
import java.util.*;

/** Reader/rotation allocation diagnostic only; no world or qualification. */
public final class PackedCellReadProbe {
    static volatile long consumed;
    static byte[] fixture(boolean dense)throws Exception {
        var tree=new SparseVoxelOctree(16);
        if(dense)for(int i=0;i<4096;i++)tree.fill(i%16,i/16%16,i/256,i%16+1,i/16%16+1,i/256+1,WorldVoxels.encode(new Protocol.Edit(0,0,0,Blocks.LED).withColor(i*3733)));
        else {tree.fill(0,0,0,1,1,1,WorldVoxels.encode(Blocks.BRICKS));tree.fill(15,15,15,16,16,16,WorldVoxels.encode(new Protocol.Edit(0,0,0,Blocks.LED).withColor(0x73519b)));}
        var out=new ByteArrayOutputStream();tree.write(new DataOutputStream(out));return out.toByteArray();
    }
    public static void main(String[] args)throws Exception {
        var bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
        if(!bean.isThreadAllocatedMemorySupported())throw new IllegalStateException("Allocation counter unavailable");bean.setThreadAllocatedMemoryEnabled(true);
        var results=new ArrayList<String>();
        for(boolean dense:new boolean[]{false,true}) {
            byte[] wire=fixture(dense);String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(wire));int count=dense?32:512;
            var reference=SparseVoxelOctree.read(new DataInputStream(new ByteArrayInputStream(wire)),16);
            var asset=new StructurePackage("Read diagnostic",new Bounds(1,1,1,0,0,0),List.of(),List.of(new Cell(new Position(0,0,0),reference,-1)));
            var arenaBuilder=new StructureGeometry.Builder();int offset=arenaBuilder.add(reference);var arena=arenaBuilder.snapshot();
            // Explicit graph-reader control remains valid when production routing changes.
            var graphCells=new AbstractList<Cell>() {
                public int size(){return 1;}
                public Cell get(int index){Objects.checkIndex(index,1);return new Cell(new Position(0,0,0),arena.readGraph(offset),-1);}
            };
            for(int operation=-1;operation<4;operation++)for(String mode:List.of("graph","packed","routed")) {
                var cells=mode.equals("graph")?graphCells:mode.equals("packed")||operation<0?asset.cells:asset.placementCells(operation);
                var check=cells.get(0).geometry();if(!reference.sameValues(check)||reference.nodes()!=check.nodes()||reference.occupied()!=check.occupied()||reference.leafCount()!=check.leafCount())throw new AssertionError("Fixture query mismatch");
                double[] times=new double[20];long[] allocations=new long[20];
                for(int run=-3;run<20;run++) {
                    long before=bean.getCurrentThreadAllocatedBytes(),start=System.nanoTime(),sum=0;
                    for(int i=0;i<count;i++) {
                        var tree=cells.get(0).geometry();
                        if(operation>=0)tree=tree.rotatedY(operation).packedCellCopy();
                        int point=i*1973&4095;
                        sum+=tree.get(point%16,point/16%16,point/256)+tree.uniform(0,0,0,16)+tree.nodes()+tree.occupied()+tree.leafCount();
                    }
                    consumed=sum;
                    if(run>=0){times[run]=(System.nanoTime()-start)/1e6;allocations[run]=bean.getCurrentThreadAllocatedBytes()-before;}
                }
                Arrays.sort(times);Arrays.sort(allocations);
                results.add(String.format(Locale.ROOT,"{\"fixture\":\"%s\",\"wire_sha256\":\"%s\",\"wire_bytes\":%d,\"mode\":\"%s\",\"operation\":\"%s\",\"cells_per_batch\":%d,\"median_ms\":%.6f,\"p95_ms\":%.6f,\"worst_ms\":%.6f,\"median_thread_allocated_bytes\":%d}",dense?"dense":"sparse",hash,wire.length,mode,operation<0?"queries":"placement-r"+operation,count,(times[9]+times[10])/2,times[18],times[19],(allocations[9]+allocations[10])/2));
            }
        }
        System.out.println("{\"runs\":20,\"warmups\":3,\"qualification\":false,\"scope\":\"Actual package cell views: graph reference, always-packed tradeoff and production rotation routing; current-thread allocation, not retained/peak memory or city/system qualification\",\"results\":["+String.join(",",results)+"]}");
    }
}
