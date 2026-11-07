import dev.jayms.net.*;
import dev.jayms.net.model.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Actual synthetic offline save/reload, then per-cell reconstruction without flat re-expansion. */
public final class HistoryCellCursorCheck {
    static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static void edit(LocalGame game,WorldVoxels world,Protocol.Edit edit){world.apply(edit);WorldVoxels.remember(game.edits,edit);}
    public static void main(String[] args)throws Exception {
        Path parent=Path.of("target/structure-cell-cursor-tests");Files.createDirectories(parent);Path root=Files.createTempDirectory(parent,"synthetic-");int cells=0,batches=0,bytes=0;
        try {
            Path path=root.resolve("world.dat");var game=new LocalGame(path,126);var source=new WorldVoxels(new Terrain(126));
            for(int i=0;i<2051;i++)edit(game,source,new Protocol.Edit(i-1025,180,0,Blocks.STONE));
            edit(game,source,new Protocol.Edit(-1026,180,0,Blocks.piece(Blocks.GLASS,1),1,0,0,0));edit(game,source,new Protocol.Edit(-1026,180,0,Blocks.piece(Blocks.STONE,1),1,1,0,0));
            edit(game,source,new Protocol.Edit(-1028,Terrain.MIN_Y,0,0));
            var voxels=new SparseVoxelOctree(8);voxels.set(1,2,3,0xff123456);var component=game.models.stageStructureModel(new ModelDefinition("synthetic",voxels));edit(game,source,new Protocol.Edit(-1027,180,0,component.id()));
            game.save();byte[] original=Files.readAllBytes(path);var loaded=new LocalGame(path,0);var history=(WorldVoxels.History)loaded.edits;var cursor=history.cellCopyCursor();var snapshot=source.snapshot();var restored=new WorldVoxels(new Terrain(126));var seen=new HashSet<WorldVoxels.CellPosition>();
            var retained=cursor.nextBatch();try{retained.clear();throw new AssertionError("Mutable position batch");}catch(UnsupportedOperationException expected){}
            for(var batch=retained;!batch.isEmpty();batch=cursor.nextBatch()) {
                require(batch.size()<=1024,"Unbounded batch");batches++;
                for(var p:batch){require(seen.add(p),"Duplicate cell");var out=new ByteArrayOutputStream();new Protocol.CellState(p.x(),p.y(),p.z(),snapshot.cell(p.x(),p.y(),p.z())).write(new DataOutputStream(out));byte[] record=out.toByteArray();bytes+=record.length;var in=new DataInputStream(new ByteArrayInputStream(record));var decoded=Protocol.CellState.read(in);require(in.read()==-1,"Trailing cell");restored.replaceCell(decoded.x(),decoded.y(),decoded.z(),decoded.tree());cells++;}
            }
            require(cells==2054&&batches==3&&cursor.nextBatch().isEmpty(),"Missing indexed cells");for(var p:seen)require(snapshot.cell(p.x(),p.y(),p.z()).sameValues(restored.cell(p.x(),p.y(),p.z())),"Per-cell reconstruction changed geometry");
            require(loaded.models.get(component.id()).definition().fingerprint().equals(component.definition().fingerprint()),"Model definition lost");
            var revision=history.revision();var same=history.values().iterator().next();history.put(same.key(),same);require(history.revision()>revision,"Mutation stamp unchanged");try{cursor.nextBatch();throw new AssertionError("Exhausted cursor ignored mutation");}catch(ConcurrentModificationException expected){}
            source.apply(new Protocol.Edit(0,180,0,Blocks.GLASS));require(snapshot.cell(0,180,0).sameValues(restored.cell(0,180,0)),"Retained snapshot changed");require(retained.size()==1024&&Arrays.equals(original,Files.readAllBytes(path)),"Cursor changed source save");
        } finally {try(var paths=Files.walk(root)){for(var p:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}}
        System.out.printf("{\"status\":\"passed\",\"cells\":%d,\"batches\":%d,\"cell_wire_bytes\":%d,\"fractional_air_models\":true,\"revision_fence\":true,\"production_region_backend\":false,\"qualification\":false}%n",cells,batches,bytes);
    }
}
