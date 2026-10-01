package dev.jayms;

import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.lwjgl.opengl.GL20.*;

public final class ShaderProgram implements AutoCloseable {

    private final int programId;

    public ShaderProgram(
            String vertexPath,
            String fragmentPath
    ) {
        String vertexSource = readFile(vertexPath);
        String fragmentSource = readFile(fragmentPath);

        int vertexShader = compileShader(
                GL_VERTEX_SHADER,
                vertexSource
        );

        int fragmentShader = compileShader(
                GL_FRAGMENT_SHADER,
                fragmentSource
        );

        programId = glCreateProgram();

        glAttachShader(programId, vertexShader);
        glAttachShader(programId, fragmentShader);
        glLinkProgram(programId);

        if (glGetProgrami(programId, GL_LINK_STATUS) == GL_FALSE) {
            throw new IllegalStateException(
                    "Shader link failed:\n"
                            + glGetProgramInfoLog(programId)
            );
        }

        glDetachShader(programId, vertexShader);
        glDetachShader(programId, fragmentShader);

        glDeleteShader(vertexShader);
        glDeleteShader(fragmentShader);
    }

    private int compileShader(int type, String source) {
        int shader = glCreateShader(type);

        glShaderSource(shader, source);
        glCompileShader(shader);

        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            String log = glGetShaderInfoLog(shader);
            glDeleteShader(shader);

            throw new IllegalStateException(
                    "Shader compilation failed:\n" + log
            );
        }

        return shader;
    }

    public void bind() {
        glUseProgram(programId);
    }

    public void setMatrix4(
            String name,
            Matrix4f matrix
    ) {
        int location =
                glGetUniformLocation(programId, name);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer buffer = stack.mallocFloat(16);
            matrix.get(buffer);

            glUniformMatrix4fv(
                    location,
                    false,
                    buffer
            );
        }
    }

    public void setVector3(
            String name,
            float x,
            float y,
            float z
    ) {
        int location =
                glGetUniformLocation(programId, name);

        glUniform3f(location, x, y, z);
    }

    private String readFile(String filename) {
        try {
            String resource = filename.replace("src/main/resources/", "");
            try (var input = ShaderProgram.class.getClassLoader().getResourceAsStream(resource)) {
                if (input == null) throw new IOException("Missing resource " + resource);
                return new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Could not read shader: " + filename,
                    exception
            );
        }
    }

    @Override
    public void close() {
        glDeleteProgram(programId);
    }
}