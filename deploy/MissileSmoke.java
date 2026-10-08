import dev.jayms.*;
import dev.jayms.net.*;
import dev.jayms.net.city.*;
import dev.jayms.physics.*;
import dev.jayms.player.*;
import dev.jayms.ui.*;
import dev.jayms.window.Window;
import org.joml.Vector3f;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;

/** Actual Main + X11 keys, synthetic city and isolated save. No account or network. */
public class MissileSmoke {
    static Main game=new Main(); static Path out; static volatile long window,frames; static volatile boolean active,menu,cheat;
    static volatile int impacts,shards,removed,buildings,smoke,peakShards; static volatile float age,smokeRise,shardTravel; static List<Vector3f> initialShards; static float initialSmokeY; static volatile String notice,capture,captured; static volatile Throwable failure;
    static volatile boolean save, saved; static int grade=36;
    static Object get(String name)throws Exception{var f=Main.class.getDeclaredField(name);f.setAccessible(true);return f.get(game);}
    static void set(String name,Object value)throws Exception{var f=Main.class.getDeclaredField(name);f.setAccessible(true);f.set(game,value);}
    static void require(boolean ok,String msg){if(!ok)throw new AssertionError(msg);}
    static void waitFor(java.util.function.BooleanSupplier condition,String msg)throws Exception{long end=System.currentTimeMillis()+90000;while(!condition.getAsBoolean()&&System.currentTimeMillis()<end)Thread.sleep(25);require(condition.getAsBoolean(),msg+"; notice="+notice);}
    static void x(String...args)throws Exception{if(args[0].equals("key")){var down=args.clone();down[0]="keydown";x(down);var up=args.clone();up[0]="keyup";x(up);return;}var cmd=new ArrayList<String>(List.of("xdotool"));cmd.addAll(List.of(args));var p=new ProcessBuilder(cmd).redirectError(ProcessBuilder.Redirect.DISCARD).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();require(p.waitFor()==0,"X11 command");long f=frames+1;waitFor(()->frames>=f,"Input frames");}
    static void shot(String name)throws Exception{capture=name;waitFor(()->name.equals(captured),"Capture "+name);}
    static void fixture()throws Exception{
        World w=(World)get("world");LocalGame l=(LocalGame)get("local");w.stream(48,48,81);
        for(int x=36;x<=76;x++)for(int z=36;z<=64;z++){
            for(int y=grade-8;y<=90;y++)w.apply(new Protocol.Edit(x,y,z,y<=grade?Blocks.DIRT:0));
        }
        var b=new CityFrame.Building(900,0,0,44,grade+1,44,8,0);
        var far=new CityFrame.Building(901,0,0,68,grade+1,44,8,0);
        for(var building:List.of(b,far))for(var e:StructureBlueprint.generate(0,0,building.x(),building.y(),building.z()))w.apply(e);
        var seed=l.city.frame();
        var ground=new CitySimulation.Ground(){public int type(int x,int y,int z){return w.sample(x,y,z);}public boolean occupied(int x,int y,int z,int width,int depth){return false;}public void apply(List<Protocol.Edit> batch){for(var e:batch){w.apply(e);WorldVoxels.remember(l.edits,e);}}};
        l.city=new CitySimulation(seed.config(),ground,w.terrain(),new CityFrame(seed.config(),0,List.of(new CityFrame.Road(44,43,grade)),List.of(),List.of(b,far),seed.citizens(),seed.horses(),seed.economy()));
        ((Player)get("player")).driveSeat(new Vector3f(8,grade+1,24),0);
        var camera=(IsometricCamera)get("overview");camera.focus(48,48,grade+14);camera.zoom(4);
        var settings=((dev.jayms.render.RenderPipeline)get("rendering")).settings;
        settings.renderScale=.5f; settings.reflections=false; settings.screenGi=false; settings.volumetrics=false; settings.clouds=false; settings.ao=false; settings.contactShadows=false;
    }
    public static void main(String[]args)throws Exception{
        out=Path.of(args[0]);Files.createDirectories(out);set("offlineSave",out.resolve("synthetic-city.dat"));set("gameConfig",new GameConfig(true,false,1200,10));glfwInitHint(GLFW_PLATFORM,GLFW_PLATFORM_X11);
        var driver=new Thread(()->{try{
            waitFor(()->window!=0,"Start");String id=Long.toString(org.lwjgl.glfw.GLFWNativeX11.glfwGetX11Window(window));x("windowfocus",id);x("windowsize",id,"960","720");if(menu)x("key","--window",id,"Escape");waitFor(()->!menu,"Close menu");
            x("key","--window",id,"Delete");require(!active&&impacts==0&&notice.contains("cheat"),"Non-cheat missile rejected");shot("city-missile-before.png");
            x("key","--window",id,"F4");require(cheat,"F4 enters cheat mode");x("key","--window",id,"Insert");require(notice.contains("10,000"),"Budget cheat regression");
            x("key","--window",id,"F10");x("key","--window",id,"Delete");require(active,"Delete launches missile");
            x("key","--window",id,"Delete");require(notice.contains("Wait"),"Repeated drop blocked while active");
            shot("city-missile-falling.png");
            waitFor(()->impacts==1,"Impact");require(shards>0&&shards<=192&&removed>0,"Bounded physical shards");require(buildings==1,"Hit building removed; far building kept");require(smoke==96,"Smoke spawned");shot("city-missile-explosion.png");
            waitFor(()->age>=1.2f,"Rising smoke and debris motion");require(smokeRise>1&&shardTravel>1,"Smoke rises and voxels move over one unit");shot("city-missile-debris.png");x("key","--window",id,"F10");
            waitFor(()->age>8&&smoke==0&&shards==0,"Smoke and debris cleanup");save=true;waitFor(()->saved,"Save");var loaded=CitySimulation.load(out.resolve("synthetic-city.dat.city"));require(loaded.buildings().size()==1&&loaded.buildings().get(0).id()==901,"Demolition persists");var local=new LocalGame(out.resolve("synthetic-city.dat"),1);require(local.edits.values().stream().anyMatch(e->e.type()==0),"Crater edits persist");
            x("key","--window",id,"F4");require(!cheat,"F4 leaves cheat mode");x("key","--window",id,"Delete");require(impacts==1&&!active,"Disabled missile rejected after impact");
            Files.writeString(out.resolve("results.json"),"{\n\"Playtest\":\"Production Main offline City Builder; Linux X11 inherited DISPLAY/XAUTHORITY, Mesa software GL, isolated synthetic profile. Browser playtesting does not apply to native LWJGL.\",\n\"steps\":[\"Delete without cheats rejected\",\"F4 enables cheats; Insert budget cheat still works\",\"F10 starts recorder; Delete drops falling missile at city view centre\",\"Second Delete rejected while missile falls\",\"Impact removes hit building and terrain; keeps far building\",\"Capture explosion, 96 smoke particles and moving debris; F10 stops recorder\",\"Save/reload preserves demolition and AIR edits\",\"F4 disables cheats; Delete rejected\"],\n\"expected\":\"One impact, 1..192 physical shards, 96 rising smoke puffs, cleanup after 8 seconds, persistent destruction, unaffected distant building and budget cheat\",\n\"observed\":\"All assertions passed; cleanupShards="+shards+", peakShards="+peakShards+", smokeRise="+smokeRise+", shardTravel="+shardTravel+", removed="+removed+"\"\n}\n");
        }catch(Throwable e){failure=e;try{Files.writeString(out.resolve("failure.txt"),e.getClass().getSimpleName()+": "+e.getMessage());}catch(Exception ignored){}}finally{if(window!=0)glfwSetWindowShouldClose(window,true);}},"missile-input");driver.start();
        game.run(new Main.FrameObserver(){public void started(Main main)throws Exception{fixture();window=((Window)get("window")).getHandle();}
            public void beforeFrame(Main main)throws Exception{if(save&&!saved){((LocalGame)get("local")).save();saved=true;}if(capture!=null)for(var chunk:((World)get("world")).getLoadedChunks().values())chunk.checkMesh();}
            public void afterFrame(Main main)throws Exception{var m=(CityMissiles)get("missiles");active=m.active();impacts=m.impacts;shards=m.debris().size();smoke=m.smoke().size();age=m.effectAge();
                if(impacts==1&&initialShards==null){initialShards=m.debris().stream().map(b->new Vector3f(b.position)).toList();peakShards=shards;initialSmokeY=m.smoke().get(50).position().y;}
                if(initialShards!=null&&!m.debris().isEmpty()){
                    var bodies=m.debris();for(int i=0;i<bodies.size();i++)shardTravel=Math.max(shardTravel,bodies.get(i).position.distance(initialShards.get(i)));
                    smokeRise=Math.max(smokeRise,m.smoke().get(50).position().y-initialSmokeY);
                }
                removed=m.removed;buildings=((LocalGame)get("local")).city.frame().buildings().size();notice=(String)get("notice");menu=((ControlsMenu)get("menu")).open;cheat=(boolean)get("cheatMode");require(glGetError()==GL_NO_ERROR,"GL clean");if(capture!=null&&!capture.equals(captured)){int width=(int)get("framebufferWidth"),height=(int)get("framebufferHeight");var bytes=java.nio.ByteBuffer.allocateDirect(width*height*3);glReadPixels(0,0,width,height,GL_RGB,GL_UNSIGNED_BYTE,bytes);var image=new java.awt.image.BufferedImage(width,height,java.awt.image.BufferedImage.TYPE_INT_RGB);for(int y=0;y<height;y++)for(int x=0;x<width;x++){int k=(y*width+x)*3;image.setRGB(x,height-y-1,(bytes.get(k)&255)<<16|(bytes.get(k+1)&255)<<8|(bytes.get(k+2)&255));}javax.imageio.ImageIO.write(image,"png",out.resolve(capture).toFile());captured=capture;}frames++;}
        });driver.join();if(failure!=null)throw new AssertionError("Missile playtest failed",failure);
    }
}
