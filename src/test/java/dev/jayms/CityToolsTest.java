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
