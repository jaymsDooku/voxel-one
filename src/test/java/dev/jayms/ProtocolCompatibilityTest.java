package dev.jayms;

import static org.junit.jupiter.api.Assertions.*;

import dev.jayms.net.*;
import dev.jayms.net.city.*;
import dev.jayms.net.model.ModelLibrary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.net.*;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.*;

class ProtocolCompatibilityTest {
    @TempDir Path temp;

    private void request(DataInputStream in, int version, boolean register) throws IOException {
        assertEquals(Protocol.MAGIC, in.readInt());
        assertEquals(version, in.readInt());
        assertEquals(register ? Protocol.REGISTER : Protocol.LOGIN, in.readUnsignedByte());
        assertEquals("tester", in.readUTF());
        assertEquals("test-password", in.readUTF());
    }

    private void reply(DataOutputStream out, int version, boolean success, String message)
            throws IOException {
        out.writeInt(Protocol.MAGIC);
        out.writeInt(version);
        out.writeBoolean(success);
        out.writeUTF(message);
        out.flush();
    }

    @Test
    void loginAndRegistrationReconnectToLegacyVersionsAndKeepCommandsAligned() throws Exception {
        var identity = SecureTransport.server(temp.resolve("tls"));
        for (int legacyVersion : List.of(14, 15)) {
            for (boolean register : List.of(false, true)) {
                try (var listener = identity.context().getServerSocketFactory()
                        .createServerSocket(0, 2, InetAddress.getLoopbackAddress())) {
                    listener.setSoTimeout(10000);
                    var task = new FutureTask<Void>(() -> {
                        try (var first = listener.accept()) {
                            first.setSoTimeout(10000);
                            request(new DataInputStream(first.getInputStream()), Protocol.VERSION, register);
                            reply(new DataOutputStream(first.getOutputStream()), legacyVersion, false,
                                    "Client version mismatch; download the latest client.");
                        }
                        try (var second = listener.accept()) {
                            second.setSoTimeout(10000);
                            var in = new DataInputStream(second.getInputStream());
                            var out = new DataOutputStream(second.getOutputStream());
                            request(in, legacyVersion, register);
                            reply(out, legacyVersion, true, "Welcome tester");
                            out.writeInt(1);
                            new Protocol.Pose(1, 8, 20, 24, 0, 0, 0, 0, false).write(out);
                            out.writeUTF("tester");
                            out.writeLong(Terrain.DEFAULT_SEED);
                            new ModelLibrary().write(out);
                            new Inventory().write(out);
                            out.writeByte(20);
                            out.writeInt(0); // World edits
                            out.writeInt(0); // Players
                            out.writeInt(0); // Drops
                            CityFrame.empty(GameConfig.cityGame()).write(out, 6);
                            out.flush();
                            assertEquals(Protocol.READY, in.readUnsignedByte());
                            if (legacyVersion == 15) {
                                assertEquals(Protocol.CITY_COMMAND, in.readUnsignedByte());
                                assertEquals(CityCommand.SPECIAL, CityCommand.read(in).kind());
                            }
                            assertEquals(Protocol.CITY_COMMAND, in.readUnsignedByte());
                            assertEquals(CityCommand.ROAD, CityCommand.read(in).kind());
                            out.writeByte(Protocol.CITY_STATE);
                            CityFrame.empty(GameConfig.cityGame()).write(out, 6);
                            out.writeByte(Protocol.CITY_RESULT);
                            out.writeUTF("Road received");
                            out.flush();
                            assertEquals(-1, in.read()); // Client closes; no SPECIAL bytes sent.
                        }
                        return null;
                    });
                    Thread thread = new Thread(task);
                    thread.setDaemon(true);
                    thread.start();
                    try (var client = new MultiplayerClient("127.0.0.1", listener.getLocalPort(),
                            "tester", "test-password".toCharArray(), register, identity.fingerprint())) {
                        assertEquals(legacyVersion, client.serverProtocol);
                        assertTrue(client.city.config().city());
                        assertEquals(Terrain.LEGACY_VERSION, client.generatorVersion);
                        assertFalse(client.cityCommand(new CityCommand(CityCommand.SPECIAL,
                                SpecialBuildings.PORT, List.of(), 0, 0)));
                        assertTrue(client.notice().contains("server update"));
                        assertFalse(client.cityCommand(new CityCommand(CityCommand.EXCHANGE, 0, List.of())));
                        assertFalse(client.cityCommand(new CityCommand(CityCommand.SPECIAL,
                                SpecialBuildings.EXCHANGE, List.of(), 0, 0)));
                        assertFalse(client.cityCommand(new CityCommand(new CityCommand.Capital(
                                0, 1, 0, 1, 1, 1, 0))));
                        assertEquals(legacyVersion == 15, client.cityCommand(new CityCommand(
                                CityCommand.SPECIAL, 1, List.of(), 0, 0)));
                        if (legacyVersion == 14) assertTrue(client.notice().contains("server update"));
                        assertTrue(client.cityCommand(new CityCommand(CityCommand.ROAD, 0, List.of())));
                        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                        while (!client.notice().equals("Road received") && System.nanoTime() < deadline) {
                            client.poll();
                            Thread.sleep(10);
                        }
                        assertEquals("Road received", client.notice());
                    }
                    task.get(10, TimeUnit.SECONDS);
                }
            }
        }
    }

    @Test
    void incompatibleVersionsAreRejectedWithActionableDiagnostics() throws Exception {
        var identity = SecureTransport.server(temp.resolve("tls"));
        for (int version : List.of(13, 21, 22, Protocol.VERSION + 1)) {
            try (var listener = identity.context().getServerSocketFactory()
                    .createServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
                listener.setSoTimeout(10000);
                var task = new FutureTask<Void>(() -> {
                    try (var socket = listener.accept()) {
                        socket.setSoTimeout(10000);
                        request(new DataInputStream(socket.getInputStream()), Protocol.VERSION, false);
                        reply(new DataOutputStream(socket.getOutputStream()), version, false, "Mismatch");
                    }
                    return null;
                });
                Thread thread = new Thread(task);
                thread.setDaemon(true);
                thread.start();
                IOException failure = assertThrows(IOException.class, () -> new MultiplayerClient(
                        "127.0.0.1", listener.getLocalPort(), "tester",
                        "test-password".toCharArray(), false, identity.fingerprint()));
                assertTrue(failure.getMessage().contains("server " + version));
                assertTrue(failure.getMessage().contains(version < Protocol.VERSION ? "server needs" : "launcher"));
                task.get(10, TimeUnit.SECONDS);
            }
        }
    }
}
