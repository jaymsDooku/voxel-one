package dev.jayms.net.city;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import dev.jayms.net.*;
import java.util.*;
import java.lang.reflect.*;

class RoadSpacingTest {
    @Test void validationAndSweptClearance() {
        for (float bad : new float[] {-1, 2.1f, Float.NaN, Float.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class, () -> new RoadSpacing(bad, 1));
        assertTrue(CitySimulation.clearStep(0, 0, 2, 0, 1, 0, 0));
        assertFalse(CitySimulation.clearStep(0, 0, 2, 0, 1, 0, .8f));
        assertTrue(CitySimulation.clearStep(0, 0, -.1f, 0, .2f, 0, .8f));
        assertFalse(CitySimulation.clearStep(0, 0, .1f, 0, .2f, 0, .8f));
        assertTrue(CitySimulation.clearStep(0, 0, .1f, 0, 1, 0, .8f));
    }

    @Test void opposingWalkersPassAndCrowdedSpawnCanSeparate() throws Exception {
        exercise(.8f, false, false, false);
        exercise(.8f, false, false, true);
        exercise(1.2f, false, false, false);
        exercise(1.4f, false, true, false);
        exercise(.8f, true, false, false);
    }

    @Test void acceleratedTravelRetainsClearance() throws Exception {
        exercise(.8f, false, false, false, 60);
        exercise(1.4f, false, true, false, 60);
    }

    private void exercise(float gap, boolean overlap, boolean mounted, boolean following) throws Exception {
        exercise(gap, overlap, mounted, following, 1200);
    }

    private void exercise(float gap, boolean overlap, boolean mounted, boolean following,
            double daySeconds) throws Exception {
        String previous = System.getProperty("voxel.road.pedestrianSpacing");
        try {
            System.setProperty("voxel.road.pedestrianSpacing", Float.toString(gap));
            var terrain = new Terrain(Terrain.DEFAULT_SEED);
            int grade = Math.max(-26, Math.min(88, terrain.column(8,24).height()));
            var ground = new CitySimulation.Ground() {
                public int type(int x,int y,int z) { return y <= grade ? Blocks.STONE : Blocks.AIR; }
                public void apply(List<Protocol.Edit> edits) {}
                public boolean occupied(int x,int y,int z,int w,int d) { return false; }
            };
            var sim = new CitySimulation(new GameConfig(false, false, daySeconds, 10), ground, terrain, null);
            var road=CitySimulation.class.getDeclaredMethod("road",List.class);
            road.setAccessible(true);
            road.invoke(sim,List.of(new Polygon.Point(-8,24),new Polygon.Point(12,24)));
            assertFalse(sim.frame().roads().isEmpty());
            int a = add(sim, 0.5f), b = add(sim, overlap ? .5f : 4.5f);
            if (mounted) {
                for (int id : new int[] {a,b}) {
                    int horse=sim.ecs.create();
                    var pos=sim.ecs.get(id,CitySimulation.Position.class);
                    sim.ecs.put(horse,CitySimulation.Position.class,new CitySimulation.Position(pos.x,pos.y,pos.z));
                    var mount=new CitySimulation.Mount(); mount.rider=-id;
                    sim.ecs.put(horse,CitySimulation.Mount.class,mount);
                    sim.ecs.get(id,CitySimulation.Household.class).horse=horse;
                }
            }
            var pa = sim.ecs.get(a, CitySimulation.Position.class);
            var pb = sim.ecs.get(b, CitySimulation.Position.class);
            var ta = sim.ecs.get(a, CitySimulation.Travel.class);
            var tb = sim.ecs.get(b, CitySimulation.Travel.class);
            ta.route.add(new Polygon.Cell(8,24)); tb.route.add(new Polygon.Cell(following ? 12 : -4,24));
            var travel = CitySimulation.class.getDeclaredMethod("travel", int.class,
                    CitySimulation.Position.class, CitySimulation.Household.class,
                    CitySimulation.Travel.class, float.class);
            travel.setAccessible(true);
            double last = overlap ? 0 : gap;
            for (int i=0;i<500;i++) {
                for (int id : new int[]{a,b}) {
                    var t = sim.ecs.get(id, CitySimulation.Travel.class);
                    if (!t.route.isEmpty()) travel.invoke(sim,id,
                            sim.ecs.get(id,CitySimulation.Position.class),
                            sim.ecs.get(id,CitySimulation.Household.class),t,.05f);
                    double separation = Math.hypot(pa.x-pb.x,pa.z-pb.z);
                    assertTrue(separation >= Math.min(gap,last)-.0001, "Clearance decreased gap="+gap+" mounted="+mounted+" step="+i+" distance="+separation+" last="+last+" pos="+pa.x+","+pa.z+" / "+pb.x+","+pb.z);
                    last=separation;
                }
            }
            assertTrue(ta.route.isEmpty() && tb.route.isEmpty(), "Both walkers arrive");
            assertEquals(8.5f,pa.x,.001); assertEquals(following ? 12.5f : -3.5f,pb.x,.001);
        } finally {
            if (previous == null) System.clearProperty("voxel.road.pedestrianSpacing");
            else System.setProperty("voxel.road.pedestrianSpacing", previous);
        }
    }
    private int add(CitySimulation sim, float x) {
        int id=sim.ecs.create();
        sim.ecs.put(id,CitySimulation.Position.class,new CitySimulation.Position(x,33,24.5f));
        sim.ecs.put(id,CitySimulation.Household.class,new CitySimulation.Household("Test walker",0));
        sim.ecs.put(id,CitySimulation.Travel.class,new CitySimulation.Travel());
        return id;
    }
}
