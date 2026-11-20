package dev.jayms.combat.client;

import dev.jayms.combat.CombatEngine.Intent;
import dev.jayms.combat.CombatEngine.Item;
import dev.jayms.combat.practice.PracticeClient;
import dev.jayms.combat.practice.PracticeMatch.*;
import dev.jayms.combat.practice.PracticeServer;
import dev.jayms.net.SecureTransport;
import dev.jayms.recording.ScreenRecorder;
import dev.jayms.window.Window;
import org.lwjgl.system.MemoryUtil;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.lwjgl.glfw.GLFW.glfwSetWindowShouldClose;
import static org.lwjgl.opengl.GL33.GL_PACK_ALIGNMENT;
import static org.lwjgl.opengl.GL33.GL_RGB;
import static org.lwjgl.opengl.GL33.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL33.glPixelStorei;
import static org.lwjgl.opengl.GL33.glReadPixels;

/** Optional Windows/macOS/Linux desktop playtest. No synthetic frames enter the recorder. */
public final class PracticeNativePlaytest {
    private PracticeNativePlaytest() { }

    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]).toAbsolutePath();
        Files.createDirectories(output);
        var identity = SecureTransport.server(Files.createTempDirectory(output.getParent(), "practice-tls-"));
        try (var server = new PracticeServer("127.0.0.1", 0, identity.context(), Kit.NO_DEBUFF);
             var first = new PracticeClient("127.0.0.1", server.port(), identity.fingerprint(), "Aster");
             var second = new PracticeClient("127.0.0.1", server.port(), identity.fingerprint(), "Rowan")) {
            var peer = Executors.newSingleThreadScheduledExecutor();
            long[] sequence = {0};
            peer.scheduleAtFixedRate(() -> second.input(intent(second.view(), second.id(), ++sequence[0], false)),
                    0, 50, TimeUnit.MILLISECONDS);
            try {
                Observer observer = new Observer(first, second, output);
                new PracticeGame(first, Kit.NO_DEBUFF, false, output).run(observer);
                if (observer.report == null) throw new IllegalStateException("Desktop playtest ended before exchanging hits and healing");
                Files.writeString(output.resolve("desktop-playtest.txt"), observer.report);
                System.out.println(observer.report);
            } finally {
                peer.shutdownNow();
            }
        }
    }

    private static final class Observer implements PracticeGame.FrameObserver {
        private final PracticeClient first, second;
        private final Path output;
        private long start, recordedAt, previousTick = -1;
        private boolean ready, captured, capturedFlash, stopped, remotePresented;
        private float presentX, presentY, presentZ, hurtTint, previousX, previousY, previousZ;
        private double peakKnockbackHorizontal, peakKnockbackUp;
        private long lastSprintHit = -1;
        private int sprintHits;
        private int betweenSnapshotFrames;
        private String report;

        Observer(PracticeClient first, PracticeClient second, Path output) {
            this.first = first;
            this.second = second;
            this.output = output;
        }

        @Override public Intent input(Intent controls, View view) {
            return intent(view, first.id(), controls.sequence(), true);
        }

        @Override public void presented(int id, float x, float y, float z, float hurtTint) {
            if (id != second.id()) return;
            remotePresented = true;
            presentX = x;
            presentY = y;
            presentZ = z;
            this.hurtTint = hurtTint;
        }

        @Override public void frame(Window window, ScreenRecorder recorder, View view, int width, int height) throws Exception {
            long now = System.nanoTime();
            if (start == 0) start = now;
            if (!ready && view.fighters().size() == 2) {
                first.ready();
                second.ready();
                ready = true;
            }
            if (view.phase() == Phase.FIGHT && recordedAt == 0) {
                recorder.toggle(width * 2 / 3, height * 2 / 3);
                recordedAt = now;
            }
            Fighter a = fighter(view, first.id()), b = fighter(view, second.id());
            if (view.phase() == Phase.FIGHT && view.tick() != previousTick && b != null
                    && b.hurtTick() >= 0 && view.tick() > b.hurtTick() && view.tick() <= b.hurtTick() + 3) {
                double horizontal = Math.hypot(b.vx(), b.vz());
                peakKnockbackHorizontal = Math.max(peakKnockbackHorizontal, horizontal);
                peakKnockbackUp = Math.max(peakKnockbackUp, b.vy());
                if (horizontal > .58 && b.hurtTick() > lastSprintHit) {
                    lastSprintHit = b.hurtTick();
                    sprintHits++;
                }
            }
            if (remotePresented && view.phase() == Phase.FIGHT && b != null && b.hurtTick() >= 0) {
                double moved = Math.abs(presentX - previousX) + Math.abs(presentY - previousY)
                        + Math.abs(presentZ - previousZ);
                if (previousTick == view.tick() && moved > .005) betweenSnapshotFrames++;
                if (!capturedFlash && a != null && a.pitch() > -40 && hurtTint > .5f) {
                    screenshot(output.resolve("hit-flash.png"), width, height);
                    capturedFlash = true;
                }
            }
            previousTick = view.tick();
            previousX = presentX;
            previousY = presentY;
            previousZ = presentZ;
            remotePresented = false;
            if (!captured && a != null && b != null && a.hits() > 0 && b.hits() > 0 && a.used() > 0
                    && a.pitch() > -40) {
                screenshot(output.resolve("basalt-courtyard.png"), width, height);
                captured = true;
            }
            if (view.phase() == Phase.RESULT && captured && !stopped) {
                var acknowledged = second.view();
                if (acknowledged.phase() == Phase.RESULT && acknowledged.winner() == view.winner()) {
                    if (!capturedFlash || betweenSnapshotFrames == 0)
                        throw new IllegalStateException("Missing native evidence for damage tint or motion between snapshots");
                    if (sprintHits < 3 || peakKnockbackUp < .12 || peakKnockbackUp > .24)
                        throw new IllegalStateException("Expected three sprint hits with a low arc, got "
                                + sprintHits + " hits and " + peakKnockbackUp + " upward blocks per tick");
                    if (recorder.active()) recorder.toggle(width, height);
                    stopped = true;
                    report = "Windows native Voxel One renderer and two TLS clients in Basalt Courtyard.\n"
                            + "Both clients agreed on " + view.result() + " and winner " + view.winner() + ".\n"
                            + a.name() + " hits " + a.hits() + ", longest combo " + a.combo() + ", pots used " + a.used() + ".\n"
                            + b.name() + " hits " + b.hits() + ", longest combo " + b.combo() + ", pots used " + b.used() + ".\n"
                            + "Captured the opponent's red flash and observed " + betweenSnapshotFrames
                            + " moving frames between received snapshots after damage.\n"
                            + "Observed " + sprintHits + " distinct sprint knockback launches during the W-tap combo.\n"
                            + String.format(Locale.ROOT,
                            "Peak observed opponent displacement after damage: %.3f horizontal, %.3f upward blocks per tick.%n",
                            peakKnockbackHorizontal, peakKnockbackUp);
                }
            }
            if (stopped && recorder.status("F10").startsWith("Recording saved"))
                glfwSetWindowShouldClose(window.getHandle(), true);
            if (now - start > TimeUnit.SECONDS.toNanos(32)) {
                if (recorder.active()) recorder.toggle(width, height);
                throw new IllegalStateException("Desktop round timed out: " + view.phase() + " " + view.fighters());
            }
        }
    }

    private static Intent intent(View view, int id, long sequence, boolean primary) {
        Fighter self = fighter(view, id);
        Fighter other = view.fighters().stream().filter(f -> f.id() != id).findFirst().orElse(null);
        if (self == null || other == null || view.phase() != Phase.FIGHT)
            return new Intent(sequence, id == 1 ? -90 : 90, 0, 0, 0, false, false, false, false, 0);
        double dx = other.x() - self.x(), dz = other.z() - self.z(), range = Math.hypot(dx, dz);
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx));
        float pitch = (float) Math.toDegrees(Math.atan2(other.y() - self.y() - .68, range));
        boolean heal = primary && self.health() < 17 && self.used() == 0;
        int slot = 0;
        if (heal) {
            for (int i = 0; i < 9; i++) {
                if (view.inventory().get(i).item() == Item.HEALING) { slot = i; break; }
            }
        }
        boolean combo = primary && self.used() > 0;
        boolean wTap = combo && other.hurtTick() >= 0 && view.tick() - other.hurtTick() < 2;
        boolean advance = !wTap && range > (combo ? .65 : 2.35);
        float forward = heal ? -.2f : advance ? 1 : 0;
        boolean swing = primary ? combo && !wTap : self.hits() < 2;
        return new Intent(sequence, yaw, heal ? -85 : pitch, forward, 0, false, forward > 0,
                !heal && range < 3.05 && swing && sequence % 3 == 0, heal, slot);
    }

    private static Fighter fighter(View view, int id) {
        return view.fighters().stream().filter(f -> f.id() == id).findFirst().orElse(null);
    }

    static void screenshot(Path destination, int width, int height) throws Exception {
        var pixels = MemoryUtil.memAlloc(width * height * 3);
        try {
            glPixelStorei(GL_PACK_ALIGNMENT, 1);
            glReadPixels(0, 0, width, height, GL_RGB, GL_UNSIGNED_BYTE, pixels);
            var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int i = ((height - 1 - y) * width + x) * 3;
                    image.setRGB(x, y, (pixels.get(i) & 255) << 16 | (pixels.get(i + 1) & 255) << 8 | pixels.get(i + 2) & 255);
                }
            }
            ImageIO.write(image, "png", destination.toFile());
        } finally {
            glPixelStorei(GL_PACK_ALIGNMENT, 4);
            MemoryUtil.memFree(pixels);
        }
    }
}
