import dev.jayms.net.*;
import dev.jayms.net.city.GameConfig;
import dev.jayms.net.model.*;
import dev.jayms.net.structure.StructureOwnerQueue;
import java.nio.file.*;
import java.util.*;

/** Capture authoritative stored cells with an empty flat history, then strict restart. */
public final class WorldOverrideCursorCheck {
    static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception {
        Path parent=Path.of("target/structure-world-override-tests");Files.createDirectories(parent);Path root=Files.createTempDirectory(parent,"synthetic-");int count;
        try(var owner=new StructureOwnerQueue()) {
            var game=new LocalGame(root.resolve("legacy.dat"),126);var world=new WorldVoxels(new Terrain(126));
            var component=new SparseVoxelOctree(8);component.set(1,2,3,0xff123456);game.models.acceptWorldEntry(new ModelLibrary.Entry(4096,"synthetic",new ModelDefinition("component",component)));
            for(int i=0;i<2051;i++)world.apply(new Protocol.Edit(i,180,0,Blocks.STONE));
            var fraction=new SparseVoxelOctree(16);fraction.fill(1,2,3,2,3,4,0xfe22bbff);world.replaceCell(-1,180,-1,fraction);
            require(new Terrain(126).block(-2,Terrain.MIN_Y,-1)!=0,"Air fixture lacks solid implicit terrain");world.replaceCell(-2,Terrain.MIN_Y,-1,new SparseVoxelOctree(16));world.apply(new Protocol.Edit(-3,180,-1,4096));
            require(game.edits.isEmpty(),"Fixture unexpectedly contains flat edits");
            var snapshot=RegionOfflineOwnerCheck.worker(()->RegionOfflineOwnerPrototype.capture(game,()->world,owner,()->false),owner);
            var store=new RegionGenerationPrototype(root.resolve("private"));try(var staged=snapshot.stage(store,()->false)){staged.commit(()->false);}
            var loaded=store.loadComplete(126,Terrain.CURRENT_VERSION,GameConfig.sandbox(),()->false).orElseThrow();count=0;
            for(int i=-3;i<2051;i++){int z=i<0?-1:0,y=i==-2?Terrain.MIN_Y:180;require(world.cell(i,y,z).sameValues(loaded.state().world().cell(i,y,z)),"Stored cell lost without history");count++;}
            require(loaded.state().models().get(4096)!=null&&loaded.state().offline().orElseThrow().health()==20,"Models/survival lost");
            byte[] before=Files.readAllBytes(root.resolve("private/current.region"));
            try(var staged=snapshot.stage(store,()->false)) {
                world.apply(new Protocol.Edit(0,180,0,Blocks.GLASS));boolean rejected=false;try{staged.commit(()->false);}catch(java.io.IOException expected){rejected=true;}require(rejected,"World-only stale edit accepted");
            }
            require(Arrays.equals(before,Files.readAllBytes(root.resolve("private/current.region"))),"Stale generation changed");
            snapshot=RegionOfflineOwnerCheck.worker(()->RegionOfflineOwnerPrototype.capture(game,()->world,owner,()->false),owner);try(var staged=snapshot.stage(store,()->false)){staged.commit(()->false);}
            require(store.loadComplete(126,Terrain.CURRENT_VERSION,GameConfig.sandbox(),()->false).orElseThrow().state().world().type(0,180,0)==Blocks.GLASS,"Retry lost latest geometry");
            require(game.edits.isEmpty()&&!Files.exists(root.resolve("legacy.dat")),"Capture wrote flat source");
        } finally {try(var paths=Files.walk(root)){for(var p:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}}
        System.out.println("{\"status\":\"passed\",\"stored_cells\":"+count+",\"flat_records\":0,\"strict_restart\":true,\"models_and_survival\":true,\"air_overrides_terrain\":true,\"stale_world_rejected\":true,\"retry\":true,\"source_unchanged\":true,\"qualification\":false}");
    }
}
