package dev.jayms.combat.arena;

import dev.jayms.World;
import dev.jayms.combat.CombatEngine;
import dev.jayms.combat.CombatEngine.Intent;
import dev.jayms.combat.CombatEngine.Vec3;
import dev.jayms.combat.CombatGeometry;
import dev.jayms.combat.CombatRules;
import dev.jayms.net.Blocks;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

class VaultBattlefieldTest {
    private static final PracticeTerrain TERRAIN = PracticeTerrain.VAULT_BATTLEFIELD;

    @Test void allSixtySpawnsHaveClearGroundRoutesToBothObjectives() {
        var positions = new HashSet<Vec3>();
        for (int side = 0; side < 2; side++) {
            for (int member = 0; member < 30; member++) {
                var spawn = TERRAIN.spawn(side, member);
                var feet = new Vec3(spawn.x(), spawn.y(), spawn.z());
                assertTrue(positions.add(feet));
                assertTrue(side == 0 ? feet.z() > 25 : feet.z() < -55);
                assertStandable(feet);
                int lane = VaultBattlefield.laneForSpawn(member);
                for (boolean core : new boolean[]{false, true}) {
                    List<Vec3> route = side == 0 ? VaultBattlefield.defendRoute(lane, true, core)
                            : VaultBattlefield.attackRoute(lane, core);
                    Vec3 destination = walkRoute(feet, route);
                    assertTrue(core ? VaultBattlefield.inCoreZone(destination)
                            : VaultBattlefield.inBreachZone(destination));
                    if (side == 0) walkRoute(feet, VaultBattlefield.defendRoute(lane, false, core));
                }
            }
        }
        assertEquals(60, positions.size());
        var gallery = TERRAIN.spectator();
        assertStandable(new Vec3(gallery.x(), gallery.y(), gallery.z()));
    }

    @Test void trenchStairsAndRaisedDefensesHaveReachableLandings() {
        assertStandable(new Vec3(40.5, 73, 65.5));
        for (int z = 65; z >= 50; z--) assertStandable(new Vec3(37.5, 73 + (67 - z) / 2, z + .5));
        for (int z = 15; z >= 10; z--) assertStandable(new Vec3(17.5, 73 - Math.max(0, z - 11), z + .5));
        for (int x = 44; x <= 49; x++) assertStandable(new Vec3(x + .5, 69 + Math.min(4, x - 44), 31.5));
        assertReachableWithJumps(new Vec3(17.5, 69, 15.5), -90, feet -> feet.z() < 11 && feet.y() >= 72.99);
        assertReachableWithJumps(new Vec3(44.5, 69, 31.5), 0, feet -> feet.x() > 49 && feet.y() >= 72.99);
        assertReachableWithJumps(new Vec3(37.5, 74, 65.5), -90, feet -> feet.z() < 51 && feet.y() >= 80.99);
        assertFalse(VaultBattlefield.inBreachZone(VaultBattlefield.breachMarker().add(0, 3, 0)));
        assertFalse(VaultBattlefield.inCoreZone(VaultBattlefield.coreMarker().add(0, 8, 0)));
        assertTrue(VaultBattlefield.inCoreZone(VaultBattlefield.coreMarker().add(0, -.001, 0)));
    }

    @Test void trenchBanksSealTheSubgradeAndRecoveryRoutesReachGround() {
        for (int x = -VaultBattlefield.HALF_WIDTH; x < VaultBattlefield.HALF_WIDTH; x++) {
            for (int z = -VaultBattlefield.HALF_LENGTH; z < VaultBattlefield.HALF_LENGTH; z++) {
                if (TERRAIN.block(x, VaultBattlefield.FLOOR_Y, z) == Blocks.AIR) continue;
                for (int y = VaultBattlefield.TRENCH_Y; y < VaultBattlefield.FLOOR_Y; y++) {
                    if (TERRAIN.block(x, y, z) == Blocks.AIR) fail("Open subgrade at " + new Vec3(x, y, z));
                }
            }
        }
        assertEscapeRoute(new Vec3(7.5, 69, 13.5), 1);
        assertEscapeRoute(new Vec3(-46.5, 69, 40.5), 0);
        assertEscapeRoute(new Vec3(45.5, 69, 27.5), 2);
        assertEscapeRoute(new Vec3(20.5, 69, 69.5), 2);
    }

    @Test void loadedWorldUsesSameBlocksAsCollisionAndLeavesSpaceAboveForThrows() throws Exception {
        try (var world = new World()) {
            VaultBattlefieldWorld.generate(world);
            for (int x = -VaultBattlefield.HALF_WIDTH; x < VaultBattlefield.HALF_WIDTH; x += 9) {
                for (int z = -VaultBattlefield.HALF_LENGTH; z < VaultBattlefield.HALF_LENGTH; z += 7) {
                    for (int y = VaultBattlefield.TRENCH_Y; y < VaultBattlefield.CEILING_Y; y += 2) {
                        int block = TERRAIN.block(x, y, z);
                        assertEquals(block, world.getBlock(x, y, z));
                        assertEquals(block != Blocks.AIR, TERRAIN.solid(x, y, z));
                        assertEquals(block != Blocks.AIR, TERRAIN.throwableSolid(x, y, z));
                    }
                }
            }
        }
        Vec3 above = VaultBattlefield.coreMarker().add(0, 40, 0);
        assertFalse(TERRAIN.contains(above));
        assertTrue(TERRAIN.containsThrowable(above));
        assertFalse(TERRAIN.throwableSolid(0, (int) above.y(), 44));
    }

    @Test void gatesAndCoverChangeMeleeSightlines() {
        assertFalse(Double.isFinite(CombatGeometry.firstBlock(TERRAIN, new Vec3(.5, 74, 8.5), new Vec3(0, 0, 11))));
        assertTrue(Double.isFinite(CombatGeometry.firstBlock(TERRAIN, new Vec3(8.5, 74, 8.5), new Vec3(0, 0, 11))));
        assertFalse(Double.isFinite(CombatGeometry.firstBlock(TERRAIN, new Vec3(.5, 74, -51.5), new Vec3(0, 0, 10))));
        assertTrue(Double.isFinite(CombatGeometry.firstBlock(TERRAIN, new Vec3(14.5, 74, -55.5), new Vec3(0, 0, 12))));
    }

    private static Vec3 walkRoute(Vec3 origin, List<Vec3> route) {
        Vec3 feet = origin;
        for (Vec3 point : route) {
            Vec3 delta = point.minus(feet);
            int steps = Math.max(1, (int) Math.ceil(delta.length() * 4));
            for (int step = 1; step <= steps; step++) assertStandable(feet.add(delta.scale((double) step / steps)));
            feet = point;
        }
        return feet;
    }

    private static void assertStandable(Vec3 feet) {
        assertTrue(TERRAIN.contains(feet), () -> "Outside battlefield " + feet);
        assertTrue(TERRAIN.solid((int) Math.floor(feet.x()), (int) feet.y() - 1, (int) Math.floor(feet.z())),
                () -> "Unsupported feet " + feet);
        Vec3 offset = new Vec3(.001, .001, .001);
        assertTrue(CombatGeometry.clipMovement(TERRAIN, 1, feet, offset).minus(feet.add(offset)).length() < 1e-9,
                () -> "Blocked pose " + feet);
    }

    private static void assertReachableWithJumps(Vec3 start, float yaw, Predicate<Vec3> arrived) {
        var engine = new CombatEngine(CombatRules.NO_DEBUFF, TERRAIN);
        assertTrue(engine.join(1, start, yaw));
        for (int tick = 1; tick <= 160; tick++) {
            engine.submit(1, new Intent(tick, yaw, 0, 1, 0, true, false, false, false, 0));
            engine.tick();
            if (arrived.test(engine.snapshot(1).feet())) return;
        }
        fail("Jump approach from " + start + " stopped at " + engine.snapshot(1).feet());
    }

    private static void assertEscapeRoute(Vec3 start, int lane) {
        var route = VaultBattlefield.escapeRoute(start, lane);
        assertFalse(route.isEmpty());
        assertTrue(VaultBattlefield.attackRoute(lane, false).contains(route.get(route.size() - 1)));
        var engine = new CombatEngine(CombatRules.NO_DEBUFF, TERRAIN);
        assertTrue(engine.join(1, start, 0));
        int waypoint = 0;
        for (int tick = 1; tick <= 700; tick++) {
            Vec3 feet = engine.snapshot(1).feet();
            Vec3 delta = route.get(waypoint).minus(feet);
            if (Math.hypot(delta.x(), delta.z()) < .75 && Math.abs(delta.y()) < .8) {
                if (++waypoint == route.size()) return;
                delta = route.get(waypoint).minus(feet);
            }
            float yaw = (float) Math.toDegrees(Math.atan2(delta.z(), delta.x()));
            engine.submit(1, new Intent(tick, yaw, 0, 1, 0, true, false, false, false, 0));
            engine.tick();
        }
        fail("Recovery from " + start + " stopped at " + engine.snapshot(1).feet() + " on the way to "
                + route.get(waypoint));
    }
}
