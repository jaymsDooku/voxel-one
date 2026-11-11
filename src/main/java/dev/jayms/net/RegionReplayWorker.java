package dev.jayms.net;

import dev.jayms.ChunkPos;
import dev.jayms.World;
import dev.jayms.net.model.ModelLibrary;
import dev.jayms.net.structure.StructurePackage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.*;
import java.util.function.*;

/** Private asynchronous readback, not a live archive or installation API.
 * The factory runs once on this worker and must validate the complete world,
 * including its tail, before returning an owned source. On factory failure it
 * must clean any resources it has not returned. No index or spool crosses threads.
 * Callers still own real authority/model/terrain identity and revision fences. */
final class RegionReplayWorker implements AutoCloseable {
    static final int MAX_QUEUED=8;
    record Revision(long world,long models,long terrain) {}
    record Source(RegionReplaySpool spool,Terrain terrain,Predicate<Protocol.Edit> allowed,Revision revision,String fingerprint,LocalGame.ReadbackMetadata metadata) {
        Source(RegionReplaySpool spool,Terrain terrain,Predicate<Protocol.Edit> allowed,Revision revision){this(spool,terrain,allowed,revision,"",null);}
        Source(RegionReplaySpool spool,Terrain terrain,Predicate<Protocol.Edit> allowed,Revision revision,String fingerprint){this(spool,terrain,allowed,revision,fingerprint,null);}
        Source {
            Objects.requireNonNull(spool);Objects.requireNonNull(terrain);Objects.requireNonNull(allowed);Objects.requireNonNull(revision);Objects.requireNonNull(fingerprint);
            if(metadata!=null&&!fingerprint.equals(metadata.fingerprint()))throw new IllegalArgumentException("Replay metadata fingerprint mismatch");
            if(metadata!=null&&!metadata.matchesTerrain(terrain))throw new IllegalArgumentException("Replay metadata terrain mismatch");
            if(!fingerprint.isEmpty()&&!fingerprint.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Replay source fingerprint");
        }
    }
    @FunctionalInterface interface Factory {Source open(BooleanSupplier cancel,IntConsumer checkedRows)throws IOException;}
    enum State {OPENING,RUNNING,CLOSING,CLEANUP_FAILED,CLOSED,FAILED}
    enum Phase {QUEUED,LOADING,READY,CANCELLED,FAILED}
    record Status(State state,int sourceRows,IOException failure) {}
    record Progress(Phase phase,IOException failure) {}
    private record Outcome(Phase phase,RegionReplayPage page,RegionReplayChunk.Snapshot chunk,IOException failure) {
        Outcome(Phase phase,RegionReplayPage page,IOException failure){this(phase,page,null,failure);}
    }
    final class Request {
        private final ChunkPos region;
        private final RegionReplayPage.CellRange range;
        private final int maxRows;
        private final Revision revision;
        private final World.ReplayFence fence;
        private final World.ReplayMeshFence meshFence;
        private final Binding binding;
        // -1 selects the existing page path. Chunk requests share its queue,
        // cancellation state and single active worker slot.
        private final int maxCellLeaves;
        private final List<ChunkPos> neighbourRegions;
        private final AtomicInteger checkedRows=new AtomicInteger();
        private final AtomicInteger completedCells=new AtomicInteger();
        private final AtomicReference<Outcome> outcome=new AtomicReference<>(new Outcome(Phase.QUEUED,null,null));
        private Request(ChunkPos region,RegionReplayPage.CellRange range,int maxRows,Revision revision,World.ReplayFence fence,Binding binding) {
            this(region,range,maxRows,revision,fence,binding,-1);
        }
        private Request(ChunkPos region,RegionReplayPage.CellRange range,int maxRows,Revision revision,World.ReplayFence fence,Binding binding,int maxCellLeaves) {
            this(region,range,maxRows,revision,fence,binding,maxCellLeaves,List.of());
        }
        private Request(ChunkPos region,RegionReplayPage.CellRange range,int maxRows,Revision revision,World.ReplayFence fence,Binding binding,int maxCellLeaves,List<ChunkPos> neighbours) {
            this.neighbourRegions=neighbours;
            this.region=region;this.range=range;this.maxRows=maxRows;this.revision=revision;this.fence=fence;this.binding=binding;
            this.maxCellLeaves=maxCellLeaves;
            this.meshFence=maxCellLeaves>=0&&fence!=null?fence.captureMeshDependencies(region):null;
        }
        Progress outcome(){var value=outcome.get();return new Progress(value.phase(),value.failure());}
        int checkedRows(){return checkedRows.get();}
        /** Chunk assembly only: 0..4096, including implicit terrain. This
         * diagnostic count never grants result, data or mesh readiness. */
        int completedCells(){return completedCells.get();}
        boolean cancelled(){return outcome.get().phase()==Phase.CANCELLED;}
        /** Nonblocking; cancellation wins if it precedes terminal publication. */
        boolean cancel() {
            for(;;) {
                var old=outcome.get();if(terminal(old.phase()))return false;
                if(outcome.compareAndSet(old,new Outcome(Phase.CANCELLED,null,null))) {
                    synchronized(gate){queue.remove(this);gate.notifyAll();}return true;
                }
            }
        }
        private void finish(Outcome next) {
            for(;;){var old=outcome.get();if(terminal(old.phase()))return;if(outcome.compareAndSet(old,next))return;}
        }
        /** Owner-side nonblocking extraction. These supplied revision tags do
         * not replace checks against actual live object identities/revisions. */
        RegionReplayPage result(Revision current)throws IOException {
            owner();
            if(binding!=null)throw new IOException("Replay request requires its source binding");
            if(fence!=null)throw new IOException("Replay request requires its world fence");
            return extract(current);
        }
        /** Checks actual owner/world state. Still a detached result: source
         * generation, dependencies, coverage and installation remain separate. */
        RegionReplayPage result(Revision current,World world)throws IOException {
            owner();
            if(binding!=null)throw new IOException("Replay request requires its source binding");
            return checkedWorldResult(current,world);
        }
        private RegionReplayPage checkedWorldResult(Revision current,World world)throws IOException {
            if(fence==null)throw new IOException("Replay request has no world fence");
            if(stopping.get()||status.state()!=State.RUNNING)throw new IOException("Replay worker no longer active");
            validateWorld(world);var page=extract(current);validateWorld(world);return page;
        }
        private void validateWorld(World world)throws IOException {
            try{fence.validate(world);if(meshFence!=null)meshFence.validate(world);}
            catch(IllegalStateException|ConcurrentModificationException changed){throw new IOException("Replay target world changed",changed);}
        }
        private RegionReplayPage extract(Revision current)throws IOException {
            if(maxCellLeaves>=0)throw new IOException("Replay request contains chunk geometry");
            return extractOutcome(current).page();
        }
        /** Detached CPU geometry only; never installs a chunk or declares it
         * mesh/data ready. Bound requests must use their binding's extractor. */
        RegionReplayChunk.Snapshot chunkResult(Revision current,World world)throws IOException {
            owner();
            if(binding!=null)throw new IOException("Replay request requires its source binding");
            return checkedWorldChunkResult(current,world);
        }
        private RegionReplayChunk.Snapshot checkedWorldChunkResult(Revision current,World world)throws IOException {
            owner();
            if(maxCellLeaves<0)throw new IOException("Replay request contains a cell page");
            if(fence==null)throw new IOException("Replay request has no world fence");
            if(stopping.get()||status.state()!=State.RUNNING)throw new IOException("Replay worker no longer active");
            validateWorld(world);var chunk=extractOutcome(current).chunk();validateWorld(world);return chunk;
        }
        private Outcome extractOutcome(Revision current)throws IOException {
            owner();Objects.requireNonNull(current);
            if(!revision.equals(current))throw new IOException("Replay request revision changed");
            var value=outcome.get();
            if(value.phase()==Phase.CANCELLED)throw new CancellationException("Replay request cancelled");
            if(value.phase()==Phase.FAILED)throw value.failure();
            if(value.phase()!=Phase.READY)throw new IllegalStateException("Replay request not complete");
            return value;
        }
    }
    private static boolean terminal(Phase phase){return phase==Phase.READY||phase==Phase.CANCELLED||phase==Phase.FAILED;}
    private final Thread owner=Thread.currentThread();
    private final Object gate=new Object();
    private final ArrayDeque<Request> queue=new ArrayDeque<>();
    private final AtomicBoolean stopping=new AtomicBoolean();
    private final int capacity;
    private volatile Status status=new Status(State.OPENING,0,null);
    private volatile String fingerprint="";
    private LocalGame.ReadbackMetadata metadata;
    private Revision sourceRevision;
    // Owner-only transfer/binding receipts, never read by the worker.
    private LocalGame transferredMetadata;
    private ModelLibrary transferredModels;
    private long transferredModelsRevision;
    private Binding targetBinding;
    String fingerprint(){return fingerprint;}
    /** Owner-only, nonblocking single transfer. Prepared maps become owner-owned;
     * the worker's private model-policy maps remain separate. No live install. */
    LocalGame metadataResult(Revision current)throws IOException {
        owner();Objects.requireNonNull(current);
        synchronized(gate) {
            if(stopping.get()||status.state()!=State.RUNNING)throw new IOException("Replay metadata not available");
            if(!current.equals(sourceRevision))throw new IOException("Replay metadata revision changed");
            if(metadata==null)throw new IOException("Replay metadata unavailable or already transferred");
            var result=metadata.transfer();metadata=null;
            transferredMetadata=result;transferredModels=result.models;transferredModelsRevision=result.models.revision();return result;
        }
    }
    /** Private detached-target session. This does not mark coverage loaded,
     * install geometry, promote metadata, or authorize a save over a live path. */
    final class Binding {
        private final LocalGame local;
        private final World target;
        private World.ReplayFence baseline;
        private ReadView view;
        private ChunkPump chunkPump;
        private boolean requestsIssued;
        private final Revision revision=sourceRevision;
        private final String identity=fingerprint;
        private Binding(LocalGame local,World target){this.local=local;this.target=target;baseline=target.beginReplayFence();}
        String fingerprint(){return identity;}
        private void validate(World current)throws IOException {
            owner();
            if(stopping.get()||status.state()!=State.RUNNING)throw new IOException("Replay binding no longer active");
            if(view!=null&&view.closed)throw new IOException("Replay read view closed");
            if(chunkPump!=null&&chunkPump.closed)throw new IOException("Replay chunk pump closed");
            if(targetBinding!=this||transferredMetadata!=local||local.replayComplete()
                    ||local.models!=transferredModels||transferredModels.revision()!=transferredModelsRevision
                    ||!identity.equals(fingerprint)||!identity.equals(local.readbackFingerprint()))
                throw new IOException("Replay source metadata changed");
            try{baseline.validate(current);}
            catch(IllegalStateException|ConcurrentModificationException changed){throw new IOException("Replay bound target changed",changed);}
        }
        Request submit(ChunkPos region,RegionReplayPage.CellRange range,int maxRows,World current)throws IOException {
            validate(current);var request=enqueue(region,range,maxRows,revision,current.beginReplayFence(),this);requestsIssued=true;return request;
        }
        Request submitChunk(ChunkPos region,int maxCellLeaves,World current)throws IOException {
            validate(current);
            var request=enqueueChunk(region,maxCellLeaves,revision,current.beginReplayFence(),this);
            requestsIssued=true;return request;
        }
        /** Same-source detached neighbours only; mask bits are -X,+X,-Y,+Y,-Z,+Z. */
        Request submitChunkWithNeighbours(ChunkPos region,int maxCellLeaves,int neighbourMask,World current)throws IOException {
            validate(current);
            var request=enqueueChunk(region,maxCellLeaves,revision,current.beginReplayFence(),this,true,neighbourMask);
            requestsIssued=true;return request;
        }
        RegionReplayChunk.Snapshot chunkResult(Request request,World current)throws IOException {
            validate(current);Objects.requireNonNull(request);
            if(request.binding!=this)throw new IOException("Replay request belongs to another binding");
            var chunk=request.checkedWorldChunkResult(revision,current);validate(current);return chunk;
        }
        record ChunkPumpStatus(int desired,int pending,int ready,int failed,int stagedCells,boolean backpressure,boolean closed) {}
        ChunkPump openChunkPump(World current,int capacity,int maxCellLeaves)throws IOException {
            validate(current);
            if(chunkPump!=null||view!=null||requestsIssued)throw new IOException("Replay chunk pump must open before requests or read view");
            if(capacity<1||capacity>2||maxCellLeaves<0||maxCellLeaves>RegionReplayChunk.MAX_CELL_LEAVES)
                throw new IllegalArgumentException("Replay chunk pump budget");
            chunkPump=new ChunkPump(capacity,maxCellLeaves);return chunkPump;
        }
        /** Attach complete chunk data to a private read-only World. This only
         * grants data sampling; it never installs live chunks or GPU meshes. */
        ChunkPump openChunkReadPump(World current,int capacity,int maxCellLeaves)throws IOException {
            var pump=openChunkPump(current,capacity,maxCellLeaves);
            try {
                current.attachReplayReadView(pump.readView());
                baseline=current.beginReplayFence();return pump;
            }catch(IllegalStateException|ConcurrentModificationException failure) {
                pump.close();throw new IOException("Replay chunk read view unavailable",failure);
            }
        }
        /** Bounded owner ticks over detached CPU results only. Ready here never
         * means installed, mesh-ready, saveable or interactive world coverage. */
        final class ChunkPump implements AutoCloseable {
            private final int capacity,maxCellLeaves;
            private Set<ChunkPos> desired=new LinkedHashSet<>();
            private final Map<ChunkPos,Request> pending=new LinkedHashMap<>();
            private final Map<ChunkPos,RegionReplayChunk.Snapshot> ready=new LinkedHashMap<>();
            private final Map<ChunkPos,IOException> failures=new LinkedHashMap<>();
            private boolean closed,backpressure;
            private ChunkPump(int capacity,int maxCellLeaves){this.capacity=capacity;this.maxCellLeaves=maxCellLeaves;}
            private void cancelPending(){for(var request:pending.values())request.cancel();pending.clear();}
            private void check()throws IOException {
                owner();if(closed)throw new IOException("Replay chunk pump closed");
                try{validate(target);}catch(IOException failure){cancelPending();ready.clear();failures.clear();throw failure;}
            }
            private World.ReplayReadView readView() {
                return new World.ReplayReadView() {
                    private RegionReplayChunk.Snapshot find(int x,int y,int z) {
                        try{check();}catch(IOException failure){throw new IllegalStateException("Replay chunk read view unavailable",failure);}
                        if(!new Protocol.Edit(x,y,z,0).valid())return null;
                        return ready.get(ChunkPos.fromBlock(x,y,z));
                    }
                    @Override public boolean ready(int x,int y,int z){return find(x,y,z)!=null;}
                    @Override public dev.jayms.net.model.SparseVoxelOctree cell(int x,int y,int z) {
                        var chunk=find(x,y,z);
                        if(chunk==null)throw new World.UnloadedReplayCellException(x,y,z);
                        return chunk.geometry().region(Math.floorMod(x,16)*16,Math.floorMod(y,16)*16,Math.floorMod(z,16)*16,16).freeze();
                    }
                    @Override public void close() {
                        try{ChunkPump.this.close();}catch(IOException failure){throw new IllegalStateException("Replay chunk read view owner required",failure);}
                    }
                };
            }
            void desired(List<ChunkPos> regions)throws IOException {
                check();Objects.requireNonNull(regions);
                int count=regions.size();
                if(count<0||count>capacity)throw new IllegalArgumentException("Replay desired-chunk budget");
                var next=new LinkedHashSet<ChunkPos>();
                for(int i=0;i<count;i++)next.add(Objects.requireNonNull(regions.get(i)));
                if(regions.size()!=count)throw new ConcurrentModificationException("Replay desired chunks changed");
                for(var iterator=pending.entrySet().iterator();iterator.hasNext();) {
                    var entry=iterator.next();if(!next.contains(entry.getKey())){entry.getValue().cancel();iterator.remove();}
                }
                ready.keySet().retainAll(next);failures.keySet().retainAll(next);desired=next;backpressure=false;
            }
            void retry(ChunkPos region)throws IOException {
                check();if(!desired.contains(region))throw new IllegalArgumentException("Replay chunk not desired");failures.remove(region);
            }
            void tick()throws IOException {
                check();backpressure=false;
                try {
                    for(var iterator=pending.entrySet().iterator();iterator.hasNext();) {
                        var entry=iterator.next();var progress=entry.getValue().outcome();
                        switch(progress.phase()) {
                            case READY->{ready.put(entry.getKey(),chunkResult(entry.getValue(),target));iterator.remove();}
                            case FAILED->{failures.put(entry.getKey(),progress.failure());iterator.remove();}
                            case CANCELLED->{failures.put(entry.getKey(),new IOException("Replay chunk cancelled"));iterator.remove();}
                            default->{} // Observe once; never wait for worker completion.
                        }
                    }
                    for(var region:desired) {
                        if(ready.containsKey(region)||pending.containsKey(region)||failures.containsKey(region))continue;
                        var request=enqueueChunk(region,maxCellLeaves,revision,target.beginReplayFence(),Binding.this,false);
                        if(request==null){backpressure=true;break;}
                        requestsIssued=true;pending.put(region,request);
                    }
                }catch(IOException failure){cancelPending();ready.clear();throw failure;}
            }
            RegionReplayChunk.Snapshot result(ChunkPos region)throws IOException {check();return ready.get(region);}
            IOException failure(ChunkPos region)throws IOException {check();return failures.get(region);}
            ChunkPumpStatus status()throws IOException {
                owner();if(closed)return new ChunkPumpStatus(0,0,0,0,0,false,true);
                check();int staged=4096*ready.size();for(var request:pending.values())staged+=request.completedCells();
                return new ChunkPumpStatus(desired.size(),pending.size(),ready.size(),failures.size(),staged,backpressure,false);
            }
            @Override public void close()throws IOException {
                owner();if(closed)return;cancelPending();desired.clear();ready.clear();failures.clear();backpressure=false;closed=true;
            }
        }
        private Request trySubmit(Window window)throws IOException {
            validate(target);
            var request=enqueue(window.region(),new RegionReplayPage.CellRange(window.first(),window.first()+64),
                    RegionReplayPage.MAX_ROWS,revision,target.beginReplayFence(),this,false);
            if(request!=null)requestsIssued=true;return request;
        }
        record Window(ChunkPos region,int first) {
            static Window atCell(int x,int y,int z) {
                if(!new Protocol.Edit(x,y,z,Blocks.AIR).valid())throw new IllegalArgumentException("Replay demand cell bounds");
                int key=(x&15)|((z&15)<<4)|((y&15)<<8);
                return new Window(ChunkPos.fromBlock(x,y,z),key/64*64);
            }
            Window {
                Objects.requireNonNull(region);
                if(first<0||first>4096-64||first%64!=0)throw new IllegalArgumentException("Replay window alignment");
            }
        }
        record PumpStatus(int desired,int pending,int ready,int failed,boolean backpressure,boolean closed) {}
        Pump openPump(World current,int maxWindows)throws IOException {
            return new Pump(openReadView(current,maxWindows));
        }
        /** Owner-driven bounded ticks only; never waits for worker completion.
         * This private read-only pump does not enable live gameplay. */
        final class Pump implements AutoCloseable {
            private final ReadView read;
            private Set<Window> desired=new LinkedHashSet<>();
            private final Map<Window,Request> pending=new LinkedHashMap<>();
            private final Map<Window,IOException> failures=new LinkedHashMap<>();
            private boolean closed,backpressure;
            private Pump(ReadView read){this.read=read;}
            private void cancelPending(){for(var request:pending.values())request.cancel();pending.clear();}
            private void check()throws IOException {
                owner();if(closed)throw new IOException("Replay pump closed");
                try{validate(target);}catch(IOException failure){cancelPending();throw failure;}
            }
            void desired(List<Window> windows)throws IOException {
                check();Objects.requireNonNull(windows);
                if(windows.size()>read.capacity)throw new IllegalArgumentException("Replay desired-window budget");
                var next=new LinkedHashSet<>(List.copyOf(windows));
                for(var iterator=pending.entrySet().iterator();iterator.hasNext();) {
                    var entry=iterator.next();if(!next.contains(entry.getKey())){entry.getValue().cancel();iterator.remove();}
                }
                failures.keySet().retainAll(next);desired=next;backpressure=false;
            }
            static final int MAX_BOUNDARIES=64;
            /** Bounded owner-side demand hints. Reported readiness may be stale
             * or from another view; only coordinates choose the desired windows.
             * Preflight all hints before cancelling or replacing existing work. */
            void desiredBoundaries(List<dev.jayms.BlockRaycaster.UnavailableCell> boundaries)throws IOException {
                check();Objects.requireNonNull(boundaries);int count=boundaries.size();
                if(count>MAX_BOUNDARIES)throw new IllegalArgumentException("Replay boundary hint budget");
                var next=new LinkedHashSet<Window>();
                for(int i=0;i<count;i++) {
                    var cell=Objects.requireNonNull(boundaries.get(i));
                    next.add(Window.atCell(cell.x(),cell.y(),cell.z()));
                    if(next.size()>read.capacity)throw new IllegalArgumentException("Replay desired-window budget");
                }
                if(boundaries.size()!=count)throw new ConcurrentModificationException("Replay boundary hints changed");
                desired(new ArrayList<>(next));
            }
            void retry(Window window)throws IOException {
                check();if(!desired.contains(window))throw new IllegalArgumentException("Replay window not desired");
                failures.remove(window);
            }
            void tick()throws IOException {
                check();backpressure=false;
                try {
                    for(var iterator=pending.entrySet().iterator();iterator.hasNext();) {
                        var entry=iterator.next();var progress=entry.getValue().outcome();
                        switch(progress.phase()) {
                            case READY->{publish(entry.getValue(),target);iterator.remove();}
                            case FAILED->{failures.put(entry.getKey(),progress.failure());iterator.remove();}
                            case CANCELLED->{failures.put(entry.getKey(),new IOException("Replay window cancelled"));iterator.remove();}
                            default->{} // One observation; never poll or wait here.
                        }
                    }
                    for(var window:desired) {
                        if(read.frames.containsKey(window)||pending.containsKey(window)||failures.containsKey(window))continue;
                        var request=trySubmit(window);
                        if(request==null){backpressure=true;break;}
                        pending.put(window,request);
                    }
                }catch(IOException failure){cancelPending();throw failure;}
            }
            IOException failure(Window window)throws IOException {check();return failures.get(window);}
            PumpStatus status()throws IOException {
                owner();if(closed)return new PumpStatus(0,0,0,0,false,true);
                check();int ready=0;for(var window:desired)if(read.frames.containsKey(window))ready++;
                return new PumpStatus(desired.size(),pending.size(),ready,failures.size(),backpressure,false);
            }
            @Override public void close()throws IOException {
                owner();if(closed)return;cancelPending();desired.clear();failures.clear();backpressure=false;read.close();closed=true;
            }
        }
        RegionReplayPage result(Request request,World current)throws IOException {
            validate(current);Objects.requireNonNull(request);
            if(request.binding!=this)throw new IOException("Replay request belongs to another binding");
            var page=request.checkedWorldResult(revision,current);validate(current);return page;
        }
        /** Open before the first request. Read-only data coverage is distinct
         * from live geometry, history, mesh readiness and save authority. */
        ReadView openReadView(World current,int maxWindows)throws IOException {
            validate(current);
            if(view!=null||chunkPump!=null||requestsIssued)throw new IOException("Replay view must open before requests");
            var candidate=new ReadView(maxWindows);
            try {
                current.attachReplayReadView(candidate);view=candidate;baseline=current.beginReplayFence();return candidate;
            }catch(IllegalStateException|ConcurrentModificationException failure) {
                candidate.close();throw new IOException("Replay read view unavailable",failure);
            }
        }
        /** Publish one complete immutable 64-cell window. Failure leaves
         * existing coverage unchanged. No canonical world geometry is installed. */
        void publish(Request request,World current)throws IOException {
            validate(current);if(view==null)throw new IOException("Replay read view not open");
            var page=result(request,current);view.publish(page);
        }
        final class ReadView implements World.ReplayReadView {
            static final int MAX_WINDOWS=4;
            private final int capacity;
            private final WorldVoxels implicit=new WorldVoxels(target.terrain());
            private Map<Window,RegionReplayPage> frames=new LinkedHashMap<>();
            private boolean closed;
            private ReadView(int capacity){if(capacity<1||capacity>MAX_WINDOWS)throw new IllegalArgumentException("Replay view window budget");this.capacity=capacity;}
            private void check() {
                try{validate(target);}catch(IOException error){throw new IllegalStateException("Replay read view unavailable",error);}
            }
            private RegionReplayPage find(int x,int y,int z) {
                if(!new Protocol.Edit(x,y,z,0).valid())return null;
                int key=(x&15)|((z&15)<<4)|((y&15)<<8);
                return frames.get(new Window(ChunkPos.fromBlock(x,y,z),key/64*64));
            }
            @Override public boolean ready(int x,int y,int z){check();return find(x,y,z)!=null;}
            @Override public dev.jayms.net.model.SparseVoxelOctree cell(int x,int y,int z) {
                check();var page=find(x,y,z);
                if(page==null)throw new World.UnloadedReplayCellException(x,y,z);
                var image=page.cells().get(new WorldVoxels.CellPosition(x,y,z));
                return image==null?implicit.cell(x,y,z):image.tree();
            }
            private void publish(RegionReplayPage page)throws IOException {
                var range=page.range();
                if(range.size()!=64||range.firstInclusive()%64!=0)throw new IOException("Replay view requires an aligned 64-cell window");
                var next=new LinkedHashMap<>(frames);var key=new Window(page.region(),range.firstInclusive());next.remove(key);next.put(key,page);
                if(next.size()>capacity)next.remove(next.keySet().iterator().next());
                frames=next;
            }
            int retainedWindows()throws IOException{owner();return frames.size();}
            @Override public void close() {
                if(Thread.currentThread()!=owner)throw new IllegalStateException("Replay view owner required");
                frames=Map.of();closed=true;
            }
        }
    }
    Binding bindTarget(LocalGame local,World target)throws IOException {
        owner();Objects.requireNonNull(local);Objects.requireNonNull(target);
        if(stopping.get()||status.state()!=State.RUNNING)throw new IOException("Replay source not available");
        if(targetBinding!=null)throw new IOException("Replay target already bound");
        if(local!=transferredMetadata||local.replayComplete()||local.models!=transferredModels
                ||transferredModels.revision()!=transferredModelsRevision||!fingerprint.equals(local.readbackFingerprint())
                ||target.models()!=transferredModels||target.terrain().seed!=local.seed||target.terrain().version!=local.generatorVersion
                ||target.terrain().stressGrid()!=null||target.terrain().geometryRevision()!=0
                ||target.editsVersion()!=0||target.skyGeometryRevision()!=0||!target.getLoadedChunks().isEmpty())
            throw new IOException("Replay target does not match source metadata");
        try{var binding=new Binding(local,target);targetBinding=binding;return binding;}
        catch(IllegalStateException|ConcurrentModificationException changed){throw new IOException("Replay target unavailable",changed);}
    }
    private Request active; // worker-owned except under gate during close
    private boolean retryCleanup;

    RegionReplayWorker(Path directory,Factory factory,int capacity,int maxRegions,long maxBytes,int maxRegionRows) {
        Objects.requireNonNull(directory);Objects.requireNonNull(factory);
        if(capacity<1||capacity>MAX_QUEUED||maxRegions<1||maxRegions>RegionReplayCache.MAX_REGIONS||
                maxBytes<0||maxBytes>RegionReplayCache.MAX_BYTES||maxRegionRows<0||maxRegionRows>LocalGame.MAX_SAVED_EDITS||
                (long)maxRegionRows*RegionReplayCells.RECORD_BYTES>maxBytes)
            throw new IllegalArgumentException("Replay worker budget");
        this.capacity=capacity;
        var thread=new Thread(()->run(directory,factory,maxRegions,maxBytes,maxRegionRows),"voxel-region-readback");
        thread.setDaemon(true);thread.start();
    }
    Status status(){return status;}
    private void owner()throws IOException{if(Thread.currentThread()!=owner)throw new IOException("Replay worker owner required");}
    Request submit(ChunkPos region,RegionReplayPage.CellRange range,int maxRows,Revision revision)throws IOException {
        return enqueue(region,range,maxRows,revision,null,null);
    }
    /** Owner-side constant-field capture; no model/history copy or file I/O. */
    Request submitFenced(ChunkPos region,RegionReplayPage.CellRange range,int maxRows,Revision revision,World world)throws IOException {
        owner();Objects.requireNonNull(world);
        return enqueue(region,range,maxRows,revision,world.beginReplayFence(),null);
    }
    Request submitChunkFenced(ChunkPos region,int maxCellLeaves,Revision revision,World world)throws IOException {
        owner();Objects.requireNonNull(world);
        return enqueueChunk(region,maxCellLeaves,revision,world.beginReplayFence(),null);
    }
    /** Opt-in bounded same-source neighbour loading. No live mesh installation.
     * Mask bits are -X,+X,-Y,+Y,-Z,+Z; zero keeps unknown exterior faces. */
    Request submitChunkWithNeighboursFenced(ChunkPos region,int maxCellLeaves,int neighbourMask,Revision revision,World world)throws IOException {
        owner();Objects.requireNonNull(world);
        return enqueueChunk(region,maxCellLeaves,revision,world.beginReplayFence(),null,true,neighbourMask);
    }
    private static List<ChunkPos> neighbourRegions(ChunkPos region,int mask)throws IOException {
        if(mask<0||mask>63)throw new IllegalArgumentException("Replay worker neighbour mask");
        if(mask==0)return List.of();
        RegionReplayChunk.checkRegion(region);var result=new ArrayList<ChunkPos>(Integer.bitCount(mask));
        int[][] offsets={{-1,0,0},{1,0,0},{0,-1,0},{0,1,0},{0,0,-1},{0,0,1}};
        for(int i=0;i<6;i++)if((mask&(1<<i))!=0) {
            var d=offsets[i];var position=new ChunkPos(region.chunkX()+d[0],region.chunkY()+d[1],region.chunkZ()+d[2]);
            RegionReplayChunk.checkRegion(position);result.add(position);
        }
        return List.copyOf(result);
    }
    private Request enqueueChunk(ChunkPos region,int maxCellLeaves,Revision revision,World.ReplayFence fence,Binding binding)throws IOException {
        return enqueueChunk(region,maxCellLeaves,revision,fence,binding,true);
    }
    private Request enqueueChunk(ChunkPos region,int maxCellLeaves,Revision revision,World.ReplayFence fence,Binding binding,boolean rejectFull)throws IOException {
        return enqueueChunk(region,maxCellLeaves,revision,fence,binding,rejectFull,0);
    }
    private Request enqueueChunk(ChunkPos region,int maxCellLeaves,Revision revision,World.ReplayFence fence,Binding binding,boolean rejectFull,int neighbourMask)throws IOException {
        owner();Objects.requireNonNull(region);Objects.requireNonNull(revision);
        var neighbours=neighbourRegions(region,neighbourMask);
        if(maxCellLeaves<0||maxCellLeaves>RegionReplayChunk.MAX_CELL_LEAVES)
            throw new IllegalArgumentException("Replay worker chunk geometry budget");
        synchronized(gate) {
            if(stopping.get())throw new IOException("Replay worker not accepting requests");
            if(queue.size()>=capacity){if(rejectFull)throw new IOException("Replay worker queue full");return null;}
            var request=new Request(region,null,0,revision,fence,binding,maxCellLeaves,neighbours);
            queue.addLast(request);gate.notifyAll();return request;
        }
    }
    private Request enqueue(ChunkPos region,RegionReplayPage.CellRange range,int maxRows,Revision revision,World.ReplayFence fence,Binding binding)throws IOException {
        return enqueue(region,range,maxRows,revision,fence,binding,true);
    }
    private Request enqueue(ChunkPos region,RegionReplayPage.CellRange range,int maxRows,Revision revision,World.ReplayFence fence,Binding binding,boolean rejectFull)throws IOException {
        owner();Objects.requireNonNull(region);Objects.requireNonNull(range);Objects.requireNonNull(revision);
        if(range.size()>RegionReplayPage.MAX_CELLS||maxRows<0||maxRows>RegionReplayPage.MAX_ROWS)
            throw new IllegalArgumentException("Replay worker page budget");
        synchronized(gate) {
            if(stopping.get())throw new IOException("Replay worker not accepting requests");
            if(queue.size()>=capacity){if(rejectFull)throw new IOException("Replay worker queue full");return null;}
            var request=new Request(region,range,maxRows,revision,fence,binding);queue.addLast(request);gate.notifyAll();return request;
        }
    }
    private void run(Path directory,Factory factory,int maxRegions,long maxBytes,int maxRegionRows) {
        Source source=null;RegionReplayCache cache=null;IOException failure=null;
        try {
            source=Objects.requireNonNull(factory.open(stopping::get,rows->{
                synchronized(gate){if(!stopping.get())status=new Status(State.OPENING,Math.max(status.sourceRows(),Math.max(0,Math.min(rows,LocalGame.MAX_SAVED_EDITS))),null);}
            }));
            StructurePackage.cancelled(stopping::get);
            fingerprint=source.fingerprint();metadata=source.metadata();sourceRevision=source.revision();
            var opened=source;
            cache=new RegionReplayCache(directory,source.spool(),maxRegions,maxBytes,maxRegionRows,edit->{
                var request=active;if(request!=null)request.checkedRows.incrementAndGet();return opened.allowed().test(edit);
            });
            synchronized(gate){if(!stopping.get())status=new Status(State.RUNNING,status.sourceRows(),null);}
            for(;;) {
                Request request;
                synchronized(gate) {
                    while(queue.isEmpty()&&!stopping.get())gate.wait();
                    if(stopping.get())break;request=queue.removeFirst();active=request;
                }
                try {
                    if(request.cancelled())continue;
                    request.finish(new Outcome(Phase.LOADING,null,null));
                    if(!request.revision.equals(source.revision()))throw new IOException("Replay source revision mismatch");
                    if(source.metadata()!=null&&!source.metadata().matchesTerrain(source.terrain()))throw new IOException("Replay source terrain changed");
                    RegionReplayPage page=null;RegionReplayChunk.Snapshot chunk=null;
                    if(request.maxCellLeaves>=0)chunk=RegionReplayChunk.load(cache,request.region,source.terrain(),request.maxCellLeaves,
                            ()->stopping.get()||request.cancelled(),request.completedCells::set);
                    else page=cache.load(request.region,request.range,source.terrain(),request.maxRows,
                            ()->stopping.get()||request.cancelled());
                    if(chunk!=null) {
                        var sourceMetadata=source.metadata();
                        chunk=RegionReplayModelDraws.prepare(chunk,sourceMetadata==null?type->null:sourceMetadata::sourceDefinition,
                                ()->stopping.get()||request.cancelled());
                        // At most six extra frozen roots. No neighbour render arrays,
                        // model batches or live World queries cross onto this worker.
                        var neighbours=new ArrayList<RegionReplayChunk.Snapshot>(request.neighbourRegions.size());
                        for(var position:request.neighbourRegions) {
                            StructurePackage.cancelled(()->stopping.get()||request.cancelled());
                            neighbours.add(RegionReplayChunk.load(cache,position,source.terrain(),request.maxCellLeaves,
                                    ()->stopping.get()||request.cancelled()));
                        }
                        chunk=RegionReplayMesh.prepare(chunk,neighbours,()->stopping.get()||request.cancelled());
                        if(source.terrain().geometryRevision()!=chunk.terrainRevision())throw new IOException("Replay source terrain changed");
                    }
                    StructurePackage.cancelled(()->stopping.get()||request.cancelled());
                    if(source.metadata()!=null&&!source.metadata().matchesTerrain(source.terrain()))throw new IOException("Replay source terrain changed");
                    request.finish(new Outcome(Phase.READY,page,chunk,null));
                }catch(CancellationException cancelled){request.cancel();}
                catch(IOException|RuntimeException error){request.finish(new Outcome(Phase.FAILED,null,new IOException("Replay page request failed",error)));}
                catch(Error error){request.finish(new Outcome(Phase.FAILED,null,new IOException("Replay page worker failed",error)));throw error;}
                finally{synchronized(gate){active=null;}}
            }
        }catch(CancellationException cancelled) {
            if(!stopping.get())failure=new IOException("Replay source cancelled",cancelled);
        }catch(Exception|Error error){failure=new IOException("Replay worker failed",error);}
        finally {
            synchronized(gate) {
                stopping.set(true);
                if(active!=null)active.finish(new Outcome(Phase.FAILED,null,new IOException("Replay worker stopped")));
                for(var request:queue) {
                    if(failure==null)request.finish(new Outcome(Phase.CANCELLED,null,null));
                    else request.finish(new Outcome(Phase.FAILED,null,failure));
                }
                queue.clear();active=null;metadata=null;status=new Status(State.CLOSING,status.sourceRows(),failure);
            }
            // Cleanup stays on the resource-owning worker. Retry only after an
            // explicit close call; never loop/poll on filesystem failures.
            boolean cacheClosed=cache==null,sourceClosed=source==null;
            for(;;) {
                IOException cleanup=null;
                if(!cacheClosed)try{cache.close();cacheClosed=true;}catch(IOException error){cleanup=error;}
                if(!sourceClosed)try{source.spool().close();sourceClosed=true;}catch(IOException error){if(cleanup==null)cleanup=error;else cleanup.addSuppressed(error);}
                synchronized(gate) {
                    if(cleanup==null){status=new Status(failure==null?State.CLOSED:State.FAILED,status.sourceRows(),failure);return;}
                    status=new Status(State.CLEANUP_FAILED,status.sourceRows(),cleanup);
                    while(!retryCleanup)try{gate.wait();}catch(InterruptedException interrupted){/* Retry still needs an explicit close. */}
                    retryCleanup=false;
                }
            }
        }
    }
    /** Owner-side signal only: never joins the worker or waits for I/O. A later
     * close retries failed cleanup on that same worker. Poll status for receipt. */
    @Override public void close()throws IOException {
        owner();if(targetBinding!=null&&targetBinding.view!=null)targetBinding.view.close();
        if(targetBinding!=null&&targetBinding.chunkPump!=null)targetBinding.chunkPump.close();
        transferredMetadata=null;transferredModels=null;targetBinding=null;synchronized(gate) {
            if(status.state()==State.CLOSED||status.state()==State.FAILED)return;
            stopping.set(true);if(status.state()==State.CLEANUP_FAILED)retryCleanup=true;
            if(active!=null)active.cancel();
            for(var request:queue)request.finish(new Outcome(Phase.CANCELLED,null,null));queue.clear();
            if(status.state()!=State.CLEANUP_FAILED)status=new Status(State.CLOSING,status.sourceRows(),null);
            gate.notifyAll();
        }
    }
}
