import dev.jayms.net.*;
import dev.jayms.net.city.*;
import dev.jayms.net.model.*;
import dev.jayms.net.structure.StructureOwnerQueue;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Actual LocalGame and owner queue, isolated synthetic data; private backup destination only. */
public final class RegionOfflineOwnerCheck {
    static int rejected,pumps;
    interface Action{void run()throws Exception;}
    static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    static void reject(Class<? extends Throwable> type,Action action)throws Exception{try{action.run();}catch(Throwable error){if(type.isInstance(error)){rejected++;return;}throw new AssertionError(error);}throw new AssertionError("Unexpected acceptance");}
    static <T>T worker(Callable<T> call,StructureOwnerQueue owner,Runnable afterPump)throws Exception {
        var task=new FutureTask<T>(call);var thread=new Thread(task,"synthetic-region-worker");thread.start();long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);
        try{while(!task.isDone()){if(System.nanoTime()>deadline)throw new AssertionError("Synthetic worker timed out");if(owner.pump()){pumps++;afterPump.run();}else Thread.sleep(1);}
            try{return task.get();}catch(ExecutionException invalid){var cause=invalid.getCause();if(cause instanceof Exception exception)throw exception;throw new AssertionError(cause);}
        }finally{thread.interrupt();thread.join(5000);require(!thread.isAlive(),"Worker leaked");}
    }
    static <T>T worker(Callable<T> call,StructureOwnerQueue owner)throws Exception{return worker(call,owner,()->{});}
    static RegionOfflineOwnerPrototype.Staged stage(LocalGame game,StructureOwnerQueue owner,RegionGenerationPrototype store)throws Exception{return worker(()->RegionOfflineOwnerPrototype.capture(game,owner,()->false).stage(store,()->false),owner);}
    public static void main(String[] args)throws Exception {
        Path parent=Path.of("target/structure-offline-owner-tests");Files.createDirectories(parent);Path root=Files.createTempDirectory(parent,"synthetic-");
        try(var owner=new StructureOwnerQueue()){
            Path legacy=root.resolve("legacy-world");var game=new LocalGame(legacy,123);var tree=new SparseVoxelOctree(8);tree.set(1,2,3,0xff112233);var definition=new ModelDefinition("synthetic",tree);game.models.accept(new ModelLibrary.Entry(42,"fixture",definition));game.models.accept(new ModelLibrary.Entry(75,"unused",definition));game.models.acceptWorldEntry(new ModelLibrary.Entry(4096,"placed",definition));game.inventory.add(42,3);game.health=7;game.drops.put(17,new ItemDrop(17,42,2,1,128,1));
            for(int i=0;i<2050;i++)WorldVoxels.remember(game.edits,new Protocol.Edit(i%128,128,i/128,Blocks.STONE));WorldVoxels.remember(game.edits,new Protocol.Edit(0,130,0,4096));WorldVoxels.remember(game.edits,new Protocol.Edit(1,130,0,0));WorldVoxels.remember(game.edits,new Protocol.Edit(1,130,0,Blocks.piece(Blocks.GLASS,1),1,1,0,1));WorldVoxels.remember(game.edits,new Protocol.Edit(2,Terrain.MIN_Y,0,0));
            var store=new RegionGenerationPrototype(root.resolve("private"));reject(IllegalStateException.class,()->RegionOfflineOwnerPrototype.capture(game,owner,()->false));
            try(var pending=stage(game,owner,store)){reject(IOException.class,()->worker(()->pending.commit(()->false),owner));pending.commit(()->false);reject(IOException.class,()->pending.commit(()->false));}
            var restored=new RegionAuthorityPrototype(root.resolve("private"),123,Terrain.CURRENT_VERSION);require(restored.metadata().orElseThrow().health()==7&&restored.metadata().orElseThrow().inventory().countType(42)==3&&restored.metadata().orElseThrow().drops().equals(List.copyOf(game.drops.values()))&&restored.models().get(75)!=null,"Owner backup lost survival/library");
            var expected=new WorldVoxels(new Terrain(123));for(var edit:game.edits.values())expected.apply(edit);for(int[] p:new int[][]{{0,128,0},{0,130,0},{1,130,0},{2,Terrain.MIN_Y,0},{127,128,15}})require(expected.cell(p[0],p[1],p[2]).sameValues(restored.cell(p[0],p[1],p[2])),"Owner geometry lost");
            byte[] saved=Files.readAllBytes(root.resolve("private/current.region"));
            for(String change:List.of("health","inventory","drop","model-value","model-identity","history-identity","history-aba")){
                try(var pending=stage(game,owner,store)){
                    switch(change){case "health" -> game.health=8;case "inventory" -> game.inventory.add(Blocks.STONE,1);case "drop" -> game.drops.put(18,new ItemDrop(18,Blocks.STONE,1,2,128,2));case "model-value" -> game.models.accept(new ModelLibrary.Entry(76,"new",definition));case "model-identity" -> game.models=game.models.copy();case "history-identity" -> game.edits=WorldVoxels.History.copyOf(game.edits);case "history-aba" -> {var key=new Protocol.Edit(3,140,0,Blocks.STONE);game.edits.put(key.key(),key);game.edits.remove(key.key());}}
                    reject(IOException.class,()->pending.commit(()->false));
                }
                require(Arrays.equals(saved,Files.readAllBytes(root.resolve("private/current.region"))),"Stale owner snapshot published");
            }
            var survivalCalls=new AtomicInteger();reject(IOException.class,()->worker(()->RegionOfflineOwnerPrototype.capture(game,owner,()->false),owner,()->{if(survivalCalls.incrementAndGet()==1)game.health=9;}));game.health=8;
            var originalHistory=game.edits;game.edits=new LinkedHashMap<>(game.edits);reject(IOException.class,()->worker(()->RegionOfflineOwnerPrototype.capture(game,owner,()->false),owner));game.edits=originalHistory;
            var counts=new AtomicInteger();reject(IOException.class,()->worker(()->RegionOfflineOwnerPrototype.capture(game,owner,()->false),owner,()->{if(counts.incrementAndGet()==2)WorldVoxels.remember(game.edits,new Protocol.Edit(3,141,0,Blocks.STONE));}));
            var cancelled=new AtomicBoolean();counts.set(0);reject(CancellationException.class,()->worker(()->RegionOfflineOwnerPrototype.capture(game,owner,cancelled::get),owner,()->{if(counts.incrementAndGet()==2)cancelled.set(true);}));
            reject(CancellationException.class,()->worker(()->RegionOfflineOwnerPrototype.capture(game,owner,()->true),owner));
            try(var pending=stage(game,owner,store)){reject(CancellationException.class,()->pending.commit(()->true));require(Arrays.equals(saved,Files.readAllBytes(root.resolve("private/current.region"))),"Cancelled backup published");pending.commit(()->false);}
            var wrongStore=new RegionGenerationPrototype(root.resolve("other-world"));var wrongGeometry=RegionHistoryPrototype.fromCells(999,Terrain.CURRENT_VERSION,List.of(),new RegionHistoryPrototype.CodecBudget(1024,0,0,0),()->false);var wrongCandidate=RegionWorldLibraryPrototype.capture(wrongGeometry,new ModelLibrary(),()->false);try(var pending=wrongStore.stage(wrongCandidate,()->false)){pending.commit(()->false);}byte[] wrongSaved=Files.readAllBytes(root.resolve("other-world/current.region"));
            var snapshot=worker(()->RegionOfflineOwnerPrototype.capture(game,owner,()->false),owner);reject(IOException.class,()->snapshot.stage(wrongStore,()->false));require(Arrays.equals(wrongSaved,Files.readAllBytes(root.resolve("other-world/current.region"))),"Cross-world destination changed");try(var first=snapshot.stage(store,()->false);var second=snapshot.stage(store,()->false)){first.commit(()->false);reject(IOException.class,()->second.commit(()->false));}
            restored=new RegionAuthorityPrototype(root.resolve("private"),123,Terrain.CURRENT_VERSION);require(restored.metadata().orElseThrow().health()==8&&restored.metadata().orElseThrow().drops().size()==2&&restored.models().get(76)!=null,"Retry lost latest owner state");
            try(var closed=new StructureOwnerQueue()){closed.close();reject(IOException.class,()->worker(()->RegionOfflineOwnerPrototype.capture(game,closed,()->false),closed));}
            var ground=new CitySimulation.Ground(){public int type(int x,int y,int z){return y<=0?Blocks.STONE:0;}public void apply(List<Protocol.Edit> edits){}public boolean occupied(int x,int y,int z,int w,int d){return false;}};game.city=new CitySimulation(GameConfig.cityGame(),ground,new Terrain(123),null);reject(IOException.class,()->worker(()->RegionOfflineOwnerPrototype.capture(game,owner,()->false),owner));
            require(!Files.exists(legacy),"Adapter wrote legacy source file");try(var files=Files.list(root.resolve("private"))){require(files.noneMatch(p->p.getFileName().toString().startsWith(".region-stage-")),"Private stage leaked");}
        }finally{try(var paths=Files.walk(root)){for(var path:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(path);}}
        System.out.printf("{\"status\":\"passed\",\"actual_local_game\":true,\"owner_queue_pumps\":%d,\"rejection_cases\":%d,\"legacy_file_untouched\":true,\"production_backend_selected\":false,\"qualification\":false}%n",pumps,rejected);
    }
}
