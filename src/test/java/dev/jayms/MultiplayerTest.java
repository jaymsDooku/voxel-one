package dev.jayms;

import static org.junit.jupiter.api.Assertions.*;

import dev.jayms.net.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.function.BooleanSupplier;

class MultiplayerTest {
    @TempDir Path temp;
    private final List<Throwable> serverErrors = new ArrayList<>();
    private SecureTransport.ServerIdentity identity;
    private AccountStore accounts;

    @BeforeEach
    void setup() throws Exception {
        identity = SecureTransport.server(temp.resolve("tls"));
        accounts = new AccountStore(temp.resolve("accounts.db"));
        accounts.register("alice", "correct-password-a".toCharArray());
        accounts.register("bob", "correct-password-b".toCharArray());
        accounts.register("late", "correct-password-c".toCharArray());
    }

    private void until(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + 5000000000L;
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) fail("Timed out waiting for network event");
            Thread.sleep(10);
        }
    }

    private Thread run(MultiplayerServer server) {
        Thread t =
                new Thread(
                        () -> {
                            try {
                                server.run();
                            } catch (Exception e) {
                                synchronized (serverErrors) {
                                    serverErrors.add(e);
                                }
                            }
                        });
        t.start();
        return t;
    }

    private MultiplayerClient client(
            MultiplayerServer server, String name, String password, boolean register)
            throws IOException {
        return new MultiplayerClient(
                "127.0.0.1",
                server.port(),
                name,
                password.toCharArray(),
                register,
                identity.fingerprint());
    }

    @Test
    void movementEditsLateJoinDisconnectAndPersistence() throws Exception {
        Path save = temp.resolve("world.dat");
        try (var server =
                new MultiplayerServer("127.0.0.1", 0, save, accounts, identity.context())) {
            Thread thread = run(server);
            try (var a = client(server, "alice", "correct-password-a", false);
                    var b = client(server, "bob", "correct-password-b", false)) {
                until(
                        () -> {
                            a.poll();
                            b.poll();
                            return a.players.containsKey(b.id) && b.players.containsKey(a.id);
                        });
                assertEquals("alice", b.names.get(a.id));
                assertEquals("bob", a.names.get(b.id));
                a.move(new Protocol.Pose(a.id, 9, Protocol.spawnY(), 24, 45, 0));
                until(
                        () -> {
                            b.poll();
                            return b.players.get(a.id).x() == 9;
                        });
                var edit = new Protocol.Edit(10, (int) Protocol.spawnY() - 1, 24, 0);
                assertTrue(a.edit(edit, new Protocol.Pose(a.id, 9, Protocol.spawnY(), 24, 45, 0)));
                List<Protocol.Edit> seen = new ArrayList<>();
                until(
                        () -> {
                            seen.addAll(b.poll());
                            a.poll();
                            return seen.contains(edit) && !a.pending(edit);
                        });
                try (var late = client(server, "late", "correct-password-c", false)) {
                    assertTrue(late.initialEdits.contains(edit));
                    assertEquals("alice", late.names.get(a.id));
                }
                b.close();
                until(
                        () -> {
                            a.poll();
                            return !a.players.containsKey(b.id);
                        });
            }
            server.close();
            thread.join(2000);
            assertFalse(thread.isAlive());
        }
        try (var server =
                new MultiplayerServer(
                        "127.0.0.1",
                        0,
                        save,
                        new AccountStore(temp.resolve("accounts.db")),
                        identity.context())) {
            Thread thread = run(server);
            try (var c = client(server, "alice", "correct-password-a", false)) {
                assertEquals(1, c.initialEdits.size());
            }
            server.close();
            thread.join(2000);
        }
        assertTrue(serverErrors.isEmpty());
    }

    @Test
    void authenticationRegistrationDuplicateSessionsAndCertificatePin() throws Exception {
        try (var server =
                new MultiplayerServer("127.0.0.1", 0, null, accounts, identity.context())) {
            Thread thread = run(server);
            assertThrows(IOException.class, () -> client(server, "alice", "wrong-password", false));
            assertThrows(
                    IOException.class, () -> client(server, "unknown", "wrong-password", false));
            assertThrows(
                    IOException.class,
                    () ->
                            new MultiplayerClient(
                                    "127.0.0.1",
                                    server.port(),
                                    "alice",
                                    "correct-password-a".toCharArray(),
                                    false,
                                    "0".repeat(64)));
            try (var a = client(server, "ALICE", "correct-password-a", false)) {
                assertEquals("alice", a.username);
                assertThrows(
                        IOException.class,
                        () -> client(server, "alice", "correct-password-a", false));
                try (var fresh = client(server, "new_player", "a-new-good-password", true)) {
                    assertEquals("new_player", fresh.username);
                }
                assertThrows(
                        IOException.class,
                        () -> client(server, "new_player", "a-new-good-password", true));
            }
            server.close();
            thread.join(2000);
        }
    }

    @Test
    void placementUsesClickPoseInsteadOfStaleMovementAndRollsBack() throws Exception {
        try (var server =
                new MultiplayerServer("127.0.0.1", 0, null, accounts, identity.context())) {
            Thread thread = run(server);
            try (var a = client(server, "alice", "correct-password-a", false)) {
                // Last movement is at x=9. The placement pose is already inside the target block at
                // x=10.
                a.move(new Protocol.Pose(a.id, 9, Protocol.spawnY(), 24, 0, 0));
                var edit = new Protocol.Edit(10, (int) Protocol.spawnY(), 24, 3);
                assertTrue(
                        a.edit(
                                edit,
                                new Protocol.Pose(a.id, 10.5f, Protocol.spawnY(), 24.5f, 0, 0)));
                assertFalse(
                        a.edit(
                                edit,
                                new Protocol.Pose(
                                        a.id,
                                        9,
                                        Protocol.spawnY(),
                                        24,
                                        0,
                                        0))); // One pending edit per block.
                List<Protocol.Edit> replies = new ArrayList<>();
                until(
                        () -> {
                            replies.addAll(a.poll());
                            return !a.pending(edit);
                        });
                assertTrue(replies.contains(new Protocol.Edit(edit.x(), edit.y(), edit.z(), 0)));
                assertFalse(a.notice().isEmpty());
                assertTrue(a.edit(edit, new Protocol.Pose(a.id, 9, Protocol.spawnY(), 24, 0, 0)));
                until(
                        () -> {
                            replies.addAll(a.poll());
                            return !a.pending(edit);
                        });
                assertTrue(replies.contains(edit));
                var far = new Protocol.Edit(50, 25, 50, 3);
                assertTrue(a.edit(far, new Protocol.Pose(a.id, 9, Protocol.spawnY(), 24, 0, 0)));
                until(
                        () -> {
                            a.poll();
                            return !a.pending(far);
                        });
            }
            server.close();
            thread.join(2000);
        }
    }

    @Test
    void validatesNonFiniteAndOutOfBoundsPoses() {
        assertFalse(new Protocol.Pose(1, Float.NaN, 0, 0, 0, 0).valid());
        assertFalse(new Protocol.Pose(1, 500, 0, 0, 0, 0).valid());
    }
}
