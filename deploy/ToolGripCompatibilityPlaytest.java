import dev.jayms.*;
import dev.jayms.net.*;
import dev.jayms.net.city.GameConfig;
import dev.jayms.ui.*;
import java.nio.file.*;
import static org.lwjgl.glfw.GLFW.*;

/** City walking compatibility and real Q drop/pickup, with a synthetic profile. */
public class ToolGripCompatibilityPlaytest extends SandboxPlaytest {
    public static void main(String[] args) throws Exception {
        Path out=Path.of(args[0]);Files.createDirectories(out);
        set("offlineSave",Path.of("target/tool-city-profile/world.dat").toAbsolutePath());
        set("gameConfig",GameConfig.cityGame());
        Thread driver=new Thread(()->{
            long h=0;
            try {
                await(()->get("player")!=null,"Engine ready");
                h=((dev.jayms.window.Window)get("window")).getHandle();
                String id=Long.toString(org.lwjgl.glfw.GLFWNativeX11.glfwGetX11Window(h));
                x("windowfocus",id);
                onFrame(()->{
                    require(local().city.frame().config().city(),"City mode retained");
                    set("isometric",false);((ControlsMenu)get("menu")).open=false;
                    var m=Main.class.getDeclaredMethod("setCaptured",boolean.class);m.setAccessible(true);m.invoke(game,true);
                    player().toggleFlight();
                    local().inventory=new Inventory();local().inventory.add(Items.WOODEN_SWORD,1);
                    ((InventoryHud)get("inventoryHud")).selected=0;return null;
                });
                await(()->player().heldItem()==Items.WOODEN_SWORD,"Sword equipped in City walking");
                capture(id,out.resolve("city-sword.png"));
                x("key","q");
                await(()->local().inventory.countType(Items.WOODEN_SWORD)==0&&!local().drops.isEmpty(),"Q drops sword");
                await(()->player().heldItem()==0,"Empty hand after drop");
                capture(id,out.resolve("city-dropped-sword.png"));
                onFrame(()->{var d=local().drops.values().stream().filter(v->v.type()==Items.WOODEN_SWORD).findFirst().orElseThrow();
                    player().driveSeat(new org.joml.Vector3f(d.x(),d.y(),d.z()),-90);return null;});
                await(()->local().inventory.countType(Items.WOODEN_SWORD)==1,"Walk into dropped sword picks it up");
                await(()->player().heldItem()==Items.WOODEN_SWORD,"Sword re-equipped after pickup");
                capture(id,out.resolve("city-picked-up-sword.png"));
                Files.writeString(out.resolve("compatibility.txt"),"Playtest: PASS. Linux X11 software OpenGL; isolated synthetic City profile. City config retained, first-person walking view, sword equipped. Real Q through production input drops sword, clears held item, and renders empty hand plus world drop. Harness moves player to drop; production tick picks up and re-equips the same sword. Expected counts 1 -> 0 -> 1 observed. Camera and inventory remain usable. Teleport to drop is harness-driven, not a keyboard movement claim.\n");
            }catch(Throwable e){failure=e;try{Files.writeString(out.resolve("compatibility.txt"),"FAIL: "+e.getClass().getSimpleName()+": "+e.getMessage());}catch(Exception ignored){}}
            finally{if(h!=0)glfwSetWindowShouldClose(h,true);}
        });driver.setDaemon(true);driver.start();
        game.run(new Main.FrameObserver(){public void beforeFrame(Main app){for(var j=jobs.poll();j!=null;j=jobs.poll())j.run();}public void afterFrame(Main app){frames.incrementAndGet();}});
        driver.join(1000);require(failure==null&&Files.exists(out.resolve("compatibility.txt")),"City workflow completed");
    }
}
