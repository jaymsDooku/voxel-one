package dev.jayms.net;

import dev.jayms.net.model.ModelLibrary;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class WorldSaveGenerationTest {
    @TempDir Path folder;
    @Test void sameGenerationConcurrentPublishersHaveExactlyOneWinner()throws Exception {
        var target=folder.resolve("world.dat");Files.writeString(target,"base");var expected=WorldSavePublication.capture(target,1024,()->false);
        var a=folder.resolve("a.tmp");var b=folder.resolve("b.tmp");Files.writeString(a,"writer-a");Files.writeString(b,"writer-b");
        var start=new CountDownLatch(1);var results=new ConcurrentLinkedQueue<String>();var failures=new ConcurrentLinkedQueue<Throwable>();
        var threads=new ArrayList<Thread>();
        for(var path:List.of(a,b)) {
            var thread=new Thread(()->{try{start.await();WorldSavePublication.replaceIfUnchanged(path,target,expected,()->false);results.add(path.getFileName().toString());}catch(IOException conflict){failures.add(conflict);}catch(Throwable unexpected){failures.add(unexpected);}});
            threads.add(thread);thread.start();
        }
        start.countDown();for(var thread:threads){thread.join(10000);assertFalse(thread.isAlive());}
        assertEquals(1,results.size());assertEquals(1,failures.size());assertInstanceOf(IOException.class,failures.peek());
        boolean wonA=results.peek().equals("a.tmp");assertEquals(wonA?"writer-a":"writer-b",Files.readString(target));
        assertFalse(Files.exists(wonA?a:b));assertEquals(wonA?"writer-b":"writer-a",Files.readString(wonA?b:a));
    }
    @Test void ordinaryPublicationInvalidatesEvenIdenticalBytesAndWrongTargetCannotUseToken()throws Exception {
        var target=folder.resolve("world.dat");Files.writeString(target,"same");var token=WorldSavePublication.capture(target,1024,()->false);
        var ordinary=folder.resolve("ordinary.tmp");Files.writeString(ordinary,"same");WorldSavePublication.replace(ordinary,target,true);
        var stale=folder.resolve("stale.tmp");Files.writeString(stale,"stale");
        assertThrows(IOException.class,()->WorldSavePublication.replaceIfUnchanged(stale,target,token,()->false));assertEquals("same",Files.readString(target));assertEquals("stale",Files.readString(stale));
        var current=WorldSavePublication.capture(target,1024,()->false);
        assertThrows(IOException.class,()->WorldSavePublication.replaceIfUnchanged(stale,folder.resolve("other.dat"),current,()->false));assertFalse(Files.exists(folder.resolve("other.dat")));
        WorldSavePublication.replaceIfUnchanged(stale,target,current,()->false);assertEquals("stale",Files.readString(target));
    }
    @Test void guardedStagedSaveRejectsStaleCommitCleansPrefixAndRetries()throws Exception {
        var path=folder.resolve("world.dat");var local=new LocalGame(path,42);local.save();var token=WorldSavePublication.capture(path,1024*1024,()->false);
        var rows=RegionReplayTest.fixture();
        try(var spool=RegionReplaySpool.stage(folder,StagedReplaySourceSaveTest.input(RegionReplayTest.encode(rows)),e->true,()->false)) {
            try(var stage=StagedOfflineSave.stageRegions(path,42,Terrain.CURRENT_VERSION,new ModelLibrary(),spool,e->true,()->false,p->{})) {
                local.health=19;local.save();var current=Files.readAllBytes(path);
                assertThrows(IOException.class,()->stage.publish(new Inventory(),17,List.of(),0,token,()->false));assertArrayEquals(current,Files.readAllBytes(path));
                assertThrows(IOException.class,()->stage.publish(new Inventory(),17,List.of(),0,token,()->false));
            }
            try(var files=Files.list(folder)){assertFalse(files.anyMatch(p->p.getFileName().toString().startsWith(".structure-offline-")));}
            var current=WorldSavePublication.capture(path,1024*1024,()->false);
            try(var stage=StagedOfflineSave.stageRegions(path,42,Terrain.CURRENT_VERSION,new ModelLibrary(),spool,e->true,()->false,p->{})) {
                stage.publish(new Inventory(),17,List.of(),0,current,()->false);
            }
            var loaded=new LocalGame(path,0);assertEquals(17,loaded.health);assertEquals(new ArrayList<>(rows.values()),new ArrayList<>(loaded.edits.values()));
        }
    }
    @Test void boundedCaptureAbsenceObservedChangesAndCancellationNeverPublish()throws Exception {
        var target=folder.resolve("world.dat");var source=folder.resolve("source.tmp");Files.write(source,new byte[131073]);
        var absent=WorldSavePublication.capture(target,0,()->false);WorldSavePublication.replaceIfUnchanged(source,target,absent,()->false);assertEquals(131073,Files.size(target));
        assertThrows(IOException.class,()->WorldSavePublication.capture(target,131072,()->false));
        assertThrows(IllegalArgumentException.class,()->WorldSavePublication.capture(target,-1,()->false));
        assertThrows(IllegalArgumentException.class,()->WorldSavePublication.capture(target,WorldSavePublication.MAX_GENERATION_BYTES+1,()->false));
        var calls=new AtomicInteger();assertThrows(CancellationException.class,()->WorldSavePublication.capture(target,200000,()->calls.incrementAndGet()>3));
        var expected=WorldSavePublication.capture(target,200000,()->false);Files.writeString(source,"replacement");
        assertThrows(CancellationException.class,()->WorldSavePublication.replaceIfUnchanged(source,target,expected,()->true));assertEquals(131073,Files.size(target));assertTrue(Files.exists(source));
        // A non-cooperating edit completed before the check is detected by bytes,
        // even with unchanged length and restored modification time.
        var modified=Files.getLastModifiedTime(target);var bytes=Files.readAllBytes(target);bytes[10]=1;Files.write(target,bytes);Files.setLastModifiedTime(target,modified);
        assertThrows(IOException.class,()->WorldSavePublication.replaceIfUnchanged(source,target,expected,()->false));assertArrayEquals(bytes,Files.readAllBytes(target));
        var missing=folder.resolve("missing.dat");var missingToken=WorldSavePublication.capture(missing,1024,()->false);Files.writeString(missing,"foreign");
        assertThrows(IOException.class,()->WorldSavePublication.replaceIfUnchanged(source,missing,missingToken,()->false));assertEquals("foreign",Files.readString(missing));
    }
}
