package dev.jayms.net;

import dev.jayms.net.structure.StructurePackage;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Worker-private replay sink. Carries at most 4097 rows across validated sections.
 * A later failure may leave a private prefix; callers must discard that target. */
final class RegionReplayLoader {
    private final Map<String,Protocol.Edit> target;
    private final BooleanSupplier cancel;
    private final ArrayList<Protocol.Edit> pending=new ArrayList<>();

    RegionReplayLoader(Map<String,Protocol.Edit> target,BooleanSupplier cancel) {
        this.target=Objects.requireNonNull(target);this.cancel=Objects.requireNonNull(cancel);
    }
    void accept(Protocol.Edit edit) {
        check();Objects.requireNonNull(edit);
        if(!pending.isEmpty()) {
            var first=pending.get(0);
            if(pending.size()==4097||first.x()!=edit.x()||first.y()!=edit.y()||first.z()!=edit.z())flush();
        }
        // Only an exact clear can begin a compact group. Ordinary legacy rows
        // need no buffering, geometry reconstruction or eligibility scan.
        if(pending.isEmpty()&&(edit.depth()!=0||edit.type()!=0||edit.color()!=0)) {
            WorldVoxels.rememberValidated(target,edit);return;
        }
        pending.add(edit);
    }
    void finish(){check();flush();check();}
    private void check(){StructurePackage.cancelled(cancel);}
    private void flush() {
        if(pending.isEmpty())return;
        check();
        if(!(target instanceof WorldVoxels.History history)||!history.rememberCanonicalReplay(pending,this::check))
            for(var edit:pending){check();WorldVoxels.rememberValidated(target,edit);}
        pending.clear();check();
    }
}
