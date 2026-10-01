package dev.jayms.net;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Offline survival state uses the same stack rules and terrain as multiplayer. */
public final class LocalGame {
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
                if (in.readInt() != 3) throw new IOException("Invalid offline world");
                worldSeed = in.readLong();
                inventory = Inventory.read(in);
                health = in.readUnsignedByte();
                if (health > 20) throw new IOException("Invalid saved health");
                int n = in.readInt();
                if (n < 0 || n > 2000000) throw new IOException("Invalid world");
                for (int i = 0; i < n; i++) {
                    var e = Protocol.Edit.read(in);
                    if (!e.valid()) throw new IOException("Invalid edit");
                    edits.put(e.key(), e);
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

    public boolean edit(Protocol.Edit e, int old, int slot) {
        if (e.type() == 0) {
            if (old == 0) return false;
            ItemDrop d = new ItemDrop(++nextDrop, old, 1, e.x() + .5f, e.y() + .35f, e.z() + .5f);
            drops.put(d.id(), d);
        } else if (!inventory.take(slot, e.type())) return false;
        edits.put(e.key(), e);
        return true;
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
            out.writeInt(3);
            out.writeLong(seed);
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
