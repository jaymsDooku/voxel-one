package dev.jayms.combat.client;

import dev.jayms.combat.CombatEngine.Effect;
import dev.jayms.combat.CombatEngine.Intent;
import dev.jayms.combat.faction.FactionMatch;
import dev.jayms.combat.practice.PracticeClient;
import dev.jayms.combat.practice.PracticeMatch.Fighter;
import dev.jayms.combat.practice.PracticeMatch.Kit;
import dev.jayms.combat.practice.PracticeMatch.Phase;
import dev.jayms.combat.practice.PracticeMatch.Result;
import dev.jayms.combat.practice.PracticeMatch.View;
import dev.jayms.combat.practice.PracticeServer;
import dev.jayms.net.AccountStore;
import dev.jayms.net.SecureTransport;
import dev.jayms.recording.ScreenRecorder;
import dev.jayms.window.Window;
import org.lwjgl.system.MemoryUtil;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.lwjgl.glfw.GLFW.glfwSetWindowShouldClose;
import static org.lwjgl.opengl.GL33.GL_PACK_ALIGNMENT;
import static org.lwjgl.opengl.GL33.GL_RGB;
import static org.lwjgl.opengl.GL33.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL33.glPixelStorei;
import static org.lwjgl.opengl.GL33.glReadPixels;

/** Optional desktop smoke test of the native faction HUD and four authenticated arena clients. */
public final class FactionNativePlaytest {
    private static final String HOST = "127.0.0.1";
    private static final char[] TEST_PASSWORD = "local-arena-fixture".toCharArray();
    private static final int BARD = 2, ROGUE = 4;
    private static final long MAX_VIDEO_BYTES = 6_000_000;

    private FactionNativePlaytest() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Expected the recording directory");
        Path output = Path.of(args[0]).toAbsolutePath();
        Files.createDirectories(output);
        Path runtime = Files.createTempDirectory(output.getParent(), "faction-playtest-");
        var tls = SecureTransport.server(runtime.resolve("tls"));
        var accounts = new AccountStore(runtime.resolve("accounts.dat"));
        var settings = new FactionMatch.Settings(40, 1_200, 60, 48, 4, 120, 120, 30_000, 20_000, 10_000);
        var factions = new FactionMatch(runtime.resolve("season.dat"), settings, Clock.systemUTC());
        try (var server = new PracticeServer(HOST, 0, tls.context(), factions, accounts);
             var bard = connect(server, tls, "EmberBard");
             var slate = connect(server, tls, "SlateGuard");
             var ally = connect(server, tls, "EmberGuard");
             var rogue = connect(server, tls, "SlateRogue")) {
            Observer observer = new Observer(bard, slate, ally, rogue, output);
            var peerThread = Executors.newSingleThreadScheduledExecutor();
            AtomicLong sequence = new AtomicLong();
            peerThread.scheduleAtFixedRate(() -> {
                long n = sequence.incrementAndGet();
                slate.input(drive(slate.view(), slate.id(), n, 90, observer.withdraw ? -5 : -.72, false));
                ally.input(drive(ally.view(), ally.id(), n, -90, -2.6, false));
                rogue.input(drive(rogue.view(), rogue.id(), n, 90, -3.2, false));
            }, 0, 50, TimeUnit.MILLISECONDS);
            try {
                new PracticeGame(bard, Kit.DEBUFF, true, output).run(observer);
                if (observer.report == null) throw new IllegalStateException("No completed native faction round");
                Path clip;
                try (var files = Files.list(output)) {
                    clip = files.filter(file -> file.getFileName().toString().endsWith(".mp4"))
                            .max(java.util.Comparator.comparingLong(FactionNativePlaytest::modified))
                            .orElseThrow(() -> new IllegalStateException("No native faction recording was saved"));
                }
                long bytes = Files.size(clip);
                if (bytes == 0 || bytes >= MAX_VIDEO_BYTES) throw new IllegalStateException("Unexpected MP4 size " + bytes);
                String report = observer.report + "Recording: " + clip.getFileName() + " (" + bytes + " bytes).\n";
                Files.writeString(output.resolve("faction-native-playtest.txt"), report);
                System.out.print(report);
            } finally {
                peerThread.shutdownNow();
            }
        }
    }

    private static long modified(Path file) {
        try {
            return Files.getLastModifiedTime(file).toMillis();
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static PracticeClient connect(PracticeServer server, SecureTransport.ServerIdentity tls, String name)
            throws IOException {
        return new PracticeClient(HOST, server.port(), tls.fingerprint(), name, TEST_PASSWORD, true);
    }

    private static final class Observer implements PracticeGame.FrameObserver {
        private final PracticeClient bard, slate, ally, rogue;
        private final List<PracticeClient> clients;
        private final Path output;
        private long started, contestedTick = -1, resultTick = -1, lastStrike = -1;
        private boolean choseRole, ready, startedCapture, ability, teamBuff, screenshot, finished;
        private volatile boolean withdraw;
        private String report;

        Observer(PracticeClient bard, PracticeClient slate, PracticeClient ally, PracticeClient rogue, Path output) {
            this.bard = bard;
            this.slate = slate;
            this.ally = ally;
            this.rogue = rogue;
            this.output = output;
            clients = List.of(bard, slate, ally, rogue);
        }

        @Override public Intent input(Intent controls, View view) {
            Fighter self = fighter(view, bard.id());
            boolean hit = teamBuff && self != null && self.hits() == 0 && Math.abs(self.z() - .72) < .35
                    && status(view, "Hill contested") && (lastStrike < 0 || view.tick() >= lastStrike + 6);
            if (hit) lastStrike = view.tick();
            return drive(view, bard.id(), controls.sequence(), -90, .72, hit);
        }

        @Override public void frame(Window window, ScreenRecorder recorder, View view, int width, int height)
                throws IOException {
            long now = System.nanoTime();
            if (started == 0) started = now;
            if (!choseRole && clients.stream().allMatch(client -> client.view().fighters().size() == 4)) {
                if (!bard.action(BARD) || !rogue.action(ROGUE)) {
                    throw new IllegalStateException("Faction role action rejected");
                }
                choseRole = true;
            }
            if (!ready && status(view, "Ember / Bard") && status(rogue.view(), "Slate / Rogue")) {
                for (var client : clients) {
                    if (!client.ready()) throw new IllegalStateException("Could not ready client " + client.id());
                }
                ready = true;
            }
            Fighter actor = fighter(view, bard.id());
            if (!startedCapture && view.phase() == Phase.FIGHT && actor != null && actor.z() < 5.5f) {
                recorder.toggle(width * 2 / 3, height * 2 / 3);
                if (!recorder.active()) throw new IllegalStateException(recorder.status("F10"));
                startedCapture = true;
            }
            if (view.phase() == Phase.FIGHT && status(view, "Hill contested")) {
                if (contestedTick == -1) contestedTick = view.tick();
                if (!ability && bard.action(FactionMatch.ABILITY)) ability = true;
            }
            if (ability && view.effects().containsKey(Effect.STRENGTH)
                    && ally.view().effects().containsKey(Effect.STRENGTH)) {
                if (slate.view().effects().containsKey(Effect.STRENGTH)
                        || rogue.view().effects().containsKey(Effect.STRENGTH)) {
                    throw new IllegalStateException("Enemy received the Bard's Strength");
                }
                teamBuff = true;
            }
            var teammate = fighter(view, ally.id());
            if (!screenshot && teamBuff && status(view, "Hill contested") && teammate != null
                    && teammate.z() < -2.4f) {
                screenshot(output.resolve("basalt-courtyard-factions.png"), width, height);
                screenshot = true;
            }
            if (screenshot && actor != null && actor.hits() > 0 && view.tick() >= contestedTick + 24) {
                withdraw = true;
            }
            if (resultTick == -1 && withdraw && clients.stream().allMatch(client -> {
                var verdict = client.view();
                return verdict.phase() == Phase.RESULT && verdict.result() == Result.OBJECTIVE
                        && verdict.winner() == bard.id() && status(verdict, "hills 1");
            })) {
                resultTick = view.tick();
                report = "Basalt Courtyard native faction playtest on " + System.getProperty("os.name") + ".\n"
                        + "Four authenticated TLS clients formed two opposing teams. The Ember Bard rendered in the native client.\n"
                        + "Hill contested at server tick " + contestedTick + ". The Bard buffed only Ember and landed "
                        + actor.hits() + " sword hit(s).\n"
                        + "All four clients agreed on Ember's objective victory and one captured hill at tick "
                        + resultTick + ".\n";
            }
            if (!finished && resultTick >= 0 && view.tick() >= resultTick + 20) {
                if (recorder.active()) recorder.toggle(width, height);
                finished = true;
            }
            if (finished && recorder.status("F10").startsWith("Recording saved")) {
                glfwSetWindowShouldClose(window.getHandle(), true);
            }
            if (now - started > TimeUnit.SECONDS.toNanos(30)) {
                throw new IllegalStateException("Faction playtest timed out in " + view.phase() + ", "
                        + view.status() + ", fighters " + view.fighters() + ", recorder " + recorder.status("F10"));
            }
        }
    }

    private static Intent drive(View view, int id, long sequence, float yaw, double z, boolean attack) {
        Fighter self = fighter(view, id);
        double offset = self == null || view.phase() != Phase.FIGHT ? 0 : z - self.z();
        float forward = Math.abs(offset) <= .18 ? 0 : (float) Math.copySign(1, offset * Math.signum(yaw));
        return new Intent(sequence, yaw, 0, forward, 0, false, false, attack, false, 0);
    }

    private static Fighter fighter(View view, int id) {
        return view.fighters().stream().filter(f -> f.id() == id).findFirst().orElse(null);
    }

    private static boolean status(View view, String text) {
        return view.status().stream().anyMatch(line -> line.contains(text));
    }

    private static void screenshot(Path destination, int width, int height) throws IOException {
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
