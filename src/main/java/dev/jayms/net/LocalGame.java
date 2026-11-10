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
    public final Map<Integer, ItemDrop> drops;
    public Map<String, Protocol.Edit> edits = new WorldVoxels.History();
    public final long seed;
    public final int generatorVersion;
    private boolean regionalSaves;
    private int nextDrop;
    private float fallTop;
    private final Path save;
    private final boolean replayComplete;
    private final String readbackFingerprint;
    public boolean replayComplete(){return replayComplete;}
    public String readbackFingerprint(){return readbackFingerprint;}
    private void requireReplay()throws IOException {
        if(!replayComplete)throw new IOException("Offline world replay is incomplete");
    }
    private void requireReplayState() {
        if(!replayComplete)throw new IllegalStateException("Offline world replay is incomplete");
    }
    /** Worker-prepared, single-transfer owner state. Independent maps/inventory
     * are allocated on the worker; only frozen model definitions are shared. */
    static final class ReadbackMetadata {
        private final Path save;
        private final long seed;
        private final int generator,health,nextDrop;
        private final String fingerprint;
        private ModelLibrary models;
        private Inventory inventory;
        private Map<Integer,ItemDrop> drops;
        private boolean transferred;
        private ReadbackMetadata(Path save,Header header,LoadedDrops loaded,String fingerprint) {
            this.save=save;seed=header.seed();generator=header.generator();health=header.health();nextDrop=loaded.nextDrop();this.fingerprint=fingerprint;
            models=header.models().copy();inventory=header.inventory().copy();drops=new LinkedHashMap<>(loaded.drops());
        }
        String fingerprint(){return fingerprint;}
        synchronized LocalGame transfer()throws IOException {
            if(transferred)throw new IOException("Replay metadata already transferred");
            var result=new LocalGame(this);transferred=true;models=null;inventory=null;drops=null;return result;
        }
    }
    /** Metadata only: no replay, live world, city simulation or writable save. */
    private LocalGame(ReadbackMetadata metadata) {
        save=metadata.save;seed=metadata.seed;generatorVersion=metadata.generator;health=metadata.health;nextDrop=metadata.nextDrop;
        models=metadata.models;inventory=metadata.inventory;drops=metadata.drops;
        edits=Collections.emptyMap();regionalSaves=true;replayComplete=false;readbackFingerprint=metadata.fingerprint;
    }

    public LocalGame(Path save, long requestedSeed) throws IOException {
        this(save,requestedSeed,()->false,count->{});
    }

    /** Private worker load until returned. Progress counts replay records. Never
     * writes the source or publishes partially decoded state. */
    public LocalGame(Path save,long requestedSeed,BooleanSupplier cancel,IntConsumer progress)throws IOException {
        Objects.requireNonNull(save);Objects.requireNonNull(cancel);Objects.requireNonNull(progress);
        drops=new LinkedHashMap<>();replayComplete=true;readbackFingerprint="";
        StructurePackage.cancelled(cancel);progress.accept(0);StructurePackage.cancelled(cancel);
        this.save = save;
        long worldSeed = requestedSeed;
        int generator = Terrain.CURRENT_VERSION;
        if (Files.exists(save))
            try (var in = new DataInputStream(new BufferedInputStream(new LoadInput(Files.newInputStream(save),cancel), 64 * 1024))) {
                var header=readHeader(in,cancel);
                int version=header.version();regionalSaves=version==10;
                worldSeed=header.seed();generator=header.generator();
                if(header.models()!=null)models=header.models();
                inventory=header.inventory();health=header.health();
                if(regionalSaves) {
                    var body=readRegionalBody(Path.of(System.getProperty("java.io.tmpdir")),in,edits,cancel,progress);
                    drops.putAll(body.drops());nextDrop=body.nextDrop();
                }
                else {
                    int n = in.readInt();
                    if (n < 0 || n > MAX_SAVED_EDITS) throw new IOException("Invalid world");
                    var replay=new WorldVoxels.ReplayWriter(edits,cancel);
                    for (int i = 0; i < n; i++) {
                        StructurePackage.cancelled(cancel);
                        var e =
                                version >= 6
                                        ? Protocol.Edit.read(in)
                                        : version == 5
                                                ? Protocol.Edit.readV7(in)
                                                : Protocol.Edit.readLegacy(in);
                        if (!e.valid()) throw new IOException("Invalid edit");
                        replay.accept(e);
                        if(((i+1)&127)==0)progress.accept(i+1);
                    }
                    replay.finish();progress.accept(n);StructurePackage.cancelled(cancel);
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

    private record Header(int version,long seed,int generator,ModelLibrary models,Inventory inventory,int health) {}
    /** Same prefix validation for resident loading and private regional readback. */
    private static Header readHeader(DataInputStream in,BooleanSupplier cancel)throws IOException {
        StructurePackage.cancelled(cancel);
        int version=in.readInt();
        if(version!=3&&version!=4&&version!=5&&version!=6&&version!=7&&version!=8&&version!=9&&version!=10)
            throw new IOException("Invalid offline world");
        long seed=in.readLong();int generator=version>=7?in.readInt():Terrain.LEGACY_VERSION;
        try{new Terrain(seed,generator);}catch(IllegalArgumentException error){throw new IOException("Unsupported terrain generator",error);}
        ModelLibrary models=version>=4?(version>=9?ModelLibrary.readAll(in):ModelLibrary.read(in)):null;
        var inventory=Inventory.read(in,version>=8);int health=in.readUnsignedByte();
        if(health>20)throw new IOException("Invalid saved health");
        StructurePackage.cancelled(cancel);return new Header(version,seed,generator,models,inventory,health);
    }
    /** Read-only v10 opener for a RegionReplayWorker factory. No resident replay
     * history is built. The complete prefix, model references, tail and EOF pass
     * before a source escapes. The hash identifies the bytes read, not the live
     * path or authority; callers still need real installation revision fences. */
    static RegionReplayWorker.Source openRegionalReadback(Path save,Path directory,RegionReplayWorker.Revision revision,
            BooleanSupplier cancel,IntConsumer progress)throws IOException {
        Objects.requireNonNull(save);Objects.requireNonNull(directory);Objects.requireNonNull(revision);
        Objects.requireNonNull(cancel);Objects.requireNonNull(progress);StructurePackage.cancelled(cancel);
        java.security.MessageDigest digest;
        try{digest=java.security.MessageDigest.getInstance("SHA-256");}
        catch(java.security.NoSuchAlgorithmException error){throw new IOException("SHA-256 unavailable",error);}
        RegionReplaySpool spool=null;
        try(var in=new DataInputStream(new BufferedInputStream(new java.security.DigestInputStream(
                new LoadInput(Files.newInputStream(save),cancel),digest),64*1024))) {
            var header=readHeader(in,cancel);
            if(header.version()!=10)throw new IOException("Regional readback requires version 10");
            var definitions=header.models();
            for(int slot=0;slot<Inventory.SIZE;slot++) {
                StructurePackage.cancelled(cancel);
                if(!definitions.has(header.inventory().type(slot)))throw new IOException("Missing saved inventory model");
            }
            java.util.function.Predicate<Protocol.Edit> allowed=edit->definitions.has(edit.type());
            spool=RegionReplaySpool.stage(directory,in,allowed,cancel,RegionReplaySpool.MAX_BYTES,progress);
            var loaded=readRegionalTail(in,cancel,definitions::has);StructurePackage.cancelled(cancel);
            String fingerprint=HexFormat.of().formatHex(digest.digest());
            var metadata=new ReadbackMetadata(save,header,loaded,fingerprint);StructurePackage.cancelled(cancel);
            return new RegionReplayWorker.Source(spool,new Terrain(header.seed(),header.generator()),allowed,revision,fingerprint,metadata);
        }catch(IOException|RuntimeException|Error failure) {
            if(spool!=null)try{spool.close();}catch(IOException cleanup){failure.addSuppressed(cleanup);}throw failure;
        }
    }

    record LoadedDrops(Map<Integer,ItemDrop> drops,int nextDrop) {}
    private static LoadedDrops readDrops(DataInputStream in,boolean current,BooleanSupplier cancel,java.util.function.IntPredicate allowed)throws IOException {
        int count=in.readInt();
        if(count<0||count>100000)throw new IOException("Invalid drops");
        var drops=new LinkedHashMap<Integer,ItemDrop>();int next=0;
        for(int i=0;i<count;i++) {
            StructurePackage.cancelled(cancel);var drop=ItemDrop.read(in,current);
            if(!allowed.test(drop.type()))throw new IOException("Missing dropped model");
            drops.put(drop.id(),drop);next=Math.max(next,drop.id());
        }
        return new LoadedDrops(Collections.unmodifiableMap(drops),next);
    }
    /** Shared tail gate for private resident or paged replay staging. This
     * validates transient records without admitting a replay prefix as a world.
     * Prefix metadata/models remain the opener's responsibility. */
    static LoadedDrops readRegionalTail(DataInputStream in,BooleanSupplier cancel)throws IOException {
        return readRegionalTail(in,cancel,type->true);
    }
    private static LoadedDrops readRegionalTail(DataInputStream in,BooleanSupplier cancel,java.util.function.IntPredicate allowed)throws IOException {
        var drops=readDrops(in,true,cancel,allowed);
        if(in.read()!=-1)throw new IOException("Trailing regional offline world");
        StructurePackage.cancelled(cancel);return drops;
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
            var drops=readRegionalTail(in,cancel);
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
        requireReplay();
        startGame(config, ground, ProductionCatalog.cityGame());
    }

    public void startGame(
            GameConfig config, CitySimulation.Ground ground, ProductionCatalog catalog)
            throws IOException {
        requireReplay();
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
        requireReplay();
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
        requireReplayState();
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
        requireReplayState();
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
        requireReplayState();
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
        requireReplayState();
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
        requireReplay();
        saveWorld();
        if (city != null) city.save(save.resolveSibling(save.getFileName() + ".city"));
    }

    private void saveWorld() throws IOException { saveWorld(false); }

    private void saveWorld(boolean requireAtomic) throws IOException {
        requireReplay();
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
        requireReplay();
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
        requireReplay();
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
        requireReplayState();
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
