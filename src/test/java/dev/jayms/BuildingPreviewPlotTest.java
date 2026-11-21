package dev.jayms;

import dev.jayms.building.BuildingCompiler;
import dev.jayms.net.Blocks;
import dev.jayms.net.LocalGame;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.io.TempDir;
import dev.jayms.net.Protocol;
import dev.jayms.net.WorldVoxels;
import dev.jayms.net.city.StressGrid;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BuildingPreviewPlotTest {
    @TempDir Path temporary;

    @Test void upperFinialRemainsPickableAndEditableWhenTheCameraStreamsAway() throws Exception {
        var building=BuildingCompiler.compile("""
                building tall 256 3264 256 grid16 monument
                part finial 0 3200 0 256 3264 256
                box SAND 128 3248 128 144 3264 144
                end
                """);
        try (var world=new World(17)) {
            world.terrain().stressGrid(new StressGrid(0));
            BuildingPreviewPlot.install(world,building,0,()->{});
            assertTrue(world.editsSnapshot().isEmpty());
            assertTrue(world.requiresSkyCeiling(0,0));
            assertEquals(Blocks.SAND,world.getBlock(8,204,8));
            var hit=BlockRaycaster.cast(world,new Vector3f(8.25f,210,8.25f),new Vector3f(0,-1,0),10);
            assertNotNull(hit);
            assertEquals(204,hit.y());
            assertEquals(1,hit.normalY());

            var carve=new Protocol.Edit(8,204,8,Blocks.AIR,1,0,0,0);
            var save=temporary.resolve("ordinary.dat");
            var ordinary=new LocalGame(save,17);
            assertFalse(ordinary.acceptsEdit(carve));
            assertFalse(ordinary.edit(carve,world.region(carve),0));
            var preview=new LocalGame(temporary.resolve("inspection.dat"),17);
            preview.beginBuildingPreview(world.streamCeilingY());
            // The click preflight and the offline inventory transaction share this rule.
            assertTrue(preview.acceptsEdit(carve));
            assertTrue(preview.edit(carve,world.region(carve),0));
            assertFalse(preview.acceptsEdit(new Protocol.Edit(8,world.streamCeilingY()+1,8,Blocks.AIR)));
            assertFalse(preview.acceptsEdit(new Protocol.Edit(8,204,8,Blocks.STONE).withColor(1)));
            assertFalse(carve.valid());
            preview.save();
            assertFalse(Files.exists(temporary.resolve("inspection.dat")));
            world.apply(carve);
            assertEquals(Blocks.AIR,world.material(8*16+2,204*16+2,8*16+2));
            assertEquals(Blocks.SAND,world.material(8*16+12,204*16+2,8*16+2));
            assertEquals(Blocks.SAND,WorldVoxels.decode(world.editedCellsInChunk(
                    ChunkPos.fromBlock(8,204,8)).iterator().next().voxels().get(12,2,2)));

            world.stream(400,400,1);
            assertTrue(world.nearbyReady(400,400,0));
            assertTrue(world.isLoaded(400,223,400));
            assertEquals(Blocks.AIR,world.getBlock(400,223,400));
            world.unloadChunk(ChunkPos.fromBlock(8,204,8));
            assertTrue(world.isLoaded(8,204,8));
            assertEquals(Blocks.PARTIAL,world.getBlock(8,204,8));
        }
    }
}
