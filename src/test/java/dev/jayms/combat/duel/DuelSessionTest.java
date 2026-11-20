package dev.jayms.combat.duel;

import dev.jayms.combat.*;
import dev.jayms.combat.arena.BasaltCourtyard;
import dev.jayms.net.Blocks;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class DuelSessionTest {
    BasaltCourtyard.Fixture arena;CourtyardCombatWorld world;DuelSession session;long[] seq;
    @BeforeEach void open(){arena=BasaltCourtyard.generate();world=new CourtyardCombatWorld(arena);session=new DuelSession(DuelSession.Profile.NO_DEBUFF,world,2,1000,5);seq=new long[2];session.connected(0,true);session.connected(1,true);}
    @AfterEach void close() throws Exception {arena.close();}
    boolean op(int side,DuelSession.Op op,int a,int b){return session.submit(side,DuelSession.Command.operation(session.epoch(),++seq[side],op,a,b));}
    void input(int side,double f,double yaw,double pitch,boolean jump,boolean sprint,boolean attack,boolean use,int slot){session.submit(side,new DuelSession.Command(session.epoch(),++seq[side],seq[side],DuelSession.Op.INPUT,f,0,yaw,pitch,jump,sprint,attack,use,false,slot,0));}
    void start(){op(0,DuelSession.Op.READY,0,0);op(1,DuelSession.Op.READY,0,0);session.step();session.step();assertEquals(DuelSession.Phase.LIVE,session.phase());}
    CombatSimulation.Snapshot body(int side){return session.state().fighters().get(side).body();}
    @Test void optInWarmupReadyGalleryAndFreshRoundReset(){
        assertEquals(11,body(0).pose().feet().y());assertFalse(op(0,DuelSession.Op.REFILL,1,0));
        input(0,1,90,0,false,false,true,true,1);session.step();assertEquals(20,body(1).health());
        start();assertEquals(new Vec3(0,1,-28),body(0).pose().feet());assertTrue(body(0).effects().isEmpty());
        input(0,0,90,-89,false,false,false,true,1);session.step();assertEquals(0,body(0).inventory().get(1).count());
        op(0,DuelSession.Op.FORFEIT,0,0);assertEquals(DuelSession.Phase.RESULT,session.phase());
        start();assertEquals(1,body(0).inventory().get(1).count());assertEquals(20,body(0).health());assertEquals(0,session.state().projectiles().size());
    }
    @Test void replayEpochAndLiveKitEditingAreRejected(){
        long old=session.epoch();start();
        assertFalse(session.submit(0,DuelSession.Command.operation(old,++seq[0],DuelSession.Op.FORFEIT,0,0)));
        assertFalse(op(0,DuelSession.Op.SWAP,0,1));
        var c=DuelSession.Command.operation(session.epoch(),++seq[0],DuelSession.Op.REFILL,1,0);
        assertFalse(session.submit(0,c));assertFalse(session.submit(0,c));assertEquals(DuelSession.Phase.LIVE,session.phase());
    }
    @Test void kitSwapAndRefillConserveFiniteSupplies(){
        var original=body(0).inventory();assertTrue(op(0,DuelSession.Op.SWAP,1,8));start();
        input(0,0,90,-89,false,false,false,true,1);session.step();assertEquals(0,body(0).inventory().get(1).count());
        assertTrue(op(0,DuelSession.Op.REFILL,1,0));assertEquals(1,body(0).inventory().get(1).count());assertEquals(0,body(0).inventory().get(9).count());
        assertEquals(original.stream().mapToInt(KitProfile.Stack::count).sum()-1,body(0).inventory().stream().mapToInt(KitProfile.Stack::count).sum());
        assertFalse(op(0,DuelSession.Op.REFILL,1,0));
    }
    @Test void movementIsNormalizedBoundedAndStaleInputStops(){
        start();input(0,1,90,0,false,false,false,false,0);
        for(int i=0;i<20;i++)session.step();double z=body(0).pose().feet().z();assertTrue(z>-28 && z<-26.8);
        for(int i=0;i<20;i++)session.step();assertEquals(z,body(0).pose().feet().z());
        for(int i=0;i<300;i++){input(0,1,0,0,false,true,false,false,0);session.step();}
        assertEquals(30.7,body(0).pose().feet().x(),.0001);assertEquals(1,body(0).pose().feet().y(),.0001);
        assertThrows(IllegalArgumentException.class,()->new DuelSession.Command(1,1,0,DuelSession.Op.INPUT,Double.NaN,0,0,0,false,false,false,false,false,0,0));
    }
    @Test void realSpacingSprintImpulseCounterAndResults(){
        start();for(int i=0;i<124;i++){input(0,1,90,0,false,false,false,false,0);input(1,1,-90,0,false,false,false,false,0);session.step();}
        double before=body(1).health();input(0,0,90,0,false,true,false,false,0);session.step();
        input(0,0,90,0,false,true,true,false,0);input(1,0,-90,0,false,false,true,false,0);session.step();
        assertTrue(body(1).health()<before);assertTrue(body(1).pose().velocity().z()>0);assertTrue(body(1).pose().feet().y()>1);
        assertEquals(1,session.state().fighters().get(0).score().hits());
        op(1,DuelSession.Op.FORFEIT,0,0);assertTrue(session.state().result().contains("A wins"));assertEquals(1,session.state().fighters().get(0).score().longestCombo());
    }
    @Test void knockoutEndsRoundAndDeadLifeCannotSpendOrAttack(){
        start();for(int i=0;i<124;i++){input(0,1,90,0,false,false,false,false,0);input(1,1,-90,0,false,false,false,false,0);session.step();}
        for(int t=0;t<500 && session.phase()==DuelSession.Phase.LIVE;t++) {
            Vec3 a=body(0).pose().feet(),b=body(1).pose().feet(),delta=b.subtract(a);
            double yaw=Math.toDegrees(Math.atan2(delta.z(),delta.x()));
            double pitch=Math.toDegrees(Math.atan2(delta.y()-.32,Math.hypot(delta.x(),delta.z())));
            input(0,delta.length()>2.1?1:0,yaw,pitch,false,false,t%11==0,false,0);session.step();
        }
        assertEquals(DuelSession.Phase.RESULT,session.phase());assertFalse(body(1).alive());assertEquals(0,body(1).health());
        assertTrue(session.state().result().contains("A wins by knockout"));
        var before=body(1).inventory();input(1,1,-90,0,false,false,true,true,1);session.step();assertEquals(before,body(1).inventory());
    }
    @Test void reconnectGraceAndTimeoutDraw(){
        start();session.connected(1,false);for(int i=0;i<4;i++)session.step();assertEquals(DuelSession.Phase.LIVE,session.phase());
        session.connected(1,true);session.step();assertEquals(DuelSession.Phase.LIVE,session.phase());
        session.connected(1,false);for(int i=0;i<5;i++)session.step();assertTrue(session.state().result().contains("Disconnect: A wins"));
        session=new DuelSession(DuelSession.Profile.NO_DEBUFF,world,1,2,2);seq=new long[2];session.connected(0,true);session.connected(1,true);start();session.step();assertEquals("Draw: time limit",session.state().result());
    }
    @Test void exactSweepsCoverCornersAndArenaBounds(){
        assertEquals(.5,world.firstSolid(new Vec3(12,1.5,5),new Vec3(12,1.5,15),0).orElseThrow(),1e-9);
        assertTrue(world.firstSolid(new Vec3(9.8,2.2,9.8),new Vec3(9.8,2.2,10),.1).isEmpty());
        var stop=world.move(new Vec3(12,1,8),new Vec3(0,0,6),.3,1.8);assertEquals(9.7,stop.feet().z(),.00001);
        var floor=world.move(new Vec3(0,3,0),new Vec3(0,-5,0),.3,1.8);assertEquals(1,floor.feet().y());assertTrue(floor.grounded());
        assertFalse(world.safeLanding(new Vec3(12,1,12),.3,1.8));assertFalse(world.safeLanding(new Vec3(0,11,-44),.3,1.8));
        assertThrows(IllegalArgumentException.class,()->world.move(new Vec3(0,1,0),new Vec3(100,0,0),.3,1.8));
    }
    CombatSimulation combat(DuelSession.Profile profile,Vec3 a,Vec3 b){var sim=new CombatSimulation(profile==DuelSession.Profile.NO_DEBUFF?CombatRules.practice():CombatRules.debuff(),world);sim.addParticipant("A",new CombatSimulation.Pose(a,Vec3.ZERO,true),DuelSession.kit(profile));sim.addParticipant("B",new CombatSimulation.Pose(b,Vec3.ZERO,true),DuelSession.kit(profile));return sim;}
    @Test void actualCoverRejectsMeleeAndOpponentSplashWorks(){
        var sim=combat(DuelSession.Profile.NO_DEBUFF,new Vec3(9.4,1,11),new Vec3(11,1,9.4));
        sim.submit("A",new CombatIntent(1,new CombatIntent.Swing(new Vec3(1.6,-1.9,-1.6))));sim.step(1);assertEquals(20,sim.snapshot("B").health());
        sim=combat(DuelSession.Profile.NO_DEBUFF,new Vec3(0,1,0),new Vec3(0,1,2));
        sim.worldDamage("A",8,CombatEvent.DamageKind.ENVIRONMENT);sim.worldDamage("B",8,CombatEvent.DamageKind.ENVIRONMENT);
        sim.submit("A",new CombatIntent(1,new CombatIntent.Select(1)));sim.submit("A",new CombatIntent(2,new CombatIntent.StartUse(new Vec3(0,-1,.3))));sim.step(10);
        assertTrue(sim.snapshot("A").health()>12);assertTrue(sim.snapshot("B").health()>12);
    }
    @Test void profileDebuffsPearlAndSeparateWorldIsolation() throws Exception {
        assertTrue(DuelSession.kit(DuelSession.Profile.NO_DEBUFF).slots().stream().noneMatch(s->s.item()==KitProfile.Item.POISON_SPLASH));
        var sim=combat(DuelSession.Profile.DEBUFF,new Vec3(0,1,0),new Vec3(0,1,2));
        sim.submit("A",new CombatIntent(1,new CombatIntent.Select(7)));sim.submit("A",new CombatIntent(2,new CombatIntent.StartUse(new Vec3(0,-1,.3))));sim.step(10);assertFalse(sim.snapshot("B").effects().isEmpty());
        sim.submit("A",new CombatIntent(3,new CombatIntent.Select(2)));sim.submit("A",new CombatIntent(4,new CombatIntent.StartUse(new Vec3(1,-.5,0))));sim.step(20);
        assertTrue(sim.snapshot("A").pose().feet().x()>2);assertTrue(sim.snapshot("A").pearlReadyAt()>sim.tick());
        var other=combat(DuelSession.Profile.NO_DEBUFF,new Vec3(0,1,0),new Vec3(0,1,2));assertEquals(20,other.snapshot("A").health());assertTrue(other.snapshot("A").effects().isEmpty());
        arena.world().setBlock(2,1,2,Blocks.BRICKS);try(var fresh=BasaltCourtyard.generate()){assertEquals(Blocks.AIR,fresh.world().getBlock(2,1,2));}
    }
}
