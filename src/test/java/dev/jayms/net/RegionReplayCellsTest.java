package dev.jayms.net;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class RegionReplayCellsTest {
    @TempDir Path folder;
    static final dev.jayms.ChunkPos REGION=RegionReplayWindowTest.REGION;
    RegionReplaySpool spool(List<Protocol.Edit> rows)throws Exception {
        return RegionReplaySpool.stage(folder,new DataInputStream(new ByteArrayInputStream(RegionReplayLoaderTest.encodeRows(rows))),e->true,()->false);
    }
    Path file(String prefix)throws IOException {
        try(var files=Files.list(folder)){return files.filter(p->p.getFileName().toString().startsWith(prefix)).findFirst().orElseThrow();}
    }
    void noCellFiles()throws IOException {
        try(var files=Files.list(folder)){assertTrue(files.noneMatch(p->p.getFileName().toString().startsWith(".replay-cells-")));}
    }
    @Test void allWindowsRestoreFilteredSourceOrderAndGeometryAfterSourceSpoolCloses()throws Exception {
        var rows=new ArrayList<>(RegionReplayWindowTest.fixture());
        rows.add(1,new Protocol.Edit(32,180,32,Blocks.STONE));
        rows.add(4097,new Protocol.Edit(32,180,32,0));
        RegionReplayCells cells;var policy=new AtomicInteger();
        try(var spool=spool(rows)) {
            cells=RegionReplayCells.build(folder,spool,REGION,8192,e->{policy.incrementAndGet();return true;},()->false);
            assertEquals(8192,policy.get());assertEquals(8192,cells.size());assertEquals(8192L*28,cells.encodedBytes());
        }
        var expectedWorld=new WorldVoxels(new Terrain(42));
        for(var edit:rows)if(dev.jayms.ChunkPos.fromBlock(edit.x(),edit.y(),edit.z()).equals(REGION))expectedWorld.apply(edit);
        RegionReplayPage retained=null;
        try(cells) {
            for(int first=0;first<4096;first+=64) {
                var range=new RegionReplayPage.CellRange(first,first+64);
                var expected=rows.stream().filter(e->dev.jayms.ChunkPos.fromBlock(e.x(),e.y(),e.z()).equals(REGION)&&range.contains(e)).toList();
                var actual=cells.readWindow(range,128,e->true,()->false);
                assertEquals(expected,actual);assertThrows(UnsupportedOperationException.class,actual::clear);
                var page=RegionReplayPage.loadIndexed(cells,range,new Terrain(42),128,e->true,()->false);retained=page;
                assertEquals(range,page.range());assertEquals(REGION,page.region());assertEquals(64,page.cells().size());
                var expectedHistory=new LinkedHashMap<String,Protocol.Edit>();expected.forEach(e->WorldVoxels.rememberValidated(expectedHistory,e));
                assertEquals(new ArrayList<>(expectedHistory.entrySet()),new ArrayList<>(page.history().entrySet()));
                for(var entry:page.cells().entrySet()) {
                    var p=entry.getKey();assertTrue(expectedWorld.cell(p.x(),p.y(),p.z()).sameValues(entry.getValue().tree()));
                }
            }
        }
        var entry=retained.cells().entrySet().iterator().next();var p=entry.getKey();var original=entry.getValue().tree().copy();
        var world=new WorldVoxels(new Terrain(42));world.installCellImage(p.x(),p.y(),p.z(),entry.getValue());world.apply(new Protocol.Edit(p.x(),p.y(),p.z(),0));
        assertTrue(original.sameValues(entry.getValue().tree()));noCellFiles();
        try(var files=Files.list(folder)){assertEquals(0,files.count());}
    }
    @Test void metadataAdmissionPrecedesPayloadIoAndBuildChecksExcludedModels()throws Exception {
        var rows=List.of(RegionReplayWindowTest.whole(0,Blocks.STONE),RegionReplayWindowTest.whole(4095,1031));
        var range=new RegionReplayPage.CellRange(0,64);var calls=new AtomicInteger();
        try(var spool=spool(rows)) {
            assertThrows(IOException.class,()->RegionReplayCells.build(folder,spool,REGION,1,e->{calls.incrementAndGet();return true;},()->false));
            assertEquals(0,calls.get());noCellFiles();
            assertThrows(IOException.class,()->RegionReplayCells.build(folder,spool,REGION,2,e->!Blocks.structureModel(e.type()),()->false));noCellFiles();
            try(var cells=RegionReplayCells.build(folder,spool,REGION,2,e->true,()->false)) {
                var path=file(".replay-cells-");var aside=folder.resolve("held");Files.move(path,aside);
                try {
                    var failure=assertThrows(IOException.class,()->cells.readWindow(range,0,e->{calls.incrementAndGet();return true;},()->false));
                    assertTrue(failure.getMessage().contains("selected row budget"));assertEquals(0,calls.get());
                }finally{Files.move(aside,path);}
                assertEquals(List.of(rows.get(0)),cells.readWindow(range,1,e->!Blocks.structureModel(e.type()),()->false));
                assertThrows(IllegalArgumentException.class,()->cells.readWindow(new RegionReplayPage.CellRange(0,65),1,e->true,()->false));
                assertThrows(IllegalArgumentException.class,()->cells.readWindow(range,4098,e->true,()->false));
                assertThrows(IllegalArgumentException.class,()->cells.readWindow(range,-1,e->true,()->false));
            }
        }
    }
    @Test void selectedCrcsRejectBeforePolicyAndOtherCopiedCellCorruptionIsDeferred()throws Exception {
        var rows=List.of(RegionReplayWindowTest.whole(0,Blocks.STONE),RegionReplayWindowTest.whole(4095,Blocks.GLASS));
        var first=new RegionReplayPage.CellRange(0,64);var last=new RegionReplayPage.CellRange(4032,4096);
        try(var spool=spool(rows);var cells=RegionReplayCells.build(folder,spool,REGION,2,e->true,()->false)) {
            var path=file(".replay-cells-");var original=Files.readAllBytes(path);var changed=original.clone();changed[changed.length-1]^=1;Files.write(path,changed);
            assertEquals(List.of(rows.get(0)),cells.readWindow(first,1,e->true,()->false));var calls=new AtomicInteger();
            assertThrows(IOException.class,()->cells.readWindow(last,1,e->{calls.incrementAndGet();return true;},()->false));assertEquals(0,calls.get());
            // The original spool is independent of the copied index file.
            var sourceRows=new ArrayList<Protocol.Edit>();spool.visitRegion(REGION,e->true,()->false,(p,s)->sourceRows.addAll(s));assertEquals(rows,sourceRows);
            for(int size:new int[]{original.length-1,original.length+1}) {
                Files.write(path,Arrays.copyOf(original,size));assertThrows(IOException.class,()->cells.readWindow(first,1,e->true,()->false));
            }
            Files.write(path,original);assertEquals(List.of(rows.get(1)),cells.readWindow(last,1,e->true,()->false));
        }
    }
    @Test void futureSourceFrameCorruptionDuringCopyDeletesPrivatePrefixAndAllowsRetry()throws Exception {
        var rows=new ArrayList<Protocol.Edit>();rows.addAll(Collections.nCopies(1024,RegionReplayWindowTest.whole(0,Blocks.STONE)));
        rows.addAll(Collections.nCopies(1024,RegionReplayWindowTest.whole(4095,Blocks.GLASS)));
        try(var spool=spool(rows)) {
            var path=file(".replay-spool-");var original=Files.readAllBytes(path);var bad=original.clone();bad[4+20+1024*24+20+23]^=1;
            var calls=new AtomicInteger();
            assertThrows(IOException.class,()->RegionReplayCells.build(folder,spool,REGION,2048,e->{
                if(calls.incrementAndGet()==1)try{Files.write(path,bad);}catch(IOException failure){throw new UncheckedIOException(failure);}return true;
            },()->false));noCellFiles();Files.write(path,original);
            try(var cells=RegionReplayCells.build(folder,spool,REGION,2048,e->true,()->false)){assertEquals(2048,cells.size());}
        }
        noCellFiles();
    }
    @Test void cancelledScatterAndReadFailuresKeepOwnershipRetryAndCleanup()throws Exception {
        var rows=new ArrayList<Protocol.Edit>();for(int i=0;i<16;i++)rows.add(RegionReplayWindowTest.whole(i,Blocks.STONE));
        Files.writeString(folder.resolve("keep"),"unchanged");
        try(var spool=spool(rows)) {
            assertThrows(CancellationException.class,()->RegionReplayCells.build(folder,spool,REGION,16,e->true,()->{
                try(var files=Files.list(folder)) {
                    var p=files.filter(f->f.getFileName().toString().startsWith(".replay-cells-")).findFirst();
                    return p.isPresent()&&Files.size(p.get())>0;
                }catch(IOException failure){throw new UncheckedIOException(failure);}
            }));noCellFiles();
            try(var cells=RegionReplayCells.build(folder,spool,REGION,16,e->true,()->false)) {
                var range=new RegionReplayPage.CellRange(0,64);var total=new AtomicInteger();
                cells.readWindow(range,16,e->true,()->{total.incrementAndGet();return false;});
                for(int cutoff:List.of(1,total.get()/2,total.get()-1)) {
                    var seen=new AtomicInteger();assertThrows(CancellationException.class,()->cells.readWindow(range,16,e->true,()->seen.incrementAndGet()>=cutoff));
                    assertEquals(rows,cells.readWindow(range,16,e->true,()->false));
                }
                cells.readWindow(range,16,e->{assertThrows(IOException.class,cells::close);assertThrows(IOException.class,()->cells.readWindow(range,16,x->true,()->false));return true;},()->false);
                var executor=Executors.newSingleThreadExecutor();
                try{executor.submit(()->{assertThrows(IOException.class,()->cells.readWindow(range,16,e->true,()->false));assertThrows(IOException.class,cells::close);}).get(5,TimeUnit.SECONDS);}
                finally{executor.shutdownNow();assertTrue(executor.awaitTermination(5,TimeUnit.SECONDS));}
                assertThrows(IllegalStateException.class,()->cells.readWindow(range,16,e->{throw new IllegalStateException("Synthetic policy failure");},()->false));
                assertEquals(rows,cells.readWindow(range,16,e->true,()->false));
            }
        }
        noCellFiles();assertEquals("unchanged",Files.readString(folder.resolve("keep")));
    }
    @Test void complete4097RowCellRoundTripsAnd4098SourceRowsRejectBeforeReadPolicy()throws Exception {
        var rows=RegionReplayLoaderTest.fragmented(-17);var region=dev.jayms.ChunkPos.fromBlock(-17,180,-1);
        int key=15|(15<<4)|(4<<8);var range=new RegionReplayPage.CellRange(key,key+1);
        try(var spool=spool(rows);var cells=RegionReplayCells.build(folder,spool,region,4097,e->true,()->false)) {
            assertEquals(rows,cells.readWindow(range,4097,e->true,()->false));assertEquals(4097L*28,cells.encodedBytes());
            var actual=RegionReplayPage.loadIndexed(cells,range,new Terrain(42),4097,e->true,()->false);
            var expected=RegionReplayPage.loadWindow(spool,region,range,new Terrain(42),4097,4097,e->true,()->false);
            assertEquals(new ArrayList<>(expected.history().entrySet()),new ArrayList<>(actual.history().entrySet()));
            assertTrue(expected.cells().values().iterator().next().tree().sameValues(actual.cells().values().iterator().next().tree()));
        }
        rows.add(rows.get(1));
        try(var spool=spool(rows);var cells=RegionReplayCells.build(folder,spool,region,4098,e->true,()->false)) {
            var calls=new AtomicInteger();assertThrows(IOException.class,()->cells.readWindow(range,4097,e->{calls.incrementAndGet();return true;},()->false));assertEquals(0,calls.get());
        }
    }
    @Test void emptyRegionClosedHandlesAndInterruptedReadsKeepFences()throws Exception {
        RegionReplayCells cells;
        try(var spool=spool(List.of())) {cells=RegionReplayCells.build(folder,spool,REGION,0,e->{fail();return false;},()->false);}
        var range=new RegionReplayPage.CellRange(0,64);
        try(cells) {
            assertEquals(0,cells.size());assertEquals(0,cells.encodedBytes());assertTrue(cells.readWindow(range,0,e->{fail();return false;},()->false).isEmpty());
            var page=RegionReplayPage.loadIndexed(cells,range,new Terrain(42),0,e->true,()->false);assertEquals(range,page.range());assertTrue(page.cells().isEmpty());
            try{Thread.currentThread().interrupt();assertThrows(CancellationException.class,()->cells.readWindow(range,0,e->true,()->false));}finally{Thread.interrupted();}
        }
        cells.close();assertThrows(IOException.class,()->cells.readWindow(range,0,e->true,()->false));noCellFiles();
    }
}
