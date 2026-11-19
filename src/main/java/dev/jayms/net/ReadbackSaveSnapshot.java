package dev.jayms.net;

import dev.jayms.World;
import dev.jayms.net.model.ModelLibrary;
import dev.jayms.net.structure.StructurePackage;
import java.io.IOException;
import java.util.*;
import java.util.function.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicReference;

/** Private owner capture -> worker staging -> owner authorization -> worker
 * publication, plus a strict blocking reference commit. Neither is connected to
 * Main. Does not grant partial LocalGame save authority or
 * exclude foreign writers/path aliases/independent city-sidecar changes. */
final class ReadbackSaveSnapshot {
    private final Thread owner=Thread.currentThread();
    private final LocalGame local;private final World world;private final World.ReplayFence fence;
    private final ModelLibrary liveModels,models;private final long modelRevision;
    private final WorldSavePublication.Generation generation;private final String fingerprint;
    private final long seed;private final int generator,health;
    private final Inventory inventory;private final Map<Integer,ItemDrop> drops;
    private final RegionReplayOverlay overlay;
    private ReadbackSaveSnapshot(LocalGame local,World world,World.ReplayFence fence,RegionReplayOverlay overlay,
            Inventory inventory,Map<Integer,ItemDrop> drops) {
        this.local=local;this.world=world;this.fence=fence;this.overlay=overlay;
        liveModels=local.models;modelRevision=liveModels.revision();models=liveModels.copy();
        generation=local.readbackGeneration();fingerprint=local.readbackFingerprint();
        seed=local.seed;generator=local.generatorVersion;health=local.health;this.inventory=inventory;this.drops=drops;
    }
    static ReadbackSaveSnapshot capture(LocalGame local,World world,RegionReplayOverlay overlay,BooleanSupplier cancel)throws IOException {
        Objects.requireNonNull(local);Objects.requireNonNull(world);Objects.requireNonNull(overlay);Objects.requireNonNull(cancel);StructurePackage.cancelled(cancel);
        if(local.replayComplete()||local.readbackGeneration()==null||local.readbackFingerprint().isEmpty()
                ||world.models()!=local.models||world.terrain().seed!=local.seed||world.terrain().version!=local.generatorVersion)
            throw new IOException("Readback save source/World mismatch");
        var fence=world.beginReplayFence();
        if(local.health<0||local.health>20)throw new IOException("Readback save health");
        var inventory=Objects.requireNonNull(local.inventory).copy();
        for(int i=0;i<Inventory.SIZE;i++)if(!local.models.has(inventory.type(i)))throw new IOException("Readback save inventory dependency");
        int count=local.drops.size();if(count>Crafting.MAX_DROPS)throw new IOException("Readback save drop budget");
        var drops=new LinkedHashMap<Integer,ItemDrop>();
        for(var entry:local.drops.entrySet()) {
            StructurePackage.cancelled(cancel);if(drops.size()>=count)throw new IOException("Readback save drops changed");
            var d=entry.getValue();
            if(d==null||entry.getKey()==null||entry.getKey()!=d.id()||d.id()<=0||d.type()<=0||!Items.valid(d.type())
                    ||!local.models.has(d.type())||d.count()<0||d.count()>Items.stackLimit(d.type())
                    ||!Float.isFinite(d.x())||!Float.isFinite(d.y())||!Float.isFinite(d.z())||drops.put(d.id(),d)!=null)
                throw new IOException("Invalid readback save drop");
        }
        if(drops.size()!=count)throw new IOException("Readback save drops changed");
        for(var edit:overlay.rows()){StructurePackage.cancelled(cancel);if(!local.models.has(edit.type()))throw new IOException("Readback save overlay dependency");}
        var snapshot=new ReadbackSaveSnapshot(local,world,fence,overlay,inventory,Collections.unmodifiableMap(drops));
        snapshot.validate();StructurePackage.cancelled(cancel);return snapshot;
    }
    private void owner()throws IOException{if(Thread.currentThread()!=owner)throw new IOException("Readback save owner required");}
    private void validate()throws IOException {
        owner();
        try{fence.validate(world);}catch(IllegalStateException|ConcurrentModificationException changed){throw new IOException("Readback save World changed",changed);}
        if(local.replayComplete()||local.readbackGeneration()!=generation||!fingerprint.equals(local.readbackFingerprint())
                ||local.models!=liveModels||world.models()!=liveModels||liveModels.revision()!=modelRevision||local.health!=health||!drops.equals(local.drops))
            throw new IOException("Readback save owner state changed");
        var current=local.inventory;if(current==null)throw new IOException("Readback save inventory changed");
        for(int i=0;i<Inventory.SIZE;i++)if(current.type(i)!=inventory.type(i)||current.count(i)!=inventory.count(i))throw new IOException("Readback save inventory changed");
    }
    /** Uses only immutable captured values; spool enforces its owning thread.
     * Source identity is the metadata generation object, not a caller-supplied tag. */
    Prepared stage(RegionReplayWorker.Source source,BooleanSupplier cancel,IntConsumer progress)throws IOException {
        Objects.requireNonNull(source);Objects.requireNonNull(cancel);Objects.requireNonNull(progress);
        StructurePackage.cancelled(cancel);
        if(source.metadata()==null||source.metadata().generation()!=generation||!fingerprint.equals(source.fingerprint())
                ||source.terrain().seed!=seed||source.terrain().version!=generator||!source.metadata().matchesTerrain(source.terrain()))
            throw new IOException("Readback save staging source changed");
        var prefix=StagedOfflineSave.stageRegions(generation.target(),seed,generator,models,source.spool(),overlay,
                edit->models.has(edit.type()),cancel,progress);
        try {
            prefix.sealTail(inventory,health,drops.values(),drops.size(),cancel);
            if(!source.metadata().matchesTerrain(source.terrain()))throw new IOException("Readback save source terrain changed");
            StructurePackage.cancelled(cancel);return new Prepared(prefix);
        }catch(IOException|RuntimeException|Error failure){try{prefix.close();}catch(IOException cleanup){failure.addSuppressed(cleanup);}throw failure;}
    }
    final class Prepared implements AutoCloseable {
        private final StagedOfflineSave prefix;
        private boolean consumed,transferred;
        private Prepared(StagedOfflineSave prefix){this.prefix=prefix;}
        /** Owner-blocking reference integration only. No frame-time guarantee;
         * live integration still needs dirty-state and sidecar coordination. */
        void commit(BooleanSupplier cancel)throws IOException {
            owner();Objects.requireNonNull(cancel);claim();
            try{StructurePackage.cancelled(cancel);validate();prefix.publishSealed(generation,cancel);}
            catch(IOException|RuntimeException|Error failure){try{prefix.close();}catch(IOException cleanup){failure.addSuppressed(cleanup);}throw failure;}
        }
        private void claim()throws IOException{if(consumed)throw new IOException("Readback save already consumed");consumed=true;}
        /** Authorizes this captured snapshot, not later owner edits. No I/O here.
         * Caller must retain dirty state until worker completion and revalidation. */
        Publication authorize(BooleanSupplier cancel)throws IOException {
            owner();Objects.requireNonNull(cancel);
            if(consumed)throw new IOException("Readback save already consumed");
            StructurePackage.cancelled(cancel);validate();StructurePackage.cancelled(cancel);
            var publication=new Publication(prefix);claim();transferred=true;return publication;
        }
        @Override public void close()throws IOException{owner();if(!transferred){consumed=true;prefix.close();}}
    }
    enum Phase {AUTHORIZED,WORKING,COMMITTING,PUBLISHED,CANCELLED,FAILED}
    /** Private one-worker transfer. No executor, dirty-state clearing, generation
     * refresh, Main integration or sidecar transaction is supplied by this API. */
    final class Publication implements AutoCloseable {
        private final StagedOfflineSave prefix;
        private final AtomicReference<Phase> phase=new AtomicReference<>(Phase.AUTHORIZED);
        private Thread worker;
        private Publication(StagedOfflineSave prefix){this.prefix=prefix;}
        Phase phase(){return phase.get();}
        private synchronized void worker()throws IOException {
            if(Thread.currentThread()==owner)throw new IOException("Readback publication worker required");
            if(worker==null)worker=Thread.currentThread();
            else if(worker!=Thread.currentThread())throw new IOException("Readback publication worker changed");
        }
        /** True means cancellation won before the durable commit boundary. */
        boolean cancel()throws IOException {
            owner();
            for(;;){var prior=phase.get();if(prior==Phase.CANCELLED)return true;
                if(prior!=Phase.AUTHORIZED&&prior!=Phase.WORKING)return false;
                if(phase.compareAndSet(prior,Phase.CANCELLED))return true;}
        }
        void publish()throws IOException {
            worker();
            if(!phase.compareAndSet(Phase.AUTHORIZED,Phase.WORKING)) {
                if(phase.get()==Phase.CANCELLED){prefix.close();throw new CancellationException("Readback publication cancelled");}
                throw new IOException("Readback publication already consumed");
            }
            try {
                prefix.publishSealed(generation,()->phase.get()==Phase.CANCELLED,
                        ()->phase.compareAndSet(Phase.WORKING,Phase.COMMITTING));
                phase.set(Phase.PUBLISHED);
            }catch(IOException|RuntimeException|Error failure){
                phase.compareAndSet(Phase.WORKING,Phase.FAILED);phase.compareAndSet(Phase.COMMITTING,Phase.FAILED);
                try{prefix.close();}catch(IOException cleanup){failure.addSuppressed(cleanup);}throw failure;
            }
        }
        /** Owner completion check only. False never rolls back a published file. */
        boolean snapshotCurrent()throws IOException {
            owner();if(phase.get()!=Phase.PUBLISHED)return false;
            try{validate();return true;}catch(IOException changed){return false;}
        }
        /** Worker cleanup only, including cancellation before work starts. */
        @Override public void close()throws IOException {
            worker();phase.compareAndSet(Phase.AUTHORIZED,Phase.CANCELLED);prefix.close();
        }
    }
}
