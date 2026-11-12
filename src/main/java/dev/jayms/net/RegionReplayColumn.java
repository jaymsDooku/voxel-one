package dev.jayms.net;

import dev.jayms.net.model.SparseVoxelOctree;
import dev.jayms.net.structure.StructurePackage;
import java.io.IOException;
import java.util.*;
import java.util.function.*;

/** Private worker-only canonical overrides for one vertical column. Unmentioned
 * cells remain unknown here; this is not complete terrain, sky or world authority.
 * Caller source/model/publication fences remain separate from terrain stability. */
final class RegionReplayColumn {
    static final int MAX_ROWS=RegionReplayPage.MAX_ROWS,MAX_CELLS=MAX_ROWS;
    static final int MAX_LEAVES=dev.jayms.Chunk.MAX_REPLAY_LEAVES;
    record Snapshot(int chunkX,int chunkZ,int inputRows,long terrainRevision,
                    Map<WorldVoxels.CellPosition,WorldVoxels.CellImage> cells) {}
    private RegionReplayColumn() {}
    private static void terrain(Terrain source,long revision)throws IOException {
        if((revision&1)!=0||source.geometryRevision()!=revision)throw new IOException("Replay column terrain changed");
    }
    static Snapshot load(RegionReplaySpool spool,int chunkX,int chunkZ,Terrain source,
            int maxRows,int maxCells,int maxLeaves,Predicate<Protocol.Edit> allowed,BooleanSupplier cancel)throws IOException {
        Objects.requireNonNull(spool);Objects.requireNonNull(source);Objects.requireNonNull(allowed);Objects.requireNonNull(cancel);
        if(maxRows<0||maxRows>MAX_ROWS||maxCells<0||maxCells>MAX_CELLS||maxLeaves<0||maxLeaves>MAX_LEAVES)
            throw new IllegalArgumentException("Replay column geometry budget");
        StructurePackage.cancelled(cancel);long revision=source.geometryRevision();terrain(source,revision);
        var history=new WorldVoxels.History();var positions=new LinkedHashSet<WorldVoxels.CellPosition>();
        var loader=new RegionReplayLoader(history,cancel);int[] inputRows={0};
        spool.visitColumn(chunkX,chunkZ,maxRows,allowed,cancel,(region,rows)->{
            for(var edit:rows) {
                StructurePackage.cancelled(cancel);terrain(source,revision);
                if(++inputRows[0]>maxRows)throw new IOException("Replay column row budget");
                var position=new WorldVoxels.CellPosition(edit.x(),edit.y(),edit.z());
                if(!positions.contains(position)) {
                    if(positions.size()==maxCells)throw new IOException("Replay column cell budget");positions.add(position);
                }
                loader.accept(edit);
            }
        });loader.finish();terrain(source,revision);StructurePackage.cancelled(cancel);
        var grouped=new LinkedHashMap<WorldVoxels.CellPosition,List<Protocol.Edit>>();
        for(var edit:history.values()) {
            StructurePackage.cancelled(cancel);
            grouped.computeIfAbsent(new WorldVoxels.CellPosition(edit.x(),edit.y(),edit.z()),p->new ArrayList<>()).add(edit);
        }
        var images=new LinkedHashMap<WorldVoxels.CellPosition,WorldVoxels.CellImage>();int leaves=0;
        for(var entry:grouped.entrySet()) {
            StructurePackage.cancelled(cancel);terrain(source,revision);var p=entry.getKey();
            var tree=new SparseVoxelOctree(16);tree.fill(0,0,0,16,16,16,WorldVoxels.encode(source.block(p.x(),p.y(),p.z())));
            for(var edit:entry.getValue()) {
                StructurePackage.cancelled(cancel);int side=16>>edit.depth(),x=edit.ix()*side,y=edit.iy()*side,z=edit.iz()*side;
                tree.fill(x,y,z,x+side,y+side,z+side,WorldVoxels.encode(edit));
            }
            int count=tree.leafCount();if(count>maxLeaves-leaves)throw new IOException("Replay column leaf budget");leaves+=count;
            StructurePackage.cancelled(cancel);terrain(source,revision);images.put(p,WorldVoxels.prepareCellImage(tree));
        }
        StructurePackage.cancelled(cancel);terrain(source,revision);
        return new Snapshot(chunkX,chunkZ,inputRows[0],revision,Collections.unmodifiableMap(images));
    }
    /** Complete natural vertical range plus every canonical override. Worker-only
     * input, not sky authority. Explicit air replaces terrain rather than layering
     * on top of it. The caller must still fence source/model/publication identity. */
    record SkyInput(dev.jayms.render.WorldSkyVisibility.Input input,long terrainRevision,long modelRevision,int stagedLeaves) {}
    static SkyInput skyInput(Snapshot source,Terrain terrain,dev.jayms.net.model.ModelLibrary models,
            int maxLeaves,BooleanSupplier cancel,IntConsumer progress)throws IOException {
        Objects.requireNonNull(source);Objects.requireNonNull(terrain);Objects.requireNonNull(models);
        Objects.requireNonNull(cancel);Objects.requireNonNull(progress);
        if(maxLeaves<0||maxLeaves>MAX_LEAVES)throw new IllegalArgumentException("Replay column sky geometry budget");
        long revision=source.terrainRevision(),modelRevision=models.revision();terrain(terrain,revision);
        var capturedModels=models.copy();
        if(capturedModels.revision()!=modelRevision)throw new IOException("Replay column models changed");
        RegionReplayChunk.checkRegion(new dev.jayms.ChunkPos(source.chunkX(),0,source.chunkZ()));
        if(source.cells().size()>MAX_CELLS)throw new IOException("Replay column cell budget");
        for(var entry:source.cells().entrySet()) {
            var p=entry.getKey();var root=entry.getValue().tree();
            if(Math.floorDiv(p.x(),16)!=source.chunkX()||Math.floorDiv(p.z(),16)!=source.chunkZ()
                    ||!new Protocol.Edit(p.x(),p.y(),p.z(),0).valid()||root.size()!=16||!root.isFrozen())
                throw new IOException("Invalid replay column cell");
        }
        var chunks=new ArrayList<dev.jayms.render.WorldSkyVisibility.Geometry>();
        var edits=new ArrayList<dev.jayms.render.WorldSkyVisibility.EditedCell>();int leaves=0,done=0;
        StructurePackage.cancelled(cancel);progress.accept(0);
        for(int cy=Math.floorDiv(Terrain.MIN_Y,16);cy<=Math.floorDiv(Terrain.MAX_Y,16);cy++) {
            var geometry=new SparseVoxelOctree(256);
            for(int key=0;key<4096;key++) {
                StructurePackage.cancelled(cancel);terrain(terrain,revision);
                int x=key&15,z=(key>>>4)&15,y=key>>>8;
                int wx=source.chunkX()*16+x,wy=cy*16+y,wz=source.chunkZ()*16+z;
                var image=source.cells().get(new WorldVoxels.CellPosition(wx,wy,wz));
                SparseVoxelOctree cell;
                if(image!=null)cell=image.tree();
                else {cell=new SparseVoxelOctree(16);cell.fill(0,0,0,16,16,16,WorldVoxels.encode(terrain.block(wx,wy,wz)));}
                int count=cell.leafCount();if(count>maxLeaves-leaves)throw new IOException("Replay column sky geometry budget");leaves+=count;
                geometry.replaceRegion(x*16,y*16,z*16,cell);progress.accept(++done);
            }
            chunks.add(new dev.jayms.render.WorldSkyVisibility.Geometry(new dev.jayms.ChunkPos(source.chunkX(),cy,source.chunkZ()),geometry.freeze()));
        }
        for(var entry:source.cells().entrySet()) {
            StructurePackage.cancelled(cancel);terrain(terrain,revision);var p=entry.getKey();
            if(p.y()>=Terrain.MIN_Y&&p.y()<=Terrain.MAX_Y)continue;
            int count=entry.getValue().tree().leafCount();if(count>maxLeaves-leaves)throw new IOException("Replay column sky geometry budget");leaves+=count;
            edits.add(new dev.jayms.render.WorldSkyVisibility.EditedCell(p.x(),p.y(),p.z(),entry.getValue().tree()));
        }
        StructurePackage.cancelled(cancel);terrain(terrain,revision);
        if(models.revision()!=modelRevision)throw new IOException("Replay column models changed");
        return new SkyInput(new dev.jayms.render.WorldSkyVisibility.Input(List.copyOf(chunks),List.copyOf(edits),capturedModels),revision,modelRevision,leaves);
    }

}
