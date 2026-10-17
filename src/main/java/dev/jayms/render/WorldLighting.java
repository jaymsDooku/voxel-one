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
    static final class Capture {
        final World world;final int x,z;final long stamp,modelsRevision;
        final ModelLibrary models;
        final long[] geometry=new long[36];
        SparseVoxelOctree[] chunks=new SparseVoxelOctree[288];
        SparseVoxelOctree[][] overrides=new SparseVoxelOctree[288][];
        int capturedCells,editing;
        int index;Iterator<World.EditedCell> cells=Collections.emptyIterator();
        Capture(World world,int x,int z) {
            this.world=world;this.x=x;this.z=z;
            stamp=stamp(world,x,z);modelsRevision=world.models().revision();models=world.models().copy();
            for(int i=0;i<36;i++)geometry[i]=world.geometryRevision(Math.floorDiv(x-48,16)+i%6,Math.floorDiv(z-48,16)+i/6);
        }
        static long stamp(World w,int x,int z){return w.localRevision(x-48,x+47,z-48,z+47);}
        boolean valid(World w,int x,int z){
            if(world!=w||this.x!=x||this.z!=z||modelsRevision!=w.models().revision()||stamp!=stamp(w,x,z))return false;
            for(int i=0;i<36;i++)if(geometry[i]!=w.geometryRevision(Math.floorDiv(x-48,16)+i%6,Math.floorDiv(z-48,16)+i/6))return false;
            return true;
        }
        int slot(int px,int py,int pz){return (px-x+48)/16+6*((py+32)/16+8*((pz-z+48)/16));}
        SparseVoxelOctree overrideAt(int px,int py,int pz){
            if(px<x-48||px>x+47||py< -32||py>95||pz<z-48||pz>z+47)return null;
            var cells=overrides[slot(px,py,pz)];return cells==null?null:cells[Math.floorMod(px,16)+16*(Math.floorMod(py,16)+16*Math.floorMod(pz,16))];
        }
        void releasePayload(){chunks=null;overrides=null;}
        boolean pump(long deadline) {
            int steps=0;
            while(System.nanoTime()<deadline&&steps++<1024) {
                if(cells.hasNext()){
                    var c=cells.next();if(overrides[editing]==null)overrides[editing]=new SparseVoxelOctree[4096];
                    overrides[editing][Math.floorMod(c.x(),16)+16*(Math.floorMod(c.y(),16)+16*Math.floorMod(c.z(),16))]=c.voxels();capturedCells++;continue;
                }
                if(index==288)return true;
                int i=index++,cx=Math.floorDiv(x-48,16)+i%6,cy=-2+i/6%8,cz=Math.floorDiv(z-48,16)+i/48;
                var p=new ChunkPos(cx,cy,cz);var chunk=world.getLoadedChunks().get(p);
                if(chunk!=null)chunks[i]=chunk.snapshot();else{editing=i;cells=world.editedCellsInChunk(p).iterator();}
            }
            return index==288&&!cells.hasNext();
        }
    }

    Capture acceptedFence(){return acceptedCapture;}

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
        if(System.nanoTime()>=deadline)return null;
        int cx = Math.floorDiv((int) Math.floor(px), 16) * 16,
                cz = Math.floorDiv((int) Math.floor(pz), 16) * 16;
        LightVolume result = null;
        if (pending != null && pending.isDone()) {
            try {
                var baked = pending.get();
                if (submittedCapture.valid(world,cx,cz)&&submittedModels==world.models().revision()&&submittedWorld==world&&submittedRevision == Capture.stamp(world,cx,cz)
                        && submittedX == cx
                        && submittedZ == cz
                        && submittedAmbient == skyStrength && submittedSeparateSky==dynamicSky && Objects.equals(submittedSkyKey,globalSkyKey)) {
                    result = baked;
                    revision = submittedRevision;acceptedModels=submittedModels;acceptedCapture=submittedCapture;
                    ambient = submittedAmbient;
                    separateSky=submittedSeparateSky;sky=submittedSky;skyKey=submittedSkyKey;acceptedWorld=world;
                    centerX = cx;
                    centerZ = cz;
                }
            } catch (Exception e) {
                throw new IllegalStateException("Voxel lighting failed", e);
            } finally {
                pending = null;submittedCapture.releasePayload();submittedCapture=null;
            }
        }
        if (pending == null
                && (acceptedCapture==null||!acceptedCapture.valid(world,cx,cz)||acceptedModels!=world.models().revision()||acceptedWorld!=world||cx != centerX
                        || cz != centerZ
                        || revision != Capture.stamp(world,cx,cz)
                        || ambient != skyStrength || separateSky!=dynamicSky || !Objects.equals(skyKey,globalSkyKey))) {
            if(capture==null||!capture.valid(world,cx,cz))capture=new Capture(world,cx,cz);
            if(!capture.pump(deadline))return result;
            if(!capture.valid(world,cx,cz)){capture=null;return result;}
            var prepared=capture;capture=null;submittedCapture=prepared;
            submittedSky=globalSky;submittedSkyKey=globalSkyKey;submittedWorld=world;
            submittedAmbient = skyStrength;
            submittedSeparateSky=dynamicSky;
            submittedX = cx;
            submittedZ = cz;
            submittedRevision = prepared.stamp;submittedModels=prepared.modelsRevision;
            var chunks=prepared.chunks;
            var overrides=prepared.overrides;
            long seed = world.terrain().seed;
            int generatorVersion = world.terrain().version;
            var stressGrid = world.terrain().stressGrid();
            ModelLibrary models = prepared.models;
            pending =
                    worker.submit(
                            () -> {
                                var terrain = new Terrain(seed, generatorVersion);
                                terrain.stressGrid(stressGrid);
                                WorldVoxels fallback = new WorldVoxels(terrain);

                                return LightVolume.bake(
                                        cx - 48,
                                        -32,
                                        cz - 48,
                                        96,
                                        128,
                                        96,
                                        skyStrength,
                                        new LightVolume.Sampler() {
                                            public float skyVisibility(float x,float y,float z){return globalSky==null?1:globalSky.sampleKnown(x,y,z,1);}
                                            private final Map<Integer, SparseVoxelOctree> geometry =
                                                    new HashMap<>();

                                            private SparseVoxelOctree cell(int x, int y, int z) {
                                                int slot=(x-cx+48)/16+6*((y+32)/16+8*((z-cz+48)/16));
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
                                                int value = cell(x, y, z).uniform(0, 0, 0, 16);
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
                            });
        }
        return result;
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
