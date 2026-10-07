import dev.jayms.*;
import dev.jayms.net.*;
import dev.jayms.net.city.*;
import java.nio.file.*;
import static org.lwjgl.glfw.GLFW.*;

/** Native menu workflow for the populated benchmark. Uses only synthetic saves. */
public final class PopulatedStressSmoke extends StressGridSmoke {
    CityFrame populated;
    void focus(int type) throws Exception {
        var b=local().city.frame().buildings().stream().filter(v->v.type()==type).findFirst().orElseThrow();
        ((dev.jayms.player.Player)get(game,"player")).position().set(b.x()+3,b.y()+1,b.z()-1);
        ((IsometricCamera)get(game,"overview")).focus(b.x()+3,b.z()+3,b.y());
        // Warm terrain through the real application API; meshes still use the normal render budget.
        ((World)get(game,"world")).stream(b.x()+3,b.z()+3,81);
    }
    void requireDetailed(int type) throws Exception {
        var b=local().city.frame().buildings().stream().filter(v->v.type()==type).findFirst().orElseThrow();
        var columns=((World)get(game,"world")).renderedColumns();
        for(int x=Math.floorDiv(b.x(),16);x<=Math.floorDiv(b.x()+StructureBlueprint.width(type)-1,16);x++)
            for(int z=Math.floorDiv(b.z(),16);z<=Math.floorDiv(b.z()+StructureBlueprint.depth(type)-1,16);z++)
                require(columns.contains(new ChunkPos(x,0,z)),"Developed building columns fully meshed for type "+type);
    }
    void verifyPopulated() throws Exception {
        var f=local().city.frame();
        int[] targets={192,80,80,48};
        for(int t=0;t<4;t++) { final int type=t; require(f.buildings().stream().filter(b->b.type()==type).count()>=targets[t],"Populated type "+t); }
        require(f.citizens().size()==128,"128 detailed citizens");
        require(f.citizens().stream().filter(c->c.home()>0).count()>=110,"Residents have homes");
        require(f.agriculture().farms().size()>=48 && f.agriculture().fields().size()>=96,"Active farms and fields");
        require(f.economy().properties().stream().filter(p->p.operator()>0).count()>=208,"Business and farm operators");
        var world=(World)get(game,"world");
        var b=f.buildings().getFirst();
        require(world.sample(b.x(),b.y(),b.z())!=Blocks.AIR,"Real building floor exists");
    }
    @Override public void beforeFrame(Main value) throws Exception {
        if(System.nanoTime()<due || rendered<waitFrames) return;
        due=System.nanoTime()+700_000_000L;
        Files.writeString(root.resolve("playtest-stage.txt"),"stage="+step+", starts="+starts+"\n");
        switch(step++) {
            case 0 -> { if(!menu().open) key("Escape"); }
            case 1 -> { require(menu().open,"Controls open"); click(470,32); }
            case 2 -> { require(menu().saves.open,"Saves menu open"); click(100,372); }
            case 3 -> { savedCity=CitySimulation.load(CitySaves.sidecar(active(),".city")); click(80,140); }
            case 4 -> click(420,372);
            case 5 -> { require(starts==2,"Stress save loaded"); key("Escape"); }
            case 6 -> click(470,32);
            case 7 -> { require(menu().saves.open,"City saves open"); click(100,550); }
            case 8 -> { verifyPopulated(); populated=local().city.frame(); capture="populated-stress-menu.png"; pass("Native Develop stress save added 192 houses, 80 businesses, 80 factories, 48 crop farms and filled the 128 citizen limit; real voxel floor and operators checked."); click(100,550); }
            case 9 -> { require(local().city.frame().buildings().equals(populated.buildings()),"Repeat creates no duplicate buildings"); require(local().city.frame().citizens().equals(populated.citizens()),"Repeat creates no duplicate residents"); pass("Repeated native action kept buildings and citizens unchanged."); click(100,372); }
            case 10 -> { populated=CitySimulation.load(CitySaves.sidecar(active(),".city")); click(420,550); }
            case 11 -> { key("Escape"); focus(0); waitFrames=rendered+20; }
            case 12 -> { requireDetailed(0); capture="populated-stress-houses.png"; key("F10"); x("keydown","w"); waitFrames=rendered+12; }
            case 13 -> { x("keyup","w"); key("F10"); focus(1); waitFrames=rendered+20; }
            case 14 -> { requireDetailed(1); capture="populated-stress-businesses.png"; }
            case 15 -> { requireDetailed(2); capture="populated-stress-factories.png"; }
            case 16 -> { requireDetailed(3); capture="populated-stress-farms.png"; verifyPopulated(); pass("Running simulation retained populated city; four populated districts rendered with 20 frames for terrain streaming."); key("Escape"); }
            case 17 -> click(470,32);
            case 18 -> click(100,372);
            case 19 -> { populated=CitySimulation.load(CitySaves.sidecar(active(),".city")); click(80,140); }
            case 20 -> click(420,372);
            case 21 -> { require(starts==3,"Stress save reloaded"); verifyPopulated(); require(local().city.frame().buildings().containsAll(populated.buildings()),"All populated buildings persisted"); require(local().city.frame().citizens().size()==populated.citizens().size(),"Residents persisted"); pass("Save/reload kept all saved buildings, 128 residents, farms and business operators."); key("Escape"); }
            case 22 -> click(470,32);
            case 23 -> click(80,110);
            case 24 -> click(420,372);
            case 25 -> { require(starts==4 && local().city.frame().stressGrid()==null,"Original restored"); require(local().city.frame().roads().equals(savedCity.roads()),"Original roads unchanged"); require(local().city.frame().citizens().size()==savedCity.citizens().size(),"Original residents unchanged"); key("F6"); }
            case 26 -> { require(!(boolean)get(game,"isometric"),"F6 regression"); key("F6"); }
            case 27 -> { capture="populated-stress-original.png"; pass("Original save roads and residents unchanged; F6 toggle works."); }
            case 28 -> glfwSetWindowShouldClose(((dev.jayms.window.Window)get(game,"window")).getHandle(),true);
            default -> throw new AssertionError("Driver did not finish");
        }
    }
    @Override public void afterFrame(Main value) throws Exception {
        String pending=capture;
        super.afterFrame(value);
        // Capture the current district before moving to the next one.
        if("populated-stress-businesses.png".equals(pending)) { focus(2); waitFrames=rendered+20; }
        if("populated-stress-factories.png".equals(pending)) { focus(3); waitFrames=rendered+20; }
    }
    public static void main(String[] args) throws Exception {
        var test=new PopulatedStressSmoke(); test.root=Path.of(args[0]).toAbsolutePath(); test.evidence=Path.of(args[1]);
        Files.createDirectories(test.root); Files.createDirectories(test.evidence);
        var game=new Main(); set(game,"offlineSave",test.root.resolve("offline-city.dat")); set(game,"gameConfig",GameConfig.cityGame());
        glfwInitHint(GLFW_PLATFORM,GLFW_PLATFORM_X11);
        try {
            game.run(test); test.require(test.step==29,"All workflow steps executed");
            Files.writeString(test.evidence.resolve("populated-stress-playtest.txt"),
                "Playtest: PASS. Command: python3 deploy/run_populated_stress_smoke.py --display \"$DISPLAY\"\n"+
                "Environment: Linux native Main; inherited assigned DISPLAY/XAUTHORITY; Mesa software OpenGL; isolated synthetic user.home and saves. No browser workflow applies to this native Java game.\n"+
                "Expected: Develop stress save fills target counts, creates real voxels and operators, repeat adds no duplicates, save/reload persists, Original city and F6 remain intact.\n"+
                "Rendering: district navigation synchronizes player and camera; World.stream preloads 81 columns, then 20 frames use the normal mesh budget. Fully meshed building columns required before capture. Streaming latency is not claimed.\n"+
                "Steps: native Controls > City saves > load Stress Test Grid > Develop stress save > repeat > Save current > render each developed ring > F10 pan > save/reload > load Original > F6.\n"+
                "Observed: "+String.join("\nObserved: ",test.checks)+"\nRendered frames: "+test.rendered+"\n");
        } catch(Throwable e) {
            Files.writeString(test.root.resolve("assertion-failure.txt"),"stage="+test.step+", starts="+test.starts+", "+e.getClass().getSimpleName()+": "+e.getMessage()+"\n"); throw e;
        }
    }
}
