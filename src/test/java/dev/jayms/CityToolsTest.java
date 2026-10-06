package dev.jayms;

import static org.junit.jupiter.api.Assertions.*;

import dev.jayms.net.city.*;
import dev.jayms.ui.CityTools;

import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

import java.util.*;

class CityToolsTest {
    final Matrix4f projection = new Matrix4f().ortho(-60, 60, -45, 45, .1f, 400),
            view = new Matrix4f().lookAt(100, 100, 100, 18, 24, 28, 0, 1, 0);
    final CityFrame frame =
            new CityFrame(
                    GameConfig.cityGame(),
                    0,
                    List.of(new CityFrame.Road(0, 0, 23)),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of());

    void click(CityTools tools, float x, float z, List<CityCommand> result) {
        var p = new Matrix4f(projection).mul(view).transform(new Vector4f(x, 24.03f, z, 1));
        tools.click(
                (p.x / p.w * .5f + .5f) * 1280,
                (.5f - p.y / p.w * .5f) * 720,
                1280,
                720,
                projection,
                view,
                frame,
                result::add);
    }

    @Test
    void guideHasEightCardinalTargetsAtThirtyAndSixtyBlocks() {
        var origin = new Polygon.Point(10, 20);
        var targets = dev.jayms.ui.BuildingGuide.targets(origin);
        assertEquals(8, targets.size());
        assertTrue(targets.contains(new Polygon.Point(40, 20)));
        assertTrue(targets.contains(new Polygon.Point(10, -40)));
        assertDoesNotThrow(() -> dev.jayms.ui.BuildingGuide.targets(new Polygon.Point(260, 270)));
    }

    @Test
    void guideSnapsRoadEndpointAndClearsAfterSubmission() {
        var tools = new CityTools();
        tools.tool = 4;
        var result = new ArrayList<CityCommand>();
        click(tools, 0, 24, result);
        click(tools, 30.5f, 24, result);
        assertEquals(new Polygon.Point(30, 24), result.get(0).points().get(1));
        click(tools, 30.5f, 24, result);
        click(tools, 44, 24, result);
        assertEquals(new Polygon.Point(30.5f, 24), result.get(1).points().get(0));
    }

    @Test
    void zoneRoadSnapUsesBoundaryOnlyWhenPointerIntersectsRoad() {
        var roads = List.of(new CityFrame.Road(12, 24, 23));
        var addresses = new CityAddresses.State(List.of(new CityAddresses.Street(1, "Test",
                List.of(new Polygon.Point(0, 24), new Polygon.Point(40, 24)))), List.of());
        var city = new CityFrame(frame.config(), 0, roads, List.of(), List.of(), List.of(),
                List.of(), frame.economy(), addresses);
        assertEquals(new Polygon.Point(12, 24.5f), dev.jayms.ui.BuildingGuide.snapRoad(
                new Polygon.Point(12.5f, 24.5f), city));
        assertEquals(new Polygon.Point(12.5f, 28), dev.jayms.ui.BuildingGuide.snapRoad(
                new Polygon.Point(12.5f, 28), city));
    }

    @Test
    void roadsideClicksProduceZonesAcceptedBySimulationOnBothSides() {
        for (float pointerZ : new float[] {36, 35.5f, 33.5f, 33}) {
            var roads = new ArrayList<CityFrame.Road>();
            for (int x = -60; x <= 60; x++) for (int z = 33; z <= 35; z++)
                roads.add(new CityFrame.Road(x, z, 23));
            var city = new CityFrame(frame.config(), 0, roads, List.of(), List.of(), List.of(), List.of());
            var tools = new CityTools();
            tools.tool = 0;
            var commands = new ArrayList<CityCommand>();
            float boundary = pointerZ > 34.5f ? 36 : 33;
            float outside = boundary == 36 ? 55 : 14;
            for (var point : List.of(new Polygon.Point(0, pointerZ), new Polygon.Point(20, pointerZ),
                    new Polygon.Point(20, outside), new Polygon.Point(0, outside))) {
                var p = new Matrix4f(projection).mul(view).transform(new Vector4f(point.x(), 24.03f, point.z(), 1));
                float x = (p.x / p.w * .5f + .5f) * 1280;
                float y = (.5f - p.y / p.w * .5f) * 720;
                var preview = tools.cursorPoint(x, y, 1280, 720, projection, view, city);
                tools.click(x, y, 1280, 720, projection, view, city, commands::add);
                if (point.z() == pointerZ) assertEquals(boundary, preview.z());
            }
            tools.key(257, commands::add);
            assertEquals(1, commands.size());
            assertEquals(boundary, commands.get(0).points().get(0).z());
            assertEquals(boundary, commands.get(0).points().get(1).z());
            var polygon = new Polygon(commands.get(0).points());
            assertTrue(roads.stream().noneMatch(r -> polygon.contains(r.x() + .5f, r.z() + .5f)));
            var ground = new CityTest.Ground();
            var simulation = new CitySimulation(city.config(), ground, ground.terrain, city);
            int before = simulation.frame().zones().size();
            assertTrue(simulation.command(commands.get(0), 1,
                    new dev.jayms.net.Protocol.Pose(1, 8, 40, 24, 0, 0)).contains("created"));
            assertEquals(before + 1, simulation.frame().zones().size());
        }
    }

    @Test
    void zoneCornersRemainInWorldCoordinatesWhenRotatingMidPolygon() {
        var overview = new IsometricCamera();
        overview.cityMode();
        overview.focus(18, 28, 24);
        overview.zoom(-20);
        overview.zoom(-8);
        var tools = new CityTools();
        tools.tool = 0;
        var result = new ArrayList<CityCommand>();
        var corners =
                List.of(
                        new Polygon.Point(14, 26),
                        new Polygon.Point(26, 26),
                        new Polygon.Point(26, 38),
                        new Polygon.Point(14, 38));
        for (var corner : corners) {
            var projection = overview.projection(new World(), 1280, 720);
            var view = overview.camera().createViewMatrix();
            var p =
                    new Matrix4f(projection)
                            .mul(view)
                            .transform(new Vector4f(corner.x(), 24.03f, corner.z(), 1));
            tools.click(
                    (p.x * .5f + .5f) * 1280,
                    (.5f - p.y * .5f) * 720,
                    1280,
                    720,
                    projection,
                    view,
                    frame,
                    result::add);
            overview.rotate(1);
        }
        assertTrue(result.isEmpty());
        tools.key(257, result::add);
        assertEquals(1, result.size());
        assertEquals(CityCommand.ZONE, result.get(0).kind());
        assertEquals(corners, result.get(0).points());
    }

    @Test
    void isometricPickingSendsRoadEndpointsInWorldCoordinates() {
        var tools = new CityTools();
        tools.tool = 4;
        var result = new ArrayList<CityCommand>();
        click(tools, 14, 26, result);
        click(tools, 28.5f, 26, result);
        assertEquals(1, result.size());
        assertEquals(CityCommand.ROAD, result.get(0).kind());
        assertEquals(
                List.of(new Polygon.Point(14, 26), new Polygon.Point(28.5f, 26)),
                result.get(0).points());
    }

    @Test
    void specialMenuControlsDoNotPlaceBuildings() {
        var tools = new CityTools(); tools.tool = 6;
        var result = new ArrayList<CityCommand>();
        tools.click(40, 140 + 3 * 28 + 10, 1280, 720, projection, view, frame, result::add);
        tools.click(40, 140 + 6 * 28 + 10, 1280, 720, projection, view, frame, result::add);
        tools.click(40, 140 + 7 * 28 + 10, 1280, 720, projection, view, frame, result::add);
        assertEquals(3, tools.specialKind);
        assertEquals(2, tools.specialLevel);
        assertEquals(1, tools.specialOwner);
        assertTrue(result.isEmpty());
        assertTrue(tools.key(256, result::add));
        assertEquals(-1, tools.tool);
    }

    @Test
    void specialBuildingClickSubmitsSelectedTypeLevelAndPublicOwner() {
        var tools = new CityTools(); tools.tool = 6; tools.specialKind = 4; tools.specialLevel = 3;
        var result = new ArrayList<CityCommand>();
        click(tools, 28.5f, 26, result);
        assertEquals(1, result.size());
        assertEquals(CityCommand.SPECIAL, result.get(0).kind());
        assertEquals(SpecialBuildings.type(4, 3), result.get(0).value());
        assertEquals(0, result.get(0).ownerKind());
        assertEquals(0, result.get(0).ownerId());
        assertEquals(List.of(new Polygon.Point(28, 26)), result.get(0).points());
    }

    @Test
    void polygonConfirmationUndoAndCancel() {
        var tools = new CityTools();
        tools.tool = 0;
        var result = new ArrayList<CityCommand>();
        click(tools, 14, 26, result);
        click(tools, 26, 26, result);
        click(tools, 26, 38, result);
        click(tools, 14, 38, result);
        assertTrue(result.isEmpty());
        tools.key(259, result::add);
        tools.key(257, result::add);
        assertEquals(1, result.size());
        assertEquals(3, result.get(0).points().size());
        tools.tool = 1;
        click(tools, 14, 26, result);
        assertTrue(tools.key(256, result::add));
        assertEquals(-1, tools.tool);
    }
}
