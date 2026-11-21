package dev.jayms.net;

import dev.jayms.ChunkPos;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class RegionReplayBucketsTest {
    @TempDir Path folder;
    RegionReplayCellsTest fixture(){var test=new RegionReplayCellsTest();test.folder=folder;return test;}
    List<Protocol.Edit> rows() {
        var rows=new ArrayList<Protocol.Edit>();for(int cycle=0;cycle<2;cycle++)for(int i=0;i<96;i++)rows.add(new Protocol.Edit(i*16,160,0,cycle==0?Blocks.STONE:Blocks.GLASS));return rows;
    }
    static ChunkPos region(Protocol.Edit e){return ChunkPos.fromBlock(e.x(),e.y(),e.z());}
    List<Protocol.Edit> read(RegionReplaySpool source,ChunkPos region,int max)throws IOException {
        var result=new ArrayList<Protocol.Edit>();source.visitIndexedRegion(region,max,e->true,()->false,(p,rs)->{assertEquals(region,p);result.addAll(rs);});return result;
    }
    Path bucket(ChunkPos region)throws IOException{return fixture().file(".replay-bucket-"+RegionReplaySpool.bucket(region)+"-");}
    @Test void indexedRegionsPreserveRecurringOrderAndCopiedStorageIsBounded()throws Exception {
        var rows=rows();try(var source=fixture().spool(rows)) {
            for(int i=0;i<96;i++) {
                var region=region(rows.get(i));assertEquals(rows.stream().filter(e->region(e).equals(region)).toList(),read(source,region,2));
            }
            assertEquals(List.of(),read(source,new ChunkPos(-100,10,0),0));
            try(var paths=Files.list(folder)) {
                var buckets=paths.filter(p->p.getFileName().toString().startsWith(".replay-bucket-")).toList();assertTrue(buckets.size()<=32);
                long size=0;for(var p:buckets)size+=Files.size(p);assertEquals(Files.size(fixture().file(".replay-index-")),size);
            }
        }
        try(var paths=Files.list(folder)){assertEquals(0,paths.count());}
    }
    @Test void selectedBucketIntegrityPrecedesPolicyAndUnselectedCorruptionIsDeferred()throws Exception {
        var rows=rows();var a=region(rows.get(0));var b=rows.stream().map(RegionReplayBucketsTest::region).filter(p->RegionReplaySpool.bucket(p)!=RegionReplaySpool.bucket(a)).findFirst().orElseThrow();
        try(var source=fixture().spool(rows)) {
            var expected=read(source,a,2);var other=bucket(b);var good=Files.readAllBytes(other);var bad=good.clone();bad[bad.length-1]^=1;Files.write(other,bad);
            assertEquals(expected,read(source,a,2));var policy=new AtomicInteger();
            assertThrows(IOException.class,()->source.visitIndexedRegion(b,2,e->{policy.incrementAndGet();return true;},()->false,(p,rs)->fail()));assertEquals(0,policy.get());
            Files.write(other,good);assertEquals(2,read(source,b,2).size());
            // The copied metadata snapshot is independent of later original-index changes.
            var original=fixture().file(".replay-index-");Files.write(original,new byte[(int)Files.size(original)]);
            assertEquals(expected,read(source,a,2));assertThrows(IOException.class,()->source.visitRegion(a,e->true,()->false,(p,rs)->fail()));
        }
    }
    @Test void preparationCorruptionAndCancellationCleanPrefixesAndAllowRetry()throws Exception {
        var rows=rows();try(var source=fixture().spool(rows)) {
            var original=fixture().file(".replay-index-");var good=Files.readAllBytes(original);var bad=good.clone();bad[bad.length-1]^=1;Files.write(original,bad);
            var policy=new AtomicInteger();assertThrows(IOException.class,()->source.visitIndexedRegion(region(rows.get(0)),2,e->{policy.incrementAndGet();return true;},()->false,(p,rs)->fail()));assertEquals(0,policy.get());
            try(var paths=Files.list(folder)){assertFalse(paths.anyMatch(p->p.getFileName().toString().startsWith(".replay-bucket-")));}
            Files.write(original,good);var checks=new AtomicInteger();
            assertThrows(CancellationException.class,()->source.visitIndexedRegion(region(rows.get(0)),2,e->true,()->checks.incrementAndGet()>10,(p,rs)->fail()));
            try(var paths=Files.list(folder)){assertFalse(paths.anyMatch(p->p.getFileName().toString().startsWith(".replay-bucket-")));}
            assertEquals(2,read(source,region(rows.get(0)),2).size());
            var canceled=new AtomicBoolean();assertThrows(CancellationException.class,()->source.visitIndexedRegion(region(rows.get(0)),2,e->true,canceled::get,(p,rs)->canceled.set(true)));
            assertEquals(2,read(source,region(rows.get(0)),2).size());
        }
    }
    @Test void largeMatchingDirectoryFallsBackWithoutWeakeningRowPreflight()throws Exception {
        var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);out.writeInt(4097);var region=new ChunkPos(0,10,0);
        for(int i=0;i<4097;i++){var body=new ByteArrayOutputStream();new Protocol.Edit(i%16,160,0,Blocks.STONE).write(new DataOutputStream(body));RegionReplay.writeSection(out,region,1,body);}
        try(var source=RegionReplaySpool.stage(folder,new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())),e->true,()->false)) {
            var policy=new AtomicInteger();assertThrows(IOException.class,()->source.visitIndexedRegion(region,4096,e->{policy.incrementAndGet();return true;},()->false,(p,rs)->fail()));assertEquals(0,policy.get());
            var actual=read(source,region,4097);assertEquals(4097,actual.size());for(int i=0;i<4097;i++)assertEquals(i%16,actual.get(i).x());
        }
    }
    @Test void failedBucketDeletionRetainsOwnershipAndOwnerThreadRules()throws Exception {
        var rows=rows();var source=fixture().spool(rows);var region=region(rows.get(0));read(source,region,2);
        var error=new AtomicReference<Throwable>();var thread=new Thread(()->{try{read(source,region,2);}catch(Throwable failure){error.set(failure);}});thread.start();thread.join(5000);assertFalse(thread.isAlive());assertInstanceOf(IOException.class,error.get());
        var path=bucket(region);Files.delete(path);Files.createDirectory(path);var child=Files.writeString(path.resolve("synthetic-blocker"),"synthetic");
        try{assertThrows(IOException.class,source::close);assertTrue(Files.exists(child));}
        finally{Files.deleteIfExists(child);source.close();}
        try(var paths=Files.list(folder)){assertEquals(0,paths.count());}
    }
}
