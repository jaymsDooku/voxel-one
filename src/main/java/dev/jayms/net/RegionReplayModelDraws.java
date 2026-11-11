package dev.jayms.net;

import dev.jayms.net.model.*;
import dev.jayms.net.structure.StructurePackage;
import java.io.IOException;
import java.util.*;
import java.util.function.*;

/** Private worker CPU model batches. No GPU objects, World installation or
 * owner-library updates. Definitions are frozen; CPU arrays retain renderer
 * ownership semantics and are not immutable shared authority. */
final class RegionReplayModelDraws {
    static final int MAX_TYPES=512,MAX_INSTANCE_QUADS=262_144;
    record Batch(int type,ModelDefinition definition,ModelMesher.Geometry geometry,int[] cells) {}
    record Draws(List<Batch> batches,long instanceQuads) {}
    private RegionReplayModelDraws() {}
    private static int type(RegionReplayChunk.Snapshot source,int key) {
        return WorldVoxels.decode(source.geometry().uniform((key&15)*16,(key>>>8)*16,((key>>>4)&15)*16,16));
    }
    static RegionReplayChunk.Snapshot prepare(RegionReplayChunk.Snapshot source,IntFunction<ModelDefinition> definitions,
            BooleanSupplier cancel)throws IOException {
        Objects.requireNonNull(source);Objects.requireNonNull(definitions);Objects.requireNonNull(cancel);
        if(source.geometry().size()!=256)throw new IllegalArgumentException("Replay model batch resolution");
        // Persistent root capture prevents a mutable caller wrapper changing the scan.
        source=new RegionReplayChunk.Snapshot(source.region(),source.geometry().copy().freeze(),source.terrainRevision(),
                source.editedCells(),source.stagedCellLeaves(),source.mesh(),source.modelDraws());
        var counts=new LinkedHashMap<Integer,Integer>();
        for(int key=0;key<4096;key++) {
            StructurePackage.cancelled(cancel);int id=type(source,key);if(!Blocks.isModel(id))continue;
            if(!counts.containsKey(id)&&counts.size()==MAX_TYPES)throw new IOException("Replay model type budget");
            counts.merge(id,1,Integer::sum);
        }
        var frozen=new LinkedHashMap<Integer,ModelDefinition>();var faces=new HashMap<Integer,Integer>();long weighted=0;
        // Complete count-only admission before allocating any model render arrays.
        for(var entry:counts.entrySet()) {
            StructurePackage.cancelled(cancel);var definition=definitions.apply(entry.getKey());
            if(definition==null)throw new IOException("Missing replay model dependency");
            var copy=new ModelDefinition(definition.name(),definition.voxels().copy().freeze());
            var tree=copy.voxels();int count=ModelMesher.count(tree.size(),(x,y,z)->{
                StructurePackage.cancelled(cancel);return tree.get(x,y,z);
            },ModelMesher.MAX_QUADS);
            weighted+=(long)count*entry.getValue();
            if(weighted>MAX_INSTANCE_QUADS)throw new IOException("Replay chunk model-instance surface budget");
            frozen.put(entry.getKey(),copy);faces.put(entry.getKey(),count);
        }
        var cells=new HashMap<Integer,int[]>();var offsets=new HashMap<Integer,Integer>();
        for(var entry:counts.entrySet()){cells.put(entry.getKey(),new int[entry.getValue()]);offsets.put(entry.getKey(),0);}
        for(int key=0;key<4096;key++) {
            StructurePackage.cancelled(cancel);int id=type(source,key);var positions=cells.get(id);if(positions==null)continue;
            int offset=offsets.get(id);positions[offset]=key;offsets.put(id,offset+1);
        }
        var batches=new ArrayList<Batch>();
        for(var entry:frozen.entrySet()) {
            StructurePackage.cancelled(cancel);var tree=entry.getValue().voxels();
            var mesh=ModelMesher.mesh(tree.size(),(x,y,z)->{StructurePackage.cancelled(cancel);return tree.get(x,y,z);});
            if(mesh.quads()!=faces.get(entry.getKey()))throw new IOException("Replay model surface changed");
            batches.add(new Batch(entry.getKey(),entry.getValue(),mesh,cells.get(entry.getKey())));
        }
        StructurePackage.cancelled(cancel);
        return new RegionReplayChunk.Snapshot(source.region(),source.geometry(),source.terrainRevision(),source.editedCells(),
                source.stagedCellLeaves(),source.mesh(),new Draws(List.copyOf(batches),weighted));
    }
}
