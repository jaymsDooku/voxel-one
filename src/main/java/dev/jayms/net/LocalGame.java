package dev.jayms.net;

import dev.jayms.net.city.*;
import dev.jayms.net.model.*;
import dev.jayms.net.structure.*;

import java.io.*;
import java.nio.file.*;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;

/** Offline survival state uses the same stack rules and terrain as multiplayer. */
public final class LocalGame {
    static final int MAX_SAVED_EDITS = 2_000_000;
    public CitySimulation city;
    public ModelLibrary models = new ModelLibrary();
    public Inventory inventory = new Inventory();
    public int health = 20;
    public final Map<Integer, ItemDrop> drops = new LinkedHashMap<>();
    public Map<String, Protocol.Edit> edits = new WorldVoxels.History();
    public final long seed;
    public final int generatorVersion;
    private boolean regionalSaves;
    private int nextDrop;
    private float fallTop;
    private final Path save;

    public LocalGame(Path save, long requestedSeed) throws IOException {
        this(save,requestedSeed,()->false,count->{});
    }

    /** Private worker load until returned. Progress counts replay records. Never
     * writes the source or publishes partially decoded state. */
    public LocalGame(Path save,long requestedSeed,BooleanSupplier cancel,IntConsumer progress)throws IOException {
        Objects.requireNonNull(save);Objects.requireNonNull(cancel);Objects.requireNonNull(progress);
        StructurePackage.cancelled(cancel);progress.accept(0);StructurePackage.cancelled(cancel);
        this.save = save;
        long worldSeed = requestedSeed;
        int generator = Terrain.CURRENT_VERSION;
        if (Files.exists(save))
            try (var in = new DataInputStream(new BufferedInputStream(new LoadInput(Files.newInputStream(save),cancel), 64 * 1024))) {
                int version = in.readInt();
                if (version != 3 && version != 4 && version != 5 && version != 6 && version != 7 && version != 8 && version != 9 && version != 10)
                    throw new IOException("Invalid offline world");
                regionalSaves=version==10;
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
                if(regionalSaves) {
                    var body=readRegionalBody(Path.of(System.getProperty("java.io.tmpdir")),in,edits,cancel,progress);
                    drops.putAll(body.drops());nextDrop=body.nextDrop();
                }
                else {
                    int n = in.readInt();
                    if (n < 0 || n > MAX_SAVED_EDITS) throw new IOException("Invalid world");
                    for (int i = 0; i < n; i++) {
                        StructurePackage.cancelled(cancel);
                        var e =
                                version >= 6
                                        ? Protocol.Edit.read(in)
                                        : version == 5
                                                ? Protocol.Edit.readV7(in)
                                                : Protocol.Edit.readLegacy(in);
                        if (!e.valid()) throw new IOException("Invalid edit");
                        WorldVoxels.rememberValidated(edits, e);
                        if(((i+1)&127)==0)progress.accept(i+1);
                    }
                    progress.accept(n);StructurePackage.cancelled(cancel);
                }
                if(!regionalSaves) {
                    int n=in.readInt();
                    if(n<0||n>100000)throw new IOException("Invalid drops");
                    for(int i=0;i<n;i++) {
                        StructurePackage.cancelled(cancel);var drop=ItemDrop.read(in,version>=8);
                        drops.put(drop.id(),drop);nextDrop=Math.max(nextDrop,drop.id());
                    }
                }
            }
        StructurePackage.cancelled(cancel);
        seed = worldSeed;
        generatorVersion = generator;
    }

    record LoadedDrops(Map<Integer,ItemDrop> drops,int nextDrop) {}
    private static LoadedDrops readDrops(DataInputStream in,boolean current,BooleanSupplier cancel)throws IOException {
        int count=in.readInt();
        if(count<0||count>100000)throw new IOException("Invalid drops");
        var drops=new LinkedHashMap<Integer,ItemDrop>();int next=0;
        for(int i=0;i<count;i++) {
            StructurePackage.cancelled(cancel);var drop=ItemDrop.read(in,current);
            drops.put(drop.id(),drop);next=Math.max(next,drop.id());
        }
        return new LoadedDrops(Collections.unmodifiableMap(drops),next);
    }
    /** Worker-private target only. Validate the whole source tail before building
     * resident history. Exact canonical groups use compact rows; this is not live paging.
     * Progress counts source records, not completion of the later private drain. */
    static LoadedDrops readRegionalBody(Path directory,DataInputStream in,Map<String,Protocol.Edit> target,
            BooleanSupplier cancel,IntConsumer progress)throws IOException {
        Objects.requireNonNull(target);
        if(!target.isEmpty())throw new IllegalArgumentException("Fresh private regional history required");
        try(var spool=RegionReplaySpool.stage(directory,
                in,edit->true,cancel,RegionReplaySpool.MAX_BYTES,progress)) {
            var drops=readDrops(in,true,cancel);
            if(in.read()!=-1)throw new IOException("Trailing regional offline world");
            StructurePackage.cancelled(cancel);
            var loader=new RegionReplayLoader(target,cancel);
            spool.visit(edit->true,cancel,(region,section)->{
                for(var edit:section){StructurePackage.cancelled(cancel);loader.accept(edit);}
            });
            loader.finish();
            StructurePackage.cancelled(cancel);return drops;
        }
    }

    /** Below buffering, checks cancellation around at most 64 KiB per read.
     * This cannot interrupt a blocked filesystem call; callers must use a worker. */
    private static final class LoadInput extends FilterInputStream {
        private final BooleanSupplier cancel;
        LoadInput(InputStream input,BooleanSupplier cancel){super(input);this.cancel=cancel;}
        @Override public int read()throws IOException {
            StructurePackage.cancelled(cancel);int result=in.read();StructurePackage.cancelled(cancel);return result;
        }
        @Override public int read(byte[] bytes,int offset,int length)throws IOException {
            Objects.checkFromIndexSize(offset,length,bytes.length);StructurePackage.cancelled(cancel);
            int result=in.read(bytes,offset,Math.min(length,65536));StructurePackage.cancelled(cancel);return result;
        }
        @Override public long skip(long count)throws IOException {
            StructurePackage.cancelled(cancel);long result=in.skip(Math.min(Math.max(0,count),65536));
            StructurePackage.cancelled(cancel);return result;
        }
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

    private void saveWorld() throws IOException { saveWorld(false); }

    private void saveWorld(boolean requireAtomic) throws IOException {
        int editCount = edits.size();
        if (editCount < 0 || editCount > MAX_SAVED_EDITS)
            throw new IOException("World edit persistence limit");
        Files.createDirectories(save.toAbsolutePath().getParent());
        Path temp = save.resolveSibling(save.getFileName() + ".tmp");
        var channel=FileChannel.open(temp,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING,StandardOpenOption.WRITE);
        boolean committed=false;
        try {
            try (channel;var out = new DataOutputStream(new BufferedOutputStream(Channels.newOutputStream(channel), 64 * 1024))) {
                out.writeInt(regionalSaves?10:models.hasStructureModels()?9:8);
                out.writeLong(seed);
                out.writeInt(generatorVersion);
                if(regionalSaves||models.hasStructureModels())models.writeAll(out);else models.write(out);
                inventory.write(out);
                out.writeByte(health);
                if(regionalSaves)RegionReplay.write(out,edits,()->false,p->{});
                else {out.writeInt(editCount);for(var e:edits.values())e.write(out);}
                out.writeInt(drops.size());
                for (var d : drops.values()) d.write(out);
                out.flush();
                channel.force(true);
            }
            WorldSavePublication.replace(temp,save,requireAtomic);committed=true;
        } finally {if(!committed)Files.deleteIfExists(temp);}
    }

    /** Private worker-built history, consumed only after owner-thread revision checks. */
    public static final class StructureHistory implements AutoCloseable {
        private StagedOfflineSave save;
        private List<ModelLibrary.Entry> saveModels;
        @Override public void close()throws IOException {if(save!=null)save.close();}
        private final LocalGame owner;
        private final WorldVoxels.History base,next;
        private final Map<StructurePackage.Position,SparseVoxelOctree> cells;
        private final List<dev.jayms.World.CellReplacement> replacements;
        private final long revision;
        private StructureHistory(LocalGame owner,WorldVoxels.History base,long revision,WorldVoxels.History next,Map<StructurePackage.Position,SparseVoxelOctree> cells,List<dev.jayms.World.CellReplacement> replacements) {
            this.owner=owner;this.base=base;this.revision=revision;this.next=next;this.cells=cells;this.replacements=List.copyOf(replacements);
        }
    }
    private record HistorySource(WorldVoxels.History base,long revision,WorldVoxels.History next) {}

    /** Worker only, for immutable engine-prepared cells. Capture history roots on
     * the owner; prepare detached edits on the worker and revalidate on the owner.
     * The result remains bound to this exact cell map and commit revision. */
    public StructureHistory stageStructureHistory(Map<StructurePackage.Position,SparseVoxelOctree> cells,
            StructureOwnerQueue owner,BooleanSupplier cancel,IntConsumer progress)throws IOException {
        var source=owner.call(()->{
            StructurePackage.cancelled(cancel);
            // Preserve the existing synchronous path for legacy/custom map implementations.
            if(!(edits instanceof WorldVoxels.History base))return null;
            if(base.size()>MAX_SAVED_EDITS)throw new IOException("World edit persistence limit");
            return new HistorySource(base,base.revision(),WorldVoxels.History.copyOf(base));
        },cancel);
        if(source==null){progress.accept(100);return null;}
        var next=source.next();long copied=0,total=Math.max(1L,cells.size());
        if(next.replacementSize(cells,cancel)>MAX_SAVED_EDITS)throw new IOException("World edit persistence limit");
        progress.accept(0);
        var replacements=new ArrayList<dev.jayms.World.CellReplacement>(cells.size());
        for(var entry:cells.entrySet()) {
            StructurePackage.cancelled(cancel);var p=entry.getKey();
            var replacement=dev.jayms.World.prepareCellReplacement(p.x(),p.y(),p.z(),entry.getValue());
            StructurePackage.cancelled(cancel);replacements.add(replacement);
            WorldVoxels.rememberCell(next,replacement.replay());
            progress.accept((int)(100*++copied/total));
        }
        StructurePackage.cancelled(cancel);
        if(next.size()>MAX_SAVED_EDITS)throw new IOException("World edit persistence limit");
        owner.call(()->{
            StructurePackage.cancelled(cancel);
            if(edits!=source.base()||source.base().revision()!=source.revision())
                throw new IOException("World changed while staging save history; retry placement");
            return true;
        },cancel);
        progress.accept(100);StructurePackage.cancelled(cancel);return new StructureHistory(this,source.base(),source.revision(),next,cells,replacements);
    }

    /** Worker only. Prepared models and history stay detached until owner revalidation. */
    public void stageStructureSave(StructureHistory history,ModelLibrary prepared,BooleanSupplier cancel,IntConsumer progress)throws IOException {
        if(history==null)return; // Legacy history maps retain synchronous saving.
        if(history.owner!=this||history.save!=null)throw new IOException("Invalid staged history save");
        var snapshot=prepared.copy();
        history.saveModels=List.copyOf(snapshot.entries());
        history.save=StagedOfflineSave.stageRegions(save,seed,generatorVersion,snapshot,history.next,cancel,progress);
    }

    public dev.jayms.net.structure.StructureEngine.Authority structureAuthority(dev.jayms.World world) {
        return structureAuthority(world, () -> null);
    }

    public dev.jayms.net.structure.StructureEngine.Authority structureAuthority(dev.jayms.World world, java.util.function.Supplier<Protocol.Pose> player) {
        return structureAuthority(world,player,null);
    }

    public dev.jayms.net.structure.StructureEngine.Authority structureAuthority(dev.jayms.World world, java.util.function.Supplier<Protocol.Pose> player,StructureHistory history) {
        return new dev.jayms.net.structure.StructureEngine.Authority() {
            public Object lock(){return LocalGame.this;}
            public ModelLibrary models(){return models;}
            public SparseVoxelOctree cell(int x,int y,int z){return world.cell(x,y,z);}
            public Object chunkStamp(dev.jayms.ChunkPos position){return world.structureChunkStamp(position);}
            @Override public dev.jayms.net.structure.StructureEngine.Commit stageCommit(ModelLibrary prepared,Map<StructurePackage.Position,SparseVoxelOctree> cells,Map<StructurePackage.Position,SparseVoxelOctree> beforeImages,long revision,BooleanSupplier cancel)throws IOException {
                StructurePackage.cancelled(cancel);
                // Engine applies this only after revalidating every before-image under lock.
                return ()->publish(prepared,cells,beforeImages);
            }
            public void commit(ModelLibrary prepared,Map<dev.jayms.net.structure.StructurePackage.Position,SparseVoxelOctree> cells)throws IOException {
                publish(prepared,cells,null);
            }
            private void publish(ModelLibrary prepared,Map<StructurePackage.Position,SparseVoxelOctree> cells,Map<StructurePackage.Position,SparseVoxelOctree> verifiedBefore)throws IOException {
                var livePlayer=player.get();
                dev.jayms.net.structure.StructureOccupancy.validate(cells,city==null?null:city.frame(),livePlayer==null?List.of():List.of(livePlayer));
                if(history!=null&&(history.owner!=LocalGame.this||history.cells!=cells||edits!=history.base||history.base.revision()!=history.revision))
                    throw new IOException("World changed while staging save history; retry placement");
                if(history!=null&&history.save!=null&&!history.saveModels.equals(List.copyOf(prepared.entries())))
                    throw new IOException("Prepared models changed while staging save; retry placement");
                var original=edits;var originalModels=models.copy();var next=history==null?WorldVoxels.History.copyOf(original):history.next;
                Map<StructurePackage.Position,SparseVoxelOctree> before=verifiedBefore==null?new LinkedHashMap<>():verifiedBefore;
                if(verifiedBefore==null||history==null)for(var e:cells.entrySet()) {
                    var p=e.getKey();
                    if(verifiedBefore==null)before.put(p,world.cell(p.x(),p.y(),p.z()).copy().freeze());
                    if(history==null)WorldVoxels.rememberCell(next,new Protocol.CellState(p.x(),p.y(),p.z(),e.getValue()));
                }
                if(next.size()>MAX_SAVED_EDITS)throw new IOException("World edit persistence limit");
                try {
                    models.replaceWith(prepared);edits=next;
                    if(history!=null)world.replaceCells(history.replacements);
                    else for(var e:cells.entrySet()) {var p=e.getKey();world.replaceCell(p.x(),p.y(),p.z(),e.getValue());}
                    if(history!=null&&history.save!=null){history.save.publish(inventory,health,drops.values(),drops.size());regionalSaves=history.save.regional();}
                    else saveWorld(true);
                } catch(IOException|RuntimeException failure) {
                    edits=original;models.replaceWith(originalModels);
                    for(var e:before.entrySet()) {var p=e.getKey();world.replaceCell(p.x(),p.y(),p.z(),e.getValue());}
                    throw failure;
                }
            }
        };
    }
}
