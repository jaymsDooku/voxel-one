package dev.jayms.combat;

import org.junit.jupiter.api.Test;
import java.util.*;
import dev.jayms.combat.KitProfile.Stack;
import static org.junit.jupiter.api.Assertions.*;
import static dev.jayms.combat.KitProfile.*;
import static dev.jayms.combat.CombatEvent.*;
import static dev.jayms.combat.CombatIntent.*;

class CombatSimulationTest {
    static final Vec3 EAST = new Vec3(1, 0, 0), DOWN = new Vec3(0, -1, 0);
    static CombatSimulation.Pose pose(double x) { return new CombatSimulation.Pose(new Vec3(x, 0, 0), Vec3.ZERO, true); }
    static KitProfile kit(Item... items) {
        return new KitProfile("test", Arrays.stream(items).map(i -> new Stack(i, i == Item.SWORD || i == Item.BOW ? 1 : 4)).toList(), List.of());
    }
    static CombatSimulation duel(CombatRules rules, TestCombatWorld world, KitProfile a, KitProfile b) {
        CombatSimulation s = new CombatSimulation(rules, world);
        s.addParticipant("a", pose(0), a); s.addParticipant("b", pose(2), b); return s;
    }
    static void send(CombatSimulation s, String id, Action action) {
        assertEquals(CombatSimulation.Submission.QUEUED, s.submit(id, new CombatIntent(s.snapshot(id).lastSequence() + 1, action)));
    }
    static double health(CombatSimulation s, String id) { return s.snapshot(id).health(); }
    static boolean event(List<CombatEvent> events, Type type, String detail) {
        return events.stream().anyMatch(e -> e.type() == type && (detail == null || detail.equals(e.detail())));
    }
    @Test void fastReclickHasNoSwingCooldownButVictimWindowAndExactExpiry() {
        var s = duel(CombatRules.practice(), new TestCombatWorld(), kit(Item.SWORD), kit(Item.SWORD));
        send(s,"a",new Swing(EAST)); send(s,"a",new Swing(EAST)); s.step(1);
        assertEquals(14, health(s,"b"));
        assertEquals(2, s.drainEvents().stream().filter(e -> e.type() == Type.SWING).count());
        s.setPose("b",pose(2)); send(s,"a",new Swing(EAST)); s.step(9);
        assertEquals(14,health(s,"b"));
        s.setPose("b",pose(2)); send(s,"a",new Swing(EAST)); s.step(1);
        assertEquals(8,health(s,"b")); // tick 11, inclusive expiry
    }
    @Test void compoundStrongerHitOnlyAddsDifferenceAndFallingCritRequiresAirborne() {
        var s = duel(CombatRules.practice(),new TestCombatWorld(),kit(Item.SWORD),kit(Item.SWORD));
        send(s,"a",new Swing(EAST)); s.step(1);
        s.setPose("b",pose(2));
        s.setPose("a",new CombatSimulation.Pose(new Vec3(0,.1,0),new Vec3(0,-.1,0),false));
        send(s,"a",new Swing(EAST)); s.step(1);
        assertEquals(11,health(s,"b"));
        s.setPose("b",pose(2)); send(s,"a",new Swing(EAST)); s.step(1);
        assertEquals(11,health(s,"b"));
    }
    @Test void sprintHitMustRearmWithOffThenOnAndAirHasNoSprintBonus() {
        var s = duel(CombatRules.practice(),new TestCombatWorld(),kit(Item.SWORD),KitProfile.practice());
        send(s,"a",new Sprint(true)); send(s,"a",new Sprint(true)); send(s,"a",new Swing(EAST)); s.step(1);
        var first=s.drainEvents(); assertTrue(event(first,Type.KNOCKBACK,"sprint"));
        s.step(10); s.setPose("b",pose(2));
        send(s,"a",new Sprint(true)); send(s,"a",new Swing(EAST)); s.step(1);
        assertTrue(event(s.drainEvents(),Type.KNOCKBACK,"normal"));
        s.step(10); s.setPose("b",pose(2));
        send(s,"a",new Sprint(false)); send(s,"a",new Sprint(true)); send(s,"a",new Swing(EAST)); s.step(1);
        assertTrue(event(s.drainEvents(),Type.KNOCKBACK,"sprint"));
        s.step(10); s.setPose("b",pose(2));
        s.setPose("a",new CombatSimulation.Pose(new Vec3(0,.1,0),new Vec3(0,-.1,0),false));
        send(s,"a",new Sprint(false)); send(s,"a",new Sprint(true)); send(s,"a",new Swing(EAST)); s.step(1);
        assertTrue(event(s.drainEvents(),Type.KNOCKBACK,"normal"));
    }
    @Test void wallsReachAndAimRejectMelee() {
        var world=new TestCombatWorld(); world.wallX=1;
        var s=duel(CombatRules.practice(),world,kit(Item.SWORD),kit(Item.SWORD));
        send(s,"a",new Swing(EAST)); s.step(1); assertEquals(20,health(s,"b"));
        world.wallX=Double.POSITIVE_INFINITY; s.setPose("b",pose(3.31));
        send(s,"a",new Swing(EAST)); s.step(1); assertEquals(20,health(s,"b"));
        s.setPose("b",pose(2)); send(s,"a",new Swing(new Vec3(0,0,1))); s.step(1);
        assertEquals(20,health(s,"b"));
    }
    @Test void guardUsesOneHandAndArmorWearsPerSlotThenBreaks() {
        var armored=new KitProfile("armor",List.of(new Stack(Item.SWORD,1),new Stack(Item.HEAL_SPLASH,1)),
                List.of(new Armor(ArmorSlot.CHEST,.5,1),new Armor(ArmorSlot.HEAD,.1,2)));
        var s=duel(CombatRules.practice(),new TestCombatWorld(),kit(Item.SWORD),armored);
        send(s,"b",new Guard(true)); s.step(1);
        send(s,"a",new Swing(EAST)); s.step(1);
        assertEquals(18.8,health(s,"b"),1e-9);
        assertTrue(s.snapshot("b").armor().stream().allMatch(a -> a.durability()==0));
        assertEquals(2,s.drainEvents().stream().filter(e -> e.type()==Type.ARMOR_BREAK).count());
        send(s,"b",new Select(1)); s.step(10); assertFalse(s.snapshot("b").guarding());
        s.setPose("b",pose(2)); send(s,"a",new Swing(EAST)); s.step(1); assertEquals(12.8,health(s,"b"),1e-9);
    }
    @Test void replayAndRateBoundsSurviveDeathAndRecovery() {
        var s=duel(CombatRules.practice(),new TestCombatWorld(),kit(Item.PEARL),kit(Item.SWORD));
        for(int i=1;i<=8;i++) assertEquals(CombatSimulation.Submission.QUEUED,s.submit("a",new CombatIntent(i,new CancelUse())));
        assertEquals(CombatSimulation.Submission.RATE_LIMITED,s.submit("a",new CombatIntent(9,new StartUse(EAST))));
        s.step(1); assertEquals(CombatSimulation.Submission.REPLAY,s.submit("a",new CombatIntent(9,new StartUse(EAST))));
        assertEquals(4,s.snapshot("a").inventory().get(0).count());
        s.worldDamage("a",100,DamageKind.ENVIRONMENT);
        assertEquals(CombatSimulation.Submission.DEAD,s.submit("a",new CombatIntent(10,new StartUse(EAST))));
        s.recover("a",pose(0),kit(Item.PEARL));
        assertEquals(CombatSimulation.Submission.REPLAY,s.submit("a",new CombatIntent(10,new StartUse(EAST))));
        assertEquals(2,s.snapshot("a").life()); assertEquals(20,health(s,"a"));
    }
    @Test void finitePotionHealsSelfAndOpponentByDistanceAndConsumesOnce() {
        var s=duel(CombatRules.practice(),new TestCombatWorld(),kit(Item.HEAL_SPLASH),kit(Item.SWORD));
        s.worldDamage("a",10,DamageKind.ENVIRONMENT); s.worldDamage("b",10,DamageKind.ENVIRONMENT);
        send(s,"a",new StartUse(DOWN)); s.step(4);
        assertEquals(3,s.snapshot("a").inventory().get(0).count());
        double own=health(s,"a"), opponent=health(s,"b");
        assertEquals(16.4,own,1e-9); assertTrue(opponent>10 && opponent<own);
        assertEquals(0,s.projectileCount());
        var impacts=s.drainEvents(); assertTrue(event(impacts,Type.PROJECTILE_IMPACT,"HEAL_SPLASH"));
    }
    @Test void movingAndMissedPotsUseImpactPositionAndCurrentBodyNotLaunchPosition() {
        var s=duel(CombatRules.practice(),new TestCombatWorld(),kit(Item.HEAL_SPLASH),kit(Item.SWORD));
        s.worldDamage("b",10,DamageKind.ENVIRONMENT);
        send(s,"a",new StartUse(DOWN)); s.step(1); s.setPose("b",pose(10)); s.step(3);
        assertEquals(10,health(s,"b"));
        s.setPose("b",pose(10)); send(s,"a",new StartUse(EAST)); s.step(3);
        s.setPose("b",new CombatSimulation.Pose(new Vec3(1.8,0,0),Vec3.ZERO,true)); s.step(2);
        assertTrue(health(s,"b")>10);
    }
    @Test void splashDoesNotHealThroughWall() {
        var w=new TestCombatWorld(); w.wallX=1;
        var s=duel(CombatRules.practice(),w,kit(Item.HEAL_SPLASH),kit(Item.SWORD));
        s.worldDamage("b",10,DamageKind.ENVIRONMENT); send(s,"a",new StartUse(DOWN)); s.step(4);
        assertEquals(10,health(s,"b"));
    }
    @Test void drinksFoodCancellationHungerAndSlowRegeneration() {
        var s=duel(CombatRules.practice(),new TestCombatWorld(),kit(Item.FOOD,Item.SPEED_DRINK),kit(Item.SWORD));
        s.addExhaustion("a",44); s.step(1); assertEquals(14,s.snapshot("a").hunger()); assertEquals(0,s.snapshot("a").saturation());
        s.worldDamage("a",8,DamageKind.ENVIRONMENT);
        send(s,"a",new StartUse(EAST)); s.step(16); send(s,"a",new CancelUse()); s.step(20);
        assertEquals(4,s.snapshot("a").inventory().get(0).count()); assertEquals(14,s.snapshot("a").hunger());
        send(s,"a",new StartUse(EAST)); s.step(33);
        assertEquals(20,s.snapshot("a").hunger()); assertEquals(7.2,s.snapshot("a").saturation(),1e-9);
        assertEquals(12,health(s,"a")); s.step(10); assertEquals(13,health(s,"a")); // tick 80
        send(s,"a",new Select(1)); send(s,"a",new StartUse(EAST)); s.step(10);
        s.worldDamage("a",1,DamageKind.ENVIRONMENT); s.step(30);
        assertTrue(s.snapshot("a").effects().isEmpty()); assertEquals(4,s.snapshot("a").inventory().get(1).count());
    }
    @Test void beneficialEffectsExpireAtExactTickAndFireResistanceBlocksFireOnly() {
        var s=duel(CombatRules.practice(),new TestCombatWorld(),kit(Item.FIRE_RESIST_DRINK,Item.SPEED_DRINK),kit(Item.SWORD));
        send(s,"a",new StartUse(EAST)); s.step(33); // expiry 633
        s.worldDamage("a",5,DamageKind.FIRE); assertEquals(20,health(s,"a"));
        s.worldDamage("a",5,DamageKind.ENVIRONMENT); assertEquals(15,health(s,"a"));
        send(s,"a",new Select(1)); send(s,"a",new StartUse(EAST)); s.step(33);
        assertEquals(1.2,s.snapshot("a").movementMultiplier());
        for(int i=0;i<2;i++) s.step(200); s.step(166); // tick 632
        assertTrue(s.snapshot("a").effects().stream().anyMatch(e -> e.effect()==Effect.FIRE_RESISTANCE));
        s.step(1); assertFalse(s.snapshot("a").effects().stream().anyMatch(e -> e.effect()==Effect.FIRE_RESISTANCE));
        s.worldDamage("a",2,DamageKind.FIRE); assertTrue(event(s.drainEvents(),Type.DAMAGE,"FIRE"));
        s.step(33); assertEquals(1,s.snapshot("a").movementMultiplier());
    }
    @Test void poisonIsDebuffOnlyNonlethalAndDoesNotUseMeleeImmunity() {
        assertThrows(IllegalArgumentException.class,()->duel(CombatRules.practice(),new TestCombatWorld(),kit(Item.POISON_SPLASH),kit(Item.SWORD)));
        var s=duel(CombatRules.debuff(),new TestCombatWorld(),kit(Item.POISON_SPLASH,Item.SLOWNESS_SPLASH),kit(Item.SWORD));
        s.worldDamage("b",18,DamageKind.ENVIRONMENT);
        send(s,"a",new StartUse(EAST)); s.step(4);
        assertTrue(s.snapshot("b").effects().stream().anyMatch(e -> e.effect()==Effect.POISON));
        s.step(80); assertEquals(1,health(s,"b")); assertTrue(s.snapshot("b").alive());
        send(s,"a",new Select(1)); send(s,"a",new StartUse(EAST)); s.step(4);
        assertEquals(.85,s.snapshot("b").movementMultiplier());
    }
    @Test void pearlCostCooldownSafeLandingAndUnsafeRejection() {
        var w=new TestCombatWorld();
        var s=duel(CombatRules.practice(),w,kit(Item.PEARL),kit(Item.SWORD));
        s.setPose("b",pose(20)); send(s,"a",new StartUse(new Vec3(1,-1,0))); s.step(4);
        assertEquals(18,health(s,"a")); assertEquals(3,s.snapshot("a").inventory().get(0).count());
        assertTrue(s.snapshot("a").pose().feet().x()>0); assertEquals(0,s.snapshot("a").pose().feet().y());
        send(s,"a",new StartUse(DOWN)); s.step(1); assertEquals(3,s.snapshot("a").inventory().get(0).count());
        assertTrue(event(s.drainEvents(),Type.PEARL_REJECT,"cooldown-or-health"));
        s.step(200); s.step(96); // tick 301, cooldown expires
        w.landingAllowed=false; Vec3 before=s.snapshot("a").pose().feet(); double beforeHealth=health(s,"a");
        send(s,"a",new StartUse(DOWN)); s.step(4);
        assertEquals(before,s.snapshot("a").pose().feet()); assertEquals(beforeHealth-2,health(s,"a"));
        assertTrue(event(s.drainEvents(),Type.PEARL_REJECT,"unsafe-destination"));
    }
    @Test void pearlNearWallSearchesForSupportedBodyAndBoundsCanRejectAllCandidates() {
        var w=new TestCombatWorld(); w.wallX=1;
        var s=duel(CombatRules.practice(),w,kit(Item.PEARL),kit(Item.SWORD));
        send(s,"a",new StartUse(EAST)); s.step(3);
        assertTrue(event(s.drainEvents(),Type.TELEPORT,"pearl"));
        assertTrue(w.safeLanding(s.snapshot("a").pose().feet(),.3,1.8));
        var w2=new TestCombatWorld(); w2.bound=.2;
        var t=duel(CombatRules.practice(),w2,kit(Item.PEARL),kit(Item.SWORD));
        send(t,"a",new StartUse(DOWN)); t.step(3);
        assertTrue(event(t.drainEvents(),Type.PEARL_REJECT,"unsafe-destination"));
    }
    @Test void bowDrawSweptCollisionAndArrowSlotOrderAreFinite() {
        var s=duel(CombatRules.practice(),new TestCombatWorld(),kit(Item.BOW,Item.ARROW,Item.ARROW),kit(Item.SWORD));
        s.setPose("b",pose(5)); send(s,"a",new StartUse(EAST)); s.step(20);
        send(s,"a",new ReleaseUse(EAST)); s.step(3);
        assertEquals(10,health(s,"b")); assertEquals(3,s.snapshot("a").inventory().get(1).count());
        assertEquals(4,s.snapshot("a").inventory().get(2).count());
        assertTrue(event(s.drainEvents(),Type.PROJECTILE_IMPACT,"ARROW"));
        send(s,"a",new StartUse(EAST)); send(s,"a",new ReleaseUse(EAST)); s.step(1);
        assertEquals(3,s.snapshot("a").inventory().get(1).count());
    }
    @Test void bowHitsWallBeforeVictimAndNoArrowMeansNoShot() {
        var w=new TestCombatWorld(); w.wallX=1;
        var s=duel(CombatRules.practice(),w,kit(Item.BOW,Item.ARROW),kit(Item.SWORD));
        send(s,"a",new StartUse(EAST)); s.step(20); send(s,"a",new ReleaseUse(EAST)); s.step(1);
        assertEquals(20,health(s,"b")); assertEquals(0,s.projectileCount());
        var t=duel(CombatRules.practice(),new TestCombatWorld(),kit(Item.BOW),kit(Item.SWORD));
        send(t,"a",new StartUse(EAST)); t.step(20); send(t,"a",new ReleaseUse(EAST)); t.step(1);
        assertEquals(0,t.projectileCount());
    }
    @Test void deathAttributionRecoveryClearsEffectsUseAndOldProjectiles() {
        var s=duel(CombatRules.practice(),new TestCombatWorld(),kit(Item.SWORD,Item.PEARL),kit(Item.SPEED_DRINK));
        send(s,"b",new StartUse(EAST)); s.step(1); send(s,"a",new Swing(EAST)); s.step(1);
        assertFalse(s.snapshot("b").using()); s.worldDamage("b",100,DamageKind.ENVIRONMENT);
        var death=s.drainEvents().stream().filter(e -> e.type()==Type.DEATH).findFirst().orElseThrow();
        assertEquals("a",death.actor()); assertEquals("b",death.target());
        send(s,"a",new Select(1)); send(s,"a",new StartUse(EAST)); s.step(1); assertEquals(1,s.projectileCount());
        s.worldDamage("a",100,DamageKind.ENVIRONMENT); s.recover("a",pose(0),kit(Item.SWORD));
        assertEquals(0,s.projectileCount()); assertTrue(s.snapshot("a").effects().isEmpty());
    }
    @Test void exactReplayProducesIdenticalSnapshotsEventsAndStepBatching() {
        var a=duel(CombatRules.debuff(),new TestCombatWorld(),KitProfile.practice(),KitProfile.practice());
        var b=duel(CombatRules.debuff(),new TestCombatWorld(),KitProfile.practice(),KitProfile.practice());
        for(var s:List.of(a,b)) { send(s,"a",new Sprint(true)); send(s,"a",new Swing(EAST)); }
        a.step(20); for(int i=0;i<20;i++) b.step(1);
        assertEquals(a.snapshot("a"),b.snapshot("a")); assertEquals(a.snapshot("b"),b.snapshot("b"));
        assertEquals(a.drainEvents(),b.drainEvents());
    }
    @Test void profileImmutabilityValidationAndBoundedSteps() {
        var slots=new ArrayList<>(List.of(new Stack(Item.SWORD,1))); var kit=new KitProfile("immutable",slots,List.of());
        slots.clear(); assertEquals(1,kit.slots().size());
        var s=duel(CombatRules.practice(),new TestCombatWorld(),kit,kit);
        assertThrows(UnsupportedOperationException.class,()->s.snapshot("a").inventory().clear());
        assertThrows(IllegalArgumentException.class,()->s.step(201)); assertEquals(0,s.tick());
        assertThrows(IllegalArgumentException.class,()->new Swing(Vec3.ZERO));
        assertThrows(IllegalArgumentException.class,()->new Vec3(Double.NaN,0,0));
        assertThrows(IllegalArgumentException.class,()->new Stack(Item.PEARL,65));
    }
}
