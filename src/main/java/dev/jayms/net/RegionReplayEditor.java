package dev.jayms.net;

import dev.jayms.World;
import dev.jayms.net.structure.StructurePackage;
import java.io.IOException;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Private opt-in resident World/journal adapter. Partial replay remains read-only.
 * Caller supplies a World matching its source. No Main or source installation.
 * Existing World batch replacement is not atomic: failures poison this journal
 * for explicit recovery, rather than claiming rollback or safe save authority. */
final class RegionReplayEditor {
    private final Thread owner=Thread.currentThread();
    private final World world;
    private final RegionReplayJournal journal;
    private World.ReplayFence fence;
    private boolean busy;
    RegionReplayEditor(World world,RegionReplayJournal journal)throws IOException {
        this.world=Objects.requireNonNull(world);this.journal=Objects.requireNonNull(journal);
        if(world.hasReplayReadView())throw new IOException("Replay editor requires resident World");
        journal.capture(()->false);fence=world.beginReplayFence();journal.bind(world,this::check);
    }
    private void check()throws IOException {
        if(Thread.currentThread()!=owner)throw new IOException("Replay editor owner required");
        try{fence.validate(world);}catch(IllegalStateException|ConcurrentModificationException changed){journal.invalidate();throw new IOException("Replay editor World changed outside journal",changed);}
    }
    private void enter()throws IOException {
        if(Thread.currentThread()!=owner)throw new IOException("Replay editor owner required");
        if(busy)throw new IOException("Replay editor reentry");busy=true;
    }
    RegionReplayJournal.Snapshot capture(BooleanSupplier cancel)throws IOException {
        Objects.requireNonNull(cancel);enter();
        try{check();var result=journal.capture(cancel);check();return result;}finally{busy=false;}
    }
    void apply(Protocol.Edit edit,BooleanSupplier cancel)throws IOException {
        Objects.requireNonNull(edit);mutate(List.of(edit),()->world.apply(edit),cancel);
    }
    void replaceCells(List<World.CellReplacement> cells,BooleanSupplier cancel)throws IOException {
        Objects.requireNonNull(cells);Objects.requireNonNull(cancel);
        if(cells.size()>RegionReplayOverlay.MAX_ROWS)throw new IOException("Replay editor cell budget");
        var replacements=List.copyOf(cells);int count=0;
        for(var cell:replacements){StructurePackage.cancelled(cancel);int n=cell.replay().editCount();if(n>RegionReplayOverlay.MAX_ROWS-count)throw new IOException("Replay editor row budget");count+=n;}
        var edits=new ArrayList<Protocol.Edit>(count);
        for(var cell:replacements){StructurePackage.cancelled(cancel);edits.addAll(cell.replay().orderedEdits());}
        mutate(edits,()->world.replaceCells(replacements),cancel);
    }
    private void mutate(List<Protocol.Edit> edits,Runnable apply,BooleanSupplier cancel)throws IOException {
        Objects.requireNonNull(cancel);enter();
        try {
            check();
            try(var reservation=journal.reserve(edits,cancel)) {
                StructurePackage.cancelled(cancel);check();
                long expected=Math.addExact(world.editsVersion(),edits.size());
                var models=world.models();long modelRevision=models.revision(),terrainRevision=world.terrain().geometryRevision();
                try {
                    apply.run();
                    if(world.editsVersion()!=expected||world.models()!=models||models.revision()!=modelRevision
                            ||world.terrain().geometryRevision()!=terrainRevision)throw new IOException("Replay editor mutation receipt mismatch");
                    var next=world.beginReplayFence();reservation.commit();fence=next;
                }catch(IOException|RuntimeException|Error failure){
                    try{journal.invalidate();}catch(IOException cleanup){failure.addSuppressed(cleanup);}throw failure;
                }
            }
        }finally{busy=false;}
    }
}
