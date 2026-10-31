import dev.jayms.ChunkPos;
import dev.jayms.net.*;
import dev.jayms.net.model.*;
import dev.jayms.net.structure.*;
import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import static dev.jayms.net.structure.StructurePackage.*;

/** Actual engine adapter over private synthetic roots/files. Not LocalGame or server persistence. */
public final class RegionAuthorityPrototype implements StructureEngine.Authority {
    private record Root(long revision,long generation,WorldVoxels world,ModelLibrary models,Map<Position,SparseVoxelOctree> known){}
    private record Stamp(long revision,Object geometry){}
    private static final RegionHistoryPrototype.CodecBudget BUDGET=new RegionHistoryPrototype.CodecBudget(8*1024*1024,100_000,1024,131_072);
    private final Object lock=new Object();private final RegionGenerationPrototype store;
    private final long seed;private final int terrainVersion;private volatile Root root;
    Runnable beforeApply=()->{};
    public RegionAuthorityPrototype(Path directory,long seed,int version)throws IOException{this(directory,seed,version,RegionGenerationPrototype.NIO);}
    RegionAuthorityPrototype(Path directory,long seed,int version,RegionGenerationPrototype.Operations operations)throws IOException {
        this.seed=seed;terrainVersion=version;store=new RegionGenerationPrototype(directory,operations);
        var loaded=store.load(()->false);var known=new LinkedHashMap<Position,SparseVoxelOctree>();
        if(loaded.isEmpty())root=new Root(0,0,new WorldVoxels(new Terrain(seed,version)),new ModelLibrary(),Map.of());
        else {
            var saved=loaded.orElseThrow();
            var identity=saved.formatVersion()==1?saved.manifest().geometrySnapshot():saved.worldLibrary().geometrySnapshot();
            if(identity.terrainSeed()!=seed||identity.terrainVersion()!=version)throw new IOException("World identity mismatch");
            WorldVoxels world;ModelLibrary models;
            if(saved.formatVersion()==1){var restored=saved.manifest().restore(new ModelLibrary(),()->false);world=restored.world();models=restored.models();}
            else {var restored=saved.worldLibrary().restore(()->false);world=restored.world();models=restored.models();}
            for(var region:identity.regions().values())for(var cell:region)known.put(new Position(cell.x(),cell.y(),cell.z()),world.cell(cell.x(),cell.y(),cell.z()));
            root=new Root(0,saved.generation(),world,models,Collections.unmodifiableMap(known));
        }
    }
    @Override public Object lock(){return lock;}
    @Override public ModelLibrary models(){return root.models().copy();}
    @Override public SparseVoxelOctree cell(int x,int y,int z){return root.world().cell(x,y,z);}
    @Override public Object chunkStamp(ChunkPos position){var state=root;return new Stamp(state.revision(),state.world().chunkStamp(position));}
    public long revision(){return root.revision();}
    public long generation(){return root.generation();}
    public Source snapshot() {
        var state=root;
        return new Source(){
            public SparseVoxelOctree cell(int x,int y,int z){return state.world().cell(x,y,z);}
            public ModelLibrary models(){return state.models().copy();}
            public Object chunkStamp(ChunkPos p){return new Stamp(state.revision(),state.world().chunkStamp(p));}
        };
    }
    private static void check(BooleanSupplier cancel){if(Thread.currentThread().isInterrupted()||cancel.getAsBoolean())throw new CancellationException();}
    /** Synthetic concurrent edit; deliberately dirty until a later generation is saved. */
    public void edit(Protocol.Edit edit)throws IOException {
        if(!edit.valid())throw new IOException("Invalid synthetic edit");
        synchronized(lock) {
            var state=root;if(Blocks.isModel(edit.type())&&state.models().get(edit.type())==null)throw new IOException("Unknown model");
            var world=state.world().snapshot();world.apply(edit);var known=new LinkedHashMap<>(state.known());
            known.put(new Position(edit.x(),edit.y(),edit.z()),world.cell(edit.x(),edit.y(),edit.z()));
            root=new Root(state.revision()+1,state.generation(),world,state.models(),Collections.unmodifiableMap(known));
        }
    }
    @Override public void commit(ModelLibrary models,Map<Position,SparseVoxelOctree> cells)throws IOException {
        try(var staged=stageCommit(models,cells,root.models().revision(),()->false)){synchronized(lock){staged.apply();}}
    }
    @Override public StructureEngine.Commit stageCommit(ModelLibrary models,Map<Position,SparseVoxelOctree> cells,long modelRevision,BooleanSupplier cancel)throws IOException {
        check(cancel);Root expected;WorldVoxels world;Map<Position,SparseVoxelOctree> known;
        synchronized(lock){expected=root;if(expected.models().revision()!=modelRevision)throw new IOException("Model revision changed");world=expected.world().snapshot();known=new LinkedHashMap<>(expected.known());}
        var stagedModels=models.copy();
        for(var entry:cells.entrySet()){check(cancel);var p=entry.getKey();var tree=entry.getValue().copy().freeze();world.replaceCell(p.x(),p.y(),p.z(),tree);known.put(p,tree);if(known.size()>100_000)throw new IOException("Private final-cell budget");}
        var records=new ArrayList<Protocol.CellState>();
        for(var entry:known.entrySet()){check(cancel);var p=entry.getKey();records.add(new Protocol.CellState(p.x(),p.y(),p.z(),entry.getValue()));}
        var geometry=RegionHistoryPrototype.fromCells(seed,terrainVersion,records,BUDGET,cancel);
        var worldLibrary=RegionWorldLibraryPrototype.capture(geometry,stagedModels,cancel);var file=store.stage(worldLibrary,cancel);
        if(file.expectedGeneration()!=expected.generation()){file.close();throw new IOException("Disk generation changed; reload");}
        var candidate=new Root(expected.revision()+1,file.nextGeneration(),world,stagedModels,Collections.unmodifiableMap(known));
        return new StructureEngine.Commit(){
            private boolean closed;
            @Override public void apply()throws IOException {
                if(!Thread.holdsLock(lock))throw new IOException("Authority lock required");
                if(closed)throw new IOException("Commit closed");var hook=beforeApply;beforeApply=()->{};hook.run();
                if(root!=expected)throw new IOException("Root changed; retry publication");check(cancel);
                file.commit(cancel);root=candidate;closed=true;
            }
            @Override public void close()throws IOException{closed=true;file.close();}
        };
    }
}
