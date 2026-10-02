package dev.jayms.player;

import dev.jayms.*;
import dev.jayms.net.*;

import org.joml.Matrix4f;

/** Articulated voxel horse with saddle, mane, tail and alternating walking legs. */
public final class HorseModel implements AutoCloseable {
    private final Mesh cube;

    public HorseModel() {
        Chunk chunk = new Chunk();
        chunk.setBlock(0, 0, 0, Blocks.STONE);
        chunk.generateMesh();
        cube = chunk.getMesh();
    }

    public void render(Protocol.Pose p, ShaderProgram shader) {
        shader.setInt("uVertexColor", 0);
        shader.setInt("uInstanced", 0);
        Matrix4f root =
                new Matrix4f()
                        .translate(p.x(), p.y(), p.z())
                        .rotateY((float) Math.toRadians(-p.yaw() - 90));
        shader.setVector3("uColor", .42f, .25f, .13f);
        box(shader, root, .65f, .63f, 1.45f, 0, .65f, 0);
        box(shader, root, .42f, .85f, .4f, 0, 1.05f, -.55f);
        box(shader, root, .45f, .42f, .75f, 0, 1.6f, -.76f);
        for (int x : new int[] {-1, 1})
            for (int z : new int[] {-1, 1}) {
                float swing =
                        (float) Math.sin(p.walkPhase() + (x == z ? 0 : Math.PI))
                                * .6f
                                * p.walkAmount();
                var leg = new Matrix4f(root).translate(x * .23f, .72f, z * .52f).rotateX(swing);
                box(shader, leg, .18f, .72f, .2f, 0, -.72f, 0);
                shader.setVector3("uColor", .16f, .12f, .09f);
                box(shader, leg, .21f, .13f, .23f, 0, -.72f, 0);
                shader.setVector3("uColor", .42f, .25f, .13f);
            }
        shader.setVector3("uColor", .12f, .08f, .05f);
        box(shader, root, .18f, .7f, .16f, 0, 1.05f, -.35f);
        box(
                shader,
                new Matrix4f(root).translate(0, .88f, .7f).rotateX(-.35f),
                .15f,
                .7f,
                .16f,
                0,
                -.7f,
                0);
        box(shader, root, .12f, .3f, .14f, -.14f, 1.97f, -.72f);
        box(shader, root, .12f, .3f, .14f, .14f, 1.97f, -.72f);
        shader.setVector3("uColor", .6f, .12f, .09f);
        box(shader, root, .72f, .1f, .65f, 0, 1.29f, .05f);
        shader.setVector3("uColor", .85f, .75f, .45f);
        box(shader, root, .74f, .12f, .18f, 0, 1.39f, -.17f);
        shader.setVector3("uColor", .05f, .04f, .03f);
        for (int x : new int[] {-1, 1}) box(shader, root, .04f, .09f, .08f, x * .23f, 1.82f, -.94f);
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
