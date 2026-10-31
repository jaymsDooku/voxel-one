import dev.jayms.net.*;
import dev.jayms.net.city.*;
import dev.jayms.net.model.*;
import dev.jayms.net.structure.StructureOwnerQueue;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.*;

/** Actual synthetic owner queue, live world cells and private generation backup. */
public final class RegionOfflineCellOwnerCheck {
    static int rejected;
    interface Action{void run()throws Exception;}
    static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static void reject(Class<? extends Throwable> type,Action action)throws Exception{try{action.run();}catch(Throwable e){if(type.isInstance(e)){rejected++;return;}throw new AssertionError(e);}throw new AssertionError("Unexpected acceptance");}
    static void edit(LocalGame game,WorldVoxels world,Protocol.Edit edit){world.apply(edit);WorldVoxels.remember(game.edits,edit);}
    static RegionOfflineOwnerPrototype.Staged stage(LocalGame game,AtomicReference<WorldVoxels> world,StructureOwnerQueue owner,RegionGenerationPrototype store)throws Exception{return RegionOfflineOwnerCheck.worker(()->RegionOfflineOwnerPrototype.capture(game,world::get,owner,()->false).stage(store,()->false),owner);}
    static int compare(LocalGame game,WorldVoxels world,RegionWorldLibraryPrototype.CompleteRestored restored) {
        int count=0;var cursor=((WorldVoxels.History)game.edits).cellCopyCursor();for(var batch=cursor.nextBatch();!batch.isEmpty();batch=cursor.nextBatch())for(var p:batch){require(world.cell(p.x(),p.y(),p.z()).sameValues(restored.world().cell(p.x(),p.y(),p.z())),"Backup cell geometry changed");count++;}return count;
    }
    public static void main(String[] args)throws Exception {
        Path parent=Path.of("target/structure-offline-cell-owner-tests");Files.createDirectories(parent);Path root=Files.createTempDirectory(parent,"synthetic-");int cells;
        try(var owner=new StructureOwnerQueue()) {
            var game=new LocalGame(root.resolve("legacy.dat"),126);var terrain=new Terrain(126);var world=new AtomicReference<>(new WorldVoxels(terrain));
            for(int i=0;i<2051;i++)edit(game,world.get(),new Protocol.Edit(i-1025,180,0,Blocks.STONE));
            edit(game,world.get(),new Protocol.Edit(-1026,180,0,Blocks.piece(Blocks.GLASS,1),1,0,0,0));edit(game,world.get(),new Protocol.Edit(-1026,180,0,Blocks.piece(Blocks.STONE,1),1,1,0,0));edit(game,world.get(),new Protocol.Edit(-1028,Terrain.MIN_Y,0,0));
            var voxels=new SparseVoxelOctree(8);voxels.set(1,2,3,0xff123456);var model=game.models.stageStructureModel(new ModelDefinition("synthetic",voxels));edit(game,world.get(),new Protocol.Edit(-1027,180,0,model.id()));game.inventory.add(Blocks.STONE,3);game.health=12;
            var ground=new CitySimulation.Ground(){public int type(int x,int y,int z){return world.get().type(x,y,z);}public void apply(List<Protocol.Edit> edits){for(var e:edits)edit(game,world.get(),e);}public boolean occupied(int x,int y,int z,int width,int depth){return false;}};
            game.city=new CitySimulation(GameConfig.cityGame(),ground,terrain,null);game.city.collectHistory();game.save();byte[] original=Files.readAllBytes(root.resolve("legacy.dat")),originalCity=Files.readAllBytes(root.resolve("legacy.dat.city")),originalHistory=Files.readAllBytes(root.resolve("legacy.dat.city.history"));
            var store=new RegionGenerationPrototype(root.resolve("private"));try(var staged=stage(game,world,owner,store)){staged.commit(()->false);}
            var restored=store.loadComplete(126,Terrain.CURRENT_VERSION,GameConfig.cityGame(),()->false).orElseThrow();require(restored.generation()==1&&restored.state().city().orElseThrow().frame().equals(game.city.frame())&&restored.state().offline().orElseThrow().health()==12&&restored.state().models().get(model.id()).definition().fingerprint().equals(model.definition().fingerprint()),"Backup state changed");cells=compare(game,world.get(),restored.state());require(cells>=2054,"Missing fixture cells");byte[] saved=Files.readAllBytes(root.resolve("private/current.region"));
            for(int change=0;change<6;change++) {
                try(var staged=stage(game,world,owner,store)) {
                    switch(change){case 0->world.get().apply(new Protocol.Edit(500,180,500,Blocks.GLASS));case 1->world.set(world.get().snapshot());case 2->game.health--;case 3->terrain.stressGrid(null);case 4->game.edits=WorldVoxels.History.copyOf(game.edits);default->game.city.advance(1);}
                    reject(IOException.class,()->staged.commit(()->false));
                }
                require(Arrays.equals(saved,Files.readAllBytes(root.resolve("private/current.region"))),"Rejected backup changed generation");
                if(change==0)WorldVoxels.remember(game.edits,new Protocol.Edit(500,180,500,Blocks.GLASS));
            }
            reject(CancellationException.class,()->RegionOfflineOwnerCheck.worker(()->RegionOfflineOwnerPrototype.capture(game,world::get,owner,()->true),owner));
            var cancel=new AtomicBoolean();reject(CancellationException.class,()->RegionOfflineOwnerCheck.worker(()->RegionOfflineOwnerPrototype.capture(game,world::get,owner,cancel::get),owner,()->cancel.set(true)));
            var pumps=new AtomicInteger();reject(IOException.class,()->RegionOfflineOwnerCheck.worker(()->RegionOfflineOwnerPrototype.capture(game,world::get,owner,()->false),owner,()->{if(pumps.incrementAndGet()==2)world.set(world.get().snapshot());}));
            reject(IOException.class,()->RegionOfflineOwnerCheck.worker(()->RegionOfflineOwnerPrototype.capture(game,()->new WorldVoxels(new Terrain(127)),owner,()->false),owner));
            try(var staged=stage(game,world,owner,store)){staged.commit(()->false);}restored=store.loadComplete(126,Terrain.CURRENT_VERSION,GameConfig.cityGame(),()->false).orElseThrow();require(restored.generation()==2&&restored.state().offline().orElseThrow().health()==11&&restored.state().city().orElseThrow().frame().equals(game.city.frame()),"Retry lost current state");compare(game,world.get(),restored.state());
            require(Arrays.equals(original,Files.readAllBytes(root.resolve("legacy.dat")))&&Arrays.equals(originalCity,Files.readAllBytes(root.resolve("legacy.dat.city")))&&Arrays.equals(originalHistory,Files.readAllBytes(root.resolve("legacy.dat.city.history"))),"Private backup wrote legacy source files");
        } finally {try(var paths=Files.walk(root)){for(var p:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}}
        System.out.printf("{\"status\":\"passed\",\"captured_cells\":%d,\"rejection_cases\":%d,\"owner_pumps\":%d,\"world_geometry_capture\":true,\"legacy_files_unchanged\":true,\"production_region_backend\":false,\"qualification\":false}%n",cells,rejected,RegionOfflineOwnerCheck.pumps);
    }
}
