package dev.jayms.combat.arena;

import dev.jayms.World;
import dev.jayms.net.Blocks;
import dev.jayms.net.Protocol;
import dev.jayms.net.WorldVoxels;
import org.junit.jupiter.api.Test;

import static dev.jayms.combat.arena.BasaltCourtyard.CEILING_Y;
import static dev.jayms.combat.arena.BasaltCourtyard.FLOOR_Y;
import static dev.jayms.combat.arena.BasaltCourtyard.MAX_X;
import static dev.jayms.combat.arena.BasaltCourtyard.MAX_Z;
import static dev.jayms.combat.arena.BasaltCourtyard.MIN_X;
import static dev.jayms.combat.arena.BasaltCourtyard.MIN_Z;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BasaltCourtyardTest {
    @Test
    void worldAndHeadlessLayoutAgreeAcrossMirroredCombatFootprint() throws Exception {
        try (var world = new World()) {
            BasaltCourtyardWorld.generate(world);
            var headless = new WorldVoxels(world.terrain());
            BasaltCourtyard.install(world.terrain(), edit -> {
                assertTrue(edit.valid());
                headless.apply(edit);
            });
            for (int x = MIN_X; x < MAX_X; x++) {
                for (int z = MIN_Z; z < MAX_Z; z++) {
                    for (int y = FLOOR_Y; y < CEILING_Y; y++) {
                        int rendered = world.sample(x, y, z);
                        assertEquals(rendered, headless.type(x, y, z));
                        assertEquals(rendered, world.sample(-x - 1, y, -z - 1));
                        assertEquals(rendered != Blocks.AIR, BasaltCourtyard.solid(x, y, z));
                    }
                }
            }
        }
    }

    @Test
    void spawnsFaceThroughAnOpenLaneAndBothSidesCanKiteAroundCover() {
        var south = BasaltCourtyard.spawn(0);
        var north = BasaltCourtyard.spawn(1);
        assertEquals(south.x(), north.x());
        assertEquals(south.y(), north.y());
        assertEquals(south.z(), -north.z());
        assertEquals(-90, south.yaw());
        assertEquals(90, north.yaw());
        for (int side = 0; side < 2; side++) {
            for (int member = 0; member < 5; member++) {
                var slot = BasaltCourtyard.teamSpawn(side, member);
                assertTrue(BasaltCourtyard.canStand(slot.x(), slot.y(), slot.z()));
            }
        }
        for (double z = north.z(); z <= south.z(); z++) {
            assertTrue(BasaltCourtyard.canStand(0, FLOOR_Y + 1, z));
        }
        for (double x : new double[] {-13.5, -7.5, 7.5, 13.5}) {
            for (int z = -14; z <= 14; z++) {
                assertTrue(BasaltCourtyard.canStand(x, FLOOR_Y + 1, z + .5));
            }
        }
        for (double x : new double[] {-16.5, 16.5}) {
            assertTrue(BasaltCourtyard.canStand(x, FLOOR_Y + 1, 6.5));
            assertTrue(BasaltCourtyard.canStand(x, FLOOR_Y + 2, 5.5));
            assertTrue(BasaltCourtyard.canStand(x, FLOOR_Y + 3, 4.5));
            assertTrue(BasaltCourtyard.canStand(x, FLOOR_Y + 3, .5));
        }
        assertThrows(IllegalArgumentException.class, () -> BasaltCourtyard.spawn(2));
    }

    @Test
    void catchWallsBlockThrowsAndTheGalleryIsOutsideCombat() throws Exception {
        try (var world = new World()) {
            BasaltCourtyardWorld.generate(world);
            for (int x = MIN_X; x < MAX_X; x++) {
                for (int dy = 1; dy <= 5; dy++) {
                    assertTrue(BasaltCourtyardWorld.solid(world, x, FLOOR_Y + dy, MIN_Z));
                    assertTrue(BasaltCourtyardWorld.solid(world, x, FLOOR_Y + dy, MAX_Z - 1));
                }
            }
            for (int z = MIN_Z; z < MAX_Z; z++) {
                for (int dy = 1; dy <= 5; dy++) {
                    assertTrue(BasaltCourtyardWorld.solid(world, MIN_X, FLOOR_Y + dy, z));
                    assertTrue(BasaltCourtyardWorld.solid(world, MAX_X - 1, FLOOR_Y + dy, z));
                }
            }
            assertTrue(BasaltCourtyardWorld.solid(world, 9, FLOOR_Y + 2, 7));
            assertTrue(BasaltCourtyardWorld.solid(world, 0, CEILING_Y, 0));
            assertFalse(BasaltCourtyardWorld.canStand(world, 9.5, FLOOR_Y + 1, 7.5));
            assertTrue(BasaltCourtyardWorld.canStand(world, 9.5, FLOOR_Y + 3, 7.5));
            assertFalse(BasaltCourtyardWorld.canStand(world, 9.5, FLOOR_Y + 3.5, 7.5));
            assertFalse(BasaltCourtyardWorld.canStand(world, -23, FLOOR_Y + 1, 0));
            var gallery = BasaltCourtyard.spectator();
            assertFalse(BasaltCourtyard.contains(gallery.x(), gallery.y(), gallery.z()));
            assertEquals(Blocks.PLANKS, world.sample((int) gallery.x(), (int) gallery.y() - 1, (int) gallery.z()));
            assertEquals(Blocks.AIR, world.sample((int) gallery.x(), (int) gallery.y(), (int) gallery.z()));
        }
    }

    @Test
    void resetClearsIntrusionsAndRepairEditsWithoutTouchingOtherWorldCells() throws Exception {
        try (var world = new World(63)) {
            BasaltCourtyardWorld.generate(world);
            world.setBlock(0, FLOOR_Y, 0, Blocks.AIR);
            world.setBlock(0, FLOOR_Y + 2, 0, Blocks.BRICKS);
            world.setBlock(0, FLOOR_Y + 1, 40, Blocks.WOOD);
            var fraction = new Protocol.Edit(1, FLOOR_Y + 2, 0, Blocks.piece(Blocks.STONE, 1), 1, 0, 0, 0);
            world.apply(fraction);
            assertFalse(BasaltCourtyardWorld.canStand(world, .5, FLOOR_Y + 1, .5));
            BasaltCourtyardWorld.reset(world);
            assertTrue(BasaltCourtyardWorld.canStand(world, .5, FLOOR_Y + 1, .5));
            assertEquals(Blocks.AIR, world.sample(1, FLOOR_Y + 2, 0));
            assertEquals(Blocks.WOOD, world.sample(0, FLOOR_Y + 1, 40));
            long version = world.editsVersion();
            BasaltCourtyardWorld.reset(world);
            assertEquals(version, world.editsVersion());
        }
    }
}
