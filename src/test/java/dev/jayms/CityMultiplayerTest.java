package dev.jayms;

import static org.junit.jupiter.api.Assertions.*;

import dev.jayms.net.*;
import dev.jayms.net.city.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.util.*;
import java.util.function.BooleanSupplier;

class CityMultiplayerTest {
    @TempDir Path temp;

    void until(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + 10_000_000_000L;
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) fail("City network timeout");
            Thread.sleep(15);
        }
    }

    @Test
    void sharedRoadsZoningHorseOwnershipLateJoinAndRestart() throws Exception {
        var tls = SecureTransport.server(temp.resolve("tls"));
        var accounts = new AccountStore(temp.resolve("accounts"));
        accounts.register("alice", "correct-password-a".toCharArray());
        accounts.register("bob", "correct-password-b".toCharArray());
        accounts.register("late", "correct-password-c".toCharArray());
        Path save = temp.resolve("city-world.dat");
        int roadCount;
        try (var server =
                new MultiplayerServer(
                        "127.0.0.1",
                        0,
                        save,
                        accounts,
                        tls.context(),
                        Terrain.DEFAULT_SEED,
                        GameConfig.cityGame())) {
            Thread thread =
                    new Thread(
                            () -> {
                                try {
                                    server.run();
                                } catch (Exception e) {
                                    throw new RuntimeException(e);
                                }
                            });
            thread.start();
            try (var a =
                            new MultiplayerClient(
                                    "127.0.0.1",
                                    server.port(),
                                    "alice",
                                    "correct-password-a".toCharArray(),
                                    false,
                                    tls.fingerprint());
                    var b =
                            new MultiplayerClient(
                                    "127.0.0.1",
                                    server.port(),
                                    "bob",
                                    "correct-password-b".toCharArray(),
                                    false,
                                    tls.fingerprint())) {
                assertTrue(a.city.config().city());
                assertEquals(18, a.city.citizens().size());
                assertEquals(a.city.roads(), b.city.roads());
                roadCount = a.city.roads().size();
                assertTrue(
                        a.cityCommand(
                                new CityCommand(
                                        CityCommand.ROAD,
                                        1,
                                        List.of(
                                                new Polygon.Point(44, 24),
                                                new Polygon.Point(60, 24)))));
                var seen = new ArrayList<Protocol.Edit>();
                until(
                        () -> {
                            a.poll();
                            seen.addAll(b.poll());
                            // Each client receives city snapshots independently. Wait for the
                            // commanded road in both before comparing their authoritative state.
                            return b.city.roads().size() > roadCount
                                    && a.city.roads().stream()
                                            .anyMatch(r -> r.x() == 55 && r.z() == 24)
                                    && b.city.roads().stream()
                                            .anyMatch(r -> r.x() == 55 && r.z() == 24)
                                    && seen.stream()
                                            .anyMatch(
                                                    e ->
                                                            e.x() == 55
                                                                    && e.z() == 24
                                                                    && e.type() == Blocks.ROAD_LINE_X);
                        });
                assertEquals(a.city.roads(), b.city.roads());
                assertTrue(b.city.roads().stream().anyMatch(r -> r.x() == 55 && r.z() == 24 && r.type() == 1));
                assertTrue(b.city.economy().roadSpending() > 0);
                assertEquals(a.city.economy().roadSpending(), b.city.economy().roadSpending());
                assertEquals(35, b.city.economy().firms().size());
                Thread.sleep(550);
                var zone =
                        List.of(
                                new Polygon.Point(47, 26),
                                new Polygon.Point(59, 26),
                                new Polygon.Point(57, 36),
                                new Polygon.Point(49, 36));
                a.cityCommand(new CityCommand(CityCommand.ZONE, 0, zone));
                until(
                        () -> {
                            a.poll();
                            b.poll();
                            return b.city.zones().size() == 7;
                        });
                assertEquals(zone, b.city.zones().get(6).polygon().vertices());
                var h =
                        a.city.horses().stream()
                                .filter(m -> m.rider() == 0)
                                .findFirst()
                                .orElseThrow();
                a.move(new Protocol.Pose(a.id, h.x(), h.y(), h.z(), 0, 0));
                b.move(new Protocol.Pose(b.id, h.x(), h.y(), h.z(), 0, 0));
                Thread.sleep(550);
                a.cityCommand(new CityCommand(CityCommand.RIDE, h.id(), List.of()));
                until(
                        () -> {
                            a.poll();
                            b.poll();
                            return b.city.horses().stream()
                                    .anyMatch(m -> m.id() == h.id() && m.rider() == a.id);
                        });
                b.cityCommand(new CityCommand(CityCommand.RIDE, h.id(), List.of()));
                until(
                        () -> {
                            b.poll();
                            return b.notice().contains("already");
                        });
                a.move(new Protocol.Pose(a.id, 11, h.y() + .75f, 24, 30, 0));
                until(
                        () -> {
                            a.poll();
                            b.poll();
                            return b.city.horses().stream()
                                    .anyMatch(m -> m.id() == h.id() && m.x() == 11);
                        });
                try (var late =
                        new MultiplayerClient(
                                "127.0.0.1",
                                server.port(),
                                "late",
                                "correct-password-c".toCharArray(),
                                false,
                                tls.fingerprint())) {
                    assertEquals(7, late.city.zones().size());
                    assertTrue(
                            late.initialEdits.stream()
                                    .anyMatch(
                                            e ->
                                                    e.x() == 55
                                                            && e.z() == 24
                                                            && e.type() == Blocks.DIRT));
                }
                a.close();
                until(
                        () -> {
                            b.poll();
                            return b.city.horses().stream()
                                    .anyMatch(m -> m.id() == h.id() && m.rider() == 0);
                        });
            }
            server.close();
            thread.join(2000);
            assertFalse(thread.isAlive());
        }
        try (var server = new MultiplayerServer("127.0.0.1", 0, save, accounts, tls.context())) {
            Thread thread =
                    new Thread(
                            () -> {
                                try {
                                    server.run();
                                } catch (Exception e) {
                                    throw new RuntimeException(e);
                                }
                            });
            thread.start();
            try (var a =
                    new MultiplayerClient(
                            "127.0.0.1",
                            server.port(),
                            "alice",
                            "correct-password-a".toCharArray(),
                            false,
                            tls.fingerprint())) {
                assertTrue(a.city.config().city());
                assertEquals(7, a.city.zones().size());
                assertTrue(a.city.roads().size() > roadCount);
                assertTrue(a.city.roads().stream().anyMatch(r -> r.x() == 55 && r.z() == 24 && r.type() == 1));
                assertTrue(a.city.elapsed() > 0);
                assertTrue(a.city.economy().roadSpending() > 0);
                assertEquals(35, a.city.economy().firms().size());
                assertTrue(a.city.horses().stream().noneMatch(h -> h.rider() > 0));
            }
            server.close();
            thread.join(2000);
        }
    }
}
