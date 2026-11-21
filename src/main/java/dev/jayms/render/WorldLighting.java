package dev.jayms.render;

import dev.jayms.*;
import dev.jayms.net.*;
import dev.jayms.net.model.*;

import java.util.*;
import java.util.concurrent.*;

/**
 * One worker, coalesced edits, immutable octree snapshots; no GL or shared terrain cache on worker.
 */
public final class WorldLighting implements AutoCloseable {
    private final ExecutorService worker =
            Executors.newSingleThreadExecutor(
                    r -> {
                        Thread t = new Thread(r, "voxel-lighting");
                        t.setDaemon(true);
                        return t;
                    });
    private Future<LightVolume> pending;
    public long submitted,completed,rejected,captureRestarts;
    public record WorkerTiming(long id,long wallNanos,long cpuNanos) {}
    private final java.util.concurrent.atomic.AtomicReference<WorkerTiming> finishedWorkerTiming=new java.util.concurrent.atomic.AtomicReference<>(new WorkerTiming(0,0,0));
    public boolean bakeDone(){return pending!=null&&pending.isDone();}
    public WorkerTiming workerTiming(){return finishedWorkerTiming.get();}
    public long pendingAgeNanos(){return submittedCapture==null?0:System.nanoTime()-submittedCapture.created;}

    private int centerX = Integer.MIN_VALUE, centerZ;
    private long revision = -1, submittedRevision;
    private int submittedX, submittedZ;
    private float ambient = -1, submittedAmbient;
    private boolean separateSky,submittedSeparateSky;
    private WorldSkyVisibility sky,submittedSky;
    private Object skyKey,submittedSkyKey;
    private World acceptedWorld,submittedWorld;

    private Capture capture,submittedCapture,acceptedCapture;
    private long acceptedModels=-1,submittedModels;
    private int originY=-32;
    /** Resident focus only; explicit replay still requires its fixed joint coverage. */
    public void focusHeight(float y){originY=heightOrigin(originY,y);}
    int originY(){return originY;}
    static int heightOrigin(int previous,float y){
        if(!Float.isFinite(y)||Math.abs((double)y)>Integer.MAX_VALUE-256L)throw new IllegalArgumentException("Lighting focus height");
        if(y>=previous+16L&&y<previous+112L)return previous;
        return Math.toIntExact((long)Math.floor((double)y/16)*16-64);
    }
    static boolean heightCovered(LightVolume volume,float y){return volume!=null&&Float.isFinite(y)&&y>=volume.y+16L&&y<volume.y+112L;}

    static final class Capture {
        final long created=System.nanoTime();
        final World world;final int x,y,z;final long stamp,modelsRevision;
        final ModelLibrary models;final Object terrainGrid;
        final long[] geometry=new long[36];final boolean[] ceilings=new boolean[36];
        final Map<ChunkPos,SparseVoxelOctree> replayChunks;
        final World.ReplayLightingSnapshot replayCoverage;
        SparseVoxelOctree[] chunks=new SparseVoxelOctree[288];
        SparseVoxelOctree[][] overrides=new SparseVoxelOctree[288][];
        int capturedCells,editing;
        int index;Iterator<World.EditedCell> cells=Collections.emptyIterator();
        Capture(World world,int x,int z) {this(world,x,z,null);}
        Capture(World world,int x,int z,World.ReplayLightingSnapshot replayCoverage) {this(world,x,z,-32,replayCoverage);}
        Capture(World world,int x,int z,int y,World.ReplayLightingSnapshot replayCoverage) {
            if(Math.floorMod(y,16)!=0||y>Integer.MAX_VALUE-128||replayCoverage!=null&&y!=-32)throw new IllegalArgumentException("Lighting capture height");
            this.y=y;this.replayCoverage=replayCoverage;
            this.world=world;this.x=x;this.z=z;terrainGrid=world.terrain().stressGrid();replayChunks=replayCoverage==null?world.replayLightingChunks():replayCoverage.roots();
            stamp=stamp(world,x,z);modelsRevision=world.models().revision();models=world.models().copy();
            for(int i=0;i<36;i++){int a=Math.floorDiv(x-48,16)+i%6,b=Math.floorDiv(z-48,16)+i/6;geometry[i]=world.geometryRevision(a,b);ceilings[i]=replayCoverage!=null||world.requiresSkyCeiling(a,b);}
        }
        boolean matchesReplay(World.ReplayLightingSnapshot expected){return replayCoverage==null?expected==null:expected!=null&&expected.geometryKey()==replayCoverage.geometryKey()&&expected.skyKey()==replayCoverage.skyKey();}
        static long stamp(World w,int x,int z){return w.localRevision(x-48,x+47,z-48,z+47);}
        boolean valid(World w,int x,int z){
            if(world!=w||w.isClosed()||terrainGrid!=w.terrain().stressGrid()||this.x!=x||this.z!=z||modelsRevision!=w.models().revision()||stamp!=stamp(w,x,z))return false;
            if(replayCoverage!=null&&!replayCoverage.current(w))return false;
            if(!Objects.equals(replayChunks,w.replayLightingChunks()))return false;
            for(int i=0;i<36;i++)if(geometry[i]!=w.geometryRevision(Math.floorDiv(x-48,16)+i%6,Math.floorDiv(z-48,16)+i/6))return false;
            return true;
        }
        int slot(int px,int py,int pz){return (px-x+48)/16+6*((py-y)/16+8*((pz-z+48)/16));}
        SparseVoxelOctree overrideAt(int px,int py,int pz){
            if(px<x-48||px>x+47||py<y||(long)py>=y+128L||pz<z-48||pz>z+47)return null;
            var cells=overrides[slot(px,py,pz)];return cells==null?null:cells[Math.floorMod(px,16)+16*(Math.floorMod(py,16)+16*Math.floorMod(pz,16))];
        }
        void releasePayload(){chunks=null;overrides=null;}
        boolean pump(long deadline) {
        int trace=dev.jayms.OwnerTrace.begin("gi.capture");try{
            int steps=0;
            while(System.nanoTime()<deadline&&steps++<1024) {
                if(cells.hasNext()){
                    var c=cells.next();if(overrides[editing]==null)overrides[editing]=new SparseVoxelOctree[4096];
                    overrides[editing][Math.floorMod(c.x(),16)+16*(Math.floorMod(c.y(),16)+16*Math.floorMod(c.z(),16))]=c.voxels();capturedCells++;continue;
                }
                if(index==288)return true;
                int i=index,cx=Math.floorDiv(x-48,16)+i%6,cy=y/16+i/6%8,cz=Math.floorDiv(z-48,16)+i/48;
                var p=new ChunkPos(cx,cy,cz);
                if(replayChunks!=null) {
                    var known=replayChunks.get(p);if(known==null)return false;
                    chunks[i]=known;index++;continue;
                }
                index++;var chunk=world.getLoadedChunks().get(p);
                if(chunk!=null)chunks[i]=chunk.snapshot();else{editing=i;cells=world.editedCellsInChunk(p).iterator();}
            }
            return index==288&&!cells.hasNext();

        }finally{dev.jayms.OwnerTrace.end(trace,index);}
    }
    }

    static float skyVisibility(WorldSkyVisibility sky,boolean[] ceilings,int cx,int cz,float x,float y,float z){
        int a=Math.floorDiv((int)Math.floor(x),16)-Math.floorDiv(cx-48,16),b=Math.floorDiv((int)Math.floor(z),16)-Math.floorDiv(cz-48,16);
        return sky==null||a<0||a>=6||b<0||b>=6||!ceilings[a+6*b]?1:sky.sampleKnown(x,y,z,1);
    }
    Capture acceptedFence(){return acceptedCapture;}
    /** A renderer-discarded payload must be regenerated even if the player returns to the same focus. */
    public void invalidateAccepted(){acceptedCapture=null;acceptedWorld=null;revision=-1;}

    public LightVolume update(World world, float px, float pz) {
        return update(world, px, pz, 1);
    }

    public LightVolume update(World world, float px, float pz, float skyStrength) {return update(world,px,pz,skyStrength,false);}
    public LightVolume update(World world,float px,float pz,float skyStrength,boolean dynamicSky) {return update(world,px,pz,skyStrength,dynamicSky,null);}
    public LightVolume update(World world,float px,float pz,float skyStrength,boolean dynamicSky,WorldSkyVisibility globalSky) {return update(world,px,pz,skyStrength,dynamicSky,globalSky,globalSky);}
    public LightVolume update(World world,float px,float pz,float skyStrength,boolean dynamicSky,WorldSkyVisibility globalSky,Object globalSkyKey) {
        return update(world,px,pz,skyStrength,dynamicSky,globalSky,globalSkyKey,System.nanoTime()+FrameBudget.STREAMING_CPU_NANOS);
    }
    public LightVolume update(World world,float px,float pz,float skyStrength,boolean dynamicSky,WorldSkyVisibility globalSky,Object globalSkyKey,long deadline) {
        return update(world,px,pz,skyStrength,dynamicSky,globalSky,globalSkyKey,deadline,true);
    }
    public LightVolume update(World world,float px,float pz,float skyStrength,boolean dynamicSky,WorldSkyVisibility globalSky,Object globalSkyKey,long deadline,boolean allowPrepare) {
        return updateInternal(world,px,pz,px,pz,skyStrength,dynamicSky,globalSky,globalSkyKey,globalSkyKey,deadline,allowPrepare,false,true);
    }
    public LightVolume updatePredicted(World world,float px,float pz,float actualX,float actualZ,float skyStrength,boolean dynamicSky,SkyVisibilityScheduler.Prepared prepared,long deadline,boolean allowPrepare){
        return updatePredicted(world,px,pz,actualX,actualZ,skyStrength,dynamicSky,prepared,deadline,allowPrepare,true);
    }
    public LightVolume updatePredicted(World world,float px,float pz,float actualX,float actualZ,float skyStrength,boolean dynamicSky,SkyVisibilityScheduler.Prepared prepared,long deadline,boolean allowPrepare,boolean collect){
        if(capture!=null&&capture.y==originY&&capture.valid(world,capture.x,capture.z)&&Math.abs(actualX-capture.x)<=80&&Math.abs(actualZ-capture.z)<=80){px=capture.x;pz=capture.z;}
        Object key=SkyVisibilityScheduler.ceilingKey(prepared,world,px,pz);
        Object completionKey=pending==null?key:SkyVisibilityScheduler.ceilingKey(prepared,world,submittedX,submittedZ);
        return updateInternal(world,px,pz,actualX,actualZ,skyStrength,dynamicSky,prepared==null?null:prepared.data,key,completionKey,deadline,allowPrepare,true,collect);
    }
    /** Explicit private CPU bake lane. Complete source-fenced geometry and sky
     * are required; ordinary update and renderer admission remain unchanged. */
    public LightVolume updateReplay(World world,int x,int z,float skyStrength,boolean dynamicSky,long deadline) {return updateReplay(world,x,z,skyStrength,dynamicSky,deadline,true);}
    public LightVolume updateReplay(World world,int x,int z,float skyStrength,boolean dynamicSky,long deadline,boolean allowPrepare) {
        Objects.requireNonNull(world);
        if(world.isClosed()||System.nanoTime()>=deadline)return null;
        if(!world.hasReplayReadView())throw new IllegalArgumentException("Replay lighting requires private view");
        var replay=world.replayLightingSnapshot(x,z);
        var sky=replay==null?null:replay.sky();var key=replay==null?null:replay.skyKey();
        return updateInternal(world,x,z,x,z,skyStrength,dynamicSky,sky,key,key,deadline,allowPrepare&&replay!=null,false,true,replay);
    }
    private LightVolume updateInternal(World world,float px,float pz,float actualX,float actualZ,float skyStrength,boolean dynamicSky,WorldSkyVisibility globalSky,Object globalSkyKey,Object completionKey,long deadline,boolean allowPrepare,boolean relevant,boolean collect){
        return updateInternal(world,px,pz,actualX,actualZ,skyStrength,dynamicSky,globalSky,globalSkyKey,completionKey,deadline,allowPrepare,relevant,collect,null);
    }
    private LightVolume updateInternal(World world,float px,float pz,float actualX,float actualZ,float skyStrength,boolean dynamicSky,WorldSkyVisibility globalSky,Object globalSkyKey,Object completionKey,long deadline,boolean allowPrepare,boolean relevant,boolean collect,World.ReplayLightingSnapshot replay){
        int trace=dev.jayms.OwnerTrace.begin("gi.update");try{
        if(world.isClosed()||System.nanoTime()>=deadline)return null;
        int cx = Math.floorDiv((int) Math.floor(px), 16) * 16,
                cz = Math.floorDiv((int) Math.floor(pz), 16) * 16;
        int cy=world.hasReplayReadView()?-32:originY;
        LightVolume result = null;
        if (collect && pending != null && pending.isDone()) {
            try {
                var baked = pending.get();
                boolean focusMatches=relevant?Math.abs(actualX-submittedX)<=80&&Math.abs(actualZ-submittedZ)<=80:submittedX==cx&&submittedZ==cz;
                if (submittedCapture.y==cy&&submittedCapture.valid(world,submittedX,submittedZ)&&submittedModels==world.models().revision()&&submittedWorld==world&&submittedRevision == Capture.stamp(world,submittedX,submittedZ)
                        && focusMatches
                        && submittedAmbient == skyStrength && submittedSeparateSky==dynamicSky && Objects.equals(submittedSkyKey,completionKey)) {
                    completed++;result = baked;
                    revision = submittedRevision;acceptedModels=submittedModels;acceptedCapture=submittedCapture;
                    ambient = submittedAmbient;
                    separateSky=submittedSeparateSky;sky=submittedSky;skyKey=submittedSkyKey;acceptedWorld=world;
                    centerX = submittedX;
                    centerZ = submittedZ;
                }else rejected++;
            } catch (Exception e) {
                throw new IllegalStateException("Voxel lighting failed", e);
            } finally {
                pending = null;submittedCapture.releasePayload();submittedCapture=null;
            }
        }
        // Let the caller occupy the GPU slot before considering another capture.
        if (result == null && allowPrepare && pending == null
                && (acceptedCapture==null||acceptedCapture.y!=cy||!acceptedCapture.valid(world,cx,cz)||acceptedModels!=world.models().revision()||acceptedWorld!=world||cx != centerX
                        || cz != centerZ
                        || revision != Capture.stamp(world,cx,cz)
                        || ambient != skyStrength || separateSky!=dynamicSky || !Objects.equals(skyKey,globalSkyKey))) {
            if(capture==null||capture.y!=cy||!capture.valid(world,cx,cz)||!capture.matchesReplay(replay)){if(capture!=null)captureRestarts++;capture=new Capture(world,cx,cz,cy,replay);}
            if(!capture.pump(deadline))return result;
            if(!capture.valid(world,cx,cz)){capture=null;return result;}
            // Ordinary replay updates remain closed. Only the explicit lane has
            // a current joint geometry/sky receipt, retained through collection.
            if(capture.replayChunks!=null&&capture.replayCoverage==null)return result;
            var prepared=capture;capture=null;submittedCapture=prepared;submitted++;
            submittedSky=globalSky;submittedSkyKey=globalSkyKey;submittedWorld=world;
            submittedAmbient = skyStrength;
            submittedSeparateSky=dynamicSky;
            submittedX = cx;
            submittedZ = cz;
            submittedRevision = prepared.stamp;submittedModels=prepared.modelsRevision;
            int baseY=prepared.y;var chunks=prepared.chunks;
            var overrides=prepared.overrides;var ceilings=prepared.ceilings;
            var timing=finishedWorkerTiming;long workerTicket=submitted;
            long seed = world.terrain().seed;
            int generatorVersion = world.terrain().version;
            var stressGrid = world.terrain().stressGrid();
            ModelLibrary models = prepared.models;
            var task =
                    new dev.jayms.PublishedFuture<LightVolume>(
                            () -> {
                                long started=System.nanoTime();var bean=java.lang.management.ManagementFactory.getThreadMXBean();long cpu=bean.isCurrentThreadCpuTimeSupported()?bean.getCurrentThreadCpuTime():-1;
                                var terrain = new Terrain(seed, generatorVersion);
                                terrain.stressGrid(stressGrid);
                                WorldVoxels fallback = new WorldVoxels(terrain);

                                var bakedField=LightVolume.bake(
                                        cx - 48,
                                        baseY,
                                        cz - 48,
                                        96,
                                        128,
                                        96,
                                        skyStrength,
                                        new LightVolume.Sampler() {
                                            public float skyVisibility(float x,float y,float z){return WorldLighting.skyVisibility(globalSky,ceilings,cx,cz,x,y,z);}
                                            private final Map<Integer, SparseVoxelOctree> geometry =
                                                    new HashMap<>();

                                            private SparseVoxelOctree cell(int x, int y, int z) {
                                                int slot=(x-cx+48)/16+6*((y-baseY)/16+8*((z-cz+48)/16));
                                                var octree=chunks[slot];var cells=overrides[slot];
                                                var override=cells==null?null:cells[Math.floorMod(x,16)+16*(Math.floorMod(y,16)+16*Math.floorMod(z,16))];
                                                return octree == null
                                                        ? override==null?fallback.cell(x,y,z):override
                                                        : octree.region(
                                                                Math.floorMod(x, 16) * 16,
                                                                Math.floorMod(y, 16) * 16,
                                                                Math.floorMod(z, 16) * 16,
                                                                16);
                                            }

                                            public int value(int x, int y, int z) {
                                                int slot=(x-cx+48)/16+6*((y-baseY)/16+8*((z-cz+48)/16));
                                                var root=chunks[slot];var cells=overrides[slot];var override=cells==null?null:cells[Math.floorMod(x,16)+16*(Math.floorMod(y,16)+16*Math.floorMod(z,16))];
                                                int value=root!=null?root.uniform(Math.floorMod(x,16)*16,Math.floorMod(y,16)*16,Math.floorMod(z,16)*16,16):override!=null?override.uniform(0,0,0,16):WorldVoxels.encode(terrain.block(x,y,z));
                                                return Blocks.isModel(WorldVoxels.decode(value))
                                                        ? -1
                                                        : value;
                                            }

                                            public SparseVoxelOctree detail(int x, int y, int z) {
                                                var tree = cell(x, y, z);
                                                int type =
                                                        WorldVoxels.decode(
                                                                tree.uniform(0, 0, 0, 16));
                                                if (!Blocks.isModel(type)) return tree;
                                                return geometry.computeIfAbsent(
                                                        type, id -> modelGeometry(models.get(id)));
                                            }
                                        },dynamicSky);
                                long endCpu=bean.isCurrentThreadCpuTimeSupported()?bean.getCurrentThreadCpuTime():-1;timing.set(new WorkerTiming(workerTicket,System.nanoTime()-started,cpu<0||endCpu<0?-1:endCpu-cpu));return bakedField;
                            });
            pending=task;worker.execute(task);
        }
        return result;

        }finally{dev.jayms.OwnerTrace.end(trace,0);}
    }

    /**
     * Model colours are opaque ARGB, not encoded block IDs. Preserve their actual 8/16/32 geometry.
     */
    public static SparseVoxelOctree modelGeometry(ModelLibrary.Entry entry) {
        var model = entry == null ? null : entry.definition().voxels();
        var tree = new SparseVoxelOctree(model == null ? 16 : model.size());
        if (model == null) tree.fill(0, 0, 0, 16, 16, 16, WorldVoxels.encode(Blocks.STONE));
        else
            for (var leaf : model.leaves())
                tree.fill(
                        leaf.x(),
                        leaf.y(),
                        leaf.z(),
                        leaf.x() + leaf.side(),
                        leaf.y() + leaf.side(),
                        leaf.z() + leaf.side(),
                        WorldVoxels.encode(Blocks.STONE));
        return tree;
    }

    @Override
    public void close() {
        if(pending!=null){pending.cancel(true);pending=null;}
        capture=submittedCapture=acceptedCapture=null;sky=null;submittedSky=null;skyKey=null;submittedSkyKey=null;acceptedWorld=null;submittedWorld=null;
        worker.shutdownNow();
        try{if(!worker.awaitTermination(3,TimeUnit.SECONDS))throw new IllegalStateException("Lighting worker did not stop");}
        catch(InterruptedException e){Thread.currentThread().interrupt();}
    }
}
