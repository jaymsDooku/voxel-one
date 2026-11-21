import dev.jayms.World;
import dev.jayms.ChunkPos;
import dev.jayms.net.*;
import dev.jayms.net.model.SparseVoxelOctree;
import dev.jayms.net.structure.StructurePackage;
import java.awt.*;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import javax.swing.*;

/** Admitted cell preparation/install and neighbor notification component only. No complete-placement qualification. */
public final class NeighborBatchBenchmark {
    private static final int SIDE=64,COUNT=SIDE*SIDE,RUNS=20;
    private static final class Neighbor extends dev.jayms.Chunk {
        int notifications;
        @Override public void markDirty(){notifications++;super.markDirty();}
    }
    private static final com.sun.management.ThreadMXBean ALLOC=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
    private record Measurement(double ms,long bytes){}
    private record Prepared(World.CellReplacement[] cells,Measurement measurement){}
    private static long allocated(){return ALLOC.getThreadAllocatedBytes(Thread.currentThread().getId());}
    private static Measurement measured(long begin,long bytes){return new Measurement((System.nanoTime()-begin)/1e6,allocated()-bytes);}
    private static int x(int i){return i%SIDE-32;}
    private static int z(int i){return i/SIDE-32;}
    private static Prepared prepare(SparseVoxelOctree tree)throws Exception {
        var task=new FutureTask<Prepared>(()->{
            long bytes=allocated(),begin=System.nanoTime();var cells=new World.CellReplacement[COUNT];
            for(int i=0;i<COUNT;i++)cells[i]=World.prepareCellReplacement(x(i),180,z(i),tree);
            return new Prepared(cells,measured(begin,bytes));
        });
        var worker=new Thread(task,"synthetic-cell-benchmark-worker");worker.start();
        try{return task.get(120,TimeUnit.SECONDS);}
        finally{task.cancel(true);worker.join(2000);if(worker.isAlive())throw new IllegalStateException("Worker did not release");}
    }
    private static SparseVoxelOctree fixture(boolean fractional)throws Exception {
        var tree=new SparseVoxelOctree(16);
        if(fractional)for(int i=0;i<32;i++) {
            int x=(i&7)*2,y=((i>>>3)&3)*4;
            tree.fill(x,y,3,x+1,y+1,4,i%2==0?0xfe123456:WorldVoxels.encode(Blocks.GLASS));
        } else tree.fill(0,0,0,16,16,16,WorldVoxels.encode(Blocks.STONE));
        var cells=new ArrayList<StructurePackage.Cell>(COUNT);
        for(int i=0;i<COUNT;i++)cells.add(new StructurePackage.Cell(new StructurePackage.Position(i%SIDE,0,i/SIDE),tree,-1));
        var asset=new StructurePackage("Prepared benchmark",new StructurePackage.Bounds(SIDE,1,SIDE,0,0,0),List.of(),cells);
        if(asset.leafCount!=(fractional?COUNT*32:COUNT))throw new AssertionError("Fixture admission/count");
        return tree.packedCellCopy();
    }
    private static WorldVoxels.History expected(SparseVoxelOctree tree) {
        var result=new WorldVoxels.History();var unrelated=new Protocol.Edit(500,180,500,Blocks.BRICKS);result.put(unrelated.key(),unrelated);
        for(int i=0;i<COUNT;i++)WorldVoxels.rememberCell(result,new Protocol.CellState(x(i),180,z(i),tree));
        return result;
    }
    private static void verify(World world,SparseVoxelOctree tree,WorldVoxels.History expected) {
        if(!expected.equals(world.editsSnapshot()))throw new AssertionError("Exact replay history");
        // World exposes chunk-grouped history; global HashMap chunk iteration has no order contract.
        var chunks=new HashMap<ChunkPos,List<Protocol.Edit>>();
        for(var edit:expected.values())chunks.computeIfAbsent(ChunkPos.fromBlock(edit.x(),edit.y(),edit.z()),p->new ArrayList<>()).add(edit);
        for(var entry:chunks.entrySet())if(!entry.getValue().equals(new ArrayList<>(world.editsInChunk(entry.getKey()))))throw new AssertionError("Per-chunk replay order");
        if(world.editsVersion()!=1L+COUNT*(1+tree.leafCount()))throw new AssertionError("Replay revisions");
        for(int i=0;i<COUNT;i++)if(!world.cell(x(i),180,z(i)).sameValues(tree))throw new AssertionError("Geometry/colour/material");
        if(world.sample(500,180,500)!=Blocks.BRICKS)throw new AssertionError("Unrelated edit lost");
    }
    private static String stats(double[] values) {
        var sorted=values.clone();Arrays.sort(sorted);
        return String.format(Locale.ROOT,"{\"median\":%.6f,\"p95\":%.6f,\"worst\":%.6f}",(sorted[9]+sorted[10])/2,sorted[18],sorted[19]);
    }
    public static void main(String[] args)throws Exception {
        if(args.length<1||args.length>2||args.length==2&&!args[1].equals("--evidence"))throw new IllegalArgumentException("Fresh result path [--evidence]");
        var path=Path.of(args[0]);if(Files.exists(path))throw new IllegalArgumentException("Fresh result path required");
        if(!ALLOC.isThreadAllocatedMemorySupported())throw new IllegalStateException("Allocation counters unavailable");
        if(!ALLOC.isThreadAllocatedMemoryEnabled())ALLOC.setThreadAllocatedMemoryEnabled(true);
        var invalid=new SparseVoxelOctree(16);invalid.fill(0,0,0,1,1,1,WorldVoxels.encode(Blocks.FLOWER_POT));
        try{World.prepareCellReplacement(0,180,0,invalid);throw new AssertionError("Invalid fractional model admitted");}catch(IllegalArgumentException expected){}
        var results=new ArrayList<String>();var text=new StringBuilder("Voxel One: neighbor batch component benchmark\n\n20 runs after 3 warmups; three install paths rotate order.\n4096 cells, 64 x 1 x 64; fresh world with 4 loaded neighbors each path/run.\n\n");
        for(boolean fractional:new boolean[]{false,true}) {
            var tree=fixture(fractional);var expected=expected(tree);
            double[][] ms=new double[4][RUNS],bytes=new double[4][RUNS];
            for(int run=-3;run<RUNS;run++) {
                Prepared prepared=prepare(tree);var replacements=List.of(prepared.cells);
                if(run>=0){ms[1][run]=prepared.measurement.ms;bytes[1][run]=prepared.measurement.bytes;}
                // Rotate three installation paths through first/second/third position.
                for(int turn=0;turn<3;turn++) {
                    int installPath=(run+3+turn)%3,mode=installPath==0?0:installPath==1?2:3;
                    try(var world=new World(42)) {
                        world.apply(new Protocol.Edit(500,180,500,Blocks.BRICKS));
                        var neighbors=new ArrayList<Neighbor>();
                        for(int z=-2;z<2;z++){var neighbor=new Neighbor();world.addChunk(new ChunkPos(-3,11,z),neighbor);neighbors.add(neighbor);}
                        for(var neighbor:neighbors)neighbor.notifications=0;
                        long before=allocated(),begin=System.nanoTime();
                        if(installPath==0)for(int i=0;i<COUNT;i++)world.replaceCell(x(i),180,z(i),tree);
                        else if(installPath==1)for(var cell:replacements)world.replaceCell(cell);
                        else world.replaceCells(replacements);
                        var installed=measured(begin,before);verify(world,tree,expected);
                        int notifications=neighbors.stream().mapToInt(n->n.notifications).sum();
                        if(notifications!=(installPath==2?4:1024))throw new AssertionError("Neighbor notification count: "+notifications);
                        if(run>=0){ms[mode][run]=installed.ms;bytes[mode][run]=installed.bytes;}
                    }
                }
            }
            String name=fractional?"fractional32":"uniform";var modes=new String[]{"direct_owner","worker_preparation","prepared_owner_install","batched_owner_install"};
            text.append(name).append(": ").append(COUNT*tree.leafCount()).append(" leaves, admitted.\n");
            for(int mode=0;mode<4;mode++) {
                String row="{\"fixture\":\""+name+"\",\"cells\":"+COUNT+",\"leaves\":"+(COUNT*tree.leafCount())+",\"phase\":\""+modes[mode]+"\",\"runs\":20,\"ms\":"+stats(ms[mode])+",\"allocated_bytes\":"+stats(bytes[mode])+",\"samples_ms\":"+Arrays.toString(ms[mode])+",\"samples_allocated_bytes\":"+Arrays.toString(bytes[mode])+"}";
                results.add(row);text.append(modes[mode]).append(" ms ").append(stats(ms[mode])).append('\n');
                text.append("  allocated bytes ").append(stats(bytes[mode])).append('\n');
            }
            text.append('\n');
        }
        Files.writeString(path,"[\n"+String.join(",\n",results)+"\n]\n",StandardOpenOption.CREATE_NEW);
        text.append("PASS: exact geometry, colours/materials, replay order/revisions,\nunrelated edits; 1024 vs 4 neighbor notifications; invalid model rejects.\n\nExcludes queue/transfer, world setup/verification, conflict checks,\nresident changed-cell geometry/meshes/GPU, saves/network and full placement.\nThread allocation is not retained/peak memory.\nFull city/maximum, physical GPU and 32-client qualification pending.");
        System.out.println("Playtest: admitted prepared-cell CLI benchmark passed; 20 runs per fixture/path; strict geometry/history/revision checks.");
        if(args.length==2)SwingUtilities.invokeAndWait(()->{
            var frame=new JFrame("Voxel One neighbor batch benchmark evidence");var area=new JTextArea(text.toString());area.setEditable(false);area.setFont(new Font(Font.MONOSPACED,Font.PLAIN,14));area.setMargin(new Insets(16,16,16,16));frame.add(new JScrollPane(area));frame.setSize(1150,720);frame.setLocation(30,30);frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);frame.setVisible(true);
            var timer=new javax.swing.Timer(20000,e->frame.dispose());timer.setRepeats(false);timer.start();
        });
    }
}
