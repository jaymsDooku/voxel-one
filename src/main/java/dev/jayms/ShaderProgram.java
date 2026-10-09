package dev.jayms;

import static org.lwjgl.opengl.GL20.*;

import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

import java.io.IOException;
import java.nio.FloatBuffer;

public final class ShaderProgram implements AutoCloseable {

    private final int programId;

    public ShaderProgram(String vertexPath, String fragmentPath) {
        String vertexSource = readFile(vertexPath);
        String fragmentSource = readFile(fragmentPath);

        int vertexShader = compileShader(GL_VERTEX_SHADER, vertexSource);

        int fragmentShader = compileShader(GL_FRAGMENT_SHADER, fragmentSource);

        programId = glCreateProgram();

        glAttachShader(programId, vertexShader);
        glAttachShader(programId, fragmentShader);
        glLinkProgram(programId);

        if (glGetProgrami(programId, GL_LINK_STATUS) == GL_FALSE) {
            throw new IllegalStateException(
                    "Shader link failed:\n" + glGetProgramInfoLog(programId));
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

            throw new IllegalStateException("Shader compilation failed:\n" + log);
        }

        return shader;
    }

    public void bind() {
        glUseProgram(programId);
    }

    public void setMatrix4(String name, Matrix4f matrix) {
        int location = glGetUniformLocation(programId, name);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer buffer = stack.mallocFloat(16);
            matrix.get(buffer);

            glUniformMatrix4fv(location, false, buffer);
        }
    }

    public void setInt(String name, int value) {
        glUniform1i(glGetUniformLocation(programId, name), value);
    }

    public void setMatrix3(String name, org.joml.Matrix3f matrix) {
        try(MemoryStack stack=MemoryStack.stackPush()) {
            glUniformMatrix3fv(glGetUniformLocation(programId,name),false,matrix.get(stack.mallocFloat(9)));
        }
    }

    public void setFloat(String name, float value) {
        glUniform1f(glGetUniformLocation(programId, name), value);
    }

    public void setInts(String name, int[] values) {
        glUniform1iv(glGetUniformLocation(programId, name), values);
    }

    public void setVector3(String name, float x, float y, float z) {
        int location = glGetUniformLocation(programId, name);

        glUniform3f(location, x, y, z);
    }

    private String readFile(String filename) {
        try {
            String resource = filename.replace("src/main/resources/", "");
            try (var input = ShaderProgram.class.getClassLoader().getResourceAsStream(resource)) {
                if (input == null) throw new IOException("Missing resource " + resource);
                String source=new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                // Shared atmosphere contract; includes are classpath resources, never external files.
                var pattern=java.util.regex.Pattern.compile("(?m)^#include \"(shaders/[a-zA-Z0-9_.-]+)\"$");
                var matcher=pattern.matcher(source);StringBuilder expanded=new StringBuilder();
                while(matcher.find()) {
                    String included=matcher.group(1);
                    if(included.equals(resource))throw new IOException("Recursive shader include");
                    try(var common=ShaderProgram.class.getClassLoader().getResourceAsStream(included)) {
                        if(common==null)throw new IOException("Missing include "+included);
                        String text=new String(common.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
                        if(text.contains("#include"))throw new IOException("Nested shader include");
                        matcher.appendReplacement(expanded,java.util.regex.Matcher.quoteReplacement(text));
                    }
                }
                matcher.appendTail(expanded);return expanded.toString();
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read shader: " + filename, exception);
        }
    }

    @Override
    public void close() {
        glDeleteProgram(programId);
    }
}
