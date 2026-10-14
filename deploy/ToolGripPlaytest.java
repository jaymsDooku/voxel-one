import dev.jayms.*;
import dev.jayms.net.*;
import dev.jayms.net.city.GameConfig;
import dev.jayms.ui.*;
import java.nio.file.*;
import static org.lwjgl.glfw.GLFW.*;

/** Real renderer and input, isolated synthetic inventory; shares the sandbox fixture driver. */
public class ToolGripPlaytest extends SandboxPlaytest {
    static void equip(int type) throws Exception {
        onFrame(() -> { var inv=new Inventory(); if(type!=0)inv.add(type,8); local().inventory=inv;
            ((InventoryHud)get("inventoryHud")).selected=0; return null; });
        await(() -> player().heldItem()==type,"Equipped "+type);
    }
    public static void main(String[] args) throws Exception {
        Path out=Path.of(args[0]); String phase=args[1]; Files.createDirectories(out);
        glfwInitHint(GLFW_PLATFORM, GLFW_PLATFORM_X11);
        set("offlineSave",Path.of("target/tool-profile/world.dat").toAbsolutePath());
        set("gameConfig",GameConfig.sandbox());
        Thread driver=new Thread(() -> {
            long handle=0;
            try {
                await(() -> get("player")!=null,"Engine ready"); Thread.sleep(1500);
                handle=((dev.jayms.window.Window)get("window")).getHandle();
                String id=Long.toString(org.lwjgl.glfw.GLFWNativeX11.glfwGetX11Window(handle));
                x("windowfocus",id); Thread.sleep(1000); if(((ControlsMenu)get("menu")).open)x("key","Escape");
                onFrame(() -> { org.lwjgl.glfw.GLFW.glfwSetWindowFocusCallback(((dev.jayms.window.Window)get("window")).getHandle(),null); ((ControlsMenu)get("menu")).open=false;
                    var m=Main.class.getDeclaredMethod("setCaptured",boolean.class);m.setAccessible(true);m.invoke(game,true);return null; });
                onFrame(() -> { var w=(World)get("world"); fixtureY=w.terrain().column(8,24).height()+2;
                    for(int dx=3;dx<14;dx++)for(int dz=18;dz<30;dz++) {
                        w.apply(new Protocol.Edit(dx,fixtureY,dz,Blocks.STONE));
                        for(int dy=1;dy<6;dy++)w.apply(new Protocol.Edit(dx,fixtureY+dy,dz,0)); }
                    var p=new dev.jayms.player.Player(new org.joml.Vector3f(8.5f,fixtureY+1,24.5f),-90,0,(Camera)get("camera"));
                    p.toggleFlight(); set("player",p); warmFixture(); return null; });
                int[] tools=phase.equals("before")?new int[]{191,192}:new int[]{191,192};
                for(int tool:tools) {
                    equip(tool);
                    for(int view=0;view<3;view++) {
                        require(player().cameraView().ordinal()==view,"Capture view "+view);
                        capture(id,out.resolve("tool-grip-verified-"+phase+"-"+tool+"-view"+view+".png"));
                        onFrame(() -> {player().toggleView();return null;});
                        final int expected=(view+1)%3;
                        await(() -> player().cameraView().ordinal()==expected,"F5 view "+expected);
                    }
                }
                if(!phase.equals("before")) {
                    equip(195); x("key","--window",id,"F10"); Thread.sleep(1000); onFrame(() -> {player().swing();return null;});
                    x("keydown","w"); Thread.sleep(700); x("keyup","w");
                    x("keydown","space"); Thread.sleep(300); x("keyup","space");
                    onFrame(() -> {player().toggleView();return null;}); onFrame(() -> {player().swing();return null;}); onFrame(() -> {player().toggleView();return null;}); Thread.sleep(1000);
                    x("key","--window",id,"F10"); onFrame(() -> {player().toggleView();return null;});
                    for(int item:new int[]{0,Blocks.STONE}) {
                        equip(item); capture(id,out.resolve("tool-grip-generic-"+item+".png"));
                    }
                }
                if (!phase.equals("before")) {
                    equip(Blocks.STONE);
                    onFrame(() -> {var p=new dev.jayms.player.Player(new org.joml.Vector3f(8.5f,fixtureY+1,24.5f),-90,0,(Camera)get("camera"));p.toggleFlight();set("player",p);
                        ((World)get("world")).apply(new Protocol.Edit(8,fixtureY+2,21,Blocks.STONE));warmFixture();return null;});
                    onFrame(() -> {var m=Main.class.getDeclaredMethod("interact",boolean.class);m.setAccessible(true);m.invoke(game,true);return null;});
                    await(() -> local().inventory.countType(Blocks.STONE)==7,"Place consumes one block");
                    require(((World)get("world")).sample(8,fixtureY+2,22)==Blocks.STONE,"Block placed on aimed face");
                    capture(id,out.resolve("tool-grip-placement.png"));
                }
                Files.writeString(out.resolve("tool-grip-"+phase+"-result.txt"),"Playtest: executed Linux X11 software OpenGL isolated Sandbox profile. Equipped tools "+java.util.Arrays.toString(tools)+"; captured first-person, rear and front F5 views. "+(phase.equals("before")?"Baseline captures.":"F10: idle, harness-triggered production attack/recovery, W movement, flight rise, rear and front views. Generic empty and Stone captured. Production placement callback consumes one Stone and places it on the aimed face. Visual inspection required.")+"\n");
            }catch(Throwable e){failure=e; try{Files.writeString(out.resolve("tool-grip-"+phase+"-result.txt"),"FAIL "+e.getClass().getSimpleName()+": "+e.getMessage());}catch(Exception ignored){}}
            finally {if(handle!=0)glfwSetWindowShouldClose(handle,true);}
        }); driver.setDaemon(true); driver.start();
        game.run(new Main.FrameObserver(){ public void beforeFrame(Main a){for(var j=jobs.poll();j!=null;j=jobs.poll())j.run();} public void afterFrame(Main a){frames.incrementAndGet();}});
        driver.join(1000); if(failure!=null)throw new AssertionError("Tool grip playtest failed");
    }
}
