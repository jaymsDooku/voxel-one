package dev.jayms.net;

import dev.jayms.net.structure.StructurePackage;
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
    List<Protocol.Edit> rows(){return rows;}
}
