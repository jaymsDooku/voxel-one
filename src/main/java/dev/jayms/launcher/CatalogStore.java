package dev.jayms.launcher;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;

/** Only the repository's public catalog can replace the last validated snapshot. */
public final class CatalogStore {
    public static final URI ENDPOINT = URI.create("https://raw.githubusercontent.com/jaymsDooku/voxel-one/experience-catalog/catalog.properties");
    private final Path cache;
    private final URI endpoint;
    public record Snapshot(HubCatalog catalog, String status) {}
    public CatalogStore(Path cache) { this(cache, ENDPOINT); }
    // Package-local loopback seam for integration tests; production always uses ENDPOINT.
    CatalogStore(Path cache, URI endpoint) {
        if (!endpoint.equals(ENDPOINT) && !("http".equals(endpoint.getScheme())
                && "127.0.0.1".equals(endpoint.getHost()) && endpoint.getUserInfo() == null))
            throw new IllegalArgumentException("Unsupported catalog endpoint");
        this.cache = cache.resolve("catalog.properties"); this.endpoint = endpoint;
    }
    public Snapshot cached() throws IOException {
        if (Files.isRegularFile(cache)) {
            try (InputStream in = Files.newInputStream(cache)) { return new Snapshot(HubCatalog.read(in), "Offline cached catalog"); }
            catch (IOException ignored) { }
        }
        return new Snapshot(HubCatalog.bundled(), "Bundled library • publication check pending");
    }
    public Snapshot refresh() throws IOException {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        try {
            var response = client.send(HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(12))
                    .header("Cache-Control", "no-cache").GET().build(), HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                if (response.statusCode() != 200) throw new IOException("Catalog service unavailable");
                // Deadline closes a stalled response body as well as bounding its byte count.
                var timer = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
                var timeout = timer.schedule(() -> { try { body.close(); } catch (IOException ignored) {} }, 15, java.util.concurrent.TimeUnit.SECONDS);
                try {
                    byte[] bytes = body.readNBytes(HubCatalog.MAX_BYTES + 1);
                    HubCatalog catalog = HubCatalog.read(new ByteArrayInputStream(bytes));
                    Files.createDirectories(cache.getParent());
                    Path temp = Files.createTempFile(cache.getParent(), "catalog-", ".tmp");
                    try {
                        Files.write(temp, bytes);
                        Files.move(temp, cache, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                    } finally { Files.deleteIfExists(temp); }
                    return new Snapshot(catalog, "Published catalog " + catalog.version());
                } finally { timeout.cancel(false); timer.shutdownNow(); }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); throw new IOException("Catalog check cancelled", e);
        }
    }
}
