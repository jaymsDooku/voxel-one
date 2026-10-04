package dev.jayms;

import static org.junit.jupiter.api.Assertions.*;

import dev.jayms.net.*;
import dev.jayms.net.city.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.file.*;
import java.util.*;

class BusinessCatalogTest {
    @TempDir Path temp;

    private ProductionCatalog excavation() throws IOException {
        Path file = temp.resolve("production.properties");
        Files.writeString(
                file,
                Files.readString(Path.of("config/city-production.properties"))
                        +
"""

business.types=15
business.15.name=Excavator
business.15.harvest=2,4
business.15.rate=4
business.15.capacity=2
companies=earth,sand
company.earth.name=Earth Movers
company.earth.type=15
company.earth.cash=900
company.sand.name=Sand Crew
company.sand.type=15
company.sand.cash=1200
""");
        return ProductionCatalog.load(file);
    }

    @Test
    void seedsMultipleCompaniesAndKeepsSavedBalances() throws Exception {
        var catalog = excavation();
        var e = new CityEconomy(new Ecs(), null, catalog);
        var firms = e.companies().stream().filter(c -> c.kind == 15).toList();
        assertEquals(2, firms.size());
        assertEquals(900, firms.get(0).cash);
        firms.get(0).cash = 123;
        e.ensureIndustries();
        assertEquals(2, e.companies().stream().filter(c -> c.kind == 15).count());
        var bytes = new ByteArrayOutputStream();
        e.state().write(new DataOutputStream(bytes));
        var restored =
                new CityEconomy(
                        new Ecs(),
                        CityEconomy.State.read(
                                new DataInputStream(
                                        new ByteArrayInputStream(bytes.toByteArray()))));
        restored.ensureIndustries();
        assertEquals(
                123,
                restored.companies().stream()
                        .filter(c -> c.name.equals("Earth Movers"))
                        .findFirst()
                        .orElseThrow()
                        .cash);
        assertEquals(catalog, restored.resources.catalog);
        assertEquals("Excavator", restored.resources.catalog.businesses().sector(15));
    }

    @Test
    void excavatesLiveDirtAndSandRespectsCapacityAndProtection() throws Exception {
        var catalog = excavation();
        var g = new CityTest.Ground();
        var e = new CityEconomy(new Ecs(), null, catalog);
        var firm = e.companies().stream().filter(c -> c.kind == 15).findFirst().orElseThrow();
        var h = new CityHarvesting(g, g.terrain, (x, z) -> false);
        for (int i = 0; i < 150; i++) h.work(e, firm, .25);
        for (int material : List.of(Blocks.DIRT, Blocks.SAND)) {
            assertEquals(2 * CityMaterials.UNIT, e.resources.available(0, firm.id, material));
            assertTrue(
                    g.edits.values().stream()
                            .anyMatch(
                                    edit ->
                                            g.terrain.block(edit.x(), edit.y(), edit.z())
                                                    == material));
        }
        assertEquals(4, e.resources.production(firm.id).harvested());
        assertEquals(4, g.edits.size());
        assertEquals("Storage full", e.resources.production(firm.id).status());
        var protectedEconomy = new CityEconomy(new Ecs(), null, catalog);
        var protectedFirm =
                protectedEconomy.companies().stream()
                        .filter(c -> c.kind == 15)
                        .findFirst()
                        .orElseThrow();
        var protectedGround = new CityTest.Ground();
        var protectedHarvest =
                new CityHarvesting(protectedGround, protectedGround.terrain, (x, z) -> true);
        protectedHarvest.work(protectedEconomy, protectedFirm, 1);
        assertEquals(0, protectedEconomy.resources.production(protectedFirm.id).harvested());
        assertTrue(protectedGround.edits.isEmpty());
    }

    @Test
    void rejectsBadReferencesRatesMaterialsAndDuplicateIds() {
        for (String text :
                List.of(
                        "business.types=15,15\nbusiness.15.name=Excavator",
                        "business.types=15\nbusiness.15.name=Excavator\nbusiness.15.rate=NaN",
                        "business.types=15\nbusiness.15.name=Excavator\nbusiness.15.harvest=999",
                        "companies=new\ncompany.new.name=Bad\ncompany.new.type=63",
                        "companies=new\n"
                                + "company.new.name=Bad\n"
                                + "company.new.type=2\n"
                                + "company.new.cash=-1")) {
            var p = new Properties();
            assertDoesNotThrow(() -> p.load(new StringReader(text)));
            assertThrows(IllegalArgumentException.class, () -> BusinessCatalog.load(p, true));
        }
    }

    @Test
    void supportsReplacingCompaniesAndReadsLegacyCatalog() throws Exception {
        var p = new Properties();
        p.load(
                new StringReader(
                        "companies.inherit-defaults=false\n"
                                + "companies=mine\n"
                                + "company.mine.name=Only Mine\n"
                                + "company.mine.type=2"));
        assertEquals(1, BusinessCatalog.load(p, false).companies().size());
        var old = ProductionCatalog.toolEra();
        var bytes = new ByteArrayOutputStream();
        old.write(new DataOutputStream(bytes), 6);
        assertEquals(
                old,
                ProductionCatalog.read(
                        new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())), 6));
    }

    @Test
    void framesAndCitySavesPreserveConfiguration() throws Exception {
        var g = new CityTest.Ground();
        var sim = new CitySimulation(GameConfig.cityGame(), g, g.terrain, null, excavation());
        var bytes = new ByteArrayOutputStream();
        sim.frame().write(new DataOutputStream(bytes));
        var frame =
                CityFrame.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        assertEquals(
                sim.frame().economy().resources().catalog(), frame.economy().resources().catalog());
        Path save = temp.resolve("test.city");
        sim.save(save);
        assertEquals(
                frame.economy().resources().catalog(),
                CitySimulation.load(save).economy().resources().catalog());
    }

    @Test
    void configuredExampleReceivesPaidWorkersInTheCity() throws Exception {
        var catalog = ProductionCatalog.load(Path.of("config/city-excavation.properties"));
        var g = new CityTest.Ground();
        var sim =
                new CitySimulation(
                        new GameConfig(true, false, 1200, 10), g, g.terrain, null, catalog);
        for (int i = 0; i < 180; i++) sim.advance(1);
        var firms = sim.economy.companies().stream().filter(c -> c.kind == 15).toList();
        assertEquals(2, firms.size());
        for (var firm : firms) {
            assertTrue(firm.wages > 0, firm.name);
            assertTrue(sim.economy.resources.production(firm.id).harvested() > 0, firm.name);
        }
    }

    @Test
    void newManufacturingTypesUseExistingRecipeConfiguration() throws Exception {
        var old = ProductionCatalog.toolEra();
        var types = new ArrayList<>(old.businesses().types());
        types.add(new BusinessCatalog.Type(16, "New workshop", List.of(), 4, 16));
        var catalog =
                new ProductionCatalog(
                        old.products(),
                        List.of(
                                new ProductionCatalog.Recipe(
                                        "new-bricks",
                                        16,
                                        Blocks.BRICKS,
                                        1,
                                        Map.of(Blocks.STONE, 1),
                                        4,
                                        16,
                                        false)),
                        List.of(),
                        new BusinessCatalog(
                                types,
                                List.of(
                                        new BusinessCatalog.Company(
                                                "new", "New Workshop", 16, 1500))));
        var e = new CityEconomy(new Ecs(), null, catalog);
        var firm = e.companies().get(0);
        e.resources.add(0, firm.id, Blocks.STONE, CityMaterials.UNIT);
        var g = new CityTest.Ground();
        new CityHarvesting(g, g.terrain, (x, z) -> false).work(e, firm, .25);
        assertEquals(CityMaterials.UNIT, e.resources.available(0, firm.id, Blocks.BRICKS));
        assertEquals(0, e.resources.available(0, firm.id, Blocks.STONE));
    }

    @Test
    void emptyConfiguredRosterPreservesSavedMunicipalAccounts() throws Exception {
        var old = ProductionCatalog.toolEra();
        var catalog =
                new ProductionCatalog(
                        old.products(),
                        old.recipes(),
                        old.equipment(),
                        new BusinessCatalog(old.businesses().types(), List.of()));
        var economy = new CityEconomy(new Ecs(), null, catalog);
        economy.budget = 4321;
        var restored = new CityEconomy(new Ecs(), economy.state());
        assertEquals(4321, restored.budget);
        assertTrue(restored.companies().isEmpty());
    }
    @Test
    void legacyCivicBuildingSavesRemainReadableForEveryType() throws Exception {
        for (int type = 4; type <= 18; type++) {
            var building = new CityFrame.Building(40, 0, type, 60, 32, 52, 1, 0);
            var frame = new CityFrame(GameConfig.cityGame(), 0, List.of(), List.of(),
                    List.of(building), List.of(), List.of());
            Path save = temp.resolve("legacy-civic-" + type + ".city");
            try (var out = new DataOutputStream(Files.newOutputStream(save))) {
                out.writeInt(0x43495436);
                frame.write(out, 6);
            }
            assertEquals(List.of(building), CitySimulation.load(save).buildings());
        }
    }

    @Test
    void terrainIntegrationPreservesCitySevenCatalogAndEveryCivicBuilding() throws Exception {
        var ground = new CityTest.Ground();
        var catalog = excavation();
        var simulation = new CitySimulation(GameConfig.cityGame(), ground,
                new Terrain(42, Terrain.CURRENT_VERSION), null, catalog);
        var original = simulation.frame();
        var buildings = new ArrayList<CityFrame.Building>();
        for (int type = 4; type <= 18; type++)
            buildings.add(new CityFrame.Building(100 + type, 0, type, type * 8, 32, 52, 1, 0));
        var frame = new CityFrame(original.config(), original.elapsed(), original.roads(),
                original.zones(), buildings, original.citizens(), original.horses(),
                original.economy(), CityAddresses.migrate(original.roads(), buildings), original.agriculture());
        Path save = temp.resolve("city-seven.city");
        try (var out = new DataOutputStream(Files.newOutputStream(save))) {
            out.writeInt(0x43495437);
            frame.write(out, 7);
        }
        var loaded = CitySimulation.load(save);
        assertEquals(frame, loaded);
        assertEquals(catalog, loaded.economy().resources().catalog());
        var bytes = new ByteArrayOutputStream();
        loaded.write(new DataOutputStream(bytes));
        assertEquals(loaded, CityFrame.read(new DataInputStream(
                new ByteArrayInputStream(bytes.toByteArray()))));
        var restored = new CitySimulation(loaded.config(), ground,
                new Terrain(42, Terrain.CURRENT_VERSION), loaded, ProductionCatalog.toolEra());
        restored.save(save);
        try (var in = new DataInputStream(Files.newInputStream(save))) {
            assertEquals(0x43495437, in.readInt());
        }
        assertEquals(buildings, CitySimulation.load(save).buildings());
        assertEquals(catalog, CitySimulation.load(save).economy().resources().catalog());
    }

    @Test
    void terrainIntegrationMigratesCitySixCivicBuildingsToSeven() throws Exception {
        var ground = new CityTest.Ground();
        var building = new CityFrame.Building(40, 0, 18, 60, 32, 52, 1, 0);
        var frame = new CityFrame(GameConfig.cityGame(), 0, List.of(), List.of(),
                List.of(building), List.of(), List.of());
        Path save = temp.resolve("city-six.city");
        try (var out = new DataOutputStream(Files.newOutputStream(save))) {
            out.writeInt(0x43495436);
            frame.write(out, 6);
        }
        var loaded = CitySimulation.load(save);
        var restored = new CitySimulation(loaded.config(), ground,
                new Terrain(42, Terrain.LEGACY_VERSION), loaded);
        restored.save(save);
        try (var in = new DataInputStream(Files.newInputStream(save))) {
            assertEquals(0x43495437, in.readInt());
        }
        assertEquals(restored.frame(), CitySimulation.load(save));
        assertEquals(List.of(building), CitySimulation.load(save).buildings());
    }

}
