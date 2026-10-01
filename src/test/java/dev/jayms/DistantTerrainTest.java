package dev.jayms;

import static org.junit.jupiter.api.Assertions.*;

import dev.jayms.net.*;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.*;

class DistantTerrainTest {
    @Test
    void adaptiveTilesCoverTheVisibleWorldWithoutOverlapIncludingNegativeCoordinates() {
        for (int[] center : new int[][] {{0, 16}, {-160, -272}, {100000, -50000}}) {
            var plan = new DistantTerrainPlan(center[0], center[1]);
            var bounds = plan.bounds();
            assertTrue(bounds.minX() <= center[0] - 2048 && bounds.maxX() >= center[0] + 2048);
            assertTrue(bounds.minZ() <= center[1] - 2048 && bounds.maxZ() >= center[1] + 2048);
            assertTrue(plan.tiles().size() < 2048, "Bound CPU/GPU tile residency");
            var selected = plan.select(new HashSet<>(plan.tiles()));
            long area = selected.stream().mapToLong(t -> (long) t.size() * t.size()).sum();
            var coverage = plan.coverageBounds();
            assertEquals(
                    (long) (coverage.maxX() - coverage.minX())
                            * (long) (coverage.maxZ() - coverage.minZ()),
                    area);
            assertEquals(center[0], (bounds.minX() + bounds.maxX()) / 2);
            assertEquals(center[1], (bounds.minZ() + bounds.maxZ()) / 2);
            for (int i = 0; i < selected.size(); i++)
                for (int j = i + 1; j < selected.size(); j++) {
                    var a = selected.get(i);
                    var b = selected.get(j);
                    assertFalse(
                            a.x() < b.x() + b.size()
                                    && a.x() + a.size() > b.x()
                                    && a.z() < b.z() + b.size()
                                    && a.z() + a.size() > b.z(),
                            "LOD tiles overlap");
                }
            for (int a = -6; a <= 6; a++)
                for (int b = -6; b <= 6; b++) {
                    int x = center[0] + a * 16 + 8, z = center[1] + b * 16 + 8;
                    var tile =
                            selected.stream()
                                    .filter(
                                            t ->
                                                    x >= t.x()
                                                            && x < t.x() + t.size()
                                                            && z >= t.z()
                                                            && z < t.z() + t.size())
                                    .findFirst()
                                    .orElseThrow();
                    assertEquals(
                            1,
                            tile.step(),
                            "Full-detail transition must use matching voxel heights");
                }
        }
    }

    @Test
    void coarseCoverageStaysUntilEveryChildHasCoverage() {
        var plan = new DistantTerrainPlan(0, 0);
        var root =
                plan.tiles().stream()
                        .filter(t -> t.size() == 1024 && t.x() == 0 && t.z() == 0)
                        .findFirst()
                        .orElseThrow();
        Set<DistantTerrainPlan.Tile> ready = new HashSet<>();
        ready.add(root);
        assertEquals(List.of(root), plan.select(ready));
        var child = new DistantTerrainPlan.Tile(0, 0, 512);
        ready.add(child);
        assertEquals(List.of(root), plan.select(ready));
        ready.add(new DistantTerrainPlan.Tile(512, 0, 512));
        ready.add(new DistantTerrainPlan.Tile(0, 512, 512));
        ready.add(new DistantTerrainPlan.Tile(512, 512, 512));
        assertEquals(4, plan.select(ready).size());
        assertFalse(plan.select(ready).contains(root));
    }

    @Test
    void fineSurfacesUseTheWorldSeedAndKnownSurfaceEdits() {
        Terrain terrain = new Terrain(Terrain.DEFAULT_SEED);
        int h = terrain.column(-14, -13).height();
        var tile = new DistantTerrainPlan.Tile(-16, -16, 16);
        var mesh =
                new DistantTerrainMesher(
                                terrain,
                                List.of(
                                        new Protocol.Edit(-14, h, -13, 0),
                                        new Protocol.Edit(-14, 80, -13, Blocks.STONE)))
                        .build(tile);
        assertTrue(hasTop(mesh, 2, 3, h), "A mined top block must expose the next lower layer");
        assertTrue(hasTop(mesh, 2, 3, 81), "Known construction outside loaded chunks must render");
        var removed =
                new DistantTerrainMesher(
                                terrain,
                                List.of(
                                        new Protocol.Edit(-14, h, -13, 0),
                                        new Protocol.Edit(-14, 80, -13, 0)))
                        .build(tile);
        assertFalse(hasTop(removed, 2, 3, 81));
        var untouched = new DistantTerrainMesher(terrain, List.of()).build(tile);
        assertTrue(hasTop(untouched, 2, 3, h + 1));
        var other = new DistantTerrainMesher(new Terrain(123456), List.of()).build(tile);
        assertFalse(Arrays.equals(untouched.vertices(), other.vertices()));
    }

    private boolean hasTop(MeshData mesh, int x, int z, int y) {
        float[] v = mesh.vertices();
        for (int i = 0; i < v.length; i += 36)
            if (v[i] == x && v[i + 1] == y && v[i + 2] == z + 1 && v[i + 4] == 1) return true;
        return false;
    }

    @Test
    void terrainAndSkirtsHaveFiniteOutwardFacingTrianglesAtEveryResolution() {
        var mesher = new DistantTerrainMesher(new Terrain(Terrain.DEFAULT_SEED), List.of());
        for (int size : new int[] {16, 64, 256, 1024}) {
            var data = mesher.build(new DistantTerrainPlan.Tile(-size, 0, size));
            for (float value : data.vertices()) assertTrue(Float.isFinite(value));
            assertTrue(data.indices().length > 0);
            for (int i = 0; i < data.indices().length; i += 3) {
                int a = data.indices()[i] * 9,
                        b = data.indices()[i + 1] * 9,
                        c = data.indices()[i + 2] * 9;
                float[] v = data.vertices();
                Vector3f u = new Vector3f(v[b] - v[a], v[b + 1] - v[a + 1], v[b + 2] - v[a + 2]);
                Vector3f w = new Vector3f(v[c] - v[a], v[c + 1] - v[a + 1], v[c + 2] - v[a + 2]);
                assertTrue(
                        u.cross(w).dot(v[a + 3], v[a + 4], v[a + 5]) > 0,
                        "Invalid/cullable face winding");
            }
        }
    }

    @Test
    void fineForestTilesIncludeVisibleTreeGeometry() {
        Terrain terrain = new Terrain(Terrain.DEFAULT_SEED);
        for (int x = -256; x <= 256; x += 16)
            for (int z = -256; z <= 256; z += 16) {
                if (terrain.column(x, z).biome() != Terrain.Biome.FOREST) continue;
                var data =
                        new DistantTerrainMesher(terrain, List.of())
                                .build(new DistantTerrainPlan.Tile(x, z, 16));
                float[] leaves = Blocks.color(Blocks.LEAVES);
                for (int i = 0; i < data.vertices().length; i += 9)
                    if (data.vertices()[i + 6] == leaves[0]
                            && data.vertices()[i + 7] == leaves[1]
                            && data.vertices()[i + 8] == leaves[2]) return;
            }
        fail("Forest proxies should contain tree leaves, not only flat green ground");
    }

    @Test
    void editsSnapshotsAreImmutableAndEmptyColumnsNeedNoGpuMesh() {
        World world = new World();
        Chunk chunk = new Chunk();
        chunk.setBlock(0, 0, 0, Blocks.STONE);
        world.addChunk(new ChunkPos(-1, 0, -1), chunk);
        assertFalse(chunk.isEmpty());
        assertTrue(world.renderedColumns().isEmpty());
        world.setBlock(-16, 0, -16, 0);
        assertTrue(chunk.isEmpty());
        assertEquals(Set.of(new ChunkPos(-1, 0, -1)), world.renderedColumns());
        var snapshot = world.editsSnapshot();
        long revision = world.editsVersion();
        assertThrows(UnsupportedOperationException.class, () -> snapshot.clear());
        world.setBlock(-16, 0, -16, Blocks.STONE);
        assertTrue(world.editsVersion() > revision);
        assertEquals(0, snapshot.get("-16,0,-16").type());
        assertFalse(chunk.isEmpty());
    }
}
