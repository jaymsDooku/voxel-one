package dev.jayms;

import dev.jayms.net.*;
import dev.jayms.net.city.*;
import dev.jayms.ui.*;
import dev.jayms.window.Window;
import java.nio.file.*;
import java.util.*;
import static org.lwjgl.glfw.GLFW.*;

/** Runs Main's full window, world, simulation, renderer, HUD and F10 recorder. */
public final class PopulationMainPlaytest implements Main.FrameObserver {
    static Object get(Main g,String name) throws Exception { var f=Main.class.getDeclaredField(name);f.setAccessible(true);return f.get(g); }
    static void set(Main g,String name,Object value) throws Exception { var f=Main.class.getDeclaredField(name);f.setAccessible(true);f.set(g,value); }
    static void call(Main g,String name,Class<?> arg,Object value) throws Exception { var m=Main.class.getDeclaredMethod(name,arg);m.setAccessible(true);m.invoke(g,value); }
    final Path output; CitySimulation city; PopulationJourneyTest.Journey journey=new PopulationJourneyTest.Journey();
    CitySimulation.Ground ground; Terrain terrain; String completedReport; boolean edge; int edgeTicks;
    final Map<Integer,CitizenLife.Education> previous=new HashMap<>();
    final List<String> captures=new ArrayList<>();
    int frames, ticks, selected=1; String capture; long recordingStarted; boolean recording, done;
    PopulationMainPlaytest(Path output) { this.output=output; }
    public void started(Main game) throws Exception {
        var world=(World)get(game,"world");var local=(LocalGame)get(game,"local");
        terrain=world.terrain();int grade=terrain.column(8,24).height();
        var prototype=PopulationJourneyTest.fixture(new PopulationJourneyTest.Ground(grade),terrain,true).frame();
        var cells=new HashSet<List<Integer>>();
        for(var r:prototype.roads()) cells.add(List.of(r.x(),r.z()));
        for(var b:prototype.buildings()) for(int x=b.x();x<b.x()+6;x++) for(int z=b.z()-1;z<=b.z()+7;z++) cells.add(List.of(x,z));
        // Grade the whole test town, including door-to-road approaches.
        for(int x=0;x<=110;x++) for(int z=5;z<=45;z++) cells.add(List.of(x,z));
        var clear=new ArrayList<Protocol.Edit>();
        for(var cell:cells) { int x=cell.get(0),z=cell.get(1);clear.add(new Protocol.Edit(x,grade,z,Blocks.DIRT));
            for(int y=grade+1;y<=Terrain.MAX_Y;y++) if(world.sample(x,y,z)!=0) clear.add(new Protocol.Edit(x,y,z,0)); }
        ground=new CitySimulation.Ground() {
            public int type(int x,int y,int z) { return world.sample(x,y,z); }
            public boolean occupied(int x,int y,int z,int w,int d) { return false; }
            public void apply(List<Protocol.Edit> batch) { for(var edit:batch) {world.apply(edit);WorldVoxels.remember(local.edits,edit);} }
        };
        ground.apply(clear); city=PopulationJourneyTest.fixture(ground,terrain,true);local.city=city;
        set(game,"isometric",true);call(game,"setCaptured",boolean.class,false);
        for(int id=1;id<=2;id++) previous.put(id,CitizenLife.Education.NONE);
        capture="population-main-zero-study.png";
    }
    public void beforeFrame(Main game) throws Exception {
        if(done) return;
        ((ControlsMenu)get(game,"menu")).open=false;
        if(edge) {
            for(int i=0;i<1200;i++) { city.advance(.1);edgeTicks++; }
            if(city.life(1).study!=0 || city.life(1).education!=CitizenLife.Education.NONE) throw new AssertionError("Disconnected school taught learner");
            selected=1;capture="population-main-disconnected.png";done=true;
            set(game,"notice","Playtest: disconnected school | zero study after 120 seconds");
            return;
        }
        int count=frames<35?1:720;
        for(int n=0;n<count;n++) {
            city.advance(.1);ticks++;journey.observe(city);
            boolean changed=false;
            for(int id=1;id<=2;id++) if(city.life(id).education!=previous.get(id)) {
                previous.put(id,city.life(id).education);selected=id;
                capture="population-main-"+city.life(id).education.toString().toLowerCase(java.util.Locale.ROOT)+"-"+id+".png";
                changed=true;
            }
            if(changed) break;
        }
        if(frames==4) {call(game,"recordInput",int.class,GLFW_KEY_F10);recording=true;recordingStarted=System.nanoTime();}
        if(recording && (frames>=30 || System.nanoTime()-recordingStarted>8_000_000_000L)) {call(game,"recordInput",int.class,GLFW_KEY_F10);recording=false;}
        var p=city.ecs.get(selected,CitySimulation.Position.class);
        var overview=(IsometricCamera)get(game,"overview");overview.cityMode();overview.focus(p.x,p.z,p.y);overview.zoom(-12);
        ((CityTools)get(game,"cityTools")).selectedCitizen=selected;
        set(game,"notice","Playtest: zero-study journeys | accelerated render sampling | tick "+ticks);
        if(frames==25) capture="population-main-zero-study.png";
        if(frames==30) capture="population-main-commute.png";
        if(city.life(1).age>=22.1 && !done) {
            journey.verify(city);selected=1;capture="population-main-qualified-jobs.png";
            done=true;
        }
        if(ticks>135000) throw new AssertionError("Journey did not finish: "+previous+journey.maxStudy);
    }
    public void afterFrame(Main game) throws Exception {
        if(capture!=null) {SpecialBuildingsSmoke.capture(output.resolve(capture));captures.add(capture);capture=null;}
        frames++;
        if(done && !edge) {
            if(recording) call(game,"recordInput",int.class,GLFW_KEY_F10);
            city.save(Path.of("target/population-main/final.city"));
            var loaded=CitySimulation.load(Path.of("target/population-main/final.city"));
            if(!loaded.citizens().equals(city.frame().citizens())) throw new AssertionError("Save mismatch");
            String report="Main.run full application: PASS\nInitial learners: age 5, NONE, study 0, separate houses.\n"+
                "No age, degree or study mutations after fixture creation.\n"+
                "Ticks: "+ticks+" | Render frames: "+frames+"\nStages: "+journey.stages+
                "\nVisited schools: "+journey.schools+"\nMax study: "+journey.maxStudy+
                "\nActivities: "+journey.activities+"\nCaptures: "+captures+"\nSave/reload: PASS\n";
            completedReport=report;
            city=PopulationJourneyTest.fixture(ground,terrain,false);((LocalGame)get(game,"local")).city=city;
            edge=true;done=false;return;
        }
        if(done && edge) {
            Files.writeString(output.resolve("population-main-results.txt"),completedReport+"Disconnected-road edge: PASS; 1200 ticks, zero study, NONE education.\n");
            glfwSetWindowShouldClose(((Window)get(game,"window")).getHandle(),true);
        }
    }
    public static void main(String[] args) throws Exception {
        Path out=Path.of(args[0]);Files.createDirectories(out);
        var game=new Main();set(game,"offlineSave",Path.of("target/population-main/world.dat"));
        set(game,"gameConfig",new GameConfig(true,true,60,8));
        game.run(new PopulationMainPlaytest(out));
        System.out.println("PASS: Main zero-study education journeys and qualified jobs");
    }
}
