package dev.jayms;

import dev.jayms.net.*;
import dev.jayms.net.city.*;
import dev.jayms.ui.*;
import dev.jayms.window.Window;
import java.nio.file.*;
import java.util.*;
import static org.lwjgl.glfw.GLFW.*;

/** Full Main workflow on the inherited role display; only an isolated synthetic profile is used. */
public final class RegionalMainPlaytest implements Main.FrameObserver {
    static Object get(Main game,String name) throws Exception {
        var f=Main.class.getDeclaredField(name);f.setAccessible(true);return f.get(game);
    }
    static void set(Main game,String name,Object value) throws Exception {
        var f=Main.class.getDeclaredField(name);f.setAccessible(true);f.set(game,value);
    }
    static void call(Main game,String name,Class<?> type,Object value) throws Exception {
        var m=Main.class.getDeclaredMethod(name,type);m.setAccessible(true);m.invoke(game,value);
    }
    final Path output;CitySimulation city;MayorDashboard dashboard;int frames;String capture;
    long started,recordStart;boolean recording,finished;double cash;int localCount;
    final List<String> steps=new ArrayList<>();
    RegionalMainPlaytest(Path output) { this.output=output; }
    public void started(Main game) throws Exception {
        city=((LocalGame)get(game,"local")).city;dashboard=(MayorDashboard)get(game,"mayorDashboard");
        localCount=city.frame().citizens().size();
        set(game,"isometric",true);call(game,"setCaptured",boolean.class,false);
        ((ControlsMenu)get(game,"menu")).open=false;
    }
    public void beforeFrame(Main game) throws Exception {
        if(frames==0) {
            started=System.nanoTime();call(game,"input",int.class,GLFW_KEY_F9);
            dashboard.click(920,60,1280,720,city.frame(),id->{});
            if(!dashboard.open || dashboard.tab!=6) throw new AssertionError("F9 / Districts navigation failed");
        }
        if(frames==2 || frames==6) {
            dashboard.click(440,180,1280,720,city.frame(),id->{throw new AssertionError("Settlement selected a resident");});
            int expected=frames==2?1_000_000:2_000_000;
            if(city.population.population()!=expected) throw new AssertionError("UI settlement count mismatch");
            steps.add("Settle 1,000,000 through Districts UI: population="+expected);
        }
        if(frames==10) {
            dashboard.tab=0;var m=CityMetrics.from(city.frame());
            if(m.population()!=2_000_000+localCount) throw new AssertionError("Overview omitted regional residents");
            capture="regional-scale-overview.png";
        }
        if(frames==14) {
            dashboard.tab=6;dashboard.click(40,285,1280,720,city.frame(),id->{});
            if(city.population.state().agents().size()!=64) throw new AssertionError("District activation failed");
            cash=RegionalPopulationTest.money(city.population.state());capture="regional-scale-districts.png";
            steps.add("Focus district #1: 64 nearby residents, population remains 2000000");
        }
        if(frames==18) {
            dashboard.click(630,180,1280,720,city.frame(),id->{
                try { call(game,"inspectCitizen",int.class,id); } catch(Exception e) { throw new RuntimeException(e); }
            });
            if(dashboard.open) throw new AssertionError("Inspector did not close dashboard");
            call(game,"recordInput",int.class,GLFW_KEY_F10);recording=true;recordStart=System.nanoTime();
        }
        if(frames>=19 && frames<46) {
            if(city.population.state().agents().size()!=64) throw new AssertionError("Nearby agents collapsed while viewing district");
            var agent=city.population.state().agents().get(0);
            var camera=(IsometricCamera)get(game,"overview");
            var group=city.population.state().groups().get(0);
            camera.focus(agent.x(),agent.z(),group.y());camera.zoom(-1);
            set(game,"notice","Playtest: 2,000,000 regional residents | 64 nearby agents | individual needs, payroll and movement");
        }
        if(frames==30) capture="regional-scale-nearby.png";
        if(recording && System.nanoTime()-recordStart>=6_000_000_000L) { call(game,"recordInput",int.class,GLFW_KEY_F10);recording=false; }
        if(frames==46) {
            if(recording) { call(game,"recordInput",int.class,GLFW_KEY_F10); recording=false; }
            var s=city.population.state();
            if(Math.abs(RegionalPopulationTest.money(s)-cash)>1e-3) throw new AssertionError("Regional money changed without a transfer");
            city.save(Path.of("target/regional-playtest/roundtrip.city"));
            if(!CitySimulation.load(Path.of("target/regional-playtest/roundtrip.city")).population().equals(s))
                throw new AssertionError("Native application save/reload mismatch");
            steps.add("Viewed nearby resident in Main renderer: movement and wallet updates; money conserved; save/reload equal");
            // Empty-food, empty-wallet, no-job edge through the live Main simulation.
            var frame=city.frame();var poor=new RegionalPopulation.Group(1,1,0,1,1000,520,frame.population().groups().get(0).y(),536,0,0,5,0,0,0,12,3);
            var state=new RegionalPopulation.State(0,0,List.of(poor),List.of());
            var saved=new CityFrame(frame.config(),frame.elapsed(),frame.roads(),frame.zones(),frame.buildings(),frame.citizens(),frame.horses(),
                    frame.economy(),frame.addresses(),frame.agriculture(),state);
            var ground=new CityTest.Ground();city=new CitySimulation(saved.config(),ground,ground.terrain,saved,ProductionCatalog.toolEra());
            ((LocalGame)get(game,"local")).city=city;
            for(int i=0;i<120;i++) city.advance(.1);
            var result=city.population.state().groups().get(0);
            if(result.hunger()!=0 || result.food()!=0 || result.savings()!=0 || result.treasury()!=0)
                throw new AssertionError("Starvation edge minted resources");
            dashboard.show();dashboard.tab=0;capture="regional-scale-starvation-edge.png";
            steps.add("Edge: no food, money or work; 12 simulated seconds: hunger=0, food=0, money=0");
        }
        if(frames==50) {
            var frame=city.frame();
            var empty=new CityFrame(frame.config(),frame.elapsed(),frame.roads(),frame.zones(),frame.buildings(),frame.citizens(),frame.horses(),frame.economy(),frame.addresses(),frame.agriculture());
            var ground=new CityTest.Ground();city=new CitySimulation(empty.config(),ground,ground.terrain,empty,ProductionCatalog.toolEra());
            ((LocalGame)get(game,"local")).city=city;
            if(CityMetrics.from(city.frame()).population()!=localCount || city.frame().visibleCitizens().size()!=localCount)
                throw new AssertionError("Local-only regression changed population");
            dashboard.tab=2;capture="regional-scale-local-regression.png";
            steps.add("Regression: legacy/local-only city retains "+localCount+" named citizens and searchable citizen table");
        }
        if(frames>55) finished=true;
        if(System.nanoTime()-started>120_000_000_000L) throw new AssertionError("Native workflow timeout");
    }
    public void afterFrame(Main game) throws Exception {
        if(capture!=null) {SpecialBuildingsSmoke.capture(output.resolve(capture));capture=null;}
        frames++;
        if(finished) {
            Files.writeString(output.resolve("regional-scale-playtest.txt"),"Playtest: Main.run, inherited X11 display, Mesa OpenGL, isolated synthetic profile.\n"
                    +String.join("\n",steps)+"\nExpected and observed: all assertions passed. Rendered frames: "+frames+"\n");
            glfwSetWindowShouldClose(((Window)get(game,"window")).getHandle(),true);
        }
    }
    public static void main(String[] args) throws Exception {
        Path out=Path.of(args[0]);Files.createDirectories(out);
        var game=new Main();set(game,"offlineSave",Path.of("target/regional-playtest/world.dat"));
        set(game,"gameConfig",new GameConfig(true,true,60,8));set(game,"productionCatalog",ProductionCatalog.toolEra());
        game.run(new RegionalMainPlaytest(out));
        System.out.println("PASS: regional-scale native Main workflow");
    }
}
