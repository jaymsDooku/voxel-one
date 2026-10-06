import dev.jayms.*;
import dev.jayms.net.*;
import dev.jayms.net.city.*;
import dev.jayms.ui.*;
import java.nio.file.*;
import java.util.*;
import static org.lwjgl.glfw.GLFW.*;

/** Actual Main, automatic factory employment and synthetic offline profile. */
public class FactoryStaffingSmoke extends RoadSpacingSmoke {
    static volatile boolean recording;
    static int factory, company;
    static long processed;
    static int captureSecond = 700;
    public static void main(String[] args) throws Exception {
        Path out=Path.of(args[0]);Files.createDirectories(out);
        if(args.length>1){RoadSpacing.configure("--pedestrian-spacing",args[1]);RoadSpacing.configure("--mounted-spacing",args[1]);}
        game=new Main();set("gameConfig",new GameConfig(true,false,1200,10));
        set("productionCatalog",ProductionCatalog.toolEra());set("offlineSave",out.resolve("world.dat"));
        glfwInitHint(GLFW_PLATFORM,GLFW_PLATFORM_X11);
        var driver=new Thread(()->{
            long handle=0;
            try {
                await(()->get("player")!=null && latest!=null,"Engine and automatic factory ready");
                handle=((dev.jayms.window.Window)get("window")).getHandle();
                input("search","--onlyvisible","--name","^Voxel One","windowfocus");
                if(((ControlsMenu)get("menu")).open)input("key","Escape");
                if(!(boolean)get("isometric"))input("key","F6");
                var b=latest.buildings().stream().filter(x->x.id()==factory).findFirst().orElseThrow();
                var view=(IsometricCamera)get("overview");view.focus(b.x()+3,b.z()+3,b.y()+1);view.zoom(4);
                ((BuildingInfo)get("buildingInfo")).show(factory,0);
                input("key","Right");input("key","Right");
                input("key","F10");recording=true;Thread.sleep(8000);input("key","Right");Thread.sleep(4000);input("key","F10");Thread.sleep(2500);
                require(processed>0,"Automatic factory production");
                require(latest.citizens().stream().anyMatch(c->c.job()==factory),"Automatic factory crew");
                Files.writeString(out.resolve("results.json"),"{\"status\":\"passed\",\"platform\":\"Linux inherited X11; Mesa\",\"profile\":\"isolated synthetic offline city\",\"simulatedSecondsBeforeCapture\":"+captureSecond+",\"pedestrianGapBlocks\":"+RoadSpacing.configured().pedestrians()+",\"mountedGapBlocks\":"+RoadSpacing.configured().mounted()+",\"processedAt700\":"+processed+",\"checks\":[\"automatic staffing without job mutation\",\"eligible graduate commute\",\"factory production by 700 seconds; both tool deliveries by 1200 seconds\",\"shop coverage retained\",\"native factory inspector\"],\"recording\":\"engine F10 recorder\"}\n");
            }catch(Throwable e){failure=e;}
            finally{if(handle!=0)glfwSetWindowShouldClose(handle,true);}
        });driver.setDaemon(true);
        game.run(new Main.FrameObserver(){
            int frames;
            public void started(Main running) throws Exception {
                var city=((LocalGame)get("local")).city;
                var pose=new Protocol.Pose(1,8,40,24,0,0);
                city.command(new CityCommand(CityCommand.ROAD,0,List.of(new Polygon.Point(-10,24),new Polygon.Point(-60,24))),1,pose);
                city.command(new CityCommand(CityCommand.ZONE,2,List.of(new Polygon.Point(-50,26),new Polygon.Point(-10,26),new Polygon.Point(-10,50),new Polygon.Point(-50,50))),1,pose);
                for(int i=0;i<700;i++)city.advance(1);
                var f=city.economy.companies().stream().filter(x->x.kind==8).findFirst().orElseThrow();company=f.id;
                factory=city.economy.properties.stream().filter(p->p.operator()==company).findFirst().orElseThrow().building();
                processed=city.economy.resources.production(company).processed();require(processed>0,"Automatic factory produces by 700 seconds");
                int mine=city.economy.companies().stream().filter(x->x.kind==2).findFirst().orElseThrow().id;
                int logging=city.economy.companies().stream().filter(x->x.kind==3).findFirst().orElseThrow().id;
                while(args.length>1 && captureSecond<1200 &&
                        (city.economy.resources.productivity(mine,2)!=2 || city.economy.resources.productivity(logging,3)!=2)) {
                    city.advance(1);captureSecond++;
                }
                require(city.economy.resources.productivity(mine,2)==2,"Tools traded to mine");
                require(city.economy.resources.productivity(logging,3)==2,"Tools traded to logging crew");
                require(city.frame().citizens().stream().anyMatch(c->c.job()==factory && city.eligible(factory,c.id())),"Eligible factory worker");
                require(city.frame().citizens().stream().anyMatch(c->city.frame().buildings().stream().anyMatch(b->b.id()==c.job() && b.type()==1)),"Shop coverage retained");
                latest=city.frame();driver.start();
            }
            public void afterFrame(Main running) throws Exception {
                latest=((LocalGame)get("local")).city.frame();
                if(recording && ++frames==5){
                    int[] sz=((dev.jayms.window.Window)get("window")).getSize();int w=sz[0],h=sz[1];var px=org.lwjgl.BufferUtils.createByteBuffer(w*h*4);
                    org.lwjgl.opengl.GL11.glReadPixels(0,0,w,h,org.lwjgl.opengl.GL11.GL_RGBA,org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE,px);
                    var im=new java.awt.image.BufferedImage(w,h,java.awt.image.BufferedImage.TYPE_INT_RGB);
                    for(int y=0;y<h;y++)for(int x=0;x<w;x++){int i=((h-y-1)*w+x)*4;im.setRGB(x,y,((px.get(i)&255)<<16)|((px.get(i+1)&255)<<8)|(px.get(i+2)&255));}
                    javax.imageio.ImageIO.write(im,"png",out.resolve("factory.png").toFile());
                }
            }
        });driver.join(1000);if(failure!=null)throw new AssertionError("Factory playtest failed",failure);require(Files.exists(out.resolve("results.json")),"Factory playtest complete");
    }
}
