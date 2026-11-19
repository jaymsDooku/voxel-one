package dev.jayms.net;

import dev.jayms.net.structure.StructurePackage;
import java.io.IOException;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Private owner-only ordered unsaved overlay. Not a World mutation listener or
 * source installation API. A publication can acknowledge only its exact prefix. */
final class RegionReplayJournal {
    private final Thread owner=Thread.currentThread();
    private final ArrayList<Protocol.Edit> rows=new ArrayList<>();
    private long epoch;
    private boolean busy;
    static final class Snapshot {
        private final RegionReplayJournal journal;
        private final long epoch;
        private final RegionReplayOverlay overlay;
        private Snapshot(RegionReplayJournal journal,long epoch,RegionReplayOverlay overlay){this.journal=journal;this.epoch=epoch;this.overlay=overlay;}
        RegionReplayOverlay overlay(){return overlay;}
        void validate(BooleanSupplier cancel)throws IOException {journal.validate(this,cancel);}
        RegionReplayOverlay acknowledge(BooleanSupplier cancel)throws IOException {return journal.acknowledge(this,cancel);}
    }
    private void enter()throws IOException {
        if(Thread.currentThread()!=owner)throw new IOException("Replay journal owner required");
        if(busy)throw new IOException("Replay journal reentry");busy=true;
    }
    void append(List<Protocol.Edit> edits,BooleanSupplier cancel)throws IOException {
        Objects.requireNonNull(edits);Objects.requireNonNull(cancel);enter();
        try {
            StructurePackage.cancelled(cancel);
            if(edits.size()>RegionReplayOverlay.MAX_ROWS-rows.size())throw new IOException("Replay journal row budget");
            var added=RegionReplayOverlay.capture(edits,cancel);StructurePackage.cancelled(cancel);
            rows.addAll(added.rows());
        }finally{busy=false;}
    }
    Snapshot capture(BooleanSupplier cancel)throws IOException {
        Objects.requireNonNull(cancel);enter();
        try{return new Snapshot(this,epoch,RegionReplayOverlay.capture(rows,cancel));}finally{busy=false;}
    }
    private void prefix(Snapshot snapshot,BooleanSupplier cancel)throws IOException {
        StructurePackage.cancelled(cancel);
        if(snapshot.journal!=this||snapshot.epoch!=epoch||snapshot.overlay.rows().size()>rows.size())throw new IOException("Replay journal prefix changed");
        for(int i=0;i<snapshot.overlay.rows().size();i++) {
            StructurePackage.cancelled(cancel);
            if(!snapshot.overlay.rows().get(i).equals(rows.get(i)))throw new IOException("Replay journal prefix changed");
        }
    }
    private void validate(Snapshot snapshot,BooleanSupplier cancel)throws IOException {
        Objects.requireNonNull(cancel);enter();try{prefix(snapshot,cancel);}finally{busy=false;}
    }
    private RegionReplayOverlay acknowledge(Snapshot snapshot,BooleanSupplier cancel)throws IOException {
        Objects.requireNonNull(cancel);enter();
        try {
            prefix(snapshot,cancel);if(epoch==Long.MAX_VALUE)throw new IOException("Replay journal epoch exhausted");
            int count=snapshot.overlay.rows().size();
            var remaining=RegionReplayOverlay.capture(rows.subList(count,rows.size()),cancel);
            StructurePackage.cancelled(cancel);rows.subList(0,count).clear();epoch++;return remaining;
        }finally{busy=false;}
    }
}
