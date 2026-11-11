package dev.jayms.net;
import dev.jayms.*;
import dev.jayms.net.model.ModelDefinition;
import dev.jayms.net.structure.StructurePackage;
import java.io.IOException;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Private serial model/instance GL staging. Not Main/World publication or
 * GPU coverage. Caller-owned CPU geometry must remain unchanged while staging. */
final class RegionReplayModelUpload implements AutoCloseable {
    record Batch(int type,ModelDefinition definition,Mesh mesh,int instances) {}
    static final class Resources implements AutoCloseable {
        private final Thread owner=Thread.currentThread();private final List<Batch> batches;private boolean closed;
        Resources(List<Batch> batches){this.batches=List.copyOf(batches);}
        List<Batch> batches()throws IOException{owner();if(closed)throw new IOException("Replay model resources closed");return batches;}
        private void owner()throws IOException{if(Thread.currentThread()!=owner)throw new IOException("Replay model GL owner required");}
        @Override public void close()throws IOException{owner();if(closed)return;for(var batch:batches)if(batch.mesh()!=null)batch.mesh().close();closed=true;}
    }
    record Progress(int completed,int total,boolean instances) {}
    private final Thread owner=Thread.currentThread();
    private final RegionReplayWorker.Binding binding;private final RegionReplayWorker.Request request;private final World world;
    private final RegionReplayChunk.Snapshot snapshot;private final List<RegionReplayModelDraws.Batch> batches;
    private final List<float[]> positions=new ArrayList<>();private final List<Batch> completed=new ArrayList<>();
    private int index;private Mesh.Upload upload;private Mesh current;private Mesh.InstanceUpload instances;private boolean closed;
    RegionReplayModelUpload(RegionReplayWorker.Binding binding,RegionReplayWorker.Request request,World world)throws IOException {
        this.binding=Objects.requireNonNull(binding);this.request=Objects.requireNonNull(request);this.world=Objects.requireNonNull(world);
        snapshot=binding.chunkResult(request,world);
        if(snapshot.detached()==null||snapshot.modelDraws()==null)throw new IOException("Replay model CPU data unavailable");
        batches=snapshot.modelDraws().batches();if(batches.size()>RegionReplayModelDraws.MAX_TYPES)throw new IOException("Replay model type budget");
        var seen=new BitSet(4096);int count=0;
        for(var batch:batches) {
            if(batch.cells().length>4096-count)throw new IOException("Replay model cell budget");count+=batch.cells().length;var xyz=new float[batch.cells().length*3];
            for(int i=0;i<batch.cells().length;i++){int key=batch.cells()[i];if(key<0||key>=4096||seen.get(key))throw new IOException("Replay model cell changed");seen.set(key);xyz[i*3]=key&15;xyz[i*3+1]=key>>>8;xyz[i*3+2]=(key>>>4)&15;}
            positions.add(xyz);
        }
    }
    private void owner()throws IOException{if(Thread.currentThread()!=owner)throw new IOException("Replay model GL owner required");}
    private void validate()throws IOException{owner();if(closed)throw new IOException("Replay model upload closed");if(binding.chunkResult(request,world)!=snapshot)throw new IOException("Replay model source changed");}
    private void cleanup(Throwable failure){try{close();}catch(IOException|RuntimeException|Error cleanup){failure.addSuppressed(cleanup);}}
    boolean ready()throws IOException{owner();try{validate();return index==batches.size();}catch(IOException|RuntimeException|Error failure){cleanup(failure);throw failure;}}
    Progress progress()throws IOException{owner();try{validate();return new Progress(index,batches.size(),current!=null);}catch(IOException|RuntimeException|Error failure){cleanup(failure);throw failure;}}
    int pump(int bytes,long deadline,BooleanSupplier cancel)throws IOException {
        owner();Objects.requireNonNull(cancel);if(bytes<0||bytes>RegionReplayMeshUpload.MAX_PUMP_BYTES)throw new IllegalArgumentException("Replay model upload byte budget");
        try {
            validate();StructurePackage.cancelled(cancel);if(index==batches.size()||bytes<4||System.nanoTime()>=deadline)return 0;
            var batch=batches.get(index);int written;
            if(current==null) {
                if(upload==null) {
                    if(batch.geometry().indices().length==0){completed.add(new Batch(batch.type(),batch.definition(),null,positions.get(index).length/3));index++;validate();return 0;}
                    upload=new Mesh.Upload(new MeshData(batch.geometry().vertices(),batch.geometry().indices()));
                }
                written=upload.pump(bytes,deadline);
                if(upload.ready()){current=upload.take();upload.close();upload=null;}
            }else {
                if(instances==null)instances=new Mesh.InstanceUpload(current,positions.get(index));
                written=instances.pump(bytes,deadline);
                if(instances.ready()){instances.finish();instances.close();instances=null;completed.add(new Batch(batch.type(),batch.definition(),current,positions.get(index).length/3));current=null;index++;}
            }
            validate();StructurePackage.cancelled(cancel);return written;
        }catch(IOException|RuntimeException|Error failure){cleanup(failure);throw failure;}
    }
    Resources take(BooleanSupplier cancel)throws IOException {
        owner();Objects.requireNonNull(cancel);Resources result=null;
        try {
            validate();StructurePackage.cancelled(cancel);if(index!=batches.size())throw new IOException("Replay model upload incomplete");
            result=new Resources(completed);validate();StructurePackage.cancelled(cancel);completed.clear();close();return result;
        }catch(IOException|RuntimeException|Error failure){if(result!=null)try{result.close();}catch(IOException|RuntimeException|Error cleanup){failure.addSuppressed(cleanup);}cleanup(failure);throw failure;}
    }
    @Override public void close()throws IOException {
        owner();if(closed)return;if(instances!=null)instances.close();if(upload!=null)upload.close();if(current!=null)current.close();for(var batch:completed)if(batch.mesh()!=null)batch.mesh().close();closed=true;
    }
}
