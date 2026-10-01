package dev.jayms;

import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glVertexAttribPointer;
import static org.lwjgl.opengl.GL30.*;

import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

public final class Mesh implements AutoCloseable {

    private final int vao;
    private final int vbo;
    private final int ebo;

    private final int indexCount;
    private int instances;
    private FloatBuffer instanceData;

    public Mesh(MeshData meshData) {
        this.indexCount = meshData.indices().length;

        vao = glGenVertexArrays();
        vbo = glGenBuffers();
        ebo = glGenBuffers();

        glBindVertexArray(vao);

        uploadVertices(meshData.vertices());
        uploadIndices(meshData.indices());

        int stride = 9 * Float.BYTES;

        // Position: layout location 0
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0L);
        glEnableVertexAttribArray(0);

        // Normal: layout location 1
        glVertexAttribPointer(1, 3, GL_FLOAT, false, stride, 3L * Float.BYTES);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(2, 3, GL_FLOAT, false, stride, 6L * Float.BYTES);
        glEnableVertexAttribArray(2);

        glBindVertexArray(0);
    }

    private void uploadVertices(float[] vertices) {
        FloatBuffer buffer = MemoryUtil.memAllocFloat(vertices.length);

        try {
            buffer.put(vertices).flip();

            glBindBuffer(GL_ARRAY_BUFFER, vbo);
            glBufferData(GL_ARRAY_BUFFER, buffer, GL_STATIC_DRAW);
        } finally {
            MemoryUtil.memFree(buffer);
        }
    }

    private void uploadIndices(int[] indices) {
        IntBuffer buffer = MemoryUtil.memAllocInt(indices.length);

        try {
            buffer.put(indices).flip();

            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
            glBufferData(GL_ELEMENT_ARRAY_BUFFER, buffer, GL_STATIC_DRAW);
        } finally {
            MemoryUtil.memFree(buffer);
        }
    }

    public void render() {
        glBindVertexArray(vao);

        glDrawElements(GL_TRIANGLES, indexCount, GL_UNSIGNED_INT, 0L);

        glBindVertexArray(0);
    }

    public void renderInstanced(float[] positions) {
        if (positions.length == 0) return;
        glBindVertexArray(vao);
        if (instances == 0) {
            instances = glGenBuffers();
            glBindBuffer(GL_ARRAY_BUFFER, instances);
            glVertexAttribPointer(3, 3, GL_FLOAT, false, 3 * Float.BYTES, 0);
            glEnableVertexAttribArray(3);
            org.lwjgl.opengl.GL33.glVertexAttribDivisor(3, 1);
        }
        if (instanceData == null || instanceData.capacity() < positions.length) {
            if (instanceData != null) MemoryUtil.memFree(instanceData);
            instanceData = MemoryUtil.memAllocFloat(Math.max(64, positions.length));
        }
        instanceData.clear().put(positions).flip();
        glBindBuffer(GL_ARRAY_BUFFER, instances);
        glBufferData(GL_ARRAY_BUFFER, instanceData, GL_STREAM_DRAW);
        org.lwjgl.opengl.GL31.glDrawElementsInstanced(
                GL_TRIANGLES, indexCount, GL_UNSIGNED_INT, 0L, positions.length / 3);
        glBindVertexArray(0);
    }

    @Override
    public void close() {
        if (instances != 0) glDeleteBuffers(instances);
        if (instanceData != null) MemoryUtil.memFree(instanceData);
        glDeleteBuffers(ebo);
        glDeleteBuffers(vbo);
        glDeleteVertexArrays(vao);
    }
}
