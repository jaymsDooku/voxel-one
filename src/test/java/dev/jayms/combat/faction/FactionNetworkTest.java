package dev.jayms.combat.faction;

import dev.jayms.combat.CombatEngine.Effect;
import dev.jayms.combat.CombatEngine.Intent;
import dev.jayms.combat.CombatEngine.Item;
import dev.jayms.combat.arena.BasaltCourtyard;
import dev.jayms.combat.practice.PracticeClient;
import dev.jayms.combat.practice.PracticeMatch.Fighter;
import dev.jayms.combat.practice.PracticeMatch.Kit;
import dev.jayms.combat.practice.PracticeMatch.Phase;
import dev.jayms.combat.practice.PracticeMatch.Result;
import dev.jayms.combat.practice.PracticeServer;
import dev.jayms.net.AccountStore;
import dev.jayms.net.SecureTransport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class FactionNetworkTest {
    private static final String HOST = "127.0.0.1";
    private static final String PASSWORD = "synthetic-faction-fixture";
    private static final int BARD = 2;
    private static final int ROGUE = 4;
    private static final Pattern ENERGY = Pattern.compile("([0-9]+) energy");
    @TempDir Path temporary;

    @Test
    @Timeout(35)
    void fourAuthenticatedClientsPlayFactionRolesAndContestTheCourtyardHill() throws Exception {
        var identity = SecureTransport.server(temporary.resolve("tls"));
        var accounts = new AccountStore(temporary.resolve("faction-accounts"));
        var settings = new FactionMatch.Settings(6, 1_200, 10, 32, 4, 120, 120, 30_000, 20_000, 10_000);
        var factions = new FactionMatch(temporary.resolve("faction-season"), settings, Clock.systemUTC());
        assertTrue(accounts.register("SlateMain", PASSWORD.toCharArray()));
        try (var server = new PracticeServer(HOST, 0, identity.context(), factions, accounts);
             var practice = new PracticeServer(HOST, 0, identity.context(), Kit.NO_DEBUFF);
             var guest = new PracticeClient(HOST, practice.port(), identity.fingerprint(), "EmberBard");
             var bard = connect(server, identity, "EmberBard", true);
             var slate = connect(server, identity, "SlateMain", false);
             var ally = connect(server, identity, "EmberAlly", true);
             var rogue = connect(server, identity, "SlateRogue", true)) {
            var clients = List.of(bard, slate, ally, rogue);
            await(() -> clients.stream().allMatch(client -> client.view().fighters().size() == 4),
                    "four authenticated faction seats");
            assertEquals(BasaltCourtyard.spawn(0).z(), self(bard).z());
            assertEquals(BasaltCourtyard.spawn(1).z(), self(slate).z());
            assertEquals(BasaltCourtyard.teamSpawn(0, 1).x(), self(ally).x());
            assertEquals(BasaltCourtyard.teamSpawn(1, 1).x(), self(rogue).x());
            assertEquals(Kit.DEBUFF, bard.view().kit());
            assertEquals(Item.POISON, bard.view().inventory().get(5).item());
            assertEquals(Kit.NO_DEBUFF, guest.view().kit());
            assertEquals(Item.HEALING, guest.view().inventory().get(5).item());
            assertEquals(1, guest.view().fighters().size());
            assertTrue(guest.view().status().isEmpty());

            var badPassword = assertThrows(IOException.class, () -> new PracticeClient(HOST, server.port(),
                    identity.fingerprint(), "SlateMain", "incorrect-test-password".toCharArray(), false));
            assertTrue(badPassword.getMessage().contains("sign-in rejected"));
            var guestRejected = assertThrows(IOException.class, () ->
                    new PracticeClient(HOST, server.port(), identity.fingerprint(), "Guest"));
            assertTrue(guestRejected.getMessage().contains("sign-in rejected"));
            var factionLoginRejected = assertThrows(IOException.class, () ->
                    connect(practice, identity, "SlateMain", false));
            assertTrue(factionLoginRejected.getMessage().contains("sign-in rejected"));

            assertTrue(bard.action(BARD));
            assertTrue(rogue.action(ROGUE));
            await(() -> status(bard, "Ember / Bard") && status(rogue, "Slate / Rogue"), "faction role HUD");
            assertEquals("Bard", self(bard).role());
            assertEquals("Rogue", self(rogue).role());
            for (var client : clients) {
                assertEquals(0, fighter(client, bard.id()).side());
                assertEquals(1, fighter(client, rogue.id()).side());
            }
            for (var client : clients) assertTrue(client.ready());
            await(() -> phase(clients, Phase.FIGHT), "all clients enter the 2v2 fight");
            await(() -> bard.view().status().stream().map(ENERGY::matcher)
                    .anyMatch(matcher -> matcher.find() && Integer.parseInt(matcher.group(1)) >= 40), "bard energy");
            assertTrue(bard.action(FactionMatch.ABILITY));
            await(() -> bard.view().effects().containsKey(Effect.STRENGTH)
                    && ally.view().effects().containsKey(Effect.STRENGTH), "team strength aura");
            assertFalse(slate.view().effects().containsKey(Effect.STRENGTH));
            assertFalse(rogue.view().effects().containsKey(Effect.STRENGTH));
            assertTrue(rogue.view().effects().containsKey(Effect.SPEED));
            assertTrue(guest.input(new Intent(0, 0, 0, 0, 0, false, false, false, false, 0)));
            assertTrue(ally.input(new Intent(0, 0, 0, 0, 0, false, false, false, false, 0)));
            assertTrue(rogue.input(new Intent(0, 0, 0, 0, 0, false, false, false, false, 0)));

            long sequence = 1;
            long deadline = deadline();
            while (!clients.stream().allMatch(client -> status(client, "Hill contested"))) {
                if (System.nanoTime() >= deadline) fail("Both teams did not reach the courtyard hill");
                drive(bard, sequence, .72);
                drive(slate, sequence++, -.72);
                Thread.sleep(30);
            }
            assertTrue(Math.abs(self(bard).z()) < 2 && Math.abs(self(slate).z()) < 2);
            assertTrue(phase(clients, Phase.FIGHT));
            deadline = deadline();
            while (!phase(clients, Phase.RESULT)) {
                if (System.nanoTime() >= deadline) fail("Uncontested courtyard hill did not resolve");
                drive(bard, sequence, .72);
                drive(slate, sequence++, -5);
                Thread.sleep(30);
            }
            for (var client : clients) {
                assertEquals(Result.OBJECTIVE, client.view().result());
                assertEquals(bard.id(), client.view().winner());
                assertTrue(status(client, "Ember DTR 2 / stash 12 / hills 1"));
            }
            assertEquals(Phase.WAITING, guest.view().phase());
            assertTrue(guest.view().status().isEmpty());
            assertTrue(guest.connected());
        }
    }

    private static PracticeClient connect(PracticeServer server, SecureTransport.ServerIdentity identity,
                                          String name, boolean register) throws IOException {
        return new PracticeClient(HOST, server.port(), identity.fingerprint(), name, PASSWORD.toCharArray(), register);
    }

    private static void drive(PracticeClient client, long sequence, double z) {
        Fighter fighter = self(client);
        double offset = z - fighter.z();
        float yaw = offset < 0 ? -90 : 90;
        float forward = Math.abs(offset) > .18 ? 1 : 0;
        assertTrue(client.input(new Intent(sequence, yaw, 0, forward, 0, false, false, false, false, 0)));
    }

    private static Fighter self(PracticeClient client) {
        return fighter(client, client.id());
    }

    private static Fighter fighter(PracticeClient client, int id) {
        return client.view().fighters().stream().filter(fighter -> fighter.id() == id).findFirst().orElseThrow();
    }

    private static boolean status(PracticeClient client, String text) {
        return client.view().status().stream().anyMatch(line -> line.contains(text));
    }

    private static boolean phase(List<PracticeClient> clients, Phase phase) {
        return clients.stream().allMatch(client -> client.view().phase() == phase);
    }

    private static void await(BooleanSupplier condition, String label) throws InterruptedException {
        long deadline = deadline();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() >= deadline) fail("Timed out waiting for " + label);
            Thread.sleep(10);
        }
    }

    private static long deadline() { return System.nanoTime() + TimeUnit.SECONDS.toNanos(6); }
}
