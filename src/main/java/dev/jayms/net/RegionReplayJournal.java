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
    private boolean busy,failed;
    @FunctionalInterface interface Guard {void validate()throws IOException;}
    private Guard guard;
    void bind(Guard guard)throws IOException {
        Objects.requireNonNull(guard);enter();try{if(this.guard!=null)throw new IOException("Replay journal already bound");this.guard=guard;}finally{busy=false;}
    }
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
        if(busy)throw new IOException("Replay journal reentry");
        if(failed)throw new IOException("Replay journal requires recovery");busy=true;
        try{if(guard!=null)guard.validate();}catch(IOException|RuntimeException|Error failure){busy=false;throw failure;}
    }
    void append(List<Protocol.Edit> edits,BooleanSupplier cancel)throws IOException {
        try(var reservation=reserve(edits,cancel)){reservation.commit();}
    }
    /** Capacity and all row allocations precede the caller's World mutation.
     * Normal commit only appends existing immutable references. */
    Reservation reserve(List<Protocol.Edit> edits,BooleanSupplier cancel)throws IOException {
        Objects.requireNonNull(edits);Objects.requireNonNull(cancel);enter();
        try {
            StructurePackage.cancelled(cancel);
            if(edits.size()>RegionReplayOverlay.MAX_ROWS-rows.size())throw new IOException("Replay journal row budget");
            var added=RegionReplayOverlay.capture(edits,cancel);rows.ensureCapacity(rows.size()+added.rows().size());
            StructurePackage.cancelled(cancel);return new Reservation(added);
        }catch(IOException|RuntimeException|Error failure){busy=false;throw failure;}
    }
    void invalidate()throws IOException {
        if(Thread.currentThread()!=owner)throw new IOException("Replay journal owner required");failed=true;
    }
    final class Reservation implements AutoCloseable {
        private final RegionReplayOverlay added;private boolean done;
        private Reservation(RegionReplayOverlay added){this.added=added;}
        private void check()throws IOException {
            if(Thread.currentThread()!=owner)throw new IOException("Replay journal owner required");
            if(done||!busy)throw new IOException("Replay journal reservation consumed");
        }
        void commit()throws IOException {
            check();if(failed)throw new IOException("Replay journal requires recovery");
            for(int i=0;i<added.rows().size();i++)rows.add(added.rows().get(i));done=true;busy=false;
        }
        void failed()throws IOException {check();failed=true;done=true;busy=false;}
        @Override public void close()throws IOException {
            if(Thread.currentThread()!=owner)throw new IOException("Replay journal owner required");
            if(!done){done=true;busy=false;}
        }
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
