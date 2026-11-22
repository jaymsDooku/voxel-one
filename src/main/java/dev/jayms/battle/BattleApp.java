package dev.jayms.battle;

import dev.jayms.*;
import dev.jayms.net.Blocks;
import dev.jayms.recording.ScreenRecorder;
import dev.jayms.ui.Overlay;
import java.nio.file.Path;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;

/** Separate desktop arena, using the engine mesh, shader, HUD, physics and F10 recorder. */
public final class BattleApp {
    @FunctionalInterface public interface Observer { void frame(BattleApp app) throws Exception; }
    public final BattleArena arena = new BattleArena();
    public long window, frames, inputSequence, renderedInputSequence;
    public int width = 960, height = 640;
    public double frameSeconds, simulationSeconds;
    public String renderer;
    private float yaw = (float)Math.PI, pitch = .10f;
    private boolean captured = true, firstMouse = true;
    private double mouseX, mouseY;
    public Vector3f direction() {
        return new Vector3f((float)Math.sin(yaw) * (float)Math.cos(pitch), -(float)Math.sin(pitch),
                (float)Math.cos(yaw) * (float)Math.cos(pitch));
    }
    public Vector3f eye() {
        Vector3f p = arena.player.position();
        Vector3f eye = new Vector3f(p.x, 3.15f, p.z).sub(new Vector3f((float)Math.sin(yaw), 0, (float)Math.cos(yaw)).mul(5));
        // Shoulder offset keeps the human's head clear of the aiming reticle.
        eye.add(-(float)Math.cos(yaw) * 1.6f, 0, (float)Math.sin(yaw) * 1.6f);
        eye.x = Math.max(-11.2f, Math.min(11.2f, eye.x)); eye.z = Math.max(-11.2f, Math.min(11.2f, eye.z));
        return eye;
    }
    public static void validateArguments(String[] args) {
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--offline")) continue;
            if (args[i].equals("--game") && i + 1 < args.length && args[++i].equals("battle")) continue;
            throw new IllegalArgumentException("Usage: --game battle [--offline]; arena has no network or saves");
        }
    }
    public static void main(String[] args) throws Exception { validateArguments(args); new BattleApp().run(null); }
    public void run(Observer observer) throws Exception {
        if (!glfwInit()) throw new IllegalStateException("GLFW initialization failed");
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3); glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        window = glfwCreateWindow(width, height, "Voxel Battle Simulator", 0, 0);
        if (window == 0) { glfwTerminate(); throw new IllegalStateException("Battle window creation failed"); }
        try {
            glfwMakeContextCurrent(window); GL.createCapabilities(); glfwSwapInterval(1);
            renderer = glGetString(GL_RENDERER);
            Chunk chunk = new Chunk(); chunk.setBlock(0, 0, 0, Blocks.STONE); chunk.generateMesh();
            try (var cube = chunk.getMesh(); var shader = new ShaderProgram("shaders/voxel.vert", "shaders/voxel.frag");
                 var ui = new Overlay(); var recorder = new ScreenRecorder(Path.of(System.getProperty("user.home"), ".voxel-one", "recordings"))) {
                glfwSetFramebufferSizeCallback(window, (w, x, y) -> { width = x; height = y; });
                glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
                glfwSetWindowFocusCallback(window, (w, focused) -> { if (!focused) release(); });
                glfwSetCursorPosCallback(window, (w, x, y) -> {
                    if (captured && !firstMouse) {
                        yaw -= (float)(x - mouseX) * .003f;
                        pitch = Math.max(-.65f, Math.min(.85f, pitch + (float)(y - mouseY) * .003f));
                    }
                    firstMouse = false; mouseX = x; mouseY = y;
                });
                glfwSetMouseButtonCallback(window, (w, button, action, mods) -> {
                    if (action != GLFW_PRESS) return;
                    if (!captured) { captured = true; firstMouse = true; glfwSetInputMode(w, GLFW_CURSOR, GLFW_CURSOR_DISABLED); return; }
                    if (button == GLFW_MOUSE_BUTTON_LEFT || button == GLFW_MOUSE_BUTTON_RIGHT) {
                        arena.strike(arena.swing(arena.player, button == GLFW_MOUSE_BUTTON_RIGHT), eye(), direction()); inputSequence++;
                    }
                });
                glfwSetKeyCallback(window, (w, key, scan, action, mods) -> {
                    if (action != GLFW_PRESS) return;
                    try {
                        if (key == GLFW_KEY_ESCAPE) glfwSetWindowShouldClose(w, true);
                        if (key == GLFW_KEY_TAB) release();
                        if (key == GLFW_KEY_R) {
                            arena.reset(); yaw = (float)Math.PI; pitch = .1f; firstMouse = true;
                            captured = true; glfwSetInputMode(w, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
                        }
                        if (key == GLFW_KEY_F10) recorder.toggle(width, height);
                    } catch (Exception e) { throw new IllegalStateException(e); }
                    inputSequence++;
                });
                double before = glfwGetTime(); float accumulator = 0;
                while (!glfwWindowShouldClose(window)) {
                    glfwPollEvents(); double now = glfwGetTime();
                    frameSeconds = now - before; before = now;
                    accumulator += (float)Math.min(.1, frameSeconds);
                    Vector3f forward = new Vector3f((float)Math.sin(yaw), 0, (float)Math.cos(yaw));
                    Vector3f right = new Vector3f(-forward.z, 0, forward.x);
                    Vector3f movement = new Vector3f();
                    if (captured) {
                        movement.fma(key(GLFW_KEY_W) - key(GLFW_KEY_S), forward);
                        movement.fma(key(GLFW_KEY_D) - key(GLFW_KEY_A), right);
                        yaw += (key(GLFW_KEY_LEFT) - key(GLFW_KEY_RIGHT)) * frameSeconds * 1.2f;
                        pitch = Math.max(-.65f, Math.min(.85f, pitch + (key(GLFW_KEY_DOWN) - key(GLFW_KEY_UP)) * (float)frameSeconds * .65f));
                    }
                    long start = System.nanoTime();
                    while (accumulator >= BattleArena.STEP) { arena.step(movement, yaw); accumulator -= BattleArena.STEP; }
                    simulationSeconds = (System.nanoTime() - start) / 1e9;
                    draw(shader, cube, ui, recorder.status("F10"));
                    if (observer != null) observer.frame(this);
                    frames++; renderedInputSequence = inputSequence;
                    recorder.capture(width, height); glfwSwapBuffers(window);
                }
            }
        } finally {
            org.lwjgl.glfw.Callbacks.glfwFreeCallbacks(window); glfwDestroyWindow(window); glfwTerminate();
        }
    }
    private float key(int key) { return glfwGetKey(window, key) == GLFW_PRESS ? 1 : 0; }
    private void release() { captured = false; firstMouse = true; glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_NORMAL); }
    private void box(ShaderProgram shader, Mesh cube, Vector3f position, Quaternionf rotation, Vector3f half, float r, float g, float b) {
        shader.setVector3("uColor", r, g, b);
        shader.setMatrix4("uModel", new Matrix4f().translation(position).rotate(rotation).scale(half.x * 2, half.y * 2, half.z * 2).translate(-.5f, -.5f, -.5f)); cube.render();
    }
    private void draw(ShaderProgram shader, Mesh cube, Overlay ui, String recording) {
        glViewport(0, 0, width, height); glClearColor(.055f, .08f, .12f, 1);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT); glEnable(GL_DEPTH_TEST); glEnable(GL_CULL_FACE);
        shader.bind();
        String[] samplers = {"uEnvironment", "uShadow", "uIrradiance", "uFineRoots", "uFineLight", "uMaterials", "uVoxelRadiance", "uDistanceField", "uCascadeProbes[0]", "uCascadeProbes[1]", "uCascadeProbes[2]", "uClusters", "uLightIndices", "uLights", "uReflectionProbe"};
        for (int i = 0; i < samplers.length; i++) shader.setInt(samplers[i], i);
        shader.setInt("uTransmittance", 15);
        shader.setInt("uVertexColor", 0); shader.setInt("uInstanced", 0); shader.setInt("uLightingEnabled", 0);
        shader.setFloat("uTransparency", 0); shader.setVector3("uLightDirection", -.5f, -1, -.3f);
        shader.setMatrix4("uProjection", new Matrix4f().perspective((float)Math.toRadians(55), (float)width / Math.max(1, height), .1f, 100));
        shader.setMatrix4("uView", new Matrix4f().lookAt(eye(), new Vector3f(eye()).add(direction()), new Vector3f(0, 1, 0)));
        Quaternionf identity = new Quaternionf();
        for (int x = -6; x < 6; x++) for (int z = -6; z < 6; z++) {
            float c = (x + z) % 2 == 0 ? .20f : .25f;
            box(shader, cube, new Vector3f(x * 2 + 1, -.2f, z * 2 + 1), identity, new Vector3f(1, .2f, 1), c, c + .035f, c + .065f);
        }
        for (var body : arena.physics.bodies) if (body.inverseMass == 0 && body.position.y > 0)
            box(shader, cube, body.position, body.rotation, body.halfSize, .16f, .21f, .28f);
        for (var f : java.util.List.of(arena.player, arena.opponent)) for (var p : f.parts.values()) {
            var b = p.body; float health = p.health / p.maximum;
            float r = f.human ? .12f : .95f, g = f.human ? .58f : .48f, blue = f.human ? .94f : .12f;
            if (!p.attached) { r *= .65f; g *= .65f; blue *= .65f; }
            r = r * (.55f + .45f * health); g *= .55f + .45f * health;
            Vector3f position = new Vector3f(b.position);
            if (f.state == BattleArena.State.READY && f.cooldown > 0 && p.part == (f.lastHeavy ? BattleArena.Part.RIGHT_ARM : BattleArena.Part.LEFT_ARM))
                position.add(b.rotation.transform(new Vector3f(0, 0, .45f * Math.min(1, f.cooldown * 2))));
            box(shader, cube, position, b.rotation, b.halfSize, r, g, blue);
            if (p.part == BattleArena.Part.HEAD) for (float side : new float[]{-.16f, .16f})
                box(shader, cube, b.anchor(new Vector3f(side, .08f, .35f)), b.rotation, new Vector3f(.08f, .06f, .015f), .03f, .05f, .07f);
        }
        ui.begin(width, height);
        ui.rectangle(0, 0, width, 116, .025f, .04f, .065f, .94f);
        ui.text("VOXEL BATTLE SIMULATOR / OFFLINE ARENA", 18, 14, 1.8f);
        status(ui, arena.player, 18, 44); status(ui, arena.opponent, 18, 77);
        ui.rectangle(width / 2f - 7, height / 2f - 1, 14, 2, 1, 1, 1, .9f);
        ui.rectangle(width / 2f - 1, height / 2f - 7, 2, 14, 1, 1, 1, .9f);
        ui.rectangle(0, height - 93, width, 93, .025f, .04f, .065f, .94f);
        ui.text(arena.lastImpact, 18, height - 80, 1.2f);
        ui.text("WASD Move / Mouse or arrows Aim / LMB Left jab / RMB Right heavy", 18, height - 55, 1.2f);
        ui.text("R New round / F10 Record / TAB Free mouse / ESC Quit" + (captured ? "" : " / Click to resume"), 18, height - 30, 1.2f);
        if (!recording.isEmpty()) ui.text(recording, 18, 123, 1.2f);
        ui.end();
    }
    private void status(Overlay ui, BattleArena.Fighter f, float x, float y) {
        StringBuilder text = new StringBuilder(f.human ? "YOU    " : "PARTNER");
        text.append(" ").append(f.state).append("  ");
        String[] labels = {"H", "T", "LA", "RA", "LL", "RL"}; int i = 0;
        for (var p : f.parts.values()) text.append(labels[i++]).append(":").append(p.attached ? (int)p.health : "LOST").append(" ");
        ui.text(text.toString(), x, y, 1.25f);
    }
}
