package dev.jayms.combat.client;

import dev.jayms.ShaderProgram;
import dev.jayms.player.PlayerDimensions;
import org.joml.Matrix4f;

import java.util.List;

import static org.lwjgl.opengl.GL33.*;

/** Draws the standing combat bounds at the same poses used for hit registration. */
final class PracticeHitboxes implements AutoCloseable {
    private final ShaderProgram shader = new ShaderProgram("shaders/arena-hitbox.vert", "shaders/arena-hitbox.frag");
    private final int vao = glGenVertexArrays();

    void render(Matrix4f clipFromWorld, List<ArenaMotionTimeline.Presented> fighters) {
        shader.bind();
        shader.setMatrix4("uClipFromWorld", clipFromWorld);
        shader.setFloat("uRadius", (float) PlayerDimensions.RADIUS);
        shader.setFloat("uHeight", (float) PlayerDimensions.HEIGHT);
        glBindVertexArray(vao);
        glDepthMask(false);
        for (var fighter : fighters) {
            if (fighter.fighter().health() <= 0) continue;
            shader.setVector3("uFeet", fighter.x(), fighter.y(), fighter.z());
            glDrawArrays(GL_LINES, 0, 24);
        }
        glDepthMask(true);
        glBindVertexArray(0);
    }

    @Override public void close() {
        glDeleteVertexArrays(vao);
        shader.close();
    }
}
