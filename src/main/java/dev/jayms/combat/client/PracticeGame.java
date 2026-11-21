package dev.jayms.combat.client;

import dev.jayms.Camera;
import dev.jayms.Chunk;
import dev.jayms.ShaderProgram;
import dev.jayms.VoxelModelRenderer;
import dev.jayms.World;
import dev.jayms.combat.CombatEngine;
import dev.jayms.combat.CombatEngine.Arena;
import dev.jayms.combat.CombatEngine.AttackView;
import dev.jayms.combat.CombatEngine.Intent;
import dev.jayms.combat.CombatEngine.Item;
import dev.jayms.combat.CombatEngine.PoseView;
import dev.jayms.combat.CombatEngine.Stack;
import dev.jayms.combat.CombatEngine.Vec3;
import dev.jayms.combat.CombatGeometry;
import dev.jayms.combat.CombatRules;
import dev.jayms.combat.arena.BasaltBattlefieldWorld;
import dev.jayms.combat.arena.BasaltCourtyardWorld;
import dev.jayms.combat.arena.PracticeTerrain;
import dev.jayms.combat.arena.VaultBattlefield;
import dev.jayms.combat.arena.VaultBattlefieldWorld;
import dev.jayms.combat.practice.PracticeClient;
import dev.jayms.combat.practice.PracticeMatch;
import dev.jayms.combat.practice.PracticeMatch.Fighter;
import dev.jayms.combat.practice.PracticeMatch.Kit;
import dev.jayms.combat.practice.PracticeMatch.Phase;
import dev.jayms.combat.practice.PracticeMatch.View;
import dev.jayms.combat.practice.PracticeServer;
import dev.jayms.net.Blocks;
import dev.jayms.net.ItemDrop;
import dev.jayms.net.Items;
import dev.jayms.net.Protocol;
import dev.jayms.net.city.GameConfig;
import dev.jayms.player.PlayerDimensions;
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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
    private final PracticeHandAnimation hand = new PracticeHandAnimation();
    private final ArenaSprintToggle sprintToggle = new ArenaSprintToggle();
    private final ArenaAutoClicker autoClicker = new ArenaAutoClicker();
    private final PracticeTerrain terrain;
    private final ArenaMotionTimeline motion;
    private final String[] hotbarLabels = new String[SLOTS.length];
    private final Matrix4f projection = new Matrix4f();
    private final Matrix4f clipFromWorld = new Matrix4f();
    private final FrustumIntersection frustum = new FrustumIntersection();
    private Controls controls;
    private Window window;
    private View view;
    private final List<ArenaMotionTimeline.Presented> remoteActors = new ArrayList<>(4);
    private boolean inventory, usePressed, previousIntentUse, firstMouse = true, positioned;
    private boolean sprintReleased, showHitboxes, hitboxShortcutNeedsControl;
    private boolean showSparringStats;
    private final ArrayDeque<Intent> pendingCombatInputs = new ArrayDeque<>();
    private PoseView localPose;
    private Boolean scriptedUse;
    private int width, height, slot, source = -1;
    private long frameInput, sequence;
    private double mouseX, mouseY;
    private ArenaMotionTimeline.HitFeedback hitFeedback = ArenaMotionTimeline.HitFeedback.NONE;
    private Phase lastPhase;
    private boolean wasSpectator;

    /** Optional frame callback used by the engine's native playtest harness. */
    @FunctionalInterface
    public interface FrameObserver {
        void frame(Window window, ScreenRecorder recorder, View view, int width, int height) throws Exception;
        default Intent input(Intent controls, View view) { return controls; }
        /** The world pose and confirmed flash strength submitted for each visible remote fighter. */
        default void presented(int id, float x, float y, float z, float hurtTint) {}
        default void presented(int id, float x, float y, float z, float hurtTint, float swingProgress) {
            presented(id, x, y, z, hurtTint);
        }
        default void sampled(long now, View view, double x, double y, double z, Vec3 confirmed) {}
        default void hitFeedback(long tick, float received, float dealt, float rollRadians) {}
        default void sprintClickPreview(long now, View view, double offsetX, double offsetZ) {}
        default void submitted(long now, Intent intent) {}
    }

    public PracticeGame(PracticeClient remote, PracticeMatch.Kit kit, boolean factions, Path recordings) {
        this.remote = remote;
        this.factions = factions;
        this.recordings = recordings;
        offline = remote == null ? new PracticeMatch(kit, true) : null;
        id = remote == null ? 1 : remote.id();
        terrain = (remote == null ? kit : remote.view().kit()).terrain();
        motion = new ArenaMotionTimeline(terrain);
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
            window = new Window(1120, 680, title(), false, true);
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
             var hitboxes = new PracticeHitboxes();
             var models = new VoxelModelRenderer(world.models())) {
            switch (terrain) {
                case BATTLEFIELD -> BasaltBattlefieldWorld.generate(world);
                case VAULT_BATTLEFIELD -> VaultBattlefieldWorld.generate(world);
                case COURTYARD -> {
                    BasaltCourtyardWorld.generate(world);
                    world.stream(0, 0, 25);
                }
            }
            for (var entry : world.getLoadedChunks().entrySet())
                if (entry.getKey().chunkY() >= 2) entry.getValue().checkMesh();
            bind(recorder);
            view = snapshot();
            updatePresentation(System.nanoTime(), observer);
            window.show();
            glViewport(0, 0, width, height);
            frameInput = System.nanoTime();
            while (!window.shouldClose()) {
                window.pollEvents();
                long now = System.nanoTime();
                if (autoClicker.enabled() && autoClicker.poll(now, !inventory && canAnimateCombat()
                        && glfwGetWindowAttrib(window.getHandle(), GLFW_FOCUSED) == GLFW_TRUE && !useHeld())) {
                    attack(now);
                }
                if (now >= frameInput) {
                    sendInput(observer, now);
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
                updatePresentation(now, observer);
                if (width == 0 || height == 0) { glfwWaitEventsTimeout(.1); continue; }
                render(world, shader, rendering, models, avatars, hitboxes, now);
                drawHud(overlay, recorder);
                if (observer != null) {
                    observer.hitFeedback(view.tick(), hitFeedback.received(), hitFeedback.dealt(), hitFeedback.rollRadians());
                    for (var fighter : remoteActors)
                        observer.presented(fighter.fighter().id(), fighter.x(), fighter.y(), fighter.z(), fighter.hurt(), fighter.swingProgress());
                    observer.frame(window, recorder, view, width, height);
                }
                recorder.capture(width, height);
                window.swapBuffers();
            }
        } finally {
            world.close();
        }
    }

    private void bind(ScreenRecorder recorder) {
        long handle = window.getHandle();
        for (int i = 0; i < SLOTS.length; i++) hotbarLabels[i] = Controls.keyName(controls.code(SLOTS[i]));
        hitboxShortcutNeedsControl = Arrays.stream(Controls.Action.values())
                .anyMatch(action -> controls.matches(action, GLFW_KEY_F12));
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
            if (!focused) {
                usePressed = sprintReleased = false;
                pendingCombatInputs.clear();
                sprintToggle.suspend();
            }
        });
        glfwSetScrollCallback(handle, (w, x, y) -> {
            if (!inventory && y != 0) selectSlot(Math.floorMod(slot - (y > 0 ? 1 : -1), 9), System.nanoTime());
        });
        glfwSetMouseButtonCallback(handle, (w, button, action, mods) -> {
            sprintInput(w, -button - 1, action);
            if (action == GLFW_RELEASE) releaseSwordBlock(-button - 1);
            if (action != GLFW_PRESS) return;
            if (inventory) {
                if (button == GLFW_MOUSE_BUTTON_LEFT && (mods & GLFW_MOD_SHIFT) != 0) {
                    selectInventory(true);
                } else if (!organiseWithHotbar(-button - 1) && button == GLFW_MOUSE_BUTTON_LEFT) {
                    selectInventory(false);
                }
            } else combatPress(-button - 1);
        });
        glfwSetKeyCallback(handle, (w, key, scan, action, mods) -> {
            if (action == GLFW_PRESS && key == GLFW_KEY_F12
                    && (!hitboxShortcutNeedsControl || (mods & GLFW_MOD_CONTROL) != 0)) {
                showHitboxes = !showHitboxes;
                return;
            }
            sprintInput(w, key, action);
            if (action == GLFW_RELEASE) releaseSwordBlock(key);
            if (action != GLFW_PRESS) return;
            if (key == GLFW_KEY_ESCAPE) {
                if (inventory) setInventory(false);
                else glfwSetWindowShouldClose(handle, true);
            } else if (controls.matches(INVENTORY, key)) {
                if (view != null && !view.inventory().isEmpty()) setInventory(!inventory);
            } else if (controls.matches(RECORD, key)) recorder.toggle(width, height);
            else if (inventory && organiseWithHotbar(key)) return;
            else if (key == GLFW_KEY_ENTER) {
                if (offline != null && !inventory && (mods & GLFW_MOD_CONTROL) != 0
                        && controls.matches(SPARRING_STATS, key)) {
                    combatPress(key);
                    return;
                }
                setInventory(false);
                if (remote != null) remote.ready();
                else offline.ready(id);
            } else if (inventory) {
                return;
            } else if (factions && factionKey(key) != 0) {
                remote.action(factionKey(key));
            } else {
                int selection = hotbarSlot(key);
                if (selection >= 0) selectSlot(selection, System.nanoTime());
                combatPress(key);
            }
        });
    }

    private void sprintInput(long handle, int code, int action) {
        if (inventory || glfwGetWindowAttrib(handle, GLFW_FOCUSED) != GLFW_TRUE) return;
        if (controls.matches(SPRINT, code)) {
            boolean before = sprintToggle.requested();
            long now = System.nanoTime();
            if (action == GLFW_PRESS && view != null && view.food() > 6) sprintToggle.press(now);
            if (action == GLFW_RELEASE)
                sprintToggle.release(now, canContinueSprint(axis(handle, FORWARD, BACKWARD)), lastHorizontalWallTick());
            if (before && !sprintToggle.requested() && canAnimateCombat()) sprintReleased = true;
        } else if (action == GLFW_RELEASE && controls.matches(FORWARD, code)) {
            sprintToggle.stopRun();
            if (canAnimateCombat()) sprintReleased = true;
        }
    }

    private boolean canContinueSprint(float forward) {
        if (forward <= 0 || view == null || view.food() <= 6) return false;
        Item item = selectedItem();
        if (useHeld() && (item == Item.SWORD || CombatEngine.startsTimedUse(item, view.food()))) return false;
        var fighter = self();
        return fighter != null && fighter.health() > 0 && fighter.moving()
                && fighter.lastHorizontalWallTick() != view.tick();
    }

    private long lastHorizontalWallTick() {
        Fighter fighter = view == null ? null : self();
        return fighter == null ? -1 : fighter.lastHorizontalWallTick();
    }

    private boolean sprintRequested(float forward) {
        if (sprintToggle.continuingRun() && sprintToggle.updateRun(canContinueSprint(forward), lastHorizontalWallTick()))
            sprintReleased = true;
        return sprintToggle.requested();
    }

    private int hotbarSlot(int key) {
        for (int i = 0; i < SLOTS.length; i++)
            if (controls.matches(SLOTS[i], key)) return i;
        return -1;
    }

    private boolean organiseWithHotbar(int code) {
        int slot = hotbarSlot(code);
        if (slot < 0) return false;
        swapInventory(hoveredInventorySlot(), slot);
        return true;
    }

    private void combatPress(int key) {
        if (offline != null && controls.matches(SPARRING_STATS, key)) {
            showSparringStats = !showSparringStats;
            return;
        }
        if (controls.matches(AUTO_CLICK, key)) {
            autoClicker.toggle();
            return;
        }
        if (controls.matches(BREAK, key) && !autoClicker.enabled() && canAnimateCombat()) attack(System.nanoTime());
        if (controls.matches(PLACE, key)) {
            usePressed = selectedItem() != Item.SWORD;
            if (canAnimateCombat()) hand.use(selectedItem(), System.nanoTime());
            if (selectedItem() == Item.SWORD && canAnimateCombat()) sendCombatEdge(false, true);
        }
    }

    private void attack(long now) {
        hand.attack(selectedItem(), useHeld(), now);
        sendCombatEdge(true, useHeld() || selectedItem() != Item.SWORD && usePressed);
    }

    private void releaseSwordBlock(int code) {
        if (!inventory && controls.matches(PLACE, code) && selectedItem() == Item.SWORD && canAnimateCombat()
                && glfwGetWindowAttrib(window.getHandle(), GLFW_FOCUSED) == GLFW_TRUE)
            sendCombatEdge(false, false);
    }

    private void sendCombatEdge(boolean attack, boolean use) {
        long handle = window.getHandle();
        if (glfwGetWindowAttrib(handle, GLFW_FOCUSED) != GLFW_TRUE) return;
        // Reserve two slots for a W-tap release and its following input when the writer is busy.
        if (!flushCombatInputs() && pendingCombatInputs.size() > 30) return;
        float forward = axis(handle, FORWARD, BACKWARD), strafe = axis(handle, RIGHT, LEFT);
        boolean sprint = sprintRequested(forward);
        boolean jump = controls.down(handle, JUMP);
        if (sprintReleased && sprint && forward > 0)
            pendingCombatInputs.add(new Intent(++sequence, camera.yaw(), camera.pitch(), forward, strafe,
                    jump, false, false, use, slot));
        pendingCombatInputs.add(new Intent(++sequence, camera.yaw(), camera.pitch(), forward, strafe,
                jump, sprint, attack, use, slot, attack ? displayedTarget() : null));
        sprintReleased = false;
        flushCombatInputs();
    }

    private AttackView displayedTarget() {
        return displayedTarget(camera, view.kit().rules(), localPose, remoteActors, terrain);
    }

    static AttackView displayedTarget(Camera camera, CombatRules rules, PoseView localPose,
                                      List<ArenaMotionTimeline.Presented> remoteActors) {
        return displayedTarget(camera, rules, localPose, remoteActors, ArenaMotionTimeline.TERRAIN);
    }

    static AttackView displayedTarget(Camera camera, CombatRules rules, PoseView localPose,
                                      List<ArenaMotionTimeline.Presented> remoteActors, Arena terrain) {
        // The camera can already have moved in response to a hit. Select what its crosshair sees.
        Vector3f position = camera.position();
        Vec3 eye = new Vec3(position.x, position.y, position.z);
        Vec3 ray = CombatGeometry.direction(camera.yaw(), camera.pitch()).scale(rules.reach());
        ArenaMotionTimeline.Presented target = null;
        double closest = CombatGeometry.firstBlock(terrain, eye, ray);
        for (var candidate : remoteActors) {
            if (candidate.fighter().health() <= 0) continue;
            double hit = CombatGeometry.firstMeleeBody(eye, ray,
                    new Vec3(candidate.x(), candidate.y(), candidate.z()));
            if (hit < closest) {
                closest = hit;
                target = candidate;
            }
        }
        if (target == null || Math.abs(target.pose().tick() - localPose.tick()) > 3)
            return new AttackView(0, localPose, localPose);
        return new AttackView(target.fighter().id(), localPose, target.pose());
    }

    private boolean flushCombatInputs() {
        while (!pendingCombatInputs.isEmpty()) {
            if (!submitInput(pendingCombatInputs.peek())) return false;
            pendingCombatInputs.remove();
        }
        return true;
    }

    private void selectSlot(int index, long now) {
        slot = index;
        hand.select(selectedItem(), now);
    }

    private Item selectedItem() {
        return view != null && slot >= 0 && slot < view.inventory().size()
                ? view.inventory().get(slot).item() : Item.EMPTY;
    }

    private boolean useHeld() {
        long w = window.getHandle();
        return !inventory && (scriptedUse != null ? scriptedUse
                : glfwGetWindowAttrib(w, GLFW_FOCUSED) == GLFW_TRUE && controls.down(w, PLACE));
    }

    private boolean canAnimateCombat() {
        if (view == null || view.phase() != Phase.FIGHT || view.spectatorId() == id
                || remote != null && !remote.connected()) return false;
        Fighter actor = self();
        return actor != null && actor.health() > 0;
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
        inventory = open; source = -1; firstMouse = true; usePressed = sprintReleased = false;
        pendingCombatInputs.clear();
        if (open) sprintToggle.suspend();
        int inventoryCursor = glfwGetPlatform() == GLFW_PLATFORM_WIN32 ? GLFW_CURSOR_CAPTURED : GLFW_CURSOR_NORMAL;
        glfwSetInputMode(window.getHandle(), GLFW_CURSOR, open ? inventoryCursor : GLFW_CURSOR_DISABLED);
    }

    private int hoveredInventorySlot() {
        if (view == null || view.inventory().size() != 36) return -1;
        int[] screen = window.getSize();
        float x = (float) mouseX * width / Math.max(1, screen[0]);
        float y = (float) mouseY * height / Math.max(1, screen[1]);
        float left = (width - 540) / 2f, top = (height - 184) / 2f;
        if (x < left || x >= left + 540 || y < top || y >= top + 184) return -1;
        return ((int) (y - top) / 46) * 9 + (int) (x - left) / 60;
    }

    private void selectInventory(boolean shift) {
        int selection = hoveredInventorySlot();
        if (selection < 0) return;
        if (shift) {
            if (remote != null) remote.quickMove(selection);
            else offline.quickMove(id, selection);
            source = -1;
        } else if (source < 0) {
            source = selection;
        } else {
            swapInventory(source, selection);
        }
    }

    private void swapInventory(int from, int to) {
        if (from >= 0 && from != to) {
            if (remote != null) remote.swap(from, to);
            else offline.swap(id, from, to);
        }
        source = -1;
    }

    private View snapshot() { return remote == null ? offline.view(id) : remote.view(); }

    private void sendInput(FrameObserver observer, long now) {
        boolean enabled = !inventory && glfwGetWindowAttrib(window.getHandle(), GLFW_FOCUSED) == GLFW_TRUE;
        long w = window.getHandle();
        float forward = enabled ? axis(w, FORWARD, BACKWARD) : 0;
        float strafe = enabled ? axis(w, RIGHT, LEFT) : 0;
        boolean jump = enabled && controls.down(w, JUMP);
        boolean sprint = enabled && sprintRequested(forward);
        boolean use = enabled && (controls.down(w, PLACE) || selectedItem() != Item.SWORD && usePressed);
        if (!enabled || !canAnimateCombat()) {
            sprintReleased = false;
            pendingCombatInputs.clear();
        }
        if (!flushCombatInputs()) return;
        // Preserve a release and repress that both happened between movement samples.
        if (sprintReleased && sprint && forward > 0) {
            if (!submitInput(new Intent(++sequence, camera.yaw(), camera.pitch(), forward, strafe,
                    jump, false, false, use, slot))) return;
            sprintReleased = false;
        }
        Intent intent = new Intent(++sequence, camera.yaw(), camera.pitch(), forward, strafe,
                jump, sprint, false, use, slot);
        if (observer != null) {
            Intent controlsIntent = intent;
            intent = observer.input(intent, view);
            camera.setYaw(intent.yaw());
            camera.setPitch(intent.pitch());
            selectSlot(intent.slot(), now);
            boolean startingUse = intent.use() && !previousIntentUse;
            previousIntentUse = intent.use();
            scriptedUse = intent == controlsIntent ? null : intent.use();
            if (scriptedUse != null && !inventory && canAnimateCombat()) {
                Item item = selectedItem();
                if (startingUse && !usePressed) hand.use(item, now);
                if (intent.attack()) {
                    hand.attack(item, scriptedUse, now);
                    // Exercise the rendered crosshair and historical pose check on native playtest swings.
                    if (intent.attackView() == null) {
                        intent = new Intent(intent.sequence(), intent.yaw(), intent.pitch(), intent.forward(), intent.strafe(),
                                intent.jump(), intent.sprint(), true, intent.use(), intent.slot(), displayedTarget());
                    }
                }
            }
        }
        if (submitInput(intent)) {
            if (observer != null && intent.attack()) observer.submitted(now, intent);
            usePressed = sprintReleased = false;
        }
    }

    private boolean submitInput(Intent intent) {
        if (remote != null) {
            if (!remote.input(intent)) return false;
        } else {
            offline.input(id, intent);
        }
        boolean sprint = intent.sprint() && intent.forward() > 0;
        motion.submitSprintInput(id, sprint, sprint && canPreviewSprintClick(intent), System.nanoTime());
        return true;
    }

    private boolean canPreviewSprintClick(Intent intent) {
        var target = intent.attackView();
        if (!intent.attack() || intent.use() || target == null || target.targetId() == 0
                || inventory || !canAnimateCombat() || view.food() <= 6) return false;
        var actor = self();
        return actor.lastHorizontalWallTick() != view.tick()
                && (!showTeams() || view.fighters().stream().anyMatch(fighter ->
                        fighter.id() == target.targetId() && fighter.side() != actor.side()));
    }

    private int axis(long window, Controls.Action positive, Controls.Action negative) {
        return (controls.down(window, positive) ? 1 : 0) - (controls.down(window, negative) ? 1 : 0);
    }

    private Fighter self() { return view.fighters().stream().filter(f -> f.id() == id).findFirst().orElse(null); }

    private void updatePresentation(long now, FrameObserver observer) {
        motion.observe(view.tick(), view.fighters(), now, id, view.lastHit());
        var frame = motion.frame(now);
        remoteActors.clear();
        for (Fighter fighter : view.fighters()) {
            if (fighter.id() == id) continue;
            var candidate = motion.sample(fighter.id(), frame.remote());
            if (candidate != null && candidate.visible()) remoteActors.add(candidate);
        }
        Fighter actor = self();
        boolean spectator = actor == null || view.spectatorId() == id;
        hitFeedback = spectator ? ArenaMotionTimeline.HitFeedback.NONE : motion.hitFeedback(id, frame.local());
        var gallery = terrain.spectator();
        boolean respawn = !positioned || spectator != wasSpectator || view.phase() == Phase.COUNTDOWN && lastPhase != Phase.COUNTDOWN;
        if (respawn) {
            camera.setYaw(spectator ? gallery.yaw() : actor.yaw());
            camera.setPitch(spectator ? gallery.pitch() : actor.pitch());
            hand.reset(spectator ? Item.EMPTY : selectedItem(), now);
        }
        var local = spectator || respawn ? null : motion.sampleLocal(id, frame.local(), now, factions, view.phase() == Phase.FIGHT);
        localPose = local == null ? new PoseView(view.tick(), view.tick(), view.tick()) : local.pose();
        Vec3 feet = spectator ? new Vec3(gallery.x(), gallery.y(), gallery.z())
                : local == null ? new Vec3(actor.x(), actor.y(), actor.z()) : local.point();
        camera.position().set((float) feet.x(), (float) (feet.y() + PlayerDimensions.EYE_HEIGHT), (float) feet.z());
        if (observer != null) {
            observer.sampled(now, view, feet.x(), feet.y(), feet.z(), local == null ? feet : local.confirmedPoint());
            Vec3 sprintOffset = local == null ? null : motion.predictedSprintOffset();
            if (sprintOffset != null) observer.sprintClickPreview(now, view, sprintOffset.x(), sprintOffset.z());
        }
        positioned = true; wasSpectator = spectator; lastPhase = view.phase();
    }

    private void render(World world, ShaderProgram shader, RenderPipeline pipeline, VoxelModelRenderer models,
                        PlayerModel avatars, PracticeHitboxes hitboxes, long now) {
        Vector3f eye = camera.position();
        projection.identity().perspective((float) Math.toRadians(73), (float) width / height, .06f, 180);
        Matrix4f matrix = practiceViewMatrix(camera, hitFeedback.rollRadians());
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
        for (var actor : remoteActors) {
            if (!frustum.testAab(actor.x() - 1, actor.y() - .2f, actor.z() - 1,
                    actor.x() + 1, actor.y() + 2.5f, actor.z() + 1)) continue;
            Fighter fighter = actor.fighter();
            var pose = new Protocol.Pose(fighter.id(), actor.x(), actor.y(), actor.z(), actor.yaw(),
                    actor.pitch(), actor.walkPhase(), actor.walkAmount(), false,
                    actor.swingProgress(), renderItem(fighter.held()));
            avatars.renderPracticeAvatar(pose, shader, models,
                    fighter.side() < 0 ? -1 : TEAM_JERSEYS[fighter.side()], actor.hurt());
        }
        for (var missile : view.missiles()) {
            var drop = new ItemDrop((int) missile.id(), renderItem(missile.item()), 1, missile.x(), missile.y(), missile.z());
            avatars.renderDrop(drop, view.tick() / 20f, shader);
        }
        if (showHitboxes && !remoteActors.isEmpty()) hitboxes.render(clipFromWorld, remoteActors);
        pipeline.finish();
        Fighter actor = self();
        if (actor != null && actor.health() > 0 && view.spectatorId() != id && view.inventory().size() == 36) {
            glClear(GL_DEPTH_BUFFER_BIT);
            pipeline.prepareHeld(shader);
            shader.setInt("uFog", 0);
            shader.setInt("uShadowEnabled", 0);
            shader.setInt("uHeld", 1);
            shader.setInt("uLightingEnabled", 0);
            shader.setVector3("uLightDirection", -.35f, -.70f, -.62f);
            shader.setMatrix4("uProjection", new Matrix4f().perspective((float) Math.toRadians(70), (float) width / height, .03f, 10));
            shader.setMatrix4("uView", new Matrix4f());
            var held = hand.pose(selectedItem(), canAnimateCombat() && useHeld(), actor.moving(), now);
            avatars.renderPracticeFirstPerson(held.walkPhase(), held.walkAmount(), held.swing(),
                    held.equipDrop(), held.blocking(), renderItem(held.item()), 0xffffff, shader, models);
            shader.setInt("uHeld", 0);
        }
    }

    static Matrix4f practiceViewMatrix(Camera camera, float rollRadians) {
        // Rotate in eye space so the projected center, authoritative facing, and crosshair all stay aligned.
        return camera.createViewMatrix().rotateLocalZ(rollRadians);
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
        if (!inventory) {
            ui.rectangle(width / 2f - 6, height / 2f, 12, 2, 1, 1, 1, .85f);
            ui.rectangle(width / 2f - 1, height / 2f - 5, 2, 12, 1, 1, 1, .85f);
            if (hitFeedback.dealt() > 0) {
                float x = width / 2f, y = height / 2f;
                float alpha = hitFeedback.dealt();
                ui.line(x - 10, y - 10, x - 5, y - 5, 2, 1, .45f, .20f, alpha);
                ui.line(x + 10, y - 10, x + 5, y - 5, 2, 1, .45f, .20f, alpha);
                ui.line(x - 10, y + 10, x - 5, y + 5, 2, 1, .45f, .20f, alpha);
                ui.line(x + 10, y + 10, x + 5, y + 5, 2, 1, .45f, .20f, alpha);
            }
        }
        if (hitFeedback.received() > 0 && !view.kit().rules().knockbackOnly()) {
            float alpha = .75f * hitFeedback.received();
            ui.rectangle(0, 0, width, 8, 1, .16f, .12f, alpha);
            ui.rectangle(0, height - 8, width, 8, 1, .16f, .12f, alpha);
        }
        if (showTeams()) drawTeamLabels(ui);
        if (!inventory && view.kit() == Kit.VAULT_BATTLES && view.phase() == Phase.FIGHT) drawVaultMarker(ui);
        ui.rectangle(16, 13, 325, 59, .031f, .05f, .065f, .90f);
        String heading = terrain.title() + "  /  " + (factions ? "Faction Arena" : view.kit().title());
        ui.text(heading, 27, 24, Math.min(1.4f, 303f / ui.textWidth(heading, 1f)));
        if (showHitboxes) {
            String label = "Hitboxes  /  " + (hitboxShortcutNeedsControl ? "Ctrl+F12" : "F12");
            float labelWidth = ui.textWidth(label, 1.1f) + 20;
            ui.rectangle(width - labelWidth - 16, 13, labelWidth, 24, .031f, .05f, .065f, .90f);
            ui.text(label, width - labelWidth - 6, 20, 1.1f, .72f, 1, .72f, 1);
        }
        if (offline != null && !inventory) drawSparringStats(ui);
        String state = stateText(actor);
        ui.text(state, 27, 47, 1.05f, .66f, .84f, .90f, 1);
        if (factions) {
            String actions = "Z/X: team  F1-F5: class  R: ability  C: resupply  V: raid  L: logout";
            ui.rectangle(16, height - 13, ui.textWidth(actions, 1.05f) + 12, 13, .031f, .05f, .065f, .90f);
            ui.text(actions, 22, height - 11, 1.05f);
        }
        int line = 0;
        if (view.kit().teamBased()) {
            drawTeamCounts(ui, actor);
            line++;
        }
        for (String status : view.status()) {
            ui.rectangle(16, 76 + line * 19, ui.textWidth(status, 1f) + 22, 19, .031f, .05f, .065f, .86f);
            float y = 81 + line++ * 19;
            if (view.kit() == Kit.VAULT_BATTLES && line == 2) ui.text(status, 27, y, 1, 1, .83f, .42f, 1);
            else ui.text(status, 27, y, 1);
        }
        String sprintStatus = sprintToggle.status();
        if (actor != null && actor.health() > 0 && !sprintStatus.isEmpty()) {
            float top = 76 + line * 19;
            ui.rectangle(16, top, ui.textWidth(sprintStatus, 1.35f) + 22, 24, .031f, .05f, .065f, .94f);
            ui.text(sprintStatus, 27, top + 7, 1.35f, .61f, .94f, .76f, 1);
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

    private boolean showTeams() {
        return factions || view.kit().teamBased();
    }

    private void drawTeamCounts(Overlay ui, Fighter actor) {
        int[] living = new int[2];
        for (Fighter fighter : view.fighters())
            if (fighter.side() >= 0 && fighter.health() > 0) living[fighter.side()]++;
        String[] labels = new String[living.length];
        float panelWidth = 4;
        for (int side = 0; side < labels.length; side++) {
            labels[side] = view.kit().teamName(side) + " " + living[side] + "/" + view.kit().teamSize()
                    + (actor != null && actor.side() == side ? " (you)" : "");
            panelWidth += ui.textWidth(labels[side], 1f) + 18;
        }
        float x = 27;
        ui.rectangle(16, 76, panelWidth, 19, .031f, .05f, .065f, .90f);
        for (int side = 0; side < labels.length; side++) {
            String team = labels[side];
            int color = TEAM_JERSEYS[side];
            ui.text(team, x, 81, 1f, (color >> 16 & 255) / 255f, (color >> 8 & 255) / 255f,
                    (color & 255) / 255f, 1);
            x += ui.textWidth(team, 1f) + 18;
        }
    }

    private void drawVaultMarker(Overlay ui) {
        var stage = view.vaultStage();
        if (stage == PracticeMatch.VaultStage.CAPTURED) return;
        boolean outer = stage == PracticeMatch.VaultStage.OUTER_RING;
        Vec3 marker = outer ? VaultBattlefield.breachMarker() : VaultBattlefield.coreMarker();
        // Keep the waypoint readable over the route and intervening defenses.
        Vector4f clip = clipFromWorld.transform(new Vector4f((float) marker.x(), (float) marker.y() + 3.5f,
                (float) marker.z(), 1));
        if (clip.w <= 0 || Math.abs(clip.x) > .90f * clip.w || Math.abs(clip.y) > .74f * clip.w
                || Math.abs(clip.z) > clip.w) return;
        float x = (clip.x / clip.w * .5f + .5f) * width;
        float y = (.5f - clip.y / clip.w * .5f) * height;
        Vector3f eye = camera.position();
        int distance = (int) Math.round(Math.hypot(marker.x() - eye.x, marker.z() - eye.z));
        String text = (outer ? "Outer Ring" : "Vault Core") + "  " + distance + " blocks";
        float textWidth = ui.textWidth(text, 1.05f);
        ui.rectangle(x - textWidth / 2 - 8, y - 23, textWidth + 16, 19, .031f, .05f, .065f, .92f);
        ui.text(text, x - textWidth / 2, y - 18, 1.05f, 1, .83f, .42f, 1);
        ui.line(x - 4, y, x, y + 4, 2, 1, .83f, .42f, 1);
        ui.line(x, y + 4, x + 4, y, 2, 1, .83f, .42f, 1);
    }

    private void drawSparringStats(Overlay ui) {
        float top = showHitboxes ? 44 : 13;
        int binding = controls.code(SPARRING_STATS);
        String key = (binding == GLFW_KEY_ENTER ? "Ctrl+" : "") + Controls.keyName(binding);
        if (!showSparringStats) {
            if (view.phase() == Phase.WAITING) {
                String hint = key + "  Partner stats";
                float boxWidth = ui.textWidth(hint, 1.1f) + 20;
                ui.rectangle(width - boxWidth - 16, top, boxWidth, 24, .031f, .05f, .065f, .90f);
                ui.text(hint, width - boxWidth - 6, top + 7, 1.1f);
            }
            return;
        }
        Fighter actor = self();
        Fighter partner = view.fighters().stream().filter(fighter -> fighter.id() != id
                && (!view.kit().teamBased() || actor != null && fighter.side() == actor.side())).findFirst().orElse(null);
        if (partner == null) return;

        boolean lab = view.kit().rules().knockbackOnly();
        float left = width - 276;
        ui.rectangle(left, top, 260, lab ? 112 : 166, .031f, .05f, .065f, .94f);
        ui.text(partner.name(), left + 10, top + 9, 1.3f);
        ui.text(key, left + 250 - ui.textWidth(key, 1f), top + 11, 1f, .66f, .84f, .90f, 1);
        ui.rectangle(left + 10, top + 28, 240, 1, .22f, .33f, .41f, 1);
        sparringStat(ui, left, top + 37, "Hits", Integer.toString(partner.hits()));
        sparringStat(ui, left, top + 55, "Best combo", Integer.toString(partner.combo()));
        sparringStat(ui, left, top + 73, "H / V (blocks per tick)", String.format(Locale.ROOT, "%.2f / %+.2f",
                Math.hypot(partner.vx(), partner.vz()), partner.vy()));
        sparringStat(ui, left, top + 91, "Held item",
                partner.held() == Item.EMPTY ? "Empty hand" : PracticeItemIcons.name(partner.held()));
        if (!lab) {
            sparringStat(ui, left, top + 109, "Health", String.format(Locale.ROOT, "%.2f / 10 hearts", partner.health() / 2));
            sparringStat(ui, left, top + 127, "Potions used", Integer.toString(partner.used()));
            sparringStat(ui, left, top + 145, "Potions left", Integer.toString(partner.remaining()));
        }
    }

    private static void sparringStat(Overlay ui, float left, float top, String label, String value) {
        ui.text(label, left + 10, top, 1.05f, .66f, .84f, .90f, 1);
        ui.text(value, left + 250 - ui.textWidth(value, 1.05f), top, 1.05f);
    }

    private String stateText(Fighter actor) {
        if (remote != null && !remote.connected()) return "Disconnected  /  Esc to exit";
        if (view.spectatorId() == id) {
            String state = view.kit().teamBased() && actor != null && actor.health() <= 0
                    ? "Eliminated  |  Spectating" : "Spectating";
            return view.kit() == Kit.VAULT_BATTLES && view.phase() == Phase.FIGHT
                    ? state + "  /  " + vaultClock(view.ticksLeft()) : state;
        }
        return switch (view.phase()) {
            case WAITING -> view.kit().teamBased() && view.fighters().size() < view.kit().fighterCapacity()
                    ? "Waiting for players  /  " + view.fighters().size() + "/" + view.kit().fighterCapacity()
                    : actor != null && actor.ready() ? "Ready  |  Enter: cancel" : "Enter: ready  |  E: edit kit";
            case COUNTDOWN -> "Get ready";
            case FIGHT -> fightState() + "  |  " + Controls.keyName(controls.code(AUTO_CLICK)) + "  Auto-click "
                    + (autoClicker.enabled() ? "ON (9 CPS)" : "off");
            case RESULT -> view.ticksLeft() > 0 ? "Round complete" : "Enter: ready for another round";
        };
    }

    private String fightState() {
        if (view.kit() == Kit.VAULT_BATTLES) return "Siege  /  " + vaultClock(view.ticksLeft());
        if (view.kit().rules().knockbackOnly()) return "Trading";
        return "Fight  /  " + view.ticksLeft() / PracticeMatch.TICKS_PER_SECOND + "s";
    }

    private static String vaultClock(int ticks) {
        int seconds = (ticks + PracticeMatch.TICKS_PER_SECOND - 1) / PracticeMatch.TICKS_PER_SECOND;
        return seconds / 60 + ":" + (seconds % 60 < 10 ? "0" : "") + seconds % 60;
    }

    private String title() {
        if (factions) return "Voxel One | Faction Arena";
        var kit = snapshot().kit();
        return switch (kit) {
            case KNOCKBACK_LAB, TEAMFIGHTING_LAB, TEAMFIGHTING_30V30, VAULT_BATTLES -> "Voxel One | " + kit.title();
            default -> "Voxel One | PotPvP Practice";
        };
    }

    private String resultText() {
        if (view.winner() == 0) return "Draw";
        if (view.kit().teamBased()) {
            return view.kit().teamName(view.winningTeam()) + (view.kit() == Kit.VAULT_BATTLES ? " win" : " wins");
        }
        return view.fighters().stream().filter(f -> f.id() == view.winner()).findFirst()
                .map(f -> (f.side() < 0 ? f.name() : TEAM_NAMES[f.side()]) + " wins").orElse("Faction victory");
    }

    private void drawTeamLabels(Overlay ui) {
        for (var actor : remoteActors) {
            Fighter fighter = actor.fighter();
            if (fighter.side() < 0 || !visible(actor)) continue;
            Vector4f clip = clipFromWorld.transform(new Vector4f(actor.x(), actor.y() + PlayerDimensions.NAMEPLATE_HEIGHT, actor.z(), 1));
            if (clip.w <= 0 || Math.abs(clip.x) > clip.w || Math.abs(clip.y) > clip.w || Math.abs(clip.z) > clip.w) continue;
            float x = (clip.x / clip.w * .5f + .5f) * width;
            float y = (.5f - clip.y / clip.w * .5f) * height;
            String text = factions && !fighter.role().isEmpty() ? fighter.name() + " / " + fighter.role() : fighter.name();
            float boxWidth = ui.textWidth(text, 1.15f) + 14;
            int color = TEAM_JERSEYS[fighter.side()];
            ui.rectangle(x - boxWidth / 2, y - 3, boxWidth, 16, .031f, .05f, .065f, .83f);
            ui.text(text, x - boxWidth / 2 + 7, y, 1.15f, (color >> 16 & 255) / 255f,
                    (color >> 8 & 255) / 255f, (color & 255) / 255f, 1);
        }
    }

    private boolean visible(ArenaMotionTimeline.Presented fighter) {
        Vector3f eye = camera.position();
        Vector3f offset = new Vector3f(fighter.x(), fighter.y() + 1.36f, fighter.z()).sub(eye);
        int count = (int) Math.ceil(offset.length() * 5);
        // Team colors still identify distant fighters on the battlefield without stacking nameplates.
        int maxSamples = terrain == PracticeTerrain.COURTYARD ? 220 : 100;
        if (count < 1 || count > maxSamples) return false;
        for (int i = 1; i < count; i++) {
            float t = i / (float) count;
            if (terrain.block((int) Math.floor(eye.x + offset.x * t),
                    (int) Math.floor(eye.y + offset.y * t),
                    (int) Math.floor(eye.z + offset.z * t)) != Blocks.AIR) return false;
        }
        return true;
    }

    private void drawPlayerHud(Overlay ui, Fighter actor) {
        float x = (width - 540) / 2f, y = height - 74;
        for (int i = 0; i < 9; i++)
            drawSlot(ui, view.inventory().get(i), hotbarLabels[i], x + i * 60, y, i == slot, false);
        if (view.kit().rules().knockbackOnly()) {
            String summary = String.format(Locale.ROOT, "HITS %d   BEST COMBO %d   H %.2f   V %+.2f  BLOCKS/TICK",
                    actor.hits(), actor.combo(), Math.hypot(actor.vx(), actor.vz()), actor.vy());
            ui.rectangle(x, y - 30, 540, 29, .031f, .05f, .065f, .92f);
            ui.text(summary, x + 9, y - 20, Math.min(1.1f, 522f / ui.textWidth(summary, 1f)));
            return;
        }
        String summary = String.format(Locale.ROOT, "FOOD %d   HITS %d   COMBO %d   POTS %d / %d",
                view.food(), actor.hits(), actor.combo(), actor.used(), actor.remaining());
        ui.rectangle(x, y - 30, 540, 29, .031f, .05f, .065f, .92f);
        drawHealthHearts(ui, x + 9, y - 25, actor.health());
        float summaryScale = Math.min(1.15f, 307f / Math.max(1, ui.textWidth(summary, 1f)));
        ui.text(summary, x + 224, y - 20, summaryScale);
        int armor = view.durability().stream().mapToInt(Integer::intValue).sum();
        String held = PracticeItemIcons.name(selectedItem());
        String timers = (held.isEmpty() ? "" : held + "   ") + "Armor " + armor
                + (view.cooldown() > 0 ? "   Pearl " + (view.cooldown() + 19) / 20 + "s" : "");
        for (var effect : view.effects().entrySet()) timers += "   " + effect.getKey().name() + " " + (effect.getValue() + 19) / 20 + "s";
        float timerScale = Math.min(1.02f, 532f / Math.max(1, ui.textWidth(timers, 1f)));
        ui.rectangle(x, y + 48, ui.textWidth(timers, timerScale) + 8, 13, .031f, .05f, .065f, .90f);
        ui.text(timers, x + 4, y + 51, timerScale);
        if (view.phase() == Phase.RESULT && view.kit().teamSize() <= 2) {
            for (Fighter fighter : view.fighters()) {
                String result = String.format(Locale.ROOT, "%s  HP %.1f  Hits %d  Combo %d  Pots used %d  Left %d",
                        fighter.name(), fighter.health(), fighter.hits(), fighter.combo(), fighter.used(), fighter.remaining());
                ui.text(result, x, y - 54 - view.fighters().indexOf(fighter) * 19, 1.1f);
            }
        }
    }

    private static final String[] HEART = {
            ".oo...oo.",
            "ohro.orro",
            "ohrrrrrro",
            "orrrrrrro",
            "orrrrrrdo",
            ".orrrddo.",
            "..orrdo..",
            "...odo...",
            "....o...."
    };

    private static void drawHealthHearts(Overlay ui, float x, float y, float health) {
        for (int heart = 0; heart < 10; heart++) {
            float fill = Math.max(0, Math.min(1, (health - 2 * heart) / 2));
            float rightEdge = .5f + fill * (HEART[0].length() - 1);
            for (int row = 0; row < HEART.length; row++) {
                for (int col = 0; col < HEART[row].length(); col++) {
                    char pixel = HEART[row].charAt(col);
                    if (pixel == '.') continue;
                    float left = x + 21 * heart + 2 * col;
                    float top = y + 2 * row;
                    if (pixel == 'o') {
                        heartPixel(ui, left, top, 2, 0x576276);
                        continue;
                    }
                    float filled = 2 * Math.max(0, Math.min(1, rightEdge - col));
                    int empty = pixel == 'h' ? 0x303d50 : pixel == 'd' ? 0x172330 : 0x233142;
                    int full = pixel == 'h' ? 0xffa39c : pixel == 'd' ? 0xa9253e : 0xec4558;
                    if (filled > 0) heartPixel(ui, left, top, filled, full);
                    if (filled < 2) heartPixel(ui, left + filled, top, 2 - filled, empty);
                }
            }
        }
    }

    private static void heartPixel(Overlay ui, float x, float y, float width, int rgb) {
        ui.rectangle(x, y, width, 2, ((rgb >>> 16) & 255) / 255f, ((rgb >>> 8) & 255) / 255f,
                (rgb & 255) / 255f, 1);
    }

    private void drawInventory(Overlay ui) {
        float x = (width - 540) / 2f, y = (height - 184) / 2f;
        ui.rectangle(x - 12, y - 46, 564, 242, .031f, .05f, .065f, .96f);
        ui.text(view.phase() == Phase.WAITING ? "Kit layout" : "Inventory", x, y - 33, 1.6f);
        ui.text("Click two slots  |  Shift-click to move  |  Hotbar key to swap hovered slot", x, y - 15, 1.05f);
        int hovered = hoveredInventorySlot();
        if (hovered >= 0) {
            String name = PracticeItemIcons.name(view.inventory().get(hovered).item());
            if (!name.isEmpty()) ui.text(name, x + 540 - ui.textWidth(name, 1.2f), y - 32, 1.2f);
        }
        for (int i = 0; i < 36; i++)
            drawSlot(ui, view.inventory().get(i), i < 9 ? hotbarLabels[i] : null,
                    x + i % 9 * 60, y + i / 9 * 46, i == source, i == hovered);
    }

    private static void drawSlot(Overlay ui, Stack stack, String key, float x, float y, boolean selected, boolean hovered) {
        ui.rectangle(x + 1, y + 1, 58, 43, selected ? .27f : hovered ? .15f : .07f,
                selected ? .48f : hovered ? .23f : .12f, hovered ? .29f : .17f, .95f);
        if (key != null) {
            float scale = Math.min(.9f, 48f / Math.max(1, ui.textWidth(key, 1)));
            ui.text(key, x + 4, y + 3, scale, .70f, .79f, .87f, 1);
        }
        if (stack.count() == 0) return;
        PracticeItemIcons.draw(ui, stack.item(), x + 14, y + 9, 32);
        if (stack.count() > 1) {
            String count = Integer.toString(stack.count());
            float countX = x + 55 - ui.textWidth(count, 1.1f);
            ui.rectangle(countX - 2, y + 31, x + 57 - countX, 12, .025f, .04f, .06f, .95f);
            ui.text(count, countX, y + 33, 1.1f);
        }
    }
}
