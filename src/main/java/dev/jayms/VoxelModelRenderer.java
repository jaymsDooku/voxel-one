package dev.jayms;

import dev.jayms.net.*;
import dev.jayms.net.model.*;

import org.joml.*;

import java.io.IOException;
import java.util.*;

/** One cached GPU mesh per definition, one instanced draw per visible model type. */
public final class VoxelModelRenderer implements AutoCloseable {
    private final ModelLibrary library;
    private final Map<Integer, Mesh> meshes = new HashMap<>();

    public VoxelModelRenderer(ModelLibrary library) {
        this.library = library;
    }

    public static Mesh mesh(ModelDefinition definition) throws IOException {
        var data = ModelMesher.mesh(definition.voxels());
        return new Mesh(new MeshData(data.vertices(), data.indices()));
    }

    private Mesh mesh(int type) {
        return meshes.computeIfAbsent(
                type,
                id -> {
                    try {
                        return mesh(library.get(id).definition());
                    } catch (IOException e) {
                        throw new IllegalStateException(e);
                    }
                });
    }

    public void render(World world, FrustumIntersection frustum, ShaderProgram shader) {
        Map<Integer, List<Float>> groups = new HashMap<>();
        for (var entry : world.getLoadedChunks().entrySet()) {
            ChunkPos p = entry.getKey();
            if (entry.getValue().models().isEmpty()
                    || !frustum.testAab(
                            p.chunkX() * 16,
                            p.chunkY() * 16,
                            p.chunkZ() * 16,
                            p.chunkX() * 16 + 16,
                            p.chunkY() * 16 + 16,
                            p.chunkZ() * 16 + 16)) continue;
            for (var item : entry.getValue().models().entrySet()) {
                if (library.get(item.getValue()) == null) continue;
                int i = item.getKey();
                var positions = groups.computeIfAbsent(item.getValue(), k -> new ArrayList<>());
                positions.add((float) (p.chunkX() * 16 + i % 16));
                positions.add((float) (p.chunkY() * 16 + i / 256));
                positions.add((float) (p.chunkZ() * 16 + (i / 16) % 16));
            }
        }
        shader.setInt("uVertexColor", 1);
        shader.setInt("uInstanced", 1);
        shader.setMatrix4("uModel", new Matrix4f());
        for (var group : groups.entrySet()) {
            float[] data = new float[group.getValue().size()];
            for (int i = 0; i < data.length; i++) data[i] = group.getValue().get(i);
            mesh(group.getKey()).renderInstanced(data);
        }
        shader.setInt("uInstanced", 0);
    }

    public void renderDrop(ItemDrop drop, float time, ShaderProgram shader) {
        if (library.get(drop.type()) == null) return;
        shader.setInt("uVertexColor", 1);
        shader.setInt("uInstanced", 0);
        shader.setMatrix4(
                "uModel",
                new Matrix4f()
                        .translate(
                                drop.x(),
                                drop.y() + .06f * (float) java.lang.Math.sin(time * 3 + drop.id()),
                                drop.z())
                        .rotateY(time + drop.id())
                        .scale(.35f)
                        .translate(-.5f, 0, -.5f));
        mesh(drop.type()).render();
    }

    @Override
    public void close() {
        for (Mesh mesh : meshes.values()) mesh.close();
        meshes.clear();
    }
}
