package dev.jayms.net;

import dev.jayms.net.city.*;
import dev.jayms.net.model.*;

import java.io.*;
import java.nio.file.*;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.util.*;

/** Offline survival state uses the same stack rules and terrain as multiplayer. */
public final class LocalGame {
    public CitySimulation city;
    public ModelLibrary models = new ModelLibrary();
    public Inventory inventory = new Inventory();
    public int health = 20;
    public final Map<Integer, ItemDrop> drops = new LinkedHashMap<>();
    public Map<String, Protocol.Edit> edits = new WorldVoxels.History();
    public final long seed;
    public final int generatorVersion;
    private int nextDrop;
    private float fallTop;
    private final Path save;

    public LocalGame(Path save, long requestedSeed) throws IOException {
        this.save = save;
        long worldSeed = requestedSeed;
        int generator = Terrain.CURRENT_VERSION;
        if (Files.exists(save))
            try (var in = new DataInputStream(Files.newInputStream(save))) {
                int version = in.readInt();
                if (version != 3 && version != 4 && version != 5 && version != 6 && version != 7 && version != 8 && version != 9)
                    throw new IOException("Invalid offline world");
                worldSeed = in.readLong();
                generator = version >= 7 ? in.readInt() : Terrain.LEGACY_VERSION;
                try {
                    new Terrain(worldSeed, generator);
                } catch (IllegalArgumentException e) {
                    throw new IOException("Unsupported terrain generator", e);
                }
                if (version >= 4) models = version>=9?ModelLibrary.readAll(in):ModelLibrary.read(in);
                inventory = Inventory.read(in,version>=8);
                health = in.readUnsignedByte();
                if (health > 20) throw new IOException("Invalid saved health");
                int n = in.readInt();
                if (n < 0 || n > 2000000) throw new IOException("Invalid world");
                for (int i = 0; i < n; i++) {
                    var e =
                            version >= 6
                                    ? Protocol.Edit.read(in)
                                    : version == 5
                                            ? Protocol.Edit.readV7(in)
                                            : Protocol.Edit.readLegacy(in);
                    if (!e.valid()) throw new IOException("Invalid edit");
                    WorldVoxels.remember(edits, e);
                }
                n = in.readInt();
                if (n < 0 || n > 100000) throw new IOException("Invalid drops");
                for (int i = 0; i < n; i++) {
                    ItemDrop d = ItemDrop.read(in,version>=8);
                    drops.put(d.id(), d);
                    nextDrop = Math.max(nextDrop, d.id());
                }
            }
        seed = worldSeed;
        generatorVersion = generator;
    }

    public void startGame(GameConfig config, CitySimulation.Ground ground) throws IOException {
        startGame(config, ground, ProductionCatalog.cityGame());
    }

    public void startGame(
            GameConfig config, CitySimulation.Ground ground, ProductionCatalog catalog)
            throws IOException {
        var stored = CitySimulation.load(save.resolveSibling(save.getFileName() + ".city"));
        validateGame(stored, config);
        city =
                new CitySimulation(
                        stored == null ? config : stored.config(),
                        ground,
                        new Terrain(seed, generatorVersion),
                        stored,
                        catalog);
        city.loadHistory(save.resolveSibling(save.getFileName()+".city"));
        city.collectHistory();
    }

    /** Reject a cross-game path before any world edits or saves. Legacy sandbox saves have no sidecar. */
    public static void validateGame(Path save, GameConfig config) throws IOException {
        validateGame(CitySimulation.load(save.resolveSibling(save.getFileName() + ".city")), config);
    }

    private static void validateGame(CityFrame stored, GameConfig config) throws IOException {
        if (stored != null && stored.config().city() != config.city())
            throw new IOException("World belongs to " + (stored.config().city() ? "Voxel City One" : "Voxel Sandbox")
                    + "; select that game or use a different --world path.");
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
        if (e.type() == 0 && !Blocks.structureModel(old)) {
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
        } else if (e.type()!=0 && !inventory.take(slot, e.type())) return false;
        WorldVoxels.remember(edits, e);
        return true;
    }

    public String craft(int recipe, Protocol.Pose pose) { return craft(recipe,1,pose); }

    public String craft(int recipe,int quantity,Protocol.Pose pose) {
        if (!pose.valid()) return "Invalid craft position.";
        var result=Crafting.prepare(inventory,recipe,quantity,Crafting.MAX_DROPS-drops.size());
        if(!result.accepted()) return result.message();
        int needed=Crafting.dropSlots(result.output(),result.excess());
        if(nextDrop>Integer.MAX_VALUE-needed) return "Ground item IDs exhausted.";
        List<ItemDrop> prepared=new ArrayList<>();
        int excess=result.excess();
        for(int i=1;excess>0;i++) {
            int n=Math.min(excess,Items.stackLimit(result.output()));
            prepared.add(new ItemDrop(nextDrop+i,result.output(),n,pose.x()+.4f,pose.y()+.35f,pose.z()));
            excess-=n;
        }
        inventory=result.inventory();
        for(var d:prepared) drops.put(d.id(),d);
        nextDrop+=needed;
        return result.message();
    }

    public String drop(int slot,Protocol.Pose pose) {
        if(!pose.valid()||slot<0||slot>=Inventory.HOTBAR||inventory.count(slot)==0) return "Nothing to drop.";
        if(drops.size()>=Crafting.MAX_DROPS||nextDrop==Integer.MAX_VALUE) return "Too many ground items.";
        int type=inventory.type(slot);
        // Land beyond automatic pickup range; walk towards the item to collect it.
        var d=new ItemDrop(nextDrop+1,type,1,Items.dropX(pose.x()),pose.y()+.35f,pose.z());
        if(!inventory.take(slot,type)) return "Inventory changed.";
        nextDrop++; drops.put(d.id(),d);
        return "Dropped "+Items.name(type)+".";
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
        saveWorld();
        if (city != null) city.save(save.resolveSibling(save.getFileName() + ".city"));
    }

    private void saveWorld() throws IOException {
        Files.createDirectories(save.toAbsolutePath().getParent());
        Path temp = save.resolveSibling(save.getFileName() + ".tmp");
        try (var channel = FileChannel.open(temp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
                var out = new DataOutputStream(new BufferedOutputStream(Channels.newOutputStream(channel), 64 * 1024))) {
            out.writeInt(models.hasStructureModels()?9:8);
            out.writeLong(seed);
            out.writeInt(generatorVersion);
            if(models.hasStructureModels())models.writeAll(out);else models.write(out);
            inventory.write(out);
            out.writeByte(health);
            out.writeInt(edits.size());
            for (var e : edits.values()) e.write(out);
            out.writeInt(drops.size());
            for (var d : drops.values()) d.write(out);
            out.flush();
            channel.force(true);
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

    public dev.jayms.net.structure.StructureEngine.Authority structureAuthority(dev.jayms.World world) {
        return structureAuthority(world, () -> null);
    }

    public dev.jayms.net.structure.StructureEngine.Authority structureAuthority(dev.jayms.World world, java.util.function.Supplier<Protocol.Pose> player) {
        return new dev.jayms.net.structure.StructureEngine.Authority() {
            public Object lock(){return LocalGame.this;}
            public ModelLibrary models(){return models;}
            public SparseVoxelOctree cell(int x,int y,int z){return world.cell(x,y,z);}
            public void commit(ModelLibrary prepared,Map<dev.jayms.net.structure.StructurePackage.Position,SparseVoxelOctree> cells)throws IOException {
                var livePlayer=player.get();
                dev.jayms.net.structure.StructureOccupancy.validate(cells,city==null?null:city.frame(),livePlayer==null?List.of():List.of(livePlayer));
                var original=edits;var originalModels=models.copy();var next=WorldVoxels.History.copyOf(original);
                var before=new LinkedHashMap<dev.jayms.net.structure.StructurePackage.Position,SparseVoxelOctree>();
                for(var e:cells.entrySet()) {
                    var p=e.getKey();before.put(p,world.cell(p.x(),p.y(),p.z()).copy().freeze());
                    for(var edit:new Protocol.CellState(p.x(),p.y(),p.z(),e.getValue()).edits())WorldVoxels.remember(next,edit);
                }
                if(next.size()>2_000_000)throw new IOException("World edit persistence limit");
                try {
                    models.replaceWith(prepared);edits=next;
                    for(var e:cells.entrySet()) {var p=e.getKey();for(var edit:new Protocol.CellState(p.x(),p.y(),p.z(),e.getValue()).edits())world.apply(edit);}
                    saveWorld();
                } catch(IOException|RuntimeException failure) {
                    edits=original;models.replaceWith(originalModels);
                    for(var e:before.entrySet()) {var p=e.getKey();for(var edit:new Protocol.CellState(p.x(),p.y(),p.z(),e.getValue()).edits())world.apply(edit);}
                    throw failure;
                }
            }
        };
    }
}
