package dev.jayms.combat.client;

import dev.jayms.combat.CombatEngine.Intent;
import dev.jayms.combat.practice.PracticeClient;
import dev.jayms.combat.practice.PracticeMatch.Kit;
import dev.jayms.combat.practice.PracticeMatch.Phase;
import dev.jayms.combat.practice.PracticeMatch.View;
import dev.jayms.combat.practice.PracticeServer;
import dev.jayms.net.SecureTransport;
import dev.jayms.recording.ScreenRecorder;
import dev.jayms.window.Window;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.lwjgl.glfw.GLFW.glfwSetWindowShouldClose;
import static org.lwjgl.glfw.GLFW.glfwSetWindowSize;

/** Captures the real first-person layers in a live Basalt Courtyard match. */
public final class PracticeHandNativePlaytest {
    private PracticeHandNativePlaytest() {}

    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]).toAbsolutePath();
        Files.createDirectories(output);
        var identity = SecureTransport.server(Files.createTempDirectory(output.getParent(), "hand-test-tls-"));
        try (var server = new PracticeServer("127.0.0.1", 0, identity.context(), Kit.NO_DEBUFF);
             var first = new PracticeClient("127.0.0.1", server.port(), identity.fingerprint(), "Aster");
             var second = new PracticeClient("127.0.0.1", server.port(), identity.fingerprint(), "Rowan")) {
            var peer = Executors.newSingleThreadScheduledExecutor();
            long[] sequence = {0};
            peer.scheduleAtFixedRate(() -> second.input(new Intent(++sequence[0], 90, 0, 0, 0,
                    false, false, false, false, 0)), 0, 50, TimeUnit.MILLISECONDS);
            try {
                var playtest = new Observer(first, second, output);
                new PracticeGame(first, Kit.NO_DEBUFF, false, output).run(playtest);
                if (!playtest.complete) throw new IllegalStateException("Practice hand playtest did not finish");
                Files.write(output.resolve("hand-playtest.txt"), playtest.notes);
            } finally {
                peer.shutdownNow();
            }
        }
    }

    private static final class Observer implements PracticeGame.FrameObserver {
        private final PracticeClient first, second;
        private final Path output;
        private final List<String> notes = new ArrayList<>();
        private long firstFrame, fight, swing, guard, release, lowered, pearlAt, raise;
        private boolean ready, idle, swung, blocked, recovered, equipped, pearlIdle, pearlUsed, resized, stopped, complete;

        Observer(PracticeClient first, PracticeClient second, Path output) {
            this.first = first;
            this.second = second;
            this.output = output;
        }

        @Override public Intent input(Intent controls, View view) {
            long now = System.nanoTime();
            long elapsed = fight == 0 ? 0 : TimeUnit.NANOSECONDS.toMillis(now - fight);
            boolean attack = view.phase() == Phase.FIGHT && elapsed >= 720 && swing == 0;
            if (attack) swing = now;
            boolean blocking = swing != 0 && elapsed >= 1_350 && elapsed < 2_150;
            if (blocking && guard == 0) guard = now;
            if (!blocking && guard != 0 && release == 0) release = now;
            int slot = elapsed >= 2_650 && elapsed < 3_590 ? 1 : 0;
            if (slot == 1 && lowered == 0) lowered = now;
            boolean throwing = slot == 1 && elapsed >= 3_140 && elapsed < 3_270;
            if (throwing && pearlAt == 0) pearlAt = now;
            if (slot == 0 && lowered != 0 && raise == 0) raise = now;
            return new Intent(controls.sequence(), -90, 0, 0, 0, false, false, attack, blocking || throwing, slot);
        }

        @Override public void frame(Window window, ScreenRecorder recorder, View view, int width, int height)
                throws Exception {
            long now = System.nanoTime();
            if (firstFrame == 0) {
                firstFrame = now;
                glfwSetWindowSize(window.getHandle(), 1280, 720);
            }
            if (!ready && view.fighters().size() == 2 && Math.abs(width / (float) height - 16f / 9f) < .02) {
                first.ready();
                second.ready();
                ready = true;
            }
            if (view.phase() == Phase.FIGHT && fight == 0) {
                fight = now;
                notes.add("Native first-person pose check with two TLS clients in Basalt Courtyard.");
                recorder.toggle(width * 3 / 4, height * 3 / 4);
            }
            if (!idle && after(now, fight, 450)) {
                grab("idle", width, height);
                idle = true;
            }
            if (!swung && after(now, swing, 88)) {
                grab("swing", width, height);
                swung = true;
            }
            if (!blocked && after(now, guard, 170)) {
                grab("guard", width, height);
                blocked = true;
            }
            if (!recovered && after(now, release, 210)) {
                grab("recovered", width, height);
                recovered = true;
            }
            if (!equipped && after(now, lowered, 90)) {
                grab("equip", width, height);
                equipped = true;
            }
            if (!pearlIdle && after(now, lowered, 365)) {
                grab("pearl-idle", width, height);
                pearlIdle = true;
            }
            if (!pearlUsed && view.inventory().size() == 36 && view.inventory().get(1).count() == 15
                    && after(now, pearlAt, 65)) {
                grab("pearl-throw", width, height);
                pearlUsed = true;
                notes.add("Server confirmed one pearl consumed from the equipped stack.");
            }
            if (!resized && after(now, raise, 410)) {
                grab("re-equipped", width, height);
                glfwSetWindowSize(window.getHandle(), 960, 720);
                resized = true;
            }
            if (resized && !stopped && Math.abs(width / (float) height - 4f / 3f) < .02
                    && after(now, raise, 680)) {
                grab("four-three", width, height);
                if (recorder.active()) recorder.toggle(width, height);
                stopped = true;
            }
            if (stopped && recorder.status("F10").startsWith("Recording saved")) {
                complete = idle && swung && blocked && recovered && equipped && pearlIdle && pearlUsed && resized;
                glfwSetWindowShouldClose(window.getHandle(), true);
            }
            if (now - firstFrame > TimeUnit.SECONDS.toNanos(24)) {
                throw new IllegalStateException("Timed out capturing hand and sword poses");
            }
        }

        private static boolean after(long now, long event, long milliseconds) {
            return event != 0 && now - event >= TimeUnit.MILLISECONDS.toNanos(milliseconds);
        }

        private void grab(String name, int width, int height) throws Exception {
            PracticeNativePlaytest.screenshot(output.resolve(name + ".png"), width, height);
            notes.add(name + " " + width + "x" + height);
        }
    }
}
