package dev.jayms.assets;

import dev.jayms.GameIdentity;
import dev.jayms.net.model.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Local catalog. Voxel payloads use the same .vxm packets as the model editor. */
public final class AssetCatalog {
    public record Asset(String id, String description, String author, ModelDefinition model, boolean seed) {}
    private final Path root;
    private final Map<String, Asset> assets = new LinkedHashMap<>();

    public AssetCatalog(Path profile) throws IOException {
        root = profile.resolve("asset-catalog");
        Files.createDirectories(root);
        var pot = ModelGenerators.flowerPot();
        assets.put("flower-pot", new Asset("flower-pot", "Existing engine flower pot. Decoration at one-block scale.", "engine", pot, true));
        try (var seed = AssetCatalog.class.getResourceAsStream("/asset-manager/hudson-crown.vxm")) {
            if (seed == null) throw new IOException("Bundled skyscraper model is missing");
            var tower = parse(seed.readNBytes(ModelDefinition.MAX_BYTES + 5));
            assets.put("hudson-crown", new Asset("hudson-crown", "Original monumental art deco skyscraper with stepped limestone base, vertical windows and crown. Miniature decoration at one-block scale; no political insignia.", "Voxel One", tower, true));
        }
        try (var stream = Files.list(root)) {
            for (Path file : stream.filter(p -> p.getFileName().toString().matches("[a-f0-9]{64}\\.asset")).sorted().toList()) {
                try (var in = new DataInputStream(Files.newInputStream(file))) {
                    String description = metadata(in.readUTF(), "Description", 500);
                    String author = metadata(in.readUTF(), "Author", 64);
                    ModelDefinition model = ModelDefinition.read(in);
                    if (in.read() != -1) throw new IOException("Trailing catalog data");
                    validate(model);
                    String id = model.fingerprint();
                    if (!file.getFileName().toString().equals(id + ".asset")) throw new IOException("Catalog fingerprint mismatch");
                    assets.put(id, new Asset(id, description, author, model, false));
                }
            }
        }
    }

    public synchronized List<Asset> search(String query) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return assets.values().stream().filter(a -> (a.model.name()+" "+a.description+" "+a.author).toLowerCase(Locale.ROOT).contains(q)).toList();
    }

    public synchronized Asset get(String id) throws IOException {
        Asset asset = assets.get(id);
        if (asset == null) throw new IOException("Asset not found");
        return asset;
    }

    public synchronized Asset upload(String filename, String description, String author, byte[] bytes) throws IOException {
        if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".vxm"))
            throw new IOException("Choose a .vxm model exported by the Voxel One model editor");
        description = metadata(description, "Description", 500);
        author = metadata(author, "Author", 64);
        ModelDefinition model = parse(bytes);
        String id = model.fingerprint();
        Asset existing = assets.get(id);
        if (existing != null) return existing;
        var asset = new Asset(id, description, author, model, false);
        var data = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(data)) {
            out.writeUTF(description); out.writeUTF(author); model.write(out);
        }
        atomicWrite(root.resolve(id + ".asset"), data.toByteArray());
        assets.put(id, asset);
        return asset;
    }

    public synchronized String addToGame(String id, String gameKey, Path profile) throws IOException {
        GameIdentity game;
        try { game = GameIdentity.parse(gameKey); }
        catch (IllegalArgumentException e) { throw new IOException(e.getMessage()); }
        Asset asset = get(id);
        Path pending = profile.resolve("asset-imports").resolve(game.key);
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(bytes)) { asset.model.write(out); }
        atomicWrite(pending.resolve(asset.model.fingerprint()+".vxm"), bytes.toByteArray());
        return "Added to " + game.title + ". Open an offline game to receive it. If already running, restart the game. Multiplayer worlds require their own publishing permission.";
    }

    public static ModelDefinition parse(byte[] bytes) throws IOException {
        if (bytes.length > ModelDefinition.MAX_BYTES + 4) throw new IOException("Model exceeds 200004-byte upload limit");
        try (var in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            ModelDefinition model = ModelDefinition.read(in);
            if (in.read() != -1) throw new IOException("Trailing model data");
            validate(model);
            return model;
        } catch (IllegalArgumentException e) { throw new IOException("Invalid model: " + e.getMessage(), e); }
    }

    private static void validate(ModelDefinition model) throws IOException {
        if (model.voxels().occupied() == 0) throw new IOException("Model must contain at least one voxel");
        ModelMesher.mesh(model.voxels());
    }

    private static String metadata(String value, String field, int max) throws IOException {
        if (value == null || value.isBlank() || value.length() > max || value.chars().anyMatch(c -> Character.isISOControl(c)))
            throw new IOException(field + " is required (1-" + max + " characters, no control characters)");
        return value.trim();
    }

    static void atomicWrite(Path target, byte[] bytes) throws IOException {
        Files.createDirectories(target.getParent());
        Path tmp = Files.createTempFile(target.getParent(), "asset-", ".tmp");
        try {
            Files.write(tmp, bytes);
            try { Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(tmp); }
    }
}
