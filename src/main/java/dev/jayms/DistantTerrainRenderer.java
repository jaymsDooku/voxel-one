package dev.jayms;

import dev.jayms.net.*;
import dev.jayms.net.city.StressGrid;
import dev.jayms.net.city.RoadMarkings;
import dev.jayms.render.MaterialTextures;

import org.joml.FrustumIntersection;
import org.joml.Matrix4f;

import java.util.*;
import java.util.concurrent.*;
import static org.lwjgl.opengl.GL33.*;

/** CPU generation stays on one worker; GL uploads and disposal stay on the render thread. */
public final class DistantTerrainRenderer implements AutoCloseable {
    private final ThreadPoolExecutor worker =
            new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(8),
                    r -> {
                        Thread thread = new Thread(r, "distant-terrain");
                        thread.setDaemon(true);
                        return thread;
                    });
    private Terrain terrain;
    private boolean gridPaint;
    // Match the mean texels of the detailed grass and asphalt material layers.
    // Coverage must mix these colors in linear light at the horizon.
    private static final float[] GRID_GRASS=linearAlbedo(Blocks.color(Blocks.GRASS),MaterialTextures.meanBrightness(MaterialTextures.layer(Blocks.GRASS)));
    private static final float[] GRID_ASPHALT=linearAlbedo(Blocks.color(Blocks.ASPHALT),MaterialTextures.meanBrightness(MaterialTextures.layer(Blocks.ASPHALT)));
    private static final float[] GRID_PAINT=linearAlbedo(new float[]{.95f,.95f,.9f},1);
    private static float[] linearAlbedo(float[] rgb,float brightness){
        for(int i=0;i<rgb.length;i++) rgb[i]=(float)Math.pow(rgb[i]*brightness,2.2);
        return rgb;
    }
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
    private final DistantTerrainTransitions transitions=new DistantTerrainTransitions();
    private final List<DistantTerrainPlan.Tile> transitionDraws=new ArrayList<>();
    private final Matrix4f modelScratch=new Matrix4f();
    private int detailTexture;
    private Set<ChunkPos> maskColumns;
    private WorldBounds maskBounds;
    private ColumnMask columnMask;

    /** One bit per column within the far pass, including the inward bias on its boundary skirts. */
    record ColumnMask(int originX, int originZ, int wordsPerRow, int rows, int[] words) {
        static ColumnMask build(WorldBounds bounds, Set<ChunkPos> detailed) {
            int originX=(int)Math.floor(bounds.minX()/16)-1;
            int originZ=(int)Math.floor(bounds.minZ()/16)-1;
            int columns=(int)Math.floor(bounds.maxX()/16)-originX+1;
            int rows=(int)Math.floor(bounds.maxZ()/16)-originZ+1;
            int stride=(columns+31)/32;
            int[] words=new int[stride*rows];
            for (ChunkPos column : detailed) {
                int x=column.chunkX()-originX, z=column.chunkZ()-originZ;
                if (x>=0 && x<columns && z>=0 && z<rows)
                    words[z*stride+(x>>>5)] |= 1<<(x&31);
            }
            return new ColumnMask(originX,originZ,stride,rows,words);
        }
    }
    public void settings(int radius,float errorPixels){if(this.radius!=radius||this.errorPixels!=errorPixels){this.radius=radius;this.errorPixels=errorPixels;centerX=Integer.MIN_VALUE;}}
    public long estimatedMeshBytes(){return meshBytes;}public int queuedTiles(){return pending.size();}
    private int projectionPixels;
    private float screenProjection,elevation; private int screenHeight; private boolean orthographic;
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

    public void gridPaint(boolean enabled) { gridPaint=enabled; }

    public void update(World world, float x, float z) {
        if (!Objects.equals(terrain.stressGrid(),world.terrain().stressGrid())) {
            // Keep the terrain used by already submitted jobs immutable.
            remove(t->true);
            terrain=new Terrain(terrain.seed,terrain.version);
            terrain.stressGrid(world.terrain().stressGrid());
            revision=-1;
        }
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
            Set<DistantTerrainPlan.Tile> changedGridTiles=new HashSet<>();
            if(terrain.stressGrid()!=null && (!meshes.isEmpty() || !pending.isEmpty())){
                for(var events:List.of(changed,removed)) for(var edit:events){
                    if(edit.depth()!=0 || edit.y()>terrain.stressGrid().grade()
                            || !terrain.stressGrid().contains(edit.x(),edit.z())) continue;
                    for(int size=32;size<=DistantTerrainPlan.ROOT_SIZE;size*=2)
                        changedGridTiles.add(new DistantTerrainPlan.Tile(Math.floorDiv(edit.x(),size)*size,
                                Math.floorDiv(edit.z(),size)*size,size));
                }
            }
            remove(t -> changedGridTiles.contains(t) || t.step() == 1 && (changed.stream().anyMatch(e -> t.touches(e.x(), e.z()))||removed.stream().anyMatch(e->t.touches(e.x(),e.z()))));
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
                if(meshBytes+bytes<=128L*1024*1024&&meshes.size()<4096){meshes.put(entry.getKey(),new Mesh(data));meshBytes+=bytes;meshGeneration++;uploadedBytes+=bytes;} else capacityRejected.add(entry.getKey());
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
                            meshBytes-=e.getValue().byteSize();e.getValue().close();meshGeneration++;
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
        if (detailTexture==0) detailTexture=glGenTextures();
        // Unit one also holds the near pass's shadow array. A 2D binding leaves that array intact.
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D,detailTexture);
        if (maskColumns!=detailed || !bounds().equals(maskBounds)) {
            columnMask=ColumnMask.build(bounds(),detailed);
            glTexImage2D(GL_TEXTURE_2D,0,GL_R32UI,columnMask.wordsPerRow(),columnMask.rows(),0,
                    GL_RED_INTEGER,GL_UNSIGNED_INT,columnMask.words());
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
            maskColumns=detailed; // World supplies a new immutable set for each membership change.
            maskBounds=bounds();
        }
        glActiveTexture(GL_TEXTURE0);
        if(terrain.stressGrid()!=null){
            shader.setVector3("uGridLayout",StressGrid.STRIDE,StressGrid.ROAD,gridPaint?RoadMarkings.GRID_STRIPE_WIDTH:0);
            shader.setVector3("uGridOrigin",StressGrid.MIN_X,0,StressGrid.MIN_Z);
            shader.setVector3("uGridGrass",GRID_GRASS[0],GRID_GRASS[1],GRID_GRASS[2]);
            shader.setVector3("uGridAsphalt",GRID_ASPHALT[0],GRID_ASPHALT[1],GRID_ASPHALT[2]);
            shader.setVector3("uGridPaint",GRID_PAINT[0],GRID_PAINT[1],GRID_PAINT[2]);
        }
        shader.setVector3("uTerrainMinimum", bounds().minX(), 0, bounds().minZ());
        shader.setVector3("uTerrainMaximum", bounds().maxX(), 0, bounds().maxZ());
        shader.setVector3("uDetailOrigin",columnMask.originX(),0,columnMask.originZ());
        shader.setInt("uDetailColumns",1);
        shader.setInt("uDistantTerrain", 1);
        shader.setInt("uVertexColor", 1);
        shader.setInt("uInstanced", 0);
        rendered = 0;
        float now=(float)org.lwjgl.glfw.GLFW.glfwGetTime();
        if(selectedGeneration!=meshGeneration){
            selectedCache=plan.select(meshes.keySet());
            transitions.select(selectedCache,meshes.keySet(),now);
            selectedGeneration=meshGeneration;
        } else {
            transitions.expire(now);
        }
        Iterable<DistantTerrainPlan.Tile> draws=selectedCache;
        if(!transitions.parents().isEmpty()){
            transitionDraws.clear();
            transitionDraws.addAll(selectedCache);
            transitionDraws.addAll(transitions.parents());
            draws=transitionDraws;
        }
        for (var tile : draws) {
            if (!frustum.testAab(
                    tile.x(),
                    Terrain.MIN_Y,
                    tile.z(),
                    tile.x() + tile.size(),
                    Terrain.MAX_Y + 1,
                    tile.z() + tile.size())) continue;
            shader.setMatrix4("uModel", modelScratch.translation(tile.x(), 0, tile.z()));
            shader.setFloat("uLodFade",transitions.fraction(tile,now));
            shader.setInt("uLodParent",transitions.parents().contains(tile)?1:0);
            meshes.get(tile).render();
            rendered++;
        }
        shader.setInt("uDistantTerrain", 0);
        shader.setFloat("uLodFade",1);shader.setInt("uLodParent",0);
    }

    @Override
    public void close() {
        if (detailTexture!=0) glDeleteTextures(detailTexture);
        pending.values().forEach(f -> f.cancel(true));
        worker.shutdownNow();
        meshes.values().forEach(Mesh::close);
        meshes.clear();
        pending.clear();
    }
}
