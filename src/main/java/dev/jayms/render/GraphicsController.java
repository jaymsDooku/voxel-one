package dev.jayms.render;

import dev.jayms.window.Window;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import org.lwjgl.opengl.GL;
import org.lwjgl.glfw.GLFWVidMode;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;

/** GLFW display adapter and renderer resource transaction, used only on the render thread. */
public final class GraphicsController implements AutoCloseable {
    private record WindowState(long monitor,int x,int y,int width,int height,int refresh){}
    public final GraphicsProfile.Capabilities capabilities;
    public final GraphicsTransaction<RenderPipeline> transaction;
    public final List<String> overrides;
    public final String startupWarning;
    private final Window window;
    private RenderPipeline activeRenderer;
    public GraphicsController(Window window,RenderPipeline renderer,GraphicsProfile.Loaded loaded,Path file,Consumer<RenderPipeline> publish){
        this.window=window;activeRenderer=renderer;overrides=loaded.overrides();startupWarning=loaded.warning();capabilities=capabilities();
        Path marker=file.resolveSibling(file.getFileName()+".display-pending");
        transaction=new GraphicsTransaction<>(renderer,loaded.profile(),capabilities,new GraphicsTransaction.Display(){
            public Object capture(){int[] x={0},y={0},w={0},h={0};glfwGetWindowPos(window.getHandle(),x,y);glfwGetWindowSize(window.getHandle(),w,h);var mode=glfwGetVideoMode(glfwGetPrimaryMonitor());return new WindowState(glfwGetWindowMonitor(window.getHandle()),x[0],y[0],w[0],h[0],mode==null?60:mode.refreshRate());}
            public void apply(GraphicsProfile p){if(p.riskyComparedTo(transaction.effective()))setDisplay(p);else glfwSwapInterval(p.on(GraphicsProfile.Key.VSYNC)?1:0);}
            public void restore(Object token,GraphicsProfile p){var state=(WindowState)token;glfwSetWindowAttrib(window.getHandle(),GLFW_DECORATED,p.get(GraphicsProfile.Key.DISPLAY_MODE).equals("BORDERLESS")?GLFW_FALSE:GLFW_TRUE);glfwSetWindowMonitor(window.getHandle(),state.monitor,state.x,state.y,state.width,state.height,state.refresh);glfwSwapInterval(p.on(GraphicsProfile.Key.VSYNC)?1:0);}
        },p->{
            RenderPipeline next=null;try{next=new RenderPipeline(p);next.settings.particles=activeRenderer.settings.particles;next.settings.saturation=activeRenderer.settings.saturation;next.settings.contrast=activeRenderer.settings.contrast;next.settings.cloudCoverage=activeRenderer.settings.cloudCoverage;int[] w={0},h={0};glfwGetFramebufferSize(window.getHandle(),w,h);if(w[0]<=0||h[0]<=0)throw new IllegalStateException("Display unavailable");next.prepareTargets(w[0],h[0]);if(glGetError()!=GL_NO_ERROR)throw new IllegalStateException("Renderer allocation failed");return next;}
            catch(Exception e){if(next!=null)next.close();glBindFramebuffer(GL_FRAMEBUFFER,0);throw e;}
        },new GraphicsTransaction.Store(){
            public void save(GraphicsProfile p)throws Exception{p.save(file);}
            public void beginRisk(GraphicsProfile previous)throws Exception{previous.save(file);Files.writeString(marker,"graphics-display-pending-v2\n",StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);}
            public void endRisk()throws Exception{Files.deleteIfExists(marker);}
        },next->{activeRenderer=next;publish.accept(next);});
    }
    public static GraphicsProfile.Capabilities capabilities(){
        int samples=Math.max(1,Math.min(glGetInteger(GL_MAX_SAMPLES),glGetInteger(GL_MAX_COLOR_TEXTURE_SAMPLES)));
        float anisotropy=GL.getCapabilities().GL_EXT_texture_filter_anisotropic?glGetFloat(0x84FF):1;
        var modes=new LinkedHashSet<String>();GLFWVidMode.Buffer available=glfwGetVideoModes(glfwGetPrimaryMonitor());
        if(available!=null)for(int i=0;i<available.limit();i++){var m=available.get(i);if(m.width()>=320&&m.height()>=240&&m.refreshRate()>=30&&m.refreshRate()<=360)modes.add(m.width()+"x"+m.height()+"@"+m.refreshRate());}
        return new GraphicsProfile.Capabilities(samples,Math.min(glGetInteger(GL_MAX_TEXTURE_SIZE),glGetInteger(GL_MAX_RENDERBUFFER_SIZE)),anisotropy,List.copyOf(modes));
    }
    public static GraphicsProfile.Loaded load(Path file){var loaded=GraphicsProfile.load(file);if(Files.exists(file.resolveSibling(file.getFileName()+".display-pending")))return new GraphicsProfile.Loaded(loaded.profile().with(GraphicsProfile.Key.DISPLAY_MODE,"WINDOWED"),"Recovered unconfirmed display change in windowed mode.",loaded.overrides());return loaded;}
    public void initialize(){try{setDisplay(transaction.effective());}catch(Exception e){glfwSetWindowMonitor(window.getHandle(),0,50,50,1280,720,GLFW_DONT_CARE);}}
    private void setDisplay(GraphicsProfile p){
        long handle=window.getHandle(),monitor=glfwGetPrimaryMonitor();var mode=glfwGetVideoMode(monitor);if(mode==null)throw new IllegalStateException("Monitor unavailable");
        String name=p.get(GraphicsProfile.Key.DISPLAY_MODE);int[] width={0},height={0};glfwGetWindowSize(handle,width,height);
        if(name.equals("FULLSCREEN")){glfwSetWindowAttrib(handle,GLFW_DECORATED,GLFW_TRUE);glfwSetWindowMonitor(handle,monitor,0,0,p.width(),p.height(),p.refresh());}
        else if(name.equals("BORDERLESS")){int[] x={0},y={0};glfwGetMonitorPos(monitor,x,y);glfwSetWindowAttrib(handle,GLFW_DECORATED,GLFW_FALSE);glfwSetWindowMonitor(handle,0,x[0],y[0],mode.width(),mode.height(),mode.refreshRate());}
        else{glfwSetWindowAttrib(handle,GLFW_DECORATED,GLFW_TRUE);if(glfwGetWindowMonitor(handle)!=0||width[0]!=p.width()||height[0]!=p.height())glfwSetWindowMonitor(handle,0,50,50,p.width(),p.height(),GLFW_DONT_CARE);}
        glfwSwapInterval(p.on(GraphicsProfile.Key.VSYNC)?1:0);
        if(glfwGetError(null)!=GLFW_NO_ERROR)throw new IllegalStateException("Display setup failed");
    }
    public void tick(){transaction.tick(System.nanoTime(),glfwGetWindowAttrib(window.getHandle(),GLFW_FOCUSED)==GLFW_TRUE);}
    @Override public void close(){transaction.close();}
}
