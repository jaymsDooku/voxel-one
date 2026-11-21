package dev.jayms.net;

import dev.jayms.*;
import dev.jayms.net.structure.StructurePackage;
import java.io.IOException;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/** Private ordinary-mesh GL staging only. Never installs into Chunk/World or
 * grants coverage, model GPU readiness or save authority. GL allocation may
 * block; slice budgets are not a measured frame-time guarantee. */
final class RegionReplayMeshUpload implements AutoCloseable {
    static final int MAX_PUMP_BYTES=256*1024;
    private final Thread owner=Thread.currentThread();
    private final RegionReplayWorker.Binding binding;
    private final RegionReplayWorker.Request request;
    private final World world;
    private final RegionReplayChunk.Snapshot snapshot;
    private Mesh.Upload upload;
    private boolean closed;
    RegionReplayMeshUpload(RegionReplayWorker.Binding binding,RegionReplayWorker.Request request,World world)throws IOException {
        this.binding=Objects.requireNonNull(binding);this.request=Objects.requireNonNull(request);this.world=Objects.requireNonNull(world);
        snapshot=binding.chunkResult(request,world);
        if(snapshot.detached()==null||snapshot.mesh()==null)throw new IOException("Replay CPU mesh unavailable");
        snapshot.detached().validate();
    }
    private void owner()throws IOException{if(Thread.currentThread()!=owner)throw new IOException("Replay GL owner required");}
    private void validate()throws IOException {
        owner();if(closed)throw new IOException("Replay mesh upload closed");
        if(binding.chunkResult(request,world)!=snapshot)throw new IOException("Replay mesh source changed");
    }
    private boolean complete(){return snapshot.mesh().indices().length==0||upload!=null&&upload.ready();}
    boolean ready()throws IOException {
        owner();try{validate();return complete();}catch(IOException|RuntimeException|Error failure){cleanup(failure);throw failure;}
    }
    int pump(int byteBudget,long deadline,BooleanSupplier cancel)throws IOException {
        owner();Objects.requireNonNull(cancel);
        if(byteBudget<0||byteBudget>MAX_PUMP_BYTES)throw new IllegalArgumentException("Replay mesh upload byte budget");
        try {
            validate();StructurePackage.cancelled(cancel);
            if(complete()||byteBudget<4||System.nanoTime()>=deadline)return 0;
            if(upload==null)upload=new Mesh.Upload(snapshot.mesh());
            int bytes=upload.pump(byteBudget,deadline);
            validate();StructurePackage.cancelled(cancel);return bytes;
        }catch(IOException|RuntimeException|Error failure){cleanup(failure);throw failure;}
    }
    /** Transfers only the ordinary Mesh handle. Caller must close it; source,
     * model, installation and coverage checks remain separate. Single use. */
    Mesh take(BooleanSupplier cancel)throws IOException {
        owner();Objects.requireNonNull(cancel);Mesh result=null;
        try {
            validate();StructurePackage.cancelled(cancel);
            if(!complete())throw new IOException("Replay mesh upload incomplete");
            if(upload!=null)result=upload.take();
            validate();StructurePackage.cancelled(cancel);
            close();return result;
        }catch(IOException|RuntimeException|Error failure){if(result!=null)try{result.close();}catch(RuntimeException|Error cleanup){failure.addSuppressed(cleanup);}cleanup(failure);throw failure;}
    }
    private void cleanup(Throwable failure){try{close();}catch(IOException|RuntimeException|Error cleanup){failure.addSuppressed(cleanup);}}
    @Override public void close()throws IOException {owner();if(closed)return;if(upload!=null)upload.close();closed=true;}
}
