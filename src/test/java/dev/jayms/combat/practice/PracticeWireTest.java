package dev.jayms.combat.practice;

import dev.jayms.combat.CombatEngine.Item;
import dev.jayms.combat.CombatEngine.Intent;
import dev.jayms.combat.CombatEngine.AttackView;
import dev.jayms.combat.CombatEngine.PoseView;
import dev.jayms.combat.practice.PracticeMatch.*;
import dev.jayms.net.SecureTransport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import javax.net.ssl.SSLServerSocket;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.InetAddress;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class PracticeWireTest {
    private static final String HOST = "127.0.0.1";
    @TempDir Path temporary;

    @Test void newestSnapshotsCarryVelocityDamageAndTeleportMetadata() throws Exception {
        var view = fixture();
        assertEquals(view, roundTrip(view, PracticeWire.VERSION));
        assertEquals(view, roundTrip(view, PracticeWire.POSE_VERSION));
        var fighter = roundTrip(view, PracticeWire.LEGACY_VERSION).fighters().get(0);
        assertEquals(view.fighters().get(0).health(), fighter.health());
        assertEquals(0, fighter.vx());
        assertEquals(-1, fighter.hurtTick());
        assertEquals(0, fighter.motionEpoch());
    }

    @Test void attackViewRoundTripsWithoutChangingLegacyControls() throws Exception {
        var click = new Intent(7, -90, 5, 1, 0, false, true, true, false, 0,
                new AttackView(2, new PoseView(35, 36, 35.5), new PoseView(34, 36, 35)));
        var move = new Intent(8, 0, 0, 0, 1, false, false, false, false, 2);
        for (int version = PracticeWire.LEGACY_VERSION; version <= PracticeWire.VERSION; version++) {
            var bytes = new ByteArrayOutputStream();
            var out = new DataOutputStream(bytes);
            PracticeWire.writeIntent(out, click, version);
            PracticeWire.writeIntent(out, move, version);
            try (var in = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
                assertEquals(PracticeWire.CONTROL, in.readUnsignedByte());
                var decoded = PracticeWire.readIntent(in, version);
                assertEquals(click.yaw(), decoded.yaw());
                assertTrue(decoded.sprint() && decoded.attack());
                assertEquals(version == PracticeWire.VERSION ? click.attackView() : null, decoded.attackView());
                assertEquals(PracticeWire.CONTROL, in.readUnsignedByte());
                assertEquals(move, PracticeWire.readIntent(in, version));
                assertEquals(-1, in.read());
            }
        }
    }

    @Test @Timeout(15)
    void legacyClientCanJoinTheUpdatedServer() throws Exception {
        var identity = SecureTransport.server(temporary.resolve("tls"));
        try (var server = new PracticeServer(HOST, 0, identity.context(), Kit.NO_DEBUFF);
             var client = SecureTransport.connect(HOST, server.port(), identity.fingerprint())) {
            var out = new DataOutputStream(client.getOutputStream());
            var in = new DataInputStream(client.getInputStream());
            out.writeInt(PracticeWire.MAGIC);
            out.writeInt(PracticeWire.LEGACY_VERSION);
            out.writeByte(3);
            out.writeBytes("Old");
            out.writeByte(0);
            out.flush();
            int id = in.readInt();
            var first = PracticeWire.readView(in, PracticeWire.LEGACY_VERSION);
            assertEquals(id, first.fighters().get(0).id());
            assertEquals(36, first.inventory().size());
            assertEquals(-1, first.fighters().get(0).hurtTick());
        }
    }

    @Test @Timeout(15)
    void updatedClientFallsBackWhenAnOlderServerClosesOnNewerVersions() throws Exception {
        var identity = SecureTransport.server(temporary.resolve("tls"));
        try (var listener = (SSLServerSocket) identity.context().getServerSocketFactory()
                .createServerSocket(0, 3, InetAddress.getByName(HOST))) {
            var worker = Executors.newSingleThreadExecutor();
            try {
                var previousServer = worker.submit(() -> {
                    for (int version = PracticeWire.VERSION; version > PracticeWire.LEGACY_VERSION; version--) {
                        try (var rejected = listener.accept()) {
                            rejected.setSoTimeout(3000);
                            var in = new DataInputStream(rejected.getInputStream());
                            assertEquals(PracticeWire.MAGIC, in.readInt());
                            assertEquals(version, in.readInt());
                        }
                    }
                    try (var accepted = listener.accept()) {
                        accepted.setSoTimeout(3000);
                        var in = new DataInputStream(accepted.getInputStream());
                        assertEquals(PracticeWire.MAGIC, in.readInt());
                        assertEquals(PracticeWire.LEGACY_VERSION, in.readInt());
                        assertEquals(3, in.readUnsignedByte());
                        assertArrayEquals(new byte[]{'N', 'e', 'w'}, in.readNBytes(3));
                        assertEquals(0, in.readUnsignedByte());
                        var out = new DataOutputStream(accepted.getOutputStream());
                        out.writeInt(1);
                        PracticeWire.writeView(out, fixture(), PracticeWire.LEGACY_VERSION);
                        out.flush();
                    }
                    return null;
                });
                try (var client = new PracticeClient(HOST, listener.getLocalPort(), identity.fingerprint(), "New")) {
                    assertEquals(1, client.id());
                    assertEquals(-1, client.view().fighters().get(0).hurtTick());
                }
                previousServer.get(5, TimeUnit.SECONDS);
            } finally {
                worker.shutdownNow();
            }
        }
    }

    private static View roundTrip(View view, int version) throws Exception {
        var bytes = new ByteArrayOutputStream();
        PracticeWire.writeView(new DataOutputStream(bytes), view, version);
        try (var in = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            View copy = PracticeWire.readView(in, version);
            assertEquals(-1, in.read());
            return copy;
        }
    }

    private static View fixture() {
        var fighter = new Fighter(1, "Aster", 1, 73, 2, -90, 0, 16, true, false, false, Item.SWORD,
                2, 2, 0, 31, -1, "", .4f, .22f, -.12f, 42, 3);
        return new View(Phase.FIGHT, Kit.NO_DEBUFF, Result.NONE, 0, 140, 0, 44,
                List.of(fighter), List.of(), 20, 0, Map.of(), List.of(), List.of(), 42, -1, List.of());
    }
}
