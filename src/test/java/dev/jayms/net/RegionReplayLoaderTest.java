package dev.jayms.net;

import java.io.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RegionReplayLoaderTest {
    static List<Protocol.Edit> fragmented(int x) {
        var leaves=new ArrayList<Protocol.Edit>();
        for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int ix=0;ix<16;ix++)
            leaves.add(new Protocol.Edit(x,180,-1,Blocks.piece(Blocks.LED,4),4,ix,y,z,(ix+16*z+256*y)*37));
        Collections.shuffle(leaves,new Random(60701));
        var rows=new ArrayList<Protocol.Edit>();rows.add(new Protocol.Edit(x,180,-1,0));rows.addAll(leaves);return rows;
    }
    private WorldVoxels.History replay(List<Protocol.Edit> rows) {
        var result=new WorldVoxels.History();for(var edit:rows)WorldVoxels.rememberValidated(result,edit);return result;
    }
    private void same(WorldVoxels.History expected,WorldVoxels.History actual) {
        assertEquals(new ArrayList<>(expected.entrySet()),new ArrayList<>(actual.entrySet()));
        assertEquals(expected.revision(),actual.revision());assertEquals(expected.size(),actual.size());
        for(var row:expected.entrySet())assertEquals(row.getValue(),actual.get(row.getKey()));
    }
    static byte[] encodeRows(List<Protocol.Edit> rows)throws Exception {
        var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);out.writeInt(rows.size());
        int offset=0;
        while(offset<rows.size()) {
            var first=rows.get(offset);var region=dev.jayms.ChunkPos.fromBlock(first.x(),first.y(),first.z());
            var bodyBytes=new ByteArrayOutputStream();var body=new DataOutputStream(bodyBytes);int count=0;
            while(offset<rows.size()&&count<1024) {
                var edit=rows.get(offset);
                if(!dev.jayms.ChunkPos.fromBlock(edit.x(),edit.y(),edit.z()).equals(region))break;
                edit.write(body);offset++;count++;
            }
            RegionReplay.section(out,region,count,bodyBytes);
        }
        return bytes.toByteArray();
    }
    @Test void exactArbitraryLeafOrderSurvivesCompactionSourceMutationAndSnapshots() {
        var rows=fragmented(-17);var actual=new WorldVoxels.History();
        var prefix=List.of(new Protocol.Edit(100,180,100,Blocks.STONE),new Protocol.Edit(-17,180,-1,Blocks.GLASS));
        for(var edit:prefix)WorldVoxels.rememberValidated(actual,edit);
        var before=WorldVoxels.History.copyOf(actual);var expected=replay(prefix);
        for(var edit:rows)WorldVoxels.rememberValidated(expected,edit);
        assertTrue(actual.rememberCanonicalReplay(rows,()->{}));same(expected,actual);
        rows.clear();same(expected,actual);assertEquals(new ArrayList<>(replay(prefix).entrySet()),new ArrayList<>(before.entrySet()));
        var old=WorldVoxels.History.copyOf(actual);
        WorldVoxels.rememberValidated(actual,new Protocol.Edit(-17,180,-1,0,2,0,0,0));
        WorldVoxels.rememberValidated(expected,new Protocol.Edit(-17,180,-1,0,2,0,0,0));
        same(expected,actual);assertNotEquals(actual,old);
    }
    @Test void validatedSectionsCrossBoundariesAndRecurringCellsKeepExactReplayBytes()throws Exception {
        var rows=new ArrayList<Protocol.Edit>();rows.add(new Protocol.Edit(100,180,100,Blocks.STONE));
        rows.addAll(fragmented(-17));rows.add(new Protocol.Edit(101,180,100,Blocks.GLASS));
        rows.addAll(fragmented(-17));rows.addAll(fragmented(32));
        var expected=replay(rows);var actual=new WorldVoxels.History();
        RegionReplay.read(new DataInputStream(new ByteArrayInputStream(encodeRows(rows))),actual);
        same(expected,actual);assertArrayEquals(RegionReplayTest.encode(expected),RegionReplayTest.encode(actual));
        var generic=new LinkedHashMap<String,Protocol.Edit>();
        RegionReplay.read(new DataInputStream(new ByteArrayInputStream(encodeRows(rows))),generic);
        assertEquals(new ArrayList<>(expected.entrySet()),new ArrayList<>(generic.entrySet()));
    }
    @Test void overlappingDuplicateRootAndNonclearLegacyRowsFallBackExactly() {
        var clear=new Protocol.Edit(0,180,-1,0);
        var parent=new Protocol.Edit(0,180,-1,Blocks.piece(Blocks.GLASS,1),1,0,0,0);
        var fine=new Protocol.Edit(0,180,-1,Blocks.piece(Blocks.LED,4),4,0,0,0,0x123456);
        for(var rows:List.of(List.of(clear,parent,fine),List.of(clear,fine,fine),
                List.of(new Protocol.Edit(0,180,-1,Blocks.STONE),fine),List.of(fine,parent),
                List.of(clear,new Protocol.Edit(0,180,-1,Blocks.GLASS)))) {
            var actual=new WorldVoxels.History();assertFalse(actual.rememberCanonicalReplay(rows,()->{}));assertTrue(actual.isEmpty());
            var loader=new RegionReplayLoader(actual,()->false);rows.forEach(loader::accept);loader.finish();same(replay(rows),actual);
        }
    }
    @Test void invalidInputStillRejectsWithoutInstallingTheInvalidGroup() {
        var actual=new WorldVoxels.History();var loader=new RegionReplayLoader(actual,()->false);
        assertThrows(IllegalArgumentException.class,()->loader.accept(new Protocol.Edit(0,180,-1,0,0,0,0,0,0x123456)));
        assertTrue(actual.isEmpty());
    }
    @Test void mixedDepthAndRedundantAirRowsKeepTheirExactKeysColoursAndOrder() {
        var clear=new Protocol.Edit(-17,180,-1,0);
        var rows=List.of(clear,new Protocol.Edit(-17,180,-1,Blocks.piece(Blocks.LED,4),4,15,15,15,0x123456),
                new Protocol.Edit(-17,180,-1,Blocks.piece(Blocks.STONE,1),1,0,0,0),
                new Protocol.Edit(-17,180,-1,0,2,2,0,0));
        var actual=new WorldVoxels.History();assertTrue(actual.rememberCanonicalReplay(rows,()->{}));same(replay(rows),actual);
    }
    @Test void realV10LocalGameLoadSaveAndRestartPreserveAllBytes(@org.junit.jupiter.api.io.TempDir java.nio.file.Path folder)throws Exception {
        var rows=fragmented(-17);var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);
        out.writeInt(10);out.writeLong(42);out.writeInt(Terrain.CURRENT_VERSION);
        new dev.jayms.net.model.ModelLibrary().writeAll(out);new Inventory().write(out);out.writeByte(20);
        out.write(encodeRows(rows));out.writeInt(0);
        var path=folder.resolve("world.dat");java.nio.file.Files.write(path,bytes.toByteArray());
        var loaded=new LocalGame(path,999);assertEquals(42,loaded.seed);
        assertEquals(new ArrayList<>(replay(rows).entrySet()),new ArrayList<>(loaded.edits.entrySet()));
        assertArrayEquals(bytes.toByteArray(),java.nio.file.Files.readAllBytes(path));
        loaded.save();assertArrayEquals(bytes.toByteArray(),java.nio.file.Files.readAllBytes(path));
        var restarted=new LocalGame(path,999);assertEquals(loaded.edits,restarted.edits);
        try(var files=java.nio.file.Files.list(folder)){assertTrue(files.noneMatch(p->p.getFileName().toString().startsWith(".replay-spool-")));}
    }
    @Test void adversarialLongSameCellInputStaysBoundedAndMatchesOrdinaryReplay()throws Exception {
        var rows=new ArrayList<Protocol.Edit>();rows.add(new Protocol.Edit(0,180,-1,0));
        for(int i=0;i<9000;i++)rows.add(new Protocol.Edit(0,180,-1,Blocks.piece(Blocks.LED,4),4,i%16,0,0,i));
        var actual=new WorldVoxels.History();var loader=new RegionReplayLoader(actual,()->false);
        var field=RegionReplayLoader.class.getDeclaredField("pending");field.setAccessible(true);
        for(var row:rows){loader.accept(row);assertTrue(((List<?>)field.get(loader)).size()<=4097);}
        loader.finish();same(replay(rows),actual);
    }
    @Test void cancellationDuringEligibilityNeverCommitsPartialGroupAndRetryWorks() {
        var rows=fragmented(-17);var actual=new WorldVoxels.History();
        var prior=new Protocol.Edit(-17,180,-1,Blocks.STONE);WorldVoxels.rememberValidated(actual,prior);
        var before=replay(List.of(prior));var armed=new AtomicBoolean();var checks=new AtomicInteger();
        var loader=new RegionReplayLoader(actual,()->armed.get()&&checks.incrementAndGet()==300);
        rows.forEach(loader::accept);armed.set(true);
        assertThrows(CancellationException.class,loader::finish);same(before,actual);
        armed.set(false);loader.finish();var expected=replay(List.of(prior));
        rows.forEach(e->WorldVoxels.rememberValidated(expected,e));same(expected,actual);
    }
    @Test void bothOverlapStrategiesMatchExistingCoverageAcrossMixedDepthsAndBoundaries() {
        var random=new Random(60713);
        for(int leaves:new int[]{1,2,8,31,32,33,64})for(int run=0;run<8;run++) {
            var candidates=new ArrayList<Protocol.Edit>();
            for(int i=0;i<leaves;i++) {
                int depth=2+random.nextInt(3),scale=1<<(depth-2);
                candidates.add(new Protocol.Edit(-17,180,-1,Blocks.piece(Blocks.LED,depth),depth,
                        (i&3)*scale+random.nextInt(scale),(i>>>4)*scale+random.nextInt(scale),
                        ((i>>>2)&3)*scale+random.nextInt(scale),random.nextInt(1<<24)));
            }
            Collections.shuffle(candidates,random);
            for(int variant=0;variant<2;variant++) {
                var rows=new ArrayList<Protocol.Edit>();rows.add(new Protocol.Edit(-17,180,-1,0));rows.addAll(candidates);
                if(variant==1)rows.add(candidates.get(0));
                boolean disjoint=true;
                for(int i=1;i<rows.size();i++)for(int j=1;j<i;j++)
                    if(WorldVoxels.covered(rows.get(i),rows.get(j))||WorldVoxels.covered(rows.get(j),rows.get(i)))disjoint=false;
                var actual=new WorldVoxels.History();assertEquals(disjoint,actual.rememberCanonicalReplay(rows,()->{}));
                if(disjoint)same(replay(rows),actual);else assertTrue(actual.isEmpty());
            }
        }
    }
}
