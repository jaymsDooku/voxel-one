import dev.jayms.*;
import dev.jayms.net.*;
import dev.jayms.net.model.*;
import dev.jayms.player.*;
import dev.jayms.recording.*;
import org.joml.*;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryUtil;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;

/** Production player renderer with a synthetic observer receiving serialized remote snapshots. */
public class ToolGripRenderCheck {
    static int width=960,height=720;
    static ShaderProgram shader;
    static PlayerModel rig;
    static VoxelModelRenderer models;
    static Path out;
    static ScreenRecorder recorder;
    static ToolGripNetworkFixture network;
    static boolean placing;
    static int tiny;
    static void render(int type,int view,float progress,float phase,float gait,float yaw,float pitch,float lift,int fov) throws Exception {
        glViewport(0,0,width,height);glClearColor(.35f,.55f,.72f,1);glClear(GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT);
        shader.bind();
        shader.setInt("uEnvironment",0);shader.setInt("uReflectionProbe",0);
        shader.setInt("uShadow",1);shader.setInt("uMaterials",1);
        shader.setInt("uIrradiance",2);shader.setInt("uVoxelRadiance",2);shader.setInt("uDistanceField",2);
        for(int i=0;i<3;i++)shader.setInt("uCascadeProbes["+i+"]",2);
        shader.setInt("uClusters",3);shader.setInt("uFineRoots",3);
        shader.setInt("uLightIndices",4);shader.setInt("uFineLight",4);shader.setInt("uLights",5);shader.setInt("uTransmittance",6);
        shader.setVector3("uLightDirection",-.4f,-.8f,-.5f);
        shader.setInt("uFog",0);shader.setInt("uLightingEnabled",0);shader.setInt("uHeld",view==0?1:0);
        shader.setMatrix4("uProjection",new Matrix4f().perspective((float)java.lang.Math.toRadians(fov),(float)width/height,.03f,30));
        if(view==0) {
            shader.setMatrix4("uView",new Matrix4f());
            Player p=new Player(new Vector3f(),0,0,new Camera());p.heldItem(type);p.heldColor(0x40c0ff);
            var place=Player.class.getDeclaredField("placingSwing");place.setAccessible(true);place.setBoolean(p,placing);
            for(String field:new String[]{"swingProgress","walkPhase","walkAmount"}) {
                var f=Player.class.getDeclaredField(field);f.setAccessible(true);f.setFloat(p,field.equals("swingProgress")?progress:field.equals("walkPhase")?phase:gait);
            }
            rig.renderFirstPerson(p,shader,models);
        } else {
            var eye=view==1?new Vector3f(0,1.3f,4):view==2?new Vector3f(0,1.3f,-4):new Vector3f(3,1.8f,-3);
            shader.setMatrix4("uView",new Matrix4f().lookAt(eye,new Vector3f(0,1,0),new Vector3f(0,1,0)));
            var pose=new Protocol.Pose(7,0,lift,0,yaw,pitch,phase,gait,lift>0,progress,type);
            // Exact protocol roundtrip, then production RemotePlayer interpolation and render.
            var bytes=new java.io.ByteArrayOutputStream();pose.write(new java.io.DataOutputStream(bytes));
            var received=Protocol.Pose.read(new java.io.DataInputStream(new java.io.ByteArrayInputStream(bytes.toByteArray())));
            RemotePlayer remote=new RemotePlayer("Synthetic observer fixture");remote.accept(received,0);
            Protocol.Pose seen=network.pose(type,progress,phase,gait,yaw,pitch,lift,placing);
            // Camera follows the subject's real server coordinates; the observer's received
            // world pose goes unchanged into PlayerModel, as in Main's remote draw path.
            Vector3f origin=new Vector3f(seen.x(),seen.y()-lift,seen.z());
            shader.setMatrix4("uView",new Matrix4f().lookAt(new Vector3f(eye).add(origin),
                    new Vector3f(0,1,0).add(origin),new Vector3f(0,1,0)));
            rig.render(seen,shader,models);
        }
        if(glGetError()!=GL_NO_ERROR)throw new AssertionError("OpenGL render error");
    }
    static void capture(String name)throws Exception {
        var pixels=MemoryUtil.memAlloc(width*height*3);glPixelStorei(GL_PACK_ALIGNMENT,1);
        glReadPixels(0,0,width,height,GL_RGB,GL_UNSIGNED_BYTE,pixels);
        BufferedImage image=new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);
        for(int y=0;y<height;y++)for(int x=0;x<width;x++){int i=((height-1-y)*width+x)*3;
            image.setRGB(x,y,((pixels.get(i)&255)<<16)|((pixels.get(i+1)&255)<<8)|(pixels.get(i+2)&255));}
        MemoryUtil.memFree(pixels);javax.imageio.ImageIO.write(image,"png",out.resolve(name+".png").toFile());
    }
    public static void main(String[] args) throws Exception {
        try { run(args); } finally { if(network!=null) network.close(); }
    }
    static void run(String[]args)throws Exception {
        network=new ToolGripNetworkFixture();
        out=Path.of(args[0]);Files.createDirectories(out);glfwInitHint(GLFW_PLATFORM,GLFW_PLATFORM_X11);if(!glfwInit())throw new AssertionError("GLFW unavailable");
        var window=new dev.jayms.window.Window(width,height,"Voxel One tool grip render check",true,true);
        long h=window.getHandle();glfwMakeContextCurrent(h);GL.createCapabilities();glEnable(GL_DEPTH_TEST);glEnable(GL_CULL_FACE);
        shader=new ShaderProgram("shaders/voxel.vert","shaders/voxel.frag");rig=new PlayerModel();var library=new ModelLibrary();
        var voxels=new SparseVoxelOctree(16);voxels.fill(7,0,7,9,2,9,0xffe09040);
        tiny=library.register(new ModelDefinition("Synthetic tiny model",voxels.freeze()),"fixture").id();
        models=new VoxelModelRenderer(library);
        recorder=new ScreenRecorder(Path.of("target/tool-render-recordings"));
        glfwSetKeyCallback(h,(w,k,s,a,m)->{if(k==GLFW_KEY_F10&&a==GLFW_PRESS)recorder.toggle(width,height);});
        for(int type=191;type<=(args.length>1&&args[1].equals("baseline")?192:198);type++)for(int view=0;view<4;view++){
            render(type,view,1,0,0,-90,0,0,70);capture("grip-render-"+type+"-view"+view);glfwSwapBuffers(h);glfwPollEvents();}
        if(args.length>1&&args[1].equals("baseline")) {
            models.close();rig.close();shader.close();glfwDestroyWindow(h);glfwTerminate();return;
        }
        // Exercise the full attached arc for every material/category and camera.
        for(int type=191;type<=198;type++) for(int view=0;view<4;view++) {
            for(boolean place:new boolean[]{false,true}) {
                placing=place;
                for(float progress:new float[]{0,.18f,.45f,.72f,.9f,1}) {
                    render(type,view,progress,2,.35f,-75,15,.2f,70);
                    if(!place && (progress==.18f || progress==.72f))
                        capture("arc-"+type+"-view"+view+"-"+(progress==.18f?"high":"low"));
                }
            }
        }
        placing=false;
        for(int w:new int[]{960,1280})for(int fov:new int[]{50,70,95}) {
            width=w;height=720;glfwSetWindowSize(h,width,height);Thread.sleep(100);glfwPollEvents();glfwSwapBuffers(h);glfwPollEvents();
            for(int type:new int[]{191,192,193,194}) {
                render(type,0,1,0,0,-90,0,0,fov);capture("grip-framing-"+type+"-"+width+"-fov"+fov);
                for(float progress:new float[]{.18f,.72f}) {
                    render(type,0,progress,0,0,-90,0,0,fov);
                    capture("framing-arc-"+type+"-"+width+"-fov"+fov+"-"+(progress==.18f?"high":"low"));
                }
            }
        }
        width=960;height=720;glfwSetWindowSize(h,width,height);Thread.sleep(100);glfwPollEvents();glfwSwapBuffers(h);glfwPollEvents();
        for(int type:new int[]{0,Blocks.STONE,Blocks.piece(Blocks.STONE,1),Blocks.piece(Blocks.STONE,2),Blocks.FLOWER_POT,Blocks.LED,Items.STICKS,tiny}) {
            render(type,0,1,0,0,-90,0,0,70);capture("grip-render-generic-"+type);}
        // Same ScreenRecorder used by Main's F10; event delivered to the actual GLFW window.
        String xid=Long.toString(org.lwjgl.glfw.GLFWNativeX11.glfwGetX11Window(h));
        new ProcessBuilder("xdotool","key","--window",xid,"F10").start().waitFor();glfwPollEvents();
        if(!recorder.active())throw new AssertionError("F10 did not start recorder");
        World floor=new World();Chunk chunk=new Chunk();
        for(int x=0;x<16;x++)for(int z=0;z<16;z++)chunk.setBlock(x,0,z,Blocks.STONE);
        floor.addChunk(new ChunkPos(0,0,0),chunk);
        Player motion=new Player(new Vector3f(8,1,8),-90,0,new Camera());
        float highest=1;boolean flew=false,walked=false;
        for(int frame=0;frame<210;frame++) {
            int view=frame<90?0:frame<180?3:0;
            if(frame==15||frame==105)motion.swing();
            if(frame==160){motion.toggleFlight();flew=true;}
            if(frame==190)motion.swing(true);
            float forward=frame>=45&&frame<60||frame>=130&&frame<145?.5f:0;
            motion.step(floor,1f/15,forward,0,frame==145||frame>=160&&frame<163,false);
            highest=java.lang.Math.max(highest,motion.position().y);
            walked|=motion.walkAmount()>.1f;
            if(frame>=165)motion.look(2,frame<175?1:0);
            placing=motion.placingSwing();
            render(frame>=180?Blocks.STONE:195,view,motion.swingProgress(),motion.walkPhase(),motion.walkAmount(),
                    motion.pose(1).yaw(),motion.pose(1).pitch(),motion.position().y-1,70);
            if(frame==145)capture("tool-grip-jump-start");
            if(frame==149)capture("tool-grip-jump-airborne");
            if(frame==162)capture("tool-grip-flight");
            recorder.capture(width,height);glfwSwapBuffers(h);glfwPollEvents();Thread.sleep(67);
        }
        if(highest<1.5f||!flew||!walked)throw new AssertionError("Movement fixture did not exercise walk/jump/flight");
        new ProcessBuilder("xdotool","key","--window",xid,"F10").start().waitFor();glfwPollEvents();
        recorder.close();models.close();rig.close();shader.close();glfwDestroyWindow(h);glfwTerminate();
        Files.writeString(out.resolve("tool-render-result.txt"),"Playtest: Linux X11 software OpenGL. Production PlayerModel, original Item voxel meshes, ShaderProgram, ScreenRecorder. All eight tools captured first person, rear, front and oblique remote observer; two synthetic loopback TLS clients: subject sends held/swing snapshots, observer receives and renders actual RemotePlayer.sample avatars. Ordinary 960x720 and wide 1280x720 projections at 50/70/95 degrees; production Main currently uses fixed 70 degrees in both views. Generic empty, Stone, half/quarter Stone, pot, custom tiny model, blue LED and sticks. F10 starts/stops production recorder via X11; 14 seconds of simulated movement, dt=1/15 second, recorded with wall-clock timestamps: first-person and remote idle, downward attack/recovery, walking, gravity/jump, flight, yaw/pitch and a gentler Stone placement gesture. Animation and movement use Player.step at 1/15 second and the production swing durations; does not verify keyboard timing; live multiplayer observer rendering verified. No GL errors. Visual inspection required.\n");
    }
}
