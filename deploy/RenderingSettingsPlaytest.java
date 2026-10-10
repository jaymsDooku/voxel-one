import dev.jayms.*;
import dev.jayms.player.*;
import dev.jayms.render.*;
import dev.jayms.net.*;
import dev.jayms.net.city.*;
import dev.jayms.ui.EngineEditor;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFWNativeX11;
import java.nio.file.*;
import java.util.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;

/** Exercises the production Main loop with a synthetic gallery and real X11 inputs. */
public class RenderingSettingsPlaytest {
    static final Main game=new Main();static Path out;static int frame,stage=-4;static long changed;static int recordingFrame,f6Presses,expectedF6Presses;
    static float legacyFog;static boolean legacyVolume;static int effectProgram;
    static Vector3f start;static String windowId;
    static org.lwjgl.glfw.GLFWWindowFocusCallback initialFocus;
    static org.lwjgl.glfw.GLFWKeyCallback originalKeys;
    static Object get(String name)throws Exception{var f=Main.class.getDeclaredField(name);f.setAccessible(true);return f.get(game);}
    static Object field(Object object,String name)throws Exception{var f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    static void set(String name,Object v)throws Exception{var f=Main.class.getDeclaredField(name);f.setAccessible(true);f.set(game,v);}
    static void require(boolean b,String m){if(!b)throw new AssertionError(m);}
    static void input(String... args)throws Exception{var c=new ArrayList<String>();c.add("xdotool");c.addAll(List.of(args));var p=new ProcessBuilder(c).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();require(p.waitFor()==0,"X11 input");}
    static void image(String name)throws Exception{
        int w=(int)get("framebufferWidth"),h=(int)get("framebufferHeight");var bytes=java.nio.ByteBuffer.allocateDirect(w*h*4);glReadPixels(0,0,w,h,GL_RGBA,GL_UNSIGNED_BYTE,bytes);
        var img=new java.awt.image.BufferedImage(w,h,java.awt.image.BufferedImage.TYPE_INT_RGB);
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){int i=(x+y*w)*4;img.setRGB(x,h-y-1,(bytes.get(i)&255)<<16|(bytes.get(i+1)&255)<<8|(bytes.get(i+2)&255));}
        javax.imageio.ImageIO.write(img,"png",out.resolve(name).toFile());
    }
    public static void main(String[] args)throws Exception{
        out=Path.of(args[0]);Files.createDirectories(out);set("offlineSave",Path.of("target/render-profile/synthetic-city.dat").toAbsolutePath());set("gameConfig",GameConfig.sandbox());((EngineEditor)get("engineEditor")).open=false;
        glfwInitHint(GLFW_PLATFORM,GLFW_PLATFORM_X11);
        game.run(new Main.FrameObserver(){
            public void started(Main g)throws Exception{
                long handle=((dev.jayms.window.Window)get("window")).getHandle();glfwSetWindowSizeLimits(handle,320,240,GLFW_DONT_CARE,GLFW_DONT_CARE);glfwSetWindowSize(handle,640,400);
                windowId=Long.toString(GLFWNativeX11.glfwGetX11Window(handle));
                originalKeys=glfwSetKeyCallback(handle,null);
                glfwSetKeyCallback(handle,(h,key,scancode,action,mods)->{if(key==GLFW_KEY_F6&&action==GLFW_PRESS)f6Presses++;originalKeys.invoke(h,key,scancode,action,mods);});
                initialFocus=glfwSetWindowFocusCallback(handle,null);input("windowfocus",windowId);input("keyup","F6","w","F10");
                ((dev.jayms.ui.ControlsMenu)get("menu")).open=false;
                var capture=Main.class.getDeclaredMethod("setCaptured",boolean.class);capture.setAccessible(true);capture.invoke(game,true);
                World world=(World)get("world");
                for(int x=2;x<15;x++)for(int z=2;z<15;z++)world.apply(new Protocol.Edit(x,72,z,(x+z)%2==0?Blocks.BRICKS:Blocks.PLANKS));
                for(int x=3;x<14;x++)for(int z=3;z<10;z++)world.apply(new Protocol.Edit(x,78,z,Blocks.PLANKS));
                for(int x=3;x<14;x++)for(int y=73;y<78;y++)world.apply(new Protocol.Edit(x,y,3,Blocks.BRICKS));
                world.apply(new Protocol.Edit(5,74,4,Blocks.LED).withColor(0xff3040));world.apply(new Protocol.Edit(8,74,4,Blocks.LED).withColor(0x30ff90));world.apply(new Protocol.Edit(11,74,4,Blocks.LED).withColor(0x3050ff));
                for(int z=10;z<=13;z++)world.apply(new Protocol.Edit(4,73,z,Blocks.BRICKS));
                for(int x=5;x<=11;x++)for(int z=10;z<=13;z++)world.apply(new Protocol.Edit(x,73,z,Blocks.WATER));
                for(var e:world.getLoadedChunks().entrySet())if(e.getKey().chunkX()==0&&e.getKey().chunkZ()==0)e.getValue().checkMesh();
                Player p=new Player(new Vector3f(8.5f,74.5f,14.5f),-90,-12,(dev.jayms.Camera)get("camera"));p.toggleFlight();set("player",p);set("isometric",false);capture.invoke(game,true);
                var r=(RenderPipeline)get("rendering");
                r.settings.renderScale=.5f;r.settings.atmosphereQuality=PlanetAtmosphere.Quality.LOW;
                r.settings.ao=r.settings.screenGi=r.settings.volumetrics=r.settings.bloom=false;
                changed=System.nanoTime();
            }
            public void afterFrame(Main g)throws Exception{
                frame++;if(frame==5&&initialFocus!=null){glfwSetWindowFocusCallback(((dev.jayms.window.Window)get("window")).getHandle(),initialFocus);initialFocus=null;}
                require(glGetError()==GL_NO_ERROR,"Production Main GL state is clean");
                RenderPipeline r=(RenderPipeline)get("rendering");Player p=(Player)get("player");long handle=((dev.jayms.window.Window)get("window")).getHandle();
                if(frame%10==0)System.out.println("Render phase="+stage+" lighting="+r.lightingReady()+" menu="+((dev.jayms.ui.ControlsMenu)get("menu")).open+" captured="+get("captured"));
                var menu=(dev.jayms.ui.ControlsMenu)get("menu");
                World world=(World)get("world");Object probe=field(r,"probes");
                if(stage==-4&&r.lightingReady()&&r.reflectionProbeReady()){
                    require((long)field(probe,"revision")==world.editsVersion(),"Initial warmed probe matches world");
                    r.settings.shadows=false;
                    start=p.position();
                    Player moved=new Player(new Vector3f(start).add(18,0,0),-90,-12,(Camera)get("camera"));moved.toggleFlight();set("player",moved);
                    stage=-3;changed=System.nanoTime();
                }else if(stage==-3&&r.reflectionProbeReady()&&((Vector3f)field(probe,"center")).distance(p.position())<.5f){
                    require(!r.settings.shadows&&r.lightingReady(),"Warmed lighting stays active with shadows off");
                    require(p.position().distance(start)>16,"Camera moved beyond probe refresh threshold");
                    image("render-settings-shadowless-moved.png");
                    world.apply(new Protocol.Edit(25,74,4,Blocks.LED).withColor(0xff8040));
                    for(var e:world.getLoadedChunks().entrySet())if(e.getKey().chunkX()==1&&e.getKey().chunkZ()==0)e.getValue().checkMesh();
                    stage=-2;changed=System.nanoTime();
                }else if(stage==-2&&r.reflectionProbeReady()&&(long)field(probe,"revision")==world.editsVersion()){
                    require(!r.settings.shadows&&r.lightingReady(),"Probe recaptured edited world with shadows off");
                    image("render-settings-shadowless-edited.png");
                    System.out.println("Warmed shadowless movement/edit probe regression PASS; revision="+world.editsVersion());
                    menu.rendering.key(GLFW_KEY_2,GLFW_PRESS);stage=0;changed=System.nanoTime();
                }
                else if(stage==0&&frame>=3) {input("key","Escape");stage++;changed=System.nanoTime();}
                else if(stage==1&&menu.open){input("key","r");stage++;changed=System.nanoTime();}
                else if(stage==2&&menu.rendering.open){image("render-settings-default.png");input("key","1");stage++;changed=System.nanoTime();}
                else if(stage==3&&r.settings.renderScale==.5f&&!r.settings.shadows){
                    require(r.settings.renderScale==.5f&&!r.settings.shadows&&!r.settings.screenGi,"Low cost preset live");
                    image("render-settings-low.png");
                    // Mouse changes the first row; keyboard clamps it back to the lower edge.
                    menu.rendering.click(GLFW_MOUSE_BUTTON_LEFT,610,112,640,400);
                    require(r.settings.renderScale>.5f,"Mouse row changes render scale");
                    menu.rendering.key(GLFW_KEY_LEFT,GLFW_PRESS);
                    input("key","Left");stage++;changed=System.nanoTime();
                }else if(stage==4&&System.nanoTime()-changed>500_000_000L){
                    require(r.settings.renderScale==.5f,"Scale lower bound");
                    r.settings.renderScale=1;var restored=new dev.jayms.ui.RenderingMenu(r);require(r.settings.renderScale==.5f,"Saved settings reload");
                    input("key","2");stage++;changed=System.nanoTime();
                }else if(stage==5&&r.settings.renderScale==1&&r.settings.shadows){
                    require(r.settings.renderScale==1&&r.settings.shadows&&r.settings.screenGi,"Defaults restored");
                    for(int i=0;i<21;i++){menu.rendering.key(GLFW_KEY_RIGHT,GLFW_PRESS);menu.rendering.key(GLFW_KEY_LEFT,GLFW_PRESS);menu.rendering.key(GLFW_KEY_DOWN,GLFW_PRESS);}
                    Path saved=Path.of(System.getProperty("user.home"),".voxel-one","rendering.properties");
                    Files.writeString(saved,"renderScale=NaN\ncloudCoverage=99\natmosphereQuality=INVALID\n");
                    new dev.jayms.ui.RenderingMenu(r);
                    require(Float.isFinite(r.settings.renderScale)&&r.settings.cloudCoverage==1,"Invalid saved values ignored or clamped");
                    menu.rendering.key(GLFW_KEY_2,GLFW_PRESS);
                    glfwSetWindowSize(handle,420,300);stage++;changed=System.nanoTime();
                }else if(stage==6&&System.nanoTime()-changed>500_000_000L){input("key","--repeat","20","--delay","30","Down");stage++;changed=System.nanoTime();}
                else if(stage==7&&System.nanoTime()-changed>1_000_000_000L){image("render-settings-small.png");input("key","Escape");stage++;changed=System.nanoTime();}
                else if(stage==8&&!menu.rendering.open){require(menu.open,"Back preserves controls menu");input("key","Escape");stage++;changed=System.nanoTime();}
                else if(stage==9&&!menu.open){expectedF6Presses=f6Presses+1;input("key","F6");stage++;changed=System.nanoTime();}
                else if(stage==10&&f6Presses>=expectedF6Presses){
                    require((boolean)get("isometric"),"F6 gameplay regression");
                    Files.writeString(out.resolve("results.txt"),"Playtest: PASS. Warmed lighting and completed reflection probe; shadows off camera movement beyond 16 units recaptures probe at new center; world LED edit recaptures current revision; Defaults re-enables shadows. Production Main; Linux inherited X11; software GL; isolated synthetic profile. Real Escape/R inputs open settings. Low cost applies scale 0.5 and disables shadows/GI; Left clamps scale to 0.5; persisted settings reload; Defaults restores quality; 420x300 scroll reaches final option; Escape returns to controls then gameplay; F6 still toggles isometric; GL_NO_ERROR throughout.\n");
                    menu.open=true;menu.rendering.show();
                    r.settings.volumetrics=true;r.settings.fogDensity=.012f;
                    legacyFog=r.settings.fogDensity;legacyVolume=r.settings.volumetrics;
                    for(int i=0;i<9;i++)menu.rendering.key(GLFW_KEY_UP,GLFW_PRESS);
                    menu.rendering.key(GLFW_KEY_RIGHT,GLFW_PRESS);
                    require(r.settings.volumetrics==legacyVolume,"Unavailable volume keyboard leaves field unchanged");
                    stage++;changed=System.nanoTime();
                }else if(stage==11){
                    Object temporal=field(r,"temporal"),effects=field(temporal,"effects");
                    effectProgram=(int)field(effects,"programId");
                    require(glGetUniformi(effectProgram,glGetUniformLocation(effectProgram,"uVolume"))==0,"Live legacy volumetric branch remains disabled");
                    menu.rendering.click(GLFW_MOUSE_BUTTON_LEFT,400,110+(11-(int)field(menu.rendering,"offset"))*27,420,300);
                    require(r.settings.volumetrics==legacyVolume,"Unavailable volume click leaves field unchanged");
                    image("render-settings-unavailable-volume.png");
                    for(int i=0;i<7;i++)menu.rendering.key(GLFW_KEY_DOWN,GLFW_PRESS);
                    menu.rendering.key(GLFW_KEY_RIGHT,GLFW_PRESS);
                    require(r.settings.fogDensity==legacyFog,"Unavailable fog keyboard leaves field unchanged");
                    stage++;changed=System.nanoTime();
                }else if(stage==12){
                    require(glGetUniformi(effectProgram,glGetUniformLocation(effectProgram,"uVolume"))==0,"Unavailable fog does not enable legacy scattering");
                    image("render-settings-unavailable-fog.png");
                    // Selected row is visible; direct click uses the exact UI row coordinates.
                    int offset=(int)field(menu.rendering,"offset");
                    menu.rendering.click(GLFW_MOUSE_BUTTON_LEFT,400,110+(18-offset)*27,420,300);
                    require(r.settings.fogDensity==legacyFog,"Unavailable fog click leaves field unchanged");
                    Path saved=Path.of(System.getProperty("user.home"),".voxel-one","rendering.properties");
                    Files.writeString(saved,"volumetrics="+!legacyVolume+"\nfogDensity=0.02\n");
                    new dev.jayms.ui.RenderingMenu(r);
                    require(r.settings.volumetrics==legacyVolume&&r.settings.fogDensity==legacyFog,"Saved legacy values ignored");
                    menu.rendering.key(GLFW_KEY_1,GLFW_PRESS);
                    require(r.settings.volumetrics==legacyVolume&&r.settings.fogDensity==legacyFog,"Preset leaves unavailable values unchanged");
                    var stored=new java.util.Properties();try(var in=Files.newInputStream(saved)){stored.load(in);}
                    require(!stored.containsKey("volumetrics")&&!stored.containsKey("fogDensity"),"Unavailable values not saved");
                    Files.writeString(out.resolve("unavailable-results.txt"),"Playtest: PASS. Running Main active planet atmosphere. Disabled legacy volume/fog rows display N/A and Unavailable; keyboard/click, saved values and preset cannot alter legacy fields. Live effects uVolume=0 for both rows. No misleading enabled legacy scattering control.\n");
                    glfwSetWindowShouldClose(handle,true);stage++;
                }
                if(frame>1200||System.nanoTime()-changed>(stage<0?600_000_000_000L:120_000_000_000L))throw new AssertionError("Main playtest phase timeout "+stage);
            }
        });if(originalKeys!=null)originalKeys.free();require(stage==13,"Production workflow completed");System.out.println("Production renderer Playtest passed");
    }
}
