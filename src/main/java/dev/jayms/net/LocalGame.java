package dev.jayms.net;

import dev.jayms.net.model.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Offline survival state uses the same stack rules and terrain as multiplayer. */
public final class LocalGame {
    public ModelLibrary models = new ModelLibrary();
    public Inventory inventory = new Inventory();
    public int health = 20;
    public final Map<Integer, ItemDrop> drops = new LinkedHashMap<>();
    public final Map<String, Protocol.Edit> edits = new LinkedHashMap<>();
    public final long seed;
    private int nextDrop;
    private float fallTop;
    private final Path save;

    public LocalGame(Path save, long requestedSeed) throws IOException {
        this.save = save;
        long worldSeed = requestedSeed;
        if (Files.exists(save))
            try (var in = new DataInputStream(Files.newInputStream(save))) {
                int version = in.readInt();
                if (version != 3 && version != 4 && version != 5)
                    throw new IOException("Invalid offline world");
                worldSeed = in.readLong();
                if (version >= 4) models = ModelLibrary.read(in);
                inventory = Inventory.read(in);
                health = in.readUnsignedByte();
                if (health > 20) throw new IOException("Invalid saved health");
                int n = in.readInt();
                if (n < 0 || n > 2000000) throw new IOException("Invalid world");
                for (int i = 0; i < n; i++) {
                    var e = version >= 5 ? Protocol.Edit.read(in) : Protocol.Edit.readLegacy(in);
                    if (!e.valid()) throw new IOException("Invalid edit");
                    WorldVoxels.remember(edits, e);
                }
                n = in.readInt();
                if (n < 0 || n > 100000) throw new IOException("Invalid drops");
                for (int i = 0; i < n; i++) {
                    ItemDrop d = ItemDrop.read(in);
                    drops.put(d.id(), d);
                    nextDrop = Math.max(nextDrop, d.id());
                }
            }
        seed = worldSeed;
    }

    public String createModel(ModelDefinition definition) throws IOException {
        int existing = models.find(definition), id = existing < 0 ? models.nextId() : existing;
        if (!inventory.hasSpace(id))
            throw new IOException("Inventory full: make an empty slot first");
        var model = models.register(definition, "offline");
        inventory.add(model.id(), 1);
        return "Created "
                + model.definition().name()
                + ". Close the editor and select its hotbar slot to place it.";
    }

    public boolean edit(Protocol.Edit e, int old, int slot) {
        if (!e.valid()) return false;
        if (e.type() == 0) {
            if (old == 0 || old == Blocks.PARTIAL) return false;
            ItemDrop d =
                    new ItemDrop(
                            ++nextDrop,
                            old,
                            1,
                            e.minX() + e.size() / 2,
                            e.minY() + .35f,
                            e.minZ() + e.size() / 2);
            drops.put(d.id(), d);
        } else if (!inventory.take(slot, e.type())) return false;
        WorldVoxels.remember(edits, e);
        return true;
    }

    public String craft(int recipe, Protocol.Pose pose) {
        var result = Crafting.prepare(inventory, recipe);
        if (!result.accepted()) return result.message();
        if (result.excess() > 0 && drops.size() >= 100000)
            return "Too many ground items: collect some before crafting.";
        inventory = result.inventory();
        if (result.excess() > 0) {
            var drop =
                    new ItemDrop(
                            ++nextDrop,
                            result.output(),
                            result.excess(),
                            pose.x() + .4f,
                            pose.y() + .35f,
                            pose.z());
            drops.put(drop.id(), drop);
        }
        return result.message();
    }

    public boolean tick(
            Protocol.Pose pose,
            boolean grounded,
            float dt,
            java.util.function.ToIntFunction<Protocol.Edit> block) {
        if (pose.flying()) fallTop = pose.y();
        else {
            fallTop = Math.max(fallTop, pose.y());
            if (grounded) {
                health =
                        Math.max(0, health - Math.max(0, (int) Math.floor(fallTop - pose.y() - 3)));
                fallTop = pose.y();
            }
        }
        var it = drops.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            var d = e.getValue();
            float y = d.y();
            if (block.applyAsInt(
                            new Protocol.Edit(
                                    (int) Math.floor(d.x()),
                                    (int) Math.floor(y - .3),
                                    (int) Math.floor(d.z()),
                                    0))
                    == 0) y = Math.max(Terrain.MIN_Y + .3f, y - Math.min(dt, .1f) * 3);
            int count = d.count();
            if (Math.pow(d.x() - pose.x(), 2)
                            + Math.pow(y - pose.y() - .7, 2)
                            + Math.pow(d.z() - pose.z(), 2)
                    <= 4) count = inventory.add(d.type(), count);
            if (count == 0) it.remove();
            else e.setValue(new ItemDrop(d.id(), d.type(), count, d.x(), y, d.z()));
        }
        if (health == 0) {
            health = 20;
            fallTop = 0;
            return true;
        }
        return false;
    }

    public void save() throws IOException {
        Files.createDirectories(save.toAbsolutePath().getParent());
        Path temp = save.resolveSibling(save.getFileName() + ".tmp");
        try (var out = new DataOutputStream(Files.newOutputStream(temp))) {
            out.writeInt(5);
            out.writeLong(seed);
            models.write(out);
            inventory.write(out);
            out.writeByte(health);
            out.writeInt(edits.size());
            for (var e : edits.values()) e.write(out);
            out.writeInt(drops.size());
            for (var d : drops.values()) d.write(out);
        }
        try {
            Files.move(
                    temp,
                    save,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, save, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
