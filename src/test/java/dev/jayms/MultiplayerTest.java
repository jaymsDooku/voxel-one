package dev.jayms;

import dev.jayms.net.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;

class MultiplayerTest {
    @TempDir Path temp;
    private void until(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + 3000000000L;
        while (!condition.getAsBoolean()) { if (System.nanoTime() > deadline) fail("Timed out waiting for network event"); Thread.sleep(10); }
    }
    private Thread run(MultiplayerServer server) {
        Thread t = new Thread(() -> { try { server.run(); } catch (Exception e) { throw new RuntimeException(e); } }); t.start(); return t;
    }
    @Test void movementEditsLateJoinDisconnectAndPersistence() throws Exception {
        Path save = temp.resolve("world.dat");
        try (MultiplayerServer server = new MultiplayerServer("127.0.0.1", 0, save)) {
            Thread thread = run(server);
            try (MultiplayerClient a = new MultiplayerClient("127.0.0.1", server.port()); MultiplayerClient b = new MultiplayerClient("127.0.0.1", server.port())) {
                until(() -> { a.poll(); b.poll(); return a.players.containsKey(b.id) && b.players.containsKey(a.id); });
                a.move(new Protocol.Pose(a.id, 9, Protocol.spawnY(), 24, 45, 0));
                until(() -> { b.poll(); return b.players.get(a.id).x() == 9; });
                var edit = new Protocol.Edit(10, (int)Protocol.spawnY() - 1, 24, 0);
                a.edit(edit); List<Protocol.Edit> seen = new ArrayList<>();
                until(() -> { seen.addAll(b.poll()); return seen.contains(edit); });
                try (MultiplayerClient late = new MultiplayerClient("127.0.0.1", server.port())) { assertTrue(late.initialEdits.contains(edit)); }
                b.close(); until(() -> { a.poll(); return !a.players.containsKey(b.id); });
                // Invalid coordinates and edits out of reach are ignored.
                a.edit(new Protocol.Edit(999, 0, 0, 3)); a.edit(new Protocol.Edit(50, 0, 50, 3));
            }
            server.close(); thread.join(2000); assertFalse(thread.isAlive());
        }
        try (MultiplayerServer server = new MultiplayerServer("127.0.0.1", 0, save)) {
            Thread thread = run(server);
            try (MultiplayerClient c = new MultiplayerClient("127.0.0.1", server.port())) { assertEquals(1, c.initialEdits.size()); }
            server.close(); thread.join(2000);
        }
    }
    @Test void validatesNonFiniteAndOutOfBoundsPoses() {
        assertFalse(new Protocol.Pose(1, Float.NaN, 0, 0, 0, 0).valid());
        assertFalse(new Protocol.Pose(1, 500, 0, 0, 0, 0).valid());
    }
}
