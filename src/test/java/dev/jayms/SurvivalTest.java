package dev.jayms;

import static org.junit.jupiter.api.Assertions.*;

import dev.jayms.net.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.file.*;
import java.util.*;

class SurvivalTest {
    @TempDir Path temp;

    @Test
    void exactly36SlotsStackCapacitySwapAndRoundTrip() throws Exception {
        Inventory i = new Inventory();
        assertEquals(Inventory.SIZE * 64, 2304);
        assertEquals(5, i.add(Blocks.STONE, 2309));
        for (int n = 0; n < 36; n++) {
            assertEquals(64, i.count(n));
            assertEquals(Blocks.STONE, i.type(n));
        }
        assertTrue(i.take(8, Blocks.STONE));
        assertFalse(i.take(9, Blocks.STONE));
        assertFalse(i.take(0, Blocks.GRASS));
        assertEquals(0, i.add(Blocks.STONE, 1));
        assertEquals(1, i.add(Blocks.GRASS, 1));
        var bytes = new ByteArrayOutputStream();
        i.write(new DataOutputStream(bytes));
        var copy =
                Inventory.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        assertEquals(64, copy.count(8));
        Inventory empty = new Inventory();
        empty.add(Blocks.SAND, 70);
        empty.swap(0, 35);
        assertEquals(64, empty.count(35));
        empty.swap(1, 35);
        assertEquals(6, empty.count(1));
        empty.swap(35, 1);
        assertEquals(6, empty.count(35));
        assertEquals(64, empty.count(1));
    }

    @Test
    void seededBiomesAndChunkBoundariesAreDeterministic() {
        Terrain a = new Terrain(748291), b = new Terrain(748291), other = new Terrain(42);
        Set<Terrain.Biome> biomes = new HashSet<>();
        boolean changed = false, trees = false, caves = false;
        for (int x = -800; x <= 800; x += 40)
            for (int z = -800; z <= 800; z += 40) {
                var c = a.column(x, z);
                biomes.add(c.biome());
                assertEquals(c, b.column(x, z));
                changed |= !c.equals(other.column(x, z));
                int y = c.height();
                assertEquals(a.block(x, y, z), b.block(x, y, z));
            }
        assertEquals(4, biomes.size());
        assertTrue(changed);
        for (int x = -80; x < 80; x++)
            for (int z = -80; z < 80; z++) {
                var c = a.column(x, z);
                for (int y = c.height() + 1; y < c.height() + 9; y++)
                    trees |= a.block(x, y, z) == Blocks.WOOD;
                for (int y = -20; y < c.height() - 5; y += 3) caves |= a.block(x, y, z) == 0;
            }
        assertTrue(trees);
        assertTrue(caves);
        World world = new World(748291);
        world.setBlock(-1, 30, -1, Blocks.SAND);
        ChunkPos p = new ChunkPos(-1, 1, -1);
        world.addChunk(p, ChunkGenerator.generate(p, a));
        assertEquals(Blocks.SAND, world.getBlock(-1, 30, -1));
        ChunkPos adjacent = new ChunkPos(0, 1, -1);
        world.addChunk(adjacent, ChunkGenerator.generate(adjacent, a));
        assertEquals(world.getBlock(0, 25, -1), world.getLoadedChunks().get(p).neighbor(16, 9, 15));
    }

    @Test
    void offlineBreakPickupPlacementAndSave() throws Exception {
        Path save = temp.resolve("offline.dat");
        LocalGame g = new LocalGame(save, 42);
        var e = new Protocol.Edit(0, 10, 0, 0);
        assertTrue(g.edit(e, Blocks.STONE, 0));
        assertEquals(0, g.inventory.count(0));
        assertEquals(1, g.drops.size());
        g.tick(new Protocol.Pose(0, .5f, 10, .5f, 0, 0), true, .01f, b -> Blocks.STONE);
        assertTrue(g.drops.isEmpty());
        assertEquals(1, g.inventory.count(0));
        var place = new Protocol.Edit(1, 10, 0, Blocks.STONE);
        assertTrue(g.edit(place, 0, 0));
        assertFalse(g.edit(new Protocol.Edit(2, 10, 0, Blocks.STONE), 0, 0));
        assertEquals(0, g.inventory.count(0));
        g.save();
        LocalGame loaded = new LocalGame(save, 999);
        assertEquals(42, loaded.seed);
        assertEquals(place, loaded.edits.get(place.key()));
        assertEquals(0, loaded.inventory.count(0));
    }

    @Test
    void fallingHurtsFlightIsSafeAndDeathRespawns() throws Exception {
        LocalGame g = new LocalGame(temp.resolve("health.dat"), 1);
        g.tick(new Protocol.Pose(0, 0, 20, 0, 0, 0), false, .01f, b -> 3);
        assertFalse(g.tick(new Protocol.Pose(0, 0, 10, 0, 0, 0), true, .01f, b -> 3));
        assertEquals(13, g.health);
        g.tick(new Protocol.Pose(0, 0, 50, 0, 0, 0, 0, 0, true), false, .01f, b -> 3);
        g.tick(new Protocol.Pose(0, 0, 10, 0, 0, 0, 0, 0, true), false, .01f, b -> 3);
        assertEquals(13, g.health);
        g.tick(new Protocol.Pose(0, 0, 40, 0, 0, 0), false, .01f, b -> 3);
        assertTrue(g.tick(new Protocol.Pose(0, 0, 10, 0, 0, 0), true, .01f, b -> 3));
        assertEquals(20, g.health);
    }
}
