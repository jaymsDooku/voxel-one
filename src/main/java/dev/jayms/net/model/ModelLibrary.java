package dev.jayms.net.model;

import dev.jayms.net.*;

import java.io.*;
import java.util.*;

/** Each world assigns stable item IDs to immutable model definitions. */
public final class ModelLibrary {
    public static final int MAX_CUSTOM = 128;

    public record Entry(int id, String author, ModelDefinition definition) {
        public void write(DataOutputStream out) throws IOException {
            out.writeByte(id);
            out.writeUTF(author);
            definition.write(out);
        }

        public static Entry read(DataInputStream in) throws IOException {
            int id = in.readUnsignedByte();
            String author = Protocol.readText(in, 16);
            return new Entry(id, author, ModelDefinition.read(in));
        }
    }

    private static final ModelDefinition POT = ModelGenerators.flowerPot();
    private final Map<Integer, Entry> entries = new LinkedHashMap<>();
    private final Map<String, Integer> fingerprints = new HashMap<>();

    public ModelLibrary() {
        var pot = POT;
        putUnchecked(
                new Entry(
                        Blocks.FLOWER_POT,
                        "engine",
                        new ModelDefinition(pot.name(), pot.voxels().freeze())));
    }

    public Entry get(int type) {
        return entries.get(type);
    }

    public boolean has(int type) {
        return !Blocks.isModel(type) || entries.containsKey(type);
    }

    public String name(int type) {
        Entry e = get(type);
        return e == null ? Blocks.name(type) : e.definition.name();
    }

    public Collection<Entry> entries() {
        return Collections.unmodifiableCollection(entries.values());
    }

    public int find(ModelDefinition definition) {
        return fingerprints.getOrDefault(definition.fingerprint(), -1);
    }

    public int nextId() throws IOException {
        for (int i = 9; i < 9 + MAX_CUSTOM; i++) if (!entries.containsKey(i)) return i;
        throw new IOException("This world's model library is full");
    }

    public Entry register(ModelDefinition definition, String author) throws IOException {
        int existing = find(definition);
        if (existing >= 0) return get(existing);
        if (definition.voxels().occupied() == 0)
            throw new IOException("Add voxels before creating an item");
        if (entries.values().stream().filter(e -> e.author.equals(author)).count() >= 16)
            throw new IOException("Each player can publish up to 16 unique models per world");
        ModelMesher.mesh(definition.voxels());
        int id = nextId();
        var entry =
                new Entry(
                        id,
                        author,
                        new ModelDefinition(
                                definition.name(), definition.voxels().copy().freeze()));
        putUnchecked(entry);
        return entry;
    }

    public void accept(Entry entry) throws IOException {
        if (entry.id < 9
                || entry.id >= 9 + MAX_CUSTOM
                || entries.containsKey(entry.id)
                || entry.definition.voxels().occupied() == 0)
            throw new IOException("Invalid model definition");
        ModelMesher.mesh(entry.definition.voxels());
        putUnchecked(
                new Entry(
                        entry.id,
                        entry.author,
                        new ModelDefinition(
                                entry.definition.name(),
                                entry.definition.voxels().copy().freeze())));
    }

    private void putUnchecked(Entry entry) {
        entries.put(entry.id, entry);
        fingerprints.put(entry.definition.fingerprint(), entry.id);
    }

    public ModelLibrary copy() {
        ModelLibrary result = new ModelLibrary();
        for (var e : entries.values()) if (e.id != Blocks.FLOWER_POT) result.putUnchecked(e);
        return result;
    }

    public void write(DataOutputStream out) throws IOException {
        out.writeInt(entries.size() - 1);
        for (var e : entries.values()) if (e.id != Blocks.FLOWER_POT) e.write(out);
    }

    public static ModelLibrary read(DataInputStream in) throws IOException {
        int count = in.readInt();
        if (count < 0 || count > MAX_CUSTOM) throw new IOException("Invalid model count");
        ModelLibrary result = new ModelLibrary();
        for (int i = 0; i < count; i++) result.accept(Entry.read(in));
        return result;
    }
}
