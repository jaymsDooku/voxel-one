import dev.jayms.net.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;

/** Executable integration checks against actual legacy history and world replay. */
public final class RegionHistoryPrototypeCheck {
    private static final long SEED=123;
    private static int terrainVersion;
    private static final RegionHistoryPrototype.Budget BUDGET=new RegionHistoryPrototype.Budget(20_000,1000,1000);
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static void reject(Class<? extends Throwable> type,Runnable action) {
        try{action.run();}catch(Throwable error){if(type.isInstance(error))return;throw error;}
        throw new AssertionError("Expected "+type.getSimpleName());
    }
    private static WorldVoxels replay(Iterable<Protocol.Edit> edits) {
        var world=new WorldVoxels(new Terrain(SEED,terrainVersion));for(var edit:edits)world.apply(edit);return world;
    }
    private static void run() {
        var edits=new ArrayList<Protocol.Edit>();var random=new Random(0x5317c17);
        int[] coordinates={-17,-16,-1,0,15,16,17};
        for(int i=0;i<5000;i++) {
            int x=coordinates[random.nextInt(coordinates.length)],z=coordinates[random.nextInt(coordinates.length)];
            int y=i%2==0?0:128,depth=random.nextInt(5),side=1<<depth;
            int material=i%3==0?Blocks.LED:i%3==1?Blocks.STONE:0;
            edits.add(new Protocol.Edit(x,y,z,material==0?0:Blocks.piece(material,depth),depth,
                    random.nextInt(side),random.nextInt(side),random.nextInt(side),material==Blocks.LED?random.nextInt(0x1000000):0));
        }
        // Air must remain an explicit override of natural terrain. Models retain IDs.
        edits.add(new Protocol.Edit(1,Terrain.MIN_Y,1,0));
        edits.add(new Protocol.Edit(2,128,2,1024));
        edits.add(new Protocol.Edit(-Terrain.LIMIT,Protocol.MIN_BUILD_Y,-Terrain.LIMIT,Blocks.STONE));
        edits.add(new Protocol.Edit(Terrain.LIMIT,Protocol.MAX_BUILD_Y,Terrain.LIMIT,Blocks.STONE));
        var raw=replay(edits);var legacy=new WorldVoxels.History();
        for(var edit:edits)WorldVoxels.remember(legacy,edit);
        var oldWorld=replay(legacy.values());
        var migrated=RegionHistoryPrototype.migrate(SEED,terrainVersion,legacy.values(),BUDGET,()->false);
        var restored=migrated.replay();int cells=0;
        for(var records:migrated.regions().values())for(var cell:records) {
            require(cell.tree().sameValues(raw.cell(cell.x(),cell.y(),cell.z())),"Raw replay differs");
            require(cell.tree().sameValues(oldWorld.cell(cell.x(),cell.y(),cell.z())),"Legacy replay differs");
            require(cell.tree().sameValues(restored.cell(cell.x(),cell.y(),cell.z())),"Region replay differs");
            cells++;
        }
        require(restored.type(1,Terrain.MIN_Y,1)==0,"Air override lost");
        require(new WorldVoxels(new Terrain(SEED,terrainVersion)).type(1,Terrain.MIN_Y,1)!=0,"Air test lacks terrain");
        require(restored.type(2,128,2)==1024,"Model ID lost");
        require(restored.cell(100,0,100).sameValues(raw.cell(100,0,100)),"Untouched seeded terrain changed");
        restored.apply(new Protocol.Edit(2,128,2,Blocks.STONE));
        require(migrated.replay().type(2,128,2)==1024,"Snapshot changed after later edit");
        reject(UnsupportedOperationException.class,()->migrated.regions().clear());
        reject(UnsupportedOperationException.class,()->migrated.regions().values().iterator().next().clear());
        var frozen=migrated.regions().values().iterator().next().get(0).tree();
        reject(IllegalStateException.class,()->frozen.fill(0,0,0,16,16,16,0));
        require(RegionHistoryPrototype.migrate(SEED,terrainVersion,List.of(),BUDGET,()->false).regions().isEmpty(),"Empty migration changed world");
        require(RegionHistoryPrototype.migrate(SEED,terrainVersion,legacy.values(),new RegionHistoryPrototype.Budget(legacy.size(),cells,migrated.regions().size()),()->false).regions().size()==migrated.regions().size(),"Exact budget rejected");
        reject(IllegalArgumentException.class,()->RegionHistoryPrototype.migrate(SEED,terrainVersion,edits,new RegionHistoryPrototype.Budget(1,1000,1000),()->false));
        reject(IllegalArgumentException.class,()->RegionHistoryPrototype.migrate(SEED,terrainVersion,edits,new RegionHistoryPrototype.Budget(20_000,1,1000),()->false));
        reject(IllegalArgumentException.class,()->RegionHistoryPrototype.migrate(SEED,terrainVersion,edits,new RegionHistoryPrototype.Budget(20_000,1000,1),()->false));
        reject(IllegalArgumentException.class,()->RegionHistoryPrototype.migrate(SEED,terrainVersion,List.of(new Protocol.Edit(1,128,1,-1)),BUDGET,()->false));
        reject(IllegalArgumentException.class,()->new RegionHistoryPrototype.Budget(200_001,1,1));
        reject(CancellationException.class,()->RegionHistoryPrototype.migrate(SEED,terrainVersion,legacy.values(),BUDGET,()->true));
        var checks=new AtomicInteger();
        // Cancel after replay, while detached final region records are being made.
        reject(CancellationException.class,()->RegionHistoryPrototype.migrate(SEED,terrainVersion,legacy.values(),BUDGET,()->checks.incrementAndGet()>legacy.size()+3));
        Thread.currentThread().interrupt();
        try{reject(CancellationException.class,()->RegionHistoryPrototype.migrate(SEED,terrainVersion,legacy.values(),BUDGET,()->false));
            require(Thread.currentThread().isInterrupted(),"Interrupt flag cleared");}
        finally{Thread.interrupted();}
        require(RegionHistoryPrototype.migrate(SEED,terrainVersion,legacy.values(),BUDGET,()->false).regions().size()==migrated.regions().size(),"Retry differs");
        System.out.printf("{\"status\":\"passed\",\"raw_edits\":%d,\"canonical_records\":%d,\"cells\":%d,\"regions\":%d,\"seed\":%d,\"terrain_version\":%d,\"qualification\":false}%n",edits.size(),legacy.size(),cells,migrated.regions().size(),SEED,terrainVersion);
    }
    public static void main(String[] args) {
        for(int version=Terrain.LEGACY_VERSION;version<=Terrain.CURRENT_VERSION;version++){terrainVersion=version;run();}
    }
}
