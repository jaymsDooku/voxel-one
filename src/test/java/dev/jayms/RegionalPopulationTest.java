package dev.jayms;

import dev.jayms.net.*;
import dev.jayms.net.city.*;
import dev.jayms.ui.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

class RegionalPopulationTest {
    @TempDir Path temp;
    static double money(RegionalPopulation.State s) {
        return s.groups().stream().mapToDouble(g->g.savings()+g.treasury()).sum()
                +s.agents().stream().mapToDouble(RegionalPopulation.Agent::savings).sum();
    }
    static double hunger(RegionalPopulation.State s) {
        return s.groups().stream().mapToDouble(g->g.count()*g.hunger()).sum()
                +s.agents().stream().mapToDouble(RegionalPopulation.Agent::hunger).sum();
    }
    static int homes(RegionalPopulation.State s) {
        return s.groups().stream().mapToInt(RegionalPopulation.Group::housed).sum()
                +(int)s.agents().stream().filter(RegionalPopulation.Agent::housed).count();
    }
    static int jobs(RegionalPopulation.State s) {
        return s.groups().stream().mapToInt(RegionalPopulation.Group::employed).sum()
                +(int)s.agents().stream().filter(RegionalPopulation.Agent::employed).count();
    }
    static CitySimulation city() {
        var ground=new CityTest.Ground();
        return new CitySimulation(new GameConfig(true,false,60,8),ground,ground.terrain,null,ProductionCatalog.toolEra());
    }
    @Test void tenMillionResidentsUseThirtyGroupsAndBoundedAgents() throws Exception {
        var p=new RegionalPopulation(RegionalPopulation.State.empty());
        for(int i=0;i<10;i++) p.settle(1_000_000,30);
        assertEquals(10_000_000,p.population());assertEquals(30,p.state().groups().size());
        double initial=money(p.state());
        p.focus(1);assertEquals(64,p.state().agents().size());
        long start=System.nanoTime();
        for(int i=0;i<6000;i++) p.advance(.1,60);
        double millis=(System.nanoTime()-start)/1e6;
        assertEquals(10_000_000,p.population());assertEquals(initial,money(p.state()),initial*1e-10);
        assertTrue(p.state().groups().stream().allMatch(g->g.hunger()>=0 && g.hunger()<=100));
        var out=new ByteArrayOutputStream();RegionalPopulation.write(new DataOutputStream(out),p.state());
        assertTrue(out.size()<8192,"Snapshot must scale with groups, not residents");
        assertEquals(p.state(),RegionalPopulation.read(new DataInputStream(new ByteArrayInputStream(out.toByteArray()))));
        System.out.printf(Locale.ROOT,"SCALE: population=10000000 groups=30 agents=64 steps=6000 elapsed_ms=%.3f snapshot_bytes=%d%n",millis,out.size());
    }
    @Test void maximumGroupCountRemainsBoundedAndFullyActiveSmallGroupsRoundTrip() throws Exception {
        var p=new RegionalPopulation(RegionalPopulation.State.empty());
        for(int i=0;i<RegionalPopulation.MAX_GROUPS;i++) {
            int n=RegionalPopulation.MAX_POPULATION/RegionalPopulation.MAX_GROUPS
                    +(i<RegionalPopulation.MAX_POPULATION%RegionalPopulation.MAX_GROUPS?1:0);
            p.add(new RegionalPopulation.Group(i+1,i%1024+1,i%3,i+1,n,512,30,512,n,n/2,90,
                    n*12.0,n*4.0,n*100.0,12,3));
        }
        long start=System.nanoTime();
        for(int i=0;i<600;i++) p.advance(.1,60);
        var bytes=new ByteArrayOutputStream();RegionalPopulation.write(new DataOutputStream(bytes),p.state());
        assertEquals(10_000_000,p.population());assertEquals(4096,p.state().groups().size());assertTrue(bytes.size()<500_000);
        assertEquals(p.state(),RegionalPopulation.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
        System.out.printf(Locale.ROOT,"GROUP_BOUND: population=10000000 groups=4096 steps=600 elapsed_ms=%.3f snapshot_bytes=%d%n",
                (System.nanoTime()-start)/1e6,bytes.size());
        var tiny=new RegionalPopulation(RegionalPopulation.State.empty());tiny.settle(3,30);tiny.focus(1);
        assertEquals(3,tiny.state().agents().size());assertTrue(tiny.state().groups().stream().allMatch(g->g.count()==0));
        tiny.advance(.1,60);var before=tiny.state();tiny.focus(0);
        assertEquals(3,tiny.population());assertEquals(money(before),money(tiny.state()),1e-8);
        assertEquals(hunger(before),hunger(tiny.state()),1e-8);
    }

    @Test void switchingDistrictsConservesCountsWalletsNeedsAndAssignments() {
        var p=new RegionalPopulation(RegionalPopulation.State.empty());p.settle(1000,30);p.settle(1000,30);
        var before=p.state();
        for(int i=0;i<100;i++) { p.focus(i%2+1);assertEquals(64,p.state().agents().size()); }
        p.focus(0);var after=p.state();
        assertEquals(before.population(),after.population());assertEquals(money(before),money(after),1e-6);
        assertEquals(hunger(before),hunger(after),1e-6);assertEquals(homes(before),homes(after));assertEquals(jobs(before),jobs(after));
        var g=p.state().groups().get(0);p.focusNear(g.x(),g.z());assertEquals(g.district(),p.state().focus());
        p.focusNear(0,0);assertEquals(0,p.state().agents().size());
    }
    @Test void nearbyAgentsWalkWithoutTeleportingAndHaveIndividualWallets() {
        var p=new RegionalPopulation(RegionalPopulation.State.empty());p.settle(1000,30);p.focus(1);
        boolean changed=false;
        for(int step=0;step<400;step++) {
            var before=p.state().agents();p.advance(.1,60);var after=p.state().agents();
            for(int i=0;i<before.size();i++) {
                var a=before.get(i);var b=after.get(i);
                assertTrue(Math.hypot(a.x()-b.x(),a.z()-b.z())<=.151);
                changed|=a.savings()!=b.savings();
            }
        }
        assertTrue(changed);assertEquals(64,RegionalPopulation.citizens(p.state()).size());
    }
    @Test void paidWorkProducesStockAndFoodPurchasesTransferExistingMoney() {
        var g=new RegionalPopulation.Group(1,1,0,1,100,512,30,512,100,50,90,1000,400,10000,12,3);
        var p=new RegionalPopulation(new RegionalPopulation.State(0,0,List.of(g),List.of()));
        p.advance(1,60);var next=p.state().groups().get(0);
        assertEquals(1010,next.savings(),1e-8);assertEquals(9990,next.treasury(),1e-8);
        assertEquals(400+10.0/3,next.food(),1e-8);assertEquals(88.8,next.hunger(),1e-8);
        var hungry=new RegionalPopulation.Group(1,1,0,1,100,512,30,512,100,0,20,300,10,0,12,3);
        p=new RegionalPopulation(new RegionalPopulation.State(0,0,List.of(hungry),List.of()));
        p.advance(1,60);next=p.state().groups().get(0);
        assertEquals(270,next.savings(),1e-8);assertEquals(30,next.treasury(),1e-8);
        assertEquals(0,next.food(),1e-8);assertEquals(21.8,next.hunger(),1e-8);
    }

    @Test void starvationNoStockNoMoneyAndUnemploymentDoNotMintResources() {
        var g=new RegionalPopulation.Group(1,1,0,1,1000,512,30,512,0,0,5,0,0,0,12,3);
        var p=new RegionalPopulation(new RegionalPopulation.State(0,0,List.of(g),List.of()));
        for(int i=0;i<100;i++) p.advance(.1,60);
        var after=p.state().groups().get(0);
        assertEquals(0,after.savings());assertEquals(0,after.treasury());assertEquals(0,after.food());assertEquals(0,after.hunger());
        p.focus(1);p.advance(.1,60);assertTrue(p.state().agents().stream().allMatch(a->a.hunger()==0 && a.savings()==0));
    }
    @Test void malformedAndOverLimitStatesAndCommandsAreRejectedWithoutMutation() throws Exception {
        var p=new RegionalPopulation(RegionalPopulation.State.empty());
        for(int i=0;i<10;i++) p.settle(1_000_000,30);
        var before=p.state();assertThrows(IllegalArgumentException.class,()->p.settle(1000,30));assertEquals(before,p.state());
        assertThrows(IllegalArgumentException.class,()->p.focus(999));assertEquals(before,p.state());
        assertThrows(IllegalArgumentException.class,()->p.advance(Double.NaN,60));assertEquals(before,p.state());
        assertThrows(IllegalArgumentException.class,()->new CityCommand(CityCommand.SETTLE_DISTRICT,Integer.MAX_VALUE,List.of()));
        var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);out.writeDouble(0);out.writeInt(0);out.writeInt(Integer.MAX_VALUE);
        assertThrows(IOException.class,()->RegionalPopulation.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
        assertThrows(IllegalArgumentException.class,()->new RegionalPopulation.State(0,1,List.of(),List.of()));
        assertThrows(IllegalArgumentException.class,()->new RegionalPopulation.Group(1,1,0,1,1,0,0,0,1,1,Double.NaN,0,0,0,12,3));
    }
    @Test void simulationCommandsMetricsSaveReloadAndLegacyMigration() throws Exception {
        var s=city();var original=s.frame();
        assertTrue(s.command(new CityCommand(CityCommand.SETTLE_DISTRICT,1_000_000,List.of()),1,null).contains("1000000 immigrants"));
        s.command(new CityCommand(CityCommand.SETTLE_DISTRICT,1_000_000,List.of()),1,null);
        s.command(new CityCommand(CityCommand.FOCUS_DISTRICT,1,List.of()),1,null);s.advance(.11);
        var frame=s.frame();var metrics=CityMetrics.from(frame);
        assertEquals(2_000_012,metrics.population());assertEquals(2_000_000,frame.population().population());
        assertEquals(76,frame.visibleCitizens().size());assertEquals(12,frame.citizens().size());
        var nearby=frame.visibleCitizens().get(12);
        assertTrue(CityOccupancy.overlaps(frame,nearby.x(),nearby.y(),nearby.z(),.2f,1,.2f));
        assertEquals(metrics.population(),metrics.groups().stream().mapToInt(CityMetrics.Group::population).sum());
        assertTrue(metrics.housed()>=2_000_000);assertTrue(metrics.employed()>=1_499_990);
        var file=temp.resolve("regional.city");s.save(file);assertEquals(frame,CitySimulation.load(file));
        var ground=new CityTest.Ground();var restored=new CitySimulation(frame.config(),ground,ground.terrain,CitySimulation.load(file));
        s.advance(.11);restored.advance(.11);assertEquals(s.frame().population(),restored.frame().population());
        var bytes=new ByteArrayOutputStream();original.write(new DataOutputStream(bytes),9);
        var legacy=CityFrame.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())),9);
        assertEquals(RegionalPopulation.State.empty(),legacy.population());assertEquals(original.citizens(),legacy.citizens());
        assertThrows(IOException.class,()->frame.write(new DataOutputStream(new ByteArrayOutputStream()),9));
    }
    @Test void formatElevenRegionalSaveMigratesWithoutReadingFlights() throws Exception {
        var s=city();s.command(new CityCommand(CityCommand.SETTLE_DISTRICT,1_000_000,List.of()),1,null);
        var frame=s.frame();var bytes=new ByteArrayOutputStream();
        var out=new DataOutputStream(bytes);out.writeInt(0x4349543B);frame.write(out,11);
        var file=temp.resolve("base-format-eleven.city");java.nio.file.Files.write(file,bytes.toByteArray());
        var loaded=CitySimulation.load(file);
        assertEquals(frame.population(),loaded.population());
        assertTrue(loaded.aviation().flights().isEmpty());
        var current=new ByteArrayOutputStream();loaded.write(new DataOutputStream(current));
        assertEquals(loaded,CityFrame.read(new DataInputStream(new ByteArrayInputStream(current.toByteArray()))));
        assertEquals(8,CityCommand.SETTLE_DISTRICT);assertEquals(9,CityCommand.FOCUS_DISTRICT);
        assertEquals(10,CityCommand.RUNWAY);assertEquals(11,CityCommand.FLIGHT);
    }
    @Test void dashboardSettlesFocusesAndLocatesThroughProductionCommands() {
        var s=city();var ui=new MayorDashboard();ui.show();
        ui.click(920,60,1280,720,s.frame(),id->fail());assertEquals(6,ui.tab);
        ui.click(440,180,1280,720,s.frame(),id->fail("Settlement selected a citizen"));
        // Without the command sink, UI is read-only.
        assertEquals(0,s.population.population());
        ui.submit=c->s.command(c,1,null);
        ui.click(440,180,1280,720,s.frame(),id->fail());assertEquals(1_000_000,s.population.population());
        ui.click(40,285,1280,720,s.frame(),id->fail());assertEquals(64,s.population.state().agents().size());
        var selected=new ArrayList<Integer>();ui.click(630,180,1280,720,s.frame(),selected::add);
        assertEquals(List.of(RegionalPopulation.AGENT_ID_BASE),selected);
    }
}
