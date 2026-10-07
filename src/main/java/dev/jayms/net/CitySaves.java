package dev.jayms.net;

import dev.jayms.net.city.CitySimulation;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Each named simulation owns its world, city and vehicle files. */
public final class CitySaves {
    public record Entry(String name, Path world) {}
    private final Path legacy, directory;
    public CitySaves(Path initial) {
        Path path = initial.toAbsolutePath().normalize();
        if (path.getFileName().toString().equals("world.dat")
                && path.getParent() != null && path.getParent().getParent() != null
                && path.getParent().getParent().getFileName() != null
                && path.getParent().getParent().getFileName().toString().equals("city-saves")) {
            directory = path.getParent().getParent();
            legacy = directory.getParent().resolve("offline-city.dat");
        } else {
            legacy = path;
            directory = path.getParent().resolve("city-saves");
        }
    }
    public List<Entry> list() throws IOException {
        var entries = new ArrayList<Entry>();
        if (Files.isRegularFile(legacy)) entries.add(new Entry("Original city", legacy));
        if (Files.isDirectory(directory)) try (var folders = Files.list(directory)) {
            folders.filter(p -> Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS))
                    .sorted().forEach(p -> {
                        Path world = p.resolve("world.dat");
                        if (Files.isRegularFile(world, LinkOption.NOFOLLOW_LINKS))
                            entries.add(new Entry(p.getFileName().toString(), world));
                    });
        }
        return entries;
    }
    public String name(Path world) {
        Path p = world.toAbsolutePath().normalize();
        return p.equals(legacy) ? "Original city" : p.getParent().getFileName().toString();
    }
    private Path reserve(String name) throws IOException {
        if (name == null || !name.matches("[A-Za-z0-9][A-Za-z0-9 _-]{0,39}") || !name.equals(name.trim()))
            throw new IOException("Use 1-40 letters, numbers, spaces, - or _.");
        if (name.equalsIgnoreCase("Original city"))
            throw new IOException("That name is reserved. Choose another name.");
        Files.createDirectories(directory);
        // Reject case-only duplicates on every platform.
        try (var files = Files.list(directory)) {
            if (files.anyMatch(p -> p.getFileName().toString().equalsIgnoreCase(name)))
                throw new IOException("That name already exists. Choose another name.");
        }
        Path slot = directory.resolve(name);
        Files.createDirectory(slot);
        return slot;
    }
    public Path create(String name, Path source, long seed) throws IOException {
        Path slot = reserve(name), target = slot.resolve("world.dat");
        try {
            if (source == null) new LocalGame(target, seed).save();
            else {
                for (String suffix : List.of("", ".city", ".jeep")) {
                    Path from = sidecar(source, suffix);
                    if (Files.isRegularFile(from)) Files.copy(from, sidecar(target, suffix));
                }
                validate(target, seed);
            }
            return target;
        } catch (IOException | RuntimeException failure) {
            for (String suffix : List.of("", ".city", ".jeep", ".tmp")) Files.deleteIfExists(sidecar(target, suffix));
            Files.deleteIfExists(slot);
            throw failure;
        }
    }
    public static Path sidecar(Path world, String suffix) {
        return world.resolveSibling(world.getFileName() + suffix);
    }
    /** Install the built-in benchmark once, preserving every existing save with this name. */
    public void ensureStressGrid(long seed) throws IOException {
        if (Files.isDirectory(directory)) try (var slots=Files.list(directory)) {
            if (slots.anyMatch(p -> p.getFileName().toString().equalsIgnoreCase(dev.jayms.net.city.StressGrid.NAME))) return;
        }
        createStressGrid(seed);
    }

    /** Create the benchmark as an independent save; never overwrite an existing slot. */
    public Path createStressGrid(long seed) throws IOException {
        Path slot = reserve(dev.jayms.net.city.StressGrid.NAME), target = slot.resolve("world.dat");
        try {
            new LocalGame(target, seed).save();
            var frame = new dev.jayms.net.city.CityFrame(dev.jayms.net.city.GameConfig.cityGame(),0,
                    List.of(),List.of(),List.of(),List.of(),List.of(),
                    dev.jayms.net.city.CityEconomy.State.empty(),new dev.jayms.net.city.CityAddresses.State(List.of(),List.of()),
                    dev.jayms.net.city.Agriculture.State.empty(),dev.jayms.net.city.RegionalPopulation.State.empty(),
                    dev.jayms.net.city.Aviation.State.empty(),dev.jayms.net.city.Railway.State.empty(),
                    dev.jayms.net.city.StressGrid.standard());
            try (var out = new java.io.DataOutputStream(Files.newOutputStream(sidecar(target,".city")))) {
                out.writeInt(0x4349543F); frame.write(out,15);
            }
            validate(target,seed);
            return target;
        } catch(IOException | RuntimeException e) {
            Files.deleteIfExists(sidecar(target,".tmp"));
            Files.deleteIfExists(sidecar(target,".city")); Files.deleteIfExists(target); Files.deleteIfExists(slot);
            throw e;
        }
    }
    public static void validate(Path world, long seed) throws IOException {
        if (!Files.isRegularFile(world)) throw new IOException("Save is missing.");
        new LocalGame(world, seed);
        var city = CitySimulation.load(sidecar(world, ".city"));
        if (city != null && !city.config().city()) throw new IOException("This save is not a city simulation.");
    }
}
