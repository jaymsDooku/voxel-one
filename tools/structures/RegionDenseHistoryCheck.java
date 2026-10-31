import dev.jayms.net.*;
import dev.jayms.net.model.*;
import dev.jayms.net.city.GameConfig;
import dev.jayms.net.structure.StructureOwnerQueue;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Dense replay input, tiny final geometry. This is not a large-city benchmark. */
public final class RegionDenseHistoryCheck {
    static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static void reject(RegionOfflineCellOwnerCheck.Action action)throws Exception{try{action.run();}catch(IOException expected){return;}throw new AssertionError("Unexpected acceptance");}
    public static void main(String[] args)throws Exception {
        Path parent=Path.of("target/structure-dense-history-tests");Files.createDirectories(parent);Path root=Files.createTempDirectory(parent,"synthetic-");int records;long generationBytes;
        try(var owner=new StructureOwnerQueue()) {
            var game=new LocalGame(root.resolve("legacy.dat"),126);var world=new WorldVoxels(new Terrain(126));var stone=new SparseVoxelOctree(16);stone.fill(0,0,0,16,16,16,WorldVoxels.encode(Blocks.STONE));
            for(int cell=0;cell<49;cell++) {
                for(int x=0;x<16;x++)for(int y=0;y<16;y++)for(int z=0;z<16;z++) {
                    var edit=new Protocol.Edit(cell-24,180,0,Blocks.piece(Blocks.STONE,4),4,x,y,z);require(edit.valid(),"Invalid dense fixture");game.edits.put(edit.key(),edit);
                }
                world.replaceCell(cell-24,180,0,stone);
            }
            records=game.edits.size();require(records==200704,"Dense fixture count changed");long revision=((WorldVoxels.History)game.edits).revision();
            reject(()->RegionOfflineOwnerCheck.worker(()->RegionOfflineOwnerPrototype.capture(game,owner,()->false),owner));
            var tooManyCells=new LocalGame(root.resolve("oversize.dat"),126);
            for(int i=0;i<100001;i++){var edit=new Protocol.Edit(i%512,180,i/512,Blocks.STONE);require(edit.valid(),"Invalid cell-budget fixture");tooManyCells.edits.put(edit.key(),edit);}
            reject(()->RegionOfflineOwnerCheck.worker(()->RegionOfflineOwnerPrototype.capture(tooManyCells,()->world,owner,()->false),owner));
            var tooManyChunks=new WorldVoxels(new Terrain(126));for(int i=0;i<1025;i++)tooManyChunks.replaceCell((i%32)*16,180,(i/32)*16,stone);
            reject(()->RegionOfflineOwnerCheck.worker(()->RegionOfflineOwnerPrototype.capture(game,()->tooManyChunks,owner,()->false),owner));
            var store=new RegionGenerationPrototype(root.resolve("private"));var snapshot=RegionOfflineOwnerCheck.worker(()->RegionOfflineOwnerPrototype.capture(game,()->world,owner,()->false),owner);
            try(var staged=snapshot.stage(store,()->false)){staged.commit(()->false);}
            var restored=store.loadComplete(126,Terrain.CURRENT_VERSION,GameConfig.sandbox(),()->false).orElseThrow();require(restored.generation()==1,"Generation changed");
            require(RegionOfflineCellOwnerCheck.compare(game,world,restored.state())==49,"Dense history lost final cells");require(game.edits.size()==records&&((WorldVoxels.History)game.edits).revision()==revision&&!Files.exists(root.resolve("legacy.dat")),"Capture changed source");
            byte[] saved=Files.readAllBytes(root.resolve("private/current.region"));generationBytes=saved.length;
            try(var staged=snapshot.stage(store,()->false)){var same=game.edits.values().iterator().next();game.edits.put(same.key(),same);reject(()->staged.commit(()->false));}
            require(Arrays.equals(saved,Files.readAllBytes(root.resolve("private/current.region"))),"Stale dense backup changed generation");
            snapshot=RegionOfflineOwnerCheck.worker(()->RegionOfflineOwnerPrototype.capture(game,()->world,owner,()->false),owner);try(var staged=snapshot.stage(store,()->false)){staged.commit(()->false);}require(store.load(()->false).orElseThrow().generation()==2,"Dense retry failed");
        } finally {try(var paths=Files.walk(root)){for(var p:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}}
        System.out.printf("{\"status\":\"passed\",\"input_records\":%d,\"final_cells\":49,\"generation_bytes\":%d,\"preflight_budget_rejected\":true,\"replay_path_rejected\":true,\"indexed_path_restored\":true,\"stale_rejected\":true,\"source_unchanged\":true,\"qualification\":false}%n",records,generationBytes);
    }
}
