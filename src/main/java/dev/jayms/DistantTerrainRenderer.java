package dev.jayms;

import dev.jayms.net.*;

import org.joml.FrustumIntersection;
import org.joml.Matrix4f;

import java.util.*;
import java.util.concurrent.*;

/** CPU generation stays on one worker; GL uploads and disposal stay on the render thread. */
public final class DistantTerrainRenderer implements AutoCloseable {
    private final ThreadPoolExecutor worker =
            new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(8),
                    r -> {
                        Thread thread = new Thread(r, "distant-terrain");
                        thread.setDaemon(true);
                        return thread;
                    });
    private final Terrain terrain;
    private DistantTerrainMesher mesher;
    private final Map<DistantTerrainPlan.Tile, Mesh> meshes = new HashMap<>();
    private final Map<DistantTerrainPlan.Tile, Future<MeshData>> pending = new LinkedHashMap<>();
    private Map<String, Protocol.Edit> edits = Map.of();
    private long revision = -1;
    private DistantTerrainPlan plan;
    private Set<DistantTerrainPlan.Tile> wanted = Set.of();
    private int centerX = Integer.MIN_VALUE, centerZ = Integer.MIN_VALUE;
    private int rendered;
    private int radius=DistantTerrainPlan.RADIUS;private float errorPixels=12;
    private long meshBytes,meshGeneration;
    private final Set<DistantTerrainPlan.Tile> capacityRejected=new HashSet<>();
    private java.util.List<DistantTerrainPlan.Tile> selectedCache=java.util.List.of();private long selectedGeneration=-1;
    private Set<DistantTerrainPlan.Tile> selectedSet=Set.of();
    private final Map<DistantTerrainPlan.Tile,Float> fadingParents=new HashMap<>();
    private final List<DistantTerrainPlan.Tile> transitionDraws=new ArrayList<>();
    private final Matrix4f modelScratch=new Matrix4f();
    public void settings(int radius,float errorPixels){if(this.radius!=radius||this.errorPixels!=errorPixels){this.radius=radius;this.errorPixels=errorPixels;centerX=Integer.MIN_VALUE;}}
    public long estimatedMeshBytes(){return meshBytes;}public int queuedTiles(){return pending.size();}
    private int projectionPixels;
    private float screenProjection,elevation; private int screenHeight; private boolean orthographic;
    private final Map<DistantTerrainPlan.Tile,Float> transitionStart=new HashMap<>();
    public void screen(Matrix4f projection,int height,boolean iso,float cameraY) {
        // Quantize viewport scale to avoid rebuilding on tiny zoom changes.
        int scale=Math.round(Math.abs(projection.m11())*height/32f)*32;
        if(scale!=projectionPixels || iso!=orthographic || Math.abs(cameraY-elevation)>16) {
            projectionPixels=scale;screenHeight=height;screenProjection=scale/(float)Math.max(1,height);orthographic=iso;elevation=cameraY;centerX=Integer.MIN_VALUE;
        }
    }

    public DistantTerrainRenderer(long seed) {
        this(seed, Terrain.CURRENT_VERSION);
    }

    public DistantTerrainRenderer(long seed, int generatorVersion) {
        terrain = new Terrain(seed, generatorVersion);
    }

    public WorldBounds bounds() {
        return plan.bounds();
    }

    public int readyTiles() {
        return meshes.size();
    }

    public int totalTiles() {
        return wanted.size();
    }

    public int renderedTiles() {
        return rendered;
    }

    public void update(World world, float x, float z) {
        if (terrain.stressGrid() != world.terrain().stressGrid()) terrain.stressGrid(world.terrain().stressGrid());
        int px = Math.floorDiv((int) Math.floor(x), 16) * 16;
        int pz = Math.floorDiv((int) Math.floor(z), 16) * 16;
        if (plan == null || px != centerX || pz != centerZ) {
            centerX = px;
            centerZ = pz;
            plan = new DistantTerrainPlan(px,pz,screenProjection,screenHeight,orthographic,Math.max(0,elevation-24),radius,errorPixels);
            wanted = new HashSet<>(plan.tiles());selectedGeneration=-1;capacityRejected.clear();
            remove(t -> !wanted.contains(t));
        }
        if (revision != world.editsVersion()) {
            Map<String, Protocol.Edit> next = world.editsSnapshot();
            var changed =
                    next.values().stream().filter(e -> !e.equals(edits.get(e.key()))).toList();
            var removed=edits.values().stream().filter(e->!next.containsKey(e.key())).toList();
            remove(t -> t.step() == 1 && (changed.stream().anyMatch(e -> t.touches(e.x(), e.z()))||removed.stream().anyMatch(e->t.touches(e.x(),e.z()))));
            edits = next;
            mesher = new DistantTerrainMesher(terrain, edits.values());
            revision = world.editsVersion();capacityRejected.clear();
        }
        int uploads = 0;long deadline=System.nanoTime()+1_500_000L,uploadedBytes=0;
        var it = pending.entrySet().iterator();
        while (it.hasNext() && System.nanoTime()<deadline && uploadedBytes<2*1024*1024) {
            var entry = it.next();
            if (!entry.getValue().isDone()) continue;
            try {
                MeshData data=entry.getValue().get();long bytes=DetailedMeshScheduler.bytes(data);
                if(meshBytes+bytes<=128L*1024*1024&&meshes.size()<4096){meshes.put(entry.getKey(),new Mesh(data));meshBytes+=bytes;meshGeneration++;uploadedBytes+=bytes;
                transitionStart.put(entry.getKey(),(float)org.lwjgl.glfw.GLFW.glfwGetTime());} else capacityRejected.add(entry.getKey());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Terrain generation interrupted", e);
            } catch (ExecutionException e) {
                throw new IllegalStateException("Terrain generation failed", e.getCause());
            }
            it.remove();
            uploads++;
        }
        for (var tile : plan.tiles()) {
            if (pending.size() >= 8||meshes.size()>=4096||meshBytes>=128L*1024*1024) break;
            if (!meshes.containsKey(tile) && !pending.containsKey(tile) && !capacityRejected.contains(tile)) {
                var snapshot = mesher;
                worker.purge();
                pending.put(tile, worker.submit(() -> snapshot.build(tile)));
            }
        }
    }

    private void remove(java.util.function.Predicate<DistantTerrainPlan.Tile> obsolete) {
        meshes.entrySet()
                .removeIf(
                        e -> {
                            if (!obsolete.test(e.getKey())) return false;
                            meshBytes-=e.getValue().byteSize();e.getValue().close();transitionStart.remove(e.getKey());meshGeneration++;
                            return true;
                        });
        pending.entrySet()
                .removeIf(
                        e -> {
                            if (!obsolete.test(e.getKey())) return false;
                            e.getValue().cancel(false);
                            return true;
                        });
    }

    public void render(ShaderProgram shader, FrustumIntersection frustum, Set<ChunkPos> detailed) {
        int ox = Math.floorDiv(centerX, 16) - 7, oz = Math.floorDiv(centerZ, 16) - 7;
        int[] rows = new int[16];
        for (ChunkPos column : detailed) {
            int x = column.chunkX() - ox, z = column.chunkZ() - oz;
            if (x >= 0 && x < 16 && z >= 0 && z < 16) rows[z] |= 1 << x;
        }
        shader.setVector3("uTerrainMinimum", bounds().minX(), 0, bounds().minZ());
        shader.setVector3("uTerrainMaximum", bounds().maxX(), 0, bounds().maxZ());
        shader.setVector3("uDetailOrigin", ox, 0, oz);
        shader.setInts("uDetailRows", rows);
        shader.setInt("uDistantTerrain", 1);
        shader.setInt("uVertexColor", 1);
        shader.setInt("uInstanced", 0);
        rendered = 0;
        if(selectedGeneration!=meshGeneration){selectedCache=plan.select(meshes.keySet());selectedSet=new HashSet<>(selectedCache);selectedGeneration=meshGeneration;}
        java.util.Set<DistantTerrainPlan.Tile> selected=selectedSet;fadingParents.clear();
        float now=(float)org.lwjgl.glfw.GLFW.glfwGetTime();
        for(var tile:selected) {
            float age=now-transitionStart.getOrDefault(tile,now-1);
            if(age<.4f&&tile.size()<1024) {
                int size=tile.size()*2;var parent=new DistantTerrainPlan.Tile(Math.floorDiv(tile.x(),size)*size,Math.floorDiv(tile.z(),size)*size,size);
                if(meshes.containsKey(parent)&&!selected.contains(parent))fadingParents.merge(parent,age/.4f,Math::min);
            }
        }
        Iterable<DistantTerrainPlan.Tile> draws=selectedCache;if(!fadingParents.isEmpty()){transitionDraws.clear();transitionDraws.addAll(selectedCache);transitionDraws.addAll(fadingParents.keySet());draws=transitionDraws;}
        for (var tile : draws) {
            if (!frustum.testAab(
                    tile.x(),
                    Terrain.MIN_Y,
                    tile.z(),
                    tile.x() + tile.size(),
                    Terrain.MAX_Y + 1,
                    tile.z() + tile.size())) continue;
            shader.setMatrix4("uModel", modelScratch.translation(tile.x(), 0, tile.z()));
            Float fade=fadingParents.get(tile);
            int parentSize=tile.size()*2;var parent=new DistantTerrainPlan.Tile(Math.floorDiv(tile.x(),parentSize)*parentSize,Math.floorDiv(tile.z(),parentSize)*parentSize,parentSize);
            float childFade=fadingParents.getOrDefault(parent,1f);
            shader.setFloat("uLodFade",fade==null?childFade:fade);shader.setInt("uLodParent",fade==null?0:1);
            meshes.get(tile).render();
            rendered++;
        }
        shader.setInt("uDistantTerrain", 0);
        shader.setFloat("uLodFade",1);shader.setInt("uLodParent",0);
    }

    @Override
    public void close() {
        pending.values().forEach(f -> f.cancel(true));
        worker.shutdownNow();
        meshes.values().forEach(Mesh::close);
        meshes.clear();
        pending.clear();
    }
}
