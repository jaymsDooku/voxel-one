package dev.jayms.combat.client;

import dev.jayms.combat.CombatEngine.Intent;
import dev.jayms.Camera;
import dev.jayms.combat.CombatEngine.Item;
import dev.jayms.combat.CombatEngine.Vec3;
import dev.jayms.combat.CombatRules;
import dev.jayms.combat.practice.PracticeClient;
import dev.jayms.combat.practice.PracticeMatch.*;
import dev.jayms.combat.practice.PracticeServer;
import dev.jayms.player.PlayerDimensions;
import dev.jayms.net.SecureTransport;
import dev.jayms.recording.ScreenRecorder;
import dev.jayms.window.Window;
import org.lwjgl.system.MemoryUtil;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
        if (args.length < 1 || List.of(args).subList(1, args.length).stream()
                .anyMatch(flag -> !flag.equals("--trade") && !flag.equals("--trace-only")))
            throw new IllegalArgumentException("Expected output directory and optional --trade and --trace-only");
        boolean trade = List.of(args).contains("--trade");
        Path output = Path.of(args[0]).toAbsolutePath();
        Files.createDirectories(output);
        var identity = SecureTransport.server(Files.createTempDirectory(output.getParent(), "practice-tls-"));
        var match = new MeasuredPracticeMatch();
        try (var server = new PracticeServer("127.0.0.1", 0, identity.context(), match, null);
             var first = new PracticeClient("127.0.0.1", server.port(), identity.fingerprint(), "Aster");
             var second = new PracticeClient("127.0.0.1", server.port(), identity.fingerprint(), "Rowan")) {
            var peer = Executors.newSingleThreadScheduledExecutor();
            peer.scheduleAtFixedRate(new CounterPlayer(second, first.id()), 0, 16_666_667, TimeUnit.NANOSECONDS);
            try {
                Observer observer = new Observer(first, second, output, !List.of(args).contains("--trace-only"), trade);
                try {
                    new PracticeGame(first, Kit.NO_DEBUFF, false, output).run(observer);
                } finally {
                    observer.trace.save(output);
                }
                if (observer.report == null) throw new IllegalStateException("Desktop playtest ended before both clients confirmed the round");
                Files.writeString(output.resolve("desktop-playtest.txt"), observer.report);
                System.out.println(observer.report);
            } finally {
                peer.shutdownNow();
                match.save(output);
            }
        }
    }

    /** Hold W and use the same nine-CPS clock and rendered targeting as the visible client. */
    private static final class CounterPlayer implements Runnable {
        private final PracticeClient client;
        private final int opponent;
        private final ArenaMotionTimeline timeline = new ArenaMotionTimeline();
        private final Camera camera = new Camera();
        private final ArenaAutoClicker clicks = new ArenaAutoClicker();
        private long frameNumber, sequence;

        CounterPlayer(PracticeClient client, int opponent) {
            this.client = client;
            this.opponent = opponent;
            clicks.toggle();
        }

        @Override public void run() {
            var view = client.view();
            long now = System.nanoTime();
            timeline.observe(view.tick(), view.fighters(), now);
            var frame = timeline.frame(now);
            var local = timeline.sampleLocal(client.id(), frame.local(), now, false, view.phase() == Phase.FIGHT);
            var target = timeline.sample(opponent, frame.remote());
            boolean fighting = view.phase() == Phase.FIGHT && local != null && target != null;
            boolean swing = clicks.poll(now, fighting);
            if (++frameNumber % 3 != 0 && !swing || !fighting) return;
            Vec3 feet = local.point();
            Vec3 offset = new Vec3(target.x(), target.y(), target.z()).minus(feet);
            camera.position().set((float) feet.x(), (float) (feet.y() + PlayerDimensions.EYE_HEIGHT), (float) feet.z());
            float yaw = (float) Math.toDegrees(Math.atan2(offset.z(), offset.x()));
            float pitch = aimPitch(offset.y(), Math.hypot(offset.x(), offset.z()));
            camera.setYaw(yaw);
            camera.setPitch(pitch);
            sequence++;
            client.input(new Intent(sequence, yaw, pitch, 1, 0, false, true, swing, false, 0,
                    swing ? PracticeGame.displayedTarget(camera, CombatRules.NO_DEBUFF, local.pose(), List.of(target)) : null));
        }
    }

    private static final class Observer implements PracticeGame.FrameObserver {
        private final PracticeClient first, second;
        private final Path output;
        private final boolean captureMedia;
        private final boolean trade;
        private long lastTrade = -1;
        private int mutualExchanges;
        private int framesShowingBothHits;
        private double peakHurtRoll;
        private long start, recordedAt, previousTick = -1;
        private boolean ready, captured, capturedFlash, stopped, remotePresented;
        private float presentX, presentY, presentZ, hurtTint, previousX, previousY, previousZ;
        private double peakKnockbackHorizontal, peakKnockbackUp;
        private long lastSprintHit = -1, sampledLaunch = -1;
        private final ArenaAutoClicker clicks = new ArenaAutoClicker();
        private int previousHits;
        private Vec3 displayedFeet;
        private double sampledLaunchSpeed;
        private int sprintHits, carriedLaunches, immediateImpulseCues, groundedSprintCues;
        private int betweenSnapshotFrames;
        private String report;
        private final NativeFrameTrace trace = new NativeFrameTrace();

        Observer(PracticeClient first, PracticeClient second, Path output, boolean captureMedia, boolean trade) {
            this.first = first;
            this.second = second;
            this.output = output;
            this.captureMedia = captureMedia;
            this.trade = trade;
            clicks.toggle();
        }

        @Override public Intent input(Intent controls, View view) {
            Fighter self = fighter(view, first.id());
            Vec3 offset = displayedFeet == null ? new Vec3(0, 0, 0)
                    : new Vec3(presentX, presentY, presentZ).minus(displayedFeet);
            Intent input = intent(view, first.id(), controls.sequence(), offset, clicks.poll(System.nanoTime(), view.phase() == Phase.FIGHT),
                    self != null && self.hits() > previousHits, trade);
            if (self != null) previousHits = self.hits();
            return input;
        }

        @Override public void sampled(long now, View view, double x, double y, double z, Vec3 confirmed) {
            displayedFeet = new Vec3(x, y, z);
            trace.sample(now, view, first.id(), second.id(), x, y, z, confirmed);
        }

        @Override public void submitted(long now, Intent intent) {
            trace.click(now, intent);
        }

        @Override public void hitFeedback(long tick, float received, float dealt, float rollRadians) {
            if (received > 0 && dealt > 0) framesShowingBothHits++;
            peakHurtRoll = Math.max(peakHurtRoll, Math.abs(Math.toDegrees(rollRadians)));
        }

        @Override public void presented(int id, float x, float y, float z, float hurtTint, float swingProgress) {
            if (id != second.id()) return;
            trace.presented(x, y, z, hurtTint, swingProgress);
            remotePresented = true;
            presentX = x;
            presentY = y;
            presentZ = z;
            this.hurtTint = hurtTint;
        }

        @Override public void frame(Window window, ScreenRecorder recorder, View view, int width, int height) throws Exception {
            long now = System.nanoTime();
            trace.rendered(now);
            if (start == 0) start = now;
            if (!ready && view.fighters().size() == 2) {
                first.ready();
                second.ready();
                ready = true;
            }
            if (captureMedia && view.phase() == Phase.FIGHT && recordedAt == 0) {
                recorder.toggle(width * 2 / 3, height * 2 / 3);
                recordedAt = now;
            }
            Fighter a = fighter(view, first.id()), b = fighter(view, second.id());
            if (a != null && b != null && a.hurtTick() >= 0 && b.hurtTick() >= 0
                    && Math.min(a.hurtTick(), b.hurtTick()) > lastTrade && Math.abs(a.hurtTick() - b.hurtTick()) <= 2) {
                mutualExchanges++;
                lastTrade = Math.max(a.hurtTick(), b.hurtTick());
            }
            if (view.phase() == Phase.FIGHT && view.tick() != previousTick && b != null
                    && b.impulseTick() == view.tick() && sprintImpulse(b)) {
                immediateImpulseCues++;
                if (Math.abs(b.vy() - .4208) < .001) groundedSprintCues++;
            }
            if (view.phase() == Phase.FIGHT && view.tick() != previousTick && b != null
                    && b.impulseTick() >= 0 && view.tick() >= b.impulseTick() && view.tick() <= b.impulseTick() + 2) {
                double horizontal = Math.hypot(b.vx(), b.vz());
                peakKnockbackHorizontal = Math.max(peakKnockbackHorizontal, horizontal);
                peakKnockbackUp = Math.max(peakKnockbackUp, b.vy());
                if (view.tick() == b.impulseTick() && sprintImpulse(b) && horizontal > 0) {
                    if (b.impulseTick() > lastSprintHit) {
                        lastSprintHit = b.impulseTick();
                        sprintHits++;
                    }
                    sampledLaunch = b.impulseTick();
                    sampledLaunchSpeed = horizontal;
                } else if (view.tick() == b.impulseTick() + 1 && sampledLaunch == b.impulseTick()
                        && horizontal >= .45 * sampledLaunchSpeed) {
                    carriedLaunches++;
                }
            }
            if (remotePresented && view.phase() == Phase.FIGHT && b != null && b.hurtTick() >= 0) {
                double moved = Math.abs(presentX - previousX) + Math.abs(presentY - previousY)
                        + Math.abs(presentZ - previousZ);
                if (previousTick == view.tick() && moved > .005) betweenSnapshotFrames++;
                if (!capturedFlash && a != null && a.pitch() > -40 && hurtTint > .5f) {
                    if (captureMedia) screenshot(output.resolve("hit-flash.png"), width, height);
                    capturedFlash = true;
                }
            }
            previousTick = view.tick();
            previousX = presentX;
            previousY = presentY;
            previousZ = presentZ;
            remotePresented = false;
            if (!captured && a != null && b != null && a.hits() > 0 && b.hits() > 0 && (trade || a.used() > 0)
                    && a.pitch() > -40) {
                if (captureMedia) screenshot(output.resolve("basalt-courtyard.png"), width, height);
                captured = true;
            }
            if (view.phase() == Phase.RESULT && captured && !stopped) {
                if (view.result() != Result.KNOCKOUT)
                    throw new IllegalStateException("Expected a knockout, got " + view.result());
                var acknowledged = second.view();
                if (acknowledged.phase() == Phase.RESULT && acknowledged.winner() == view.winner()) {
                    if (!capturedFlash || betweenSnapshotFrames == 0)
                        throw new IllegalStateException("Missing native evidence for damage tint or motion between snapshots");
                    if (trade && mutualExchanges < 2)
                        throw new IllegalStateException("Expected repeated two-way exchanges, observed " + mutualExchanges);
                    if (trade && (framesShowingBothHits == 0 || peakHurtRoll == 0))
                        throw new IllegalStateException("The local player did not see both hit cues and a hurt reaction");
                    if (!trade && (sprintHits < 3 || peakKnockbackUp < .38 || peakKnockbackUp > .462))
                        throw new IllegalStateException("Expected three sprint hits within the arena lift range, got "
                                + sprintHits + " hits and " + peakKnockbackUp + " upward blocks per tick");
                    if (!trade && groundedSprintCues == 0)
                        throw new IllegalStateException("No sprint cue showed the reduced grounded launch");
                    if (!trade && immediateImpulseCues < 3)
                        throw new IllegalStateException("Too few sprint impulses arrived on their hit tick: "
                                + immediateImpulseCues);
                    if (!trade && carriedLaunches == 0)
                        throw new IllegalStateException("No adjacent sprint launch snapshots retained momentum");
                    if (recorder.active()) recorder.toggle(width, height);
                    stopped = true;
                    report = "Native Voxel One renderer and two TLS clients in Basalt Courtyard on " + System.getProperty("os.name") + ".\n"
                            + "Both clients agreed on " + view.result() + " and winner " + view.winner() + ".\n"
                            + a.name() + " hits " + a.hits() + ", longest combo " + a.combo() + ", pots used " + a.used() + ".\n"
                            + b.name() + " hits " + b.hits() + ", longest combo " + b.combo() + ", pots used " + b.used() + ".\n"
                            + mutualExchanges + " exchanges in which both fighters took a full hit within two server ticks.\n"
                            + String.format(Locale.ROOT, "%d frames showed incoming and outgoing hit cues together; peak first-person roll %.2f degrees.%n",
                            framesShowingBothHits, peakHurtRoll)
                            + "Observed the opponent's red flash and " + betweenSnapshotFrames
                            + " moving frames between received snapshots after damage.\n"
                            + "Observed " + sprintHits + " distinct sprint knockback launches.\n"
                            + immediateImpulseCues + " sprint impulses reached the client on their hit tick.\n"
                            + groundedSprintCues + " sprint cues showed the reduced grounded launch of 0.4208 blocks per tick.\n"
                            + carriedLaunches + " sprint launches kept at least 45% of horizontal speed on the next tick while the defender could steer.\n"
                            + String.format(Locale.ROOT,
                            "Peak observed opponent displacement after damage: %.3f horizontal, %.3f upward blocks per tick.%n",
                            peakKnockbackHorizontal, peakKnockbackUp);
                }
            }
            if (stopped && (!captureMedia || recorder.status("F10").startsWith("Recording saved")))
                glfwSetWindowShouldClose(window.getHandle(), true);
            if (now - start > TimeUnit.SECONDS.toNanos(32)) {
                if (recorder.active()) recorder.toggle(width, height);
                throw new IllegalStateException("Desktop round timed out: " + view.phase() + " " + view.fighters());
            }
        }
    }

    private static Intent intent(View view, int id, long sequence, Vec3 displayedOffset, boolean swing, boolean wTap, boolean trade) {
        Fighter self = fighter(view, id);
        Fighter other = view.fighters().stream().filter(f -> f.id() != id).findFirst().orElse(null);
        if (self == null || other == null || view.phase() != Phase.FIGHT)
            return new Intent(sequence, id == 1 ? -90 : 90, 0, 0, 0, false, false, false, false, 0);
        double dx = displayedOffset.x(), dz = displayedOffset.z(), range = Math.hypot(dx, dz);
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx));
        float pitch = aimPitch(displayedOffset.y(), range);
        if (trade) return new Intent(sequence, yaw, pitch, 1, 0, false, true, swing, false, 0);
        boolean heal = self.health() < 17 && self.used() == 0;
        int slot = 0;
        if (heal) {
            for (int i = 0; i < 9; i++) {
                if (view.inventory().get(i).item() == Item.HEALING) { slot = i; break; }
            }
        }
        boolean advance = self.hits() == 0 || other.hits() == 0 || range >= 3.55
                || swing && range - PlayerDimensions.RADIUS - .1 <= CombatRules.NO_DEBUFF.reach();
        float forward = heal ? -.2f : wTap ? 0 : advance ? 1 : -1;
        return new Intent(sequence, yaw, heal ? -85 : pitch, forward, 0, false, forward > 0,
                swing && !heal, heal, slot);
    }

    private static float aimPitch(double elevation, double range) {
        double eyeY = PlayerDimensions.EYE_HEIGHT;
        double aimY = Math.max(elevation + .1, Math.min(eyeY, elevation + PlayerDimensions.HEIGHT));
        float pitch = (float) Math.toDegrees(Math.atan2(aimY - eyeY, Math.max(.001, range - PlayerDimensions.RADIUS - .1)));
        return Math.max(-89, Math.min(89, pitch));
    }

    private static Fighter fighter(View view, int id) {
        return view.fighters().stream().filter(f -> f.id() == id).findFirst().orElse(null);
    }

    private static boolean sprintImpulse(Fighter fighter) {
        // A stronger hit during hurt protection can apply the sprint bonus on its own.
        return fighter.impulseTick() > fighter.hurtTick() || fighter.vy() > (float) CombatRules.NO_DEBUFF.lift();
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
