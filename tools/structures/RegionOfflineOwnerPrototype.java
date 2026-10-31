import dev.jayms.net.*;
import dev.jayms.net.model.*;
import dev.jayms.net.structure.StructureOwnerQueue;
import java.io.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/** Private owner-queue backup adapter. Does not replace LocalGame state or select a production save backend. */
public final class RegionOfflineOwnerPrototype {
    private record State(Thread ownerThread,WorldVoxels.History history,long historyRevision,WorldVoxels.History.CopyCursor cursor,int count,
                         ModelLibrary liveModels,long modelRevision,ModelLibrary models,Inventory inventory,int health,List<ItemDrop> drops,RegionCityOwnerPrototype city){}
    private static void check(BooleanSupplier cancel){if(Thread.currentThread().isInterrupted()||cancel.getAsBoolean())throw new CancellationException();}
    private static State read(LocalGame game,BooleanSupplier cancel)throws IOException {
        check(cancel);
        if(!(game.edits instanceof WorldVoxels.History history))throw new IOException("Versioned history required");
        if(history.size()>200_000||game.drops.size()>100_000)throw new IOException("Owner snapshot record budget");
        return new State(Thread.currentThread(),history,history.revision(),history.copyCursor(),history.size(),game.models,game.models.revision(),game.models.copy(),game.inventory.copy(),game.health,List.copyOf(game.drops.values()),game.city==null?null:RegionCityOwnerPrototype.capture(game.city,cancel));
    }
    private static void validateGeometry(LocalGame game,State state)throws IOException {
        if((state.city()==null?game.city!=null:!state.city().sameIdentity(game.city))||game.edits!=state.history()||state.history().revision()!=state.historyRevision()||game.models!=state.liveModels()||game.models.revision()!=state.modelRevision())throw new IOException("Owner state changed; retry region backup");
    }
    private static byte[] metadata(State state,BooleanSupplier cancel)throws IOException{return RegionOfflineMetadataPrototype.capture(state.inventory(),state.health(),state.drops(),state.models(),cancel).encode(cancel);}
    private static void validate(LocalGame game,State state,byte[] metadata,BooleanSupplier cancel)throws IOException {
        validateGeometry(game,state);var current=read(game,cancel);
        if((state.city()==null?current.city()!=null:!state.city().sameState(current.city()))||!Arrays.equals(metadata,metadata(current,cancel)))throw new IOException("Owner survival state changed; retry region backup");
        check(cancel);
    }
    /** Worker only. Every live field and cursor read is dispatched to the existing game owner queue. */
    public static Snapshot capture(LocalGame game,StructureOwnerQueue owner,BooleanSupplier cancel)throws IOException {
        Objects.requireNonNull(game);Objects.requireNonNull(owner);Objects.requireNonNull(cancel);check(cancel);
        State state=owner.call(()->read(game,cancel),cancel);
        Iterable<Protocol.Edit> edits=()->new Iterator<>() {
            int remaining=state.count();Iterator<Protocol.Edit> batch=Collections.emptyIterator();
            public boolean hasNext(){return remaining>0;}
            public Protocol.Edit next(){
                if(!hasNext())throw new NoSuchElementException();check(cancel);
                if(!batch.hasNext())try {
                    var copied=owner.call(()->{validateGeometry(game,state);check(cancel);try{return state.cursor().nextBatch();}catch(ConcurrentModificationException changed){throw new IOException("Owner history changed",changed);}},cancel);
                    if(copied.isEmpty())throw new IOException("Incomplete owner history");batch=copied.iterator();
                }catch(IOException invalid){throw new UncheckedIOException(invalid);}
                remaining--;return batch.next();
            }
        };
        RegionHistoryPrototype geometry;
        try{geometry=RegionHistoryPrototype.migrate(game.seed,game.generatorVersion,edits,new RegionHistoryPrototype.Budget(200_000,100_000,1024),cancel);}
        catch(UncheckedIOException invalid){throw invalid.getCause();}
        catch(IllegalArgumentException invalid){throw new IOException("Owner migration value/budget",invalid);}
        var survival=RegionOfflineMetadataPrototype.capture(state.inventory(),state.health(),state.drops(),state.models(),cancel);byte[] frozen=survival.encode(cancel);
        var candidate=RegionWorldLibraryPrototype.capture(geometry,state.models(),survival,cancel);if(state.city()!=null)candidate=candidate.withSidecars(state.city().sidecars(),cancel);candidate.encode(cancel);
        owner.call(()->{validate(game,state,frozen,cancel);return null;},cancel);return new Snapshot(game,state,frozen,candidate);
    }
    public static final class Snapshot {
        private final LocalGame game;private final State state;private final byte[] metadata;private final RegionWorldLibraryPrototype candidate;
        private Snapshot(LocalGame game,State state,byte[] metadata,RegionWorldLibraryPrototype candidate){this.game=game;this.state=state;this.metadata=metadata;this.candidate=candidate;}
        public Staged stage(RegionGenerationPrototype destination,BooleanSupplier cancel)throws IOException {
            Objects.requireNonNull(destination);var loaded=destination.load(cancel);
            if(loaded.isPresent()){
                var saved=loaded.orElseThrow();var identity=saved.formatVersion()==1?saved.manifest().geometrySnapshot():saved.worldLibrary().geometrySnapshot();
                if(identity.terrainSeed()!=game.seed||identity.terrainVersion()!=game.generatorVersion)throw new IOException("Destination world identity differs");
            }
            long expected=loaded.map(RegionGenerationPrototype.Loaded::generation).orElse(0L);var file=destination.stage(candidate,cancel);
            if(file.expectedGeneration()!=expected){file.close();throw new IOException("Destination changed while staging owner backup");}
            return new Staged(this,file);
        }
    }
    public static final class Staged implements AutoCloseable {
        private final Snapshot source;private final RegionGenerationPrototype.Staged file;private boolean closed;
        private Staged(Snapshot source,RegionGenerationPrototype.Staged file){this.source=source;this.file=file;}
        /** Owner only: do not send mutation through the read-only owner queue. */
        public synchronized RegionGenerationPrototype.Receipt commit(BooleanSupplier cancel)throws IOException {
            if(closed)throw new IOException("Owner backup stage closed");Objects.requireNonNull(cancel);
            if(Thread.currentThread()!=source.state.ownerThread())throw new IOException("Original owner thread required");
            validate(source.game,source.state,source.metadata,cancel);var receipt=file.commit(cancel);closed=true;return receipt;
        }
        @Override public synchronized void close()throws IOException{closed=true;file.close();}
    }
}
