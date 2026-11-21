package dev.jayms.combat.practice;

import dev.jayms.combat.CombatEngine.Intent;
import dev.jayms.combat.CombatEngine.AttackView;
import dev.jayms.combat.CombatEngine.Item;
import dev.jayms.combat.CombatEngine.Effect;
import dev.jayms.combat.arena.BasaltCourtyard;
import dev.jayms.combat.practice.PracticeMatch.Fighter;
import dev.jayms.combat.practice.PracticeMatch.Kit;
import dev.jayms.combat.practice.PracticeMatch.Phase;
import dev.jayms.combat.practice.PracticeMatch.Result;
import dev.jayms.combat.practice.PracticeMatch.View;
import dev.jayms.net.SecureTransport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

class PracticeNetworkTest {
    private static final String LOOPBACK = "127.0.0.1";
    private static final int STARTING_POTS = 31;
    @TempDir Path temporary;

    @ParameterizedTest(name = "No Debuff with {0} ms added RTT on both TLS connections")
    @ValueSource(ints = {0, 80, 150})
    @Timeout(30)
    void clientsAgreeOnHitPotionAndPearlInBasaltCourtyard(int rtt) throws Exception {
        var tls = SecureTransport.server(temporary.resolve("tls"));
        try (var server = new PracticeServer(LOOPBACK, 0, tls.context(), Kit.NO_DEBUFF);
             var relayA = new TcpDelay(server.port(), rtt / 2);
             var relayB = new TcpDelay(server.port(), rtt / 2);
             var alice = new PracticeClient(LOOPBACK, relayA.port(), tls.fingerprint(), "Alice");
             var bob = new PracticeClient(LOOPBACK, relayB.port(), tls.fingerprint(), "Bob")) {
            assertEquals(Kit.NO_DEBUFF, alice.view().kit());
            assertEquals(Phase.WAITING, alice.view().phase());
            assertEquals(Phase.WAITING, bob.view().phase());
            assertEquals(BasaltCourtyard.spawn(0).z(), self(alice).z());
            assertEquals(BasaltCourtyard.spawn(1).z(), self(bob).z());
            assertTrue(alice.swap(0, 8));
            await(() -> alice.view().inventory().get(8).item() == Item.SWORD, "opening kit edit");
            assertEquals(Item.SWORD, bob.view().inventory().get(0).item());

            long started = System.nanoTime();
            assertTrue(alice.ready());
            await(() -> self(alice).ready(), "ready round trip");
            long readyRtt = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            assertTrue(readyRtt >= rtt - 8, "Expected at least the relay's added RTT, observed " + readyRtt + " ms");
            assertTrue(bob.ready());
            await(() -> bothPhase(alice, bob, Phase.COUNTDOWN), "countdown");
            assertTrue(input(alice, 0, -90, -89, 1, true, true, true, 5));
            assertTrue(input(bob, 0, 90, -89, 1, true, true, true, 5));
            await(() -> bothPhase(alice, bob, Phase.FIGHT), "fight");
            assertEquals(Item.HEALING, alice.view().inventory().get(5).item());
            assertEquals(BasaltCourtyard.spawn(0).z(), self(alice).z());
            assertEquals(BasaltCourtyard.spawn(1).z(), self(bob).z());

            long sequence = approach(alice, bob);
            assertTrue(self(alice).z() < BasaltCourtyard.spawn(0).z() - 8);
            assertTrue(self(bob).z() > BasaltCourtyard.spawn(1).z() + 8);
            Fighter aliceBefore = self(alice), bobBefore = self(bob);
            assertTrue(aliceBefore.z() - bobBefore.z() > .3 && aliceBefore.z() - bobBefore.z() < 3.2,
                    "Fighters reached the central trading lane");
            long shown = alice.view().tick();
            assertTrue(alice.input(new Intent(sequence++, -90, 0, 0, 0, false, false, true, false, 8,
                    new AttackView(bob.id(), shown, shown))));
            assertTrue(input(alice, sequence++, 0, 0, 0, false, false, false, 0));
            await(() -> fighter(alice.view(), alice.id()).hits() == 1 && self(bob).health() < 20
                    && bothAgree(alice, bob), "authoritative sword hit on both clients");
            float injured = self(bob).health();
            assertTrue(injured > 17 && injured < 18);
            assertTrue(bob.view().lastHit() >= 0);
            assertEquals(bob.view().lastHit(), self(bob).hurtTick());
            assertEquals(self(bob).hurtTick(), fighter(alice.view(), bob.id()).hurtTick());
            assertEquals(self(bob).hurtTick(), self(bob).impulseTick());
            assertEquals(self(bob).impulseTick(), fighter(alice.view(), bob.id()).impulseTick());
            assertEquals(-.211457f, self(bob).impulseZ(), .0001f);
            assertEquals(.3208f, self(bob).impulseY(), .0001f);
            await(() -> {
                var own = self(bob);
                var remote = fighter(alice.view(), bob.id());
                return own.vz() == 0 && remote.vz() == 0 && Math.abs(own.z() - remote.z()) < .001
                        && Math.abs(bobBefore.z() - own.z() - 1) < .001;
            }, "one block of normal knockback on both clients");
            assertTrue(input(alice, sequence++, -90, 0, 0, false, false, false, 8));
            assertTrue(input(bob, sequence++, 90, -89, 0, false, false, true, 5));
            await(() -> bob.view().lastSplash() >= 0 && alice.view().lastSplash() == bob.view().lastSplash()
                    && self(bob).health() > injured && bothAgree(alice, bob), "shared splash and recovery");
            assertEquals(Item.EMPTY, bob.view().inventory().get(5).item());
            assertEquals(1, self(bob).used());
            assertEquals(STARTING_POTS - 1, self(bob).remaining());
            assertTrue(bob.quickMove(21));
            await(() -> bob.view().inventory().get(5).item() == Item.HEALING, "shift-click reserve refill");
            assertEquals(Item.EMPTY, bob.view().inventory().get(21).item());
            assertEquals(Item.HEALING, alice.view().inventory().get(21).item());

            Fighter beforePearl = self(bob);
            assertTrue(input(bob, sequence++, 0, -20, 0, false, false, false, 1));
            assertTrue(input(bob, sequence, 0, -20, 0, false, false, true, 1));
            await(() -> {
                Fighter own = self(bob), remote = fighter(alice.view(), bob.id());
                return own.motionEpoch() == beforePearl.motionEpoch() + 1 && own.motionEpoch() == remote.motionEpoch()
                        && own.x() == remote.x() && own.x() > beforePearl.x() + 2
                        && own.health() == beforePearl.health() - 5 && bothAgree(alice, bob);
            }, "shared pearl teleport and landing damage");
            assertEquals(15, bob.view().inventory().get(1).count());
            assertTrue(bob.view().cooldown() > 0);
            System.out.printf("Basalt Courtyard: %d ms added RTT, %d ms ready observed, hit, potion and pearl snapshots agree%n",
                    rtt, readyRtt);

            if (rtt == 0) verifySpectatorForfeitAndRematch(server.port(), tls.fingerprint(), alice, bob);
        }
    }

    @Test
    @Timeout(20)
    void debuffKitAppliesPoisonToTheOpponentThroughTls() throws Exception {
        var tls = SecureTransport.server(temporary.resolve("tls"));
        try (var server = new PracticeServer(LOOPBACK, 0, tls.context(), Kit.DEBUFF);
             var alice = new PracticeClient(LOOPBACK, server.port(), tls.fingerprint(), "Alice");
             var bob = new PracticeClient(LOOPBACK, server.port(), tls.fingerprint(), "Bob")) {
            assertEquals(Kit.DEBUFF, alice.view().kit());
            assertEquals(Item.POISON, alice.view().inventory().get(5).item());
            assertEquals(Item.SLOWNESS, bob.view().inventory().get(6).item());
            assertTrue(alice.ready());
            assertTrue(bob.ready());
            await(() -> bothPhase(alice, bob, Phase.FIGHT), "Debuff round");
            long sequence = approach(alice, bob);
            assertTrue(input(alice, sequence, -90, -8, 0, false, false, true, 5));
            await(() -> bob.view().effects().containsKey(Effect.POISON), "poison on opposing fighter");
            await(() -> self(bob).health() < 20 && bothAgree(alice, bob), "poison damage on both clients");
            assertEquals(Item.EMPTY, alice.view().inventory().get(5).item());
            assertEquals(0, self(alice).used(), "Poison does not count as a healing potion");
        }
    }

    private static long approach(PracticeClient alice, PracticeClient bob) throws InterruptedException {
        long sequence = 1;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        for (;;) {
            float aliceForward = laneApproach(self(alice).z());
            float bobForward = laneApproach(-self(bob).z());
            assertTrue(input(alice, sequence, -90, 0, aliceForward, aliceForward == 1, false, false, 8));
            assertTrue(input(bob, sequence++, 90, 0, bobForward, bobForward == 1, false, false, 0));
            if (aliceForward == 0 && bobForward == 0) break;
            if (System.nanoTime() >= deadline) fail("Timed out approaching the central trading lane");
            Thread.sleep(25);
        }
        await(() -> self(alice).vz() == 0 && self(bob).vz() == 0, "both fighters coast to rest after sprinting");
        return sequence;
    }

    private static float laneApproach(float distanceFromCenter) {
        // Brake on confirmed position so momentum and relay delay cannot carry fighters past melee range.
        return distanceFromCenter <= 1.4f ? 0 : Math.min(1, (distanceFromCenter - 1.2f) * .75f);
    }

    private static void verifySpectatorForfeitAndRematch(int port, String pin, PracticeClient alice, PracticeClient bob)
            throws Exception {
        try (var spectator = new PracticeClient(LOOPBACK, port, pin, "Spectator")) {
            assertEquals(Phase.FIGHT, spectator.view().phase());
            assertEquals(spectator.id(), spectator.view().spectatorId());
            assertTrue(spectator.view().inventory().isEmpty());
            assertEquals(2, spectator.view().fighters().size());
            assertTrue(spectator.ready());
            assertTrue(spectator.swap(0, 8));
            assertTrue(input(spectator, 0, -90, -89, 1, true, true, true, 5));
            long actionTick = spectator.view().tick();
            await(() -> spectator.view().tick() >= actionTick + 3, "spectator actions ignored");
            assertEquals(Phase.FIGHT, spectator.view().phase());
            assertTrue(spectator.view().inventory().isEmpty());
            assertTrue(spectator.view().fighters().stream().noneMatch(f -> f.id() == spectator.id()));

            bob.close();
            await(() -> bothPhase(alice, spectator, Phase.RESULT), "forfeit");
            assertEquals(Result.FORFEIT, alice.view().result());
            assertEquals(alice.id(), alice.view().winner());
            assertEquals(alice.view().winner(), spectator.view().winner());
            await(() -> alice.view().ticksLeft() == 0, "result timer");
            assertTrue(alice.ready());
            await(() -> bothPhase(alice, spectator, Phase.WAITING) && spectator.view().spectatorId() == 0,
                    "spectator promoted and reset");
            assertEquals(Item.SWORD, alice.view().inventory().get(8).item());
            assertEquals(20, self(alice).health());
            assertEquals(STARTING_POTS, self(alice).remaining());
            assertEquals(0, self(alice).hits());
            assertEquals(Item.SWORD, spectator.view().inventory().get(0).item());
            assertTrue(spectator.ready());
            await(() -> bothPhase(alice, spectator, Phase.COUNTDOWN), "rematch countdown");
            await(() -> bothPhase(alice, spectator, Phase.FIGHT), "rematch start");
            assertEquals(BasaltCourtyard.spawn(0).z(), self(alice).z());
            assertEquals(BasaltCourtyard.spawn(1).z(), self(spectator).z());
        }
    }

    private static boolean input(PracticeClient client, long sequence, float yaw, float pitch, float forward,
                                 boolean sprint, boolean attack, boolean use, int slot) {
        return client.input(new Intent(sequence, yaw, pitch, forward, 0, false, sprint, attack, use, slot));
    }

    private static void await(BooleanSupplier condition, String label) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() >= deadline) fail("Timed out waiting for " + label);
            Thread.sleep(10);
        }
    }

    private static Fighter self(PracticeClient client) { return fighter(client.view(), client.id()); }

    private static Fighter fighter(View view, int id) {
        return view.fighters().stream().filter(f -> f.id() == id).findFirst().orElseThrow();
    }

    private static boolean bothPhase(PracticeClient first, PracticeClient second, Phase phase) {
        return first.view().phase() == phase && second.view().phase() == phase;
    }

    private static boolean bothAgree(PracticeClient first, PracticeClient second) {
        var a = first.view();
        var b = second.view();
        return a.fighters().stream().allMatch(fighter -> {
            var other = fighter(b, fighter.id());
            return fighter.health() == other.health() && fighter.hits() == other.hits()
                    && fighter.used() == other.used() && fighter.remaining() == other.remaining();
        });
    }

    /**
     * A byte relay keeps TLS end to end. Separate timed queues in each direction
     * add transit latency without limiting the 20 Hz stream to one packet per delay.
     */
    private static final class TcpDelay implements AutoCloseable {
        private final ServerSocket listener;
        private final ExecutorService threads = Executors.newCachedThreadPool(task -> {
            var thread = new Thread(task, "practice-test-delay");
            thread.setDaemon(true);
            return thread;
        });
        private volatile Socket client, server;
        private final long delayNanos;

        private record Packet(byte[] bytes, long deliverAt) { }

        TcpDelay(int serverPort, int oneWayMillis) throws IOException {
            delayNanos = TimeUnit.MILLISECONDS.toNanos(oneWayMillis);
            listener = new ServerSocket(0, 1, InetAddress.getByName(LOOPBACK));
            threads.execute(() -> {
                try {
                    client = listener.accept();
                    server = new Socket(LOOPBACK, serverPort);
                    client.setTcpNoDelay(true);
                    server.setTcpNoDelay(true);
                    pipe(client, server);
                    pipe(server, client);
                } catch (IOException closed) {
                    close();
                }
            });
        }

        int port() { return listener.getLocalPort(); }

        private void pipe(Socket from, Socket to) throws IOException {
            var input = from.getInputStream();
            var output = to.getOutputStream();
            var packets = new ArrayBlockingQueue<Packet>(64);
            threads.execute(() -> {
                byte[] bytes = new byte[4096];
                try {
                    for (int read; (read = input.read(bytes)) != -1;) {
                        packets.put(new Packet(Arrays.copyOf(bytes, read), System.nanoTime() + delayNanos));
                    }
                    packets.put(new Packet(new byte[0], System.nanoTime() + delayNanos));
                } catch (InterruptedException stopped) {
                    Thread.currentThread().interrupt();
                } catch (IOException closed) {
                    close();
                }
            });
            threads.execute(() -> {
                try {
                    for (;;) {
                        Packet packet = packets.take();
                        long remaining = packet.deliverAt - System.nanoTime();
                        if (remaining > 0) TimeUnit.NANOSECONDS.sleep(remaining);
                        if (packet.bytes.length == 0) {
                            to.shutdownOutput();
                            return;
                        }
                        output.write(packet.bytes);
                    }
                } catch (InterruptedException stopped) {
                    Thread.currentThread().interrupt();
                } catch (IOException closed) {
                    close();
                }
            });
        }

        @Override public void close() {
            try { listener.close(); } catch (IOException ignored) { }
            try { if (client != null) client.close(); } catch (IOException ignored) { }
            try { if (server != null) server.close(); } catch (IOException ignored) { }
            threads.shutdownNow();
        }
    }
}
