import dev.jayms.battle.*;
import dev.jayms.battle.BattleArena.*;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;
import java.nio.*;
import java.nio.file.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.util.*;
import static org.lwjgl.opengl.GL33.*;
import static org.lwjgl.glfw.GLFW.*;

/** Real native input, plus explicitly labelled rendered combat fixtures for symmetric breakup. */
public final class BattleSmoke {
    static volatile BattleApp active;
    static volatile Throwable failure;
    static volatile String capture, captured;
    static volatile int fixture;
    static volatile int fixtureDone;
    static volatile float px, pz, targetX, targetY, targetZ, eyeX, eyeY, eyeZ, yaw, pitch;
    static volatile int opponentHits, playerHits, playerKnockdowns, opponentKnockdowns, bodies, joints, resets;
    static volatile int playerLost, opponentLost;
    static volatile int playerSwings;
    static volatile State playerState, opponentState;
    static volatile boolean intactDynamicMotion;
    static Path out;
    static String renderer;
    static ArrayList<Double> frameMs = new ArrayList<>(), simulationMs = new ArrayList<>();
    static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    static String x(String... args) throws Exception {
        var command = new ArrayList<String>(); command.add("xdotool"); command.addAll(List.of(args));
        var p = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        String result = new String(p.getInputStream().readAllBytes()).trim();
        require(p.waitFor() == 0, "X11 input " + args[0]); return result;
    }
    static void key(String window, String key) throws Exception {
        long seq = active.renderedInputSequence;
        x("key", "--clearmodifiers", "--window", window, key);
        long end = System.currentTimeMillis() + 10000;
        while (active.renderedInputSequence <= seq && System.currentTimeMillis() < end) Thread.sleep(20);
        require(active.renderedInputSequence > seq, "Rendered key response: " + key); Thread.sleep(60);
    }
    static void shot(String file) throws Exception {
        capture = file;
        for (int i = 0; i < 300 && !file.equals(captured); i++) Thread.sleep(20);
        require(file.equals(captured), "Screenshot " + file);
    }
    static void fixture(int number) throws Exception {
        fixture = number;
        for (int i = 0; i < 300 && fixtureDone != number; i++) Thread.sleep(20);
        require(fixtureDone == number, "Rendered fixture " + number);
    }
    static void aim() throws Exception {
        for (int i = 0; i < 3; i++) {
            float desiredYaw = (float)Math.atan2(targetX - eyeX, targetZ - eyeZ);
            float difference = desiredYaw - yaw;
            while (difference > Math.PI) difference -= Math.PI * 2;
            while (difference < -Math.PI) difference += Math.PI * 2;
            float dy = targetY - eyeY, dx = targetX - eyeX, dz = targetZ - eyeZ;
            float desiredPitch = (float)-Math.atan2(dy, Math.sqrt(dx * dx + dz * dz));
            if (Math.abs(difference) > .025f) {
                String turn = difference > 0 ? "Left" : "Right";
                x("keydown", turn); Thread.sleep(Math.min(400, Math.max(20, (long)(Math.abs(difference) / 1.2f * 1000)))); x("keyup", turn);
            }
            float vertical = desiredPitch - pitch;
            if (Math.abs(vertical) > .02f) {
                String turn = vertical > 0 ? "Down" : "Up";
                x("keydown", turn); Thread.sleep(Math.min(300, Math.max(20, (long)(Math.abs(vertical) / .65f * 1000)))); x("keyup", turn);
            }
            Thread.sleep(60);
        }
    }
    static void capture(BattleApp app, String file) throws Exception {
        ByteBuffer data = MemoryUtil.memAlloc(app.width * app.height * 3);
        try {
            glReadPixels(0, 0, app.width, app.height, GL_RGB, GL_UNSIGNED_BYTE, data);
            var image = new BufferedImage(app.width, app.height, BufferedImage.TYPE_INT_RGB); int colored = 0;
            for (int y = 0; y < app.height; y++) for (int xx = 0; xx < app.width; xx++) {
                int k = (y * app.width + xx) * 3;
                int r = data.get(k) & 255, g = data.get(k + 1) & 255, b = data.get(k + 2) & 255;
                image.setRGB(xx, app.height - y - 1, r << 16 | g << 8 | b);
                if (y > 100 && y < app.height - 120 && (r > 120 || b > 150)) colored++;
            }
            ImageIO.write(image, "png", out.resolve(file).toFile());
            require(colored > 100, "Fighters visible in " + file + ": colored pixels=" + colored);
            require(glGetError() == GL_NO_ERROR, "No GL error at " + file);
        } finally { MemoryUtil.memFree(data); }
    }
    public static void main(String[] args) throws Exception {
        out = Path.of(args[0]); Files.createDirectories(out);
        BattleApp.validateArguments(new String[]{"--game", "battle", "--offline"});
        var app = new BattleApp();
        Thread input = new Thread(() -> {
            try {
                while (active == null) Thread.sleep(20);
                String window = Long.toString(org.lwjgl.glfw.GLFWNativeX11.glfwGetX11Window(active.window));
                x("windowfocus", window); Thread.sleep(200); key(window, "r");
                shot("battle-ready.png");
                float start = pz;
                x("keydown", "--window", window, "w");
                long walkEnd = System.currentTimeMillis() + 6000;
                while (pz > start - .45f && System.currentTimeMillis() < walkEnd) Thread.sleep(20);
                x("keyup", "--window", window, "w");
                require(pz < start - .3f, "W moves human toward opponent: start=" + start + " z=" + pz + " yaw=" + yaw + " state=" + playerState);
                key(window, "F10");
                int initialOpponentHits = opponentHits;
                for (int i = 0; i < 25 && playerHits == 0; i++) {
                    aim(); x("click", "1"); Thread.sleep(280);
                }
                require(playerHits > 0, "Native mouse jab hits a body part");
                shot("battle-fight.png");
                for (int i = 0; i < 15 && opponentKnockdowns == 0; i++) {
                    aim(); x("click", "3"); Thread.sleep(400);
                }
                require(opponentKnockdowns > 0, "Native heavy hit knocks partner down intact: player=" + playerState + " opponent=" + opponentState + " hits=" + playerHits);
                for (int i = 0; i < 100 && !intactDynamicMotion; i++) Thread.sleep(20);
                require(intactDynamicMotion, "Intact knockdown advances jointed dynamic bodies");
                Thread.sleep(350);
                shot("battle-knockdown.png"); Thread.sleep(800);
                require(opponentHits > initialOpponentHits || opponentHits > 0, "Partner fights back through production AI");
                key(window, "r");
                // Labelled controlled fixtures run the SAME production impacts/physics/render path.
                fixture(1); shot("battle-player-limb.png");
                for (int i = 0; i < 200 && playerState != State.READY; i++) Thread.sleep(20);
                require(playerLost == 1 && playerState == State.READY, "Player arm detachment persists after recovery");
                int swings = playerSwings;
                x("click", "1"); Thread.sleep(350);
                require(playerSwings == swings, "Lost left arm disables the native jab action");
                x("click", "3"); Thread.sleep(350);
                require(playerSwings == swings + 1, "Surviving right arm still starts a native heavy swing");
                fixture(2); shot("battle-opponent-limb.png");
                Thread.sleep(900); require(opponentLost == 1, "Opponent leg detachment persists after recovery");
                fixture(3); Thread.sleep(1200); shot("battle-both-out.png");
                require(playerState == State.OUT && opponentState == State.OUT && joints == 0 && bodies == 17,
                        "Both fighters break into terrain-colliding bodies without new debris");
                key(window, "F10");
                for (int i = 0; i < 8; i++) key(window, "r");
                require(playerLost == 0 && opponentLost == 0 && bodies == 17 && joints == 10,
                        "Eight native R resets restore 12 fighter bodies, five terrain bodies and ten joints");
                key(window, "Tab"); key(window, "r");
                shot("battle-reset.png");
                key(window, "Escape");
            } catch (Throwable e) {
                failure = e;
                if (active != null) { try { shot("battle-failure.png"); } catch (Exception ignored) {} glfwSetWindowShouldClose(active.window, true); }
            }
        }, "battle-native-input");
        input.start();
        app.run(a -> {
            active = a; var arena = a.arena;
            if (fixture != fixtureDone) {
                arena.opponent.cooldown = 100;
                if (fixture == 1) arena.impact(arena.player, Part.LEFT_ARM,
                        new Vector3f(arena.player.part(Part.LEFT_ARM).body.position).add(.15f, .2f, .1f), new Vector3f(-4, 2, -7), 64);
                if (fixture == 2) arena.impact(arena.opponent, Part.RIGHT_LEG,
                        new Vector3f(arena.opponent.part(Part.RIGHT_LEG).body.position).add(.15f, .2f, .1f), new Vector3f(4, 2, 7), 80);
                if (fixture == 3) for (var f : List.of(arena.player, arena.opponent))
                    arena.impact(f, Part.TORSO, new Vector3f(f.position()).add(.2f, .2f, .2f), new Vector3f(4, 2, 28), 140);
                fixtureDone = fixture;
            }
            px = arena.player.position().x; pz = arena.player.position().z;
            var target = arena.opponent.part(Part.TORSO).body.position; targetX = target.x; targetY = target.y; targetZ = target.z;
            var eye = a.eye(); eyeX = eye.x; eyeY = eye.y; eyeZ = eye.z;
            var direction = a.direction(); yaw = (float)Math.atan2(direction.x, direction.z); pitch = (float)-Math.asin(direction.y);
            playerHits = arena.player.hits; opponentHits = arena.opponent.hits; playerSwings = arena.player.swings;
            playerKnockdowns = arena.player.knockdowns; opponentKnockdowns = arena.opponent.knockdowns;
            playerLost = arena.player.detached(); opponentLost = arena.opponent.detached();
            playerState = arena.player.state; opponentState = arena.opponent.state;
            if (arena.opponent.state == State.DOWN && arena.opponent.detached() == 0
                    && arena.physics.joints.size() == 10 && arena.opponent.parts.values().stream().allMatch(p -> !p.body.kinematic)
                    && arena.opponent.parts.values().stream().anyMatch(p -> Math.abs(p.body.rotation.x) + Math.abs(p.body.rotation.z) > .03f))
                intactDynamicMotion = true;
            bodies = arena.physics.bodies.size(); joints = arena.physics.joints.size(); resets = arena.resets;
            renderer = a.renderer;
            if (a.frames > 15) { frameMs.add(a.frameSeconds * 1000); simulationMs.add(a.simulationSeconds * 1000); }
            if (capture != null && !capture.equals(captured)) { capture(a, capture); captured = capture; }
        });
        input.join();
        if (failure != null) { Files.writeString(out.resolve("failure.txt"), failure.getClass().getSimpleName() + ": " + failure.getMessage()); throw new AssertionError("Native battle playtest failed; see sanitized failure.txt"); }
        Collections.sort(frameMs); Collections.sort(simulationMs);
        Files.writeString(out.resolve("results.json"), "{\n  \"Playtest\": \"Linux X11 inherited role display and XAUTHORITY; synthetic isolated home; native GLFW input; 960x640\",\n"
                + "  \"renderer\": \"" + renderer + "\",\n  \"steps\": [\"W approaches; mouse LMB jab hits; RMB heavy knocks down; AI fights back\",\"F10 records real-input fight\",\"Labelled render-thread fixtures: player arm and opponent leg detach; both cores break up through production damage/physics\",\"Eight native R presses and TAB then R restore 17 bodies and 10 joints; Escape exits\"],\n"
                + "  \"observed\": \"All assertions passed; no GL errors at screenshots\",\n  \"frameSamples\": " + frameMs.size()
                + ",\n  \"frameMedianMs\": " + frameMs.get(frameMs.size()/2) + ",\n  \"frameP95Ms\": " + frameMs.get((int)(frameMs.size()*.95))
                + ",\n  \"simulationMedianMs\": " + simulationMs.get(simulationMs.size()/2) + ",\n  \"simulationP95Ms\": " + simulationMs.get((int)(simulationMs.size()*.95))
                + ",\n  \"qualification\": \"Software rendering diagnostic only. Physical-GPU 60 FPS qualification pending; no stable rollout claim.\"\n}\n");
        System.out.println("PASS: native battle input, combat, labelled symmetric breakup fixtures, F10 and repeated reset");
    }
}
