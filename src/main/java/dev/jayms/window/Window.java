package dev.jayms.window;

import dev.jayms.BlockHit;
import dev.jayms.BlockRaycaster;
import dev.jayms.ChunkGenerator;
import dev.jayms.collect.MultiMap;
import org.lwjgl.glfw.GLFWKeyCallbackI;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.system.MemoryStack;

import java.nio.IntBuffer;
import java.util.List;

import static dev.jayms.Util.glfwBool;
import static org.lwjgl.glfw.Callbacks.glfwFreeCallbacks;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.glfw.GLFW.GLFW_RESIZABLE;
import static org.lwjgl.glfw.GLFW.GLFW_VISIBLE;
import static org.lwjgl.glfw.GLFW.glfwWindowHint;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.NULL;

public class Window {

    private long window;

    private MultiMap<KeyAction, Runnable> keyCallbacks = new MultiMap<>();

    public Window(int width, int height, String title, boolean visible, boolean resizable) {
        glfwDefaultWindowHints();
        boolean modern = !Boolean.getBoolean("voxel.gl33");
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, modern ? 4 : 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);
        glfwWindowHint(GLFW_VISIBLE, glfwBool(visible)); // the window will stay hidden after creation
        glfwWindowHint(GLFW_RESIZABLE, glfwBool(resizable)); // the window will be resizable

        this.window = glfwCreateWindow(width, height, title, NULL, NULL);
        if (window == NULL && modern) {
            glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
            window = glfwCreateWindow(width, height, title, NULL, NULL);
        }
        if ( window == NULL )
            throw new RuntimeException("Failed to create the GLFW window");
    }

    public long getHandle() {
        return window;
    }

    public void registerKeyListener(int key, int action, Runnable callback) {
        keyCallbacks.put(new KeyAction(key, action), callback);
    }

    public void init() {
        setKeyCallback((window, key, scancode, action, mods) -> {
            if ( key == GLFW_KEY_ESCAPE && action == GLFW_RELEASE ) {
                glfwSetWindowShouldClose(window, true); // We will detect this in the rendering loop
                return;
            }

            List<Runnable> callbacks = keyCallbacks.get(new KeyAction(key, action));
            for (Runnable callback : callbacks) {
                callback.run();
            }
        });
    }

    private void setKeyCallback(GLFWKeyCallbackI callback) {
        glfwSetKeyCallback(window, callback);
    }

    public int[] getSize() {
        int[] size = new int[2];
        try (MemoryStack stack = stackPush()) {
            IntBuffer pWidth = stack.mallocInt(1);
            IntBuffer pHeight = stack.mallocInt(1);

            glfwGetWindowSize(window, pWidth, pHeight);
            size[0] = pWidth.get(0);
            size[1] = pHeight.get(0);
        }
        return size;
    }

    public GLFWVidMode getResolution() {
        return glfwGetVideoMode(glfwGetPrimaryMonitor());
    }

    public void center() {
        GLFWVidMode resolution = getResolution();
        int[] size = getSize();
        glfwSetWindowPos(
                window,
                (resolution.width() - size[0]) / 2,
                (resolution.height() - size[1]) / 2
        );
    }

    public void setOpenGlContext() {
        glfwMakeContextCurrent(window);
    }

    public void vSync() {
        glfwSwapInterval(1);
    }

    public void show() {
        glfwShowWindow(window);
    }

    public void swapBuffers() {
        glfwSwapBuffers(window);
    }

    public void pollEvents() {
        glfwPollEvents();
    }

    public boolean shouldClose() {
        return glfwWindowShouldClose(window);
    }

    public void destroy() {
        glfwFreeCallbacks(window);
        glfwDestroyWindow(window);
    }

}
