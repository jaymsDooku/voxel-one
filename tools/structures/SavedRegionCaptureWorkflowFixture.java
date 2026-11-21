package dev.jayms.net;

import dev.jayms.World;
import dev.jayms.net.structure.*;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static dev.jayms.net.structure.StructurePackage.*;

/** Actual saved capture -> self-contained file -> empty dependency world ->
 * authoritative placement/save/undo/repeat, then a v10 fixture for native view. */
public final class SavedRegionCaptureWorkflowFixture {
    public static Path create(Path root)throws Exception {
        var sourceDirectory=Files.createDirectories(root.resolve("source"));var source=SavedWorldInspectorFixture.create(sourceDirectory);var original=Files.readAllBytes(source);
        var bounds=new Bounds(24,8,5,8,0,0);var origin=new Position(-8,160,0);StructurePackage captured;SavedRegionCapture.Stats stats;long start=System.nanoTime();
        try(var capture=new SavedRegionCapture(source,root.resolve("capture-scratch"),"Captured wall",bounds,origin)){captured=capture.capture(()->false,p->{});stats=capture.stats();assertFalse(capture.cleanupPending());}
        long captureNanos=System.nanoTime()-start;assertArrayEquals(SavedRegionCaptureTest.expected(source,"Captured wall",bounds,origin).encode(),captured.encode());assertEquals(2,stats.visitedChunks());assertTrue(stats.peakCachedRegions()<=2);assertArrayEquals(original,Files.readAllBytes(source));
        Path exported=root.resolve("captured.voxstructure");start=System.nanoTime();captured.save(exported);long exportNanos=System.nanoTime()-start;var imported=StructurePackage.load(exported);assertArrayEquals(captured.encode(),imported.encode());assertEquals(1,imported.models.size());
        var destination=root.resolve("placed.dat");var local=new LocalGame(destination,42);assertFalse(local.models.hasStructureModels());var engine=new StructureEngine();long placementNanos;Path regional=root.resolve("placed-regional.dat");
        try(var world=new World(42,local.models)) {
            start=System.nanoTime();try(var plan=engine.prepare(imported,new Position(0,160,0),0,world.structureSnapshot(),()->false,n->{})){plan.commit("synthetic-capture",local.structureAuthority(world));}placementNanos=System.nanoTime()-start;
            int type=world.sample(6,163,3);assertTrue(Blocks.structureModel(type));assertEquals(imported.models.get(0).fingerprint(),local.models.get(type).definition().fingerprint());assertEquals(Blocks.WATER,world.sample(8,160,4));
            var restored=new LocalGame(destination,0);assertTrue(restored.models.hasStructureModels());assertArrayEquals(original,Files.readAllBytes(source));
            engine.undo("synthetic-capture",local.structureAuthority(world));assertEquals(Blocks.AIR,world.sample(6,163,3));
            try(var plan=engine.prepare(imported,new Position(0,160,0),0,world.structureSnapshot(),()->false,n->{})){plan.commit("synthetic-capture",local.structureAuthority(world));}
            try(var save=StagedOfflineSave.stageRegions(regional,local.seed,local.generatorVersion,local.models,WorldVoxels.History.copyOf(local.edits),()->false,n->{})){save.publish(local.inventory,local.health,List.of(),0);}
        }
        assertArrayEquals(original,Files.readAllBytes(source));try(var files=Files.list(root.resolve("capture-scratch"))){assertEquals(0,files.count());}
        Files.writeString(root.resolve("capture-result.json"),"{\"status\":\"passed\",\"cells\":"+captured.cells.size()+",\"models\":"+captured.models.size()+",\"visited_chunks\":"+stats.visitedChunks()+",\"cell_reads\":"+stats.cellReads()+",\"peak_cached_regions\":"+stats.peakCachedRegions()+",\"peak_cached_bytes\":"+stats.peakCachedBytes()+",\"capture_ns\":"+captureNanos+",\"export_ns\":"+exportNanos+",\"placement_save_ns\":"+placementNanos+",\"full_replay_package_bytes_match\":true,\"source_bytes_unchanged\":true,\"dependency_empty_destination_restored\":true,\"durable_restart\":true,\"undo_and_repeat\":true,\"scratch_cleanup\":true,\"qualification\":false}\n");return regional;
    }
}
