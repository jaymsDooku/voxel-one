package dev.jayms.net;
import dev.jayms.*;
import dev.jayms.net.structure.StructurePackage;
import java.io.IOException;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/** One private owner for ordinary/model staging and all-or-nothing resource
 * transfer. Does not install into a World or promote collision/save readiness. */
final class RegionReplayChunkUpload implements AutoCloseable {
    record Progress(boolean ordinaryReady,int modelsReady,int modelTypes,boolean instancePhase) {}
    static final class Resources implements AutoCloseable {
        private final Thread owner=Thread.currentThread();
        private final RegionReplayWorker.Binding binding;private final RegionReplayWorker.Request request;private final World world;
        private final RegionReplayChunk.Snapshot snapshot;private final Mesh ordinary;private final RegionReplayModelUpload.Resources models;
        private boolean closed;
        private Resources(RegionReplayWorker.Binding binding,RegionReplayWorker.Request request,World world,RegionReplayChunk.Snapshot snapshot,Mesh ordinary,RegionReplayModelUpload.Resources models) {
            this.binding=binding;this.request=request;this.world=world;this.snapshot=snapshot;this.ordinary=ordinary;this.models=models;
        }
        private void owner()throws IOException{if(Thread.currentThread()!=owner)throw new IOException("Replay chunk GL owner required");}
        void validate()throws IOException{owner();if(closed)throw new IOException("Replay chunk resources closed");if(binding.chunkResult(request,world)!=snapshot)throw new IOException("Replay chunk resource source changed");}
        /** Borrowed handles only. Close the Resources owner, not individual handles. */
        Mesh ordinary()throws IOException{validate();return ordinary;}
        RegionReplayModelUpload.Resources models()throws IOException{validate();return models;}
        RegionReplayChunk.Snapshot snapshot()throws IOException{validate();return snapshot;}
        @Override public void close()throws IOException{owner();if(closed)return;closeOwned(ordinary,models);closed=true;}
    }
    private final Thread owner=Thread.currentThread();
    private final RegionReplayWorker.Binding binding;private final RegionReplayWorker.Request request;private final World world;
    private final RegionReplayChunk.Snapshot snapshot;
    private final RegionReplayMeshUpload ordinary;private final RegionReplayModelUpload models;private boolean closed;
    RegionReplayChunkUpload(RegionReplayWorker.Binding binding,RegionReplayWorker.Request request,World world)throws IOException {
        this.binding=Objects.requireNonNull(binding);this.request=Objects.requireNonNull(request);this.world=Objects.requireNonNull(world);snapshot=binding.chunkResult(request,world);
        ordinary=new RegionReplayMeshUpload(binding,request,world);
        try{models=new RegionReplayModelUpload(binding,request,world);}
        catch(IOException|RuntimeException|Error failure){try{ordinary.close();}catch(IOException|RuntimeException|Error cleanup){failure.addSuppressed(cleanup);}throw failure;}
    }
    private void owner()throws IOException{if(Thread.currentThread()!=owner)throw new IOException("Replay chunk GL owner required");}
    private void validate()throws IOException{owner();if(closed)throw new IOException("Replay chunk upload closed");if(binding.chunkResult(request,world)!=snapshot)throw new IOException("Replay chunk source changed");}
    private void cleanup(Throwable failure){try{close();}catch(IOException|RuntimeException|Error cleanup){failure.addSuppressed(cleanup);}}
    boolean ready()throws IOException{owner();try{validate();return ordinary.ready()&&models.ready();}catch(IOException|RuntimeException|Error failure){cleanup(failure);throw failure;}}
    Progress progress()throws IOException {
        owner();try{validate();var p=models.progress();return new Progress(ordinary.ready(),p.completed(),p.total(),p.instances());}
        catch(IOException|RuntimeException|Error failure){cleanup(failure);throw failure;}
    }
    /** One lane per pump. The byte/deadline allowance is shared, not doubled. */
    int pump(int bytes,long deadline,BooleanSupplier cancel)throws IOException {
        owner();Objects.requireNonNull(cancel);if(bytes<0||bytes>RegionReplayMeshUpload.MAX_PUMP_BYTES)throw new IllegalArgumentException("Replay chunk upload byte budget");
        try{validate();StructurePackage.cancelled(cancel);int written=ordinary.ready()?models.pump(bytes,deadline,cancel):ordinary.pump(bytes,deadline,cancel);validate();StructurePackage.cancelled(cancel);return written;}
        catch(IOException|RuntimeException|Error failure){cleanup(failure);throw failure;}
    }
    Resources take(BooleanSupplier cancel)throws IOException {
        owner();Objects.requireNonNull(cancel);Mesh base=null;RegionReplayModelUpload.Resources detail=null;
        try {
            validate();StructurePackage.cancelled(cancel);if(!ready())throw new IOException("Replay chunk upload incomplete");
            base=ordinary.take(cancel);detail=models.take(cancel);validate();StructurePackage.cancelled(cancel);
            var result=new Resources(binding,request,world,snapshot,base,detail);base=null;detail=null;closed=true;return result;
        }catch(IOException|RuntimeException|Error failure){try{closeOwned(base,detail);}catch(IOException|RuntimeException|Error cleanup){failure.addSuppressed(cleanup);}cleanup(failure);throw failure;}
    }
    @Override public void close()throws IOException{owner();if(closed)return;closeOwned(ordinary,models);closed=true;}
    private static void closeOwned(AutoCloseable first,AutoCloseable second)throws IOException {
        Throwable failure=null;
        for(var resource:new AutoCloseable[]{first,second})if(resource!=null)try{resource.close();}catch(Exception|Error cleanup){if(failure==null)failure=cleanup;else failure.addSuppressed(cleanup);}
        if(failure instanceof IOException io)throw io;if(failure instanceof RuntimeException runtime)throw runtime;if(failure instanceof Error error)throw error;if(failure!=null)throw new IOException("Replay GL cleanup failed",failure);
    }
}
