import dev.jayms.*;
import dev.jayms.net.*;
import dev.jayms.net.model.*;
import dev.jayms.render.*;
import dev.jayms.window.Window;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;

/** Production world switching with active sky work, plus cleanup fault injection. Synthetic data only. */
public class StreamingShutdownWorkflow {
    static Object get(Main m,String n)throws Exception{var f=Main.class.getDeclaredField(n);f.setAccessible(true);return f.get(m);}
    static void set(Main m,String n,Object v)throws Exception{var f=Main.class.getDeclaredField(n);f.setAccessible(true);f.set(m,v);}
    static void check(boolean b,String text){if(!b)throw new AssertionError(text);}
    static final class FailingWorld extends World {
        FailingWorld(){super(999);}
        @Override public void close()throws Exception{super.close();throw new java.io.IOException("Synthetic world shutdown failure");}
    }
    public static void main(String[] args)throws Exception{
        Path out=Path.of(args[0]);Files.createDirectories(out);String mode=args[1];Main game=new Main();
        GraphicsProfile.preset(GraphicsProfile.Preset.BALANCED).with(GraphicsProfile.Key.RESOLUTION,"640x480@60").save(dev.jayms.ui.Controls.directory().resolve("graphics.properties"));
        set(game,"offlineSave",out.resolve("synthetic.dat"));set(game,"seed",748291L);set(game,"gameConfig",dev.jayms.net.city.GameConfig.sandbox());
        Path next=out.resolve("next.dat");new LocalGame(next,999).save();
        var entered=new CountDownLatch(1);var exited=new CountDownLatch(1);var release=new AtomicBoolean();var queuedRan=new AtomicBoolean();
        Future<?>[] tasks=new Future<?>[2];int[] sessions={0},frames={0};long[] switched={0},closeNanos={0};
        boolean[] caught={false};String[] renderer={null};
        try {
            game.run(new Main.FrameObserver(){
                public void started(Main main)throws Exception {
                    renderer[0]=glGetString(GL_RENDERER);sessions[0]++;
                    if(sessions[0]==2){
                        closeNanos[0]=System.nanoTime()-switched[0];
                        check(tasks[0].isCancelled()&&tasks[1].isCancelled(),"Both old active and queued futures cancelled");
                        check(exited.getCount()==0&&!queuedRan.get(),"Old active sky body exited; queued body never ran");
                        check(((World)get(main,"world")).terrain().seed==999,"New world seed");
                    }
                }
                public void afterFrame(Main main)throws Exception {
                    check(glGetError()==GL_NO_ERROR,"GL remains valid");
                    if(++frames[0]==3){
                        World world=(World)get(main,"world");
                        if(mode.equals("active-switch")){
                            var empty=new SparseVoxelOctree(256).freeze();
                            var input=new AbstractList<WorldSkyVisibility.Geometry>(){
                                public int size(){return Integer.MAX_VALUE;}
                                public WorldSkyVisibility.Geometry get(int i){entered.countDown();if(release.get())throw new CancellationException();return new WorldSkyVisibility.Geometry(new ChunkPos(0,0,0),empty);}
                            };
                            // Drain already bounded production sky work, then place a real active build in its shared lane.
                            long limit=System.nanoTime()+5_000_000_000L;
                            while(tasks[0]==null){try{tasks[0]=world.prepareTerrainTask(()->{try{return WorldSkyVisibility.build(new WorldSkyVisibility.Input(input,List.of(),new ModelLibrary()));}finally{exited.countDown();}});}catch(RejectedExecutionException busy){check(System.nanoTime()<limit,"CPU lane drains");Thread.sleep(1);}}
                            check(entered.await(5,TimeUnit.SECONDS),"Sky build active before switch");
                            tasks[1]=world.prepareTerrainTask(()->{queuedRan.set(true);return null;});
                            set(main,"requestedSave",next);switched[0]=System.nanoTime();
                        }else{
                            world.close();set(main,"world",new FailingWorld());
                            if(mode.equals("failure-switch"))set(main,"requestedSave",next);
                            else throw new IllegalArgumentException("Synthetic original run failure");
                        }
                        glfwSetWindowShouldClose(((Window)get(main,"window")).getHandle(),true);
                    }
                    if(frames[0]==6)glfwSetWindowShouldClose(((Window)get(main,"window")).getHandle(),true);
                }
            });
            check(mode.equals("active-switch")&&sessions[0]==2&&frames[0]==6,"Active switch completed");
        }catch(Exception error){
            if(mode.equals("active-switch"))throw error;
            if(mode.equals("failure-switch"))check(error instanceof java.io.IOException&&error.getMessage().equals("Synthetic world shutdown failure"),"Cleanup error preserved");
            else check(error instanceof IllegalArgumentException&&error.getSuppressed().length==1&&error.getSuppressed()[0] instanceof java.io.IOException,"Original error preserved with cleanup failure suppressed");
            caught[0]=true;
        }finally{release.set(true);}
        check(glfwGetCurrentContext()==0,"Main destroyed window/context even when scene retirement failed");
        check(mode.equals("active-switch")||caught[0],"Failure was reported");
        Files.writeString(out.resolve("result.txt"),"Playtest: PASS. Production Main "+mode+"; "+renderer[0]+"; Java21; inherited display; synthetic saves; sessions="+sessions[0]+", frames="+frames[0]+". Active sky cancellation/queued cancellation or forced failure aggregation and final window/context cleanup verified. Switch-to-next-start ms="+closeNanos[0]/1e6+" (includes next startup, not isolated close latency).\n");
    }
}
