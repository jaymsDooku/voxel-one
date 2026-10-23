package dev.jayms.music;

import dev.jayms.*;
import dev.jayms.net.Blocks;
import dev.jayms.recording.ScreenRecorder;
import dev.jayms.ui.Overlay;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.*;
import org.joml.*;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.GL;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;

/** Local-only application. All GL/OpenAL ownership is scoped to one launch. */
public final class MusicVisualizer {
    @FunctionalInterface public interface Observer { void frame(MusicVisualizer app) throws Exception; }
    public long window, frames;
    public int width = 1100, height = 720, palette;
    public double sensitivity = 1, motion = 1, rotation;
    public MusicAnalysis.Bands bands = new MusicAnalysis.Bands(0,0,0,false);
    public final MusicTrack track = new MusicTrack();
    public final MusicAnalysis analysis = new MusicAnalysis();
    public final MusicLandscape landscape=new MusicLandscape();
    /** Read-only compatibility view for native harnesses; no boxed age updates per frame. */
    public final List<Double> memories=new AbstractList<>() {
        public int size(){return landscape.ripples.count();}
        public Double get(int index){for(double age:landscape.ripples.age)if(age<6&&index--==0)return age;throw new IndexOutOfBoundsException();}
    };
    private static final short[] EMPTY_PCM=new short[0];
    public boolean cameraDrift;
    private final float[] boxes=new float[4096*9];
    private int boxCount;
    private final Matrix4f projection=new Matrix4f(),view=new Matrix4f();
    public String status = "Load local MP3, WAV, AIFF or AU audio to begin.";
    public String renderer;
    private final AtomicReference<Path> requested = new AtomicReference<>();
    private final ExecutorService loader = Executors.newSingleThreadExecutor(r -> {var t = new Thread(r,"music-decode");t.setDaemon(true);return t;});
    private Future<MusicTrack.Decoded> loading;
    private volatile boolean choosing;
    private volatile JFileChooser activeChooser;
    private boolean fullscreen;
    private int[] savedX={100}, savedY={100}, savedW={1100}, savedH={720};
    private ScreenRecorder recorder;
    private String trackLabel = "No track";
    private static final float[][] COLORS = {{.12f,.74f,1f},{1f,.30f,.12f},{.55f,1f,.35f}};

    public void requestLoad(Path file) { requested.set(file); }
    public boolean loading() { return loading != null; }
    public boolean recording() { return recorder != null && recorder.active(); }
    public void seek(double time) { track.seek(time); clearHistory(); }
    public void clearHistory() { analysis.reset(); bands = new MusicAnalysis.Bands(0,0,0,false); rotation=0;landscape.reset(); }
    private void choose() {
        if (choosing) return;
        choosing = true;
        SwingUtilities.invokeLater(() -> {
            try {var picker = new JFileChooser();activeChooser=picker;picker.setDialogTitle("Load local audio: MP3 / WAV / AIFF / AU");
                picker.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Audio (MP3, WAV, AIFF, AU)","mp3","wav","aiff","aif","au"));
                if(picker.showOpenDialog(null)==JFileChooser.APPROVE_OPTION)requestLoad(picker.getSelectedFile().toPath());
            } finally { activeChooser=null;choosing=false; }
        });
    }
    public void key(int key) {
        switch(key) {
            case GLFW_KEY_ESCAPE -> glfwSetWindowShouldClose(window,true);
            case GLFW_KEY_O -> choose();
            case GLFW_KEY_SPACE -> {if(track.ended())clearHistory();track.toggle();}
            case GLFW_KEY_LEFT -> seek(track.position()-5);
            case GLFW_KEY_RIGHT -> seek(track.position()+5);
            case GLFW_KEY_R -> seek(0);
            case GLFW_KEY_D -> cameraDrift=!cameraDrift;
            case GLFW_KEY_P -> palette=(palette+1)%COLORS.length;
            case GLFW_KEY_EQUAL, GLFW_KEY_KP_ADD -> sensitivity=java.lang.Math.min(3,sensitivity+.25);
            case GLFW_KEY_MINUS, GLFW_KEY_KP_SUBTRACT -> sensitivity=java.lang.Math.max(.25,sensitivity-.25);
            case GLFW_KEY_LEFT_BRACKET -> motion=java.lang.Math.max(0,motion-.25);
            case GLFW_KEY_RIGHT_BRACKET -> motion=java.lang.Math.min(2,motion+.25);
            case GLFW_KEY_F11 -> toggleFullscreen();
            case GLFW_KEY_F10 -> recorder.toggle(width,height);
            default -> { }
        }
    }
    private void toggleFullscreen() {
        fullscreen=!fullscreen;
        if(fullscreen) {
            glfwGetWindowPos(window,savedX,savedY);glfwGetWindowSize(window,savedW,savedH);
            long monitor=glfwGetPrimaryMonitor();var mode=glfwGetVideoMode(monitor);
            if(mode==null){fullscreen=false;return;}
            glfwSetWindowMonitor(window,monitor,0,0,mode.width(),mode.height(),mode.refreshRate());
        } else glfwSetWindowMonitor(window,0,savedX[0],savedY[0],savedW[0],savedH[0],0);
    }
    private void click(double x,double y) {
        if(y>=46&&y<=79&&x>=24&&x<width-24) {
            int button=(int)((x-24)/((width-48)/7.0));
            int[] keys={GLFW_KEY_O,GLFW_KEY_SPACE,GLFW_KEY_R,GLFW_KEY_P,GLFW_KEY_F11,GLFW_KEY_F10,GLFW_KEY_ESCAPE};
            key(keys[button]);
        }
        if(y>=height-87&&y<=height-67&&track.data()!=null)seek(java.lang.Math.max(0,java.lang.Math.min(1,(x-24)/(width-48)))*track.data().duration());
        if(y>=height-48) {
            if(x<width/2.0)sensitivity=.25+2.75*java.lang.Math.max(0,java.lang.Math.min(1,(x-24)/(width/2.0-48)));
            else motion=2*java.lang.Math.max(0,java.lang.Math.min(1,(x-width/2.0-24)/(width/2.0-48)));
        }
    }
    private void update(double dt) {
        Path next=requested.getAndSet(null);
        if(next!=null) {
            if(loading!=null)loading.cancel(true);
            track.close();clearHistory();trackLabel="No track";status="Loading local audio...";
            loading=loader.submit(() -> MusicTrack.decode(next));
        }
        if(loading!=null&&loading.isDone()) {
            try { track.load(loading.get());trackLabel="Local audio";status="Playing. SPACE pauses; arrows seek 5 seconds."; }
            catch(Exception | LinkageError e) { status="Cannot play audio. File may be invalid or over 32 MiB; check audio output. O retries."; }
            loading=null;
        }
        double pos=track.position();boolean playing=track.playing();var data=track.data();
        bands=analysis.sample(data==null?EMPTY_PCM:data.stereo(),data==null?44100:data.rate(),pos,dt,playing,sensitivity);
        landscape.update(dt,bands,motion,data!=null&&!playing&&!track.ended());
        if(playing)rotation+=dt*bands.mid()*motion*1.8;
    }
    public void run(Observer observer) throws Exception {
        if(!glfwInit())throw new IllegalStateException("GLFW initialization failed");
        glfwDefaultWindowHints();glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,3);glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,3);glfwWindowHint(GLFW_OPENGL_PROFILE,GLFW_OPENGL_CORE_PROFILE);
        window=glfwCreateWindow(width,height,"Voxel Music Visualizer",0,0);
        if(window==0){glfwTerminate();loader.shutdownNow();throw new IllegalStateException("Visualizer window unavailable");}
        glfwSetWindowSizeLimits(window,1000,600,GLFW_DONT_CARE,GLFW_DONT_CARE);
        glfwMakeContextCurrent(window);GL.createCapabilities();glfwSwapInterval(1);renderer=glGetString(GL_RENDERER);
        Chunk chunk=new Chunk();chunk.setBlock(0,0,0,Blocks.STONE);chunk.generateMesh();
        try(var cube=chunk.getMesh();var shader=new ShaderProgram("shaders/music.vert","shaders/music.frag");var ui=new Overlay();
                var recording=new ScreenRecorder(Path.of(System.getProperty("user.home"),".voxel-one","recordings"))) {
            recorder=recording;
            glfwSetFramebufferSizeCallback(window,(w,x,y)->{width=x;height=y;});
            glfwSetKeyCallback(window,(w,k,s,a,m)->{if(a==GLFW_PRESS)key(k);});
            glfwSetDropCallback(window,(w,count,names)->{if(count>0)requestLoad(Path.of(GLFWDropCallback.getName(names,0)));});
            glfwSetMouseButtonCallback(window,(w,b,a,m)->{if(b==GLFW_MOUSE_BUTTON_LEFT&&a==GLFW_PRESS){double[] x={0},y={0};int[] ww={0},hh={0};glfwGetCursorPos(w,x,y);glfwGetWindowSize(w,ww,hh);click(x[0]*width/java.lang.Math.max(1,ww[0]),y[0]*height/java.lang.Math.max(1,hh[0]));}});
            double last=glfwGetTime();
            while(!glfwWindowShouldClose(window)) {
                glfwPollEvents();double now=glfwGetTime();update(java.lang.Math.min(.1,now-last));last=now;
                if(width>0&&height>0){draw(shader,cube,ui);if(observer!=null)observer.frame(this);recorder.capture(width,height);glfwSwapBuffers(window);frames++;}
            }
        } finally {
            if(loading!=null)loading.cancel(true);loader.shutdownNow();track.close();clearHistory();
            SwingUtilities.invokeLater(() -> {var picker=activeChooser;if(picker!=null){picker.cancelSelection();var dialog=SwingUtilities.getWindowAncestor(picker);if(dialog!=null)dialog.dispose();}});
            org.lwjgl.glfw.Callbacks.glfwFreeCallbacks(window);glfwDestroyWindow(window);glfwTerminate();window=0;recorder=null;
        }
    }
    private void box(float x,float y,float z,float sx,float sy,float sz,float r,float g,float b) {
        if(boxCount>=4096)throw new IllegalStateException("Landscape box cap");
        int i=boxCount++*9;boxes[i]=x;boxes[i+1]=y;boxes[i+2]=z;
        boxes[i+3]=sx;boxes[i+4]=sy;boxes[i+5]=sz;boxes[i+6]=r;boxes[i+7]=g;boxes[i+8]=b;
    }
    /** Exact visible cell top, including water only where a water box is rendered. */
    private float visibleSurface(float x,float z) {
        double cx=java.lang.Math.rint(x),cz=java.lang.Math.rint(z);
        double top=landscape.surface(cx,cz,motion);
        if(MusicLandscape.base(cx,cz)<.9)top=java.lang.Math.max(top,landscape.water(cx,cz,motion)+.05);
        return (float)top;
    }
    private void landscape() {
        float[] c=COLORS[palette];float light=(float)(.70+landscape.atmosphere*.3);
        box(0,-.4f,0,15, .6f,15,.13f,.19f,.22f);
        for(int x=-MusicLandscape.RADIUS;x<=MusicLandscape.RADIUS;x++)for(int z=-MusicLandscape.RADIUS;z<=MusicLandscape.RADIUS;z++){
            float y=(float)landscape.surface(x,z,motion);
            float shade=(float)(.83+.17*java.lang.Math.sin(x*1.7+z*2.1));
            box(x,y/2-.15f,z,.99f,y+.3f,.99f,.16f*shade*light+c[0]*.025f,.34f*shade*light+c[1]*.025f,.24f*shade*light+c[2]*.025f);
            if(MusicLandscape.base(x,z)<.9){
                float water=(float)landscape.water(x,z,motion);
                float sparkle=(float)(.03*landscape.high*(1+java.lang.Math.sin(x+z+landscape.time)));
                box(x,water,z,1,.10f,1,.09f+sparkle,.30f+sparkle,.43f+sparkle);
            }
            // Deterministic groves; every trunk starts at the exact column top.
            int hash=java.lang.Math.floorMod(x*73+z*191+x*z*31,23);
            if(hash<4&&MusicLandscape.base(x,z)>1.05&&x*x+z*z<45){
                float trunk=hash==0?1.5f:.95f;
                float sway=(float)(java.lang.Math.sin(landscape.time*1.2+x+z)*(landscape.mid*.13+.012)*motion);
                box(x,y+trunk/2,z,.19f,trunk,.19f,.29f,.23f,.16f);
                box(x+sway,y+trunk,z,1.1f,.65f,1.1f,.18f*light,.46f*light,.29f*light);
                box(x+sway*.7f,y+trunk+.5f,z,.7f,.5f,.7f,.27f*light,.55f*light,.33f*light);
            }else if(hash==7&&MusicLandscape.base(x,z)>1){
                float bend=(float)(java.lang.Math.sin(landscape.time+x)*(landscape.mid*.07+.008)*motion);
                box(x+bend,y+.16f,z,.25f,.32f,.25f,.39f,.50f,.26f);
            }
        }
        // Beat rings follow both surfaces; thin accents leave the landscape readable.
        for(int i=0;i<MusicLandscape.RIPPLE_CAP;i++)if(landscape.ripples.age[i]<6&&motion>0){
            double age=landscape.ripples.age[i],radius=age*2;float fade=(float)(1-age/6);
            for(int j=0;j<32;j++){
                double angle=j*java.lang.Math.PI/16;
                float x=(float)(landscape.ripples.x[i]+java.lang.Math.cos(angle)*radius),z=(float)(landscape.ripples.z[i]+java.lang.Math.sin(angle)*radius);
                if(java.lang.Math.abs(x)>7||java.lang.Math.abs(z)>7)continue;
                float y=visibleSurface(x,z);
                float size=.14f*fade*(float)java.lang.Math.min(1,motion);
                box(x,y+.018f,z,size,.035f,size,c[0]*.45f*fade,c[1]*.45f*fade,c[2]*.45f*fade);
            }
        }
        for(int i=0;i<MusicLandscape.MEMORY_CAP;i++)if(landscape.memory.age[i]<20){
            float remaining=(float)(1-landscape.memory.age[i]/20),fade=remaining*remaining;
            float x=(float)java.lang.Math.rint(landscape.memory.x[i]),z=(float)java.lang.Math.rint(landscape.memory.z[i]);
            float y=visibleSurface(x,z);
            box(x,y+.035f*remaining,z,.52f*remaining,.07f*remaining,.52f*remaining,.22f+c[0]*fade*.45f,.31f+c[1]*fade*.45f,.21f+c[2]*fade*.45f);
        }
        for(int i=0;i<MusicLandscape.PARTICLE_CAP;i++)if(landscape.particles.age[i]<8&&motion>0){
            double age=landscape.particles.age[i];float fade=(float)(java.lang.Math.min(1,age/.3)*(1-age/8));
            float x=(float)(landscape.particles.x[i]+java.lang.Math.sin(age+i)*.3*motion),z=(float)landscape.particles.z[i];
            float y=(float)landscape.surface(x,z,motion)+1.0f+(float)age*.14f;
            float size=.10f*fade*(float)java.lang.Math.min(1,motion);
            box(x,y,z,size,size,size,.65f+c[0]*.3f,.65f+c[1]*.3f,.38f+c[2]*.4f);
        }
    }
    private void draw(ShaderProgram shader,Mesh cube,Overlay ui) {
        float atmosphere=(float)landscape.atmosphere;
        glViewport(0,0,width,height);glClearColor(.045f+atmosphere*.025f,.075f+atmosphere*.035f,.12f+atmosphere*.045f,1);
        glClear(GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT);glEnable(GL_DEPTH_TEST);glEnable(GL_CULL_FACE);
        shader.bind();shader.setMatrix4("uProjection",projection.identity().perspective((float)java.lang.Math.toRadians(48),(float)width/height,.1f,100));
        float drift=cameraDrift?(float)(java.lang.Math.sin(landscape.time*.045)*motion):0;
        shader.setMatrix4("uView",view.identity().lookAt(17+drift,15,22-drift,0,1,0,0,1,0));
        boxCount=0;landscape();cube.renderColoredBoxes(boxes,boxCount);
        float[] c=COLORS[palette];
        ui.begin(width,height);ui.rectangle(0,0,width,119,.025f,.035f,.065f,.96f);ui.text("VOXEL MUSIC VISUALIZER",24,14,2);
        String[] buttons={"[O] Load","[SPACE] Play/pause","[R] Restart","[P] Palette","[F11] Fullscreen","[F10] Record","[ESC] Return"};
        float buttonWidth=(width-48)/7f;
        for(int i=0;i<buttons.length;i++) {ui.rectangle(24+i*buttonWidth,46,buttonWidth-5,33,.09f,.13f,.19f,1);ui.text(buttons[i],30+i*buttonWidth,57,1.05f);}
        ui.text(status,24,82,1.1f);
        ui.rectangle(0,height-118,width,118,.025f,.035f,.065f,.96f);
        double duration=track.data()==null?0:track.data().duration();double pos=track.position();
        ui.text(String.format(java.util.Locale.ROOT,"%s  %s  %.1f / %.1f s   Bass %.2f  Mid %.2f  Treble %.2f   Ripples %d/8",trackLabel,track.ended()?"FINISHED":track.playing()?"PLAYING":"PAUSED",pos,duration,bands.bass(),bands.mid(),bands.treble(),memories.size()),24,height-108,1.15f);
        ui.rectangle(24,height-85,width-48,14,.15f,.19f,.26f,1);ui.rectangle(24,height-85,(float)((width-48)*pos/java.lang.Math.max(.01,duration)),14,c[0],c[1],c[2],1);
        ui.text(String.format(java.util.Locale.ROOT,"Sensitivity [- / +] %.2f (click to set)",sensitivity),24,height-48,1.2f);
        ui.text(String.format(java.util.Locale.ROOT,"Motion [[ / ]] %.2f (click to set)",motion),width/2f+24,height-48,1.2f);
        ui.rectangle(24,height-20,(float)((width/2f-48)*(sensitivity-.25)/2.75),5,c[0],c[1],c[2],1);ui.rectangle(width/2f+24,height-20,(float)((width/2f-48)*motion/2),5,c[0],c[1],c[2],1);
        ui.text("[D] Camera drift: "+(cameraDrift?"ON":"OFF"),width-260,126,1.1f);
        String recording=recorder.status("F10");if(!recording.isEmpty())ui.text(recording,24,126,1.2f);ui.end();
    }
}
