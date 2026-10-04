package dev.jayms;

import static org.junit.jupiter.api.Assertions.*;

import dev.jayms.net.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.file.*;
import java.util.*;

class TerrainGenerationTest {
    @TempDir Path temp;

    @Test
    void settlementAndLandmarks() {
        Terrain terrain = new Terrain(Terrain.DEFAULT_SEED);
        for (int x = -16; x <= 32; x++)
            for (int z = 0; z <= 48; z++) {
                assertEquals(26, terrain.column(x, z).height());
                assertTrue(terrain.fields(x, z).fertility() >= .5);
                assertEquals(Blocks.GRASS, terrain.block(x, 26, z));
                for (int y = 27; y < 36; y++) assertEquals(0, terrain.block(x, y, z));
            }
        assertEquals(Blocks.STONE, terrain.block(132, terrain.column(132, -72).height(), -72));
        assertTrue(terrain.column(210, 160).height() + 15 < terrain.column(210, 0).height());
        boolean forest = false, ore = false;
        for (int x = -320; x <= 320; x += 4)
            for (int z = -320; z <= 320; z += 4) {
                forest |= terrain.column(x, z).biome() == Terrain.Biome.FOREST;
                for (int y = 0; y < terrain.column(x, z).height() - 5; y += 4)
                    ore |= terrain.block(x, y, z) == Blocks.MINERAL;
            }
        assertTrue(forest);
        assertTrue(ore);
    }

    @Test
    void drainageGraphAndWetChannels() {
        for (long seed : new long[] {Terrain.DEFAULT_SEED, 42, -91, Long.MAX_VALUE}) {
            Geography geography = new Geography(seed);
            Terrain terrain = new Terrain(seed);
            for (var r : geography.drainage()) {
                assertTrue(r.upstream().level() > r.downstream().level());
                for (int i = 0; i <= 100; i++) {
                    double t = i / 100.0;
                    int x =
                            (int)
                                    Math.round(
                                            r.upstream().x()
                                                    + (r.downstream().x() - r.upstream().x()) * t);
                    int z =
                            (int)
                                    Math.round(
                                            r.upstream().z()
                                                    + (r.downstream().z() - r.upstream().z()) * t);

                    var f = terrain.fields(x, z);
                    assertTrue(f.waterLevel() > f.height(), x + "," + z);
                    assertEquals(Blocks.WATER, terrain.block(x, f.waterLevel(), z));
                }
                if (r.downstream().z() != 448)
                    assertTrue(
                            geography.drainage().stream()
                                    .anyMatch(next -> next.upstream().equals(r.downstream())));
            }
        }
    }

    @Test
    void orderAndNegativeChunkBorders() {
        Terrain a = new Terrain(42), b = new Terrain(42);
        List<int[]> points = new ArrayList<>();
        for (int x = -513; x < 513; x += 16)
            for (int z = -513; z < 513; z += 16) points.add(new int[] {x, z});
        Map<String, Geography.Fields> expected = new HashMap<>();
        for (var p : points) expected.put(Arrays.toString(p), a.fields(p[0], p[1]));
        Collections.shuffle(points, new Random(3));
        for (var p : points) assertEquals(expected.get(Arrays.toString(p)), b.fields(p[0], p[1]));
        for (int cx = -5; cx <= 5; cx++) {
            ChunkPos pos = new ChunkPos(cx, 1, -1);
            Chunk c = ChunkGenerator.generate(pos, a);
            for (int x = 0; x < 16; x++)
                for (int z = 0; z < 16; z++)
                    for (int y = 0; y < 16; y++)
                        assertEquals(b.block(cx * 16 + x, 16 + y, -16 + z), c.getBlock(x, y, z));
        }
        // No abrupt seam at the pilot boundary.
        for (int z = -600; z <= 600; z += 8)
            for (int x : new int[] {-580, -400, 400, 580})
                assertTrue(Math.abs(a.column(x, z).height() - a.column(x + 1, z).height()) <= 3);
    }

    @Test
    void treesRemainConsistentAcrossChunkBoundariesAndCacheEviction() {
        Terrain terrain = new Terrain(Terrain.DEFAULT_SEED);
        boolean crossed = false;
        for (int x = -180; x <= 180 && !crossed; x++)
            for (int z = -180; z <= 180 && !crossed; z++) {
                int h = terrain.column(x, z).height();
                if (terrain.block(x, h + 1, z) != Blocks.WOOD) continue;
                for (int dx : new int[] {-2, 2}) {
                    if (Math.floorDiv(x, 16) == Math.floorDiv(x + dx, 16)) continue;
                    for (int y = h + 3; y <= h + 8; y++) {
                        if (terrain.block(x + dx, y, z) != Blocks.LEAVES) continue;
                        ChunkPos pos = ChunkPos.fromBlock(x + dx, y, z);
                        Chunk canopy = ChunkGenerator.generate(pos, terrain);
                        assertEquals(
                                Blocks.LEAVES,
                                canopy.getBlock(
                                        Math.floorMod(x + dx, 16),
                                        Math.floorMod(y, 16),
                                        Math.floorMod(z, 16)));
                        crossed = true;
                    }
                }
            }
        assertTrue(crossed, "A tree canopy should span a chunk border in the valley");
        var original = terrain.fields(-17, 31);
        for (int i = 0; i < 33000; i++) terrain.fields(i * 3, 700);
        assertEquals(original, terrain.fields(-17, 31));
    }

    @Test
    void climateControlsCropsAndCoastReachesTheRiverMouth() {
        Terrain terrain = new Terrain(Terrain.DEFAULT_SEED);
        var crops =
                new dev.jayms.net.city.Agriculture(
                        new dev.jayms.net.city.Ecs(),
                        dev.jayms.net.city.Agriculture.State.empty(),
                        terrain);
        double min = 2, max = 0;
        for (int x = -400; x <= 400; x += 20)
            for (int z = -400; z <= 400; z += 20) {
                var f = terrain.fields(x, z);
                assertTrue(f.temperature() >= 0 && f.temperature() <= 1);
                assertTrue(f.moisture() >= 0 && f.moisture() <= 1);
                assertTrue(f.fertility() >= 0 && f.fertility() <= 1);
                assertTrue(f.geology() >= 0 && f.geology() <= 1);
                min = Math.min(min, crops.growingConditions(x, z));
                max = Math.max(max, crops.growingConditions(x, z));
            }
        assertTrue(max > min + .2);
        assertEquals(
                1,
                new dev.jayms.net.city.Agriculture(
                                new dev.jayms.net.city.Ecs(),
                                dev.jayms.net.city.Agriculture.State.empty(),
                                new Terrain(42, 1))
                        .growingConditions(0, 0));
        int wet = 0;
        for (int x = -30; x < 150; x++)
            for (int z = 420; z <= 450; z++) if (terrain.block(x, 14, z) == Blocks.WATER) wet++;
        assertTrue(wet > 500, "River mouth should open into a broad coastal bay");
    }

    @Test
    void playerCanStandAtSettlementAndWadeOutOfRiver() {
        Terrain terrain = new Terrain(Terrain.DEFAULT_SEED);
        int riverX = -1;
        for (int x = 40; x < 100; x++) {
            var f = terrain.fields(x, 24);
            if (f.waterLevel() > f.height() + 2) {
                riverX = x;
                break;
            }
        }
        assertTrue(riverX > 0);
        World world = new World(Terrain.DEFAULT_SEED);
        for (int cx = -1; cx <= 7; cx++)
            for (int cz = 0; cz <= 2; cz++)
                for (int cy = 0; cy <= 3; cy++) {
                    ChunkPos pos = new ChunkPos(cx, cy, cz);
                    world.addChunk(pos, ChunkGenerator.generate(pos, terrain));
                }
        var player =
                new dev.jayms.player.Player(
                        new org.joml.Vector3f(8.5f, 27.01f, 24.5f), 0, 0, new Camera());
        for (int i = 0; i < 120; i++) player.step(world, 1f / 60, 0, 0, false, false);
        assertTrue(player.grounded());
        assertEquals(27, player.position().y, .02);
        var f = terrain.fields(riverX, 24);
        player =
                new dev.jayms.player.Player(
                        new org.joml.Vector3f(riverX + .5f, f.waterLevel() + 1.01f, 24.5f),
                        180,
                        0,
                        new Camera());
        for (int i = 0; i < 120; i++) player.step(world, 1f / 60, 0, 0, false, false);
        assertTrue(player.grounded());
        assertEquals(
                f.height() + 1,
                player.position().y,
                .02,
                "Water should not behave like solid terrain");
        for (int i = 0; i < 360; i++) player.step(world, 1f / 60, 1, 0, i % 45 == 0, false);
        assertTrue(
                player.position().x < riverX - 8,
                "Shaped banks should allow exit on foot with jumps: "
                        + player.position()
                        + ", riverX="
                        + riverX);
    }

    @Test
    void concurrentSamplingMatchesIndependentGenerators() throws Exception {
        Terrain shared = new Terrain(42);
        var workers = java.util.concurrent.Executors.newFixedThreadPool(4);
        try {
            var tasks = new ArrayList<java.util.concurrent.Callable<Boolean>>();
            for (int i = 0; i < 4; i++) {
                final int offset = i;
                tasks.add(
                        () -> {
                            Terrain independent = new Terrain(42);
                            for (int x = -80; x <= 80; x += 4)
                                for (int z = -80; z <= 80; z += 4) {
                                    assertEquals(independent.column(x, z), shared.column(x, z));
                                    assertEquals(
                                            independent.block(x, 26 + offset, z),
                                            shared.block(x, 26 + offset, z));
                                }
                            return true;
                        });
            }
            for (var result : workers.invokeAll(tasks)) assertTrue(result.get());
        } finally {
            workers.shutdownNow();
        }
    }

    @Test
    void newNaturalMaterialsHaveSafeFineSelectionAndPersistWholeBlocks() throws Exception {
        var voxels = new WorldVoxels(new Terrain(42));
        World world = new World(42);
        for (int type : new int[] {Blocks.WATER, Blocks.MINERAL}) {
            var whole = new Protocol.Edit(0, 30, 0, type);
            voxels.apply(whole);
            world.apply(whole);
            var fine = new Protocol.Edit(0, 30, 0, 0, 1, 0, 0, 0);
            assertEquals(Blocks.PARTIAL, voxels.region(fine));
            assertEquals(Blocks.PARTIAL, world.region(fine));
            assertEquals(type, voxels.region(new Protocol.Edit(0, 30, 0, 0)));
            var bytes = new ByteArrayOutputStream();
            whole.write(new DataOutputStream(bytes));
            assertEquals(
                    whole,
                    Protocol.Edit.read(
                            new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
            assertNotEquals(0, WorldVoxels.color(type));
        }
    }

    @Test
    void legacyGeneratorMatchesPreChangeFingerprint() {
        Terrain terrain = new Terrain(Terrain.DEFAULT_SEED, Terrain.LEGACY_VERSION);
        long hash = 0xcbf29ce484222325L;
        for (int x = -96; x <= 96; x += 8)
            for (int z = -96; z <= 96; z += 8) {
                var c = terrain.column(x, z);
                hash = (hash ^ c.height()) * 0x100000001b3L;
                hash = (hash ^ c.biome().ordinal()) * 0x100000001b3L;
                for (int y = -24; y <= 80; y++)
                    hash = (hash ^ terrain.block(x, y, z)) * 0x100000001b3L;
            }
        // Captured from the unmodified generator at the branch base, covering caves and trees too.
        assertEquals(-3935568949913582231L, hash);
    }

    @Test
    void saveVersionAndLegacyMigration() throws Exception {
        Path current = temp.resolve("new.dat");
        LocalGame newWorld = new LocalGame(current, 42);
        newWorld.save();
        assertEquals(Terrain.CURRENT_VERSION, new LocalGame(current, 9).generatorVersion);
        Path old = temp.resolve("legacy.dat");
        try (var out = new DataOutputStream(Files.newOutputStream(old))) {
            out.writeInt(3);
            out.writeLong(42);
            new Inventory().write(out);
            out.writeByte(20);
            out.writeInt(0);
            out.writeInt(0);
        }
        LocalGame migrated = new LocalGame(old, 9);
        assertEquals(Terrain.LEGACY_VERSION, migrated.generatorVersion);
        Terrain before = new Terrain(42, Terrain.LEGACY_VERSION);
        migrated.save();
        LocalGame restored = new LocalGame(old, 9);
        Terrain after = new Terrain(restored.seed, restored.generatorVersion);
        for (int x = -40; x < 40; x++) assertEquals(before.column(x, 12), after.column(x, 12));
        assertThrows(IllegalArgumentException.class, () -> new Terrain(42, 99));
    }
}
