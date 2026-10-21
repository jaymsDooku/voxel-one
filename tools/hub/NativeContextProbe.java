import static org.lwjgl.glfw.GLFW.*;

/** Bounded context diagnosis only. Success does not qualify gameplay or hardware. */
public final class NativeContextProbe {
    public static void main(String[] args) {
        if (!glfwInit()) {
            System.out.println("Context diagnosis: initialization failed code=0x"+Integer.toHexString(glfwGetError((org.lwjgl.PointerBuffer)null)));
            return;
        }
        try {
            for (int variant=0;variant<4;variant++) {
                glfwDefaultWindowHints();
                glfwWindowHint(GLFW_VISIBLE,GLFW_FALSE);
                glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,variant==0?4:3);
                glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,3);
                glfwWindowHint(GLFW_OPENGL_PROFILE,GLFW_OPENGL_CORE_PROFILE);
                glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT,GLFW_TRUE);
                if(variant==2)glfwWindowHint(GLFW_COCOA_RETINA_FRAMEBUFFER,GLFW_FALSE);
                if(variant==3) {
                    glfwWindowHint(GLFW_RED_BITS,GLFW_DONT_CARE);
                    glfwWindowHint(GLFW_GREEN_BITS,GLFW_DONT_CARE);
                    glfwWindowHint(GLFW_BLUE_BITS,GLFW_DONT_CARE);
                    glfwWindowHint(GLFW_ALPHA_BITS,GLFW_DONT_CARE);
                    glfwWindowHint(GLFW_DEPTH_BITS,GLFW_DONT_CARE);
                    glfwWindowHint(GLFW_STENCIL_BITS,GLFW_DONT_CARE);
                    glfwWindowHint(GLFW_DOUBLEBUFFER,GLFW_FALSE);
                }
                glfwGetError((org.lwjgl.PointerBuffer)null);
                long window=glfwCreateWindow(64,64,"Synthetic context diagnosis",0,0);
                int code=glfwGetError((org.lwjgl.PointerBuffer)null);
                System.out.println("Context diagnosis: variant="+variant+" created="+(window!=0)+" code=0x"+Integer.toHexString(code));
                if(window!=0)glfwDestroyWindow(window);
            }
        } finally {glfwTerminate();}
        System.out.println("Playtest: four bounded context attempts completed; diagnostic only, no gameplay qualification.");
    }
}
