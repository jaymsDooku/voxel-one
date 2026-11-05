import dev.jayms.window.Window;
import static org.lwjgl.glfw.GLFW.*;

/** Real invalid-window failure path. Fixed codes only; no native error text. */
public final class WindowFailureProbe {
    public static void main(String[] args) {
        if (!glfwInit()) throw new AssertionError("Synthetic window test could not initialize GLFW");
        try {
            try {
                Window unexpected = new Window(-1, 32, "Synthetic invalid window", false, false);
                glfwDestroyWindow(unexpected.getHandle());
                throw new AssertionError("Invalid width unexpectedly accepted");
            } catch (RuntimeException expected) {
                if (!"Failed to create the GLFW window (GLFW error 0x10004)".equals(expected.getMessage()))
                    throw new AssertionError("Expected bounded GLFW_INVALID_VALUE category");
            }
            System.out.println("Playtest: invalid native width returned bounded GLFW error 0x10004; no driver text retained.");
        } finally { glfwTerminate(); }
    }
}
