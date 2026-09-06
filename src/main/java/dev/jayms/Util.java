package dev.jayms;

import static org.lwjgl.glfw.GLFW.GLFW_FALSE;
import static org.lwjgl.glfw.GLFW.GLFW_TRUE;

public final class Util {

    public static int glfwBool(boolean b) {
        return b ? GLFW_TRUE : GLFW_FALSE;
    }

}
