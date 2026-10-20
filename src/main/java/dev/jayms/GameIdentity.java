package dev.jayms;

import java.nio.file.Path;

/** Stable desktop catalog keys. Save names and server ports retain the original contracts. */
public enum GameIdentity {
    SANDBOX("sandbox", "Voxel Sandbox", "First-person exploration and building.", 25565, "offline-world.dat"),
    CITY("city", "Voxel City One", "Plan a city, then explore its living world.", 25566, "offline-city.dat"),
    MUSIC("music", "Voxel Music Visualizer", "Local audio becomes a living voxel sculpture.", 0, "");

    public final String key, title, description, saveName;
    public final int port;
    GameIdentity(String key, String title, String description, int port, String saveName) {
        this.key = key; this.title = title; this.description = description;
        this.port = port; this.saveName = saveName;
    }
    public static GameIdentity parse(String key) {
        for (var game : values()) if (game.key.equals(key)) return game;
        throw new IllegalArgumentException("Game must be city, sandbox or music");
    }
    public static GameIdentity of(boolean city) { return city ? CITY : SANDBOX; }
    public int launchPort(Integer explicitPort) {
        if (explicitPort == null) return port;
        if (explicitPort < 1 || explicitPort > 65535)
            throw new IllegalArgumentException("Port must be from 1 to 65535");
        return explicitPort;
    }
    public Path savePath(Path directory, Path explicitWorld) {
        return explicitWorld == null ? directory.resolve(saveName) : explicitWorld;
    }
    /** A user-entered port survives selection changes; untouched defaults follow the game. */
    public String selectedPort(GameIdentity previous, String current, boolean explicitPort) {
        return !explicitPort && current.equals(Integer.toString(previous.port))
                ? Integer.toString(port) : current;
    }
    @Override public String toString() { return title; }
}
