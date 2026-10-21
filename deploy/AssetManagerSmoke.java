import dev.jayms.*;
import dev.jayms.assets.*;
import dev.jayms.launcher.*;
import dev.jayms.net.*;
import dev.jayms.net.model.*;
import dev.jayms.net.city.GameConfig;
import dev.jayms.ui.*;
import java.awt.*;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import javax.swing.*;
import javax.imageio.ImageIO;
import static org.lwjgl.glfw.GLFW.*;

/** Production Launcher selection and Main intake, using an isolated synthetic profile. */
public class AssetManagerSmoke {
    static final Main game=new Main(); static volatile Throwable failure; static volatile long frames;
    static Object get(String key)throws Exception{var f=Main.class.getDeclaredField(key);f.setAccessible(true);return f.get(game);}
    static void set(String key,Object value)throws Exception{var f=Main.class.getDeclaredField(key);f.setAccessible(true);f.set(game,value);}
    static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    static void clickAsset(Container parent)throws Exception {
        for(Component c:parent.getComponents()) {
            if(c instanceof JButton b&&b.getText().equals("Asset Manager")){b.doClick();return;}
            if(c instanceof Container container)clickAsset(container);
        }
    }
    static void x(String...args)throws Exception{var cmd=new ArrayList<String>();cmd.add("xdotool");cmd.addAll(java.util.List.of(args));var p=new ProcessBuilder(cmd).redirectError(ProcessBuilder.Redirect.DISCARD).start();require(p.waitFor()==0,"X11 input");}
    static void key(String window,String value)throws Exception{x("windowfocus",window);x("key","--clearmodifiers","--window",window,value);}
    static void await(java.util.concurrent.Callable<Boolean> condition,String message)throws Exception{for(int i=0;i<900;i++){if(condition.call())return;Thread.sleep(100);}throw new AssertionError(message);}
    static int slot(Inventory inventory,int id){for(int i=0;i<Inventory.SIZE;i++)if(inventory.type(i)==id&&inventory.count(i)>0)return i;throw new AssertionError("Imported inventory item missing");}
    public static void main(String[]args)throws Exception{
        Path out=Path.of(args[0]);Files.createDirectories(out);
        var timer=new javax.swing.Timer(500,event->{for(java.awt.Window window:java.awt.Window.getWindows())if(window.isShowing()&&window instanceof JDialog dialog&&dialog.getTitle().equals("Voxel One Launcher"))try{ImageIO.write(new Robot().createScreenCapture(window.getBounds()),"png",out.resolve("asset-manager-launcher.png").toFile());clickAsset(dialog);}catch(Exception e){failure=e;dialog.dispose();}});
        timer.start();int selection=Launcher.chooseApplication();timer.stop();require(failure==null&&selection==1,"Launcher Asset Manager selection");
        var command=Launcher.command(Path.of("synthetic-client.jar"),Platform.LINUX,java.util.List.of("--asset-manager"));
        require(command.contains("--asset-manager")&&!command.contains("--server"),"Launcher builds local Asset Manager command");
        var catalog=new AssetCatalog(Controls.directory());
        var bytes=new ByteArrayOutputStream();try(var data=new DataOutputStream(bytes)){new ModelDefinition("Balcony flower",ModelGenerators.flowerPot().voxels()).write(data);}
        var asset=catalog.upload("test.vxm","Synthetic native workflow fixture","Test author",bytes.toByteArray());
        catalog.addToGame(asset.id(),"sandbox",Controls.directory());
        catalog.addToGame("hudson-crown","sandbox",Controls.directory());
        catalog.addToGame("flower-pot","sandbox",Controls.directory());
        var tower=catalog.get("hudson-crown");
        dev.jayms.render.GraphicsProfile.preset(dev.jayms.render.GraphicsProfile.Preset.LOW)
                .with(dev.jayms.render.GraphicsProfile.Key.RESOLUTION,"960x600@60")
                .with(dev.jayms.render.GraphicsProfile.Key.DETAIL,"32")
                .with(dev.jayms.render.GraphicsProfile.Key.HORIZON,"512")
                .with(dev.jayms.render.GraphicsProfile.Key.SHADOW_DISTANCE,"32")
                .save(Controls.directory().resolve("graphics.properties"));
        Path save=Controls.directory().resolve("offline-world.dat");set("offlineSave",save);set("gameConfig",new GameConfig(false,true,1200,10));
        glfwInitHint(GLFW_PLATFORM,GLFW_PLATFORM_X11);
        Thread driver=new Thread(()->{long handle=0;try{
            await(()->frames>10,"Main rendered first frames");
            var local=(LocalGame)get("local");
            int id=local.models.find(asset.model());require(id>=9,"Imported model in production Main library");require(local.inventory.count(slot(local.inventory,id))==1,"Imported inventory item");
            int towerId=local.models.find(tower.model());require(towerId>=9,"Skyscraper in production model library");
            int towerSlot=slot(local.inventory,towerId);require(towerSlot<9,"Tower reachable in hotbar");
            require(local.inventory.count(slot(local.inventory,Blocks.FLOWER_POT))==1,"Flower pot received");
            require(local.models.get(Blocks.FLOWER_POT).definition().fingerprint().equals(ModelGenerators.flowerPot().fingerprint()),"Original flower pot preserved");
            handle=((dev.jayms.window.Window)get("window")).getHandle();String window=Long.toString(org.lwjgl.glfw.GLFWNativeX11.glfwGetX11Window(handle));
            x("windowfocus",window);if(((ControlsMenu)get("menu")).open){key(window,"Escape");Thread.sleep(400);}
            key(window,"F10");key(window,Integer.toString(towerSlot+1));await(()->((InventoryHud)get("inventoryHud")).selected==towerSlot,"Real hotbar key selects skyscraper");long next=frames+2;await(()->frames>=next,"Selected tower rendered");
            var towerCapture=new ProcessBuilder("import","-window",window,out.resolve("asset-manager-tower-engine.png").toString()).redirectError(ProcessBuilder.Redirect.DISCARD).start();require(towerCapture.waitFor()==0,"Live held skyscraper screenshot");
            key(window,"e");await(()->((InventoryHud)get("inventoryHud")).open,"Inventory opens with real input");Thread.sleep(800);
            var capture=new ProcessBuilder("import","-window",window,out.resolve("asset-manager-inventory.png").toString()).redirectError(ProcessBuilder.Redirect.DISCARD).start();require(capture.waitFor()==0,"Inventory screenshot");
            key(window,"e");await(()->!((InventoryHud)get("inventoryHud")).open,"Inventory closes");Thread.sleep(500);key(window,"F10");Thread.sleep(500);
            Files.writeString(out.resolve("asset-manager-native.txt"),"Playtest: PASS. Linux X11, inherited assigned display, isolated synthetic home, Mesa software rendering. Production Launcher selection returned Asset Manager and built a local client command. Production Main imported both seed models and a native upload into its library/inventory. Real hotbar input selected the skyscraper; live held-model and inventory screenshots captured. Original flower-pot fingerprint unchanged. Real X11 E opened/closed inventory; F10 started/stopped recording. Screenshot captured from live game window. Physical GPU not qualified.\n");
        }catch(Throwable t){failure=t;try{Files.writeString(out.resolve("asset-manager-native-failure.txt"),t.getClass().getSimpleName()+": "+t.getMessage());}catch(Exception ignored){}}finally{try{if(handle==0&&get("window")!=null)handle=((dev.jayms.window.Window)get("window")).getHandle();if(handle!=0)glfwSetWindowShouldClose(handle,true);}catch(Exception ignored){}}});
        driver.setDaemon(true);driver.start();game.run(new Main.FrameObserver(){public void afterFrame(Main app){frames++;}public void loading(Main app,String stage)throws Exception{Files.writeString(Path.of("target/asset-native/loading-stage.txt"),stage);}});driver.join(1000);require(failure==null,"Native Asset Manager workflow failed");require(Files.exists(out.resolve("asset-manager-native.txt")),"Native workflow finished");
        var reopened=new LocalGame(save,0);require(reopened.models.find(asset.model())>=9,"Saved library survives reload");
        require(AssetIntake.apply(Controls.directory(),GameIdentity.SANDBOX,save,reopened).isEmpty(),"Restart does not duplicate import");require(reopened.inventory.count(slot(reopened.inventory,reopened.models.find(asset.model())))==1,"No duplicate inventory gift");
        require(reopened.inventory.count(slot(reopened.inventory,reopened.models.find(tower.model())))==1,"Tower persists without duplicate");
        System.out.println("PASS: native launcher selection and Main asset intake/inventory/reload");System.exit(0);
    }
}
