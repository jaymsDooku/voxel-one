import dev.jayms.ChunkPos;
import dev.jayms.net.*;
import dev.jayms.net.model.ModelLibrary;
import dev.jayms.net.structure.StructurePackage;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

/** Private owner-side read fence, not a placement authority or game backend.
 * Capture/poll require the same owner and its world mutation lock. Root epochs
 * must change on every root publication, including swapping back to an old root. */
public final class RegionReadViewPrototype {
    public sealed interface State permits Pending,Ready,Failed {}
    public record Pending() implements State {}
    public record Ready(List<Optional<StructurePackage.Cell>> cells) implements State {
        public Ready{cells=List.copyOf(cells);}
    }
    public record Failed(IOException cause) implements State {}
    private final Thread owner=Thread.currentThread();
    private final WorldVoxels world;
    private final Object epoch;
    private final ModelLibrary models;
    private final long modelRevision;
    private final Map<ChunkPos,Object> stamps=new HashMap<>();
    private final List<StructurePackage.Position> positions;
    private final CompletableFuture<List<Optional<StructurePackage.Cell>>> future;
    private Failed failure;
    public RegionReadViewPrototype(WorldVoxels world,Object epoch,ModelLibrary models,
            List<StructurePackage.Position> positions,int dx,int dy,int dz,
            CompletableFuture<List<Optional<StructurePackage.Cell>>> future) {
        this.world=Objects.requireNonNull(world);this.epoch=Objects.requireNonNull(epoch);
        this.models=Objects.requireNonNull(models);modelRevision=models.revision();
        if(positions.size()>1024)throw new IllegalArgumentException("Read view budget");
        this.positions=List.copyOf(positions);this.future=Objects.requireNonNull(future);
        for(var p:this.positions) {
            int x=Math.addExact(p.x(),dx),y=Math.addExact(p.y(),dy),z=Math.addExact(p.z(),dz);
            if(!new Protocol.Edit(x,y,z,0).valid())throw new IllegalArgumentException("Read destination bounds");
            var chunk=ChunkPos.fromBlock(x,y,z);var stamp=world.chunkStamp(chunk);
            if(stamp==null)throw new IllegalStateException("Terrain changed during read capture");
            stamps.put(chunk,stamp);
        }
    }
    private Failed fail(String reason){failure=new Failed(new IOException(reason));future.cancel(false);return failure;}
    /** Recheck even after completion. Ready is immutable data, never permission to
     * mutate later without another current owner/authority validation. */
    public State poll(WorldVoxels current,Object currentEpoch,ModelLibrary currentModels) {
        if(Thread.currentThread()!=owner)throw new IllegalStateException("Original read owner required");
        if(failure!=null)return failure;
        if(current!=world||currentEpoch!=epoch)return fail("World/root changed; retry read");
        if(currentModels!=models||models.revision()!=modelRevision)return fail("Models changed; retry read");
        for(var e:stamps.entrySet())if(!e.getValue().equals(current.chunkStamp(e.getKey())))return fail("Affected chunk/terrain changed; retry read");
        if(!future.isDone())return new Pending();
        try {
            var cells=future.join();
            if(cells==null||cells.size()!=positions.size())return fail("Read result count changed");
            for(int i=0;i<cells.size();i++)if(cells.get(i)==null||cells.get(i).isPresent()&&!cells.get(i).orElseThrow().position().equals(positions.get(i)))return fail("Read result position changed");
            return new Ready(cells);
        }catch(CompletionException|CancellationException invalid){return fail("Region read failed or cancelled; retry read");}
    }
}
