package dev.jayms.combat.arena;

import dev.jayms.*;
import dev.jayms.combat.Vec3;
import dev.jayms.net.Blocks;
import dev.jayms.net.Protocol;
import dev.jayms.physics.VoxelQueries;
import dev.jayms.player.Player;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import java.util.*;
import static dev.jayms.combat.arena.BasaltCourtyard.*;
import static org.junit.jupiter.api.Assertions.*;

class BasaltCourtyardTest {
    @Test void actualGeneratedFixtureIsDeterministicAndMirrored() throws Exception {
        try (var a = generate(); var b = generate()) {
            assertEquals(240, a.world().getLoadedChunks().size());
            assertEquals(fingerprint(a.world()), fingerprint(b.world()));
            for (int x = -32; x < 32; x++)
                for (int z = -48; z < 48; z++)
                    for (int y = -32; y < 128; y++) {
                        int type = a.world().getBlock(x, y, z);
                        assertEquals(type, a.world().getBlock(-x - 1, y, z));
                        assertEquals(type, a.world().getBlock(x, y, -z - 1));
                    }
        }
    }

    @Test void spawnsTeamsAndSpectatorsHaveRealClearanceAndFacing() throws Exception {
        try (var f = generate()) {
            for (Side side : Side.values()) {
                Start spawn = spawn(side);
                assertEquals(28, Math.abs(spawn.feet().z()));
                assertEquals(0, spawn.feet().x());
                assertTrue(f.safeLanding(spawn.feet(), .3, 1.8));
                assertEquals(-Math.signum(spawn.feet().z()),
                        Math.sin(Math.toRadians(spawn.yawDegrees())), 1e-6);
                for (var start : teamStarts(side)) {
                    assertTrue(f.safeLanding(start.feet(), .3, 1.8));
                    assertEquals(28, Math.abs(start.feet().z()));
                }
                Start s = spectator(side);
                assertFalse(f.collides(body(s.feet())));
                assertEquals(Blocks.STONE, f.world().getBlock(0, 10, (int)s.feet().z()));
                assertFalse(f.safeLanding(s.feet(), .3, 1.8));
                assertEquals(Blocks.AIR, f.world().getBlock(0, 10, side == Side.NORTH ? -41 : 40));
            }
            for (int i = 0; i < 3; i++) {
                Vec3 n = teamStarts(Side.NORTH).get(i).feet(), s = teamStarts(Side.SOUTH).get(i).feet();
                assertEquals(n.x(), -s.x(), 1e-9);
                assertEquals(n.z(), -s.z(), 1e-9);
            }
        }
    }

    @Test void continuousEnclosureFlatLaneAndGenerousHeadroom() throws Exception {
        try (var f = generate()) {
            World w = f.world();
            for (int x = -32; x < 32; x++)
                for (int z = -40; z < 40; z++) {
                    assertNotEquals(Blocks.AIR, w.getBlock(x, 0, z));
                    if (x == -32 || x == 31 || z == -40 || z == 39)
                        for (int y = 1; y <= WALL_HEIGHT; y++) assertNotEquals(0, w.getBlock(x, y, z));
                    else for (int y = 3; y < 24; y++) assertEquals(0, w.getBlock(x, y, z));
                }
            for (int x = -8; x < 8; x++)
                for (int z = -36; z < 36; z++)
                    assertTrue(f.safeLanding(new Vec3(x + .5, 1, z + .5), .3, 1.8));
        }
    }

    @Test void coverInterceptsProjectilesButLeavesFlatExchangeClear() throws Exception {
        try (var f = generate()) {
            for (var c : COVER) {
                var feet = new Vec3(c.minX() + .5, 1, c.minZ() + .5);
                assertTrue(f.collides(body(feet)));
                assertFalse(f.safeLanding(feet, .3, 1.8));
                assertTrue(f.safeLanding(new Vec3(feet.x(), 2, feet.z()), .3, 1.8));
            }
            var hit = BlockRaycaster.cast(f.world(), new Vector3f(12, 1.5f, 5), new Vector3f(0, 0, 1), 20);
            assertNotNull(hit);
            assertEquals(10, hit.z());
            assertEquals(5, hit.distance(), 1e-5);
            assertNull(BlockRaycaster.cast(f.world(), new Vector3f(0, 2.6f, -28), new Vector3f(0, 0, 1), 56));
            assertTrue(f.safeLanding(new Vec3(6, 1, 0), .3, 1.8));
            assertFalse(f.safeLanding(new Vec3(12, 1, 12), .3, 1.8));
        }
    }

    @Test void floorRoutesConnectBothStartsEveryCoverAndBothFlankStairs() throws Exception {
        try (var f = generate()) {
            // Whole-body navigation: one-block jumps only, no diagonal corner cutting.
            record Node(int x, int y, int z) {}
            Set<Node> visited = new HashSet<>();
            var queue = new ArrayDeque<Node>();
            var start = new Node(0, 1, -28);
            queue.add(start); visited.add(start);
            while (!queue.isEmpty()) {
                var n = queue.remove();
                for (int[] d : new int[][] {{1,0}, {-1,0}, {0,1}, {0,-1}})
                    for (int y = Math.max(1, n.y - 1); y <= Math.min(3, n.y + 1); y++) {
                        var next = new Node(n.x + d[0], y, n.z + d[1]);
                        if (!visited.contains(next) && f.safeLanding(new Vec3(next.x + .5, y, next.z + .5), .3, 1.8)) {
                            visited.add(next); queue.add(next);
                        }
                    }
            }
            assertTrue(visited.contains(new Node(0, 1, 28)));
            for (int sign : new int[] {-1,1}) {
                assertTrue(visited.contains(new Node(sign * 23, 3, 0)));
                assertTrue(visited.contains(new Node(sign * 23, 2, 6)));
                assertTrue(visited.contains(new Node(sign * 23, 2, -6)));
            }
            for (var c : COVER) assertTrue(visited.contains(new Node(c.minX(), 2, c.minZ())));
            // Continuous ground-level sprint-kite loop outside all obstacles.
            for (int z = -36; z <= 36; z++)
                for (int x : new int[] {-28,28}) assertTrue(f.safeLanding(new Vec3(x,1,z),.3,1.8));
            for (int x = -28; x <= 28; x++)
                for (int z : new int[] {-36,36}) assertTrue(f.safeLanding(new Vec3(x,1,z),.3,1.8));
        }
    }

    @Test void actualPlayerSprintsJumpsFlankAndCannotCrossBoundary() throws Exception {
        try (var f = generate()) {
            var p = player(0,1,-28,90);
            for (int i = 0; i < 420; i++) p.step(f.world(),1f/60,1,0,false,true);
            assertTrue(p.position().z > 27 && p.position().z < 29);
            assertEquals(1, p.position().y, .002);
            var wall = player(29,1,0,0);
            for (int i = 0; i < 120; i++) wall.step(f.world(),1f/60,1,0,false,true);
            assertEquals(30.7, wall.position().x, .002);
            var flank = player(23,1,-10,90);
            float maxY = 1;
            for (int i = 0; i < 180; i++) {
                flank.step(f.world(),1f/60,1,0,true,false);
                maxY = Math.max(maxY,flank.position().y);
            }
            assertTrue(maxY >= 3, "Jump steps reach the raised flank");
            assertTrue(flank.position().z > 0, "Flank route continues across platform");
        }
    }

    @Test void resetRestoresDeletedAddedAndPartialGeometryWithoutTouchingExternalEdits() throws Exception {
        try (var f = generate()) {
            long baseline = fingerprint(f.world());
            var w = f.world();
            w.setBlock(0,0,0,Blocks.AIR);
            w.setBlock(0,2,0,Blocks.BRICKS);
            w.setBlock(12,1,12,Blocks.AIR);
            w.apply(new Protocol.Edit(23,2,0,0,1,0,0,0));
            w.setBlock(0,10,-44,Blocks.AIR);
            w.setBlock(50,10,50,Blocks.SNOW);
            assertFalse(f.safeLanding(new Vec3(.5,1,.5),.3,1.8));
            assertNotEquals(baseline, fingerprint(w));
            f.reset();
            assertEquals(baseline, fingerprint(w));
            assertEquals(Blocks.SNOW,w.sample(50,10,50));
            long edits = w.editsVersion();
            f.reset();
            assertEquals(edits,w.editsVersion(),"Reset is idempotent");
            assertTrue(f.safeLanding(new Vec3(.5,1,.5),.3,1.8));
        }
    }

    @Test void landingRejectsUnsupportedOutsideWallSpectatorsAndInvalidSizes() throws Exception {
        try (var f = generate()) {
            for (Vec3 p : List.of(new Vec3(0,2,0), new Vec3(31,1,0), new Vec3(0,1,39),
                    new Vec3(0,0,0), new Vec3(0,24,0), new Vec3(0,11,-44), new Vec3(30.8,1,0)))
                assertFalse(f.safeLanding(p,.3,1.8),p.toString());
            assertFalse(f.safeLanding(Vec3.ZERO,Double.NaN,1.8));
            assertFalse(f.safeLanding(new Vec3(0,1,0),.3,Double.POSITIVE_INFINITY));
            assertFalse(f.safeLanding(new Vec3(0,1,0),0,1.8));
            assertFalse(f.safeLanding(new Vec3(0,1,0),3,1.8));
            assertTrue(f.safeLanding(new Vec3(30.7,1,0),.3,1.8));
            f.world().setBlock(0,0,0,Blocks.WATER);
            assertFalse(f.safeLanding(new Vec3(.5,1,.5),.3,1.8));
        }
        var closed = generate(); closed.close();
        assertThrows(IllegalStateException.class,closed::reset);
    }

    static Player player(float x,float y,float z,float yaw) {
        return new Player(new Vector3f(x,y,z),yaw,0,new Camera());
    }
    static VoxelQueries.Box body(Vec3 p) {
        return new VoxelQueries.Box((float)p.x()-.3f,(float)p.y(),(float)p.z()-.3f,
                (float)p.x()+.3f,(float)p.y()+1.8f,(float)p.z()+.3f);
    }
    static long fingerprint(World w) {
        long hash = 0xcbf29ce484222325L;
        for (int x=-32;x<32;x++) for (int z=-48;z<48;z++) for (int y=-32;y<128;y++)
            hash = (hash ^ w.getBlock(x,y,z))*0x100000001b3L;
        return hash;
    }
}
