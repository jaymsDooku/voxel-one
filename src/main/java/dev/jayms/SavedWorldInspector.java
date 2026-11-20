package dev.jayms;

import dev.jayms.net.*;
import dev.jayms.recording.ScreenRecorder;
import dev.jayms.ui.Controls;
import dev.jayms.ui.Overlay;
import dev.jayms.ui.SavedRegionCapturePanel;
import dev.jayms.window.Window;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;

/** Explicit read-only native view. Never starts LocalGame, login, simulation,
 * resident replay, editing or source-world saving. A separate saved-file worker
 * can export selected construction. The ordinary client remains separate. */
public final class SavedWorldInspector {
    record Options(Path file,float x,float y,float z) {}
    record Frame(String stage,int checkedRows,int cpuChunks,int gpuChunks,int failedChunks,
                 int residentChunks,int draws,BlockRaycaster.Trace ray,long number) {}
    interface Observer {
        default void started(SavedWorldInspector app)throws Exception {}
        default void frame(SavedWorldInspector app,Frame frame)throws Exception {}
        default void closed(SavedWorldInspector app,boolean sourceClosed)throws Exception {}
    }
    private static final ThreadLocal<Observer> OBSERVER=new ThreadLocal<>();
    private static final java.util.concurrent.atomic.AtomicBoolean RUNNING=new java.util.concurrent.atomic.AtomicBoolean();
    /** Scoped owner-thread capture seam, like Main.FrameObserver. No production
     * argument enables it or changes the read-only session's capabilities. */
    static AutoCloseable observe(Observer observer) {
        Objects.requireNonNull(observer);if(OBSERVER.get()!=null)throw new IllegalStateException("Inspection observer already set");
        Thread owner=Thread.currentThread();boolean[] done={false};OBSERVER.set(observer);
        return ()->{if(Thread.currentThread()!=owner)throw new IllegalStateException("Inspection observer owner required");if(!done[0]){if(OBSERVER.get()!=observer)throw new IllegalStateException("Inspection observer replaced");OBSERVER.remove();done[0]=true;}};
    }
    static Options parse(String[] args) {
        Objects.requireNonNull(args);Path file=null;float x=8,y=104,z=24;boolean position=false;
        for(int i=0;i<args.length;i++)switch(Objects.requireNonNull(args[i])) {
            case "--inspect-world"->{
                if(file!=null||i+1>=args.length||args[i+1].isBlank()||args[i+1].startsWith("--"))throw new IllegalArgumentException("Expected one inspection file");
                file=Path.of(args[++i]);
            }
            case "--inspect-position"->{
                if(position||i+3>=args.length)throw new IllegalArgumentException("Expected one inspection position X Y Z");
                x=Float.parseFloat(args[++i]);y=Float.parseFloat(args[++i]);z=Float.parseFloat(args[++i]);position=true;
            }
            default->throw new IllegalArgumentException("Inspection accepts only --inspect-world FILE and optional --inspect-position X Y Z");
        }
        if(file==null||!validPosition(x,y,z))throw new IllegalArgumentException("Inspection file and position within world bounds required");
        return new Options(file,x,y,z);
    }
    public static boolean launchIfRequested(String[] args)throws Exception {
        if(!Arrays.asList(args).contains("--inspect-world"))return false;
        var options=parse(args);if(!RUNNING.compareAndSet(false,true))throw new IllegalStateException("Inspection already running");
        try{new SavedWorldInspector(options).run(OBSERVER.get());return true;}finally{RUNNING.set(false);}
    }
    static boolean validPosition(float x,float y,float z) {
        return Float.isFinite(x)&&Float.isFinite(y)&&Float.isFinite(z)&&x>=-Terrain.LIMIT&&x<Terrain.LIMIT
                &&z>=-Terrain.LIMIT&&z<Terrain.LIMIT&&y>=Protocol.MIN_BUILD_Y&&y<Protocol.MAX_BUILD_Y+1;
    }
    static boolean validChunk(ChunkPos p) {
        long x=p.chunkX()*16L,y=p.chunkY()*16L,z=p.chunkZ()*16L;
        return x>=-Terrain.LIMIT&&x+15<=Terrain.LIMIT&&z>=-Terrain.LIMIT&&z+15<=Terrain.LIMIT
                &&y>=Protocol.MIN_BUILD_Y&&y+15<=Protocol.MAX_BUILD_Y;
    }
    private final Options options;
    private final Thread owner=Thread.currentThread();
    private final Camera camera=new Camera();
    private Window window;private ShaderProgram shader;private Overlay overlay;private ScreenRecorder recorder;
    private ReadOnlyWorldSession session;private SavedRegionCapturePanel capturePanel;private Path scratch;private List<ChunkPos> desired=List.of();
    private int width=960,height=640;private long frames;private String fatal;private boolean closed;
    SavedWorldInspector(Options options){this.options=Objects.requireNonNull(options);Objects.requireNonNull(options.file());if(!validPosition(options.x(),options.y(),options.z()))throw new IllegalArgumentException("Inspection position bounds");camera.position().set(options.x(),options.y(),options.z());}
    private void owner(){if(Thread.currentThread()!=owner||closed)throw new IllegalStateException("Inspection owner unavailable");}
    void requestClose(){owner();glfwSetWindowShouldClose(window.getHandle(),true);}
    World world()throws IOException{owner();return session.world();}
    Vector3f position(){owner();return new Vector3f(camera.position());}
    void toggleRecording(){owner();recorder.toggle(width,height);}
    SavedRegionCapturePanel capturePanel(){owner();return capturePanel;}
    private dev.jayms.net.structure.StructurePackage.Position cameraCell(){var p=camera.position();return new dev.jayms.net.structure.StructurePackage.Position((int)Math.floor(p.x),(int)Math.floor(p.y),(int)Math.floor(p.z));}
    /** Shared keyboard/capture control path. Free camera only: no collision,
     * movement permission or state is written into the saved world. */
    void control(int forward,int strafe,int vertical,int yaw,int pitch,float seconds) {
        owner();if(forward< -1||forward>1||strafe< -1||strafe>1||vertical< -1||vertical>1||yaw< -1||yaw>1||pitch< -1||pitch>1
                ||!Float.isFinite(seconds)||seconds<0)throw new IllegalArgumentException("Inspection control bounds");
        float dt=Math.min(seconds,.05f);camera.setYaw((camera.yaw()+yaw*90*dt)%360);camera.setPitch(camera.pitch()+pitch*90*dt);
        camera.position().add(camera.movementDelta(forward,strafe,vertical,dt));var p=camera.position();
        p.x=Math.max(-Terrain.LIMIT,Math.min(Terrain.LIMIT-1,p.x));p.z=Math.max(-Terrain.LIMIT,Math.min(Terrain.LIMIT-1,p.z));
        p.y=Math.max(Protocol.MIN_BUILD_Y,Math.min(Protocol.MAX_BUILD_Y+.9375f,p.y));
    }
    private int key(int code){return glfwGetKey(window.getHandle(),code)==GLFW_PRESS?1:0;}
    private void input(float seconds) {
        if(capturePanel.open())return;
        control(key(GLFW_KEY_W)-key(GLFW_KEY_S),key(GLFW_KEY_D)-key(GLFW_KEY_A),key(GLFW_KEY_SPACE)-key(GLFW_KEY_LEFT_SHIFT),
                key(GLFW_KEY_RIGHT)-key(GLFW_KEY_LEFT),key(GLFW_KEY_UP)-key(GLFW_KEY_DOWN),seconds);
    }
    private void size(){int[] w={0},h={0};glfwGetFramebufferSize(window.getHandle(),w,h);width=w[0];height=h[0];}
    private void clear(){size();glViewport(0,0,width,height);glClearColor(.035f,.055f,.085f,1);glClear(GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT);}
    private Frame frame()throws IOException {
        clear();var status=session.status();BlockRaycaster.Trace ray=null;int draws=0,resident=0;
        if(fatal==null)try {
            if(status.state()==ReadOnlyWorldSession.State.OPENING)status=session.poll(window::shouldClose);
            if(status.state()==ReadOnlyWorldSession.State.READY) {
                capturePanel.bindSnapshot(session.snapshotFingerprint());
                var p=camera.position();var direction=camera.getDirection();ray=session.trace(p,direction,32);
                var next=new LinkedHashSet<ChunkPos>();next.add(ChunkPos.fromBlock((int)Math.floor(p.x),(int)Math.floor(p.y),(int)Math.floor(p.z)));
                if(ray.unavailable()!=null){var cell=ray.unavailable();var chunk=ChunkPos.fromBlock(cell.x(),cell.y(),cell.z());if(validChunk(chunk))next.add(chunk);}
                var ordered=List.copyOf(next);if(!ordered.equals(desired)){session.demand(ordered,List.of());desired=ordered;}
                session.tick();session.pumpGpu(65536,System.nanoTime()+2_000_000L,()->window.shouldClose());
                status=session.status();ray=session.trace(p,direction,32);var world=session.world();resident=world.getLoadedChunks().size();
                if(width>0&&height>0) {
                    var projection=new Matrix4f().perspective((float)Math.toRadians(65),(float)width/height,.05f,96);
                    var view=camera.createViewMatrix();var frustum=new FrustumIntersection(new Matrix4f(projection).mul(view));
                    shader.bind();shader.setMatrix4("uProjection",projection);shader.setMatrix4("uView",view);shader.setMatrix4("uModel",new Matrix4f());
                    shader.setInt("uClipEnabled",0);shader.setInt("uInstanced",0);shader.setFloat("uJitterX",0);shader.setFloat("uJitterY",0);
                    glEnable(GL_DEPTH_TEST);glDisable(GL_CULL_FACE);draws=world.renderReplayGeometry(frustum,shader)+world.renderReplayModels(frustum,shader);
                }
            }else if(status.state()!=ReadOnlyWorldSession.State.OPENING)fatal="Saved world unavailable or exceeds inspection limits";
        }catch(IOException|IllegalStateException failure){fatal="Saved world unavailable or exceeds inspection limits";}
        String stage=fatal!=null?"unavailable":status.state()==ReadOnlyWorldSession.State.OPENING?"opening":ray!=null&&ray.unavailable()!=null?"loading":ray!=null&&ray.hit()!=null?"hit":"miss";
        var result=new Frame(stage,status.checkedRows(),status.readyCpuChunks(),status.readyGpuChunks(),status.failedChunks(),resident,draws,ray,++frames);
        if(width>0&&height>0) {
            overlay.begin(width,height);overlay.text("Saved world inspection - read only",20,28,1.6f);
            overlay.text(fatal!=null?fatal:stage.equals("opening")?"Checking saved data: "+status.checkedRows()+" rows":queryLabel(ray),20,58,1.3f);
            overlay.text("Visible regions ready: "+status.readyGpuChunks()+" / "+desired.size(),20,82,1.2f);
            if(status.failedChunks()>0)overlay.text("Region exceeds inspection limits; move to another region",20,106,1.2f);
            overlay.text("WASD move | Space / Shift up / down | Arrows look",20,height-58,1.1f);overlay.text("F10 record | F12 capture | Escape close | No world edits",20,height-36,1.1f);
            var capturing=capturePanel.status();
            if(!capturePanel.open()&&capturing.state()==SavedRegionCapturePanel.State.RUNNING)overlay.text(capturing.message()+" "+capturing.progress()+"% | F12 for cancel",20,124,1.1f);
            String recording=recorder.status("F10");if(!recording.isEmpty())overlay.text(recording,20,height-82,1.1f);
            capturePanel.render(overlay,width,height);
            if(!capturePanel.open())overlay.text("+",width/2f-3,height/2f,1.3f);overlay.end();
        }
        return result;
    }
    static String queryLabel(BlockRaycaster.Trace ray) {
        if(ray==null||ray.unavailable()!=null)return "Loading cells at the crosshair";
        var hit=ray.hit();return hit==null?"No surface within 32 cells":"Saved cell "+hit.x()+" / "+hit.y()+" / "+hit.z();
    }
    private void run(Observer observer)throws Exception {
        if(!glfwInit())throw new IOException("Inspection display unavailable");
        Throwable primary=null;
        try {
            window=new Window(width,height,"Voxel One Saved World Inspector",true,true);window.setOpenGlContext();GL.createCapabilities();window.vSync();window.init();
            shader=new ShaderProgram("shaders/voxel.vert","shaders/inspection.frag");overlay=new Overlay();recorder=new ScreenRecorder(Controls.directory().resolve("recordings"));
            window.registerKeyListener(GLFW_KEY_F10,GLFW_PRESS,this::toggleRecording);
            scratch=Files.createTempDirectory("voxel-inspection-");
            capturePanel=new SavedRegionCapturePanel(options.file(),Controls.directory().resolve("structures"),scratch.resolve("capture"));
            window.registerKeyListener(GLFW_KEY_F12,GLFW_PRESS,capturePanel::toggle);
            window.registerKeyListener(GLFW_KEY_BACKSPACE,GLFW_PRESS,capturePanel::backspace);
            window.registerKeyListener(GLFW_KEY_BACKSPACE,GLFW_REPEAT,capturePanel::backspace);
            window.registerKeyListener(GLFW_KEY_ENTER,GLFW_PRESS,()->capturePanel.enter(cameraCell()));
            glfwSetCharCallback(window.getHandle(),(handle,value)->capturePanel.character(value));
            session=new ReadOnlyWorldSession(options.file(),scratch,2,8192);
            if(observer!=null)observer.started(this);long previous=System.nanoTime();
            while(!window.shouldClose()) {
                long start=System.nanoTime();input(Math.min(.05f,(start-previous)/1_000_000_000f));previous=start;
                var current=frame();if(observer!=null)observer.frame(this,current);recorder.capture(width,height);window.swapBuffers();window.pollEvents();
                java.util.concurrent.locks.LockSupport.parkNanos(Math.max(0,16_666_667L-(System.nanoTime()-start)));
            }
        }catch(Exception|Error failure){primary=failure;throw failure;}
        finally {
            var cleanup=new CloseSequence();cleanup.run(()->closeCapture());cleanup.run(()->closeSource());cleanup.run(()->{if(recorder!=null)recorder.close();});
            cleanup.run(()->{if(observer!=null)observer.closed(this,session!=null&&session.status().state()==ReadOnlyWorldSession.State.CLOSED);});
            cleanup.run(()->{if(overlay!=null)overlay.close();});cleanup.run(()->{if(shader!=null)shader.close();});cleanup.run(()->{if(window!=null)window.destroy();});
            cleanup.run(()->glfwTerminate());closed=true;
            try{cleanup.finish();}catch(Exception|Error failure){if(primary!=null)primary.addSuppressed(failure);else throw failure;}
        }
    }
    private void closeCapture()throws Exception {
        if(capturePanel==null)return;capturePanel.close();
        while(capturePanel.status().state()!=SavedRegionCapturePanel.State.CLOSED) {
            if(capturePanel.status().state()==SavedRegionCapturePanel.State.CLEANUP_FAILED)throw new IOException("Capture scratch cleanup failed; owned scratch retained");
            if(width>0&&height>0){clear();overlay.begin(width,height);overlay.text("Cancelling saved construction capture...",20,40,1.5f);overlay.end();window.swapBuffers();}
            window.pollEvents();java.util.concurrent.locks.LockSupport.parkNanos(16_666_667L);
        }
        Files.deleteIfExists(scratch.resolve("capture"));
    }
    private void closeSource()throws Exception {
        if(session==null){if(scratch!=null)Files.delete(scratch);return;}
        session.close();
        while(true) {
            var status=session.status();if(status.state()==ReadOnlyWorldSession.State.CLOSED)break;
            if(status.state()==ReadOnlyWorldSession.State.CLEANUP_FAILED)throw new IOException("Inspection scratch cleanup failed; owned scratch retained");
            if(width>0&&height>0){clear();overlay.begin(width,height);overlay.text("Closing saved world inspection...",20,40,1.5f);overlay.end();window.swapBuffers();}
            window.pollEvents();java.util.concurrent.locks.LockSupport.parkNanos(16_666_667L);
        }
        Files.delete(scratch);
    }
}
