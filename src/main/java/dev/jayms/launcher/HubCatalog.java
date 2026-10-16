package dev.jayms.launcher;

import java.io.*;
import java.net.URI;
import java.util.*;

/** Bounded public catalog. Values are data, never executable command text. */
public record HubCatalog(String version, List<Entry> entries) {
    public static final int MAX_BYTES = 65536;
    public record Entry(String id, String title, String summary, String adapter, String target,
                        String artifact, Set<String> platforms, String build, String source,
                        String notes, String controls, String artwork, Set<String> clientSources) {
        public Entry { platforms = Set.copyOf(platforms); clientSources = Set.copyOf(clientSources); }
        public void requireClient(Updater.Result result) throws IOException {
            if (result == null || !clientSources.contains(result.revision()))
                throw new IOException("Catalog/client mismatch. Refresh the catalog or install a compatible published client.");
        }
        public boolean supported(Platform platform) {
            return platforms.contains(platform.id()) && switch (adapter) {
                case "native" -> Set.of("city", "sandbox", "editor").contains(target)
                        && artifact.equals("voxel-client");
                case "browser" -> safeWeb(target) && !source.equals("unpublished");
                case "docs" -> safeWeb(target);
                default -> false;
            };
        }
        public List<String> arguments(boolean offline, String host, int port) {
            if (!adapter.equals("native") || !Set.of("city", "sandbox", "editor").contains(target)
                    || !artifact.equals("voxel-client")) throw new IllegalArgumentException("Unsupported launch target");
            List<String> args = new ArrayList<>(List.of("--game", target.equals("editor") ? "city" : target));
            if (target.equals("editor")) args.add("--editor");
            if (offline) args.add("--offline");
            else {
                if (host.isBlank() || host.length() > 253 || !host.matches("[a-zA-Z0-9.:-]+") || port < 1 || port > 65535)
                    throw new IllegalArgumentException("Enter a server and a port from 1 to 65535.");
                args.addAll(List.of("--server", host, "--port", Integer.toString(port)));
            }
            return List.copyOf(args);
        }
    }
    public HubCatalog { entries = List.copyOf(entries); }
    public static boolean safeWeb(String value) {
        try {
            URI u = URI.create(value);
            return "https".equals(u.getScheme()) && u.getUserInfo() == null && u.getFragment() == null
                    && (u.getPort() == -1 || u.getPort() == 443)
                    && Set.of("voxel-one.jamesleaver1.chatgpt.site", "github.com").contains(u.getHost())
                    && (u.getHost().equals("voxel-one.jamesleaver1.chatgpt.site") || u.getPath().startsWith("/jaymsDooku/voxel-one/"));
        } catch (IllegalArgumentException e) { return false; }
    }
    public static HubCatalog read(InputStream input) throws IOException {
        byte[] bytes = input.readNBytes(MAX_BYTES + 1);
        if (bytes.length > MAX_BYTES) throw new IOException("Catalog is too large");
        Properties p = new Properties();
        try { p.load(new ByteArrayInputStream(bytes)); }
        catch (IllegalArgumentException e) { throw new IOException("Malformed catalog", e); }
        if (!"1".equals(p.getProperty("format")) || !"playtest".equals(p.getProperty("channel")))
            throw new IOException("Unsupported catalog format or channel");
        String version = field(p, "version", 80);
        String rawIds = p.getProperty("entries");
        if (rawIds == null || rawIds.length() > 2048) throw new IOException("Invalid catalog entries");
        String[] ids = rawIds.isEmpty() ? new String[0] : rawIds.split(",", -1);
        if (ids.length > 64) throw new IOException("Too many catalog entries");
        List<Entry> entries = new ArrayList<>(); Set<String> seen = new HashSet<>();
        for (String id : ids) {
            if (!id.matches("[a-z][a-z0-9-]{0,63}") || !seen.add(id)) throw new IOException("Invalid or duplicate experience ID");
            String prefix = id + ".";
            String adapter = field(p, prefix + "adapter", 32), target = field(p, prefix + "target", 2048);
            if (Set.of("browser", "docs").contains(adapter) && !safeWeb(target)) throw new IOException("Untrusted web target");
            Set<String> platforms = new HashSet<>(Arrays.asList(field(p, prefix + "platforms", 100).split(",")));
            if (!Set.of("windows", "linux", "mac-intel", "mac-arm").containsAll(platforms)) throw new IOException("Invalid platform");
            String source = field(p, prefix + "source", 80);
            if (!source.matches("[0-9a-f]{40}|unpublished")) throw new IOException("Invalid source revision");
            Set<String> clientSources = Set.of();
            if (adapter.equals("native")) {
                if (!"source-set-v1".equals(field(p, prefix + "client-compatibility", 32)))
                    throw new IOException("Unsupported engine/client compatibility contract");
                String[] revisions = field(p, prefix + "client-sources", 327).split(",", -1);
                clientSources = new HashSet<>(Arrays.asList(revisions));
                if (revisions.length > 8 || clientSources.size() != revisions.length
                        || clientSources.stream().anyMatch(r -> !r.matches("[0-9a-f]{40}"))
                        || !clientSources.contains(source))
                    throw new IOException("Invalid compatible client sources or catalog provenance");
            }
            entries.add(new Entry(id, field(p, prefix + "title", 120), field(p, prefix + "summary", 600),
                    adapter, target, field(p, prefix + "artifact", 64), Set.copyOf(platforms),
                    field(p, prefix + "build", 80), source, field(p, prefix + "notes", 4000),
                    field(p, prefix + "controls", 1000), field(p, prefix + "artwork", 64), clientSources));
        }
        return new HubCatalog(version, entries);
    }
    private static String field(Properties p, String key, int max) throws IOException {
        String value = p.getProperty(key, "");
        if (value.isBlank() || value.length() > max || value.chars().anyMatch(c -> c < 32 || c == 127))
            throw new IOException("Invalid catalog field: " + key);
        return value;
    }
    public static HubCatalog bundled() throws IOException {
        try (InputStream in = HubCatalog.class.getResourceAsStream("catalog.properties")) {
            if (in == null) throw new IOException("Bundled catalog missing");
            return read(in);
        }
    }
}
