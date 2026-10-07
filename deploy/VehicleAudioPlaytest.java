import dev.jayms.*;
import dev.jayms.audio.VehicleAudio;
import dev.jayms.player.*;
import dev.jayms.net.*;
import dev.jayms.net.city.*;
import dev.jayms.ui.*;
import org.joml.Vector3f;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import javax.sound.sampled.*;
import java.io.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.openal.AL10.*;
import static org.lwjgl.openal.SOFTLoopback.*;

/** Real Main, X11 car input, automatic railway and aviation, native OpenAL rendered PCM. */
public final class VehicleAudioPlaytest {
    static Main game;
    static Path out;
    static VehicleAudio audio;
    static long device;
    static volatile long frames;
    static volatile boolean ready, done;
    static volatile Throwable failure;
    static final Queue<Runnable> tasks = new ConcurrentLinkedQueue<>();
    static Object get(String key) throws Exception {var f=Main.class.getDeclaredField(key);f.setAccessible(true);return f.get(game);}
    static void set(String key,Object value) throws Exception {var f=Main.class.getDeclaredField(key);f.setAccessible(true);f.set(game,value);}
    static void require(boolean value,String check){if(!value)throw new AssertionError(check);}
    interface Check {boolean ok() throws Exception;}
    static void await(Check check,String label) throws Exception {long end=System.nanoTime()+45_000_000_000L;while(!check.ok()&&System.nanoTime()<end)Thread.sleep(50);require(check.ok(),label);}
    static void edit(Runnable action) throws Exception {var latch=new CountDownLatch(1);tasks.add(()->{try{action.run();}catch(Throwable t){failure=t;}finally{latch.countDown();}});require(latch.await(20,TimeUnit.SECONDS),"Render action completed");if(failure!=null)throw new AssertionError(failure);}
    static void x(String...args) throws Exception {var cmd=new ArrayList<String>(List.of("xdotool"));cmd.addAll(List.of(args));var p=new ProcessBuilder(cmd).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();require(p.waitFor()==0,"X11 input "+args[0]);long start=frames;await(()->frames>start+1,"Frames after input");}
    static Jeep car() throws Exception {return (Jeep)get("jeep");}
    static LocalGame local() throws Exception {return (LocalGame)get("local");}
    static World world() throws Exception {return (World)get("world");}
    static void image(String name) throws Exception {edit(()->{try{JeepPlaytest.game=game;JeepPlaytest.output=out;JeepPlaytest.screenshot(name);require(JeepPlaytest.failure==null,"Screenshot saved");}catch(Exception e){throw new RuntimeException(e);}});}
    static double pcm(String name,boolean audible) throws Exception {
        final double[] rms={0};
        edit(()->{
            short[] warmup=new short[8192];alcRenderSamplesSOFT(device,warmup,4096);
            short[] samples=new short[44100];alcRenderSamplesSOFT(device,samples,22050);
            double sum=0;byte[] bytes=new byte[samples.length*2];
            for(int i=0;i<samples.length;i++){sum+=(double)samples[i]*samples[i];bytes[i*2]=(byte)samples[i];bytes[i*2+1]=(byte)(samples[i]>>8);}
            rms[0]=Math.sqrt(sum/samples.length);
            require(audible?rms[0]>100:rms[0]<1,"PCM "+name+" audible="+audible+" RMS="+rms[0]);
            require(alGetError()==AL_NO_ERROR,"OpenAL has no errors");
            if(audible)try(var input=new AudioInputStream(new ByteArrayInputStream(bytes),new AudioFormat(22050,16,2,true,false),22050)){AudioSystem.write(input,AudioFileFormat.Type.WAVE,out.resolve(name+".wav").toFile());}catch(IOException e){throw new RuntimeException(e);}
        });return rms[0];
    }
    static CitySimulation.Ground ground=new CitySimulation.Ground(){public int type(int x,int y,int z){return y==32?Blocks.GRASS:0;}public boolean occupied(int x,int y,int z,int w,int d){return false;}public void apply(List<Protocol.Edit> edits){try{for(var e:edits)world().apply(e);}catch(Exception e){throw new RuntimeException(e);}}};
    static void fixture(boolean rail,boolean plane,int flightStage,double clock) {
        try {
            CityFrame base=local().city.frame();
            var buildings=new ArrayList<CityFrame.Building>();var tracks=new ArrayList<Railway.Track>();var trains=new ArrayList<Railway.Train>();
            if(rail){
                buildings.add(new CityFrame.Building(1,0,SpecialBuildings.RAIL_DEPOT,-12,33,-8,0,0));
                buildings.add(new CityFrame.Building(2,0,SpecialBuildings.RAIL_STATION,8,33,-8,0,0));
                buildings.add(new CityFrame.Building(3,0,SpecialBuildings.RAIL_STATION,28,33,-8,0,0));
                for(int i=-10;i<=30;i++)tracks.add(new Railway.Track(i,0,33));
                trains.add(new Railway.Train(1,1,-9.5f,33,.5f,90,0,1,0,List.of()));
            }
            var flights=new ArrayList<Aviation.Flight>();var citizens=new ArrayList<CityFrame.Citizen>();
            if(plane){
                buildings.add(new CityFrame.Building(4,0,SpecialBuildings.AIRPORT,-12,33,12,8,0));
                buildings.add(new CityFrame.Building(5,0,SpecialBuildings.AIRPORT,80,33,12,8,0));
                citizens.add(new CityFrame.Citizen(9,"Synthetic passenger",0,-4,34,30,0,0,100,100,0,0,0,"Flying"));
                flights.add(new Aviation.Flight(1,4,5,9,0,0,flightStage,clock));
            }
            var config=new GameConfig(true,false,1200,10);
            var state=new CityFrame(config,base.elapsed()+1,List.of(),List.of(),buildings,citizens,List.of(),base.economy(),CityAddresses.migrate(List.of(),buildings),Agriculture.State.empty(),RegionalPopulation.State.empty(),new Aviation.State(flights),new Railway.State(tracks,trains));
            local().city=new CitySimulation(config,ground,world().terrain(),state);
            set("isometric",true);set("captured",false);
            var camera=(IsometricCamera)get("overview");camera.cityMode();camera.focus(rail?0:-4,rail?0:30,34);camera.zoom(-5);
        }catch(Exception e){throw new RuntimeException(e);}
    }
    public static void main(String[] args) throws Exception {
        out=Path.of(args[0]);Files.createDirectories(out);game=new Main();set("offlineSave",out.resolve("synthetic.dat"));
        glfwInitHint(GLFW_PLATFORM,GLFW_PLATFORM_X11);
        var constructor=VehicleAudio.class.getDeclaredConstructor(boolean.class);constructor.setAccessible(true);audio=constructor.newInstance(true);
        require(audio.available(),"Native OpenAL loopback output available");var d=VehicleAudio.class.getDeclaredField("device");d.setAccessible(true);device=d.getLong(audio);set("vehicleAudio",audio);
        Thread driver=new Thread(()->{
            try {
                await(()->ready&&frames>=3,"Application ready");
                String id=Long.toString(org.lwjgl.glfw.GLFWNativeX11.glfwGetX11Window(((dev.jayms.window.Window)get("window")).getHandle()));
                x("windowfocus",id);
                if(((ControlsMenu)get("menu")).open)x("key","Escape");
                if(!(boolean)get("captured"))x("key","Tab");
                pcm("parked",false);x("key","j");require(car().driving(),"Real J enters car");
                edit(()->require(audio.engineState()==AL_PLAYING,"Entered car engine plays"));pcm("vehicle-audio-car-idle",true);
                final float[] idle={0};edit(()->idle[0]=audio.enginePitch());
                x("keydown","w");await(()->car().speed()>4,"Car accelerates");
                edit(()->require(audio.enginePitch()>idle[0],"Engine pitch rises with speed"));image("vehicle-audio-car.png");pcm("vehicle-audio-car-driving",true);
                x("keyup","w");await(()->Math.abs(car().speed())<.01,"Car stops");
                x("key","Escape");pcm("menu-muted",false);x("key","Escape");pcm("resumed-engine",true);
                x("key","j");require(!car().driving(),"Stopped J exits car");pcm("exited",false);
                edit(()->fixture(true,false,0,0));await(()->audio.trainSources()==1,"Automatic moving train source starts");
                image("vehicle-audio-train.png");pcm("vehicle-audio-train-moving",true);
                x("key","F10");Thread.sleep(1800);x("key","F10");
                await(()->local().city.frame().railway().trains().get(0).dwell()>0,"Train reaches station dwell");
                await(()->audio.trainSources()==0,"Dwell stops train sound");pcm("station-dwell",false);
                edit(()->fixture(false,false,0,0));pcm("removed-train",false);
                edit(()->fixture(false,true,2,0));await(()->audio.takeoffSources()==1,"Takeoff source starts");image("vehicle-audio-takeoff.png");pcm("vehicle-audio-plane-takeoff",true);
                edit(()->fixture(false,true,2,8));await(()->audio.takeoffSources()==0,"Cruise has no takeoff loop");pcm("cruise",false);
                edit(()->fixture(false,true,3,0));pcm("landed",false);
                edit(()->fixture(false,true,1,0));pcm("boarding",false);
                edit(()->{fixture(false,true,2,0);try{((IsometricCamera)get("overview")).focus(500,500,34);}catch(Exception e){throw new RuntimeException(e);}});pcm("distant-plane",false);
                edit(()->fixture(false,false,0,0));pcm("removed-plane",false);
                edit(()->{audio.close();audio.close();require(!audio.available(),"Audio close is idempotent");});
                Files.writeString(out.resolve("results.json"),"{\"status\":\"passed\",\"platform\":\"Linux assigned X11 display, Mesa software rendering\",\"profile\":\"isolated synthetic\",\"audio\":\"actual OpenAL Soft native loopback, stereo PCM 22050 Hz, 16 bit; no physical speaker claim\",\"checks\":[\"parked silence\",\"real J enter\",\"W acceleration and engine pitch\",\"menu mute and resume\",\"J exit silence\",\"automatic train motion\",\"station dwell silence\",\"train removal\",\"takeoff PCM\",\"cruise silence\",\"landing silence\",\"boarding silence\",\"distance silence\",\"plane removal\",\"idempotent close\"]}\n");
            }catch(Throwable t){failure=t;try{Files.writeString(out.resolve("failure.txt"),t.getClass().getSimpleName()+": "+t.getMessage());}catch(Exception ignored){}}
            finally{done=true;try{glfwSetWindowShouldClose(((dev.jayms.window.Window)get("window")).getHandle(),true);}catch(Exception ignored){}}
        });driver.setDaemon(true);
        try{game.run(new Main.FrameObserver(){
            public void started(Main g) throws Exception {
                World old=world(),w=new World(old.terrain().seed,old.models());
                for(int x=-4;x<=7;x++)for(int z=-3;z<=4;z++)for(int y=-2;y<=7;y++) {Chunk c=new Chunk();if(y==2)for(int i=0;i<16;i++)for(int k=0;k<16;k++)c.setBlock(i,0,k,Blocks.GRASS);w.addChunk(new ChunkPos(x,y,z),c);}
                set("world",w);old.close();var p=new Player(new Vector3f(11.3f,33.01f,8.5f),-130,-18,(Camera)get("camera"));p.toggleView();set("player",p);set("jeep",new Jeep(new Vector3f(8.5f,33.01f,8.5f),-90));set("isometric",false);ready=true;driver.start();
            }
            public void beforeFrame(Main g){Runnable t;while((t=tasks.poll())!=null)t.run();}
            public void afterFrame(Main g){frames++;}
        });}catch(Throwable t){Files.writeString(out.resolve("failure.txt"),t.getClass().getSimpleName()+": "+t.getMessage());throw t;}
        require(done&&failure==null,"Playtest completed: "+failure);
    }
}
