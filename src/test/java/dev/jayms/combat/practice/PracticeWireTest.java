package dev.jayms.combat.practice;

import dev.jayms.combat.CombatEngine.Item;
import dev.jayms.combat.CombatEngine.Intent;
import dev.jayms.combat.CombatEngine.AttackView;
import dev.jayms.combat.CombatEngine.PoseView;
import dev.jayms.combat.CombatEngine.ProjectileState;
import dev.jayms.combat.CombatEngine.Stack;
import dev.jayms.combat.CombatEngine.Vec3;
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
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static dev.jayms.combat.CombatEngine.BOW_DURABILITY;
import static org.junit.jupiter.api.Assertions.*;

class PracticeWireTest {
    private static final String HOST = "127.0.0.1";
    @TempDir Path temporary;

    @Test void newestSnapshotsCarryImmediateImpulseAndOlderVersionsKeepTheirPoseMetadata() throws Exception {
        var view = fixture();
        assertEquals(view, roundTrip(view, PracticeWire.VERSION));
        for (int version = PracticeWire.POSE_VERSION; version < PracticeWire.VERSION; version++) {
            var previous = roundTrip(view, version).fighters().get(0);
            assertEquals(view.fighters().get(0).vx(), previous.vx());
            assertEquals(view.fighters().get(0).hurtTick(), previous.hurtTick());
            assertEquals(view.fighters().get(0).motionEpoch(), previous.motionEpoch());
            assertEquals(version >= PracticeWire.WALL_VERSION ? 43 : -1, previous.lastHorizontalWallTick());
            boolean impulse = version >= PracticeWire.IMPULSE_VERSION;
            assertEquals(version >= PracticeWire.SWING_VERSION ? 41 : -1, previous.swingTick());
            assertEquals(impulse ? 44 : -1, previous.impulseTick());
            assertEquals(impulse ? .9f : 0, previous.impulseX());
            assertEquals(impulse ? .46f : 0, previous.impulseY());
            assertEquals(impulse ? -.08f : 0, previous.impulseZ());
        }
        var fighter = roundTrip(view, PracticeWire.LEGACY_VERSION).fighters().get(0);
        assertEquals(view.fighters().get(0).health(), fighter.health());
        assertEquals(0, fighter.vx());
        assertEquals(-1, fighter.hurtTick());
        assertEquals(0, fighter.motionEpoch());
        assertEquals(-1, fighter.impulseTick());
    }

    @Test @Timeout(15)
    void olderClientIsToldToUpgradeBeforeJoiningTheLab() throws Exception {
        var identity = SecureTransport.server(temporary.resolve("tls"));
        try (var server = new PracticeServer(HOST, 0, identity.context(), Kit.KNOCKBACK_LAB);
             var client = SecureTransport.connect(HOST, server.port(), identity.fingerprint())) {
            var out = new DataOutputStream(client.getOutputStream());
            var in = new DataInputStream(client.getInputStream());
            out.writeInt(PracticeWire.MAGIC);
            out.writeInt(PracticeWire.SWING_VERSION);
            out.flush();
            assertEquals(-2, in.readInt());
        }
    }

    @Test @Timeout(15)
    void battlefieldClientIsToldToUpgradeBeforeJoiningVaultBattles() throws Exception {
        var identity = SecureTransport.server(temporary.resolve("tls"));
        try (var server = new PracticeServer(HOST, 0, identity.context(), Kit.VAULT_BATTLES);
             var client = SecureTransport.connect(HOST, server.port(), identity.fingerprint())) {
            var out = new DataOutputStream(client.getOutputStream());
            var in = new DataInputStream(client.getInputStream());
            out.writeInt(PracticeWire.MAGIC);
            out.writeInt(PracticeWire.BATTLEFIELD_VERSION);
            out.flush();
            assertEquals(-2, in.readInt());
        }
    }

    @Test void vaultSnapshotCarriesSixtyFightersWithAProtocolAndRosterBound() throws Exception {
        var view = new PracticeMatch(Kit.VAULT_BATTLES, true).view(1);
        assertEquals(60, view.fighters().size());
        assertEquals(view, roundTrip(view, PracticeWire.VERSION));
        assertEquals(view.fighters(), roundTrip(view, PracticeWire.VAULT_BATTLES_VERSION).fighters());
        assertThrows(IOException.class, () -> roundTrip(view, PracticeWire.BATTLEFIELD_VERSION));

        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(bytes)) {
            out.writeByte(Phase.WAITING.ordinal());
            out.writeByte(Kit.VAULT_BATTLES.ordinal());
            out.writeByte(Result.NONE.ordinal());
            out.writeInt(0); // Winner
            out.writeShort(0); // Ticks left
            out.writeInt(0); // Spectator ID
            out.writeLong(0); // Tick
            out.writeByte(61);
        }
        try (var in = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            var failure = assertThrows(IOException.class, () -> PracticeWire.readView(in, PracticeWire.VAULT_BATTLES_VERSION));
            assertEquals("Invalid practice frame", failure.getMessage());
        }
    }

    @Test void vaultStageRoundTripsIndependentlyOfDisplayText() throws Exception {
        for (VaultStage stage : VaultStage.values()) {
            boolean captured = stage == VaultStage.CAPTURED;
            var view = new View(captured ? Phase.RESULT : Phase.FIGHT, Kit.VAULT_BATTLES,
                    captured ? Result.OBJECTIVE : Result.NONE, captured ? 2 : 0, 0, 0, 120,
                    List.of(), List.of(), 20, 0, Map.of(), List.of(), List.of(), -1, -1, List.of(), stage);
            assertEquals(view, roundTrip(view, PracticeWire.VAULT_BATTLES_VERSION));
        }
    }

    @Test void bowStateRoundTripsWithoutChangingTheVersionTenFrame() throws Exception {
        var archer = new Fighter(1, "Aster", 1, 73, 2, -90, 0, 16, true, false, false, Item.BOW,
                2, 2, 0, 29, 0, "Defenders", .4f, .22f, -.12f, 42, 3, 43, 44, .9f, .46f, -.08f, 41,
                20, 0);
        var inventory = new ArrayList<>(Collections.nCopies(36, new Stack(Item.EMPTY, 0)));
        inventory.set(8, new Stack(Item.BOW, 1, 0));
        inventory.set(35, new Stack(Item.ARROW, 19));
        var projectiles = List.of(new Missile(1, Item.ARROW, 1, 75, 1, -2.9f, .5f, .08f, true, false),
                new Missile(2, Item.ARROW, 1, 75, -5, -.9f, -.5f, 0, false, true),
                new Missile(3, Item.HEALING, 1, 74, 8));
        var view = new View(Phase.FIGHT, Kit.VAULT_BATTLES, Result.NONE, 0, 140, 0, 44,
                List.of(archer), inventory, 20, 0, Map.of(), List.of(), projectiles, 42, -1, List.of(),
                VaultStage.VAULT_CORE);
        assertEquals(view, roundTrip(view, PracticeWire.BOW_VERSION));
        var legacy = roundTrip(view, PracticeWire.VAULT_BATTLES_VERSION);
        assertEquals(0, legacy.fighters().get(0).bowDrawTicks());
        assertEquals(new Stack(Item.BOW, 1, BOW_DURABILITY), legacy.inventory().get(8));
        assertEquals(new Missile(1, Item.ARROW, 1, 75, 1), legacy.missiles().get(0));
        assertEquals(new Missile(2, Item.ARROW, 1, 75, -5), legacy.missiles().get(1));
        assertEquals(projectiles.get(2), legacy.missiles().get(2));
        assertEquals(VaultStage.VAULT_CORE, legacy.vaultStage());
    }

    @Test void liveProjectilesStayVisibleWhenAFieldFillsWithEmbeddedArrows() {
        var flights = new ArrayList<ProjectileState>();
        var p = new Vec3(1, 73, 0);
        var v = new Vec3(1, 0, 0);
        for (int id = 1; id <= 80; id++) flights.add(new ProjectileState(id, 1, Item.ARROW, p, v, false, true));
        flights.add(new ProjectileState(81, 1, Item.ARROW, p, v, true, false));
        flights.add(new ProjectileState(82, 1, Item.HEALING, p, v, false, false));
        flights.add(new ProjectileState(83, 1, Item.PEARL, p, v, false, false));
        var visible = Missile.visible(flights);
        assertEquals(64, visible.size());
        assertEquals(List.of(83L, 82L, 81L), visible.subList(0, 3).stream().map(Missile::id).toList());
        assertTrue(visible.subList(3, 64).stream().allMatch(Missile::embedded));
    }

    @Test void explosiveAmmoAndBlastsRoundTripAndOlderClientsReceiveCompatibleArrows() throws Exception {
        var explosive = new Stack(Item.EXPLOSIVE_ARROW, 8);
        var flying = new Missile(1, Item.EXPLOSIVE_ARROW, 1, 75, 1, -2.9f, .5f, .08f, false, false);
        var blast = new Missile(2, Item.EXPLOSIVE_ARROW, 1, 75, -5, 0, 0, 0, false, true);
        var inventory = new ArrayList<>(Collections.nCopies(36, new Stack(Item.EMPTY, 0)));
        inventory.set(34, explosive);
        var view = new View(Phase.FIGHT, Kit.VAULT_BATTLES, Result.NONE, 0, 140, 0, 44,
                List.of(), inventory, 20, 0, Map.of(), List.of(), List.of(flying, blast), 42, -1, List.of(),
                VaultStage.OUTER_RING);
        assertEquals(view, roundTrip(view, PracticeWire.EXPLOSIVE_ARROW_VERSION));
        var legacy = roundTrip(view, PracticeWire.BOW_VERSION);
        assertEquals(new Stack(Item.ARROW, 8), legacy.inventory().get(34));
        assertEquals(List.of(new Missile(1, Item.ARROW, 1, 75, 1, -2.9f, .5f, .08f, false, false)), legacy.missiles());
        var versionTen = roundTrip(view, PracticeWire.VAULT_BATTLES_VERSION);
        assertEquals(new Stack(Item.ARROW, 8), versionTen.inventory().get(34));
        assertEquals(List.of(new Missile(1, Item.ARROW, 1, 75, 1)), versionTen.missiles());
        assertTrue(roundTrip(view, PracticeWire.VERSION).missiles().get(1).blast());
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
                assertEquals(version >= PracticeWire.CLICK_VERSION ? click.attackView() : null, decoded.attackView());
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
    void versionTenClientCanJoinVaultBattlesWithItsExistingWireShape() throws Exception {
        var identity = SecureTransport.server(temporary.resolve("tls"));
        try (var server = new PracticeServer(HOST, 0, identity.context(), Kit.VAULT_BATTLES);
             var client = SecureTransport.connect(HOST, server.port(), identity.fingerprint())) {
            var out = new DataOutputStream(client.getOutputStream());
            var in = new DataInputStream(client.getInputStream());
            out.writeInt(PracticeWire.MAGIC);
            out.writeInt(PracticeWire.VAULT_BATTLES_VERSION);
            out.writeByte(3);
            out.writeBytes("Old");
            out.writeByte(0);
            out.flush();
            assertTrue(in.readInt() > 0);
            var first = PracticeWire.readView(in, PracticeWire.VAULT_BATTLES_VERSION);
            var next = PracticeWire.readView(in, PracticeWire.VAULT_BATTLES_VERSION);
            assertEquals(Kit.VAULT_BATTLES, first.kit());
            assertEquals(first.inventory(), next.inventory());
            assertEquals(new Stack(Item.BOW, 1), first.inventory().get(8));
            assertEquals(new Stack(Item.ARROW, 32), first.inventory().get(35));
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
                2, 2, 0, 31, -1, "", .4f, .22f, -.12f, 42, 3, 43, 44, .9f, .46f, -.08f, 41);
        return new View(Phase.FIGHT, Kit.NO_DEBUFF, Result.NONE, 0, 140, 0, 44,
                List.of(fighter), List.of(), 20, 0, Map.of(), List.of(), List.of(), 42, -1, List.of());
    }
}
