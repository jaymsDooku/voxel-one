package dev.jayms;

import dev.jayms.net.*;
import dev.jayms.net.city.*;
import dev.jayms.ui.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import org.lwjgl.opengl.GL;
import static org.lwjgl.opengl.GL33.*;
import static org.lwjgl.glfw.GLFW.*;

/** Native game UI and paid simulation playtest using only an isolated synthetic city. */
public final class ResourceProgressionSmoke {
    static void check(boolean value, String label) { if (!value) throw new AssertionError(label); }
    static void capture(Path file) throws Exception {
        var pixels=ByteBuffer.allocateDirect(1280*900*4); glReadPixels(0,0,1280,900,GL_RGBA,GL_UNSIGNED_BYTE,pixels);
        var image=new BufferedImage(1280,900,BufferedImage.TYPE_INT_RGB);
        for(int y=0;y<900;y++)for(int x=0;x<1280;x++){int i=((899-y)*1280+x)*4;image.setRGB(x,y,((pixels.get(i)&255)<<16)|((pixels.get(i+1)&255)<<8)|(pixels.get(i+2)&255));}
        ImageIO.write(image,"png",file.toFile());
    }
    static void render(MayorDashboard mayor, CitySimulation city, Path file) throws Exception {
        glClear(GL_COLOR_BUFFER_BIT);
        try(var ui=new Overlay()){ui.begin(1280,900);mayor.render(ui,1280,900,city.frame(),"Esc",true);ui.end();}
        check(glGetError()==GL_NO_ERROR,"OpenGL error");capture(file);
    }
    public static void main(String[] args) throws Exception {
        Path evidence=Path.of(args[0]), profile=Path.of(args[1]);
        check(!Files.exists(profile),"Use a fresh synthetic profile");Files.createDirectories(profile);Files.createDirectories(evidence);
        check(glfwInit(),"GLFW on inherited role display");
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,3);glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,3);
        glfwWindowHint(GLFW_OPENGL_PROFILE,GLFW_OPENGL_CORE_PROFILE);
        long window=glfwCreateWindow(1280,900,"Voxel One resource progression playtest",0,0);
        check(window!=0,"Native window");glfwMakeContextCurrent(window);GL.createCapabilities();
        var ground=new CityTest.Ground();
        var config=new GameConfig(true,false,60,10);
        var seed=new CitySimulation(config,ground,ground.terrain,null);
        var factories=new ArrayList<CityFrame.Building>();
        for(int i=0;i<17;i++) factories.add(new CityFrame.Building(1000+i,0,2,-80+(i%6)*10,27,70+(i/6)*10,16,0));
        var f=seed.frame();
        var fixture=new CityFrame(config,0,f.roads(),f.zones(),factories,f.citizens(),f.horses(),f.economy(),f.addresses(),Agriculture.State.empty());
        var city=new CitySimulation(config,ground,ground.terrain,fixture);
        for(var b:factories){int kind=32+b.id()-1000;var firm=city.economy.companies().stream().filter(c->c.kind==kind).findFirst().orElseThrow();
            var p=city.economy.property(b.id());city.economy.properties.set(city.economy.properties.indexOf(p),new CityEconomy.Property(b.id(),0,firm.id,firm.id,100,0));
            ground.apply(StructureBlueprint.generate(2,kind,b.x(),b.y(),b.z()));
        }
        for(var firm:city.economy.companies())firm.cash=100000;
        var mayor=new MayorDashboard();mayor.show();
        mayor.click(900,100,1280,900,city.frame(),id->{});check(mayor.tab==4,"Business tab mouse click");
        mayor.click(600,150,1280,900,city.frame(),id->{});check(mayor.businesses.view==2,"Resource progression mouse click");
        render(mayor,city,evidence.resolve("resource-progression-settlement.png"));
        city.command(new CityCommand(CityCommand.ROAD,0,List.of(new Polygon.Point(-10,24),new Polygon.Point(-100,24))),1,new Protocol.Pose(1,8,40,24,0,0));
        city.command(new CityCommand(CityCommand.ROAD,0,List.of(new Polygon.Point(-100,24),new Polygon.Point(-100,150))),1,new Protocol.Pose(1,8,40,24,0,0));
        String zone=city.command(new CityCommand(CityCommand.ZONE,2,List.of(new Polygon.Point(-98,110),new Polygon.Point(-40,110),new Polygon.Point(-40,150),new Polygon.Point(-98,150))),1,new Protocol.Pose(1,8,40,24,0,0));
        check(city.frame().zones().stream().anyMatch(z->z.type()==2&&z.polygon().contains(-60,130)),"Industrial zone placed: "+zone);
        var locked=city.economy.companies().stream().filter(c->c.kind==48).findFirst().orElseThrow();
        var work=new CityHarvesting(ground,ground.terrain,(x,z)->false);
        var before=city.economy.resources.state().stocks();double lockedCash=locked.cash;
        work.work(city.economy,locked,1);check(before.equals(city.economy.resources.state().stocks())&&locked.cash==lockedCash,"Locked firm has no output or spending");
        // Synthetic inputs and trained staff isolate paid production from population growth.
        // Unlock proofs themselves are earned only through the running simulation.
        var citizens=city.ecs.query(CitySimulation.Household.class);
        check(citizens.size()>=18,"Synthetic crew roster");
        var logging=city.economy.companies().stream().filter(c->c.kind==3).findFirst().orElseThrow();
        city.economy.resources.add(0,logging.id,Blocks.WOOD,64*CityMaterials.UNIT);
        var paidSamples=new ArrayList<String>();
        double initialWages=city.economy.companies().stream().mapToDouble(c->c.wages).sum();
        for(int target=2;target<=8;target++){
            System.out.println("Playtest: begin tier "+target);
            int allowed=target-1;
            for(var recipe:city.economy.resources.catalog.recipes()) {
                if(!recipe.id().startsWith("industrial-")||IndustrialProgression.required(recipe.companyKind())>allowed)continue;
                var firm=city.economy.companies().stream().filter(c->c.kind==recipe.companyKind()).findFirst().orElseThrow();
                for(var input:recipe.inputs().entrySet())city.economy.resources.add(0,firm.id,input.getKey(),64*CityMaterials.UNIT);
            }
            for(int second=0;second<60&&IndustrialProgression.tier(city.economy.resources.state())<target;second++){
                for(int i=0;i<18;i++){
                    int id=citizens.get(i);var h=city.ecs.get(id,CitySimulation.Household.class);var pos=city.ecs.get(id,CitySimulation.Position.class);
                    var needs=city.ecs.get(id,CitySimulation.Needs.class);needs.hunger=100;city.life(id).education=CitizenLife.Education.TECHNICAL;city.life(id).school=0;
                    var travel=city.ecs.get(id,CitySimulation.Travel.class);travel.retryAt=0;travel.mealUntil=0;travel.route.clear();
                    if(i==17){h.job=CityMaterials.YARD+logging.id;pos.x=-1.5f;pos.z=25.5f;}
                    else{var b=factories.get(i);h.job=b.id();pos.x=b.x()+2.5f;pos.z=b.z()+2.5f;}
                    pos.y=28.01f;travel.target=h.job;
                }
                city.advance(1);
                if(second%25==0)System.out.println("Playtest: tier "+target+" step "+second+" current "+IndustrialProgression.tier(city.economy.resources.state())+" needs "+IndustrialProgression.missing(city.economy.resources.state()));
            }
            check(IndustrialProgression.tier(city.economy.resources.state())>=target,"Paid unlock tier "+target);
            check(city.economy.companies().stream().mapToDouble(c->c.wages).sum()>initialWages,"Paid crews earned tier "+target);
            paidSamples.add("tier "+target+": prerequisite batches earned in paid simulation");
        }
        var advanced=city.economy.companies().stream().filter(c->c.kind==48).findFirst().orElseThrow();
        for(var recipe:city.economy.resources.catalog.recipes(48))
            for(var input:recipe.inputs().entrySet())city.economy.resources.add(0,advanced.id,input.getKey(),64*CityMaterials.UNIT);
        long electricityBefore=city.economy.resources.available(0,advanced.id,IndustrialProgression.POWER);
        for(int second=0;second<5;second++) {
            int id=citizens.get(16); var b=factories.get(16);
            city.ecs.get(id,CitySimulation.Household.class).job=b.id();
            var pos=city.ecs.get(id,CitySimulation.Position.class);pos.x=b.x()+2.5f;pos.z=b.z()+2.5f;
            city.ecs.get(id,CitySimulation.Needs.class).hunger=100;
            var travel=city.ecs.get(id,CitySimulation.Travel.class);travel.target=b.id();travel.route.clear();travel.retryAt=0;travel.mealUntil=0;
            city.advance(1);
        }
        for(var recipe:city.economy.resources.catalog.recipes(48))
            check(IndustrialProgression.completed(city.economy.resources.state(),recipe.id())>0,"Paid advanced line "+recipe.id());
        check(city.economy.resources.available(0,advanced.id,IndustrialProgression.POWER)<electricityBefore,"Advanced production consumes electricity");
        check(IndustrialLogistics.denseHousing(city.economy.resources.state()),"Electronics and alloys unlock dense housing");
        for(var developer:city.economy.companies())if(developer.kind==0)
            for(int material:new int[]{Blocks.WOOD,Blocks.STONE,Blocks.PLANKS,Blocks.BRICKS,Blocks.GLASS,Blocks.LED})
                city.economy.resources.add(0,developer.id,material,1024*CityMaterials.UNIT);
        var dense=city.economy.buyPlot(1,0,80,27,100,0);
        check(dense!=null&&city.economy.resources.project(dense.id()).businessKind()==50,"Dense housing plan");
        check(city.economy.resources.reserve(dense),"Dense housing material reservation");
        city.economy.work(dense.id(),8);city.advance(1);
        check(city.frame().buildings().stream().anyMatch(b->b.x()==80&&b.z()==100&&b.type()==0&&b.capacity()==8),"Finished eight-resident housing");
        render(mayor,city,evidence.resolve("resource-progression-advanced.png"));
        mayor.businesses.view=0;mayor.businesses.selected=1016;
        render(mayor,city,evidence.resolve("resource-progression-manufacturing.png"));
        mayor.businesses.view=2;mayor.businesses.selected=0;
        var stock=city.economy.resources;
        int buyer=city.economy.companies().stream().filter(c->c.kind==0).findFirst().orElseThrow().id;
        // Isolate the coal train case from fleets the developer may have bought during construction.
        for(int asset:new int[]{IndustrialProgression.MODERN_TRAIN,IndustrialProgression.ELECTRIC_RAIL,
                IndustrialProgression.TRUCK,IndustrialProgression.CAR,IndustrialProgression.ADVANCED_VEHICLE}) {
            long held=stock.available(0,buyer,asset);if(held>0)stock.remove(0,buyer,asset,held);
        }
        stock.add(0,buyer,IndustrialProgression.FREIGHT_TRAIN,CityMaterials.UNIT);stock.add(0,buyer,IndustrialProgression.STATION,CityMaterials.UNIT);
        stock.add(0,buyer,IndustrialProgression.COAL,8*CityMaterials.UNIT);
        long coal=stock.available(0,buyer,IndustrialProgression.COAL);
        check(IndustrialLogistics.deliver(stock,0,buyer,300,16*CityMaterials.UNIT),"Freight train delivery");
        check(stock.available(0,buyer,IndustrialProgression.COAL)<coal,"Freight burns coal");
        check(!IndustrialLogistics.deliver(stock,0,buyer,900,CityMaterials.UNIT),"Out of range freight rejected");
        var denseBuilding=city.frame().buildings().stream().filter(b->b.x()==80&&b.z()==100&&b.type()==0).findFirst().orElseThrow();
        String removed=city.command(new CityCommand(CityCommand.DEMOLISH,denseBuilding.id(),List.of()),1,new Protocol.Pose(1,8,40,24,0,0));
        check(city.frame().buildings().stream().noneMatch(b->b.id()==denseBuilding.id()),"Dense house demolition: "+removed);
        check(ground.type(80,36,100)==Blocks.AIR,"Demolition clears the upper floor roof");
        Path save=profile.resolve("city.dat");city.save(save);var saved=CitySimulation.load(save);
        check(city.frame().equals(saved),"Save/reload preserves exact frame and unlock proofs");
        var reloaded=new CitySimulation(config,ground,ground.terrain,saved);
        check(IndustrialProgression.tier(reloaded.economy.resources.state())==8,"Reload retains tier 8");
        String report="{\n  \"result\": \"passed\",\n  \"platform\": \"Linux native GLFW/OpenGL; inherited role display\",\n  \"profile\": \"isolated synthetic trained crews and seeded input stocks; 60-second production rate at fixed workday hour\",\n  \"playtest\": \"Click Businesses then Resource progression; place industrial zone; run paid factory shifts through all eight eras; test locked work, freight energy and range, advanced manufacturing, reserved dense housing and full-height demolition, and exact save/reload\",\n  \"unlockSamples\": \""+String.join("; ",paidSamples)+"\"\n}\n";
        Files.writeString(evidence.resolve("resource-progression-playtest.json"),report);
        glfwDestroyWindow(window);glfwTerminate();System.out.println("Resource progression Playtest: passed; eight paid era unlocks, native UI captures, locked work, freight limits and save/reload");
    }
}
