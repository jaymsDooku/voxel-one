package dev.jayms;

import static org.junit.jupiter.api.Assertions.*;

import dev.jayms.net.*;
import dev.jayms.net.model.*;

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
                a.move(
                        new Protocol.Pose(
                                a.id,
                                9,
                                Protocol.spawnY(),
                                24,
                                45,
                                0,
                                2,
                                .6f,
                                false,
                                .25f,
                                Blocks.STONE));
                until(
                        () -> {
                            b.poll();
                            var received = b.players.get(a.id);
                            return received != null && received.x() == 9;
                        });
                assertEquals(.25f, b.players.get(a.id).swingProgress());
                assertEquals(Blocks.STONE, b.players.get(a.id).heldItem());
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
                var mine = new Protocol.Edit(8, (int) Protocol.spawnY() - 1, 24, 0);
                assertTrue(a.edit(mine, a.spawn));
                until(
                        () -> {
                            a.poll();
                            return a.inventory.count(0) == 1;
                        });
                var edit =
                        new Protocol.Edit(10, (int) Protocol.spawnY() + 2, 24, a.inventory.type(0));
                assertTrue(a.edit(edit, new Protocol.Pose(a.id, 10.5f, edit.y(), 24.5f, 0, 0)));
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
    void pickupIsExclusiveInventoryCannotBeForgedAndSeedSurvivesRestart() throws Exception {
        Path save = temp.resolve("survival.dat");
        try (var server =
                new MultiplayerServer("127.0.0.1", 0, save, accounts, identity.context(), 42)) {
            Thread thread = run(server);
            try (var a = client(server, "alice", "correct-password-a", false);
                    var b = client(server, "bob", "correct-password-b", false)) {
                assertEquals(42, a.seed);
                assertEquals(a.seed, b.seed);
                until(
                        () -> {
                            a.poll();
                            b.poll();
                            return a.players.containsKey(b.id);
                        });
                var p = a.spawn;
                var broken =
                        new Protocol.Edit(
                                (int) Math.floor(p.x()),
                                (int) Math.floor(p.y()) - 1,
                                (int) Math.floor(p.z()),
                                0);
                assertTrue(a.edit(broken, p));
                until(
                        () -> {
                            a.poll();
                            b.poll();
                            return a.inventory.count(0) + b.inventory.count(0) == 1;
                        });
                assertTrue(a.edit(broken, p));
                until(
                        () -> {
                            a.poll();
                            b.poll();
                            return !a.pending(broken);
                        });
                assertEquals(1, a.inventory.count(0) + b.inventory.count(0));
                MultiplayerClient owner = a.inventory.count(0) > 0 ? a : b;
                int type = owner.inventory.type(0);
                owner.swap(0, 35);
                until(
                        () -> {
                            owner.poll();
                            return owner.inventory.count(35) == 1;
                        });
                var place = new Protocol.Edit(broken.x() + 2, broken.y() + 3, broken.z(), type);
                assertTrue(owner.edit(place, owner.spawn, 0));
                List<Protocol.Edit> replies = new ArrayList<>();
                until(
                        () -> {
                            replies.addAll(owner.poll());
                            return !owner.pending(place);
                        });
                assertFalse(replies.contains(place));
                assertEquals(1, owner.inventory.count(35));
            }
            server.close();
            thread.join(2000);
        }
        try (var server =
                new MultiplayerServer("127.0.0.1", 0, save, accounts, identity.context(), 999)) {
            Thread thread = run(server);
            try (var a = client(server, "alice", "correct-password-a", false);
                    var b = client(server, "bob", "correct-password-b", false)) {
                assertEquals(42, a.seed);
                assertEquals(1, a.inventory.count(35) + b.inventory.count(35));
                assertEquals(1, a.initialEdits.size());
            }
            server.close();
            thread.join(2000);
        }
    }

    @Test
    void largeSavedDropSnapshotDoesNotOverflowWriterQueue() throws Exception {
        Path save = temp.resolve("drops.dat");
        Inventory full = new Inventory();
        full.add(Blocks.STONE, Inventory.SIZE * Inventory.STACK);
        try (var out = new java.io.DataOutputStream(Files.newOutputStream(save))) {
            out.writeInt(Protocol.MAGIC);
            out.writeInt(-3);
            out.writeLong(42);
            out.writeInt(0);
            out.writeInt(1);
            out.writeUTF("alice");
            full.write(out);
            out.writeByte(20);
            out.writeInt(600);
            for (int i = 1; i <= 600; i++)
                new ItemDrop(i, Blocks.SAND, 1, 100 + i, 0, 100).write(out);
        }
        try (var server =
                new MultiplayerServer("127.0.0.1", 0, save, accounts, identity.context())) {
            Thread thread = run(server);
            try (var a = client(server, "alice", "correct-password-a", false)) {
                assertEquals(600, a.drops.size());
                assertEquals(64, a.inventory.count(35));
                for (int i = 0; i < 10; i++) {
                    a.poll();
                    a.move(a.spawn);
                    Thread.sleep(20);
                }
                assertTrue(a.connected());
                assertEquals(600, a.drops.size());
            }
            server.close();
            thread.join(2000);
        }
    }

    @Test
    void voxelModelsSharePlaceBreakAndSurviveWorldRestart() throws Exception {
        Path save = temp.resolve("model-world.dat");
        String fingerprint;
        SparseVoxelOctree tree = new SparseVoxelOctree(8);
        tree.set(3, 3, 3, 0xffe65a91);
        var custom = new ModelDefinition("Tiny pink voxel", tree);
        fingerprint = custom.fingerprint();
        Protocol.Edit placed;
        try (var server =
                new MultiplayerServer("127.0.0.1", 0, save, accounts, identity.context())) {
            Thread thread = run(server);
            try (var a = client(server, "alice", "correct-password-a", false);
                    var b = client(server, "bob", "correct-password-b", false)) {
                until(
                        () -> {
                            a.poll();
                            b.poll();
                            return a.players.containsKey(b.id);
                        });
                assertTrue(a.createModel(ModelGenerators.flowerPot()));
                until(
                        () -> {
                            a.poll();
                            return a.modelResults == 1 && a.inventory.type(0) == Blocks.FLOWER_POT;
                        });
                assertEquals(1, a.inventory.count(0));
                assertTrue(a.createModel(ModelGenerators.flowerPot()));
                until(
                        () -> {
                            a.poll();
                            return a.modelResults == 2;
                        });
                assertEquals(1, a.inventory.count(0));
                assertTrue(a.modelMessage.contains("two seconds"));
                assertTrue(b.createModel(custom));
                until(
                        () -> {
                            a.poll();
                            b.poll();
                            return a.models.get(9) != null && b.inventory.type(0) == 9;
                        });
                assertEquals(fingerprint, a.models.get(9).definition().fingerprint());
                var pot = new Protocol.Edit(10, (int) a.spawn.y() + 1, 24, Blocks.FLOWER_POT);
                // This pose overlaps the containing world cell, but misses every tiny pot voxel.
                var click = new Protocol.Pose(a.id, 11.15f, pot.y(), 24.5f, 0, 0);
                assertTrue(a.edit(pot, click, 0));
                List<Protocol.Edit> seen = new ArrayList<>();
                until(
                        () -> {
                            seen.addAll(b.poll());
                            a.poll();
                            return seen.contains(pot) && !a.pending(pot);
                        });
                assertEquals(0, a.inventory.count(0));
                var broken = new Protocol.Edit(pot.x(), pot.y(), pot.z(), 0);
                assertTrue(a.edit(broken, click, 0));
                until(
                        () -> {
                            a.poll();
                            b.poll();
                            return !a.pending(broken)
                                    && a.inventory.type(0) == Blocks.FLOWER_POT
                                    && a.inventory.count(0) == 1;
                        });
                placed = new Protocol.Edit(12, pot.y(), 24, 9);
                assertTrue(b.edit(placed, b.spawn, 0));
                until(
                        () -> {
                            b.poll();
                            return !b.pending(placed) && b.inventory.count(0) == 0;
                        });
                try (var late = client(server, "late", "correct-password-c", false)) {
                    assertEquals(fingerprint, late.models.get(9).definition().fingerprint());
                    assertTrue(late.initialEdits.contains(placed));
                }
            }
            server.close();
            thread.join(2000);
        }
        try (var server =
                new MultiplayerServer("127.0.0.1", 0, save, accounts, identity.context())) {
            Thread thread = run(server);
            try (var a = client(server, "alice", "correct-password-a", false)) {
                assertEquals(fingerprint, a.models.get(9).definition().fingerprint());
                assertTrue(a.initialEdits.contains(placed));
                assertEquals(Blocks.FLOWER_POT, a.inventory.type(0));
                assertEquals(1, a.inventory.count(0));
            }
            server.close();
            thread.join(2000);
        }
    }

    @Test
    void validatesNonFiniteAndOutOfBoundsPoses() {
        assertFalse(new Protocol.Pose(1, Float.NaN, 0, 0, 0, 0).valid());
        assertFalse(new Protocol.Pose(1, Terrain.LIMIT + 1f, 0, 0, 0, 0).valid());
    }
}
