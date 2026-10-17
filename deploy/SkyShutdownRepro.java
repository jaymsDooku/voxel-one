import dev.jayms.*;
import dev.jayms.net.model.*;
import dev.jayms.render.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
/** Small bounded-memory repro of active build and merge shutdown. No GL or user data. */
public class SkyShutdownRepro {
    public static void main(String[] args)throws Exception {
        Path out=Path.of(args[0]);boolean fixed=Boolean.parseBoolean(args[1]);var rows=new ArrayList<String>();
        for(boolean merge:new boolean[]{false,true}){
            var world=new World(42);var active=new CountDownLatch(1);var exited=new CountDownLatch(1);var release=new AtomicBoolean();
            var root=new SparseVoxelOctree(256).freeze();var empty=WorldSkyVisibility.build(new WorldSkyVisibility.Input(List.of(),List.of(),new ModelLibrary()));
            var values=new AbstractList<WorldSkyVisibility>(){public int size(){return Integer.MAX_VALUE;}public WorldSkyVisibility get(int i){active.countDown();if(release.get())throw new CancellationException();return empty;}};
            var chunks=new AbstractList<WorldSkyVisibility.Geometry>(){public int size(){return Integer.MAX_VALUE;}public WorldSkyVisibility.Geometry get(int i){active.countDown();if(release.get())throw new CancellationException();return new WorldSkyVisibility.Geometry(new ChunkPos(0,0,0),root);}};
            var future=world.prepareTerrainTask(()->{try{return merge?WorldSkyVisibility.merge(values):WorldSkyVisibility.build(new WorldSkyVisibility.Input(chunks,List.of(),new ModelLibrary()));}finally{exited.countDown();}});
            if(!active.await(3,TimeUnit.SECONDS))throw new AssertionError("Preparation not active");
            long start=System.nanoTime();String failure="none";
            try{world.close();}catch(IllegalStateException e){failure=e.getMessage();}
            double ms=(System.nanoTime()-start)/1e6;boolean running=exited.getCount()!=0,cancelled=future.isCancelled();
            release.set(true);if(!exited.await(3,TimeUnit.SECONDS))throw new AssertionError("Fixture did not release");world.close();
            rows.add((merge?"merge":"build")+","+ms+","+running+","+cancelled+","+failure);
            if(fixed&&(running||!cancelled||!failure.equals("none")||ms>=2000))throw new AssertionError("Active cancellation failed");
        }
        Files.writeString(out,"operation,close_ms,body_running_at_return,future_cancelled,failure\n"+String.join("\n",rows)+"\n");
        System.out.println("Playtest: active sky shutdown repro "+(fixed?"fixed passed":"baseline recorded"));
    }
}
