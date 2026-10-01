package dev.jayms.player;

import dev.jayms.*;

import org.joml.Matrix4f;
import org.joml.Vector3f;

public class PlayerModel implements AutoCloseable {
    private final Mesh cube;

    public PlayerModel() {
        Chunk source = new Chunk();
        source.setBlock(0, 0, 0, ChunkGenerator.STONE);
        source.generateMesh();
        cube = source.getMesh();
    }

    public void render(Player player, ShaderProgram shader) {
        render(
                player.position(),
                player.yaw(),
                player.pitch(),
                player.walkPhase(),
                player.walkAmount(),
                shader);
    }

    public void render(
            Vector3f position,
            float yaw,
            float pitch,
            float phase,
            float amount,
            ShaderProgram shader) {
        Matrix4f root =
                new Matrix4f().translate(position).rotateY((float) Math.toRadians(-yaw - 90));
        float swing = (float) Math.sin(phase) * .65f * amount;
        shader.setVector3("uColor", .88f, .68f, .46f);
        box(
                shader,
                new Matrix4f(root).translate(0, 1.4f, 0).rotateX((float) Math.toRadians(-pitch)),
                .4f,
                .4f,
                .4f);
        shader.setVector3("uColor", .16f, .46f, .86f);
        box(shader, new Matrix4f(root).translate(0, .7f, 0), .5f, .7f, .3f);
        limb(shader, root, -.36f, 1.4f, swing, .2f, .7f, .3f);
        limb(shader, root, .36f, 1.4f, -swing, .2f, .7f, .3f);
        shader.setVector3("uColor", .12f, .16f, .24f);
        limb(shader, root, -.13f, .7f, -swing, .22f, .7f, .3f);
        limb(shader, root, .13f, .7f, swing, .22f, .7f, .3f);
    }

    private void limb(
            ShaderProgram shader,
            Matrix4f root,
            float x,
            float y,
            float angle,
            float w,
            float h,
            float d) {
        box(
                shader,
                new Matrix4f(root).translate(x, y, 0).rotateX(angle).translate(0, -h, 0),
                w,
                h,
                d);
    }

    private void box(ShaderProgram shader, Matrix4f root, float w, float h, float d) {
        shader.setMatrix4("uModel", new Matrix4f(root).scale(w, h, d).translate(-.5f, 0, -.5f));
        cube.render();
    }

    @Override
    public void close() {
        cube.close();
    }
}
