package dev.jayms.ui;

import static org.lwjgl.opengl.GL33.*;
import static org.lwjgl.stb.STBEasyFont.*;

import org.lwjgl.system.MemoryUtil;

import java.nio.*;

/** Small core-profile UI renderer for menus, HUD, and projected holographic labels. */
public final class Overlay implements AutoCloseable {
    private final int program, vao, vbo;
    private final ByteBuffer quads = MemoryUtil.memAlloc(128 * 1024);
    private final FloatBuffer vertices = MemoryUtil.memAllocFloat(200000);

    public Overlay() {
        int vertex =
                compile(
                        GL_VERTEX_SHADER,
                        "#version 330 core\n"
                            + "layout(location=0) in vec2 p; uniform vec2 viewport; void"
                            + " main(){gl_Position=vec4(p.x/viewport.x*2-1,1-p.y/viewport.y*2,0,1);}");
        int fragment =
                compile(
                        GL_FRAGMENT_SHADER,
                        "#version 330 core\n"
                            + "uniform vec4 color; out vec4 fragColor; void"
                            + " main(){fragColor=color;}");
        program = glCreateProgram();
        glAttachShader(program, vertex);
        glAttachShader(program, fragment);
        glLinkProgram(program);
        glDeleteShader(vertex);
        glDeleteShader(fragment);
        if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE)
            throw new IllegalStateException(glGetProgramInfoLog(program));
        vao = glGenVertexArrays();
        vbo = glGenBuffers();
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 8, 0);
        glEnableVertexAttribArray(0);
        glBindVertexArray(0);
    }

    private int compile(int type, String source) {
        int shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE)
            throw new IllegalStateException(glGetShaderInfoLog(shader));
        return shader;
    }

    public void begin(int width, int height) {
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glUseProgram(program);
        glUniform2f(glGetUniformLocation(program, "viewport"), width, height);
    }

    public void end() {
        glDisable(GL_BLEND);
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
    }

    private void draw(float r, float g, float b, float alpha) {
        vertices.flip();
        glUniform4f(glGetUniformLocation(program, "color"), r, g, b, alpha);
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, vertices, GL_STREAM_DRAW);
        glDrawArrays(GL_TRIANGLES, 0, vertices.remaining() / 2);
        glBindVertexArray(0);
    }

    public void rectangle(
            float x, float y, float width, float height, float r, float g, float b, float alpha) {
        vertices.clear();
        vertices.put(
                new float[] {
                    x, y, x + width, y, x + width, y + height, x, y, x + width, y + height, x,
                    y + height
                });
        draw(r, g, b, alpha);
    }

    public int textWidth(String text, float scale) {
        return (int) (stb_easy_font_width(text) * scale);
    }

    public void text(
            String text, float x, float y, float scale, float r, float g, float b, float alpha) {
        String safe = text.length() > 256 ? text.substring(0, 256) : text;
        quads.clear();
        int count = stb_easy_font_print(0, 0, safe, null, quads);
        vertices.clear();
        for (int i = 0; i < count; i++)
            for (int corner : new int[] {0, 1, 2, 0, 2, 3}) {
                int index = i * 64 + corner * 16;
                vertices.put(x + quads.getFloat(index) * scale)
                        .put(y + quads.getFloat(index + 4) * scale);
            }
        draw(r, g, b, alpha);
    }

    public void text(String text, float x, float y, float scale) {
        text(text, x, y, scale, .87f, .95f, 1, 1);
    }

    @Override
    public void close() {
        glDeleteBuffers(vbo);
        glDeleteVertexArrays(vao);
        glDeleteProgram(program);
        MemoryUtil.memFree(quads);
        MemoryUtil.memFree(vertices);
    }
}
