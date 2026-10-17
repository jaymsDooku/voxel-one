package dev.jayms;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;

import dev.jayms.launcher.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.jar.*;

class UpdaterTest {
    @TempDir Path temp;

    private byte[] jar(String version) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().putValue("Manifest-Version", "1.0");
        manifest.getMainAttributes().putValue("Main-Class", "dev.jayms.Main");
        try (JarOutputStream out = new JarOutputStream(bytes, manifest)) {
            out.putNextEntry(new JarEntry("dev/jayms/Main.class"));
            out.write(version.getBytes());
            out.closeEntry();
        }
        return bytes.toByteArray();
    }

    private String manifest(String revision, String url, byte[] bytes) throws Exception {
        return "format=1\nrevision="
                + revision
                + "\nwindows.url="
                + url
                + "\nwindows.size="
                + bytes.length
                + "\nwindows.sha256="
                + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))
                + "\n";
    }

    @Test void freshInstallFetchesCatalogApprovedReleaseAfterLatestAdvances() throws Exception {
        var entry = HubCatalog.bundled().entries().get(0);
        byte[] approved = jar("approved-A"), incompatible = jar("latest-B");
        AtomicInteger approvedDownloads = new AtomicInteger(), latestRequests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        byte[] approvedManifest = manifest(entry.source(), base + "/approved.jar", approved).getBytes();
        byte[] latestManifest = manifest("b".repeat(40), base + "/incompatible.jar", incompatible).getBytes();
        server.createContext(entry.clientManifest().getPath(), e -> {
            e.sendResponseHeaders(200, approvedManifest.length);
            try (var out = e.getResponseBody()) { out.write(approvedManifest); }
        });
        server.createContext("/releases/latest/download/update.properties", e -> {
            latestRequests.incrementAndGet(); e.sendResponseHeaders(200, latestManifest.length);
            try (var out = e.getResponseBody()) { out.write(latestManifest); }
        });
        server.createContext("/approved.jar", e -> {
            approvedDownloads.incrementAndGet(); e.sendResponseHeaders(200, approved.length);
            try (var out = e.getResponseBody()) { out.write(approved); }
        });
        server.createContext("/incompatible.jar", e -> { latestRequests.incrementAndGet(); e.sendResponseHeaders(500, -1); e.close(); });
        server.start();
        try {
            assertNull(new Updater(temp, URI.create(base + "/unused")).installed(Platform.WINDOWS));
            URI pinned = URI.create(base + entry.clientManifest().getPath());
            var updater = new Updater(temp, pinned);
            var result = updater.update(Platform.WINDOWS, message -> {}, entry.clientSources());
            entry.requireClient(result);
            assertEquals(entry.source(), result.revision());
            assertArrayEquals(approved, Files.readAllBytes(result.client()));
            assertEquals(1, approvedDownloads.get()); assertEquals(0, latestRequests.get());
            assertFalse(updater.update(Platform.WINDOWS, message -> {}, entry.clientSources()).updated());
            assertEquals(1, approvedDownloads.get());
        } finally { server.stop(0); }
    }

    @Test
    void installsUpdatesAtomicallyRejectsTamperingAndUsesVerifiedOfflineCache() throws Exception {
        byte[] first = jar("first"), next = jar("next"), corrupt = next.clone();
        corrupt[corrupt.length - 1] ^= 1; // Same size, wrong SHA-256.
        AtomicReference<byte[]> payload = new AtomicReference<>(first);
        AtomicReference<String> metadata = new AtomicReference<>();
        AtomicInteger transfers = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/update",
                exchange -> {
                    byte[] bytes = metadata.get().getBytes();
                    exchange.sendResponseHeaders(200, bytes.length);
                    try (var out = exchange.getResponseBody()) {
                        out.write(bytes);
                    }
                });
        server.createContext(
                "/game",
                exchange -> {
                    transfers.incrementAndGet();
                    byte[] bytes = payload.get();
                    exchange.sendResponseHeaders(200, bytes.length);
                    try (var out = exchange.getResponseBody()) {
                        out.write(bytes);
                    }
                });
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        Updater updater = new Updater(temp, URI.create(base + "/update"));
        try {
            metadata.set(manifest("a".repeat(40), base + "/game", first));
            var a = updater.update(Platform.WINDOWS, s -> {});
            assertTrue(a.updated());
            assertArrayEquals(first, Files.readAllBytes(a.client()));
            assertFalse(updater.update(Platform.WINDOWS, s -> {}).updated());
            assertEquals(1, transfers.get());
            metadata.set(manifest("b".repeat(40), base + "/game", next));
            payload.set(corrupt);
            var fallback = updater.update(Platform.WINDOWS, s -> {});
            assertEquals(a.client(), fallback.client());
            assertFalse(fallback.warning().isEmpty());
            assertFalse(
                    Files.exists(
                            temp.resolve("versions")
                                    .resolve("b".repeat(40))
                                    .resolve("windows.jar")));
            payload.set(next);
            var b = updater.update(Platform.WINDOWS, s -> {});
            assertTrue(b.updated());
            assertNotEquals(a.client(), b.client());
            assertArrayEquals(next, Files.readAllBytes(b.client()));
            assertArrayEquals(first, Files.readAllBytes(a.client()));
            metadata.set(manifest("c".repeat(40), "https://example.org/game.jar", next));
            assertEquals(b.client(), updater.update(Platform.WINDOWS, s -> {}).client());
            metadata.set(manifest("../unsafe", base + "/game", next));
            assertEquals(b.client(), updater.update(Platform.WINDOWS, s -> {}).client());
            server.stop(0);
            assertEquals(b.client(), updater.update(Platform.WINDOWS, s -> {}).client());
            Files.write(b.client(), corrupt);
            assertNull(updater.installed(Platform.WINDOWS));
            assertThrows(IOException.class, () -> updater.update(Platform.WINDOWS, s -> {}));
            try (var files = Files.list(temp)) {
                assertTrue(files.noneMatch(p -> p.getFileName().toString().endsWith(".tmp")));
            }
        } finally {
            server.stop(0);
        }
    }

    @Test
    void catalogRejectsIncompatibleLatestAndCachedClientsWithoutChangingInstallation() throws Exception {
        byte[] client = jar("compatible");
        AtomicReference<String> metadata = new AtomicReference<>();
        AtomicInteger downloads = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/update", e -> {
            byte[] bytes = metadata.get().getBytes(); e.sendResponseHeaders(200, bytes.length);
            try (var out = e.getResponseBody()) { out.write(bytes); }
        });
        server.createContext("/game", e -> {
            downloads.incrementAndGet(); e.sendResponseHeaders(200, client.length);
            try (var out = e.getResponseBody()) { out.write(client); }
        });
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        Updater updater = new Updater(temp, URI.create(base + "/update"));
        String a = "a".repeat(40), b = "b".repeat(40);
        try {
            metadata.set(manifest(a, base + "/game", client));
            var first = updater.update(Platform.WINDOWS, s -> {}, Set.of(a));
            byte[] state = Files.readAllBytes(temp.resolve("installed.properties"));
            metadata.set(manifest(b, base + "/game", client));
            var fallback = updater.update(Platform.WINDOWS, s -> {}, Set.of(a));
            assertEquals(first.client(), fallback.client());
            assertTrue(fallback.warning().contains("compatible"));
            assertEquals(1, downloads.get(), "Incompatible latest must be rejected before transfer");
            assertArrayEquals(state, Files.readAllBytes(temp.resolve("installed.properties")));
            assertThrows(IOException.class, () -> updater.update(Platform.WINDOWS, s -> {}, Set.of("c".repeat(40))));
            assertEquals(1, downloads.get());
            server.stop(0);
            assertThrows(IOException.class, () -> updater.update(Platform.WINDOWS, s -> {}, Set.of(b)));
            assertEquals(a, updater.update(Platform.WINDOWS, s -> {}, Set.of(a)).revision());
            assertArrayEquals(state, Files.readAllBytes(temp.resolve("installed.properties")));
        } finally { server.stop(0); }
    }

    @Test
    void platformAndLaunchArgumentsPreserveOfflineModeAndExplicitServer() {
        assertEquals(Platform.WINDOWS, Platform.detect("Windows 11", "amd64"));
        assertEquals(Platform.MAC_ARM, Platform.detect("Mac OS X", "aarch64"));
        assertEquals(Platform.MAC_INTEL, Platform.detect("Mac OS X", "x86_64"));
        assertEquals(Platform.LINUX, Platform.detect("Linux", "amd64"));
        assertThrows(IllegalArgumentException.class, () -> Platform.detect("Linux", "armv7"));
        var defaultLaunch = Launcher.command(Path.of("client.jar"), Platform.WINDOWS, List.of());
        assertTrue(defaultLaunch.get(0).endsWith("javaw.exe"));
        assertEquals(
                List.of("--server", "198.100.154.156"),
                defaultLaunch.subList(defaultLaunch.size() - 2, defaultLaunch.size()));
        var offline =
                Launcher.command(
                        Path.of("client.jar"),
                        Platform.LINUX,
                        List.of("--offline", "--world", "my world.dat"));
        assertFalse(offline.contains("--server"));
        assertEquals("my world.dat", offline.get(offline.size() - 1));
        var explicit =
                Launcher.command(
                        Path.of("client.jar"), Platform.MAC_ARM, List.of("--server", "localhost"));
        assertEquals(1, Collections.frequency(explicit, "--server"));
        assertTrue(explicit.contains("-XstartOnFirstThread"));
    }
}
