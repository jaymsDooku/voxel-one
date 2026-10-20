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
    public final ArrayList<Double> memories = new ArrayList<>();
    public String status = "Load local WAV, AIFF or AU audio to begin.";
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
    public void clearHistory() { analysis.reset(); memories.clear(); bands = new MusicAnalysis.Bands(0,0,0,false); rotation=0; }
    private void choose() {
        if (choosing) return;
        choosing = true;
        SwingUtilities.invokeLater(() -> {
            try {var picker = new JFileChooser();activeChooser=picker;picker.setDialogTitle("Load local audio: WAV / AIFF / AU");
                picker.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Audio (WAV, AIFF, AU)","wav","aiff","aif","au"));
                if(picker.showOpenDialog(null)==JFileChooser.APPROVE_OPTION)requestLoad(picker.getSelectedFile().toPath());
            } finally { activeChooser=null;choosing=false; }
        });
    }
    public void key(int key) {
        switch(key) {
            case GLFW_KEY_ESCAPE -> glfwSetWindowShouldClose(window,true);
            case GLFW_KEY_O -> choose();
            case GLFW_KEY_SPACE -> track.toggle();
            case GLFW_KEY_LEFT -> seek(track.position()-5);
            case GLFW_KEY_RIGHT -> seek(track.position()+5);
            case GLFW_KEY_R -> seek(0);
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
            catch(Exception | LinkageError e) { status="Cannot play audio. Use WAV / AIFF / AU and an audio output; O retries."; }
            loading=null;
        }
        double pos=track.position();boolean playing=track.playing();var data=track.data();
        bands=analysis.sample(data==null?new short[0]:data.stereo(),data==null?44100:data.rate(),pos,dt,playing,sensitivity);
        for(int i=memories.size()-1;i>=0;i--){double age=memories.get(i)+dt;if(age>=3)memories.remove(i);else memories.set(i,age);}
        if(bands.beat()&&playing){if(memories.size()==12)memories.remove(0);memories.add(0d);}
        rotation+=dt*bands.mid()*motion*1.8;
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
    private void box(ShaderProgram shader,Mesh cube,float x,float y,float z,float sx,float sy,float sz,float r,float g,float b) {
        shader.setVector3("uColor",r,g,b);shader.setMatrix4("uModel",new Matrix4f().translation(x,y,z).scale(sx,sy,sz).translate(-.5f,-.5f,-.5f));cube.render();
    }
    private void core(ShaderProgram shader,Mesh cube,boolean reflection) {
        float[] color=COLORS[palette];float expand=(float)(1+bands.bass()*motion*.65);float sign=reflection?-1:1;float dim=reflection?.24f:1;
        for(int layer=-2;layer<=2;layer++)for(int i=0;i<16;i++) {
            double angle=i*java.lang.Math.PI/8+rotation*(layer%2==0?1:-1);float radius=(2.0f-java.lang.Math.abs(layer)*.24f)*expand;
            float x=(float)java.lang.Math.cos(angle)*radius,z=(float)java.lang.Math.sin(angle)*radius,y=(4.4f+layer*.58f)*sign;
            float edge=(float)(.45+.55*bands.treble());
            box(shader,cube,x,y,z,.36f*expand,.36f,.36f*expand,color[0]*edge*dim,color[1]*edge*dim,color[2]*edge*dim);
        }
        box(shader,cube,0,4.4f*sign,0,1.2f*expand,1.2f*expand,1.2f*expand,color[0]*dim,color[1]*dim,color[2]*dim);
    }
    private void draw(ShaderProgram shader,Mesh cube,Overlay ui) {
        glViewport(0,0,width,height);glClearColor(.018f,.025f,.055f,1);glClear(GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT);glEnable(GL_DEPTH_TEST);glEnable(GL_CULL_FACE);
        shader.bind();shader.setMatrix4("uProjection",new Matrix4f().perspective((float)java.lang.Math.toRadians(48),(float)width/height,.1f,100));
        shader.setMatrix4("uView",new Matrix4f().lookAt(new Vector3f(11,10,17),new Vector3f(0,2,0),new Vector3f(0,1,0)));
        // Mirrored core lies beneath a translucent floor, giving a planar reflection.
        core(shader,cube,true);
        glEnable(GL_BLEND);glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA);shader.setFloat("uAlpha",.60f);
        box(shader,cube,0,-.12f,0,30,.12f,30,.055f,.085f,.13f);shader.setFloat("uAlpha",1);glDisable(GL_BLEND);
        float[] c=COLORS[palette];
        for(int i=-7;i<=7;i++) {
            box(shader,cube,i*2,.002f,0,.016f,.012f,28,.08f,.14f,.20f);
            box(shader,cube,0,.002f,i*2,28,.012f,.016f,.08f,.14f,.20f);
        }
        for(double age:memories) {
            float fade=(float)(1-age/3),radius=(float)(1+age*4*motion);
            for(int i=0;i<40;i++) {double a=i*java.lang.Math.PI/20;
                box(shader,cube,(float)java.lang.Math.cos(a)*radius,.08f,(float)java.lang.Math.sin(a)*radius,.18f*fade,.08f*fade,.18f*fade,c[0]*fade,c[1]*fade,c[2]*fade);
            }
            double a=age*2.4;for(int j=0;j<4;j++)box(shader,cube,(float)java.lang.Math.cos(a)*5,(j*.4f+.25f)*fade,(float)java.lang.Math.sin(a)*5,.32f*fade,.32f*fade,.32f*fade,c[0]*fade,c[1]*fade,c[2]*fade);
        }
        core(shader,cube,false);
        ui.begin(width,height);ui.rectangle(0,0,width,119,.025f,.035f,.065f,.96f);ui.text("VOXEL MUSIC VISUALIZER",24,14,2);
        String[] buttons={"[O] Load","[SPACE] Play/pause","[R] Restart","[P] Palette","[F11] Fullscreen","[F10] Record","[ESC] Return"};
        float buttonWidth=(width-48)/7f;
        for(int i=0;i<buttons.length;i++) {ui.rectangle(24+i*buttonWidth,46,buttonWidth-5,33,.09f,.13f,.19f,1);ui.text(buttons[i],30+i*buttonWidth,57,1.05f);}
        ui.text(status,24,82,1.1f);
        ui.rectangle(0,height-118,width,118,.025f,.035f,.065f,.96f);
        double duration=track.data()==null?0:track.data().duration();double pos=track.position();
        ui.text(String.format(java.util.Locale.ROOT,"%s  %s  %.1f / %.1f s   Bass %.2f  Mid %.2f  Treble %.2f   Memories %d/12",trackLabel,track.ended()?"FINISHED":track.playing()?"PLAYING":"PAUSED",pos,duration,bands.bass(),bands.mid(),bands.treble(),memories.size()),24,height-108,1.15f);
        ui.rectangle(24,height-85,width-48,14,.15f,.19f,.26f,1);ui.rectangle(24,height-85,(float)((width-48)*pos/java.lang.Math.max(.01,duration)),14,c[0],c[1],c[2],1);
        ui.text(String.format(java.util.Locale.ROOT,"Sensitivity [- / +] %.2f (click to set)",sensitivity),24,height-48,1.2f);
        ui.text(String.format(java.util.Locale.ROOT,"Motion [[ / ]] %.2f (click to set)",motion),width/2f+24,height-48,1.2f);
        ui.rectangle(24,height-20,(float)((width/2f-48)*(sensitivity-.25)/2.75),5,c[0],c[1],c[2],1);ui.rectangle(width/2f+24,height-20,(float)((width/2f-48)*motion/2),5,c[0],c[1],c[2],1);
        String recording=recorder.status("F10");if(!recording.isEmpty())ui.text(recording,24,126,1.2f);ui.end();
    }
}
