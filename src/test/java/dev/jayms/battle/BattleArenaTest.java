package dev.jayms.battle;

import dev.jayms.GameIdentity;
import dev.jayms.battle.BattleArena.*;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BattleArenaTest {
    private void hit(BattleArena a, Fighter f, Part p, float damage, float impulse) {
        a.impact(f, p, new Vector3f(f.part(p).body.position).add(.17f, .19f, .12f), new Vector3f(0, 1, impulse), damage);
    }
    @Test void localDamageAndOffCentreAngularImpulse() {
        var a = new BattleArena();
        var arm = a.opponent.part(Part.LEFT_ARM);
        hit(a, a.opponent, Part.LEFT_ARM, 24, 7);
        assertEquals(40, arm.health);
        assertEquals(140, a.opponent.part(Part.TORSO).health);
        assertTrue(arm.body.velocity.z > 0);
        assertTrue(arm.body.angularVelocity.length() > .1f);
        assertEquals(0, a.opponent.part(Part.RIGHT_ARM).body.velocity.length());
        assertEquals(10, a.physics.joints.size());
    }
    @Test void symmetricDetachmentAndActionLoss() {
        for (boolean human : new boolean[]{true, false}) {
            var a = new BattleArena(); var f = human ? a.player : a.opponent;
            hit(a, f, Part.LEFT_ARM, 64, 7);
            assertFalse(f.part(Part.LEFT_ARM).attached);
            assertEquals(9, a.physics.joints.size());
            f.state = State.READY;
            assertFalse(f.canAttack(false)); assertTrue(f.canAttack(true));
            hit(a, f, Part.RIGHT_ARM, 64, 7); f.state = State.READY;
            assertNull(a.swing(f, false)); assertNull(a.swing(f, true));
            hit(a, f, Part.LEFT_LEG, 80, 7); assertEquals(.45f, f.movementFactor());
            hit(a, f, Part.RIGHT_LEG, 80, 7); assertEquals(.15f, f.movementFactor());
            assertEquals(17, a.physics.bodies.size());
        }
    }
    @Test void intactKnockdownRetainsFiveJointsAndRecoversWithoutRepair() {
        var a = new BattleArena();
        a.opponent.cooldown = 100; // Isolate recovery from the independently tested opponent attacks.
        hit(a, a.player, Part.TORSO, 10, 28);
        assertEquals(State.DOWN, a.player.state); assertEquals(0, a.player.detached());
        assertEquals(10, a.physics.joints.size()); assertNull(a.swing(a.player, true));
        for (var p : a.player.parts.values()) assertFalse(p.body.kinematic);
        for (int i = 0; i < 270; i++) a.step(new Vector3f(), (float)Math.PI);
        assertEquals(State.READY, a.player.state); assertEquals(130, a.player.part(Part.TORSO).health);
        for (var p : a.player.parts.values()) assertTrue(p.body.position.isFinite());
    }
    @Test void aSwingHitsOneNearestPartOnceAndMissStillConsumesCooldown() {
        var a = new BattleArena();
        // Position the attacker close to a side-on limb ray. Use production strike resolution.
        a.player.position().set(0, 1.75f, -1);
        var limb = a.opponent.part(Part.RIGHT_ARM);
        var origin = new Vector3f(limb.body.position).add(0, 0, 2);
        var swing = a.swing(a.player, false);
        assertTrue(a.strike(swing, origin, new Vector3f(0, 0, -1)));
        assertEquals(40, limb.health);
        assertFalse(a.strike(swing, origin, new Vector3f(0, 0, -1)));
        assertEquals(40, limb.health); assertEquals(140, a.opponent.part(Part.TORSO).health);
        assertNull(a.swing(a.player, true));
        a.player.cooldown = 0;
        var miss = a.swing(a.player, false);
        assertFalse(a.strike(miss, origin, new Vector3f(0, 1, 0)));
        assertFalse(a.strike(miss, origin, new Vector3f(0, 0, -1)));
    }
    @Test void detachedPartFallsOntoFloorAndCannotDamageAgain() {
        for (boolean human : new boolean[]{true, false}) {
            var a = new BattleArena(); var f = human ? a.player : a.opponent;
            hit(a, f, Part.LEFT_ARM, 64, 7);
            var arm = f.part(Part.LEFT_ARM); arm.body.position.set(5, 5, 5);
            arm.body.velocity.set(0, -20, 0); arm.body.angularVelocity.zero(); arm.body.rotation.identity();
            for (int i = 0; i < 480; i++) a.physics.step(BattleArena.STEP);
            assertTrue(arm.body.bounds().y0() >= -.002f);
            assertTrue(arm.body.position.y < 1);
            assertTrue(Math.abs(arm.body.velocity.y) < .01f);
            assertFalse(arm.body.kinematic); assertEquals(9, a.physics.joints.size());
            hit(a, f, Part.LEFT_ARM, 50, 7); assertEquals(0, arm.health);
        }
    }
    @Test void coreDefeatReleasesAllPartsAndResetNeverAccumulatesBodies() {
        var a = new BattleArena();
        for (int cycle = 0; cycle < 40; cycle++) {
            var old = a.physics;
            hit(a, a.player, cycle % 2 == 0 ? Part.HEAD : Part.TORSO, 200, 28);
            hit(a, a.opponent, Part.TORSO, 200, 28);
            assertEquals(State.OUT, a.player.state); assertEquals(State.OUT, a.opponent.state);
            assertEquals(6, a.player.detached()); assertEquals(6, a.opponent.detached());
            assertEquals(0, a.physics.joints.size()); assertEquals(17, a.physics.bodies.size());
            a.reset(); assertNotSame(old, a.physics);
            assertEquals(17, a.physics.bodies.size()); assertEquals(10, a.physics.joints.size());
            assertEquals(0, a.player.detached()); assertEquals(0, a.opponent.detached());
            assertEquals(State.READY, a.player.state); assertEquals(0, a.opponent.cooldown);
            assertEquals(0, a.opponent.swings); assertEquals(0, a.time);
            for (var f : new Fighter[]{a.player, a.opponent}) for (var p : f.parts.values()) {
                assertEquals(p.maximum, p.health); assertTrue(p.body.kinematic);
                assertEquals(0, p.body.velocity.length());
            }
        }
    }
    @Test void opponentApproachesFacesAndDealsDamageWithCooldown() {
        var a = new BattleArena();
        float start = a.opponent.position().z;
        for (int i = 0; i < 480; i++) a.step(new Vector3f(), (float)Math.PI);
        assertTrue(a.opponent.position().z > start + 1);
        assertTrue(a.opponent.hits > 0); assertTrue(a.opponent.swings <= 4);
        assertTrue(a.player.parts.values().stream().anyMatch(p -> p.health < p.maximum));
    }
    @Test void identityAndOfflineArguments() {
        assertEquals(GameIdentity.BATTLE, GameIdentity.parse("battle"));
        assertEquals(0, GameIdentity.BATTLE.port);
        BattleApp.validateArguments(new String[]{"--game", "battle", "--offline"});
        assertThrows(IllegalArgumentException.class, () -> BattleApp.validateArguments(new String[]{"--server", "localhost"}));
        assertThrows(IllegalArgumentException.class, () -> BattleApp.validateArguments(new String[]{"--world", "offline-world.dat"}));
        assertThrows(IllegalArgumentException.class, () -> BattleApp.validateArguments(new String[]{"--game"}));
    }
    @Test void missingLegSlowsActualMovementAndWallBoundsSurviveHeldInput() {
        var a = new BattleArena(); a.opponent.state = State.OUT;
        float start = a.player.position().x;
        for (int i = 0; i < 120; i++) a.step(new Vector3f(1, 0, 0), 0);
        assertEquals(3.5f, a.player.position().x - start, .01f);
        hit(a, a.player, Part.LEFT_LEG, 80, 7);
        for (int i = 0; i < 40; i++) a.step(new Vector3f(), 0);
        assertEquals(State.READY, a.player.state);
        start = a.player.position().x;
        for (int i = 0; i < 120; i++) a.step(new Vector3f(1, 0, 0), 0);
        assertEquals(3.5f * .45f, a.player.position().x - start, .01f);
        for (int i = 0; i < 600; i++) a.step(new Vector3f(100, 0, 0), 0);
        assertEquals(10.7f, a.player.position().x, .001f);
    }
}
