package dev.jayms.ui;

import static org.lwjgl.opengl.GL33.*;
import static org.lwjgl.stb.STBEasyFont.*;

import org.lwjgl.system.MemoryUtil;

import java.nio.*;

/** Small core-profile UI renderer for menus, HUD, and projected holographic labels. */
public final class Overlay implements AutoCloseable {
    private float coordinateScale=1;
    public void coordinateScale(float scale){if(!Float.isFinite(scale)||scale<=0)throw new IllegalArgumentException("UI scale");coordinateScale=scale;}
    private final int program, vao, vbo;
    private static final int[] CORNERS = {0, 1, 2, 0, 2, 3};
    private final ByteBuffer quads = MemoryUtil.memAlloc(128 * 1024);
    private final FloatBuffer vertices = MemoryUtil.memAllocFloat(200000);

    public Overlay() {
        int vertex =
                compile(
                        GL_VERTEX_SHADER,
                        "#version 330 core\n"
                            + "layout(location=0) in vec2 p; layout(location=1) in vec4 color; out"
                            + " vec4 tint; uniform vec2 viewport; void"
                            + " main(){tint=color;gl_Position=vec4(p.x/viewport.x*2-1,1-p.y/viewport.y*2,0,1);}");
        int fragment =
                compile(
                        GL_FRAGMENT_SHADER,
                        "#version 330 core\n"
                                + "in vec4 tint; out vec4 fragColor; void"
                                + " main(){fragColor=tint;}");
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
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 24, 0);
        glVertexAttribPointer(1, 4, GL_FLOAT, false, 24, 8);
        glEnableVertexAttribArray(1);
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
        vertices.clear();
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glUseProgram(program);
        glUniform2f(glGetUniformLocation(program, "viewport"), width, height);
    }

    public void end() {
        flush();
        glDisable(GL_BLEND);
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
    }

    private void flush() {
        if (vertices.position() == 0) return;
        vertices.flip();
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, vertices, GL_STREAM_DRAW);
        glDrawArrays(GL_TRIANGLES, 0, vertices.remaining() / 6);
        glBindVertexArray(0);
        vertices.clear();
    }

    private void reserve(int floats) {
        if (vertices.remaining() < floats) flush();
    }

    private void vertex(float x, float y, float r, float g, float b, float alpha) {
        vertices.put(x*coordinateScale).put(y*coordinateScale).put(r).put(g).put(b).put(alpha);
    }

    public void rectangle(
            float x, float y, float width, float height, float r, float g, float b, float alpha) {
        reserve(36);
        vertex(x, y, r, g, b, alpha);
        vertex(x + width, y, r, g, b, alpha);
        vertex(x + width, y + height, r, g, b, alpha);
        vertex(x, y, r, g, b, alpha);
        vertex(x + width, y + height, r, g, b, alpha);
        vertex(x, y + height, r, g, b, alpha);
    }

    /** Thick segment rendered as triangles, independent of driver line-width limits. */
    public void line(
            float x1,
            float y1,
            float x2,
            float y2,
            float width,
            float r,
            float g,
            float b,
            float alpha) {
        double length = Math.hypot(x2 - x1, y2 - y1);
        if (length == 0) return;
        float dx = (float) (-(y2 - y1) / length * width / 2);
        float dy = (float) ((x2 - x1) / length * width / 2);
        reserve(36);
        vertex(x1 + dx, y1 + dy, r, g, b, alpha);
        vertex(x2 + dx, y2 + dy, r, g, b, alpha);
        vertex(x2 - dx, y2 - dy, r, g, b, alpha);
        vertex(x1 + dx, y1 + dy, r, g, b, alpha);
        vertex(x2 - dx, y2 - dy, r, g, b, alpha);
        vertex(x1 - dx, y1 - dy, r, g, b, alpha);
    }

    public int textWidth(String text, float scale) {
        return (int) (stb_easy_font_width(text) * scale);
    }

    public void text(
            String text, float x, float y, float scale, float r, float g, float b, float alpha) {
        String safe = text.length() > 256 ? text.substring(0, 256) : text;
        quads.clear();
        int count = stb_easy_font_print(0, 0, safe, null, quads);
        reserve(count * 36);
        for (int i = 0; i < count; i++)
            for (int corner : CORNERS) {
                int index = i * 64 + corner * 16;
                vertex(
                        x + quads.getFloat(index) * scale,
                        y + quads.getFloat(index + 4) * scale,
                        r,
                        g,
                        b,
                        alpha);
            }
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
