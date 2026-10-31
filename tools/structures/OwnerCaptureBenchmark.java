import dev.jayms.net.structure.StructureOwnerQueue;
import java.lang.management.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Local small-fixture diagnostic. No city/maximum, GPU, network or stable qualification. */
public final class OwnerCaptureBenchmark {
    record Run(double wallMs,double maxOwnerMs,int pumps,long sampledHeapBytes,RegionOfflineOwnerPrototype.Snapshot snapshot){Run metrics(){return new Run(wallMs,maxOwnerMs,pumps,sampledHeapBytes,null);}}
    static long heap(){return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();}
    static Run capture(RegionOfflineCellOwnerCheck.Fixture scene,StructureOwnerQueue owner,boolean cells)throws Exception {
        long start=System.nanoTime(),deadline=start+TimeUnit.SECONDS.toNanos(20),peak=heap(),maxOwner=0;int pumps=0;
        var task=new FutureTask<>(()->cells?RegionOfflineOwnerPrototype.capture(scene.game(),scene.world()::get,owner,()->false):RegionOfflineOwnerPrototype.capture(scene.game(),owner,()->false));var worker=new Thread(task,"synthetic-benchmark-worker");worker.start();
        try {
            while(!task.isDone()) {
                if(System.nanoTime()>deadline)throw new AssertionError("Capture timed out");
                long before=System.nanoTime();boolean pumped=owner.pump();long duration=System.nanoTime()-before;
                if(pumped){pumps++;maxOwner=Math.max(maxOwner,duration);}else Thread.sleep(1);
                peak=Math.max(peak,heap());
            }
            var snapshot=task.get();return new Run((System.nanoTime()-start)/1e6,maxOwner/1e6,pumps,Math.max(peak,heap()),snapshot);
        }finally{worker.interrupt();worker.join(5000);if(worker.isAlive())throw new AssertionError("Worker leaked");}
    }
    static String stats(List<Run> rows,java.util.function.ToDoubleFunction<Run> metric) {
        double[] values=rows.stream().mapToDouble(metric).sorted().toArray();return String.format(Locale.ROOT,"{\"median\":%.6f,\"p95\":%.6f,\"worst\":%.6f}",(values[9]+values[10])/2,values[18],values[19]);
    }
    static String result(List<Run> rows) {
        var out=new StringBuilder("{\"capture_ms\":").append(stats(rows,Run::wallMs)).append(",\"maximum_owner_pump_ms\":").append(stats(rows,Run::maxOwnerMs)).append(",\"sampled_heap_used_bytes\":").append(stats(rows,r->r.sampledHeapBytes())).append(",\"runs\":[");
        for(int i=0;i<rows.size();i++){if(i>0)out.append(',');var r=rows.get(i);out.append(String.format(Locale.ROOT,"{\"capture_ms\":%.6f,\"maximum_owner_pump_ms\":%.6f,\"pumps\":%d,\"sampled_heap_used_bytes\":%d}",r.wallMs(),r.maxOwnerMs(),r.pumps(),r.sampledHeapBytes()));}return out.append("]}").toString();
    }
    static byte[] canonical(RegionWorldLibraryPrototype world)throws Exception {
        var geometry=world.geometrySnapshot();var sorted=new ArrayList<dev.jayms.net.Protocol.CellState>();for(var region:geometry.regions().values())sorted.addAll(region);
        sorted.sort(Comparator.comparingInt(dev.jayms.net.Protocol.CellState::x).thenComparingInt(dev.jayms.net.Protocol.CellState::y).thenComparingInt(dev.jayms.net.Protocol.CellState::z));
        var normalized=RegionHistoryPrototype.fromCells(geometry.terrainSeed(),geometry.terrainVersion(),sorted,new RegionHistoryPrototype.CodecBudget(8*1024*1024,100_000,1024,131_072),()->false);
        var candidate=RegionWorldLibraryPrototype.capture(normalized,world.restore(()->false).models(),world.metadata().orElseThrow(),()->false);if(world.sidecars().isPresent())candidate=candidate.withSidecars(world.sidecars().orElseThrow(),()->false);return candidate.encode(()->false);
    }
    public static void main(String[] args)throws Exception {
        Path parent=Path.of("target/structure-owner-capture-benchmark");Files.createDirectories(parent);Path root=Files.createTempDirectory(parent,"synthetic-");var replay=new ArrayList<Run>();var cells=new ArrayList<Run>();int cellCount,recordCount;
        try(var owner=new StructureOwnerQueue()) {
            var scene=RegionOfflineCellOwnerCheck.fixture(root);byte[] source=Files.readAllBytes(root.resolve("legacy.dat"));var frame=scene.game().city.frame();var history=scene.game().city.history;long revision=history.revision();recordCount=scene.game().edits.size();
            capture(scene,owner,false);capture(scene,owner,true);
            RegionOfflineOwnerPrototype.Snapshot lastReplay=null,lastCells=null;
            for(int run=0;run<20;run++){
                Run a,b;if((run&1)==0){a=capture(scene,owner,false);b=capture(scene,owner,true);}else{b=capture(scene,owner,true);a=capture(scene,owner,false);}
                replay.add(a.metrics());cells.add(b.metrics());lastReplay=a.snapshot();lastCells=b.snapshot();
            }
            var oldStore=new RegionGenerationPrototype(root.resolve("replay"));var cellStore=new RegionGenerationPrototype(root.resolve("cells"));
            try(var staged=lastReplay.stage(oldStore,()->false)){staged.commit(()->false);}try(var staged=lastCells.stage(cellStore,()->false)){staged.commit(()->false);}
            var first=oldStore.load(()->false).orElseThrow().worldLibrary();var second=cellStore.load(()->false).orElseThrow().worldLibrary();
            RegionOfflineCellOwnerCheck.require(Arrays.equals(canonical(first),canonical(second)),"Capture paths produced different canonical payloads");
            var oldRestored=oldStore.loadComplete(scene.game().seed,scene.game().generatorVersion,frame.config(),()->false).orElseThrow().state();RegionOfflineCellOwnerCheck.compare(scene.game(),scene.world().get(),oldRestored);
            var restored=cellStore.loadComplete(scene.game().seed,scene.game().generatorVersion,frame.config(),()->false).orElseThrow().state();cellCount=RegionOfflineCellOwnerCheck.compare(scene.game(),scene.world().get(),restored);
            RegionOfflineCellOwnerCheck.require(frame.equals(scene.game().city.frame())&&history==scene.game().city.history&&revision==history.revision()&&Arrays.equals(source,Files.readAllBytes(root.resolve("legacy.dat"))),"Benchmark changed source");
        }finally{try(var paths=Files.walk(root)){for(var p:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}}
        System.out.println("{\"status\":\"passed\",\"measured_runs_per_path\":20,\"warmups_per_path\":1,\"alternating_order\":true,\"cells\":"+cellCount+",\"flat_records\":"+recordCount+",\"replay\":"+result(replay)+",\"world_cells\":"+result(cells)+",\"canonical_payloads_equal\":true,\"strict_restart\":true,\"qualification\":false}");
    }
}
