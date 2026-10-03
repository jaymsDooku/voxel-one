package dev.jayms;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;

import dev.jayms.net.city.*;
import dev.jayms.ui.Overlay;

import org.lwjgl.opengl.GL;

import java.awt.image.BufferedImage;
import java.nio.*;
import java.nio.file.*;

import javax.imageio.ImageIO;

/** Offscreen integration probe. Uses live travel state, not a full-window playtest. */
public class RoadTrafficSmoke {
    static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        glfwInitHint(GLFW_PLATFORM, GLFW_PLATFORM_NULL);
        require(glfwInit(), "GLFW init");
        glfwWindowHint(GLFW_CONTEXT_CREATION_API, GLFW_EGL_CONTEXT_API);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        long window = glfwCreateWindow(960, 540, "Traffic integration", 0, 0);
        require(window != 0, "EGL context");
        glfwMakeContextCurrent(window);
        GL.createCapabilities();
        var travel =
                CitySimulation.class.getDeclaredMethod(
                        "travel",
                        int.class,
                        CitySimulation.Position.class,
                        CitySimulation.Household.class,
                        CitySimulation.Travel.class,
                        float.class);
        travel.setAccessible(true);
        for (boolean mounted : new boolean[] {false, true}) {
            var city = new CityTest().simulation(new CityTest.Ground());
            var ids = city.ecs.query(CitySimulation.Position.class, CitySimulation.Household.class);
            for (int id : ids) {
                var p = city.ecs.get(id, CitySimulation.Position.class);
                p.x = 100;
                p.z = 100;
                city.ecs.get(id, CitySimulation.Household.class).horse = 0;
            }
            float lane = mounted ? 25 : 25.75f;
            for (int i = 0; i < 3; i++) {
                int id = ids.get(i);
                var p = city.ecs.get(id, CitySimulation.Position.class);
                p.x = 1.5f - i * (mounted ? 1 : .65f);
                p.z = lane;
                p.y = city.frame().roads().get(0).y() + 1.01f;
                var t = city.ecs.get(id, CitySimulation.Travel.class);
                t.target = 0;
                t.route.clear();
                t.route.add(new RoadTraffic.Waypoint(8.5f, lane));
                if (mounted) {
                    int horse = city.ecs.query(CitySimulation.Mount.class).get(i);
                    city.ecs.get(id, CitySimulation.Household.class).horse = horse;
                    city.ecs.get(horse, CitySimulation.Mount.class).rider = -id;
                }
            }
            for (int i = 1; i < 3; i++) {
                int id = ids.get(i);
                var p = city.ecs.get(id, CitySimulation.Position.class);
                float before = p.x;
                travel.invoke(
                        city,
                        id,
                        p,
                        city.ecs.get(id, CitySimulation.Household.class),
                        city.ecs.get(id, CitySimulation.Travel.class),
                        .1f);
                require(p.x == before, "Follower must wait");
                require(
                        city.ecs
                                .get(id, CitySimulation.Travel.class)
                                .activity
                                .equals("Waiting for traffic"),
                        "Waiting state");
            }
            draw(
                    city,
                    ids,
                    mounted,
                    "Blocked leader: followers wait",
                    Path.of(
                            args[0],
                            "road-traffic-fresh-" + (mounted ? "horses" : "pedestrians") + ".png"));
            for (int i = 0; i < 3; i++) {
                int id = ids.get(i);
                var p = city.ecs.get(id, CitySimulation.Position.class);
                float before = p.x;
                travel.invoke(
                        city,
                        id,
                        p,
                        city.ecs.get(id, CitySimulation.Household.class),
                        city.ecs.get(id, CitySimulation.Travel.class),
                        .1f);
                require(p.x > before, "Queue resumes after leader moves");
            }
            draw(
                    city,
                    ids,
                    mounted,
                    "Leader moves: queue resumes",
                    Path.of(
                            args[0],
                            "road-traffic-fresh-"
                                    + (mounted ? "horses" : "pedestrians")
                                    + "-resumed.png"));
        }
        glfwDestroyWindow(window);
        glfwTerminate();
        System.out.println(
                "PASS: live pedestrian and horse queues wait then resume; four fresh EGL captures");
    }

    static void draw(
            CitySimulation city,
            java.util.List<Integer> ids,
            boolean mounted,
            String state,
            Path path)
            throws Exception {
        glViewport(0, 0, 960, 540);
        glClearColor(.04f, .08f, .09f, 1);
        glClear(GL_COLOR_BUFFER_BIT);
        try (var o = new Overlay()) {
            o.begin(960, 540);
            o.text("LIVE TRAFFIC INTEGRATION / Mesa EGL", 40, 30, 2);
            o.text(
                    (mounted ? "Horse carriageway" : "Pedestrian pavement") + " - " + state,
                    40,
                    70,
                    1.5f);
            o.rectangle(30, 150, 900, 240, .36f, .25f, .14f, 1);
            o.rectangle(30, 145, 900, 22, .55f, .55f, .55f, 1);
            o.rectangle(30, 390, 900, 22, .55f, .55f, .55f, 1);
            o.text("< reverse pavement", 540, 130, 1.2f);
            o.text("< reverse carriageway", 540, 210, 1.2f);
            o.text("forward carriageway >", 540, 330, 1.2f);
            o.text("forward pavement >", 540, 430, 1.2f);
            for (int i = 0; i < 3; i++) {
                int id = ids.get(i);
                var p = city.ecs.get(id, CitySimulation.Position.class);
                float x = 150 + p.x * 90, y = mounted ? 320 : 394;
                o.rectangle(
                        x - 10, y - 10, 20, 20, i == 0 ? .2f : .95f, i == 0 ? .8f : .5f, .2f, 1);
                o.text(
                        String.format(java.util.Locale.ROOT, "%d: x=%.2f", i + 1, p.x),
                        60 + i * 290,
                        465,
                        1.3f);
            }
            o.text(
                    "Production CitySimulation.travel; synthetic world. Full-window input"
                        + " unverified.",
                    40,
                    505,
                    1.1f);
            o.end();
        }
        var data = ByteBuffer.allocateDirect(960 * 540 * 4);
        glReadPixels(0, 0, 960, 540, GL_RGBA, GL_UNSIGNED_BYTE, data);
        var img = new BufferedImage(960, 540, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 540; y++)
            for (int x = 0; x < 960; x++) {
                int i = (y * 960 + x) * 4;
                img.setRGB(
                        x,
                        539 - y,
                        (data.get(i) & 255) << 16
                                | (data.get(i + 1) & 255) << 8
                                | (data.get(i + 2) & 255));
            }
        require(glGetError() == GL_NO_ERROR, "OpenGL capture");
        ImageIO.write(img, "png", path.toFile());
    }
}
