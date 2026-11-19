package dev.jayms.combat.faction;

import dev.jayms.combat.CombatEngine.Effect;
import dev.jayms.combat.CombatEngine.Intent;
import dev.jayms.combat.CombatEngine.Item;
import dev.jayms.combat.practice.PracticeMatch.Fighter;
import dev.jayms.combat.practice.PracticeMatch.Phase;
import dev.jayms.combat.practice.PracticeMatch.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class FactionMatchTest {
    @TempDir Path temporary;

    private static final FactionMatch.Settings DEFAULT = settings(1_200);

    private static FactionMatch.Settings settings(int capture) {
        return new FactionMatch.Settings(2, 2_000, 2, capture, 3, 140, 50, 500, 50_000, 2_000);
    }

    @Test
    void bardBuffsOnlyItsFactionAndSpendsEnergyOncePerAbility() throws Exception {
        var lobby = new FactionMatch(null, DEFAULT, new TestClock());
        int bard = lobby.join("Bard");
        int enemy = lobby.join("Slate");
        int ally = lobby.join("Ally");
        int opponent = lobby.join("Rogue");
        int spectator = lobby.join("Observer");
        lobby.action(bard, 2);
        lobby.action(ally, 2);
        lobby.action(opponent, 4);
        assertEquals(0, fighter(lobby, bard).side());
        assertEquals("Bard", fighter(lobby, bard).role());
        assertEquals(1, fighter(lobby, opponent).side());
        assertEquals("Rogue", fighter(lobby, opponent).role());
        assertTrue(has(lobby, ally, "already has a Bard"));
        assertTrue(lobby.view(spectator).inventory().isEmpty());
        assertEquals(spectator, lobby.view(spectator).spectatorId());
        start(lobby, bard, enemy, ally, opponent);
        advance(lobby, 48);
        assertTrue(lobby.view(ally).effects().containsKey(Effect.REGENERATION));
        assertFalse(lobby.view(enemy).effects().containsKey(Effect.REGENERATION));
        lobby.action(bard, FactionMatch.ABILITY);
        assertTrue(lobby.view(bard).effects().containsKey(Effect.STRENGTH));
        assertTrue(lobby.view(ally).effects().containsKey(Effect.STRENGTH));
        assertFalse(lobby.view(enemy).effects().containsKey(Effect.STRENGTH));
        var after = lobby.view(bard);
        lobby.action(bard, FactionMatch.ABILITY);
        assertEquals(after.status(), lobby.view(bard).status());
        assertEquals(after.effects(), lobby.view(bard).effects());
        assertTrue(lobby.view(opponent).effects().containsKey(Effect.SPEED));
    }

    @Test
    void contestedHillResetsThenCapturesForTheSoleHoldingFaction() throws Exception {
        var lobby = new FactionMatch(null, settings(26), new TestClock());
        int ember = lobby.join("Ember");
        int slate = lobby.join("Slate");
        start(lobby, ember, slate);
        for (int i = 0; i < 80; i++) {
            drive(lobby, ember, 0, .8);
            drive(lobby, slate, 0, -.8);
            lobby.tick();
        }
        assertEquals(Phase.FIGHT, lobby.view(ember).phase());
        assertTrue(has(lobby, ember, "Hill contested"));
        for (int i = 0; i < 65 && lobby.view(ember).phase() == Phase.FIGHT; i++) {
            drive(lobby, slate, 0, -4.5);
            lobby.tick();
        }
        assertEquals(Result.OBJECTIVE, lobby.view(ember).result());
        assertEquals(ember, lobby.view(slate).winner());
        assertTrue(has(lobby, ember, "Ember DTR 2 / stash 12 / hills 1"));
        assertFalse(has(lobby, ember, "Raid"));
    }

    @Test
    void archerTagPreventsSafeZoneEscapeAndLoggerKeepsItsPoseOnReconnect() throws Exception {
        var lobby = new FactionMatch(null, DEFAULT, new TestClock());
        int archer = lobby.join("Archer");
        int target = lobby.join("North");
        lobby.action(archer, 3);
        start(lobby, archer, target);
        arrow(lobby, archer, target);
        assertTrue(lobby.view(target).effects().containsKey(Effect.MARK));
        assertTrue(has(lobby, target, "Combat tag"));
        for (int i = 0; i < 40; i++) {
            drive(lobby, target, 0, -14);
            lobby.tick();
        }
        assertTrue(fighter(lobby, target).z() >= -11.51);
        lobby.leave(target);
        assertTrue(lobby.view(target).inventory().isEmpty());
        assertTrue(fighter(lobby, target).health() < 20);
        int rejoined = lobby.join("north");
        assertEquals(target, rejoined);
        assertTrue(lobby.view(rejoined).effects().containsKey(Effect.MARK));
        assertTrue(has(lobby, rejoined, "Rejoined your combat position"));
    }

    @Test
    void aRestartDuringTaggedLogoutAppliesExactlyOnePersistentDefeat() throws Exception {
        var file = temporary.resolve("season.dat");
        var clock = new TestClock();
        var lobby = new FactionMatch(file, DEFAULT, clock);
        int archer = lobby.join("Archer");
        int target = lobby.join("North");
        lobby.action(archer, 3);
        start(lobby, archer, target);
        arrow(lobby, archer, target);
        lobby.leave(target);

        var reopened = new FactionMatch(file, DEFAULT, clock);
        var repeated = new FactionMatch(file, DEFAULT, clock);
        int returning = reopened.join("NORTH");
        assertTrue(has(reopened, returning, "Deathban"));
        assertTrue(has(reopened, returning, "Slate DTR 1"));
        assertTrue(has(repeated, repeated.join("north"), "Slate DTR 1"));
        assertEquals(returning, reopened.view(returning).spectatorId());
        clock.time += 501;
        reopened.action(returning, FactionMatch.JOIN_SLATE);
        assertEquals(0, reopened.view(returning).spectatorId());
        clock.time += 50_001;
        reopened.tick();
        assertTrue(has(reopened, returning, "Slate DTR 2"));
        reopened.endSeason();
        assertTrue(has(new FactionMatch(file, DEFAULT, clock), 0, "season 1 / ended"));
        reopened.beginSeason();
        assertTrue(has(new FactionMatch(file, DEFAULT, clock), 0, "season 2 / opening"));
    }

    @Test
    void defaultTeamEliminationOpensBoundedRaidBeforeDeathbansExpire() throws Exception {
        var clock = new TestClock();
        var file = temporary.resolve("raid.dat");
        var settings = FactionMatch.Settings.DEFAULT;
        var lobby = new FactionMatch(file, settings, clock);
        int ember = lobby.join("Ember");
        int slate = lobby.join("Slate");
        int emberGuard = lobby.join("EmberGuard");
        int slateGuard = lobby.join("SlateGuard");
        for (int id : new int[] {ember, slate, emberGuard, slateGuard}) lobby.ready(id);
        assertEquals(Phase.COUNTDOWN, lobby.view(ember).phase());
        advance(lobby, settings.countdownTicks());
        assertEquals(Phase.FIGHT, lobby.view(ember).phase());
        send(lobby, ember, -90, -80, 0, false, true, 7);
        lobby.tick();
        assertEquals(Item.EMPTY, lobby.view(ember).inventory().get(7).item());
        for (int i = 0; i < 145; i++) {
            drive(lobby, ember, 0, -14);
            lobby.tick();
        }
        assertTrue(fighter(lobby, ember).z() >= -11.51);
        defeat(lobby, ember, slate);
        assertEquals(Phase.FIGHT, lobby.view(ember).phase());
        assertTrue(has(lobby, ember, "Slate DTR 1"));
        assertTrue(has(lobby, slate, "Deathban"));
        defeat(lobby, ember, slateGuard);

        assertEquals(Phase.RESULT, lobby.view(ember).phase());
        assertEquals(Result.KNOCKOUT, lobby.view(ember).result());
        assertEquals(ember, lobby.view(ember).winner());
        assertTrue(has(lobby, ember, "Slate DTR 0 RAIDABLE"));
        assertTrue(has(lobby, ember, "Raid Slate stash / 20s / V at enemy home"));
        assertTrue(has(lobby, slateGuard, "Deathban"));
        assertFalse(has(lobby, slateGuard, "V at enemy home"));
        lobby.ready(ember);
        assertFalse(fighter(lobby, ember).ready());
        assertThrows(IllegalStateException.class, lobby::endSeason);

        int pearls = lobby.view(ember).inventory().get(1).count();
        int confirmedHits = fighter(lobby, ember).hits();
        send(lobby, ember, -90, -80, 0, true, true, 1);
        lobby.tick();
        assertEquals(pearls, lobby.view(ember).inventory().get(1).count());
        assertEquals(confirmedHits, fighter(lobby, ember).hits());
        assertFalse(fighter(lobby, ember).swinging());
        assertTrue(lobby.view(ember).missiles().isEmpty());
        for (int i = 0; i < 145; i++) {
            drive(lobby, ember, 0, -13);
            lobby.tick();
        }
        assertTrue(fighter(lobby, ember).z() < -12.5);
        lobby.action(ember, FactionMatch.RAID);
        assertEquals(Item.HEALING, lobby.view(ember).inventory().get(7).item());
        assertTrue(has(lobby, ember, "Slate DTR 0 RAIDABLE / stash 11"));
        lobby.action(ember, FactionMatch.RAID);
        assertTrue(has(lobby, ember, "Slate DTR 0 RAIDABLE / stash 11"));
        assertEquals(slate, lobby.view(slate).spectatorId());
        advance(lobby, lobby.view(ember).ticksLeft());
        assertFalse(has(lobby, ember, "Raid Slate stash"));
        lobby.action(ember, FactionMatch.RAID);
        assertTrue(has(lobby, ember, "Slate DTR 0 RAIDABLE / stash 11"));
        lobby.ready(ember);
        assertEquals(Phase.WAITING, lobby.view(ember).phase());
        assertTrue(fighter(lobby, ember).ready());

        var reopened = new FactionMatch(file, settings, clock);
        assertTrue(has(reopened, reopened.join("SlateGuard"), "Deathban"));
        assertTrue(has(reopened, 0, "Slate DTR 0 RAIDABLE / stash 11"));
    }

    @Test
    void corruptSaveIsRejectedWithoutReplacingItsContents() throws Exception {
        var file = temporary.resolve("broken.dat");
        var broken = new byte[] {0, 1, 2};
        Files.write(file, broken);
        assertThrows(IOException.class, () -> new FactionMatch(file, DEFAULT, new TestClock()));
        assertArrayEquals(broken, Files.readAllBytes(file));
    }

    private static void arrow(FactionMatch lobby, int archer, int target) {
        for (int i = 0; i < 50; i++) {
            drive(lobby, archer, 0, 8);
            drive(lobby, target, 0, -7);
            lobby.tick();
        }
        for (int i = 0; i < 24; i++) {
            send(lobby, archer, -90, 0, 0, false, true, 5);
            lobby.tick();
        }
        send(lobby, archer, -90, 0, 0, false, false, 5);
        advance(lobby, 13);
        assertTrue(fighter(lobby, target).health() < 20);
        assertTrue(has(lobby, target, "Combat tag"));
    }

    private static void defeat(FactionMatch lobby, int attacker, int target) {
        for (int i = 0; i < 280 && fighter(lobby, target).health() > 0; i++) {
            if (i < 55) {
                drive(lobby, attacker, 0, 4);
                drive(lobby, target, 0, -4);
            } else {
                Fighter from = fighter(lobby, attacker), to = fighter(lobby, target);
                double dx = to.x() - from.x(), dz = to.z() - from.z();
                double distance = Math.hypot(dx, dz);
                float yaw = (float) Math.toDegrees(Math.atan2(dz, dx));
                float pitch = (float) Math.toDegrees(Math.atan2(to.y() - from.y() - .35, distance));
                send(lobby, attacker, yaw, pitch, distance > 1.8 ? 1 : 0, distance < 3, false, 0);
            }
            lobby.tick();
        }
        assertEquals(0, fighter(lobby, target).health());
    }

    private static void start(FactionMatch lobby, int... participants) {
        for (int id : participants) lobby.ready(id);
        assertEquals(Phase.COUNTDOWN, lobby.view(participants[0]).phase());
        advance(lobby, 2);
        assertEquals(Phase.FIGHT, lobby.view(participants[0]).phase());
    }

    private static void drive(FactionMatch lobby, int id, double x, double z) {
        Fighter player = fighter(lobby, id);
        double dx = x - player.x(), dz = z - player.z();
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx));
        send(lobby, id, yaw, 0, Math.hypot(dx, dz) > .18 ? 1 : 0, false, false, 0);
    }

    private static void send(FactionMatch lobby, int id, float yaw, float pitch, float forward,
                             boolean attack, boolean use, int slot) {
        lobby.input(id, new Intent(lobby.view(id).tick(), yaw, pitch, forward, 0, false, false, attack, use, slot));
    }

    private static Fighter fighter(FactionMatch lobby, int id) {
        return lobby.view(id).fighters().stream().filter(f -> f.id() == id).findFirst().orElseThrow();
    }

    private static boolean has(FactionMatch lobby, int id, String status) {
        return lobby.view(id).status().stream().anyMatch(line -> line.contains(status));
    }

    private static void advance(FactionMatch lobby, int amount) {
        for (int i = 0; i < amount; i++) lobby.tick();
    }

    private static final class TestClock extends Clock {
        long time = 1_000_000;
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(instant(), zone); }
        @Override public Instant instant() { return Instant.ofEpochMilli(time); }
    }
}
