package dev.jayms.combat;

import org.junit.jupiter.api.Test;
import java.util.List;
import dev.jayms.combat.KitProfile.Stack;
import static org.junit.jupiter.api.Assertions.*;
import static dev.jayms.combat.CombatSimulationTest.*;
import static dev.jayms.combat.KitProfile.*;
import static dev.jayms.combat.CombatIntent.*;
import static dev.jayms.combat.CombatEvent.*;

class CombatBoundaryTest {
    @Test void fullyGuardedTunableHitDoesNotWearArmorOrKnockBack() {
        var base=CombatRules.practice(); var d=base.damage();
        var rules=new CombatRules("full-guard",false,new CombatRules.Damage(d.health(),d.melee(),d.reach(),
                d.hurtTicks(),d.criticalMultiplier(),0,d.bowMax(),d.bowDrawTicks(),d.attributionTicks()),
                base.physics(),base.supplies(),base.limits());
        var s=duel(rules,new TestCombatWorld(),kit(Item.SWORD),KitProfile.practice());
        send(s,"b",new Guard(true)); s.step(1);
        var before=s.snapshot("b"); send(s,"a",new Swing(EAST)); s.step(1);
        assertEquals(20,health(s,"b")); assertEquals(before.armor(),s.snapshot("b").armor());
        assertEquals(before.pose(),s.snapshot("b").pose());
        assertFalse(event(s.drainEvents(),Type.KNOCKBACK,null));
    }
    @Test void projectileCapDoesNotSpendItemAndLifetimeExpiresWithoutImpact() {
        var base=CombatRules.practice();
        var rules=new CombatRules("bounded",false,base.damage(),base.physics(),base.supplies(),
                new CombatRules.Limits(2,1,8,200,3));
        var s=duel(rules,new TestCombatWorld(),kit(Item.HEAL_SPLASH),kit(Item.SWORD));
        send(s,"a",new StartUse(new Vec3(0,1,0))); send(s,"a",new StartUse(new Vec3(0,1,0))); s.step(1);
        assertEquals(1,s.projectileCount()); assertEquals(3,s.snapshot("a").inventory().get(0).count());
        s.step(3); assertEquals(0,s.projectileCount());
        var events=s.drainEvents(); assertTrue(event(events,Type.PROJECTILE_EXPIRE,"HEAL_SPLASH"));
        assertFalse(event(events,Type.PROJECTILE_IMPACT,null));
        assertThrows(IllegalArgumentException.class,()->s.addParticipant("c",pose(4),kit(Item.SWORD)));
    }
    @Test void lastItemMakesExplicitEmptySlotWithoutReordering() {
        var one=new KitProfile("one",List.of(new Stack(Item.HEAL_SPLASH,1),new Stack(Item.SWORD,1)),List.of());
        var s=duel(CombatRules.practice(),new TestCombatWorld(),one,kit(Item.SWORD));
        send(s,"a",new StartUse(DOWN)); s.step(1);
        assertEquals(Stack.EMPTY,s.snapshot("a").inventory().get(0));
        assertEquals(Item.SWORD,s.snapshot("a").inventory().get(1).item());
        send(s,"a",new StartUse(DOWN)); s.step(1); assertEquals(1,s.projectileCount());
        send(s,"a",new Select(35)); s.step(1); assertEquals(0,s.snapshot("a").selectedSlot());
    }
    @Test void poisonBelowOneNeverHealsAndExpiresBeforeFinalPulse() {
        var s=duel(CombatRules.debuff(),new TestCombatWorld(),kit(Item.POISON_SPLASH),kit(Item.SWORD));
        s.worldDamage("b",19.5,DamageKind.ENVIRONMENT); s.addExhaustion("b",100);
        send(s,"a",new StartUse(EAST)); s.step(4);
        long expiry=s.snapshot("b").effects().stream().filter(e -> e.effect()==Effect.POISON).findFirst().orElseThrow().expiresAt();
        while(s.tick()<expiry-1) s.step((int)Math.min(200,expiry-1-s.tick()));
        assertEquals(.5,health(s,"b"));
        assertTrue(s.snapshot("b").effects().stream().anyMatch(e -> e.effect()==Effect.POISON));
        s.step(1); assertTrue(s.snapshot("b").effects().isEmpty()); assertEquals(.5,health(s,"b"));
        assertTrue(event(s.drainEvents(),Type.EFFECT_EXPIRE,"POISON"));
    }
    @Test void attributionTimesOutAndDeadParticipantsCannotHealOrAct() {
        var s=duel(CombatRules.practice(),new TestCombatWorld(),kit(Item.SWORD,Item.HEAL_SPLASH),kit(Item.SWORD));
        send(s,"a",new Swing(EAST)); s.step(1); s.step(200); s.step(1);
        s.worldDamage("b",100,DamageKind.ENVIRONMENT);
        var death=s.drainEvents().stream().filter(e -> e.type()==Type.DEATH).findFirst().orElseThrow();
        assertNull(death.actor());
        s.setPose("a",pose(2)); send(s,"a",new Select(1)); send(s,"a",new StartUse(DOWN)); s.step(4);
        assertEquals(0,health(s,"b"));
        assertEquals(CombatSimulation.Submission.DEAD,s.submit("b",new CombatIntent(1,new Swing(EAST))));
        assertThrows(IllegalStateException.class,()->s.recover("a",pose(0),kit(Item.SWORD)));
    }
    @Test void immunityDoesNotWearArmorOrCancelUseAndLowHungerGatesRegen() {
        var s=duel(CombatRules.practice(),new TestCombatWorld(),kit(Item.SWORD),KitProfile.practice());
        send(s,"a",new Swing(EAST)); s.step(1); int durability=s.snapshot("b").armor().get(0).durability();
        s.setPose("b",pose(2)); send(s,"b",new Select(3)); send(s,"b",new StartUse(EAST)); s.step(1);
        send(s,"a",new Swing(EAST)); s.step(1);
        assertTrue(s.snapshot("b").using()); assertEquals(durability,s.snapshot("b").armor().get(0).durability());
        s.addExhaustion("b",100); s.step(77); assertEquals(17.6,health(s,"b"),1e-9);
        assertTrue(s.snapshot("b").hunger()<18);
    }
    @Test void effectsRefreshWithoutStackingAndRecoveryClearsThem() {
        var s=duel(CombatRules.practice(),new TestCombatWorld(),kit(Item.SPEED_DRINK),kit(Item.SWORD));
        send(s,"a",new StartUse(EAST)); s.step(33);
        long expiry=s.snapshot("a").effects().get(0).expiresAt();
        send(s,"a",new StartUse(EAST)); s.step(33);
        assertEquals(1,s.snapshot("a").effects().size()); assertEquals(1.2,s.snapshot("a").movementMultiplier());
        assertTrue(s.snapshot("a").effects().get(0).expiresAt()>expiry);
        s.worldDamage("a",100,DamageKind.ENVIRONMENT); assertTrue(s.snapshot("a").effects().isEmpty());
        s.recover("a",pose(0),kit(Item.SPEED_DRINK)); assertEquals(1,s.snapshot("a").movementMultiplier());
    }
    @Test void nearestVictimAndEqualDistanceSolidTieTakePriority() {
        var w=new TestCombatWorld();
        var s=duel(CombatRules.practice(),w,kit(Item.SWORD),kit(Item.SWORD));
        s.addParticipant("c",pose(2.5),kit(Item.SWORD));
        send(s,"a",new Swing(EAST)); s.step(1); assertEquals(14,health(s,"b")); assertEquals(20,health(s,"c"));
        s.step(10); s.setPose("b",pose(2)); w.wallX=1.7;
        send(s,"a",new Swing(EAST)); s.step(1); assertEquals(14,health(s,"b"));
    }
    @Test void eventBufferIsBoundedWithMonotonicIdsAndLossCount() {
        var s=duel(CombatRules.practice(),new TestCombatWorld(),kit(Item.SWORD),kit(Item.SWORD));
        for(int tick=0;tick<1030;tick++) {
            for(int swing=0;swing<8;swing++) send(s,"a",new Swing(new Vec3(-1,0,0)));
            s.step(1);
        }
        assertEquals(48,s.droppedEvents()); var events=s.drainEvents(); assertEquals(8192,events.size());
        assertEquals(49,events.get(0).id()); assertEquals(8240,events.get(events.size()-1).id());
        assertTrue(s.drainEvents().isEmpty());
    }
}
