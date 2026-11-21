package dev.jayms.net;

import dev.jayms.net.model.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class LegacyOfflineReplayTest {
    @TempDir Path folder;
    static byte[] encode(int version,ModelLibrary models,List<Protocol.Edit> rows,int drops)throws Exception {
        var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);
        out.writeInt(version);out.writeLong(123);
        if(version>=7)out.writeInt(Terrain.CURRENT_VERSION);
        if(version>=9)models.writeAll(out);else if(version>=4)models.write(out);
        new Inventory().write(out);out.writeByte(17);out.writeInt(rows.size());
        for(var e:rows) {
            out.writeInt(e.x());out.writeInt(e.y());out.writeInt(e.z());out.writeInt(e.type());
            if(version>=5){out.writeByte(e.depth());out.writeByte(e.ix());out.writeByte(e.iy());out.writeByte(e.iz());}
            if(version>=6)out.writeInt(e.color());
        }
        out.writeInt(drops);return bytes.toByteArray();
    }
    static WorldVoxels.History expected(List<Protocol.Edit> rows) {
        var history=new WorldVoxels.History();for(var row:rows)WorldVoxels.rememberValidated(history,row);return history;
    }
    static void same(WorldVoxels.History expected,LocalGame loaded) {
        assertEquals(new ArrayList<>(expected.entrySet()),new ArrayList<>(loaded.edits.entrySet()));
        assertEquals(expected.revision(),((WorldVoxels.History)loaded.edits).revision());
        for(var row:expected.entrySet())assertEquals(row.getValue(),loaded.edits.get(row.getKey()));
        assertEquals(123,loaded.seed);assertEquals(17,loaded.health);
    }
    @Test void v8AndV9CanonicalLoadsSaveAndRestartWithExactBytesAndDependencies()throws Exception {
        for(int version:new int[]{8,9}) {
            var models=new ModelLibrary();var rows=new ArrayList<>(RegionReplayLoaderTest.fragmented(-17));
            rows.addAll(RegionReplayLoaderTest.fragmented(32));
            if(version==9) {
                var tree=new SparseVoxelOctree(32);tree.set(31,30,29,0xff123456);
                int id=models.stageStructureModel(new ModelDefinition("Legacy dependency",tree)).id();
                rows.add(new Protocol.Edit(64,180,0,id));
            }
            var bytes=encode(version,models,rows,0);var path=folder.resolve("v"+version+".dat");Files.write(path,bytes);
            var progress=new ArrayList<Integer>();var game=new LocalGame(path,0,()->false,progress::add);
            same(expected(rows),game);assertEquals(rows.size(),progress.get(progress.size()-1));
            for(int i=1;i<progress.size();i++)assertTrue(progress.get(i)>=progress.get(i-1));
            assertEquals(models.entries().stream().map(e->e.definition().fingerprint()).toList(),
                    game.models.entries().stream().map(e->e.definition().fingerprint()).toList());
            game.save();assertArrayEquals(bytes,Files.readAllBytes(path));same(expected(rows),new LocalGame(path,0));
            var snapshot=WorldVoxels.History.copyOf(game.edits);
            WorldVoxels.remember(game.edits,new Protocol.Edit(-17,180,-1,0));
            assertEquals(new ArrayList<>(expected(rows).entrySet()),new ArrayList<>(snapshot.entrySet()));
            assertArrayEquals(bytes,Files.readAllBytes(path));
        }
    }
    @Test void v3ThroughV7RetainDecodingAndUpgradeSemantics()throws Exception {
        for(int version=3;version<=7;version++) {
            var rows=new ArrayList<Protocol.Edit>();rows.add(new Protocol.Edit(-17,0,-1,Blocks.STONE));
            rows.add(new Protocol.Edit(-17,0,-1,0));rows.add(new Protocol.Edit(32,180,0,Blocks.GLASS));
            if(version>=5) {
                var fractional=new Protocol.Edit(-17,0,-1,Blocks.piece(Blocks.LED,4),4,15,15,15);
                rows.add(version>=6?fractional.withColor(0x123456):fractional);
            }
            var path=folder.resolve("v"+version+".dat");Files.write(path,encode(version,new ModelLibrary(),rows,0));
            var game=new LocalGame(path,0);same(expected(rows),game);
            assertEquals(version>=7?Terrain.CURRENT_VERSION:Terrain.LEGACY_VERSION,game.generatorVersion);
            game.save();var restart=new LocalGame(path,0);
            assertEquals(new ArrayList<>(game.edits.entrySet()),new ArrayList<>(restart.edits.entrySet()));
            assertEquals(game.generatorVersion,restart.generatorVersion);
        }
    }
    @Test void overlappingRecurringAndNonclearRunsRetainOrdinaryHistoryOrderAndRevisions()throws Exception {
        var rows=new ArrayList<>(RegionReplayLoaderTest.fragmented(-17));
        rows.add(new Protocol.Edit(64,180,0,Blocks.STONE));
        rows.add(new Protocol.Edit(-17,180,-1,0,2,0,0,0));
        rows.add(new Protocol.Edit(-17,180,-1,Blocks.piece(Blocks.GLASS,4),4,0,0,0));
        rows.add(new Protocol.Edit(-17,180,-1,Blocks.piece(Blocks.BRICKS,1),1,0,0,0));
        rows.add(new Protocol.Edit(-17,180,-1,Blocks.piece(Blocks.LED,4),4,0,0,0,0x123456));
        var path=folder.resolve("overlap.dat");Files.write(path,encode(8,new ModelLibrary(),rows,0));
        same(expected(rows),new LocalGame(path,0));
    }
    @Test void invalidRowsAndBadOrTruncatedTailNeverRewriteTheSource()throws Exception {
        var rows=RegionReplayLoaderTest.fragmented(-17);var good=encode(8,new ModelLibrary(),rows,0);
        var invalid=new ArrayList<>(rows);invalid.add(new Protocol.Edit(0,180,0,0,0,0,0,0,1));
        for(var bytes:List.of(Arrays.copyOf(good,good.length-1),encode(8,new ModelLibrary(),rows,-1),encode(8,new ModelLibrary(),invalid,0))) {
            var path=folder.resolve("invalid.dat");Files.write(path,bytes);
            assertThrows(IOException.class,()->new LocalGame(path,0));assertArrayEquals(bytes,Files.readAllBytes(path));
        }
    }
    @Test void cancellationAndProgressFailureKeepDenseLegacyFileUnchanged()throws Exception {
        var rows=RegionReplayLoaderTest.fragmented(-17);var path=folder.resolve("cancel.dat");
        var bytes=encode(8,new ModelLibrary(),rows,0);Files.write(path,bytes);var cancelled=new AtomicBoolean();
        assertThrows(CancellationException.class,()->new LocalGame(path,0,cancelled::get,n->{if(n>=128)cancelled.set(true);}));
        assertArrayEquals(bytes,Files.readAllBytes(path));
        var failure=new IllegalStateException("Synthetic legacy progress failure");
        assertSame(failure,assertThrows(IllegalStateException.class,()->new LocalGame(path,0,()->false,n->{if(n==rows.size())throw failure;})));
        assertArrayEquals(bytes,Files.readAllBytes(path));same(expected(rows),new LocalGame(path,0));
    }
}
