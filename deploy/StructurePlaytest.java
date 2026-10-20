import dev.jayms.*;
import dev.jayms.net.*;
import dev.jayms.net.city.GameConfig;
import dev.jayms.net.model.*;
import dev.jayms.net.structure.*;
import dev.jayms.ui.*;
import java.nio.file.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import static org.lwjgl.glfw.GLFW.*;
import static dev.jayms.net.structure.StructurePackage.*;

/** Real Main/GLFW/X11 structure workflow, isolated synthetic profile. */
public class StructurePlaytest {
    static Main game=new Main();static volatile Throwable failure;static volatile String renderer;
    static final ConcurrentLinkedQueue<FutureTask<Void>> jobs=new ConcurrentLinkedQueue<>();
    static final java.util.concurrent.atomic.AtomicLong frames=new java.util.concurrent.atomic.AtomicLong();
    static Object get(String name)throws Exception {var f=Main.class.getDeclaredField(name);f.setAccessible(true);return f.get(game);}
    static void set(String name,Object value)throws Exception {var f=Main.class.getDeclaredField(name);f.setAccessible(true);f.set(game,value);}
    static World world()throws Exception{return (World)get("world");}
    static LocalGame local()throws Exception{return (LocalGame)get("local");}
    static StructureMenu menu()throws Exception{return (StructureMenu)get("structures");}
    static void onFrame(Callable<Void> action)throws Exception {var job=new FutureTask<Void>(action);jobs.add(job);job.get(90,TimeUnit.SECONDS);}
    static void require(boolean value,String label){if(!value)throw new AssertionError(label);}
    static void waitFor(Callable<Boolean> test,String label)throws Exception {
        long deadline=System.nanoTime()+90_000_000_000L;while(System.nanoTime()<deadline){if(test.call())return;Thread.sleep(50);}throw new AssertionError(label);
    }
    static String x(String... args)throws Exception {
        var command=new ArrayList<String>();command.add("xdotool");command.addAll(List.of(args));
        var process=new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start();String result=new String(process.getInputStream().readAllBytes()).trim();
        require(process.waitFor()==0,"X11 input "+args[0]);Thread.sleep(100);return result;
    }
    static void command(String text)throws Exception {
        x("type","--clearmodifiers","--delay","2",text);waitFor(()->menu().input.equals(text),"Text entry: "+text);x("key","Return");
        waitFor(()->menu().input.isEmpty(),"Command dispatched");onFrame(()->null);
        waitFor(()->!menu().busy,"Structure operation completed");onFrame(()->null);
    }
    static void capture(String window,Path path)throws Exception {
        onFrame(()->{
            // Warm only the synthetic fixture's real chunk meshes; no fake render assets.
            for(int cx=0;cx<=1;cx++)for(int cz=0;cz<=2;cz++)for(int cy=7;cy<=8;cy++) {
                var position=new ChunkPos(cx,cy,cz);
                if(!world().getLoadedChunks().containsKey(position))world().addChunk(position,ChunkGenerator.generate(position,world().terrain()));
                world().getLoadedChunks().get(position).checkMesh();
            }
            return null;
        });
        long next=frames.get()+3;waitFor(()->frames.get()>=next,"Capture frame");
        var process=new ProcessBuilder("import","-window",window,path.toString()).redirectError(ProcessBuilder.Redirect.DISCARD).start();require(process.waitFor()==0,"Native screenshot");
    }
    static void edit(Protocol.Edit edit)throws Exception {world().apply(edit);WorldVoxels.remember(local().edits,edit);}
    static Position destination(int r,Position relative)throws Exception {
        var box=StructureEngine.footprint(menu().selected,new Position(20,128,26),r);var p=StructureEngine.rotate(relative,5,5,r);
        return new Position(box.minX()+p.x(),box.minY()+p.y(),box.minZ()+p.z());
    }
    public static void main(String[] args)throws Exception {
        Path output=Path.of(args[0]);Files.createDirectories(output);glfwInitHint(GLFW_PLATFORM,GLFW_PLATFORM_X11);
        set("offlineSave",Path.of("target/structure-native/world.dat").toAbsolutePath());set("gameConfig",GameConfig.sandbox());set("seed",42L);
        var driver=new Thread(()->{
            long handle=0;
            try {
                waitFor(()->get("player")!=null&&get("structures")!=null,"Main initialized");Thread.sleep(800);
                handle=((dev.jayms.window.Window)get("window")).getHandle();
                String window=x("search","--onlyvisible","--pid",Long.toString(ProcessHandle.current().pid()),"--name","^Voxel One").lines().findFirst().orElseThrow();x("windowfocus",window);
                if(((ControlsMenu)get("menu")).open)x("key","Escape");
                onFrame(()->{
                    for(int z=8;z<=34;z++)for(int xx=0;xx<=28;xx++)edit(new Protocol.Edit(xx,127,z,Blocks.STONE));
                    for(int y=128;y<=131;y++)for(int z=12;z<=16;z++)for(int xx=4;xx<=8;xx++)
                        if(y==128||y==131||xx==4||xx==8||z==12||z==16)edit(new Protocol.Edit(xx,y,z,Blocks.BRICKS));
                    edit(new Protocol.Edit(5,129,13,Blocks.piece(Blocks.LED,3),3,1,2,3,0x22bbff));
                    for(int xx=6;xx<=7;xx++)for(int y=129;y<=130;y++)edit(new Protocol.Edit(xx,y,16,0));
                    var tree=new SparseVoxelOctree(32);tree.fill(2,0,2,30,2,30,0xff998855);tree.fill(14,2,14,18,27,18,0xff55ccaa);tree.set(31,31,31,0xffff5533);
                    int model=local().models.register(new ModelDefinition("Structure lamp",tree),"fixture").id();edit(new Protocol.Edit(6,129,14,model));
                    var player=new dev.jayms.player.Player(new org.joml.Vector3f(22,135,40),-90,-26,(Camera)get("camera"));player.toggleFlight();set("player",player);
                    return null;
                });
                Thread.sleep(2000);x("key","F12");waitFor(()->menu().open,"F12 library opens");
                onFrame(()->{menu().mark=new Position(4,128,12);return null;});command("first");command("anchor");
                onFrame(()->{menu().mark=new Position(8,131,16);return null;});command("second");command("capture Demo house");
                require(menu().selected!=null&&menu().selected.models.size()==1,"Named capture with custom dependency: "+menu().message+"; corners="+menu().first+" / "+menu().second);
                require(menu().selected.cells.stream().anyMatch(c->c.model()<0&&c.geometry().leaves().stream().anyMatch(l->l.side()<16)),"Fractional geometry captured");
                capture(window,output.resolve("structure-library-capture.png"));
                command("export");Path exported=Controls.directory().resolve("structures/exports/Demo house.vstr");require(Files.exists(exported),"Self-contained export");
                onFrame(()->{menu().selected=null;return null;});command("import "+exported);require(menu().selected!=null,"Self-contained import");
                onFrame(()->{menu().selected=null;return null;});x("key","Down");waitFor(()->!menu().busy&&menu().selected!=null,"Saved library browsing");
                command("at 20 128 26");
                // Commit must use the live player pose, including movement after preview.
                onFrame(()->{var p=new dev.jayms.player.Player(new org.joml.Vector3f(20.5f,128,26.5f),-90,-26,(Camera)get("camera"));p.toggleFlight();set("player",p);return null;});
                int collisionEdits=local().edits.size(),collisionModels=local().models.entries().size();
                command("place");require(menu().message.contains("overlap a player"),"Live player rejects structure: "+menu().message);
                require(local().edits.size()==collisionEdits&&local().models.entries().size()==collisionModels,"Body rejection leaves world and dependencies unchanged");
                capture(window,output.resolve("structure-player-conflict.png"));
                onFrame(()->{var p=new dev.jayms.player.Player(new org.joml.Vector3f(22,135,40),-90,-26,(Camera)get("camera"));p.toggleFlight();set("player",p);return null;});
                x("key","F10");
                for(int rotation=0;rotation<4;rotation++) {
                    final int r=rotation;Position preserved=destination(r,new Position(2,2,2));
                    onFrame(()->{edit(new Protocol.Edit(preserved.x(),preserved.y(),preserved.z(),Blocks.STONE));return null;});command("preview");
                    require(menu().message.contains("clear"),"Preview accepts occupied destination under empty source");
                    capture(window,output.resolve("structure-preview-"+r+".png"));command("place");require(menu().message.startsWith("Placed and saved"),"Placement rotation"+r+": "+menu().message);
                    require(world().sample(preserved.x(),preserved.y(),preserved.z())==Blocks.STONE,"Empty-source terrain preserved");
                    Position modelPosition=destination(r,new Position(2,1,2));int id=world().sample(modelPosition.x(),modelPosition.y(),modelPosition.z());
                    require(world().models().get(id).definition().voxels().size()==32,"32 resolution retained");
                    require(world().models().get(id).definition().voxels().occupied()==menu().selected.models.get(0).voxels().occupied(),"Model geometry retained");
                    require(world().models().get(id).definition().voxels().leaves().equals(StructureEngine.rotate(menu().selected.models.get(0).voxels(),r).leaves()),"Detailed model orientation and colours retained");
                    command("place");require(menu().message.contains("Occupied destination"),"Overlap rejected");
                    if(r==1){command("preview");capture(window,output.resolve("structure-preview-conflict.png"));}
                    if(r==0)x("key","F10");
                    command("undo");require(menu().message.contains("undone"),"One-operation undo");require(world().sample(preserved.x(),preserved.y(),preserved.z())==Blocks.STONE,"Undo preserves unrelated edit");
                    onFrame(()->{edit(new Protocol.Edit(preserved.x(),preserved.y(),preserved.z(),0));return null;});
                    if(r<3)command("rotate");
                }
                var original=menu().selected;
                onFrame(()->{
                    // A valid heavy dependency fixture provides an observable staging/cancel window.
                    var tree=new SparseVoxelOctree(32);for(int i=0;i<32768;i++)tree.set(i%32,(i/32)%32,i/1024,0xff000000|((i*1664525+1013904223)&0xffffff));tree.freeze();
                    var definitions=new ArrayList<ModelDefinition>();var cells=new ArrayList<Cell>();
                    for(int i=0;i<128;i++){definitions.add(new ModelDefinition("Cancel staging "+i,tree));cells.add(new Cell(new Position(i,0,0),null,i));}
                    menu().selected=new StructurePackage("Cancel staging",new Bounds(128,1,1,0,0,0),definitions,cells);return null;
                });
                int beforeCancel=local().edits.size(),modelsBeforeCancel=local().models.entries().size();
                x("type","--clearmodifiers","--delay","2","place");waitFor(()->menu().input.equals("place"),"Cancel command text entered");x("key","--delay","0","Return","Escape");onFrame(()->null);waitFor(()->menu().input.isEmpty()&&!menu().busy,"Cancellation completed");onFrame(()->null);
                require(menu().message.contains("Cancelled"),"Explicit placement cancellation: "+menu().message);
                require(local().edits.size()==beforeCancel&&local().models.entries().size()==modelsBeforeCancel,"Cancellation leaves no staged dependencies or geometry");
                onFrame(()->{menu().selected=original;return null;});
                Files.writeString(Path.of("target/structure-native/bad.vstr"),"not a package");int edits=local().edits.size();command("import target/structure-native/bad.vstr");require(local().edits.size()==edits,"Malformed import leaves world unchanged");
                command("place");require(menu().message.startsWith("Placed and saved"),"Repeated placement after cancel/import failure");command("preview");capture(window,output.resolve("structure-placed.png"));
                onFrame(()->{local().save();return null;});var loaded=new LocalGame(Path.of("target/structure-native/world.dat"),0);require(loaded.models.hasStructureModels(),"Versioned dependency restart recovery");
                x("key","F12");capture(window,output.resolve("structure-world-placed.png"));
                onFrame(()->{var player=new dev.jayms.player.Player(new org.joml.Vector3f(30,131,24),180,-12,(Camera)get("camera"));player.toggleFlight();set("player",player);return null;});
                capture(window,output.resolve("structure-component-closeup.png"));x("key","e");waitFor(()->((InventoryHud)get("inventoryHud")).open,"Inventory regression");x("key","e");x("key","F7");waitFor(()->((ModelEditor)get("editor")).open,"Existing model editor regression");x("key","F7");
                onFrame(()->{renderer=org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_RENDERER);return null;});
                Files.writeString(output.resolve("structure-native-result.json"),"{\"status\":\"passed\",\"playtest\":\"Playtest: production Main, real GLFW/X11 input, isolated synthetic offline profile; F12 marks/anchor/named capture, saved browsing, export/import, footprint and blocking preview, four rotations, empty-space preservation, overlap rejection, live-player collision rejection without mutation, one-operation undo, cancellation, malformed import atomicity, repeated placement, 32-resolution dependency and restart recovery; inventory and model editor regressions.\",\"renderer\":\""+renderer+"\",\"qualification\":\"Experimental functional checks only; physical GPU and city/network budgets pending\"}\n");
            }catch(Throwable problem){failure=problem;try{Files.writeString(output.resolve("structure-native-failure.txt"),problem.getClass().getSimpleName()+": "+problem.getMessage());}catch(Exception ignored){}}
            finally {if(handle!=0)glfwSetWindowShouldClose(handle,true);}
        },"structure-playtest-input");driver.setDaemon(true);driver.start();
        game.run(new Main.FrameObserver(){public void beforeFrame(Main app){for(var job=jobs.poll();job!=null;job=jobs.poll())job.run();}public void afterFrame(Main app){frames.incrementAndGet();}});
        driver.join(5000);if(failure!=null)throw new AssertionError("Structure native workflow failed",failure);
    }
}
