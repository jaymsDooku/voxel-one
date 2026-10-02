package dev.jayms;

import static org.junit.jupiter.api.Assertions.*;

import dev.jayms.net.*;
import dev.jayms.net.city.*;
import dev.jayms.render.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.file.*;
import java.util.*;

class CityTest {
    @TempDir Path temp;

    static class Ground implements CitySimulation.Ground {
        final Terrain terrain = new Terrain(Terrain.DEFAULT_SEED);
        final WorldVoxels voxels = new WorldVoxels(terrain);
        final Map<String, Protocol.Edit> edits = new LinkedHashMap<>();
        boolean occupied;

        public int type(int x, int y, int z) {
            return voxels.type(x, y, z);
        }

        public void apply(List<Protocol.Edit> batch) {
            for (var e : batch) {
                assertTrue(e.valid());
                voxels.apply(e);
                WorldVoxels.remember(edits, e);
            }
        }

        public boolean occupied(int x, int y, int z, int w, int d) {
            return occupied;
        }
    }

    CitySimulation simulation(Ground ground) {
        return new CitySimulation(
                new GameConfig(true, false, 1200, 10), ground, ground.terrain, null);
    }

    static Polygon box(int x, int z, int w, int d) {
        return new Polygon(
                List.of(
                        new Polygon.Point(x, z),
                        new Polygon.Point(x + w, z),
                        new Polygon.Point(x + w, z + d),
                        new Polygon.Point(x, z + d)));
    }

    @Test
    void firstPersonGroundAimHitsHorseLegs() {
        var h = new CityFrame.Horse(1, 8.5f, 24.01f, 20.5f, 90, 0, 0);
        var eye = new org.joml.Vector3f(8.5f, 25.61f, 24.5f);
        var direction = new org.joml.Vector3f(0, -.342f, -.94f).normalize();
        assertTrue(dev.jayms.player.HorseInteraction.distance(h, eye, direction) < 5);
        assertTrue(
                Float.isInfinite(
                        dev.jayms.player.HorseInteraction.distance(
                                h, eye, new org.joml.Vector3f(1, 0, 0))));
    }

    @Test
    void mountedPlayerMovesDismountsAndKeepsCollision() {
        World world = new World();
        Chunk chunk = new Chunk();
        for (int x = 0; x < 16; x++)
            for (int z = 0; z < 16; z++) chunk.setBlock(x, 0, z, Blocks.STONE);
        world.addChunk(new ChunkPos(0, 0, 0), chunk);
        var player =
                new dev.jayms.player.Player(
                        new org.joml.Vector3f(3.5f, 1.01f, 3.5f), 0, 0, new Camera());
        player.mount(true, new org.joml.Vector3f(3.5f, 1.01f, 3.5f));
        player.toggleFlight();
        assertFalse(player.flying());
        for (int n = 0; n < 30; n++) player.step(world, .02f, 1, 0, false, false);
        assertTrue(player.position().x > 6);
        assertTrue(player.position().y > 1.7f);
        player.mount(false, null);
        player.step(world, .1f, 0, 0, false, false);
        assertFalse(player.mounted());
        assertEquals(1, player.position().y, .02);
        assertFalse(player.collides(world));
    }

    @Test
    void ecsQueriesComponentsAndRemoval() {
        var ecs = new Ecs();
        int a = ecs.create(), b = ecs.create();
        ecs.put(a, String.class, "citizen");
        ecs.put(a, Integer.class, 4);
        ecs.put(b, String.class, "horse");
        assertEquals(List.of(a), ecs.query(String.class, Integer.class));
        ecs.remove(a);
        assertEquals(List.of(b), ecs.query(String.class));
        ecs.restore(9);
        assertEquals(10, ecs.create());
    }

    @Test
    void convexPolygonsRasterizeBothDirectionsAndRejectInvalidShapes() {
        var p =
                new Polygon(
                        List.of(
                                new Polygon.Point(0, 0),
                                new Polygon.Point(10, 0),
                                new Polygon.Point(7, 8),
                                new Polygon.Point(2, 9)));
        var reverse = new ArrayList<>(p.vertices());
        Collections.reverse(reverse);
        assertEquals(p.cells(), new Polygon(reverse).cells());
        assertTrue(p.contains(5, 4));
        assertFalse(p.contains(9, 8));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new Polygon(
                                List.of(
                                        new Polygon.Point(0, 0),
                                        new Polygon.Point(10, 0),
                                        new Polygon.Point(5, 2),
                                        new Polygon.Point(10, 8),
                                        new Polygon.Point(0, 8))));
        assertThrows(IllegalArgumentException.class, () -> box(0, 0, 2, 2));
        assertThrows(IllegalArgumentException.class, () -> new Polygon.Point(Float.NaN, 0));
    }

    @Test
    void zoningRequiresRoadAccessSpaceAndNoOverlap() {
        var g = new Ground();
        var sim = simulation(g);
        var pose = new Protocol.Pose(1, 8, 40, 24, 0, 0);
        assertTrue(
                sim.command(
                                new CityCommand(
                                        CityCommand.ZONE, 0, box(100, 100, 12, 12).vertices()),
                                1,
                                pose)
                        .contains("touch"));
        assertTrue(
                sim.command(
                                new CityCommand(
                                        CityCommand.ZONE, 0, box(14, 28, 10, 10).vertices()),
                                1,
                                pose)
                        .contains("overlap"));
        assertTrue(
                sim.command(
                                new CityCommand(CityCommand.ZONE, 0, box(0, 23, 8, 8).vertices()),
                                1,
                                pose)
                        .contains("cover roads"));
        int count = sim.frame().zones().size();
        assertTrue(
                sim.command(
                                new CityCommand(
                                        CityCommand.ZONE,
                                        1,
                                        new Polygon(
                                                        List.of(
                                                                new Polygon.Point(26, 12),
                                                                new Polygon.Point(42, 14),
                                                                new Polygon.Point(42, 23),
                                                                new Polygon.Point(26, 23)))
                                                .vertices()),
                                1,
                                pose)
                        .contains("created"));
        assertEquals(count + 1, sim.frame().zones().size());
        g.occupied = true;
        assertTrue(
                sim.command(
                                new CityCommand(
                                        CityCommand.ROAD,
                                        0,
                                        List.of(
                                                new Polygon.Point(44, 24),
                                                new Polygon.Point(60, 24))),
                                1,
                                pose)
                        .contains("player"));
    }

    @Test
    void starterCityBuildsAllStructuresAndAssignsHouseholdsAndJobs() {
        var g = new Ground();
        var sim = simulation(g);
        for (int i = 0; i < 160; i++) sim.advance(1);
        var frame = sim.frame();
        assertEquals(12, frame.citizens().size());
        assertEquals(
                3, frame.citizens().stream().map(CityFrame.Citizen::cohort).distinct().count());
        assertTrue(frame.buildings().stream().anyMatch(b -> b.type() == 0));
        assertTrue(frame.buildings().stream().anyMatch(b -> b.type() == 1));
        assertTrue(frame.buildings().stream().anyMatch(b -> b.type() == 2));
        assertTrue(frame.citizens().stream().allMatch(c -> c.home() > 0 && c.job() > 0));
        assertTrue(g.edits.values().stream().anyMatch(e -> e.depth() == 1));
        assertTrue(g.edits.values().stream().anyMatch(e -> e.type() == Blocks.LED));
        for (var b : frame.buildings()) assertEquals(0, g.type(b.x() + 2, b.y() + 1, b.z()));
    }

    @Test
    void populationTravelsWorksBuysFoodAndRidesHorses() {
        var g = new Ground();
        var sim = simulation(g);
        var initial = sim.frame();
        boolean walked = false, rode = false, worked = false, ate = false;
        var previous = new HashMap<Integer, Float>();
        for (int i = 0; i < 700; i++) {
            sim.advance(1);
            var f = sim.frame();
            for (var c : f.citizens()) {
                walked |= c.phase() > 1 && c.cohort() == 0;
                rode |= c.horse() != 0;
                worked |= c.activity().equals("Working in mine");
                Float old = previous.put(c.id(), c.hunger());
                if (old != null && c.hunger() > old + 10) ate = true;
            }
        }
        assertTrue(walked);
        assertTrue(rode);
        assertTrue(worked);
        assertTrue(ate);
        assertTrue(
                sim.frame().citizens().stream()
                        .anyMatch(c -> c.money() > initial.citizens().get(c.id() - 1).money()));
    }

    @Test
    void horsesAreExclusiveReleaseAndPersistWithoutHumanClaims() throws Exception {
        var g = new Ground();
        var sim = simulation(g);
        var h = sim.frame().horses().get(0);
        var p = new Protocol.Pose(101, h.x(), h.y(), h.z(), 0, 0);
        assertTrue(
                sim.command(new CityCommand(CityCommand.RIDE, h.id(), List.of()), 101, p)
                        .startsWith("Mounted"));
        assertEquals(h.id(), sim.riddenBy(101));
        assertTrue(
                sim.command(new CityCommand(CityCommand.RIDE, h.id(), List.of()), 102, p)
                        .contains("already"));
        sim.riderMoved(101, new Protocol.Pose(101, 20, h.y() + .75f, 24, 0, 0));
        assertEquals(20, sim.frame().horses().get(0).x());
        sim.save(temp.resolve("city"));
        var saved = CitySimulation.load(temp.resolve("city"));
        var restored = new CitySimulation(saved.config(), g, g.terrain, saved);
        assertEquals(0, restored.riddenBy(101));
        sim.release(101);
        assertEquals(0, sim.riddenBy(101));
    }

    @Test
    void cityStateRoundTripsClockEconomyZonesAndBuildings() throws Exception {
        var g = new Ground();
        var sim = simulation(g);
        for (int i = 0; i < 60; i++) sim.advance(1);
        var f = sim.frame();
        var bytes = new ByteArrayOutputStream();
        f.write(new DataOutputStream(bytes));
        assertEquals(
                f,
                CityFrame.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
        sim.save(temp.resolve("city"));
        var loaded = CitySimulation.load(temp.resolve("city"));
        assertEquals(f, loaded);
        var reload = new CitySimulation(f.config(), g, g.terrain, loaded);
        assertEquals(
                f.citizens().stream().map(CityFrame.Citizen::money).toList(),
                reload.frame().citizens().stream().map(CityFrame.Citizen::money).toList());
        assertEquals(f.buildings(), reload.frame().buildings());
    }

    @Test
    void gameClockWrapsAndNightPreservesColoredLighting() {
        var config = new GameConfig(true, true, 1200, 8);
        assertEquals(8, config.hour(1200), .0001);
        assertTrue(Daylight.at(config, 200).intensity() > Daylight.at(config, 800).intensity());
        assertTrue(Daylight.at(config, 800).ambient() < .1);
        var day = LightVolume.bake(0, 0, 0, 5, 5, 5, 1, (x, y, z) -> 0);
        var night =
                LightVolume.bake(
                        0,
                        0,
                        0,
                        5,
                        5,
                        5,
                        .06f,
                        (x, y, z) -> x == 2 && y == 2 && z == 2 ? 0xfeff0000 : 0);
        assertTrue(day.sample(1, 2, 2)[2] > night.sample(1, 2, 2)[2] * 3);
        assertTrue(night.sample(1, 2, 2)[0] > 1);
        assertThrows(IllegalArgumentException.class, () -> new GameConfig(true, true, 0, 12));
    }
}
