package dev.jayms.net;

import dev.jayms.net.structure.StructurePackage;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Private legacy server loading. Preserve Map.put semantics, including old
 * key positions and overlapping rows. Discard the target on any failure. */
final class KeyedReplayLoader {
    private final WorldVoxels.History target;
    private final BooleanSupplier cancel;
    private final ArrayList<Protocol.Edit> pending=new ArrayList<>();
    KeyedReplayLoader(WorldVoxels.History target,BooleanSupplier cancel) {
        this.target=Objects.requireNonNull(target);this.cancel=Objects.requireNonNull(cancel);
    }
    void accept(Protocol.Edit edit) {
        check();if(!Objects.requireNonNull(edit).valid())throw new IllegalArgumentException("Invalid keyed replay edit");
        if(!pending.isEmpty()) {
            var first=pending.get(0);
            if(pending.size()==4097||first.x()!=edit.x()||first.y()!=edit.y()||first.z()!=edit.z())flush();
        }
        if(pending.isEmpty()&&(edit.depth()!=0||edit.type()!=0||edit.color()!=0)) {
            target.put(edit.key(),edit);return;
        }
        pending.add(edit);
    }
    void finish(){check();flush();check();}
    private void check(){StructurePackage.cancelled(cancel);}
    private void flush() {
        if(pending.isEmpty())return;check();
        if(!target.putNewCanonicalReplay(pending,this::check))
            for(var edit:pending){check();target.put(edit.key(),edit);}
        pending.clear();check();
    }
}
