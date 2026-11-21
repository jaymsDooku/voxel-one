package dev.jayms.net;

import dev.jayms.net.structure.StructurePackage;
import dev.jayms.ChunkPos;
import java.io.IOException;
import java.util.function.Predicate;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Bounded private snapshot of ordered edits appended after an unchanged replay
 * source. Capture/commit generation fences remain the caller's responsibility. */
final class RegionReplayOverlay {
    static final int MAX_ROWS=65_536;
    private static final RegionReplayOverlay EMPTY=new RegionReplayOverlay(List.of());
    private final List<Protocol.Edit> rows;
    private RegionReplayOverlay(List<Protocol.Edit> rows){this.rows=rows;}
    static RegionReplayOverlay empty(){return EMPTY;}
    static RegionReplayOverlay capture(List<Protocol.Edit> input,BooleanSupplier cancel) {
        Objects.requireNonNull(input);Objects.requireNonNull(cancel);StructurePackage.cancelled(cancel);
        int count=input.size();if(count<0||count>MAX_ROWS)throw new IllegalArgumentException("Replay overlay row budget");
        var rows=new ArrayList<Protocol.Edit>(count);
        for(int i=0;i<count;i++) {
            StructurePackage.cancelled(cancel);var edit=Objects.requireNonNull(input.get(i));
            if(!edit.valid())throw new IllegalArgumentException("Invalid replay overlay edit");rows.add(edit);
        }
        if(input.size()!=count)throw new ConcurrentModificationException("Replay overlay changed during capture");
        StructurePackage.cancelled(cancel);return rows.isEmpty()?EMPTY:new RegionReplayOverlay(List.copyOf(rows));
    }
    /** Worker-owned immutable lookup. Ordinals preserve append order across cells. */
    static final class Index {
        private final RegionReplayOverlay overlay;
        private final Map<WorldVoxels.CellPosition,int[]> cells;
        Index(RegionReplayOverlay overlay,Predicate<Protocol.Edit> allowed,BooleanSupplier cancel)throws IOException {
            this.overlay=Objects.requireNonNull(overlay);
            var counts=new HashMap<WorldVoxels.CellPosition,Integer>();
            for(var edit:overlay.rows){StructurePackage.cancelled(cancel);if(!allowed.test(edit))throw new IOException("Pending replay overlay policy rejected");counts.merge(position(edit),1,Integer::sum);}
            var buckets=new HashMap<WorldVoxels.CellPosition,int[]>();
            for(var entry:counts.entrySet()){StructurePackage.cancelled(cancel);buckets.put(entry.getKey(),new int[entry.getValue()]);entry.setValue(0);}
            for(int i=0;i<overlay.rows.size();i++){StructurePackage.cancelled(cancel);var key=position(overlay.rows.get(i));int offset=counts.get(key);buckets.get(key)[offset]=i;counts.put(key,offset+1);}
            cells=Map.copyOf(buckets);
        }
        private static WorldVoxels.CellPosition position(Protocol.Edit edit){return new WorldVoxels.CellPosition(edit.x(),edit.y(),edit.z());}
        RegionReplayOverlay select(ChunkPos region,RegionReplayPage.CellRange range,int remaining,BooleanSupplier cancel)throws IOException {
            if(remaining<0)throw new IOException("Replay overlay row budget");
            var selected=new ArrayList<int[]>();int count=0;
            long x=(long)region.chunkX()*16,y=(long)region.chunkY()*16,z=(long)region.chunkZ()*16;
            for(int key=range.firstInclusive();key<range.endExclusive();key++) {
                StructurePackage.cancelled(cancel);long cx=x+(key&15),cy=y+(key>>8),cz=z+((key>>4)&15);
                if(cx<Integer.MIN_VALUE||cx>Integer.MAX_VALUE||cy<Integer.MIN_VALUE||cy>Integer.MAX_VALUE||cz<Integer.MIN_VALUE||cz>Integer.MAX_VALUE)continue;
                var bucket=cells.get(new WorldVoxels.CellPosition((int)cx,(int)cy,(int)cz));
                if(bucket!=null){if(bucket.length>remaining-count)throw new IOException("Replay overlay row budget");count+=bucket.length;selected.add(bucket);}
            }
            if(count==0)return EMPTY;
            int[] ordinals=new int[count];int offset=0;
            for(var bucket:selected){StructurePackage.cancelled(cancel);System.arraycopy(bucket,0,ordinals,offset,bucket.length);offset+=bucket.length;}
            Arrays.sort(ordinals);var rows=new ArrayList<Protocol.Edit>(count);
            for(int ordinal:ordinals){StructurePackage.cancelled(cancel);rows.add(overlay.rows.get(ordinal));}
            return new RegionReplayOverlay(List.copyOf(rows));
        }
    }
    List<Protocol.Edit> rows(){return rows;}
}
