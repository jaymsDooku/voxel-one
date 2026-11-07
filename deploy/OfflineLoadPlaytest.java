import dev.jayms.*;
import dev.jayms.net.*;
import dev.jayms.net.city.GameConfig;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import static org.lwjgl.glfw.GLFW.*;

/** Actual Main startup: real X11 Escape, synthetic source, no private profile. */
public final class OfflineLoadPlaytest {
    static Main game=new Main();static boolean observed,started,denseSuccess;static String renderer;
    static Object get(String name)throws Exception {var f=Main.class.getDeclaredField(name);f.setAccessible(true);return f.get(game);}
    static void set(String name,Object value)throws Exception {var f=Main.class.getDeclaredField(name);f.setAccessible(true);f.set(game,value);}
    static void require(boolean value,String label){if(!value)throw new AssertionError(label);}
    static String x(String... args)throws Exception {
        var command=new ArrayList<String>();command.add("xdotool");command.addAll(List.of(args));
        var process=new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        String result=new String(process.getInputStream().readAllBytes()).trim();require(process.waitFor()==0,"X11 input");return result;
    }
    static Protocol.Edit edit(int i){
        if(denseSuccess&&i<4096)return new Protocol.Edit(-512,180,1024,
                Blocks.piece(i%2==0?Blocks.BRICKS:Blocks.GLASS,4),4,i%16,i/256,i/16%16);
        return new Protocol.Edit(i%1024-512,180,i/1024+1024,Blocks.STONE);
    }
    static byte[] hash(Path path)throws Exception {
        var digest=MessageDigest.getInstance("SHA-256");
        try(var in=new DigestInputStream(Files.newInputStream(path),digest)){in.transferTo(OutputStream.nullOutputStream());}return digest.digest();
    }
    public static void main(String[] args)throws Exception {
        if(args.length!=3||!Set.of("world","replay","success").contains(args[0]))throw new IllegalArgumentException("world/replay/success, fresh fixture path, evidence directory");
        String mode=args[0];Path source=Path.of(args[1]),output=Path.of(args[2]);require(!Files.exists(source),"Fresh source fixture");Files.createDirectories(source.getParent());Files.createDirectories(output);
        denseSuccess=mode.equals("success");
        int records=mode.equals("world")?200000:mode.equals("replay")?5000:4097;
        var template=new LocalGame(source,42);
        try(var out=new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(source),65536))) {
            boolean legacy=mode.equals("replay");
            out.writeInt(legacy?3:8);out.writeLong(template.seed);
            if(!legacy){out.writeInt(template.generatorVersion);template.models.write(out);}
            template.inventory.write(out);out.writeByte(20);out.writeInt(records);
            for(int i=0;i<records;i++) {
                var edit=edit(i);
                if(legacy){out.writeInt(edit.x());out.writeInt(edit.y());out.writeInt(edit.z());out.writeInt(edit.type());}
                else edit.write(out);
            }
            out.writeInt(0);
        }
        byte[] before=hash(source);set("offlineSave",source.toAbsolutePath());set("gameConfig",GameConfig.sandbox());set("seed",42L);
        glfwInitHint(GLFW_PLATFORM,GLFW_PLATFORM_X11);
        try {
            game.run(new Main.FrameObserver(){
                public void loading(Main main,String stage)throws Exception {
                    if(mode.equals("success")||observed||!mode.equals(stage))return;
                    observed=true;renderer=org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_RENDERER);
                    var window=(dev.jayms.window.Window)get("window");
                    // Present the actual loading overlay before capturing this test boundary.
                    window.swapBuffers();window.pollEvents();
                    String xid=x("search","--onlyvisible","--pid",Long.toString(ProcessHandle.current().pid()),"--name","^Voxel One").lines().findFirst().orElseThrow();
                    x("windowfocus",xid);
                    var capture=new ProcessBuilder("import","-window",xid,output.resolve("structure-startup-"+mode+".png").toString()).redirectError(ProcessBuilder.Redirect.DISCARD).start();require(capture.waitFor()==0,"Loading screenshot");
                    x("keydown","Escape");window.pollEvents();
                }
                public void started(Main main)throws Exception {
                    started=true;require(mode.equals("success"),"Cancelled startup reached play");
                    renderer=org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_RENDERER);
                    var local=(LocalGame)get("local");var world=(World)get("world");
                    require(local.edits.size()==records,"Complete history published");
                    for(int i=0;i<records;i++)require(world.region(edit(i))==edit(i).type(),"Replay geometry "+i);
                    glfwSetWindowShouldClose(((dev.jayms.window.Window)get("window")).getHandle(),true);
                }
            });
            require(mode.equals("success")&&started,"Expected cancellation");
        }catch(CancellationException cancelled){require(!mode.equals("success")&&observed&&!started,"Cancellation before play");}
        finally{if(observed)x("keyup","Escape");}
        require(Arrays.equals(before,hash(source)),"Source bytes unchanged");
        if(mode.equals("world"))require(get("local")==null&&get("world")==null,"No partial load published");
        if(mode.equals("replay"))require(get("local")!=null&&get("world")==null,"Private geometry never published on cancellation");
        if(!mode.equals("success"))require(!(Boolean)get("sceneReady"),"No ready scene on cancellation");
        String json="{\"status\":\"passed\",\"mode\":\""+mode+"\",\"records\":"+records+",\"source_unchanged\":true,\"renderer\":\""+renderer+"\",\"qualification\":false}";
        Files.writeString(output.resolve("structure-startup-"+mode+".json"),json+"\n");System.out.println("Playtest: "+json);
    }
}
