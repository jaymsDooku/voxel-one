package dev.jayms.combat.client;

import dev.jayms.Camera;
import dev.jayms.Chunk;
import dev.jayms.ShaderProgram;
import dev.jayms.VoxelModelRenderer;
import dev.jayms.World;
import dev.jayms.combat.CombatEngine.Intent;
import dev.jayms.combat.CombatEngine.Item;
import dev.jayms.combat.CombatEngine.Stack;
import dev.jayms.combat.arena.BasaltCourtyard;
import dev.jayms.combat.arena.BasaltCourtyardWorld;
import dev.jayms.combat.practice.PracticeClient;
import dev.jayms.combat.practice.PracticeMatch;
import dev.jayms.combat.practice.PracticeMatch.Fighter;
import dev.jayms.combat.practice.PracticeMatch.Phase;
import dev.jayms.combat.practice.PracticeMatch.View;
import dev.jayms.combat.practice.PracticeServer;
import dev.jayms.net.Blocks;
import dev.jayms.net.ItemDrop;
import dev.jayms.net.Items;
import dev.jayms.net.Protocol;
import dev.jayms.net.city.GameConfig;
import dev.jayms.player.PlayerModel;
import dev.jayms.recording.ScreenRecorder;
import dev.jayms.render.GraphicsProfile;
import dev.jayms.render.RenderPipeline;
import dev.jayms.ui.Controls;
import dev.jayms.ui.Overlay;
import dev.jayms.window.Window;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPasswordField;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;

import static dev.jayms.ui.Controls.Action.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;

/** First-person practice view backed by the same match simulation online and offline. */
public final class PracticeGame {
    private static final long STEP = 50_000_000L;
    private static final int[] TEAM_JERSEYS = {0xe75a36, 0x38a6ce};
    private static final String[] TEAM_NAMES = {"Ember", "Slate"};
    private static final Controls.Action[] SLOTS = {SLOT_1, SLOT_2, SLOT_3, SLOT_4, SLOT_5, SLOT_6, SLOT_7, SLOT_8, SLOT_9};
    private final PracticeClient remote;
    private final PracticeMatch offline;
    private final boolean factions;
    private final Path recordings;
    private final int id;
    private final Camera camera = new Camera();
    private final Vector3f feet = new Vector3f(), destination = new Vector3f();
    private final Matrix4f projection = new Matrix4f();
    private final Matrix4f clipFromWorld = new Matrix4f();
    private final FrustumIntersection frustum = new FrustumIntersection();
    private Controls controls;
    private Window window;
    private View view;
    private boolean inventory, attack, usePressed, firstMouse = true, positioned;
    private int width, height, slot, source = -1;
    private long frameInput, sequence;
    private double mouseX, mouseY;
    private float handSwing = 1;
    private long confirmedAt = -1;
    private int confirmedHits;
    private Phase lastPhase;
    private boolean wasSpectator;

    /** Optional frame callback used by the engine's native playtest harness. */
    @FunctionalInterface
    public interface FrameObserver {
        void frame(Window window, ScreenRecorder recorder, View view, int width, int height) throws Exception;
        default Intent input(Intent controls, View view) { return controls; }
    }

    public PracticeGame(PracticeClient remote, PracticeMatch.Kit kit, boolean factions, Path recordings) {
        this.remote = remote;
        this.factions = factions;
        this.recordings = recordings;
        offline = remote == null ? new PracticeMatch(kit, true) : null;
        id = remote == null ? 1 : remote.id();
    }

    public static void launch(String[] args) throws Exception {
        String host = null, pin = null, name = "Player";
        int port = PracticeServer.DEFAULT_PORT;
        boolean offline = false, factions = false;
        PracticeMatch.Kit kit = PracticeMatch.Kit.NO_DEBUFF;
        for (int i = 0; i < args.length; i++) {
            String option = args[i];
            switch (option) {
                case "--offline" -> offline = true;
                case "--factions" -> factions = true;
                case "--server" -> host = value(args, ++i, option);
                case "--port" -> port = Integer.parseInt(value(args, ++i, option));
                case "--fingerprint" -> pin = value(args, ++i, option);
                case "--name" -> name = value(args, ++i, option);
                case "--kit" -> kit = PracticeMatch.Kit.valueOf(value(args, ++i, option)
                        .toUpperCase(Locale.ROOT).replace('-', '_'));
                default -> throw new IllegalArgumentException("Unknown practice option " + option);
            }
        }
        Path recordings = Controls.directory().resolve("recordings");
        if (offline || host == null) {
            if (factions) throw new IllegalArgumentException("Faction play requires a dedicated arena server");
            new PracticeGame(null, kit, false, recordings).run(null);
            return;
        }
        if (!factions) {
            try (var client = new PracticeClient(host, port, pin, name)) {
                new PracticeGame(client, kit, false, recordings).run(null);
            }
            return;
        }
        var passwordField = new JPasswordField();
        var register = new JCheckBox("Create a faction account");
        if (JOptionPane.showConfirmDialog(null, new Object[]{new JLabel("Faction account for " + name),
                passwordField, register}, "Voxel One faction sign-in", JOptionPane.OK_CANCEL_OPTION)
                != JOptionPane.OK_OPTION) return;
        char[] password = passwordField.getPassword();
        passwordField.setText("");
        PracticeClient client;
        try {
            client = new PracticeClient(host, port, pin, name, password, register.isSelected());
        } finally {
            Arrays.fill(password, '\0');
        }
        try (client) {
            new PracticeGame(client, kit, true, recordings).run(null);
        }
    }

    private static String value(String[] args, int index, String option) {
        if (index >= args.length || args[index].startsWith("--")) throw new IllegalArgumentException("Missing value for " + option);
        return args[index];
    }

    public void run(FrameObserver observer) throws Exception {
        controls = new Controls(Controls.directory().resolve("controls.properties"));
        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) {
            var callback = glfwSetErrorCallback(null);
            if (callback != null) callback.free();
            throw new IllegalStateException("Unable to initialize practice window");
        }
        try {
            window = new Window(1120, 680, factions ? "Voxel One | Faction Arena" : "Voxel One | PotPvP Practice", false, true);
            window.center();
            window.setOpenGlContext();
            window.vSync();
            GL.createCapabilities();
            glEnable(GL_DEPTH_TEST);
            glEnable(GL_CULL_FACE);
            glfwSetWindowSizeLimits(window.getHandle(), 640, 440, GLFW_DONT_CARE, GLFW_DONT_CARE);
            try (var stack = MemoryStack.stackPush()) {
                var w = stack.mallocInt(1);
                var h = stack.mallocInt(1);
                glfwGetFramebufferSize(window.getHandle(), w, h);
                width = w.get(0); height = h.get(0);
            }
            runScene(observer);
        } finally {
            if (window != null) window.destroy();
            glfwTerminate();
            var callback = glfwSetErrorCallback(null);
            if (callback != null) callback.free();
        }
    }

    private void runScene(FrameObserver observer) throws Exception {
        World world = new World();
        try (var recorder = new ScreenRecorder(recordings);
             var shader = new ShaderProgram("shaders/voxel.vert", "shaders/voxel.frag");
             var rendering = new RenderPipeline(GraphicsProfile.preset(GraphicsProfile.Preset.LOW)
                     .with(GraphicsProfile.Key.SHADOWS, "OFF").with(GraphicsProfile.Key.AA, "OFF"), shader);
             var overlay = new Overlay();
             var avatars = new PlayerModel();
             var models = new VoxelModelRenderer(world.models())) {
            BasaltCourtyardWorld.generate(world);
            world.stream(0, 0, 25);
            for (var entry : world.getLoadedChunks().entrySet())
                if (entry.getKey().chunkY() >= 2) entry.getValue().checkMesh();
            bind(recorder);
            view = snapshot();
            updateCamera(1);
            window.show();
            glViewport(0, 0, width, height);
            long previous = System.nanoTime();
            frameInput = previous;
            while (!window.shouldClose()) {
                window.pollEvents();
                long now = System.nanoTime();
                float dt = Math.min(.1f, (now - previous) / 1e9f);
                previous = now;
                if (now >= frameInput) {
                    sendInput(observer);
                    if (offline == null) frameInput = Math.max(frameInput + STEP, now);
                    else {
                        int count = 0;
                        do {
                            offline.tick();
                            frameInput += STEP;
                        } while (now >= frameInput && ++count < 4);
                        if (now >= frameInput) frameInput = now + STEP;
                    }
                }
                view = snapshot();
                updateCamera(dt);
                if (width == 0 || height == 0) { glfwWaitEventsTimeout(.1); continue; }
                handSwing = Math.min(1, handSwing + dt / .26f);
                render(world, shader, rendering, models, avatars);
                drawHud(overlay, recorder);
                if (observer != null) observer.frame(window, recorder, view, width, height);
                recorder.capture(width, height);
                window.swapBuffers();
            }
        } finally {
            world.close();
        }
    }

    private void bind(ScreenRecorder recorder) {
        long handle = window.getHandle();
        glfwSetFramebufferSizeCallback(handle, (w, x, y) -> { width = x; height = y; });
        glfwSetInputMode(handle, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
        glfwSetCursorPosCallback(handle, (w, x, y) -> {
            if (!inventory && !firstMouse) {
                camera.setYaw((camera.yaw() + (float) (x - mouseX) * controls.sensitivity) % 360);
                camera.setPitch(camera.pitch() + (float) (mouseY - y) * controls.sensitivity);
            }
            mouseX = x; mouseY = y;
            firstMouse = false;
        });
        glfwSetWindowFocusCallback(handle, (w, focused) -> {
            firstMouse = true;
            if (!focused) attack = usePressed = false;
        });
        glfwSetScrollCallback(handle, (w, x, y) -> {
            if (!inventory && y != 0) slot = Math.floorMod(slot - (y > 0 ? 1 : -1), 9);
        });
        glfwSetMouseButtonCallback(handle, (w, button, action, mods) -> {
            if (action != GLFW_PRESS) return;
            if (inventory) {
                if (button == GLFW_MOUSE_BUTTON_LEFT) selectInventory();
            } else combatPress(-button - 1);
        });
        glfwSetKeyCallback(handle, (w, key, scan, action, mods) -> {
            if (action != GLFW_PRESS) return;
            if (key == GLFW_KEY_ESCAPE) {
                if (inventory) setInventory(false);
                else glfwSetWindowShouldClose(handle, true);
            } else if (controls.matches(INVENTORY, key)) {
                if (view != null && !view.inventory().isEmpty()) setInventory(!inventory);
            } else if (controls.matches(RECORD, key)) recorder.toggle(width, height);
            else if (key == GLFW_KEY_ENTER) {
                setInventory(false);
                if (remote != null) remote.ready();
                else offline.ready(id);
            } else if (factions && factionKey(key) != 0) {
                remote.action(factionKey(key));
            } else {
                for (int i = 0; i < SLOTS.length; i++) if (controls.matches(SLOTS[i], key)) slot = i;
                if (!inventory) combatPress(key);
            }
        });
    }

    private void combatPress(int key) {
        if (controls.matches(BREAK, key)) {
            attack = true;
            if (handSwing >= .5) handSwing = 0;
        }
        if (controls.matches(PLACE, key)) {
            usePressed = true;
            if (handSwing >= .5) handSwing = .12f;
        }
    }

    private static int factionKey(int key) {
        return switch (key) {
            case GLFW_KEY_F1, GLFW_KEY_F2, GLFW_KEY_F3, GLFW_KEY_F4, GLFW_KEY_F5 -> key - GLFW_KEY_F1 + 1;
            case GLFW_KEY_R -> 6;
            case GLFW_KEY_Z -> 7;
            case GLFW_KEY_X -> 8;
            case GLFW_KEY_C -> 9;
            case GLFW_KEY_V -> 10;
            case GLFW_KEY_L -> 11;
            default -> 0;
        };
    }

    private void setInventory(boolean open) {
        if (inventory == open) return;
        inventory = open; source = -1; firstMouse = true; attack = usePressed = false;
        glfwSetInputMode(window.getHandle(), GLFW_CURSOR, open ? GLFW_CURSOR_NORMAL : GLFW_CURSOR_DISABLED);
    }

    private void selectInventory() {
        int[] screen = window.getSize();
        float x = (float) mouseX * width / Math.max(1, screen[0]);
        float y = (float) mouseY * height / Math.max(1, screen[1]);
        float left = (width - 540) / 2f, top = (height - 184) / 2f;
        if (x < left || x >= left + 540 || y < top || y >= top + 184) return;
        int selection = ((int) (y - top) / 46) * 9 + (int) (x - left) / 60;
        if (source < 0) { source = selection; return; }
        if (remote != null) remote.swap(source, selection);
        else offline.swap(id, source, selection);
        source = -1;
    }

    private View snapshot() { return remote == null ? offline.view(id) : remote.view(); }

    private void sendInput(FrameObserver observer) {
        boolean enabled = !inventory && glfwGetWindowAttrib(window.getHandle(), GLFW_FOCUSED) == GLFW_TRUE;
        long w = window.getHandle();
        float forward = enabled ? axis(w, FORWARD, BACKWARD) : 0;
        float strafe = enabled ? axis(w, RIGHT, LEFT) : 0;
        Intent intent = new Intent(++sequence, camera.yaw(), camera.pitch(), forward, strafe, enabled && controls.down(w, JUMP),
                enabled && controls.down(w, SPRINT), enabled && attack, enabled && (usePressed || controls.down(w, PLACE)), slot);
        if (observer != null) {
            intent = observer.input(intent, view);
            camera.setYaw(intent.yaw());
            camera.setPitch(intent.pitch());
            slot = intent.slot();
        }
        if (remote == null) offline.input(id, intent);
        if (remote == null || remote.input(intent)) attack = usePressed = false;
    }

    private int axis(long window, Controls.Action positive, Controls.Action negative) {
        return (controls.down(window, positive) ? 1 : 0) - (controls.down(window, negative) ? 1 : 0);
    }

    private Fighter self() { return view.fighters().stream().filter(f -> f.id() == id).findFirst().orElse(null); }

    private void updateCamera(float dt) {
        Fighter actor = self();
        boolean spectator = actor == null || view.spectatorId() == id;
        var gallery = BasaltCourtyard.spectator();
        if (spectator) destination.set(gallery.x(), gallery.y(), gallery.z());
        else destination.set(actor.x(), actor.y(), actor.z());
        boolean respawn = !positioned || spectator != wasSpectator || view.phase() == Phase.COUNTDOWN && lastPhase != Phase.COUNTDOWN;
        if (respawn) {
            camera.setYaw(spectator ? gallery.yaw() : actor.yaw());
            camera.setPitch(spectator ? gallery.pitch() : actor.pitch());
        }
        if (respawn || feet.distanceSquared(destination) > 4) feet.set(destination);
        else feet.lerp(destination, 1 - (float) Math.exp(-25 * dt));
        camera.position().set(feet).add(0, 1.62f, 0);
        positioned = true; wasSpectator = spectator; lastPhase = view.phase();
    }

    private void render(World world, ShaderProgram shader, RenderPipeline pipeline, VoxelModelRenderer models, PlayerModel avatars) {
        Vector3f eye = camera.position();
        projection.identity().perspective((float) Math.toRadians(73), (float) width / height, .06f, 180);
        Matrix4f matrix = camera.createViewMatrix();
        clipFromWorld.set(projection).mul(matrix);
        frustum.set(clipFromWorld);
        pipeline.time(GameConfig.sandbox(), 0);
        pipeline.update(world, eye.x, eye.z);
        pipeline.renderShadows(world, models, eye);
        pipeline.begin(width, height, projection, matrix, eye, false, shader);
        for (var entry : world.getLoadedChunks().entrySet()) {
            var position = entry.getKey();
            float x = position.chunkX() * (float) Chunk.WIDTH, y = position.chunkY() * (float) Chunk.HEIGHT;
            float z = position.chunkZ() * (float) Chunk.LENGTH;
            if (!frustum.testAab(x, y, z, x + 16, y + 16, z + 16)) continue;
            shader.setMatrix4("uModel", new Matrix4f().translation(x, y, z));
            pipeline.chunk(entry.getValue(), position);
        }
        for (Fighter fighter : view.fighters()) {
            if (fighter.id() == id || fighter.health() <= 0) continue;
            var pose = new Protocol.Pose(fighter.id(), fighter.x(), fighter.y(), fighter.z(), fighter.yaw(),
                    fighter.pitch(), view.tick() * .55f, fighter.moving() ? 1 : 0, false,
                    fighter.swinging() ? .36f : 1, renderItem(fighter.held()));
            avatars.render(pose, shader, models, fighter.side() < 0 ? -1 : TEAM_JERSEYS[fighter.side()]);
        }
        for (var missile : view.missiles()) {
            var drop = new ItemDrop((int) missile.id(), renderItem(missile.item()), 1, missile.x(), missile.y(), missile.z());
            avatars.renderDrop(drop, view.tick() / 20f, shader);
        }
        pipeline.finish();
        Fighter actor = self();
        if (actor != null && view.inventory().size() == 36) {
            glClear(GL_DEPTH_BUFFER_BIT);
            pipeline.prepareHeld(shader);
            shader.setInt("uFog", 0);
            shader.setInt("uShadowEnabled", 0);
            shader.setInt("uHeld", 1);
            shader.setInt("uLightingEnabled", 0);
            shader.setVector3("uLightDirection", -.35f, -.70f, -.62f);
            shader.setMatrix4("uProjection", new Matrix4f().perspective((float) Math.toRadians(70), (float) width / height, .03f, 10));
            shader.setMatrix4("uView", new Matrix4f());
            avatars.renderFirstPerson(view.tick() * .55f, actor.moving() ? 1 : 0, handSwing, false,
                    renderItem(view.inventory().get(slot).item()), 0xffffff, shader, models);
            shader.setInt("uHeld", 0);
        }
    }

    private static int renderItem(Item item) {
        return switch (item) {
            case EMPTY -> 0;
            case SWORD -> Items.STONE_SWORD;
            case HEALING -> Blocks.BRICKS;
            case SPEED -> Blocks.SNOW;
            case FIRE_RESISTANCE -> Blocks.SAND;
            case PEARL -> Blocks.LEAVES;
            case POISON -> Blocks.GRASS;
            case SLOWNESS -> Blocks.GLASS;
            case FOOD, BOW, ARROW -> Blocks.WOOD;
        };
    }

    private void drawHud(Overlay ui, ScreenRecorder recorder) {
        ui.begin(width, height);
        Fighter actor = self();
        if (actor != null && actor.hits() != confirmedHits) {
            if (view.phase() == Phase.FIGHT && actor.hits() > confirmedHits) confirmedAt = view.tick();
            confirmedHits = actor.hits();
        }
        if (!inventory) {
            ui.rectangle(width / 2f - 6, height / 2f, 12, 2, 1, 1, 1, .85f);
            ui.rectangle(width / 2f - 1, height / 2f - 5, 2, 12, 1, 1, 1, .85f);
            if (confirmedAt >= 0 && view.tick() - confirmedAt < 4) {
                float x = width / 2f, y = height / 2f;
                ui.line(x - 10, y - 10, x - 5, y - 5, 2, 1, .45f, .20f, 1);
                ui.line(x + 10, y - 10, x + 5, y - 5, 2, 1, .45f, .20f, 1);
                ui.line(x - 10, y + 10, x - 5, y + 5, 2, 1, .45f, .20f, 1);
                ui.line(x + 10, y + 10, x + 5, y + 5, 2, 1, .45f, .20f, 1);
            }
        }
        if (view.lastHit() >= 0 && view.tick() - view.lastHit() < 5) {
            ui.rectangle(0, 0, width, 8, 1, .16f, .12f, .75f);
            ui.rectangle(0, height - 8, width, 8, 1, .16f, .12f, .75f);
        }
        if (factions) drawTeamLabels(ui);
        String title = factions ? "Faction Arena" : view.kit() == PracticeMatch.Kit.DEBUFF ? "Debuff" : "No Debuff";
        ui.rectangle(16, 13, 325, 59, .031f, .05f, .065f, .90f);
        ui.text("Basalt Courtyard  /  " + title, 27, 24, 1.4f);
        String state = stateText(actor);
        ui.text(state, 27, 47, 1.05f, .66f, .84f, .90f, 1);
        if (factions) ui.text("Z/X: team  F1-F5: class  R: ability  C: resupply  V: raid  L: logout",
                22, height - 22, 1.05f);
        int line = 0;
        for (String status : view.status()) {
            ui.rectangle(16, 76 + line * 19, ui.textWidth(status, 1f) + 22, 19, .031f, .05f, .065f, .86f);
            ui.text(status, 27, 81 + line++ * 19, 1);
        }
        if (view.phase() == Phase.COUNTDOWN || view.phase() == Phase.RESULT) {
            String notice = view.phase() == Phase.COUNTDOWN ? Integer.toString((view.ticksLeft() + 19) / 20)
                    : resultText();
            float scale = view.phase() == Phase.COUNTDOWN ? 5 : 2;
            float size = ui.textWidth(notice, scale);
            ui.rectangle((width - size) / 2 - 14, height / 2f - 71, size + 28, 50, .031f, .05f, .065f, .92f);
            ui.text(notice, (width - size) / 2, height / 2f - 61, scale);
        }
        if (actor != null && !view.inventory().isEmpty()) drawPlayerHud(ui, actor);
        if (inventory && view.inventory().size() == 36) drawInventory(ui);
        String recording = recorder.status(Controls.keyName(controls.code(RECORD)));
        if (!recording.isBlank()) ui.text(recording, 20, height - (factions ? 42 : 22), 1);
        ui.end();
    }

    private String stateText(Fighter actor) {
        if (remote != null && !remote.connected()) return "Disconnected  /  Esc to exit";
        if (view.spectatorId() == id) return "Spectating";
        return switch (view.phase()) {
            case WAITING -> actor != null && actor.ready() ? "Ready  |  Enter: cancel" : "Enter: ready  |  E: edit kit";
            case COUNTDOWN -> "Get ready";
            case FIGHT -> "Fight  /  " + (view.ticksLeft() / 20) + "s";
            case RESULT -> view.ticksLeft() > 0 ? "Round complete" : "Enter: ready for another round";
        };
    }

    private String resultText() {
        if (view.winner() == 0) return "Draw";
        return view.fighters().stream().filter(f -> f.id() == view.winner()).findFirst()
                .map(f -> (f.side() < 0 ? f.name() : TEAM_NAMES[f.side()]) + " wins").orElse("Faction victory");
    }

    private void drawTeamLabels(Overlay ui) {
        for (Fighter fighter : view.fighters()) {
            if (fighter.id() == id || fighter.side() < 0 || fighter.health() <= 0 || !visible(fighter)) continue;
            Vector4f clip = clipFromWorld.transform(new Vector4f(fighter.x(), fighter.y() + 2.16f, fighter.z(), 1));
            if (clip.w <= 0 || Math.abs(clip.x) > clip.w || Math.abs(clip.y) > clip.w || Math.abs(clip.z) > clip.w) continue;
            float x = (clip.x / clip.w * .5f + .5f) * width;
            float y = (.5f - clip.y / clip.w * .5f) * height;
            String text = fighter.name() + " / " + fighter.role();
            float boxWidth = ui.textWidth(text, 1.15f) + 14;
            int color = TEAM_JERSEYS[fighter.side()];
            ui.rectangle(x - boxWidth / 2, y - 3, boxWidth, 16, .031f, .05f, .065f, .83f);
            ui.text(text, x - boxWidth / 2 + 7, y, 1.15f, (color >> 16 & 255) / 255f,
                    (color >> 8 & 255) / 255f, (color & 255) / 255f, 1);
        }
    }

    private boolean visible(Fighter fighter) {
        Vector3f eye = camera.position();
        Vector3f offset = new Vector3f(fighter.x(), fighter.y() + 1.36f, fighter.z()).sub(eye);
        int count = (int) Math.ceil(offset.length() * 5);
        if (count < 1 || count > 220) return false;
        for (int i = 1; i < count; i++) {
            float t = i / (float) count;
            if (BasaltCourtyard.block((int) Math.floor(eye.x + offset.x * t),
                    (int) Math.floor(eye.y + offset.y * t),
                    (int) Math.floor(eye.z + offset.z * t)) != Blocks.AIR) return false;
        }
        return true;
    }

    private void drawPlayerHud(Overlay ui, Fighter actor) {
        float x = (width - 540) / 2f, y = height - 74;
        String summary = String.format(Locale.ROOT, "HP %.1f / 20   FOOD %d   HITS %d   COMBO %d   POTS %d / %d",
                actor.health(), view.food(), actor.hits(), actor.combo(), actor.used(), actor.remaining());
        ui.rectangle(x, y - 30, 540, 29, .031f, .05f, .065f, .92f);
        ui.rectangle(x + 1, y - 4, 538 * Math.max(0, Math.min(20, actor.health())) / 20, 3, .96f, .29f, .22f, 1);
        ui.text(summary, x + 10, y - 22, 1.15f);
        for (int i = 0; i < 9; i++) drawSlot(ui, view.inventory().get(i), i, x + i * 60, y, i == slot);
        int armor = view.durability().stream().mapToInt(Integer::intValue).sum();
        String timers = "Armor " + armor + (view.cooldown() > 0 ? "   Pearl " + (view.cooldown() + 19) / 20 + "s" : "");
        for (var effect : view.effects().entrySet()) timers += "   " + effect.getKey().name() + " " + (effect.getValue() + 19) / 20 + "s";
        ui.text(timers, x, y + 52, 1.02f);
        if (view.phase() == Phase.RESULT) {
            for (Fighter fighter : view.fighters()) {
                String result = String.format(Locale.ROOT, "%s  HP %.1f  Hits %d  Combo %d  Pots used %d  Left %d",
                        fighter.name(), fighter.health(), fighter.hits(), fighter.combo(), fighter.used(), fighter.remaining());
                ui.text(result, x, y - 54 - view.fighters().indexOf(fighter) * 19, 1.1f);
            }
        }
    }

    private void drawInventory(Overlay ui) {
        float x = (width - 540) / 2f, y = (height - 184) / 2f;
        ui.rectangle(x - 12, y - 46, 564, 242, .031f, .05f, .065f, .96f);
        ui.text(view.phase() == Phase.WAITING ? "Kit layout" : "Inventory", x, y - 33, 1.6f);
        ui.text("Click one slot, then its destination. Top row is the hotbar.", x, y - 15, 1.05f);
        for (int i = 0; i < 36; i++) drawSlot(ui, view.inventory().get(i), i, x + i % 9 * 60, y + i / 9 * 46, i == source);
    }

    private static void drawSlot(Overlay ui, Stack stack, int i, float x, float y, boolean selected) {
        ui.rectangle(x + 1, y + 1, 58, 43, selected ? .27f : .07f, selected ? .48f : .12f, .17f, .95f);
        if (i < 9) ui.text(Integer.toString(i + 1), x + 4, y + 5, .9f, .70f, .79f, .87f, 1);
        if (stack.count() == 0) return;
        String label = switch (stack.item()) {
            case HEALING -> "Heal";
            case FIRE_RESISTANCE -> "FireRes";
            case SLOWNESS -> "Slow";
            default -> stack.item().name().substring(0, 1) + stack.item().name().substring(1).toLowerCase(Locale.ROOT);
        };
        float[] color = Blocks.color(renderItem(stack.item()));
        ui.rectangle(x + 5, y + 20, 5, 10, color[0], color[1], color[2], 1);
        ui.text(label, x + 13, y + 20, .9f);
        if (stack.count() > 1) ui.text(Integer.toString(stack.count()), x + 38, y + 33, .88f);
    }
}
