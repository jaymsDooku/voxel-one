package dev.jayms.launcher;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.security.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.jar.JarFile;

/** Immutable release downloads, bounded transfers, verified caching, and atomic installation. */
public final class Updater {
    public static final URI MANIFEST =
            URI.create(
                    "https://github.com/jaymsDooku/voxel-one/releases/latest/download/update.properties");
    public static final long MAX_CLIENT_BYTES = 100L * 1024 * 1024;
    private final Path cache;
    private final URI manifest;
    private final HttpClient http =
            HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();

    public record Result(Path client, String revision, boolean updated, String warning) {}

    private record Release(String revision, URI url, String sha256, long size) {}

    public Updater(Path cache, URI manifest) {
        this.cache = cache;
        this.manifest = manifest;
    }

    public Result update(Platform platform, Consumer<String> status) throws IOException {
        Files.createDirectories(cache);
        try (FileChannel channel =
                        FileChannel.open(
                                cache.resolve("update.lock"),
                                StandardOpenOption.CREATE,
                                StandardOpenOption.WRITE);
                FileLock lock = lock(channel)) {
            try {
                status.accept("Checking for Voxel One updates...");
                Properties properties = new Properties();
                try (var response = request(manifest)) {
                    properties.load(new ByteArrayInputStream(read(response, 32768, status, false)));
                }
                Release release = release(properties, platform);
                Path target = path(release, platform);
                boolean updated = false;
                if (!verified(target, release)) {
                    status.accept("Downloading the latest Voxel One...");
                    Path temp = Files.createTempFile(cache, "download-", ".tmp");
                    try {
                        try (InputStream body = request(release.url)) {
                            transfer(body, temp, release.size, status);
                        }
                        if (!verified(temp, release))
                            throw new IOException(
                                    "The update failed its integrity check. Please try again.");
                        validateJar(temp);
                        Files.createDirectories(target.getParent());
                        move(temp, target);
                        updated = true;
                    } finally {
                        Files.deleteIfExists(temp);
                    }
                }
                validateJar(target);
                Path state = Files.createTempFile(cache, "installed-", ".tmp");
                try {
                    try (OutputStream out = Files.newOutputStream(state)) {
                        properties.store(out, "Verified Voxel One installation");
                    }
                    move(state, cache.resolve("installed.properties"));
                } finally {
                    Files.deleteIfExists(state);
                }
                return new Result(target, release.revision, updated, "");
            } catch (IOException | IllegalArgumentException e) {
                Result old = installed(platform);
                if (old == null)
                    throw new IOException("Unable to install Voxel One: " + e.getMessage(), e);
                String warning = "Update unavailable. Starting your installed version.";
                status.accept(warning);
                return new Result(old.client, old.revision, false, warning);
            }
        }
    }

    private FileLock lock(FileChannel channel) throws IOException {
        try {
            FileLock lock = channel.tryLock();
            if (lock != null) return lock;
        } catch (OverlappingFileLockException ignored) {
        }
        throw new IOException("Another launcher is updating Voxel One. Try again in a moment.");
    }

    public Result installed(Platform platform) {
        try {
            Properties properties = new Properties();
            try (InputStream in = Files.newInputStream(cache.resolve("installed.properties"))) {
                properties.load(new ByteArrayInputStream(read(in, 32768, s -> {}, false)));
            }
            Release release = release(properties, platform);
            Path path = path(release, platform);
            if (!verified(path, release)) return null;
            validateJar(path);
            return new Result(path, release.revision, false, "");
        } catch (IOException | IllegalArgumentException e) {
            return null;
        }
    }

    private Release release(Properties p, Platform platform) throws IOException {
        if (!"1".equals(p.getProperty("format")))
            throw new IOException("Unsupported update manifest");
        String revision = p.getProperty("revision", ""), prefix = platform.id() + ".";
        String sha = p.getProperty(prefix + "sha256", "");
        if (!revision.matches("[0-9a-f]{40}") || !sha.matches("[0-9a-f]{64}"))
            throw new IOException("Invalid update version or checksum");
        URI url = URI.create(p.getProperty(prefix + "url", ""));
        boolean trusted =
                "https".equals(url.getScheme())
                        && "github.com".equals(url.getHost())
                        && url.getPath().startsWith("/jaymsDooku/voxel-one/releases/download/");
        // The injectable endpoint also permits a local test server, never an arbitrary remote HTTP
        // host.
        boolean local =
                ("127.0.0.1".equals(manifest.getHost()) || "localhost".equals(manifest.getHost()))
                        && Objects.equals(url.getHost(), manifest.getHost())
                        && Objects.equals(url.getScheme(), manifest.getScheme())
                        && url.getPort() == manifest.getPort();
        if ((!trusted && !local) || url.getUserInfo() != null || url.getFragment() != null)
            throw new IOException("Untrusted update download address");
        long size;
        try {
            size = Long.parseLong(p.getProperty(prefix + "size", ""));
        } catch (NumberFormatException e) {
            throw new IOException("Invalid update size", e);
        }
        if (size <= 0 || size > MAX_CLIENT_BYTES)
            throw new IOException("Update size is out of bounds");
        return new Release(revision, url, sha, size);
    }

    private Path path(Release release, Platform platform) {
        return cache.resolve("versions")
                .resolve(release.revision)
                .resolve(platform.id() + ".jar")
                .toAbsolutePath();
    }

    private InputStream request(URI uri) throws IOException {
        try {
            HttpResponse<InputStream> response =
                    http.send(
                            HttpRequest.newBuilder(uri)
                                    .timeout(Duration.ofSeconds(30))
                                    .header("User-Agent", "Voxel-One-Launcher/1")
                                    .GET()
                                    .build(),
                            HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                response.body().close();
                throw new IOException("Download returned HTTP " + response.statusCode());
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Download interrupted", e);
        }
    }

    private static byte[] read(
            InputStream body, int limit, Consumer<String> status, boolean progress)
            throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bounded(body, out, limit, status, progress);
        return out.toByteArray();
    }

    private static void transfer(InputStream body, Path target, long limit, Consumer<String> status)
            throws IOException {
        try (OutputStream out = Files.newOutputStream(target)) {
            bounded(body, out, limit, status, true);
        }
    }

    private static void bounded(
            InputStream body,
            OutputStream out,
            long limit,
            Consumer<String> status,
            boolean progress)
            throws IOException {
        ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
        var timeout =
                timer.schedule(
                        () -> {
                            try {
                                body.close();
                            } catch (IOException ignored) {
                            }
                        },
                        progress ? 120 : 30,
                        TimeUnit.SECONDS);
        try {
            byte[] buffer = new byte[65536];
            long size = 0, last = 0;
            int n;
            while ((n = body.read(buffer)) != -1) {
                size += n;
                if (size > limit) throw new IOException("Download exceeds its advertised size");
                out.write(buffer, 0, n);
                if (progress && size - last >= 1024 * 1024) {
                    status.accept("Downloading Voxel One: " + size / (1024 * 1024) + " MB");
                    last = size;
                }
            }
        } finally {
            timeout.cancel(false);
            timer.shutdownNow();
        }
    }

    private static boolean verified(Path path, Release release) throws IOException {
        if (!Files.isRegularFile(path) || Files.size(path) != release.size) return false;
        try {
            MessageDigest hash = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(path)) {
                byte[] buffer = new byte[65536];
                int n;
                while ((n = in.read(buffer)) != -1) hash.update(buffer, 0, n);
            }
            return HexFormat.of().formatHex(hash.digest()).equals(release.sha256);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void validateJar(Path path) throws IOException {
        try (JarFile jar = new JarFile(path.toFile())) {
            if (jar.getManifest() == null
                    || !"dev.jayms.Main"
                            .equals(jar.getManifest().getMainAttributes().getValue("Main-Class"))
                    || jar.getEntry("dev/jayms/Main.class") == null)
                throw new IOException("The downloaded file is not a Voxel One client");
        }
    }

    private static void move(Path source, Path target) throws IOException {
        try {
            Files.move(
                    source,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
