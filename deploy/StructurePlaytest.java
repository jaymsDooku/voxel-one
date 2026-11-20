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
    static Path stageDirectory;static final long started=System.nanoTime();static int stageStep;
    static final ArrayDeque<String> stageEvents=new ArrayDeque<>();
    static synchronized void stage(String name)throws Exception {
        if(!name.matches("[a-z0-9-]{1,64}"))throw new IllegalArgumentException("Fixed diagnostic stage required");
        long elapsed=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started);stageStep++;
        stageEvents.addLast("{\"stage\":\""+name+"\",\"elapsed_ms\":"+elapsed+",\"frames\":"+frames.get()+"}");
        while(stageEvents.size()>256)stageEvents.removeFirst();
        String body="{\"schema\":1,\"step\":"+stageStep+",\"current\":\""+name+"\",\"elapsed_ms\":"+elapsed+",\"frames\":"+frames.get()+",\"events\":["+String.join(",",stageEvents)+"]}\n";
        Path temp=stageDirectory.resolve("structure-native-stage.tmp"),target=stageDirectory.resolve("structure-native-stage.json");
        try{Files.writeString(temp,body);Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}finally{Files.deleteIfExists(temp);}
    }
    static Main game=new Main();static volatile Throwable failure;static volatile String renderer;
    static final ConcurrentLinkedQueue<FutureTask<Void>> jobs=new ConcurrentLinkedQueue<>();
    static final java.util.concurrent.atomic.AtomicLong frames=new java.util.concurrent.atomic.AtomicLong();
    static Object get(String name)throws Exception {var f=Main.class.getDeclaredField(name);f.setAccessible(true);return f.get(game);}
    static Object field(Object target,String name)throws Exception {var f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    static final ArrayList<String> lightingSamples=new ArrayList<>();
    static boolean lightingProbeOnly;
    /** Synthetic coordinates and bounded counters only; no runtime/profile dump. */
    static void lightingSample(String label,Path output)throws Exception {
        require(label.matches("[a-z0-9-]{1,64}"),"Fixed lighting label required");
        require(lightingSamples.size()<64,"Lighting diagnostic sample budget");
        var pipeline=(dev.jayms.render.RenderPipeline)get("rendering");
        var camera=((Camera)get("camera")).position();
        var volume=(dev.jayms.render.LightVolume)field(pipeline,"volume");
        var sky=(dev.jayms.render.WorldSkyVisibility)field(pipeline,"worldSky");
        var lighting=(dev.jayms.render.WorldLighting)field(pipeline,"lighting");
        var scheduler=(dev.jayms.render.SkyVisibilityScheduler)field(pipeline,"skyVisibility");
        int program=(int)field(get("shader"),"programId");
        var sample=new StringBuilder("{\"stage\":\"").append(label).append("\",\"frames\":").append(frames.get());
        sample.append(",\"near_ready\":").append(pipeline.nearLightingReady(world(),camera.x,camera.y,camera.z));
        sample.append(",\"has_irradiance\":").append(field(pipeline,"hasIrradiance"));
        sample.append(",\"sky_columns\":").append(sky==null?0:sky.columns);
        sample.append(",\"sky_pending\":").append(scheduler.pending());
        sample.append(",\"bake_submitted\":").append(lighting.submitted).append(",\"bake_completed\":").append(lighting.completed).append(",\"bake_rejected\":").append(lighting.rejected);
        sample.append(",\"shader\":{");boolean first=true;
        for(String uniform:List.of("uHeld","uHasIrradiance","uPlanetLighting","uSkyColumns","uSkyOffset","uRootBuffer")) {
            int location=org.lwjgl.opengl.GL20.glGetUniformLocation(program,uniform);
            require(location>=0,"Diagnostic shader uniform "+uniform);
            if(!first)sample.append(',');first=false;
            sample.append('"').append(uniform).append("\":").append(org.lwjgl.opengl.GL20.glGetUniformi(program,location));
        }
        for(String uniform:List.of("uAmbient","uDaylight","uOutputExposure")) {
            int location=org.lwjgl.opengl.GL20.glGetUniformLocation(program,uniform);require(location>=0,"Diagnostic shader uniform "+uniform);
            float value=org.lwjgl.opengl.GL20.glGetUniformf(program,location);require(Float.isFinite(value),"Finite lighting diagnostic required");
            sample.append(',').append('"').append(uniform).append("\":").append(value);
        }
        sample.append("},\"volume\":").append(volume==null?"null":"["+volume.x+","+volume.y+","+volume.z+","+volume.width+","+volume.height+","+volume.length+"]");
        var points=List.of(new float[]{camera.x,camera.y,camera.z},new float[]{6.5f,130,14.5f},new float[]{7.5f,130,14.5f});
        var gpu=lightingProbeOnly?StructureLightingProbe.sample(program,points):null;
        if(lightingProbeOnly) {
            require(volume!=null&&camera.y>=volume.y&&camera.y<volume.y+volume.height,"Actual camera height inside lighting volume");
            if(!label.equals("structure-lighting-altitude"))for(int i=1;i<points.size();i++)require(gpu.get(i)[0]+gpu.get(i)[1]+gpu.get(i)[2]>.01f,"High component local light");
            if(label.equals("structure-lighting-under-roof"))require(gpu.get(0)[3]<.001f&&sky.sampleKnown(camera.x,camera.y,camera.z,-1)==0,"Sealed room rejects sky while retaining local light");
        }
        sample.append(",\"points\":[");first=true;
        int index=0;for(float[] point:points) {
            if(!first)sample.append(',');first=false;
            float x=point[0],y=point[1],z=point[2];
            boolean inside=volume!=null&&x>=volume.x&&y>=volume.y&&z>=volume.z&&x<volume.x+volume.width&&y<volume.y+volume.height&&z<volume.z+volume.length;
            sample.append("{\"position\":").append(Arrays.toString(point)).append(",\"inside_volume\":").append(inside);
            sample.append(",\"volume_rgba\":").append(volume==null?"null":Arrays.toString(volume.sampleLighting(x,y,z)));
            sample.append(",\"sky_visibility\":").append(sky==null?-1:sky.sampleKnown(x,y,z,-1));
            if(gpu!=null)sample.append(",\"gpu_irradiance_rgba\":").append(Arrays.toString(gpu.get(index)));
            sample.append('}');index++;
        }
        sample.append("]}");require(org.lwjgl.opengl.GL11.glGetError()==org.lwjgl.opengl.GL11.GL_NO_ERROR,"Lighting diagnostic GL error");
        lightingSamples.add(sample.toString());
        Files.writeString(output.resolve("structure-native-lighting.json"),"{\"schema\":1,\"scope\":\"Owner-frame CPU fields and previous-frame shader uniforms; synthetic Main fixture only; unknown sky is -1; outside-volume CPU samples do not mirror shader sky fallback\",\"samples\":["+String.join(",",lightingSamples)+"]}\n");
    }
    static void set(String name,Object value)throws Exception {var f=Main.class.getDeclaredField(name);f.setAccessible(true);f.set(game,value);}
    static World world()throws Exception{return (World)get("world");}
    static LocalGame local()throws Exception{return (LocalGame)get("local");}
    static StructureMenu menu()throws Exception{return (StructureMenu)get("structures");}
    static void onFrame(Callable<Void> action)throws Exception {var job=new FutureTask<Void>(action);jobs.add(job);job.get(90,TimeUnit.SECONDS);}
    static void require(boolean value,String label){if(!value)throw new AssertionError(label);}
    static double handLuma(Path path)throws Exception {
        var image=javax.imageio.ImageIO.read(path.toFile());require(image!=null,"Hand diagnostic image");double total=0;int count=0;
        // Fixed first-person fist/sleeve patch, above the HUD, relative to the window.
        for(int y=image.getHeight()*72/100;y<image.getHeight()*78/100;y++)for(int x=image.getWidth()*79/100;x<image.getWidth()*84/100;x++) {
            int rgb=image.getRGB(x,y);total+=.2126*((rgb>>16)&255)+.7152*((rgb>>8)&255)+.0722*(rgb&255);count++;
        }
        require(count>0,"Hand diagnostic pixel count");return total/count;
    }
    static void waitFor(Callable<Boolean> test,String label)throws Exception {
        long deadline=System.nanoTime()+90_000_000_000L;while(System.nanoTime()<deadline){if(test.call())return;Thread.sleep(50);}throw new AssertionError(label);
    }
    static String x(String... args)throws Exception {
        var command=new ArrayList<String>();command.add("xdotool");command.addAll(List.of(args));
        var process=new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start();String result=new String(process.getInputStream().readAllBytes()).trim();
        require(process.waitFor()==0,"X11 input "+args[0]);Thread.sleep(100);return result;
    }
    static void command(String text)throws Exception {
        String action=text.split(" ",2)[0];
        require(Set.of("first","second","anchor","capture","export","import","at","preview","place","undo","rotate","cancel").contains(action),"Fixed diagnostic command required");stage("command-"+action);
        require(text.length()<=4096,"Command exceeds bounded input limit");
        x("type","--clearmodifiers","--delay","2",text);waitFor(()->menu().input.equals(text),"Text entry: "+text);x("key","Return");
        waitFor(()->menu().input.isEmpty(),"Command dispatched");onFrame(()->null);
        waitFor(()->!menu().busy,"Structure operation completed");onFrame(()->null);stage("command-"+action+"-done");
    }
    static void capture(String window,Path path)throws Exception {
        String name=path.getFileName().toString().replace(".png","");stage("capture-"+name);
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
        if(lightingProbeOnly)waitFor(()->{
            var ready=new java.util.concurrent.atomic.AtomicBoolean();onFrame(()->{var camera=((Camera)get("camera")).position();ready.set(((dev.jayms.render.RenderPipeline)get("rendering")).nearLightingReady(world(),camera.x,camera.y,camera.z));return null;});return ready.get();
        },"Current camera-height lighting ready");
        onFrame(()->{lightingSample(name,path.getParent());return null;});
        var process=new ProcessBuilder("import","-window",window,path.toString()).redirectError(ProcessBuilder.Redirect.DISCARD).start();require(process.waitFor()==0,"Native screenshot");stage("capture-"+name+"-done");
    }
    static void edit(Protocol.Edit edit)throws Exception {world().apply(edit);WorldVoxels.remember(local().edits,edit);}
    static Position destination(int r,Position relative)throws Exception {
        var box=StructureEngine.footprint(menu().selected,new Position(20,128,26),r);var p=StructureEngine.rotate(relative,5,5,r);
        return new Position(box.minX()+p.x(),box.minY()+p.y(),box.minZ()+p.z());
    }
    public static void main(String[] args)throws Exception {
        require(args.length==1||args.length==2&&args[1].equals("--lighting-probe-only"),"Fixed native mode required");lightingProbeOnly=args.length==2;
        Path output=Path.of(args[0]);Files.createDirectories(output);stageDirectory=output;stage("initializing");glfwInitHint(GLFW_PLATFORM,GLFW_PLATFORM_X11);
        set("offlineSave",Path.of("target/structure-native/world.dat").toAbsolutePath());set("gameConfig",GameConfig.sandbox());set("seed",42L);
        var driver=new Thread(()->{
            long handle=0;
            try {
                waitFor(()->get("player")!=null&&get("structures")!=null,"Main initialized");Thread.sleep(800);
                handle=((dev.jayms.window.Window)get("window")).getHandle();
                String window=x("search","--onlyvisible","--pid",Long.toString(ProcessHandle.current().pid()),"--name","^Voxel One").lines().findFirst().orElseThrow();x("windowfocus",window);
                if(((ControlsMenu)get("menu")).open)x("key","Escape");
                stage("fixture-setup");onFrame(()->{
                    for(int z=8;z<=34;z++)for(int xx=0;xx<=28;xx++)edit(new Protocol.Edit(xx,127,z,Blocks.STONE));
                    for(int y=128;y<=131;y++)for(int z=12;z<=16;z++)for(int xx=4;xx<=8;xx++)
                        if(y==128||y==131||xx==4||xx==8||z==12||z==16)edit(new Protocol.Edit(xx,y,z,Blocks.BRICKS));
                    edit(new Protocol.Edit(5,129,13,Blocks.piece(Blocks.LED,3),3,1,2,3,0x22bbff));
                    edit(new Protocol.Edit(5,130,15,Blocks.LED).withColor(0xffffff));
                    for(int xx=6;xx<=7;xx++)for(int y=129;y<=130;y++)edit(new Protocol.Edit(xx,y,16,0));
                    var tree=new SparseVoxelOctree(32);tree.fill(2,0,2,30,2,30,0xff998855);tree.fill(14,2,14,18,27,18,0xff55ccaa);tree.set(31,31,31,0xffff5533);
                    int model=local().models.register(new ModelDefinition("Structure lamp",tree),"fixture").id();edit(new Protocol.Edit(6,129,14,model));
                    var dense=new SparseVoxelOctree(8);for(int i=0;i<512;i++)dense.set(i%8,(i/8)%8,i/64,0xff000000|((i*1664525+1013904223)&0xffffff));
                    int packed=local().models.register(new ModelDefinition("Dense component",dense),"fixture").id();edit(new Protocol.Edit(7,129,14,packed));
                    var player=new dev.jayms.player.Player(new org.joml.Vector3f(22,135,40),-90,-26,(Camera)get("camera"));player.toggleFlight();set("player",player);
                    return null;
                });
                Thread.sleep(2000);x("key","F12");waitFor(()->menu().open,"F12 library opens");
                onFrame(()->{menu().mark=new Position(4,128,12);return null;});command("first");command("anchor");
                onFrame(()->{menu().mark=new Position(8,131,16);return null;});command("second");command("capture Demo house");
                require(menu().selected!=null&&menu().selected.models.size()==2,"Named capture with custom dependencies: "+menu().message+"; corners="+menu().first+" / "+menu().second);
                var packedCheck=new java.io.ByteArrayOutputStream();StructureModelCodec.write(new java.io.DataOutputStream(packedCheck),menu().selected.models.get(1));
                require(packedCheck.toByteArray()[0]==1,"Dense component exercises packed dependency encoding");
                require(menu().selected.cells.stream().anyMatch(c->c.model()<0&&c.geometry().leaves().stream().anyMatch(l->l.side()<16)),"Fractional geometry captured");
                capture(window,output.resolve("structure-library-capture.png"));
                if(lightingProbeOnly) {
                    x("key","F12");onFrame(()->{var p=new dev.jayms.player.Player(new org.joml.Vector3f(6.5f,128,19.5f),-90,0,(Camera)get("camera"));p.toggleFlight();set("player",p);return null;});
                    capture(window,output.resolve("structure-lighting-components.png"));
                    onFrame(()->{var p=new dev.jayms.player.Player(new org.joml.Vector3f(6.5f,260,19.5f),-90,0,(Camera)get("camera"));p.toggleFlight();set("player",p);return null;});
                    capture(window,output.resolve("structure-lighting-altitude.png"));
                    onFrame(()->{for(int xx=6;xx<=7;xx++)for(int y=129;y<=130;y++)edit(new Protocol.Edit(xx,y,16,Blocks.BRICKS));var p=new dev.jayms.player.Player(new org.joml.Vector3f(6.5f,128,15.5f),-90,0,(Camera)get("camera"));p.toggleFlight();set("player",p);return null;});
                    capture(window,output.resolve("structure-lighting-under-roof.png"));
                    onFrame(()->{for(int xx=6;xx<=7;xx++)for(int y=129;y<=130;y++)edit(new Protocol.Edit(xx,y,16,0));return null;});
                    onFrame(()->{var p=new dev.jayms.player.Player(new org.joml.Vector3f(6.5f,128,19.5f),-90,0,(Camera)get("camera"));p.toggleFlight();set("player",p);return null;});
                    x("key","e");waitFor(()->((InventoryHud)get("inventoryHud")).open,"Probe inventory regression");x("key","e");
                    x("key","F7");waitFor(()->((ModelEditor)get("editor")).open,"Probe model editor regression");x("key","F7");
                    capture(window,output.resolve("structure-lighting-restored-state.png"));
                    double outdoor=handLuma(output.resolve("structure-lighting-components.png")),roof=handLuma(output.resolve("structure-lighting-under-roof.png")),restored=handLuma(output.resolve("structure-lighting-restored-state.png"));
                    require(outdoor>5&&restored>5&&roof>5,"Sky and local-emitter hand lighting: "+outdoor+" / "+roof+" / "+restored);
                    Files.writeString(output.resolve("structure-native-hand.json"),"{\"outdoor_luma\":"+outdoor+",\"roof_luma\":"+roof+",\"restored_luma\":"+restored+",\"scope\":\"Mean display luma in fixed relative first-person patch; synthetic camera; outdoor/roof/return >5; sealed-roof sky visibility asserted separately; roof contains a physical white LED; not HDR radiance or performance qualification\"}\n");
                    onFrame(()->{renderer=org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_RENDERER);return null;});stage("lighting-probe-passed");
                    Files.writeString(output.resolve("structure-native-result.json"),"{\"status\":\"passed\",\"playtest\":\"Playtest: production Main synthetic fixture; named capture, actual previous-frame uniforms/textures sampled through shipped irradiance shader, outdoor camera versus sealed-roof samples with local LED light and high component emission, framebuffer captures after GL state restoration, inventory and model editor regressions. This mode does not run placement, import or undo.\",\"renderer\":\""+renderer+"\",\"qualification\":false}\n");return;
                }
                command("export");Path exported=Controls.directory().resolve("structures/exports/Demo house.vstr");require(Files.exists(exported),"Self-contained export");
                Path longImport=exported.getParent().resolve("long import path "+"a".repeat(100)).resolve("b".repeat(100)).resolve("Demo house.vstr");
                Files.createDirectories(longImport.getParent());Files.copy(exported,longImport);
                require(("import "+longImport).length()>240,"Long import path regression exceeds former cap");
                onFrame(()->{menu().selected=null;return null;});command("import "+longImport);require(menu().selected!=null,"Self-contained import");
                onFrame(()->{menu().selected=null;return null;});x("key","Down");waitFor(()->!menu().busy&&menu().selected!=null,"Saved library browsing");
                var previousTarget=menu().target;
                int invalidAnchorEdits=local().edits.size(),invalidAnchorModels=local().models.entries().size();
                command("at -2147483648 128 0");
                require(menu().message.equals("Placement anchor outside world")&&menu().target.equals(previousTarget)&&!menu().busy,"Invalid placement anchor rejected before preview");
                require(local().edits.size()==invalidAnchorEdits&&local().models.entries().size()==invalidAnchorModels,"Invalid anchor leaves world/dependencies unchanged");
                capture(window,output.resolve("structure-invalid-anchor.png"));
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
                    stage("rotation-"+rotation);final int r=rotation;Position preserved=destination(r,new Position(2,2,2));
                    onFrame(()->{edit(new Protocol.Edit(preserved.x(),preserved.y(),preserved.z(),Blocks.STONE));return null;});command("preview");
                    require(menu().message.contains("clear"),"Preview accepts occupied destination under empty source");
                    capture(window,output.resolve("structure-preview-"+r+".png"));command("place");require(menu().message.startsWith("Placed and saved"),"Placement rotation"+r+": "+menu().message);
                    require(world().sample(preserved.x(),preserved.y(),preserved.z())==Blocks.STONE,"Empty-source terrain preserved");
                    Position modelPosition=destination(r,new Position(2,1,2));int id=world().sample(modelPosition.x(),modelPosition.y(),modelPosition.z());
                    require(world().models().get(id).definition().voxels().size()==32,"32 resolution retained");
                    require(world().models().get(id).definition().voxels().occupied()==menu().selected.models.get(0).voxels().occupied(),"Model geometry retained");
                    require(world().models().get(id).definition().voxels().leaves().equals(StructureEngine.rotate(menu().selected.models.get(0).voxels(),r).leaves()),"Detailed model orientation and colours retained");
                    Position densePosition=destination(r,new Position(3,1,2));int denseId=world().sample(densePosition.x(),densePosition.y(),densePosition.z());
                    require(world().models().get(denseId).definition().voxels().leaves().equals(StructureEngine.rotate(menu().selected.models.get(1).voxels(),r).leaves()),"Packed model orientation and colours retained");
                    command("place");require(menu().message.contains("Occupied destination"),"Overlap rejected");
                    if(r==1){command("preview");capture(window,output.resolve("structure-preview-conflict.png"));}
                    if(r==0)x("key","F10");
                    if(r==0) {
                        byte[] saved=Files.readAllBytes((Path)get("offlineSave"));int edits=local().edits.size();
                        x("type","--clearmodifiers","--delay","2","undo");waitFor(()->menu().input.equals("undo"),"Undo cancellation text entered");
                        x("key","--delay","0","Return","Escape");onFrame(()->null);waitFor(()->menu().input.isEmpty()&&!menu().busy,"Undo cancellation completed");onFrame(()->null);
                        require(menu().message.contains("Cancelled"),"Explicit undo cancellation: "+menu().message);
                        require(local().edits.size()==edits&&world().sample(modelPosition.x(),modelPosition.y(),modelPosition.z())==id,"Cancelled undo preserves placed geometry and history");
                        require(Arrays.equals(saved,Files.readAllBytes((Path)get("offlineSave"))),"Cancelled undo preserves durable world");
                        capture(window,output.resolve("structure-undo-cancelled.png"));
                    }
                    command("undo");require(menu().message.contains("undone"),"One-operation undo");require(world().sample(preserved.x(),preserved.y(),preserved.z())==Blocks.STONE,"Undo preserves unrelated edit");
                    onFrame(()->{edit(new Protocol.Edit(preserved.x(),preserved.y(),preserved.z(),0));return null;});
                    if(r<3)command("rotate");
                }
                stage("cancellation-fixture");var original=menu().selected;
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
                x("type","--clearmodifiers","--delay","2","export");waitFor(()->menu().input.equals("export"),"Export cancellation text entered");
                x("key","--delay","0","Return","Escape");onFrame(()->null);waitFor(()->menu().input.isEmpty()&&!menu().busy,"Export cancellation completed");onFrame(()->null);
                require(menu().message.contains("Cancelled")&&!Files.exists(Controls.directory().resolve("structures/exports/Cancel staging.vstr")),"Cancelled export leaves no published package");
                capture(window,output.resolve("structure-export-cancelled.png"));
                var importFixture=menu().selected;Path cancelImport=Path.of("target/structure-native/cancel-import.vstr");importFixture.save(cancelImport);
                onFrame(()->{menu().selected=original;return null;});
                String importCommand="import "+cancelImport;x("type","--clearmodifiers","--delay","2",importCommand);
                waitFor(()->menu().input.equals(importCommand),"Import cancellation text entered");x("key","--delay","0","Return","Escape");onFrame(()->null);
                waitFor(()->menu().input.isEmpty()&&!menu().busy,"Import cancellation completed");onFrame(()->null);
                require(menu().message.contains("Cancelled"),"Explicit import cancellation: "+menu().message);
                require(menu().selected==original&&!Files.exists(Controls.directory().resolve("structures/Cancel staging.vstr")),"Cancelled decode leaves selection/library unchanged");
                require(local().edits.size()==beforeCancel&&local().models.entries().size()==modelsBeforeCancel,"Cancelled import leaves world/dependencies unchanged");
                capture(window,output.resolve("structure-import-cancelled.png"));
                stage("placement-chunk-admission");
                var limitTree=new SparseVoxelOctree(16);limitTree.fill(0,0,0,16,16,16,WorldVoxels.encode(Blocks.BRICKS));
                var limitCells=new ArrayList<Cell>();
                for(int z=0;z<17&&limitCells.size()<257;z++)for(int x=0;x<17&&limitCells.size()<257;x++)limitCells.add(new Cell(new Position(x*16,0,z*16),limitTree,-1));
                var limitAsset=new StructurePackage("Chunk limit fixture",new Bounds(272,1,272,0,0,0),original.models,limitCells);
                Path limitFile=Path.of("target/structure-native/chunk-limit.vstr");limitAsset.save(limitFile);command("import "+limitFile);
                require(menu().selected.name.equals(limitAsset.name),"Oversized destination package imports without world mutation");
                int limitEdits=local().edits.size(),limitModels=local().models.entries().size();long limitVersion=world().editsVersion();
                var limitLibrary=local().models;byte[] limitSave=Files.readAllBytes(Path.of("target/structure-native/world.dat"));
                command("place");require(menu().message.contains("256 affected chunks"),"Explicit occupied-chunk admission error: "+menu().message);
                require(local().edits.size()==limitEdits&&world().editsVersion()==limitVersion&&local().models==limitLibrary&&local().models.entries().size()==limitModels,"Chunk admission leaves geometry/dependencies unchanged");
                require(Arrays.equals(limitSave,Files.readAllBytes(Path.of("target/structure-native/world.dat"))),"Chunk admission leaves durable save unchanged");
                capture(window,output.resolve("structure-placement-limit.png"));onFrame(()->{menu().selected=original;return null;});
                stage("storage-admission-fixture");
                var storedTree=new SparseVoxelOctree(16);
                for(int i=0;i<4096;i++)storedTree.fill(i%16,i/16%16,i/256,i%16+1,i/16%16+1,i/256+1,WorldVoxels.encode(new Protocol.Edit(0,0,0,Blocks.LED).withColor(i*3733)));
                var storedCells=new ArrayList<Cell>();for(int i=0;i<33;i++)storedCells.add(new Cell(new Position(i,0,0),i<32?storedTree:limitTree,-1));
                var storedAsset=new StructurePackage("Storage geometry fixture",new Bounds(33,1,1,0,0,0),original.models,storedCells);
                require(storedAsset.leafCount==131073,"Stored geometry exceeds prior package leaf cap");
                Path storedFile=Path.of("target/structure-native/stored-geometry.vstr");storedAsset.save(storedFile);
                command("import "+storedFile);require(menu().selected.name.equals(storedAsset.name)&&menu().selected.leafCount==131073,"Larger storage package imports with complete geometry");
                command("place");require(menu().message.contains("131072 geometry leaves"),"Separate placement geometry gate: "+menu().message);
                require(local().edits.size()==limitEdits&&world().editsVersion()==limitVersion&&local().models==limitLibrary&&local().models.entries().size()==limitModels,"Storage import/rejected placement leaves geometry and dependencies unchanged");
                require(Arrays.equals(limitSave,Files.readAllBytes(Path.of("target/structure-native/world.dat"))),"Storage import/rejected placement leaves durable save unchanged");
                capture(window,output.resolve("structure-storage-limit.png"));onFrame(()->{menu().selected=original;return null;});
                Files.writeString(Path.of("target/structure-native/bad.vstr"),"not a package");int edits=local().edits.size();command("import target/structure-native/bad.vstr");require(local().edits.size()==edits,"Malformed import leaves world unchanged");
                command("place");require(menu().message.startsWith("Placed and saved"),"Repeated placement after cancel/import failure");command("preview");capture(window,output.resolve("structure-placed.png"));
                stage("restart-recovery");onFrame(()->{local().save();return null;});var loaded=new LocalGame(Path.of("target/structure-native/world.dat"),0);require(loaded.models.hasStructureModels(),"Versioned dependency restart recovery");
                x("key","F12");capture(window,output.resolve("structure-world-placed.png"));
                onFrame(()->{var player=new dev.jayms.player.Player(new org.joml.Vector3f(30,131,24),180,-12,(Camera)get("camera"));player.toggleFlight();set("player",player);return null;});
                capture(window,output.resolve("structure-component-closeup.png"));stage("inventory-editor-regression");x("key","e");waitFor(()->((InventoryHud)get("inventoryHud")).open,"Inventory regression");x("key","e");x("key","F7");waitFor(()->((ModelEditor)get("editor")).open,"Existing model editor regression");x("key","F7");
                onFrame(()->{renderer=org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_RENDERER);return null;});
                stage("workflow-passed");Files.writeString(output.resolve("structure-native-result.json"),"{\"status\":\"passed\",\"playtest\":\"Playtest: production Main, real GLFW/X11 input, isolated synthetic offline profile; F12 marks/anchor/named capture, saved browsing, export/import, footprint and blocking preview, four rotations, empty-space preservation, overlap rejection, live-player collision rejection without mutation, one-operation undo, placement, undo, export and import cancellation, malformed import atomicity, 131073-leaf storage import with separate placement rejection and 257-chunk rejection without geometry/dependency/save mutation, repeated placement, 32-resolution dependency and restart recovery; inventory and model editor regressions.\",\"renderer\":\""+renderer+"\",\"qualification\":\"Experimental functional checks only; physical GPU and city/network budgets pending\"}\n");
            }catch(Throwable problem){failure=problem;try{stage("workflow-failed");}catch(Exception ignored){}try{Files.writeString(output.resolve("structure-native-failure.txt"),problem.getClass().getSimpleName()+": "+problem.getMessage());}catch(Exception ignored){}}
            finally {if(handle!=0)glfwSetWindowShouldClose(handle,true);}
        },"structure-playtest-input");driver.setDaemon(true);driver.start();
        game.run(new Main.FrameObserver(){public void beforeFrame(Main app){for(var job=jobs.poll();job!=null;job=jobs.poll())job.run();}public void afterFrame(Main app){frames.incrementAndGet();}});
        driver.join(5000);if(failure!=null)throw new AssertionError("Structure native workflow failed",failure);
    }
}
