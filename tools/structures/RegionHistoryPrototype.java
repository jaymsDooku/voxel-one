import dev.jayms.ChunkPos;
import dev.jayms.net.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/** Private migration experiment, not a save format or production history replacement. */
public final class RegionHistoryPrototype {
    public record Budget(int edits,int cells,int regions) {
        public Budget {
            if(edits<1||edits>200_000||cells<1||cells>100_000||regions<1||regions>1024)
                throw new IllegalArgumentException("Diagnostic budget");
        }
    }
    private final Map<ChunkPos,List<Protocol.CellState>> regions;
    private final long seed;
    private final int terrainVersion;
    private RegionHistoryPrototype(long seed,int terrainVersion,Map<ChunkPos,List<Protocol.CellState>> regions) {
        this.seed=seed;this.terrainVersion=terrainVersion;
        this.regions=Collections.unmodifiableMap(regions);
    }
    public Map<ChunkPos,List<Protocol.CellState>> regions(){return regions;}
    private static void checkpoint(BooleanSupplier cancel) {
        if(Thread.currentThread().isInterrupted()||cancel.getAsBoolean())throw new CancellationException();
    }
    /** Source must be a detached replay in canonical order, used only by this worker. */
    public static RegionHistoryPrototype migrate(long seed,int terrainVersion,Iterable<Protocol.Edit> source,Budget budget,BooleanSupplier cancel) {
        Objects.requireNonNull(source);Objects.requireNonNull(budget);Objects.requireNonNull(cancel);
        var world=new WorldVoxels(new Terrain(seed,terrainVersion));
        var touched=new LinkedHashMap<ChunkPos,BitSet>();int edits=0,cells=0;
        checkpoint(cancel);
        for(var edit:source) {
            checkpoint(cancel);
            if(edit==null||!edit.valid())throw new IllegalArgumentException("Invalid source edit");
            if(++edits>budget.edits())throw new IllegalArgumentException("Edit budget");
            var region=ChunkPos.fromBlock(edit.x(),edit.y(),edit.z());
            var bits=touched.get(region);
            if(bits==null) {
                if(touched.size()>=budget.regions())throw new IllegalArgumentException("Region budget");
                bits=new BitSet(4096);touched.put(region,bits);
            }
            int index=(Math.floorMod(edit.y(),16)*16+Math.floorMod(edit.z(),16))*16+Math.floorMod(edit.x(),16);
            if(!bits.get(index)) {
                if(++cells>budget.cells())throw new IllegalArgumentException("Cell budget");
                bits.set(index);
            }
            world.apply(edit);
        }
        var result=new LinkedHashMap<ChunkPos,List<Protocol.CellState>>();
        for(var entry:touched.entrySet()) {
            checkpoint(cancel);var region=entry.getKey();var records=new ArrayList<Protocol.CellState>();
            for(int index=entry.getValue().nextSetBit(0);index>=0;index=entry.getValue().nextSetBit(index+1)) {
                checkpoint(cancel);
                int x=region.chunkX()*16+index%16,y=region.chunkY()*16+index/256,z=region.chunkZ()*16+(index/16)%16;
                // Retain explicit air. Absence would restore implicit seeded terrain.
                records.add(new Protocol.CellState(x,y,z,world.cell(x,y,z).copy().freeze()));
            }
            result.put(region,List.copyOf(records));
        }
        checkpoint(cancel);
        return new RegionHistoryPrototype(seed,terrainVersion,result);
    }
    /** Diagnostic replay into a private world only; never call on a live authority. */
    public WorldVoxels replay() {
        var world=new WorldVoxels(new Terrain(seed,terrainVersion));
        for(var records:regions.values())for(var cell:records)world.replaceCell(cell.x(),cell.y(),cell.z(),cell.tree());
        return world;
    }
}
