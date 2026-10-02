package dev.jayms;

import static org.junit.jupiter.api.Assertions.*;

import dev.jayms.net.city.*;
import dev.jayms.player.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.util.*;

class CityTimeTest {
    @TempDir Path temp;

    @Test
    void calendarRollsAtMidnightRatherThanTwentyFourHoursAfterStarting() {
        var config = new GameConfig(true, true, 1200, 8);
        assertEquals(1, config.time(799).day());
        assertEquals(2, config.time(800).day());
        assertEquals(0, config.time(800).hour(), .00001);
        assertEquals(4, config.time(3600).day());
        assertEquals(8, config.time(3600).hour(), .00001);
        assertEquals("Day 2 | 00:00 | Night", config.time(800).label());
        assertEquals(CityTime.Period.MORNING, config.time(1100).period());
        assertEquals(CityTime.Period.WORKDAY, config.time(0).period());
        assertEquals(CityTime.Period.EVENING, config.time(450).period());
        assertFalse(config.time(700).shopsOpen());
        assertTrue(config.time(1100).shopsOpen());
    }

    @Test
    void fixedTimeAndSandboxRemainDeliberatelyFixed() {
        assertEquals(GameConfig.sandbox().time(0), GameConfig.sandbox().time(3600));
        var fixed = new GameConfig(true, false, 600, 2);
        assertEquals("Day 1 | 02:00 | Night", fixed.time(3600).label());
    }

    @Test
    void populationsCompleteDailyLifeAcrossThreeFullDays() {
        var ground = new CityTest.Ground();
        var config = new GameConfig(true, true, 1200, 6);
        var sim =
                new CitySimulation(
                        config, ground, ground.terrain, null, ProductionCatalog.toolEra());
        var activities = new ArrayList<Set<String>>();
        for (int day = 0; day < 3; day++) activities.add(new HashSet<>());
        var slept = new HashSet<Integer>();
        var ate = new HashSet<Integer>();
        var previous = new HashMap<Integer, CityFrame.Citizen>();
        int movingSamples = 0;
        int previousBuildings = 0;
        double previousWages = 0;
        for (int day = 0; day < 3; day++) {
            for (int second = 0; second < 1200; second++) {
                sim.advance(1);
                var frame = sim.frame();
                for (var citizen : frame.citizens()) {
                    activities.get(day).add(citizen.activity());
                    if (citizen.activity().equals("Sleeping at home")) {
                        slept.add(citizen.id());
                        assertEquals(CityTime.Period.NIGHT, config.time(frame.elapsed()).period());
                    }
                    if (citizen.activity().equals("Eating at shop")) ate.add(citizen.id());
                    if (citizen.activity().equals("Working in mine"))
                        assertEquals(
                                CityTime.Period.WORKDAY, config.time(frame.elapsed()).period());
                    assertTrue(Float.isFinite(citizen.money()) && citizen.money() >= 0);
                    assertTrue(citizen.hunger() >= 0 && citizen.hunger() <= 100);
                    var old = previous.put(citizen.id(), citizen);
                    if (old != null
                            && frame.buildings().size() == previousBuildings
                            && Math.hypot(old.x() - citizen.x(), old.z() - citizen.z()) > .01) {
                        movingSamples++;
                        assertTrue(
                                citizen.phase() > old.phase(),
                                "Movement must advance the gait: " + old + " -> " + citizen);
                    }
                }
                previousBuildings = frame.buildings().size();
            }
            assertTrue(activities.get(day).contains("Working in mine"), "Work on day " + day);
            assertTrue(activities.get(day).contains("Eating at shop"), "Meals on day " + day);
            assertTrue(activities.get(day).contains("Sleeping at home"), "Sleep on day " + day);
            assertTrue(activities.get(day).contains("Relaxing at home"), "Evening on day " + day);
            double wages =
                    sim.frame().economy().firms().stream()
                            .mapToDouble(CityEconomy.Firm::wages)
                            .sum();
            assertTrue(wages > previousWages, "Paid employment continues every day");
            previousWages = wages;
        }
        assertEquals(12, slept.size());
        assertEquals(12, ate.size());
        assertTrue(movingSamples > 100);
        assertEquals(4, config.time(sim.frame().elapsed()).day());
        assertEquals(6, config.hour(sim.frame().elapsed()), .001);
        assertTrue(sim.frame().citizens().stream().allMatch(c -> c.hunger() > 35));
    }

    private CitySimulation settled(double daySeconds) {
        var g = new CityTest.Ground();
        var sim = new CityTest().simulation(g);
        for (int i = 0; i < 160; i++) sim.advance(1);
        var f = sim.frame();
        sim = new CitySimulation(new GameConfig(true, false, daySeconds, 10), g, g.terrain, f);
        for (var c : f.citizens()) {
            var b =
                    BusinessMetrics.from(f).locations().stream()
                            .map(BusinessMetrics.Location::building)
                            .filter(x -> x.id() == c.job())
                            .findFirst()
                            .orElseThrow();
            var p = sim.ecs.get(c.id(), CitySimulation.Position.class);
            p.x = b.x() + 2.5f;
            p.z = b.z() + 2.5f;
            p.y = b.y() + 1.01f;
            sim.ecs.get(c.id(), CitySimulation.Travel.class).target = c.job();
            sim.ecs.get(c.id(), CitySimulation.Needs.class).hunger = 100;
        }
        return sim;
    }

    @Test
    void dayLengthChangesRealDurationWithoutChangingHourlyNeedsAndPay() {
        var slow = settled(1200);
        var fast = settled(600);
        for (int i = 0; i < 10; i++) slow.advance(1);
        for (int i = 0; i < 5; i++) fast.advance(1);
        var a = slow.frame().citizens();
        var b = fast.frame().citizens();
        for (int i = 0; i < a.size(); i++) {
            assertEquals(a.get(i).hunger(), b.get(i).hunger(), .002);
            assertEquals(a.get(i).money(), b.get(i).money(), .002);
        }
    }

    @Test
    void savingAtNightResumesCalendarAndScheduleWithoutResettingBalances() throws Exception {
        var g = new CityTest.Ground();
        var config = new GameConfig(true, true, 600, 8);
        var sim = new CitySimulation(config, g, g.terrain, null, ProductionCatalog.toolEra());
        for (int i = 0; i < 1000; i++) sim.advance(1);
        assertEquals(3, config.time(sim.frame().elapsed()).day());
        sim.save(temp.resolve("clock.city"));
        var saved = CitySimulation.load(temp.resolve("clock.city"));
        var restored = new CitySimulation(saved.config(), g, g.terrain, saved);
        assertEquals(saved.elapsed(), restored.frame().elapsed());
        assertEquals(saved.economy(), restored.frame().economy());
        for (int i = 0; i < 5; i++) restored.advance(1);
        assertEquals(3, config.time(restored.frame().elapsed()).day());
        assertTrue(
                restored.frame().citizens().stream()
                        .anyMatch(c -> c.activity().equals("Sleeping at home")));
    }

    @Test
    void lifeGesturesAnimateWorkAndMealsWhileSleepKeepsLimbsStill() {
        var idle = PlayerAnimation.pose(0, 0, 1, false);
        for (String activity :
                List.of(
                        "Working in mine",
                        "Building for developer",
                        "Working in shop",
                        "Eating at shop")) {
            var a = CitizenAnimation.pose(idle, activity, 0, 1);
            var b = CitizenAnimation.pose(idle, activity, .5, 1);
            assertNotEquals(a.rightArm(), b.rightArm());
            assertEquals(0, a.leftLeg());
            assertEquals(0, a.rightLeg());
        }
        assertEquals(
                CitizenAnimation.pose(idle, "Sleeping at home", 0, 1),
                CitizenAnimation.pose(idle, "Sleeping at home", 20, 1));
        assertEquals(idle, CitizenAnimation.pose(idle, "Relaxing at home", 0, 1));
    }
}
