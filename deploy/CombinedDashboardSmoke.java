import static org.lwjgl.glfw.GLFW.*;

/** Verify the preserved Trends harness and Exchange harness against the combined dashboard. */
public final class CombinedDashboardSmoke {
    public static void main(String[] args) throws Exception {
        glfwInitHint(GLFW_PLATFORM, GLFW_PLATFORM_NULL);
        if (!glfwInit()) throw new AssertionError("GLFW null platform unavailable");
        glfwWindowHint(GLFW_CONTEXT_CREATION_API, GLFW_EGL_CONTEXT_API);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        long window = glfwCreateWindow(1280, 800, "Combined dashboard verification", 0, 0);
        if (window == 0) throw new AssertionError("EGL context unavailable");
        try {
            glfwMakeContextCurrent(window);
            dev.jayms.MetricTrendsSmoke.main(new String[] {args[0], "offscreen"});
        } finally {
            glfwDestroyWindow(window);
            glfwTerminate();
        }
        CapitalRenderingSmoke.main(args);
    }
}
