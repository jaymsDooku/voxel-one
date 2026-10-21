package dev.jayms.assets;

import dev.jayms.GameIdentity;
import dev.jayms.net.LocalGame;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Imports a game's selected catalog assets at offline startup, never into a live save. */
public final class AssetIntake {
    public static String apply(Path profile, GameIdentity game, Path save, LocalGame local) {
        Path directory = profile.resolve("asset-imports").resolve(game.key);
        if (!Files.isDirectory(directory)) return "";
        Path receipt = save.resolveSibling(save.getFileName()+".assets");
        Set<String> received = new HashSet<>();
        int imported = 0;
        List<String> errors = new ArrayList<>();
        try {
            if (Files.exists(receipt)) received.addAll(Files.readAllLines(receipt));
            try (var files = Files.list(directory)) {
                for (Path file : files.filter(p -> p.getFileName().toString().matches("[a-f0-9]{64}\\.vxm")).sorted().toList()) {
                    try {
                        if (Files.size(file)>200004) throw new IOException("Model upload too large");
                        var model = AssetCatalog.parse(Files.readAllBytes(file));
                        String id = model.fingerprint();
                        if (!file.getFileName().toString().equals(id+".vxm")) throw new IOException("Fingerprint mismatch");
                        if (received.contains(id)) continue;
                        // A saved custom entry also acts as a receipt if the process stopped after save.
                        if (local.models.find(model) < 9) local.createModel(model);
                        local.save();
                        received.add(id);
                        AssetCatalog.atomicWrite(receipt, String.join("\n", new TreeSet<>(received)).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        imported++;
                    } catch (IOException e) { errors.add(file.getFileName()+": "+e.getMessage()); }
                }
            }
        } catch (IOException e) { errors.add(e.getMessage()); }
        String result = imported == 0 ? "" : "Asset Manager: " + imported + " asset(s) available in inventory.";
        if (!errors.isEmpty()) result += " Asset import pending: " + String.join("; ", errors) + ". Make inventory space or check the selected models, then restart.";
        return result;
    }
    private AssetIntake() {}
}
