import dev.jayms.*;
import dev.jayms.net.*;
import dev.jayms.net.city.GameConfig;
import dev.jayms.net.model.*;
import dev.jayms.player.Player;
import dev.jayms.window.Window;
import dev.jayms.render.*;
import java.nio.file.*;
import java.util.*;
import org.joml.Vector3f;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;

/** Real keyboard movement and F10, edited geometry, async reload and loopback multiplayer. */
public class StreamingWorkflow {
    static Main game=new Main();static Path out;static int frame;static String mode;static boolean video;static int session;static long began,remoteSent;static float travelStart;
    static java.util.concurrent.Callable<MultiplayerClient> connectEditor;
    static int meshPeak;static long snapshotPeak;
    static float startX,flightX;static Vector3f previous;static double pathLength;static int tinyId;static long handle;static Protocol.Edit remoteEdit;static MultiplayerClient editor;
    static final StringBuilder samples=new StringBuilder("frame,stream_cpu_ms,render_cpu_ms,previous_video_capture_cpu_ms,pending,oldest_ms,loaded,heap_bytes,mesh_jobs,mesh_waiting,lighting_upload_bytes\n");
    static Object get(String n)throws Exception{var f=Main.class.getDeclaredField(n);f.setAccessible(true);return f.get(game);}
    static void set(String n,Object v)throws Exception{var f=Main.class.getDeclaredField(n);f.setAccessible(true);f.set(game,v);}
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static void input(String...keys)throws Exception{
        var args=new ArrayList<String>(List.of("xdotool"));args.addAll(List.of(keys));var p=new ProcessBuilder(args).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();check(p.waitFor()==0,"Native keyboard input");
    }
    static void key(String command,String key)throws Exception{input(command,"--window",Long.toString(org.lwjgl.glfw.GLFWNativeX11.glfwGetX11Window(handle)),key);}
    static void capture(String name)throws Exception{
        int w=(int)get("framebufferWidth"),h=(int)get("framebufferHeight");var pixels=java.nio.ByteBuffer.allocateDirect(w*h*3);glReadPixels(0,0,w,h,GL_RGB,GL_UNSIGNED_BYTE,pixels);
        var image=new java.awt.image.BufferedImage(w,h,java.awt.image.BufferedImage.TYPE_INT_RGB);
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){int i=(x+y*w)*3;image.setRGB(x,h-y-1,(pixels.get(i)&255)<<16|(pixels.get(i+1)&255)<<8|(pixels.get(i+2)&255));}
        javax.imageio.ImageIO.write(image,"png",out.resolve(name).toFile());
    }
    public static void main(String[] args)throws Exception{
        out=Path.of(args[0]);Files.createDirectories(out);began=System.nanoTime();mode=args[1];video=Boolean.parseBoolean(args[2]);
        GraphicsProfile.preset(GraphicsProfile.Preset.BALANCED).with(GraphicsProfile.Key.RESOLUTION,"640x480@60").save(dev.jayms.ui.Controls.directory().resolve("graphics.properties"));
        set("offlineSave",out.resolve("synthetic.dat"));set("seed",748291L);set("gameConfig",mode.equals("city")?GameConfig.cityGame():GameConfig.sandbox());
        if(mode.equals("switch")){var other=new LocalGame(out.resolve("other-world.dat"),999);other.save();}
        MultiplayerServer server=null;Thread serverThread=null;
        try {
            if(mode.equals("multiplayer")) {
                // Generated test-only credentials and TLS remain in target, never in evidence.
                var identity=SecureTransport.server(out.resolve("synthetic-tls"));var accounts=new AccountStore(out.resolve("synthetic-accounts.db"));
                char[] password=UUID.randomUUID().toString().toCharArray();accounts.register("streamgame",password.clone());accounts.register("streamedit",password.clone());
                server=new MultiplayerServer("127.0.0.1",0,out.resolve("server.dat"),accounts,identity.context(),748291);
                var active=server;serverThread=new Thread(()->{try{active.run();}catch(Exception ignored){}},"synthetic-server");serverThread.start();
                var client=new MultiplayerClient("127.0.0.1",server.port(),"streamgame",password.clone(),false,identity.fingerprint());
                final int port=server.port();final char[] editorPassword=password.clone();
                connectEditor=()->{try{return new MultiplayerClient("127.0.0.1",port,"streamedit",editorPassword,false,identity.fingerprint());}finally{Arrays.fill(editorPassword,'\0');}};
                Arrays.fill(password,'\0');set("network",client);
            }
            game.run(new Main.FrameObserver(){
                public void started(Main main)throws Exception{
                    game=main;session++;
                    if(mode.equals("multiplayer")){check(((MultiplayerClient)get("network")).connected(),"Game client stayed connected through startup");editor=connectEditor.call();connectEditor=null;remoteEdit=new Protocol.Edit((int)editor.spawn.x()+1,(int)editor.spawn.y()-1,(int)editor.spawn.z(),0);}
                    if(mode.equals("switch"))check(((World)get("world")).terrain().seed==(session==1?748291:999),"New world uses its own seed");
                    handle=((Window)get("window")).getHandle();glfwSwapInterval(0);input("windowfocus",Long.toString(org.lwjgl.glfw.GLFWNativeX11.glfwGetX11Window(handle)));
                    if(mode.equals("offline")) {
                        World w=(World)get("world");
                        for(int x=4;x<20;x++)for(int z=20;z<28;z++){w.setBlock(x,108,z,Blocks.STONE);for(int y=109;y<113;y++)w.setBlock(x,y,z,0);}
                        var tree=new SparseVoxelOctree(32);tree.set(15,0,15,0xff40b0e0);tinyId=w.models().register(new ModelDefinition("Streaming tiny model",tree),"synthetic").id();
                        for(int x=6;x<18;x++)for(int z=20;z<28;z++){w.apply(new Protocol.Edit(x,109,z,Blocks.LED,4,8,0,8).withColor(0x30a0ff));w.setBlock(x,110,z,tinyId);}
                        w.setBlock(11,110,26,Blocks.GLASS);w.setBlock(12,110,26,Blocks.WATER);
                        ((Player)get("player")).driveSeat(new Vector3f(5,109.01f,24),0);
                    }
                    startX=((Player)get("player")).position().x;travelStart=((Player)get("player")).position().z;
                    Files.writeString(out.resolve("progress.txt"),"Application startup completed\n");
                }
                public void beforeFrame(Main main)throws Exception{
                    ((dev.jayms.ui.ControlsMenu)get("menu")).open=false;
                    var capture=Main.class.getDeclaredMethod("setCaptured",boolean.class);capture.setAccessible(true);capture.invoke(game,true);
                    Player player=(Player)get("player");World w=(World)get("world");
                    if(mode.equals("offline")) {
                        if(frame==0)key("keydown","w");
                        if(frame==8){key("keyup","w");check(player.position().x>startX+.5f,"Actual W input walks on resident edited floor");}
                        if(frame==10){key("key","f");key("keydown","Shift_L");key("keydown","w");flightX=player.position().x;}
                        if(frame==14&&video)key("key","F10");
                        if(frame==24&&video)key("key","F10");
                        if(frame==30){key("keyup","w");key("keydown","s");check(player.flying(),"F toggles flight");check(player.position().x>flightX+5,"Sustained W plus sprint flight enters new terrain");}
                        if(frame==40){key("keyup","s");key("keyup","Shift_L");player.driveSeat(new Vector3f(2048,110,8),0);player.toggleFlight();}
                        if(frame==43){for(int y=-2;y<=7;y++)w.unloadChunk(new ChunkPos(0,y,1));player.driveSeat(new Vector3f(5,109.01f,24),0);}
                        if(frame==50)((LocalGame)get("local")).health=0;
                    } else if(mode.equals("travel")) {
                        if(frame==0){key("key","f");key("keydown","w");key("keydown","Shift_L");key("keydown","space");}
                        if(frame==20)key("keyup","space");
                        if(frame==60){key("keyup","w");key("keydown","s");}
                        if(frame==100){key("keyup","s");key("keydown","w");}
                        if(frame==130){key("keyup","w");key("keyup","Shift_L");((LocalGame)get("local")).health=0;}
                    } else if(mode.equals("multiplayer")) {
                        if(frame==2){remoteSent=System.nanoTime();check(editor.edit(remoteEdit,editor.spawn),"Remote client sends authoritative break while chunks are loading");}
                        editor.poll();
                        if(frame==12){Files.writeString(out.resolve("network-delivery.txt"),"Remote edit observed by frame12; upper-bound delivery ms="+(System.nanoTime()-remoteSent)/1e6+"\n");check(w.region(remoteEdit)==0,"Production Main consumed remote edit during load");w.unloadChunk(ChunkPos.fromBlock(remoteEdit.x(),remoteEdit.y(),remoteEdit.z()));}
                    }
                }
                public void afterFrame(Main main)throws Exception{
                    World w=(World)get("world");
                    var actual=((Player)get("player")).position();if(previous!=null)pathLength+=actual.distance(previous);previous=new Vector3f(actual);
                    var meshes=(DetailedMeshScheduler)get("detailedMeshes");meshPeak=Math.max(meshPeak,meshes.queued());snapshotPeak=Math.max(snapshotPeak,meshes.snapshotNanos);
                    check(w.terrainPending()<=9,"Bounded terrain completions");check(w.getLoadedChunks().size()<=1690,"Bounded resident chunks");check(glGetError()==GL_NO_ERROR,"No OpenGL error");
                    samples.append(frame+","+((long)get("streamNanos"))/1e6+","+((long)get("renderNanos"))/1e6+","+((long)get("videoNanos"))/1e6+","+w.terrainPending()+","+w.terrainOldestMillis()+","+w.getLoadedChunks().size()+","+(Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory())+","+meshes.queued()+","+meshes.waiting()+","+((RenderPipeline)get("rendering")).lightingUploadBytes+"\n");
                    if(frame==8)capture("streaming-"+mode+"-edited.png");
                    if(frame%10==0){Files.writeString(out.resolve("progress.txt"),"frame="+frame+"\n");Files.writeString(out.resolve("workflow.csv"),samples);}
                    if(mode.equals("switch")&&frame==15){set("requestedSave",out.resolve("other-world.dat"));glfwSetWindowShouldClose(handle,true);}
                    int end=mode.equals("offline")?65:mode.equals("multiplayer")?30:mode.equals("travel")?180:mode.equals("switch")?35:15;
                    // The llvmpipe upload budget can need more than50 settling
                    // frames after respawn. Observe readiness with a finite bound;
                    // retain the180-frame minimum travel/respawn workload.
                    if(mode.equals("travel")&&frame>=179) {
                        boolean ready=((RenderPipeline)get("rendering")).lightingReady();
                        end=ready?frame+1:450;
                        Files.writeString(out.resolve("lighting-settlement.txt"),
                                "frame="+frame+"; framesAfterRespawn="+(frame-130)+"; lightingReady="+ready+"\n");
                    }
                    if(++frame==end){
                        if(mode.equals("offline")){check(w.isLoaded(8,109,26),"Reloaded edited chunk resident");check(w.getBlock(8,109,26)==Blocks.PARTIAL,"Fractional LED survived unload and regeneration");check(w.cell(8,109,26).get(8,0,8)==0xfe30a0ff,"Exact RGB fractional edit replayed");check(w.getBlock(8,110,27)==tinyId,"Tiny model retained after reload");check(((LocalGame)get("local")).health==20,"Ordinary respawn restores health");check(w.nearbyReady(actual.x,actual.z,0),"Playable terrain is ready after ordinary respawn");}
                        if(mode.equals("switch"))check(session==2&&w.terrain().seed==999,"Running application switched worlds and stopped old worker");
                        if(mode.equals("travel")){check((System.nanoTime()-began)>120_000_000_000L,"Multi-minute actual flight/reversal/respawn run");check(((LocalGame)get("local")).health==20,"Respawn restored health");check(w.nearbyReady(((Player)get("player")).position().x,((Player)get("player")).position().z,0),"Streaming caught up around respawn");var lighting=RenderPipeline.class.getDeclaredField("hasIrradiance");lighting.setAccessible(true);check(lighting.getBoolean(get("rendering")),"Staged lighting became usable after settling at respawn");}
                        if(mode.equals("multiplayer")){check(w.isLoaded(remoteEdit.x(),remoteEdit.y(),remoteEdit.z()),"Remote edited chunk reloaded");check(w.region(remoteEdit)==0,"Remote edit survives reload");check(((MultiplayerClient)get("network")).connected(),"Network stayed connected while streaming");}
                        check(w.getLoadedChunks().values().stream().anyMatch(c->c.getMesh()!=null),"GPU mesh replacements became usable");
                        check(meshPeak>0&&snapshotPeak>0,"Detailed meshing made progress while streaming");
                        capture("streaming-"+mode+"-final.png");Files.writeString(out.resolve("workflow.csv"),samples);
                        Files.writeString(out.resolve("result.txt"),"Playtest: PASS. Production Main "+mode+"; inherited role display; Mesa software renderer; synthetic profile; "+frame+" frames. Requested input/edit/reload checks, queue/residency bounds and GL checks passed. Video="+video+".\n");
                        glfwSetWindowShouldClose(handle,true);
                    }
                }
            });
        }catch(Throwable e){Files.writeString(out.resolve("failure.txt"),e.getClass().getSimpleName()+": "+e.getMessage()+"\n");throw e;}
        finally{if(handle!=0){try{key("keyup","w");key("keyup","s");key("keyup","Shift_L");key("keyup","space");}catch(Exception ignored){}}if(editor!=null)editor.close();if(server!=null)server.close();if(serverThread!=null)serverThread.join(5000);}
    }
}
