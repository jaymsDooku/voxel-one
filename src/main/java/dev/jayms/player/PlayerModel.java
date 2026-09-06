package dev.jayms.player;

import dev.jayms.Chunk;
import dev.jayms.ChunkGenerator;
import dev.jayms.Mesh;
import dev.jayms.ShaderProgram;
import org.joml.Matrix4f;

public class PlayerModel implements AutoCloseable {

    private final Mesh cube;

    public PlayerModel() {
        Chunk source = new Chunk();
        source.setBlock(0, 0, 0, ChunkGenerator.STONE);
        source.generateMesh();
        cube = source.getMesh();
    }

    public void render(Player player, ShaderProgram shader) {
        Matrix4f root = new Matrix4f()
                .translate(player.position())
                .rotateY((float) Math.toRadians(-player.yaw() - 90.0f));

        // Arguments: centre X, bottom Y, centre Z, width, height, depth.

        // Head
        drawBox(shader, root,
                0, 1.4f, 0,
                0.4f, 0.4f, 0.4f);

        // Torso
        drawBox(shader, root,
                0, 0.7f, 0,
                0.5f, 0.7f, 0.3f);

        // Arms
        drawBox(shader, root,
                -0.36f, 0.7f, 0,
                0.2f, 0.7f, 0.3f);

        drawBox(shader, root,
                0.36f, 0.7f, 0,
                0.2f, 0.7f, 0.3f);

        // Legs
        drawBox(shader, root,
                -0.13f, 0, 0,
                0.22f, 0.7f, 0.3f);

        drawBox(shader, root,
                0.13f, 0, 0,
                0.22f, 0.7f, 0.3f);
    }

    private void drawBox(
            ShaderProgram shader,
            Matrix4f root,
            float x, float y, float z,
            float width, float height, float depth
    ) {
        Matrix4f model = new Matrix4f(root)
                .translate(x, y, z)
                .scale(width, height, depth)
                .translate(-0.5f, 0, -0.5f);

        shader.setMatrix4("uModel", model);
        cube.render();
    }

    @Override
    public void close() {
        cube.close();
    }

}
