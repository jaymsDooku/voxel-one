package dev.jayms.player;

import dev.jayms.*;
import dev.jayms.net.*;
import dev.jayms.net.city.*;

import org.joml.Matrix4f;

import java.util.*;

/** Instanced tiny voxel crops and articulated cattle, with no population name labels. */
public final class FarmModels implements AutoCloseable {
    private final Mesh cube;

    public FarmModels() {
        var chunk = new Chunk();
        chunk.setBlock(0, 0, 0, Blocks.STONE);
        chunk.generateMesh();
        cube = chunk.getMesh();
    }

    public void crops(Agriculture.State state, ShaderProgram shader) {
        shader.setInt("uVertexColor", 0);
        shader.setInt("uInstanced", 1);
        for (int product :
                new int[] {CityMaterials.WHEAT, CityMaterials.CARROT, CityMaterials.SUGARCANE})
            for (int stage = 0; stage < 4; stage++) {
                var points = new ArrayList<float[]>();
                for (var f : state.fields())
                    if (f.product() == product && Math.min(3, (int) (f.growth() * 4)) == stage)
                        for (int x = 0; x < 4; x++)
                            for (int z = 0; z < 4; z++)
                                points.add(
                                        new float[] {
                                            f.x() + x + .5f, f.y() + .02f, f.z() + z + .5f
                                        });
                if (points.isEmpty()) continue;
                float height =
                        (stage + 1)
                                * (product == CityMaterials.SUGARCANE
                                        ? .42f
                                        : product == CityMaterials.CARROT ? .09f : .22f);
                shader.setVector3(
                        "uColor",
                        product == CityMaterials.WHEAT && stage >= 2 ? .72f : .22f,
                        product == CityMaterials.WHEAT && stage >= 2 ? .58f : .58f,
                        .12f);
                instances(
                        shader,
                        points,
                        product == CityMaterials.CARROT ? .3f : .12f,
                        height,
                        .12f,
                        0);
                if (product == CityMaterials.WHEAT) {
                    shader.setVector3("uColor", .9f, .73f, .25f);
                    instances(shader, points, .24f, .14f, .19f, height - .08f);
                }
                if (product == CityMaterials.CARROT) {
                    shader.setVector3("uColor", .94f, .38f, .08f);
                    instances(shader, points, .2f, .12f, .22f, 0);
                }
                if (product == CityMaterials.SUGARCANE) {
                    shader.setVector3("uColor", .42f, .72f, .19f);
                    instances(shader, points, .38f, .08f, .13f, height * .7f);
                }
            }
        shader.setInt("uInstanced", 0);
    }

    private void instances(
            ShaderProgram shader, List<float[]> points, float w, float h, float d, float dy) {
        float[] offsets = new float[points.size() * 3];
        int i = 0;
        for (var p : points) {
            offsets[i++] = (p[0] - w / 2) / w;
            offsets[i++] = (p[1] + dy) / h;
            offsets[i++] = (p[2] - d / 2) / d;
        }
        shader.setMatrix4("uModel", new Matrix4f().scaling(w, h, d));
        cube.renderInstanced(offsets);
    }

    public void cow(Agriculture.Cow cow, Protocol.Pose pose, ShaderProgram shader) {
        shader.setInt("uVertexColor", 0);
        shader.setInt("uInstanced", 0);
        var root =
                new Matrix4f()
                        .translate(pose.x(), pose.y(), pose.z())
                        .rotateY((float) Math.toRadians(-pose.yaw() - 90));
        if (cow.age() < 24) root.scale(.6f);
        shader.setVector3("uColor", .9f, .88f, .82f);
        box(shader, root, .9f, .75f, 1.45f, 0, .65f, 0);
        box(shader, root, .62f, .65f, .62f, 0, 1.02f, -.77f);
        for (int x : new int[] {-1, 1})
            for (int z : new int[] {-1, 1}) {
                float swing = (float) Math.sin(pose.walkPhase() + (x == z ? 0 : Math.PI)) * .16f;
                var leg = new Matrix4f(root).translate(x * .32f, .7f, z * .5f).rotateX(swing);
                box(shader, leg, .2f, .7f, .22f, 0, -.7f, 0);
                shader.setVector3("uColor", .12f, .11f, .1f);
                box(shader, leg, .22f, .13f, .24f, 0, -.7f, 0);
                shader.setVector3("uColor", .9f, .88f, .82f);
            }
        shader.setVector3("uColor", .1f, .1f, .09f);
        box(shader, root, .91f, .4f, .5f, 0, .85f, .28f);
        box(shader, root, .63f, .27f, .33f, 0, 1.35f, -.78f);
        box(shader, root, .08f, .07f, .08f, -.32f, 1.39f, -.96f);
        box(shader, root, .08f, .07f, .08f, .32f, 1.39f, -.96f);
        box(shader, root, .12f, .7f, .12f, 0, .65f, .79f);
        shader.setVector3("uColor", .9f, .57f, .59f);
        box(shader, root, .66f, .24f, .22f, 0, 1.05f, -1.1f);
        if (cow.female()) box(shader, root, .42f, .22f, .4f, 0, .48f, .25f);
        shader.setVector3("uColor", .7f, .65f, .48f);
        for (int x : new int[] {-1, 1}) {
            box(shader, root, .12f, .26f, .13f, x * .25f, 1.63f, -.76f);
            box(shader, root, .3f, .12f, .24f, x * .43f, 1.43f, -.69f);
        }
    }

    private void box(
            ShaderProgram shader,
            Matrix4f root,
            float w,
            float h,
            float d,
            float x,
            float y,
            float z) {
        shader.setMatrix4(
                "uModel",
                new Matrix4f(root).translate(x, y, z).scale(w, h, d).translate(-.5f, 0, -.5f));
        cube.render();
    }

    public void close() {
        cube.close();
    }
}
