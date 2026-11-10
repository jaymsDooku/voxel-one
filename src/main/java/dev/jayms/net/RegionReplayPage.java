package dev.jayms.net;

import dev.jayms.ChunkPos;
import dev.jayms.net.model.SparseVoxelOctree;
import dev.jayms.net.structure.StructurePackage;
import java.io.IOException;
import java.util.*;
import java.util.function.*;

/** Bounded, detached worker result, not a live authority or durable page.
 * Callers must separately fence world/model revisions before installation.
 * No page escapes on failure. The spool and model definitions remain caller-owned. */
final class RegionReplayPage {
    // Private staging caps, not advertised structure/world support limits.
    static final int MAX_ROWS=4096,MAX_CELLS=64;
    private final ChunkPos region;
    private final Map<String,Protocol.Edit> history;
    private final Map<WorldVoxels.CellPosition,WorldVoxels.CellImage> cells;

    private RegionReplayPage(ChunkPos region,WorldVoxels.History history,
            LinkedHashMap<WorldVoxels.CellPosition,WorldVoxels.CellImage> cells) {
        this.region=region;
        this.history=Collections.unmodifiableMap(history);
        this.cells=Collections.unmodifiableMap(cells);
    }
    ChunkPos region(){return region;}
    Map<String,Protocol.Edit> history(){return history;}
    Map<WorldVoxels.CellPosition,WorldVoxels.CellImage> cells(){return cells;}

    /** Blocking worker-only load. Source-row preflight precedes decoding.
     * Cell admission precedes history insertion; geometry starts only after all
     * selected rows validate. One mutable 16-cubed cell is built at a time.
     * Retains at most maxRows history rows and maxCells packed cell images;
     * each image has at most 4096 leaves / 65536 encoded bytes. This is a
     * logical bound, not a measured JVM heap or main-thread time guarantee. */
    static RegionReplayPage load(RegionReplaySpool spool,ChunkPos region,Terrain terrain,
            int maxRows,int maxCells,Predicate<Protocol.Edit> allowed,BooleanSupplier cancel)throws IOException {
        Objects.requireNonNull(spool);Objects.requireNonNull(region);Objects.requireNonNull(terrain);
        Objects.requireNonNull(allowed);Objects.requireNonNull(cancel);
        if(maxRows<0||maxRows>MAX_ROWS||maxCells<0||maxCells>MAX_CELLS)
            throw new IllegalArgumentException("Replay page budget");
        StructurePackage.cancelled(cancel);
        var history=new WorldVoxels.History();
        var positions=new LinkedHashSet<WorldVoxels.CellPosition>();
        var loader=new RegionReplayLoader(history,cancel);
        spool.visitRegion(region,maxRows,allowed,cancel,(position,rows)->{
            for(var edit:rows) {
                StructurePackage.cancelled(cancel);
                var cell=new WorldVoxels.CellPosition(edit.x(),edit.y(),edit.z());
                if(!positions.contains(cell)) {
                    if(positions.size()==maxCells)throw new IOException("Replay page cell budget");
                    positions.add(cell);
                }
                loader.accept(edit);
            }
        });
        loader.finish();
        var grouped=new LinkedHashMap<WorldVoxels.CellPosition,List<Protocol.Edit>>();
        for(var edit:history.values()) {
            StructurePackage.cancelled(cancel);
            grouped.computeIfAbsent(new WorldVoxels.CellPosition(edit.x(),edit.y(),edit.z()),p->new ArrayList<>()).add(edit);
        }
        var images=new LinkedHashMap<WorldVoxels.CellPosition,WorldVoxels.CellImage>();
        for(var entry:grouped.entrySet()) {
            StructurePackage.cancelled(cancel);var p=entry.getKey();
            var tree=new SparseVoxelOctree(16);
            tree.fill(0,0,0,16,16,16,WorldVoxels.encode(terrain.block(p.x(),p.y(),p.z())));
            for(var edit:entry.getValue()) {
                StructurePackage.cancelled(cancel);
                int side=16>>edit.depth(),x=edit.ix()*side,y=edit.iy()*side,z=edit.iz()*side;
                tree.fill(x,y,z,x+side,y+side,z+side,WorldVoxels.encode(edit));
            }
            StructurePackage.cancelled(cancel);
            var image=WorldVoxels.prepareCellImage(tree);
            StructurePackage.cancelled(cancel);images.put(p,image);
        }
        StructurePackage.cancelled(cancel);
        return new RegionReplayPage(region,history,images);
    }
}
