package dev.jayms;

import dev.jayms.net.*;
import dev.jayms.net.model.*;

import java.util.*;

public class World implements AutoCloseable {
    private final Map<ChunkPos, Chunk> loadedChunks = new HashMap<>();
    private final Map<ChunkPos,Chunk> dirtyMeshes=new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<ChunkPos,Set<ChunkPos>> columnMembers=new HashMap<>();
    private final Set<ChunkPos> dirtyColumns=new HashSet<>(),completeColumns=new HashSet<>();
    private Set<ChunkPos> visualColumns=Set.of();private long columnGeneration,visualGeneration=-1;
    private int visualMinX,visualMaxX,visualMinZ,visualMaxZ;
    private static ChunkPos column(ChunkPos p){return new ChunkPos(p.chunkX(),0,p.chunkZ());}
    private boolean canonicalMutation;
    void meshDirty(ChunkPos p,Chunk c){if(loadedChunks.get(p)==c){if(c.takeGeometryChange()){skyStamp++;if(!c.terrainEquivalent&&!canonicalMutation)columnGeometryVersions.put(column(p),++geometryEpoch);}dirtyMeshes.put(p,c);dirtyColumns.add(column(p));}}
    void meshClean(ChunkPos p,Chunk c){if(loadedChunks.get(p)==c){dirtyMeshes.remove(p);dirtyColumns.add(column(p));sceneRevision++;}}
    Map<ChunkPos,Chunk> dirtyMeshes(){return dirtyMeshes;}
    private void updateColumns(){for(ChunkPos p:dirtyColumns){boolean complete=columnMembers.containsKey(p)&&(!streamingColumns.contains(p)||columnMembers.get(p).size()==10);for(ChunkPos member:columnMembers.getOrDefault(p,Set.of())){Chunk c=loadedChunks.get(member);if(!c.isEmpty()&&c.getMesh()==null){complete=false;break;}}boolean changed=complete?completeColumns.add(p):completeColumns.remove(p);if(changed)columnGeneration++;}dirtyColumns.clear();}

    private final Map<ChunkPos, Map<String, Protocol.Edit>> edits = new HashMap<>();
    public record EditedCell(int x,int y,int z,SparseVoxelOctree voxels) {}
    private final Map<ChunkPos,Map<String,EditedCell>> editedCells=new HashMap<>();
    private long geometryEpoch;
    private final Map<ChunkPos,Long> columnGeometryVersions=new HashMap<>();
    private final Set<ChunkPos> editedCeilings=new HashSet<>();
    /** Natural terrain ends at95. Only actual higher overrides can occlude the GI top boundary. */
    public boolean isClosed(){return closed;}
    public boolean skyTerrainEquivalent(ChunkPos column){
        if(closed||columnMembers.getOrDefault(column,Set.of()).size()!=10)return false;
        for(int y=-2;y<=7;y++){var p=new ChunkPos(column.chunkX(),y,column.chunkZ());var c=loadedChunks.get(p);if(c==null||!c.terrainEquivalent||!c.matchesTerrain(terrain,p))return false;}return true;
    }
    public boolean requiresSkyCeiling(int x,int z){
        var column=new ChunkPos(x,0,z);if(editedCeilings.contains(column))return true;
        for(var p:columnMembers.getOrDefault(column,Set.of()))if(p.chunkY()>=6){var c=loadedChunks.get(p);if(c!=null&&!c.terrainEquivalent)return true;}
        return false;
    }
    public long geometryRevision(int chunkX,int chunkZ){return columnGeometryVersions.getOrDefault(new ChunkPos(chunkX,0,chunkZ),0L);}
    /** Owner-thread views: callers must fence revisions before resuming iterators. */
    public Collection<Protocol.Edit> editsInChunk(ChunkPos p){return Collections.unmodifiableCollection(edits.getOrDefault(p,Map.of()).values());}
    public Collection<EditedCell> editedCellsInChunk(ChunkPos p){return Collections.unmodifiableCollection(editedCells.getOrDefault(p,Map.of()).values());}
    public long localRevision(int minX,int maxX,int minZ,int maxZ) {
        if(closed)return Long.MIN_VALUE;
        long stamp=0;
        for(int x=Math.floorDiv(minX,16);x<=Math.floorDiv(maxX,16);x++)
            for(int z=Math.floorDiv(minZ,16);z<=Math.floorDiv(maxZ,16);z++) {
                var p=new ChunkPos(x,0,z);stamp+=skyEditVersions.getOrDefault(p,0L);

            }
        return stamp;
    }
    private long skyStamp,sceneRevision;
    /** Membership and installed-mesh changes, owned by the frame thread. */
    public long sceneRevision(){return sceneRevision;}
    public long skyStamp(){return skyStamp;}
    private final Map<ChunkPos,Set<ChunkPos>> skyEditChunks=new HashMap<>();
    private final Map<ChunkPos,Long> skyEditVersions=new HashMap<>();
    private final java.util.concurrent.ConcurrentHashMap<ChunkPos,Boolean> skyPositions=new java.util.concurrent.ConcurrentHashMap<>();
    public Collection<ChunkPos> skyColumns(){return skyPositions.keySet();}
    public Collection<ChunkPos> skyMembers(ChunkPos p){return columnMembers.getOrDefault(p,Set.of());}
    public boolean skyColumnReady(ChunkPos p){return !streamingColumns.contains(p)||columnMembers.getOrDefault(p,Set.of()).size()==10;}
    public long skyEditRevision(ChunkPos p){return skyEditVersions.getOrDefault(p,0L);}
    public Iterator<Protocol.Edit> skyEdits(ChunkPos p){return skyEditChunks.getOrDefault(p,Set.of()).stream().flatMap(c->edits.get(c).values().stream()).iterator();}
    private final Terrain terrain;
    private TerrainScheduler terrainScheduler;
    private TerrainScheduler.Result preparing;
    private Iterator<Protocol.Edit> replay;
    private final Set<ChunkPos> streamingColumns = new HashSet<>();
    private boolean closed;
    public long streamAttachNanos, streamReplayNanos, streamDisposeNanos, streamRequestNanos, streamGenerationNanos;
    public int streamAttached, streamUnloaded;
    public <T> java.util.concurrent.Future<T> prepareTerrainTask(java.util.concurrent.Callable<T> task){
        if(closed)throw new IllegalStateException("World closed");
        if(terrainScheduler==null)terrainScheduler=new TerrainScheduler(terrain.seed,terrain.version);
        return terrainScheduler.prepare(task);
    }
    public int terrainPending() { return (terrainScheduler==null?0:terrainScheduler.pending())+(preparing==null?0:1); }
    public long terrainOldestMillis() { return Math.max(terrainScheduler==null?0:terrainScheduler.oldestMillis(),preparing==null?0:(System.nanoTime()-preparing.requestedNanos())/1_000_000); }

    private final WorldVoxels voxels;
    private long editsVersion;
    private final ModelLibrary models;

    public World() {
        this(Terrain.DEFAULT_SEED);
    }

    public World(long seed) {
        this(seed, new ModelLibrary());
    }

    public World(long seed, ModelLibrary models) {
        this(seed, models, Terrain.CURRENT_VERSION);
    }

    public World(long seed, ModelLibrary models, int generatorVersion) {
        this.models = models;
        terrain = new Terrain(seed, generatorVersion);
        voxels = new WorldVoxels(terrain);
    }

    public long editsVersion() {
        return editsVersion;
    }

    public Map<String, Protocol.Edit> editsSnapshot() {
        Map<String, Protocol.Edit> snapshot = new LinkedHashMap<>();
        edits.values().forEach(snapshot::putAll);
        return Collections.unmodifiableMap(snapshot);
    }

    /** Full columns take over from distant terrain only once all nonempty chunks have a mesh. */
    public Set<ChunkPos> renderedColumns() {
        updateColumns();return new HashSet<>(completeColumns);
    }
    /** Cached rendering-only mask. Rebuild only at membership/mesh or distance-cell boundaries. */
    public Set<ChunkPos> renderedColumns(float x,float z,int range){
        updateColumns();int minX=(int)Math.ceil((x-range-8)/16),maxX=(int)Math.floor((x+range-8)/16),minZ=(int)Math.ceil((z-range-8)/16),maxZ=(int)Math.floor((z+range-8)/16);
        if(visualGeneration!=columnGeneration||minX!=visualMinX||maxX!=visualMaxX||minZ!=visualMinZ||maxZ!=visualMaxZ){var selected=new HashSet<ChunkPos>();for(var p:completeColumns)if(p.chunkX()>=minX&&p.chunkX()<=maxX&&p.chunkZ()>=minZ&&p.chunkZ()<=maxZ)selected.add(p);visualColumns=Collections.unmodifiableSet(selected);visualGeneration=columnGeneration;visualMinX=minX;visualMaxX=maxX;visualMinZ=minZ;visualMaxZ=maxZ;}
        return visualColumns;
    }

    public ModelLibrary models() {
        return models;
    }

    public Terrain terrain() {
        return terrain;
    }

    public Map<ChunkPos, Chunk> getLoadedChunks() {
        return Collections.unmodifiableMap(loadedChunks);
    }

    public void addChunk(ChunkPos p, Chunk c) {
        attachChunk(p,c,true);
    }

    /** Canonical edits already have a spatial revision; do not mislabel their
     * temporary chunk mutation as extra resident-only geometry. */
    private void applyCanonical(Chunk chunk,Protocol.Edit edit){
        boolean equivalent=chunk.terrainEquivalent,prior=canonicalMutation;canonicalMutation=true;
        try{chunk.apply(edit);}finally{chunk.terrainEquivalent=equivalent;canonicalMutation=prior;}
    }
    private void attachChunk(ChunkPos p,Chunk c,boolean replayEdits) {
        if(c.terrainEquivalent&&!c.matchesTerrain(terrain,p))c.terrainEquivalent=false;
        if (loadedChunks.putIfAbsent(p, c) != null)
            throw new IllegalStateException("Already loaded");
        sceneRevision++;skyPositions.put(column(p),true);
        c.attach(this, p);columnMembers.computeIfAbsent(column(p),k->new HashSet<>()).add(p);meshDirty(p,c);
        if(replayEdits)for (var e : edits.getOrDefault(p, Map.of()).values()) applyCanonical(c,e);
        dirtyNeighbors(p);
    }

    private void dirtyNeighbors(ChunkPos p) {
        for (Face f : Face.values()) {
            Chunk c =
                    loadedChunks.get(
                            new ChunkPos(
                                    p.chunkX() + f.dx(), p.chunkY() + f.dy(), p.chunkZ() + f.dz()));
            if (c != null) c.markDirty();
        }
    }

    public boolean isLoaded(int x, int y, int z) {
        return loadedChunks.containsKey(ChunkPos.fromBlock(x, y, z));
    }

    public int getBlock(int x, int y, int z) {
        Chunk c = loadedChunks.get(ChunkPos.fromBlock(x, y, z));
        if (c == null) throw new IllegalStateException("Chunk not loaded");
        return c.getBlock(Math.floorMod(x, 16), Math.floorMod(y, 16), Math.floorMod(z, 16));
    }

    public int sample(int x, int y, int z) {
        ChunkPos p = ChunkPos.fromBlock(x, y, z);
        Chunk c = loadedChunks.get(p);
        if (c != null)
            return c.getBlock(Math.floorMod(x, 16), Math.floorMod(y, 16), Math.floorMod(z, 16));
        return voxels.type(x, y, z);
    }

    public int value(int x, int y, int z) {
        var c =
                loadedChunks.get(
                        new ChunkPos(
                                Math.floorDiv(x, 256),
                                Math.floorDiv(y, 256),
                                Math.floorDiv(z, 256)));
        return c == null
                ? voxels.cell(Math.floorDiv(x, 16), Math.floorDiv(y, 16), Math.floorDiv(z, 16))
                        .get(Math.floorMod(x, 16), Math.floorMod(y, 16), Math.floorMod(z, 16))
                : c.value(Math.floorMod(x, 256), Math.floorMod(y, 256), Math.floorMod(z, 256));
    }

    public int regionValue(Protocol.Edit e) {
        int side = 16 >> e.depth();
        return cell(e.x(), e.y(), e.z()).uniform(e.ix() * side, e.iy() * side, e.iz() * side, side);
    }

    public int material(int x, int y, int z) {
        var p = new ChunkPos(Math.floorDiv(x, 256), Math.floorDiv(y, 256), Math.floorDiv(z, 256));
        var c = loadedChunks.get(p);
        if (c != null)
            return c.material(Math.floorMod(x, 256), Math.floorMod(y, 256), Math.floorMod(z, 256));
        return WorldVoxels.decode(
                voxels.cell(Math.floorDiv(x, 16), Math.floorDiv(y, 16), Math.floorDiv(z, 16))
                        .get(Math.floorMod(x, 16), Math.floorMod(y, 16), Math.floorMod(z, 16)));
    }

    public dev.jayms.net.model.SparseVoxelOctree cell(int x, int y, int z) {
        var c = loadedChunks.get(ChunkPos.fromBlock(x, y, z));
        return c == null
                ? voxels.cell(x, y, z)
                : c.cell(Math.floorMod(x, 16), Math.floorMod(y, 16), Math.floorMod(z, 16));
    }

    public int region(Protocol.Edit e) {
        int side = 16 >> e.depth();
        int type =
                WorldVoxels.decode(
                        cell(e.x(), e.y(), e.z())
                                .uniform(e.ix() * side, e.iy() * side, e.iz() * side, side));
        if (type == 0 || type == Blocks.PARTIAL) return type;
        return !Blocks.subdividable(type) && e.depth() > 0
                ? Blocks.PARTIAL
                : Blocks.piece(type, e.depth());
    }

    public void setBlock(int x, int y, int z, int type) {
        apply(new Protocol.Edit(x, y, z, type));
    }

    public void apply(Protocol.Edit e) {
        var p = ChunkPos.fromBlock(e.x(), e.y(), e.z());
        editsVersion++;skyStamp++;
        ChunkPos skyColumn=column(p);skyPositions.put(skyColumn,true);if(e.y()>Terrain.MAX_Y)editedCeilings.add(skyColumn);
        skyEditChunks.computeIfAbsent(skyColumn,k->new HashSet<>()).add(p);skyEditVersions.put(skyColumn,editsVersion);
        // A detached replay may span frames. Restart from current authoritative history if
        // edits changed; newer covering edits erase any older replayed regions.
        if(preparing!=null&&preparing.position().equals(p))replay=null;
        voxels.apply(e);
        editedCells.computeIfAbsent(p,k->new LinkedHashMap<>()).put(e.cellKey(),
                new EditedCell(e.x(),e.y(),e.z(),voxels.cell(e.x(),e.y(),e.z()).copy().freeze()));
        WorldVoxels.remember(edits.computeIfAbsent(p, k -> new WorldVoxels.History()), e);
        var c = loadedChunks.get(p);
        if (c != null)applyCanonical(c,e);
        dirtyNeighbors(p);
    }

    /** Remove resident geometry while retaining authoritative edits for reload. */
    public void unloadChunk(ChunkPos position)throws Exception {
        if(terrainScheduler!=null)terrainScheduler.cancel(position);
        if(preparing!=null&&preparing.position().equals(position)){preparing=null;replay=null;}
        var chunk=loadedChunks.get(position);
        if(chunk!=null){skyStamp++;sceneRevision++;chunk.close();loadedChunks.remove(position);dirtyMeshes.remove(position);ChunkPos column=column(position);var members=columnMembers.get(column);members.remove(position);if(!chunk.terrainEquivalent)columnGeometryVersions.put(column,++geometryEpoch);if(members.isEmpty()){columnGeometryVersions.remove(column);columnMembers.remove(column);if(!skyEditChunks.containsKey(column))skyPositions.remove(column);}dirtyColumns.add(column);dirtyNeighbors(position);}
    }

    /** Synchronous bootstrap/CLI compatibility. Gameplay uses streamAsync; never call this from a frame. */
    public void stream(float x, float z, int budget) throws Exception {
        if(closed)throw new IllegalStateException("World closed");
        int cx = Math.floorDiv((int) Math.floor(x), 16),
                cz = Math.floorDiv((int) Math.floor(z), 16);
        var distant=new ArrayList<ChunkPos>();
        for(var position:loadedChunks.keySet())
            if(Math.abs(position.chunkX()-cx)>6||Math.abs(position.chunkZ()-cz)>6)distant.add(position);
        for(var position:distant)unloadChunk(position);
        int generated = 0;
        for (int ring = 0; ring <= 4; ring++)
            for (int a = -ring; a <= ring; a++)
                for (int b = -ring; b <= ring; b++) {
                    if (Math.max(Math.abs(a), Math.abs(b)) != ring) continue;
                    var members=columnMembers.get(new ChunkPos(cx+a,0,cz+b));
                    if (members!=null&&members.size()==10) continue;
                    for (int y = -2; y <= 7; y++) {
                        ChunkPos p = new ChunkPos(cx + a, y, cz + b);
                        if(!loadedChunks.containsKey(p))addChunk(p, ChunkGenerator.generate(p, terrain));
                    }
                    if (++generated >= budget) return;
                }
    }

    /** Readiness checks use membership only, never synchronous fallback terrain sampling. */
    public boolean nearbyReady(float x,float z,int radius) {
        int cx=Math.floorDiv((int)Math.floor(x),16),cz=Math.floorDiv((int)Math.floor(z),16);
        for(int a=-radius;a<=radius;a++)for(int b=-radius;b<=radius;b++) {
            var members=columnMembers.get(new ChunkPos(cx+a,0,cz+b));
            if(members==null||members.size()!=10)return false;
        }
        return true;
    }

    /** Per-chunk preparation, incremental edit replay and deadline-limited owner work.
     * Missing collision cells remain solid via VoxelQueries; distant coverage stays until all
     * ten vertical chunks are resident and meshed. No worker can reach this World instance.
     */
    public void streamAsync(float x,float y,float z,long budgetNanos) throws Exception {
        if(closed)throw new IllegalStateException("World closed");
        streamAttachNanos=streamReplayNanos=streamDisposeNanos=streamRequestNanos=streamGenerationNanos=0;
        streamAttached=streamUnloaded=0;
        long deadline=System.nanoTime()+Math.max(0,budgetNanos);
        int cx=Math.floorDiv((int)Math.floor(x),16),cz=Math.floorDiv((int)Math.floor(z),16);
        int cy=Math.max(-2,Math.min(7,Math.floorDiv((int)Math.floor(y),16)));
        if(terrainScheduler==null)terrainScheduler=new TerrainScheduler(terrain.seed,terrain.version);
        terrainScheduler.discardOutside(cx,cz,4);
        if(preparing!=null&&(Math.abs(preparing.position().chunkX()-cx)>4||Math.abs(preparing.position().chunkZ()-cz)>4)){preparing=null;replay=null;}
        long stage=System.nanoTime();
        requestNear(cx,cy,cz,Math.min(deadline,stage+Math.max(1,budgetNanos/4)));
        streamRequestNanos+=System.nanoTime()-stage;
        while(streamAttached<TerrainScheduler.CAPACITY&&System.nanoTime()<deadline) {
            if(preparing==null){preparing=terrainScheduler.poll();replay=null;if(preparing==null)break;streamGenerationNanos+=preparing.generationNanos();}
            ChunkPos p=preparing.position();
            if(loadedChunks.containsKey(p)){preparing=null;replay=null;continue;}
            if(replay==null)replay=edits.getOrDefault(p,Map.of()).values().iterator();
            stage=System.nanoTime();
            while(replay.hasNext()&&System.nanoTime()<deadline)preparing.chunk().apply(replay.next());
            streamReplayNanos+=System.nanoTime()-stage;
            if(replay.hasNext()||System.nanoTime()>=deadline)break;
            if(loadedChunks.size()>=1690)break;
            preparing.chunk().terrainEquivalent=true;
            stage=System.nanoTime();attachChunk(p,preparing.chunk(),false);streamAttachNanos+=System.nanoTime()-stage;
            streamAttached++;preparing=null;replay=null;
        }
        // Playable terrain gets ready work before any driver-facing disposal.
        // Reserve a slice for cleanup, so neither stage can monopolize every frame.
        long disposalDeadline=Math.min(deadline,System.nanoTime()+Math.max(1,budgetNanos/4));
        stage=System.nanoTime();
        while(streamUnloaded<TerrainScheduler.CAPACITY&&System.nanoTime()<disposalDeadline){
            ChunkPos remove=null;for(var p:loadedChunks.keySet())if(Math.abs(p.chunkX()-cx)>6||Math.abs(p.chunkZ()-cz)>6){remove=p;break;}
            if(remove==null)break;unloadChunk(remove);streamUnloaded++;
        }
        streamDisposeNanos=System.nanoTime()-stage;
        streamingColumns.removeIf(p->!columnMembers.containsKey(p)&&(Math.abs(p.chunkX()-cx)>4||Math.abs(p.chunkZ()-cz)>4));
        stage=System.nanoTime();requestNear(cx,cy,cz,deadline);streamRequestNanos+=System.nanoTime()-stage;
    }
    private void requestNear(int cx,int cy,int cz,long deadline){
        // Deterministic rings plus bounded FIFO retain near priority and accepted-job fairness.
        outer:for(int ring=0;ring<=4;ring++)for(int a=-ring;a<=ring;a++)for(int b=-ring;b<=ring;b++) {
            if(Math.max(Math.abs(a),Math.abs(b))!=ring)continue;
            // A complete column cannot contain a missing request. Avoid rescanning
            // all ten heights twice per frame in the resident world.
            var members=columnMembers.get(new ChunkPos(cx+a,0,cz+b));
            if(members!=null&&members.size()==10)continue;
            for(int d=0;d<=9;d++)for(int sign=d==0?0:-1;sign<=1;sign+=2) {
                if(System.nanoTime()>=deadline||terrainScheduler.pending()>=TerrainScheduler.CAPACITY)break outer;
                int yy=cy+d*sign;if(yy< -2||yy>7)continue;
                ChunkPos p=new ChunkPos(cx+a,yy,cz+b);
                if(!loadedChunks.containsKey(p)&&(preparing==null||!preparing.position().equals(p))&&terrainScheduler.request(p,terrain.stressGrid()))streamingColumns.add(column(p));
            }
        }
    }

    @Override
    public void close() throws Exception {
        closed=true;skyStamp++;
        if(terrainScheduler!=null)terrainScheduler.close();
        preparing=null;replay=null;streamingColumns.clear();skyEditChunks.clear();skyEditVersions.clear();skyPositions.clear();
        for (Chunk c : loadedChunks.values()) c.close();
        edits.clear();editedCells.clear();editedCeilings.clear();columnGeometryVersions.clear();voxels.clear();terrain.clearCaches();
        loadedChunks.clear();dirtyMeshes.clear();columnMembers.clear();dirtyColumns.clear();completeColumns.clear();visualColumns=Set.of();columnGeneration++;
    }
}
