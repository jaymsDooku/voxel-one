package dev.jayms.combat;

import dev.jayms.combat.CombatEngine.Arena;
import dev.jayms.combat.CombatEngine.Effect;
import dev.jayms.combat.CombatEngine.EventType;
import dev.jayms.combat.CombatEngine.Intent;
import dev.jayms.combat.CombatEngine.Item;
import dev.jayms.combat.CombatEngine.Stack;
import dev.jayms.combat.CombatEngine.Vec3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CombatEngineTest {
    private static final Vec3 ORIGIN = new Vec3(.5, 1, .5);

    @Test void inventoryQuickMoveMergesAcrossSectionsWithoutDroppingOverflow() {
        CombatEngine engine = duel(CombatRules.NO_DEBUFF, field(false, true), 2);
        var kit = new ArrayList<>(engine.kit(1));
        kit.set(1, new Stack(Item.PEARL, 62));
        kit.set(18, new Stack(Item.PEARL, 20));
        assertTrue(engine.setKit(1, kit));
        assertTrue(engine.reset(1, ORIGIN, 0));

        assertTrue(engine.quickMoveInventory(1, 18));
        var moved = engine.snapshot(1).inventory();
        assertEquals(new Stack(Item.PEARL, 64), moved.get(1));
        assertEquals(new Stack(Item.PEARL, 18), moved.get(18));
        assertEquals(kit.get(1), engine.kit(1).get(1));
        assertFalse(engine.quickMoveInventory(1, 18));
        assertFalse(engine.quickMoveInventory(1, 36));
        assertEquals(moved, engine.snapshot(1).inventory());

        kit.set(10, new Stack(Item.EMPTY, 0));
        assertTrue(engine.setKit(1, kit));
        assertTrue(engine.quickMoveKit(1, 1));
        assertEquals(new Stack(Item.PEARL, 64), engine.kit(1).get(18));
        assertEquals(new Stack(Item.PEARL, 18), engine.kit(1).get(10));
        assertEquals(new Stack(Item.EMPTY, 0), engine.kit(1).get(1));
    }

    @Test void meleeUsesAimReachOcclusionAndHurtImmunity() {
        CombatEngine engine = duel(CombatRules.NO_DEBUFF, field(false, true), 2.5);
        assertTrue(input(engine, 1, 0, 0, 0, 0, false, true, false, 0));
        assertFalse(input(engine, 1, 0, 0, 0, 0, false, true, false, 0));
        assertFalse(input(engine, 1, 1, Float.NaN, 0, 0, false, true, false, 0));
        assertFalse(input(engine, 1, 1, 0, 0, 1.1f, false, true, false, 0));
        assertFalse(input(engine, 1, 1, 0, 0, 0, false, true, false, 36));
        engine.tick();
        assertEquals(17.44, engine.snapshot(2).health(), .00001);
        assertEquals(List.of(198, 198, 198, 198), engine.snapshot(2).durability());
        assertEquals(1, engine.snapshot(1).stats().hits());
        assertTrue(input(engine, 1, 1, 0, 0, 0, false, true, false, 0));
        engine.tick();
        assertEquals(17.44, engine.snapshot(2).health(), .00001);
        assertEquals(1, engine.snapshot(1).stats().hits());
        assertTrue(engine.drainEvents().stream().anyMatch(event -> event.type() == EventType.SWING));

        CombatEngine wall = duel(CombatRules.NO_DEBUFF, field(true, true), 2.5);
        input(wall, 1, 0, 0, 0, 0, false, true, false, 0);
        wall.tick();
        assertEquals(20, wall.snapshot(2).health());

        CombatEngine distant = duel(CombatRules.NO_DEBUFF, field(false, true), 4.5);
        input(distant, 1, 0, 0, 0, 0, false, true, false, 0);
        distant.tick();
        assertEquals(20, distant.snapshot(2).health());

        CombatEngine unaimed = duel(CombatRules.NO_DEBUFF, field(false, true), 2.5);
        input(unaimed, 1, 0, 12, 0, 0, false, true, false, 0);
        unaimed.tick();
        assertEquals(20, unaimed.snapshot(2).health());
    }

    @Test void sprintResetRearmsBoostWhileHoldingSprintAfterAHitDoesNot() {
        CombatEngine held = new CombatEngine(CombatRules.NO_DEBUFF, field(false, true));
        held.join(1, ORIGIN, 0);
        held.join(2, new Vec3(2.2, 1, .5), 180);
        input(held, 1, 0, 0, 0, 1, true, true, false, 0);
        held.tick();
        held.tick();
        assertEquals(.52, held.snapshot(2).velocity().x(), .0001);
        for (int tick = 2; tick < 10; tick++) {
            input(held, 1, tick - 1, 0, 0, 1, true, false, false, 0);
            held.tick();
        }
        input(held, 1, 9, 0, 0, 1, true, true, false, 0);
        held.tick();
        held.tick();
        assertEquals(2, held.snapshot(1).stats().hits());
        assertTrue(held.snapshot(2).velocity().x() < .44);

        CombatEngine reset = new CombatEngine(CombatRules.NO_DEBUFF, field(false, true));
        reset.join(1, ORIGIN, 0);
        reset.join(2, new Vec3(2.2, 1, .5), 180);
        input(reset, 1, 0, 0, 0, 1, true, true, false, 0);
        reset.tick();
        reset.tick();
        for (int tick = 2; tick < 10; tick++) {
            input(reset, 1, tick - 1, 0, 0, 1, true, false, false, 0);
            reset.tick();
        }
        input(reset, 1, 9, 0, 0, 0, false, false, false, 0);
        input(reset, 1, 10, 0, 0, 1, true, true, false, 0);
        reset.tick();
        reset.tick();
        assertEquals(2, reset.snapshot(1).stats().hits());
        assertEquals(2, reset.snapshot(1).stats().longestCombo());
        assertTrue(reset.snapshot(2).velocity().x() > .5);
    }

    @Test void healingSplashCanHelpBothPlayersAndUsesSlotOnlyOnce() {
        CombatEngine engine = duel(CombatRules.NO_DEBUFF, field(false, true), 2);
        assertTrue(engine.damage(0, 1, 10));
        assertTrue(engine.damage(0, 2, 10));
        assertTrue(input(engine, 1, 0, 0, -89, 0, false, false, true, 5));
        for (int tick = 1; tick <= 7; tick++) {
            assertTrue(input(engine, 1, tick, 0, -89, 0, false, false, true, 5));
            engine.tick();
        }
        assertEquals(Item.EMPTY, engine.snapshot(1).inventory().get(5).item());
        assertEquals(1, engine.snapshot(1).stats().potsUsed());
        assertEquals(30, engine.snapshot(1).stats().potsRemaining());
        assertTrue(engine.snapshot(1).health() > 17.5);
        assertTrue(engine.snapshot(2).health() > 12);
        assertTrue(engine.snapshot(1).health() > engine.snapshot(2).health());
        assertEquals(1, engine.drainEvents().stream().filter(event -> event.type() == EventType.SPLASH).count());
        assertTrue(engine.swapInventory(1, 20, 5));
        assertEquals(Item.HEALING, engine.snapshot(1).inventory().get(5).item());
        assertEquals(Item.EMPTY, engine.snapshot(1).inventory().get(20).item());
    }

    @Test void missedSplashDoesNotHealAndPoisonCannotKill() {
        CombatEngine noDebuff = duel(CombatRules.NO_DEBUFF, field(false, true), 4.5);
        noDebuff.damage(0, 1, 10);
        input(noDebuff, 1, 0, 180, 15, 0, false, false, true, 5);
        tick(noDebuff, 25);
        assertEquals(10, noDebuff.snapshot(1).health());
        assertTrue(noDebuff.drainEvents().stream().anyMatch(event -> event.type() == EventType.SPLASH));
        assertFalse(noDebuff.setKit(1, CombatRules.DEBUFF.openingKit()));

        CombatEngine debuff = duel(CombatRules.DEBUFF, field(false, true), 2);
        debuff.damage(0, 2, 18.5);
        input(debuff, 1, 0, 0, -10, 0, false, false, true, 5);
        tick(debuff, 7);
        assertTrue(debuff.snapshot(2).effects().containsKey(Effect.POISON));
        tick(debuff, 20);
        assertEquals(1, debuff.snapshot(2).health(), .0001);
        assertEquals(0, debuff.snapshot(2).killerId());

        CombatEngine team = duel(CombatRules.DEBUFF, field(false, false), 2);
        input(team, 1, 0, 0, -10, 0, false, false, true, 5);
        tick(team, 7);
        assertFalse(team.snapshot(2).effects().containsKey(Effect.POISON));
        assertFalse(team.damage(1, 2, 3));
    }

    @Test void drinksRequireContinuousUseAndSpeedExpires() {
        CombatEngine engine = duel(CombatRules.NO_DEBUFF, field(false, true), 2.5);
        for (int tick = 0; tick < 12; tick++) {
            input(engine, 1, tick, 0, 0, 0, false, false, true, 3);
            engine.tick();
        }
        input(engine, 1, 12, 0, 0, 0, false, false, false, 3);
        engine.tick();
        assertEquals(Item.SPEED, engine.snapshot(1).inventory().get(3).item());
        for (int tick = 13; tick < 45; tick++) {
            input(engine, 1, tick, 0, 0, 0, false, false, true, 3);
            engine.tick();
        }
        assertEquals(CombatRules.NO_DEBUFF.speedDuration(), engine.snapshot(1).effects().get(Effect.SPEED));
        assertEquals(Item.EMPTY, engine.snapshot(1).inventory().get(3).item());
        tick(engine, CombatRules.NO_DEBUFF.speedDuration() - 1);
        assertEquals(1, engine.snapshot(1).effects().get(Effect.SPEED));
        engine.tick();
        assertFalse(engine.snapshot(1).effects().containsKey(Effect.SPEED));
        assertTrue(engine.applyEffect(1, Effect.FIRE_RESISTANCE, 2));
        assertFalse(engine.burn(1, 3));
        tick(engine, 2);
        assertTrue(engine.burn(1, 3));
        assertEquals(17, engine.snapshot(1).health());
    }

    @Test void pearlsConsumeOnThrowAndStayOnTheNearSideOfCover() {
        CombatEngine engine = new CombatEngine(CombatRules.NO_DEBUFF, field(true, true));
        engine.join(1, ORIGIN, 0);
        input(engine, 1, 0, 0, -10, 0, false, false, true, 1);
        engine.tick();
        assertEquals(15, engine.snapshot(1).inventory().get(1).count());
        assertEquals(320, engine.snapshot(1).pearlCooldownTicks());
        assertEquals(18, engine.snapshot(1).health());
        assertTrue(engine.snapshot(1).feet().x() > .5);
        assertTrue(engine.snapshot(1).feet().x() < 1);
        assertTrue(engine.drainEvents().stream().anyMatch(event -> event.type() == EventType.TELEPORT));
        input(engine, 1, 1, 0, -10, 0, false, false, false, 1);
        engine.tick();
        input(engine, 1, 2, 0, -10, 0, false, false, true, 1);
        engine.tick();
        assertEquals(15, engine.snapshot(1).inventory().get(1).count());
        assertEquals(318, engine.snapshot(1).pearlCooldownTicks());
    }

    @Test void pearlOutsideBoundsFailsAndResetPreservesSequenceAndKitLayout() {
        Arena compact = new Arena() {
            @Override public boolean solid(int x, int y, int z) { return y <= 0; }
            @Override public boolean contains(Vec3 feet) { return feet.x() >= 0 && feet.x() < 1 && feet.y() >= 1; }
        };
        CombatEngine engine = new CombatEngine(CombatRules.NO_DEBUFF, compact);
        engine.join(1, ORIGIN, 0);
        input(engine, 1, 0, 0, 0, 0, false, false, true, 1);
        engine.tick();
        assertTrue(engine.drainEvents().stream().anyMatch(event -> event.type() == EventType.REJECTED_PEARL));
        assertEquals(ORIGIN, engine.snapshot(1).feet());
        assertEquals(20, engine.snapshot(1).health());
        assertTrue(engine.swapKit(1, 0, 8));
        assertEquals(Item.SWORD, engine.snapshot(1).inventory().get(0).item());
        assertTrue(engine.reset(1, ORIGIN, 0));
        assertEquals(Item.SWORD, engine.snapshot(1).inventory().get(8).item());
        assertEquals(16, engine.snapshot(1).inventory().get(1).count());
        assertEquals(0, engine.snapshot(1).pearlCooldownTicks());
        assertFalse(input(engine, 1, 0, 0, 0, 0, false, false, true, 1));
    }

    @Test void bowDrawSpendsArrowAndProjectileCannotPassThroughCover() {
        for (boolean cover : List.of(false, true)) {
            CombatEngine engine = duel(CombatRules.NO_DEBUFF, field(cover, true), 7.5);
            List<Stack> kit = new ArrayList<>(CombatRules.NO_DEBUFF.openingKit());
            kit.set(0, new Stack(Item.BOW, 1));
            kit.set(35, new Stack(Item.ARROW, 5));
            assertTrue(engine.setKit(1, kit));
            assertTrue(engine.reset(1, ORIGIN, 0));
            for (int tick = 0; tick < 22; tick++) {
                input(engine, 1, tick, 0, 0, 0, false, false, true, 0);
                engine.tick();
            }
            input(engine, 1, 22, 0, 0, 0, false, false, false, 0);
            tick(engine, 10);
            assertEquals(4, engine.snapshot(1).inventory().get(35).count());
            if (cover) assertEquals(20, engine.snapshot(2).health());
            else assertTrue(engine.snapshot(2).health() < 19);
        }
    }

    @Test void movingPlayersCanLeadHealingAndTagPoliciesBlockMovementAndPearls() {
        CombatEngine engine = duel(CombatRules.NO_DEBUFF, field(false, true), 12);
        engine.damage(0, 1, 10);
        for (int i = 0; i < 8; i++) {
            input(engine, 1, i, 0, -53, 1, true, false, i == 4, 5);
            engine.tick();
        }
        assertTrue(engine.snapshot(1).feet().x() > 2);
        assertTrue(engine.snapshot(1).health() > 16);
        assertTrue(engine.drainEvents().stream().anyMatch(event -> event.type() == EventType.SPLASH));

        Arena protection = new Arena() {
            @Override public boolean solid(int x, int y, int z) { return y <= 0; }
            @Override public boolean contains(Vec3 feet) { return Math.abs(feet.x()) < 30; }
            @Override public boolean canEnter(int playerId, Vec3 feet) { return feet.x() < 2; }
        };
        CombatEngine tagged = new CombatEngine(CombatRules.NO_DEBUFF, protection);
        tagged.join(1, ORIGIN, 0);
        input(tagged, 1, 0, 0, 0, 1, true, false, true, 1);
        tick(tagged, 16);
        assertTrue(tagged.snapshot(1).feet().x() < 2);
        assertTrue(tagged.drainEvents().stream().anyMatch(event -> event.type() == EventType.REJECTED_PEARL));
    }

    @Test void detachingInputCancelsUseAndFiniteGrantsRequireSpace() {
        CombatEngine engine = duel(CombatRules.NO_DEBUFF, field(false, true), 7);
        assertFalse(engine.grant(1, new Stack(Item.HEALING, 1)));
        assertTrue(engine.grant(1, new Stack(Item.PEARL, 3)));
        assertEquals(19, engine.snapshot(1).inventory().get(1).count());
        assertFalse(engine.grant(1, new Stack(Item.PEARL, 64)));
        input(engine, 1, 0, 0, -89, 0, false, false, true, 5);
        engine.clearInput(1);
        engine.tick();
        assertEquals(Item.HEALING, engine.snapshot(1).inventory().get(5).item());
        input(engine, 1, 1, 0, -89, 0, false, false, true, 5);
        engine.tick();
        assertTrue(engine.grant(1, new Stack(Item.HEALING, 1)));
        assertEquals(Item.HEALING, engine.snapshot(1).inventory().get(5).item());
    }

    private static CombatEngine duel(CombatRules rules, Arena field, double farX) {
        CombatEngine engine = new CombatEngine(rules, field);
        assertTrue(engine.join(1, ORIGIN, 0));
        assertTrue(engine.join(2, new Vec3(farX, 1, .5), 180));
        return engine;
    }

    private static Arena field(boolean wall, boolean hostile) {
        return new Arena() {
            @Override public boolean solid(int x, int y, int z) {
                return y <= 0 || (wall && x == 1 && y <= 4 && z >= -2 && z <= 2);
            }
            @Override public boolean contains(Vec3 feet) {
                return Math.abs(feet.x()) < 30 && Math.abs(feet.z()) < 30 && feet.y() < 20;
            }
            @Override public boolean canHarm(int attackerId, int targetId) { return hostile; }
        };
    }

    private static void tick(CombatEngine engine, int count) {
        for (int tick = 0; tick < count; tick++) engine.tick();
    }

    private static boolean input(CombatEngine engine, int player, long sequence, float yaw, float pitch,
                                 float forward, boolean sprint, boolean attack, boolean use, int slot) {
        return engine.submit(player, new Intent(sequence, yaw, pitch, forward, 0, false, sprint, attack, use, slot));
    }
}
