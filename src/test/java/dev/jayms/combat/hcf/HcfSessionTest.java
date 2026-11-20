package dev.jayms.combat.hcf;

import dev.jayms.combat.*;
import dev.jayms.combat.KitProfile.*;
import dev.jayms.combat.duel.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class HcfSessionTest {
    @TempDir Path directory;
    final AtomicLong now=new AtomicLong(1000);
    final HcfRules rules=new HcfRules(2,30,2,20,12,10,5,100,200,100,50);
    static final class Floor implements CombatWorld {
        public OptionalDouble firstSolid(Vec3 a,Vec3 b,double r) {return b.y()<=1+r && b.y()<a.y()?OptionalDouble.of(Math.max(0,(a.y()-1-r)/(a.y()-b.y()))):OptionalDouble.empty();}
        public boolean safeLanding(Vec3 p,double w,double h) {return p.y()==1 && Math.abs(p.x())<31 && Math.abs(p.z())<39;}
        public Motion move(Vec3 p,Vec3 v,double w,double h) {var n=p.add(v);return new Motion(new Vec3(n.x(),Math.max(1,n.y()),n.z()),new Vec3(v.x(),n.y()<=1?0:v.y(),v.z()),n.y()<=1);}
    }
    HcfSession session() throws Exception {
        var store=new HcfStore(directory.resolve("season.hcf"));store.save(HcfStore.Saved.initial().phase(HcfSession.Phase.ACTIVE));return new HcfSession(new Floor(),store,rules,now::get);
    }
    void tick(HcfSession s,int n) throws Exception {for(int i=0;i<n;i++){now.addAndGet(50);s.step();}}
    boolean op(HcfSession s,String id,HcfCommand.Op op,int choice) throws Exception {return s.submit(id,HcfCommand.operation(s.season(),s.sequence(id)+1,op,choice));}
    void join(HcfSession s,String id,int team,HcfRole role) throws Exception {s.connect(id,team);assertTrue(op(s,id,HcfCommand.Op.EQUIP,role.ordinal()));s.step();}
    void deployed(HcfSession s,String id,Vec3 p) throws Exception {assertTrue(op(s,id,HcfCommand.Op.DEPLOY,0));s.simulation().setPose(id,new CombatSimulation.Pose(p,Vec3.ZERO,true));s.step();}
    void input(HcfSession s,String id,double yaw,double pitch,boolean attack,boolean use,boolean release,int slot) throws Exception {
        assertTrue(s.submit(id,new HcfCommand(s.season(),s.sequence(id)+1,0,HcfCommand.Op.INPUT,0,0,yaw,pitch,false,false,attack,use,release,slot,0)));
    }
    HcfSession.MemberView member(HcfSession s,String id) {return s.state().members().stream().filter(m->m.id().equals(id)).findFirst().orElseThrow();}
    @Test void friendlyFireAndAllHarmfulPathsRespectTeamsAndSpawn() throws Exception {
        var s=session();join(s,"blueone",0,HcfRole.FIGHTER);join(s,"bluetwo",0,HcfRole.ARCHER);join(s,"goldone",1,HcfRole.FIGHTER);tick(s,3);
        deployed(s,"blueone",new Vec3(0,1,0));deployed(s,"bluetwo",new Vec3(2,1,0));deployed(s,"goldone",new Vec3(8,1,0));
        input(s,"blueone",0,0,true,false,false,0);s.step();assertEquals(20,s.body("bluetwo").health());assertEquals(0,member(s,"bluetwo").tagUntil());
        input(s,"blueone",0,-89,false,true,false,7);tick(s,8);assertTrue(s.body("bluetwo").effects().isEmpty());assertTrue(s.body("blueone").effects().isEmpty());
        input(s,"blueone",0,-89,false,true,false,8);tick(s,8);assertTrue(s.body("bluetwo").effects().isEmpty());
        // Arrow collision cannot hurt a friend either.
        input(s,"blueone",0,0,false,true,false,6);tick(s,20);input(s,"blueone",0,0,false,false,true,6);tick(s,3);assertEquals(20,s.body("bluetwo").health());
        assertTrue(op(s,"goldone",HcfCommand.Op.HOME,0));s.simulation().worldDamage("goldone",100,CombatEvent.DamageKind.ENVIRONMENT);assertEquals(20,s.body("goldone").health());
    }
    @Test void archerMarkExpiresAndTeamFocusRaisesRealMeleeDamage() throws Exception {
        var s=session();join(s,"archer",0,HcfRole.ARCHER);join(s,"fighter",0,HcfRole.FIGHTER);join(s,"enemy",1,HcfRole.FIGHTER);tick(s,3);
        deployed(s,"archer",new Vec3(0,1,0));deployed(s,"fighter",new Vec3(-5,1,0));deployed(s,"enemy",new Vec3(5,1,0));
        input(s,"archer",0,0,false,true,false,6);tick(s,20);input(s,"archer",0,0,false,false,true,6);s.step();s.step();
        assertEquals(0,member(s,"enemy").markedBy());assertTrue(member(s,"enemy").markUntil()>s.state().tick());
        // Wait out hurt window, retain mark for the exact focus hit.
        tick(s,9);s.simulation().setPose("enemy",new CombatSimulation.Pose(new Vec3(5,1,0),Vec3.ZERO,true));s.simulation().setPose("fighter",new CombatSimulation.Pose(new Vec3(3,1,0),Vec3.ZERO,true));
        double before=s.body("enemy").health();input(s,"fighter",0,0,true,false,false,0);s.step();assertEquals(3,before-s.body("enemy").health(),1e-6);
        tick(s,13);assertEquals(-1,member(s,"enemy").markedBy());
    }
    @Test void bardSpendsOnceSupportsOnlyAlliesAndCleansUpOnChange() throws Exception {
        var s=session();join(s,"bard",0,HcfRole.BARD);join(s,"ally",0,HcfRole.FIGHTER);join(s,"enemy",1,HcfRole.FIGHTER);tick(s,3);
        deployed(s,"bard",new Vec3(0,1,0));deployed(s,"ally",new Vec3(2,1,0));deployed(s,"enemy",new Vec3(-2,1,0));
        var c=HcfCommand.operation(s.season(),s.sequence("bard")+1,HcfCommand.Op.ABILITY,0);assertTrue(s.submit("bard",c));assertFalse(s.submit("bard",c));
        assertEquals(70,member(s,"bard").energy());assertTrue(member(s,"ally").auraUntil()>0);assertEquals(0,member(s,"enemy").auraUntil());
        s.step();assertFalse(op(s,"bard",HcfCommand.Op.ABILITY,0));assertEquals(70,member(s,"bard").energy());
        assertTrue(op(s,"bard",HcfCommand.Op.HOME,0));assertEquals(0,member(s,"ally").auraUntil());s.step();assertTrue(op(s,"bard",HcfCommand.Op.EQUIP,HcfRole.FIGHTER.ordinal()));assertNull(member(s,"bard").active());
    }
    @Test void rogueUsesVictimFacingForRearConeAndCooldown() throws Exception {
        var s=session();join(s,"rogue",0,HcfRole.ROGUE);join(s,"enemy",1,HcfRole.FIGHTER);tick(s,3);
        deployed(s,"rogue",new Vec3(-2,1,0));deployed(s,"enemy",new Vec3(0,1,0));input(s,"enemy",0,0,false,false,false,0);s.step();
        input(s,"rogue",0,0,true,false,false,0);s.step();assertEquals(15.2,s.body("enemy").health(),1e-6);long cooldown=member(s,"rogue").abilityReadyAt();assertTrue(cooldown>s.state().tick());
        tick(s,11);s.simulation().setPose("rogue",new CombatSimulation.Pose(new Vec3(-2,1,0),Vec3.ZERO,true));s.simulation().setPose("enemy",new CombatSimulation.Pose(new Vec3(0,1,0),Vec3.ZERO,true));
        double hp=s.body("enemy").health();input(s,"rogue",0,0,true,false,false,0);s.step();assertEquals(2.4,hp-s.body("enemy").health(),1e-6);assertEquals(cooldown,member(s,"rogue").abilityReadyAt());
        tick(s,21);s.simulation().setPose("rogue",new CombatSimulation.Pose(new Vec3(2,1,0),Vec3.ZERO,true));s.simulation().setPose("enemy",new CombatSimulation.Pose(new Vec3(0,1,0),Vec3.ZERO,true));hp=s.body("enemy").health();input(s,"rogue",180,0,true,false,false,0);s.step();assertEquals(2.4,hp-s.body("enemy").health(),1e-6);
    }
    @Test void kitCapsArmorWarmupLimitAndBreakCleanup() throws Exception {
        var s=session();join(s,"bardone",0,HcfRole.BARD);s.connect("bardtwo",0);assertFalse(op(s,"bardtwo",HcfCommand.Op.EQUIP,HcfRole.BARD.ordinal()));s.step();
        assertFalse(op(s,"bardone",HcfCommand.Op.ABILITY,0));tick(s,3);assertEquals(HcfRole.BARD,member(s,"bardone").active());
        var k=HcfRole.BARD.kit();s.simulation().equip("bardone",new KitProfile("broken",k.slots(),k.armor().stream().map(a->new Armor(a.slot(),a.protection(),0)).toList()),false);s.step();assertNull(member(s,"bardone").active());
        assertEquals(4,HcfRole.FIGHTER.kit().slots().get(2).count());assertEquals(32,HcfRole.ARCHER.kit().slots().get(9).count());
        assertEquals(17,HcfRole.FIGHTER.kit().slots().stream().filter(x->x.item()==Item.HEAL_SPLASH).mapToInt(KitProfile.Stack::count).sum());
        assertThrows(IllegalArgumentException.class,()->new HcfRules(0,1,1,1,1,1,1,1,1,1,1));
    }
    @Test void combatLogoutPersistsDeathbanDtrLootAndRejectsEscape() throws Exception {
        var s=session();join(s,"attacker",0,HcfRole.FIGHTER);join(s,"victim",1,HcfRole.FIGHTER);tick(s,3);
        deployed(s,"attacker",new Vec3(0,1,0));deployed(s,"victim",new Vec3(2,1,0));input(s,"attacker",0,0,true,false,false,0);s.step();
        assertFalse(op(s,"victim",HcfCommand.Op.HOME,0));s.step();assertFalse(op(s,"victim",HcfCommand.Op.SAFE_LOGOUT,0));
        s.simulation().setPose("victim",new CombatSimulation.Pose(new Vec3(0,1,25.6),new Vec3(0,0,1),true));s.step();assertTrue(s.body("victim").pose().feet().z()<25.7);
        s.disconnect("victim");assertFalse(s.body("victim").alive());assertEquals(2,s.state().teams().get(1).dtr());assertEquals(1,s.state().loot().size());
        s.connect("victim",0);assertEquals(1,member(s,"victim").team());assertFalse(op(s,"victim",HcfCommand.Op.DEPLOY,0));
        var saved=new HcfStore(directory.resolve("season.hcf")).load();assertTrue(saved.members().stream().filter(m->m.id().equals("victim")).findFirst().orElseThrow().deathbanUntil()>now.get());
        var restarted=new HcfSession(new Floor(),new HcfStore(directory.resolve("season.hcf")),rules,now::get);
        // Still-deployed attacker suffers one crash defeat; the previous victim is not charged twice.
        assertEquals(2,restarted.state().teams().get(0).dtr());assertEquals(2,restarted.state().teams().get(1).dtr());assertEquals(1,restarted.state().loot().size());
        restarted.connect("victim",1);assertFalse(op(restarted,"victim",HcfCommand.Op.DEPLOY,0));now.addAndGet(101);restarted.step();assertTrue(op(restarted,"victim",HcfCommand.Op.DEPLOY,0));
    }
    @Test void dtrZeroOpensBoundedRaidFreezeThenRegenClosesIt() throws Exception {
        var store=new HcfStore(directory.resolve("season.hcf"));var fresh=HcfStore.Saved.initial();store.save(new HcfStore.Saved(1,1,List.of(),List.of(fresh.teams().get(0),new HcfStore.Team(1,1_000_000,1_000_000,16,0,true))));
        var s=new HcfSession(new Floor(),store,rules,now::get);join(s,"raider",0,HcfRole.FIGHTER);join(s,"defender",1,HcfRole.FIGHTER);tick(s,3);
        deployed(s,"raider",new Vec3(18,1,8));deployed(s,"defender",new Vec3(0,1,0));
        s.simulation().setPose("raider",new CombatSimulation.Pose(new Vec3(19.6,1,8),new Vec3(1,0,0),true));s.step();assertTrue(s.body("raider").pose().feet().x()<19.7);
        s.simulation().defeat("defender");s.step();assertEquals(0,s.state().teams().get(1).dtr());assertTrue(s.state().teams().get(1).raidable());
        s.simulation().setPose("raider",new CombatSimulation.Pose(new Vec3(21,1,8),Vec3.ZERO,true));int own=s.state().teams().get(0).stash(),enemy=s.state().teams().get(1).stash();assertTrue(op(s,"raider",HcfCommand.Op.RAID,0));assertEquals(own+1,s.state().teams().get(0).stash());assertEquals(enemy-1,s.state().teams().get(1).stash());
        now.addAndGet(199);s.step();assertEquals(0,s.state().teams().get(1).dtr());now.addAndGet(101);s.step();assertEquals(.25,s.state().teams().get(1).dtr());assertFalse(s.state().teams().get(1).raidable());assertFalse(op(s,"raider",HcfCommand.Op.RAID,0));
    }
    @Test void kothContestedResetTakeoverAndScore() throws Exception {
        var s=session();join(s,"blue",0,HcfRole.FIGHTER);join(s,"gold",1,HcfRole.FIGHTER);tick(s,3);
        deployed(s,"blue",new Vec3(-1,1,0));deployed(s,"gold",new Vec3(8,1,0));s.step();assertTrue(s.state().captureTicks()>0);
        s.simulation().setPose("gold",new CombatSimulation.Pose(new Vec3(1,1,0),Vec3.ZERO,true));s.step();assertTrue(s.state().contested());assertEquals(0,s.state().captureTicks());
        s.simulation().setPose("blue",new CombatSimulation.Pose(new Vec3(-8,1,0),Vec3.ZERO,true));tick(s,5);assertEquals(1,s.state().teams().get(1).score());assertEquals(0,s.state().teams().get(0).score());assertTrue(s.state().kothReadyAt()>s.state().tick());
    }
    @Test void economyFreeResupplyMinerAndPracticeIsolation() throws Exception {
        var s=session();join(s,"miner",0,HcfRole.MINER);s.step();int stash=s.state().teams().get(0).stash();assertTrue(op(s,"miner",HcfCommand.Op.RESUPPLY,0));assertEquals(stash-1,s.state().teams().get(0).stash());
        tick(s,3);assertTrue(op(s,"miner",HcfCommand.Op.CLAIM,0));s.step();deployed(s,"miner",new Vec3(-23,1,8));assertTrue(op(s,"miner",HcfCommand.Op.ABILITY,0));assertEquals(stash,s.state().teams().get(0).stash());s.step();assertFalse(op(s,"miner",HcfCommand.Op.ABILITY,0));
        tick(s,50);assertTrue(s.state().teams().get(0).stash()>stash);
        var duel=new DuelSession(DuelSession.Profile.NO_DEBUFF,new Floor(),1,100,1);duel.connected(0,true);duel.connected(1,true);duel.submit(0,DuelSession.Command.operation(1,1,DuelSession.Op.READY,0,0));duel.submit(1,DuelSession.Command.operation(1,1,DuelSession.Op.READY,0,0));duel.step();
        assertEquals(DuelSession.Phase.LIVE,duel.phase());assertEquals(20,duel.state().fighters().get(0).body().health());assertNotEquals(DuelSession.WORLD_ID,HcfSession.WORLD_ID);
    }
    @Test void seasonOpeningEndAndExplicitResetFenceOldCommands() throws Exception {
        var store=new HcfStore(directory.resolve("season.hcf"));store.save(HcfStore.Saved.initial());var s=new HcfSession(new Floor(),store,rules,now::get);s.connect("player",0);assertFalse(op(s,"player",HcfCommand.Op.DEPLOY,0));
        store.save(store.load().phase(HcfSession.Phase.ENDED));var end=new HcfSession(new Floor(),store,rules,now::get);end.connect("player",0);assertFalse(op(end,"player",HcfCommand.Op.DEPLOY,0));
        store.save(store.load().reset());var reset=new HcfSession(new Floor(),store,rules,now::get);assertEquals(2,reset.season());assertTrue(reset.state().members().isEmpty());assertEquals(3,reset.state().teams().get(0).dtr());reset.connect("player",0);
        assertFalse(reset.submit("player",HcfCommand.operation(1,1,HcfCommand.Op.CLAIM,0)));
    }
    @Test void negativeDtrRemainsRaidableAndDefeatsDoNotRepeatOnRestart() throws Exception {
        var store=new HcfStore(directory.resolve("negative.hcf"));var fresh=HcfStore.Saved.initial();
        store.save(new HcfStore.Saved(2,1,List.of(new HcfStore.Member("crashone",1,0,0,true),new HcfStore.Member("crashtwo",1,0,0,true),new HcfStore.Member("safeone",0,0,0,false)),
            List.of(fresh.teams().get(0),new HcfStore.Team(1,0,0,16,0,true))));
        var s=new HcfSession(new Floor(),store,rules,now::get);assertEquals(-1,s.state().teams().get(1).dtr());assertTrue(s.state().teams().get(1).raidable());assertEquals(3,store.load().members().size());
        var again=new HcfSession(new Floor(),store,rules,now::get);assertEquals(-1,again.state().teams().get(1).dtr());assertEquals(3,again.state().members().size());
        assertTrue(store.load().members().stream().noneMatch(HcfStore.Member::deployed));
    }
    @Test void spawnShieldBlocksOutgoingDamageAndFiniteLootCollectsOnce() throws Exception {
        var r=HcfRules.initial();var store=new HcfStore(directory.resolve("shield.hcf"));store.save(HcfStore.Saved.initial().phase(HcfSession.Phase.ACTIVE));var s=new HcfSession(new Floor(),store,r,now::get);
        s.connect("attacker",0);s.connect("victim",1);deployed(s,"attacker",new Vec3(0,1,0));deployed(s,"victim",new Vec3(2,1,0));
        input(s,"attacker",0,0,true,false,false,0);s.step();assertEquals(20,s.body("victim").health());assertEquals(0,member(s,"attacker").tagUntil());
        tick(s,101);input(s,"attacker",0,-89,false,true,false,1);tick(s,5);assertEquals(0,s.body("attacker").inventory().get(1).count());
        s.simulation().defeat("victim");s.step();assertEquals(1,s.state().loot().size());s.simulation().setPose("attacker",new CombatSimulation.Pose(new Vec3(1,1,0),Vec3.ZERO,true));
        assertTrue(op(s,"attacker",HcfCommand.Op.LOOT,0));var once=s.body("attacker").inventory();s.step();assertTrue(op(s,"attacker",HcfCommand.Op.LOOT,0));assertEquals(once,s.body("attacker").inventory());
        assertTrue(new HcfStore(directory.resolve("shield.hcf")).load().drops().size()<=1);
    }

    @Test void taggedPearlCannotLandInsideProtectedSpawnAndStillSpendsThrow() throws Exception {
        var s=session();join(s,"attacker",0,HcfRole.FIGHTER);join(s,"victim",1,HcfRole.FIGHTER);tick(s,3);
        deployed(s,"attacker",new Vec3(0,1,0));deployed(s,"victim",new Vec3(2,1,0));input(s,"attacker",0,0,true,false,false,0);s.step();
        s.simulation().setPose("victim",new CombatSimulation.Pose(new Vec3(0,1,25.5),Vec3.ZERO,true));double hp=s.body("victim").health();
        input(s,"victim",90,0,false,true,false,2);tick(s,20);
        assertEquals(25.5,s.body("victim").pose().feet().z(),1e-6);assertEquals(hp-2,s.body("victim").health(),1e-6);
        assertEquals(3,s.body("victim").inventory().get(2).count());assertTrue(s.body("victim").pearlReadyAt()>s.state().tick());assertTrue(s.state().projectiles().isEmpty());
    }

    @Test void serverTickBatchCommitsRosterSupplyAndDeploymentTogether() throws Exception {
        var s=session();s.connect("player",0);var store=new HcfStore(directory.resolve("season.hcf"));var before=store.load();
        s.beginBatch();assertTrue(op(s,"player",HcfCommand.Op.RESUPPLY,0));assertTrue(op(s,"player",HcfCommand.Op.DEPLOY,0));s.step();
        assertEquals(before,store.load(),"No partial tick transaction is written");s.commitBatch();var after=store.load();
        assertEquals(15,after.teams().get(0).stash());assertTrue(after.members().get(0).deployed());assertThrows(IllegalStateException.class,s::commitBatch);
        s.beginBatch();assertThrows(IllegalStateException.class,s::beginBatch);s.commitBatch();
    }

}
