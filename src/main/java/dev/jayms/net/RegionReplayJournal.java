package dev.jayms.net;

import dev.jayms.net.structure.StructurePackage;
import dev.jayms.World;
import java.io.IOException;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Private owner-only ordered unsaved overlay. Not a World mutation listener or
 * source installation API. A publication can acknowledge only its exact prefix. */
final class RegionReplayJournal {
    private final Thread owner=Thread.currentThread();
    private final ArrayList<Protocol.Edit> rows=new ArrayList<>();
    private long epoch;
    private boolean busy,failed,readOnly;
    @FunctionalInterface interface Guard {void validate()throws IOException;}
    private Guard guard;
    private World boundWorld;
    /** Exact unsaved source overlay for a fresh read-only paging World. This
     * supports capture/acknowledgement, never adding geometry or edit authority. */
    static RegionReplayJournal readOnlySource(World world,RegionReplayOverlay pending)throws IOException {
        Objects.requireNonNull(world);Objects.requireNonNull(pending);
        if(!world.hasReplayReadView())throw new IOException("Source journal requires a read-only replay World");
        final World.ReplayFence fence;
        try{fence=world.beginReplayFence();}
        catch(IllegalStateException|ConcurrentModificationException failure){throw new IOException("Source journal World unavailable",failure);}
        var journal=new RegionReplayJournal();journal.append(pending.rows(),()->false);
        journal.bind(world,()->{
            try{fence.validate(world);}
            catch(IllegalStateException|ConcurrentModificationException failure){journal.invalidate();throw new IOException("Source journal World changed",failure);}
        });
        journal.readOnly=true;return journal;
    }
    void bind(World world,Guard guard)throws IOException {
        Objects.requireNonNull(world);Objects.requireNonNull(guard);enter();
        try{if(this.guard!=null)throw new IOException("Replay journal already bound");this.guard=guard;boundWorld=world;}finally{busy=false;}
    }
    static final class Snapshot {
        private final RegionReplayJournal journal;
        private final long epoch;
        private final RegionReplayOverlay overlay;
        private Snapshot(RegionReplayJournal journal,long epoch,RegionReplayOverlay overlay){this.journal=journal;this.epoch=epoch;this.overlay=overlay;}
        RegionReplayOverlay overlay(){return overlay;}
        void validate(BooleanSupplier cancel)throws IOException {journal.validate(this,cancel);}
        void validateWorld(World world,BooleanSupplier cancel,boolean requireBound)throws IOException {journal.validateWorld(this,world,cancel,requireBound);}
        RegionReplayOverlay acknowledge(BooleanSupplier cancel)throws IOException {return journal.acknowledge(this,cancel);}
        RegionReplayOverlay remaining(BooleanSupplier cancel)throws IOException {return journal.remaining(this,cancel);}
        Acknowledgement prepareAcknowledgement(RegionReplayOverlay expected,BooleanSupplier cancel)throws IOException {return journal.prepareAcknowledgement(this,expected,cancel);}
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
            if(readOnly)throw new IOException("Read-only replay journal cannot add geometry");
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
    private void validateWorld(Snapshot snapshot,World world,BooleanSupplier cancel,boolean requireBound)throws IOException {
        Objects.requireNonNull(world);Objects.requireNonNull(cancel);enter();
        try {
            if((requireBound&&boundWorld==null)||(boundWorld!=null&&boundWorld!=world))throw new IOException("Replay journal World mismatch");
            prefix(snapshot,cancel);
        }finally{busy=false;}
    }
    private RegionReplayOverlay remaining(Snapshot snapshot,BooleanSupplier cancel)throws IOException {
        Objects.requireNonNull(cancel);enter();
        try{prefix(snapshot,cancel);return RegionReplayOverlay.capture(rows.subList(snapshot.overlay.rows().size(),rows.size()),cancel);}finally{busy=false;}
    }
    private RegionReplayOverlay acknowledge(Snapshot snapshot,BooleanSupplier cancel)throws IOException {
        try(var prepared=prepareAcknowledgement(snapshot,null,cancel)){return prepared.commit();}
    }
    /** Holds the journal against reentry while its exact suffix and allocations
     * are prepared. Close abandons preparation without acknowledging any rows. */
    private Acknowledgement prepareAcknowledgement(Snapshot snapshot,RegionReplayOverlay expected,BooleanSupplier cancel)throws IOException {
        Objects.requireNonNull(cancel);enter();
        try {
            prefix(snapshot,cancel);if(epoch==Long.MAX_VALUE)throw new IOException("Replay journal epoch exhausted");
            int count=snapshot.overlay.rows().size();
            var remaining=RegionReplayOverlay.capture(rows.subList(count,rows.size()),cancel);
            if(expected!=null&&!expected.rows().equals(remaining.rows()))throw new IOException("Replay journal pending suffix changed");
            var prepared=new Acknowledgement(count,remaining);
            StructurePackage.cancelled(cancel);
            if(failed)throw new IOException("Replay journal requires recovery");
            if(guard!=null)guard.validate();
            if(failed)throw new IOException("Replay journal requires recovery");
            return prepared;
        }catch(IOException|RuntimeException|Error failure){busy=false;throw failure;}
    }
    final class Acknowledgement implements AutoCloseable {
        private final List<Protocol.Edit> prefix;private final RegionReplayOverlay remaining;private boolean done;
        private Acknowledgement(int count,RegionReplayOverlay remaining){prefix=rows.subList(0,count);this.remaining=remaining;}
        RegionReplayOverlay commit()throws IOException {
            if(Thread.currentThread()!=owner)throw new IOException("Replay journal owner required");
            if(done||!busy)throw new IOException("Replay journal acknowledgement consumed");
            if(failed)throw new IOException("Replay journal requires recovery");
            // No callbacks, allocation or cancellation past this boundary.
            prefix.clear();epoch++;done=true;busy=false;return remaining;
        }
        @Override public void close()throws IOException {
            if(Thread.currentThread()!=owner)throw new IOException("Replay journal owner required");
            if(!done){done=true;busy=false;}
        }
    }
}
