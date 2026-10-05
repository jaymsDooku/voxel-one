package dev.jayms;

import static org.junit.jupiter.api.Assertions.*;
import dev.jayms.net.*;
import dev.jayms.net.city.*;
import dev.jayms.ui.*;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.util.*;

public class CitizenLifeTest {
    public CitySimulation school(int kind, double age, CitizenLife.Education education, double study) {
        var terrain = new Terrain(Terrain.DEFAULT_SEED);
        var ground = new SpecialBuildingsTest.Ground(terrain.column(8, 24).height());
        var config = new GameConfig(true, true, 60, 8);
        var school = new CityFrame.Building(10, 0, SpecialBuildings.type(kind, 1), 50, ground.grade + 1, 52, 8, 0);
        var citizen = new CityFrame.Citizen(1, "Student", 0, 52.5f, ground.grade + 1.01f, 54.5f,
                0, 0, 100, 20, 0, 0, 0, "Ready", age, CitizenLife.Gender.FEMALE, education,
                study, 0, 0, 0, 0, -10);
        return new CitySimulation(config, ground, terrain, new CityFrame(config, 0,
                List.of(new CityFrame.Road(52, 50, ground.grade), new CityFrame.Road(52, 51, ground.grade)),
                List.of(), List.of(school), List.of(citizen), List.of()));
    }

    @Test public void physicalAttendanceCompletesAllFourSchoolPaths() {
        int[] kinds = {1, 2, 3, 5};
        double[] ages = {11, 17, 21, 21};
        double[] study = {5.9999, 4.9999, 2.9999, 2.9999};
        var previous = new CitizenLife.Education[]{CitizenLife.Education.NONE, CitizenLife.Education.PRIMARY,
                CitizenLife.Education.SECONDARY, CitizenLife.Education.SECONDARY};
        var next = new CitizenLife.Education[]{CitizenLife.Education.PRIMARY, CitizenLife.Education.SECONDARY,
                CitizenLife.Education.UNIVERSITY, CitizenLife.Education.TECHNICAL};
        for (int i = 0; i < 4; i++) {
            var city = school(kinds[i], ages[i], previous[i], study[i]);
            city.advance(.11);
            assertEquals(next[i], city.life(1).education);
            assertEquals(0, city.life(1).school);
            assertEquals(0, city.life(1).study);
            assertTrue(city.life(1).age > ages[i]);
            assertEquals(next[i] == CitizenLife.Education.UNIVERSITY, city.economy.capital.graduates.contains(1));
        }
    }

    @Test public void agePrerequisitesCapacityAndNoRoutePreventFreeEducation() {
        for (double age : new double[]{4.99, 12, 18, 22}) {
            var city = school(1, age, CitizenLife.Education.NONE, 0);
            city.advance(.11);
            assertEquals(0, city.life(1).school);
            assertEquals(0, city.life(1).study);
        }
        var missing = school(2, 12, CitizenLife.Education.NONE, 0);
        missing.advance(.11); assertEquals(0, missing.life(1).school);
        var edge = school(3, 18, CitizenLife.Education.SECONDARY, 0);
        edge.ecs.get(1, CitySimulation.Position.class).x = 100;
        edge.advance(.11);
        assertEquals(10, edge.life(1).school);
        assertEquals(0, edge.life(1).study);
        assertTrue(edge.ecs.get(1, CitySimulation.Position.class).x > 90);
        var full = school(1, 5, CitizenLife.Education.NONE, 0);
        for (int n = 0; n < 8; n++) {
            int id = full.ecs.create();
            full.ecs.put(id, CitySimulation.Household.class, full.ecs.get(1, CitySimulation.Household.class));
            full.ecs.put(id, CitySimulation.Position.class, full.ecs.get(1, CitySimulation.Position.class));
            full.ecs.put(id, CitySimulation.Needs.class, full.ecs.get(1, CitySimulation.Needs.class));
            full.ecs.put(id, CitySimulation.Travel.class, new CitySimulation.Travel());
            full.ecs.put(id, CitizenLife.class, new CitizenLife(5, CitizenLife.Gender.MALE, CitizenLife.Education.NONE));
        }
        full.advance(.11);
        assertEquals(8, full.frame().citizens().stream().filter(c -> c.school() == 10).count());
    }

    public CitySimulation family(double age) {
        var terrain = new Terrain(Terrain.DEFAULT_SEED);
        var ground = new SpecialBuildingsTest.Ground(terrain.column(8,24).height());
        var config = new GameConfig(true, true, 60, 8);
        var people = new ArrayList<CityFrame.Citizen>();
        for (int i = 1; i <= 2; i++) people.add(new CityFrame.Citizen(i, "Parent " + i, 0,
                52.5f, ground.grade + 1.01f, 54.5f, 0, 0, 100, 100, 10, 0, 0, "Ready", age,
                i == 1 ? CitizenLife.Gender.FEMALE : CitizenLife.Gender.MALE,
                CitizenLife.Education.NONE, 0, 0, 0, 0, 0, -10));
        var home = new CityFrame.Building(10, 1, 0, 50, ground.grade + 1, 52, 8, 0);
        return new CitySimulation(config, ground, terrain, new CityFrame(config, 0,
                List.of(new CityFrame.Road(52,50,ground.grade)), List.of(), List.of(home), people, List.of()));
    }

    @Test public void adultsMarryHaveDependentChildrenAndCannotRepeatBirthEachTick() throws Exception {
        var city = family(19);
        city.advance(.11);
        assertEquals(2, city.life(1).spouse); assertEquals(1, city.life(2).spouse);
        assertEquals(3, city.frame().citizens().size());
        var child = city.frame().citizens().get(2);
        assertEquals(0, child.age()); assertEquals(1, child.mother()); assertEquals(2, child.father());
        assertEquals(10, child.home()); assertEquals(0, child.job());
        city.advance(.11);
        assertEquals(3, city.frame().citizens().size());
        assertTrue(city.life(child.id()).age > 0);
        assertEquals(10, city.ecs.get(child.id(), CitySimulation.Household.class).home);
        var minor = family(17.9); minor.advance(.11);
        assertEquals(2, minor.frame().citizens().size()); assertEquals(0, minor.life(1).spouse);
        var bytes = new ByteArrayOutputStream(); city.frame().write(new DataOutputStream(bytes));
        var loaded = CityFrame.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        assertEquals(city.frame(), loaded);
        var terrain = new Terrain(Terrain.DEFAULT_SEED);
        var restored = new CitySimulation(loaded.config(), new SpecialBuildingsTest.Ground(terrain.column(8,24).height()), terrain, loaded);
        restored.advance(.11); assertEquals(3, restored.frame().citizens().size());
    }

    @Test public void legacySnapshotsMigrateWithoutChangingTheirLayout() throws Exception {
        var city = family(19);
        var bytes = new ByteArrayOutputStream(); city.frame().write(new DataOutputStream(bytes), 8);
        var loaded = CityFrame.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())), 8);
        assertEquals(24, loaded.citizens().get(0).age());
        assertEquals(CitizenLife.Education.NONE, loaded.citizens().get(0).education());
    }

    @Test public void technicalCollegePermitUsesNewIdsAndInspectorShowsStudents() {
        assertEquals(19, SpecialBuildings.EXCHANGE);
        for (int level = 1; level <= 3; level++) {
            int type = SpecialBuildings.type(5, level);
            assertEquals(19 + level, type); assertEquals(5, SpecialBuildings.kind(type));
            assertEquals(level, SpecialBuildings.level(type));
            assertTrue(SpecialBuildings.name(type).startsWith("Technical college"));
        }
        var city = school(5, 18, CitizenLife.Education.SECONDARY, 0);
        city.advance(.11);
        var info = new BuildingInfo(); info.show(10, 0);
        assertTrue(info.lines(city.frame()).contains("Students: 1 / 8"));
        assertTrue(info.lines(city.frame()).stream().anyMatch(s -> s.contains("18-21")));
    }
    @Test public void educationGatesActualAssignmentAndEveryCareerType() {
        var ground = new CityTest.Ground();
        var seed = new CityTest().simulation(ground);
        var shop = seed.economy.companies().stream().filter(c -> c.kind == CityEconomy.SHOP).findFirst().orElseThrow();
        var factory = seed.economy.companies().stream().filter(c -> seed.economy.resources.catalog.recipes(c.kind).stream()
                .anyMatch(ProductionCatalog.Recipe::requiresFactory)).findFirst().orElseThrow();
        seed.economy.properties.add(new CityEconomy.Property(600, 0, shop.id, shop.id, 200, 4));
        seed.economy.properties.add(new CityEconomy.Property(601, 0, factory.id, factory.id, 200, 4));
        var f = seed.frame();
        var buildings = List.of(new CityFrame.Building(600, 1, 1, 12, 27, 28, 2, 0),
                new CityFrame.Building(601, 2, 2, 24, 27, 28, 4, 0),
                new CityFrame.Building(602, 0, SpecialBuildings.EXCHANGE, 50, 27, 52, 4, 0));
        var city = new CitySimulation(f.config(), ground, ground.terrain, new CityFrame(f.config(), f.elapsed(),
                f.roads(), f.zones(), buildings, f.citizens(), f.horses(), f.economy()));
        for (var education : CitizenLife.Education.values()) {
            city.life(1).education = education;
            assertTrue(city.eligible(CityMaterials.YARD + CityEconomy.MINE, 1));
            assertEquals(education.ordinal() >= CitizenLife.Education.SECONDARY.ordinal(), city.eligible(600, 1));
            assertEquals(education == CitizenLife.Education.TECHNICAL, city.eligible(601, 1));
            assertEquals(education == CitizenLife.Education.UNIVERSITY, city.eligible(602, 1));
        }
        city.life(1).education = CitizenLife.Education.NONE;
        city.ecs.get(1, CitySimulation.Household.class).job = 600;
        city.life(2).age = 10; city.ecs.get(2, CitySimulation.Household.class).job = 601;
        city.advance(.11);
        assertNotEquals(600, city.ecs.get(1, CitySimulation.Household.class).job);
        assertEquals(0, city.ecs.get(2, CitySimulation.Household.class).job);
    }

    @Test public void familyEdgesRequireSpaceAndRejectCloseRelatives() {
        var siblings = family(19);
        // Two children of a third resident must never be paired with each other or that parent.
        int parent = siblings.ecs.create();
        siblings.ecs.put(parent, CitySimulation.Household.class, siblings.ecs.get(1, CitySimulation.Household.class));
        siblings.ecs.put(parent, CitySimulation.Position.class, siblings.ecs.get(1, CitySimulation.Position.class));
        siblings.ecs.put(parent, CitySimulation.Needs.class, siblings.ecs.get(1, CitySimulation.Needs.class));
        siblings.ecs.put(parent, CitySimulation.Travel.class, new CitySimulation.Travel());
        siblings.ecs.put(parent, CitizenLife.class, new CitizenLife(40, CitizenLife.Gender.FEMALE, CitizenLife.Education.NONE));
        siblings.life(1).mother = parent; siblings.life(2).mother = parent;
        siblings.advance(.11);
        assertEquals(0, siblings.life(1).spouse); assertEquals(0, siblings.life(2).spouse);
        assertEquals(3, siblings.frame().citizens().size());
        var city = family(19); city.advance(.11);
        city.life(1).age += 2; city.life(2).age += 2;
        city.advance(.11);
        assertEquals(4, city.frame().citizens().size());
        var homeless = family(19);
        homeless.ecs.get(1, CitySimulation.Household.class).home = 0;
        homeless.ecs.get(2, CitySimulation.Household.class).home = 0;
        homeless.ecs.get(1, CitySimulation.Needs.class).money = 0;
        homeless.ecs.get(2, CitySimulation.Needs.class).money = 0;
        homeless.advance(.11); assertEquals(2, homeless.frame().citizens().size());
    }

    @Test public void agingUsesCityDaysAndInvalidFamilyReferencesAreRejected() throws Exception {
        var city = family(19);
        city.advance(1);
        assertEquals(19 + 1.0 / (60 * 12), city.life(1).age, 1e-7);
        city.life(1).spouse = 999;
        var bytes = new ByteArrayOutputStream(); city.frame().write(new DataOutputStream(bytes));
        assertThrows(IOException.class, () -> CityFrame.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
    }
}
