package dev.jayms.net;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class LocalGameSaveIsolationTest {
    @TempDir Path folder;
    static Map<String,Protocol.Edit> one(int x){return Map.of("fixture",new Protocol.Edit(x,160,0,Blocks.STONE));}
    static Map<String,Protocol.Edit> blocked(Map<String,Protocol.Edit> rows,CountDownLatch entered,CountDownLatch release,boolean fail) {
        return new AbstractMap<>() {
            public Set<Entry<String,Protocol.Edit>> entrySet(){return rows.entrySet();}
            @Override public Collection<Protocol.Edit> values(){return new AbstractCollection<>() {
                public int size(){return rows.size();}
                public Iterator<Protocol.Edit> iterator(){var source=rows.values().iterator();return new Iterator<>() {
                    public boolean hasNext(){return source.hasNext();}
                    public Protocol.Edit next(){
                        entered.countDown();
                        try{if(!release.await(10,TimeUnit.SECONDS))throw new AssertionError("Synthetic writer gate timed out");}
                        catch(InterruptedException error){Thread.currentThread().interrupt();throw new AssertionError(error);}
                        if(fail)throw new UncheckedIOException(new IOException("Synthetic history serialization failure"));
                        return source.next();
                    }
                };}
            };}
        };
    }
    long staged()throws IOException{try(var paths=Files.list(folder)){return paths.filter(p->p.getFileName().toString().startsWith(".world-save-")).count();}}
    @Test void failedOverlappingWriterCannotRewritePublishedSiblingOrLegacyScratch()throws Exception {
        Path target=folder.resolve("world.dat"),legacy=folder.resolve("world.dat.tmp");Files.writeString(legacy,"Unowned legacy scratch");
        var first=new LocalGame(target,42);first.health=1;var entered=new CountDownLatch(1);var release=new CountDownLatch(1);first.edits=blocked(one(0),entered,release,true);
        var failure=new AtomicReference<Throwable>();var thread=new Thread(()->{try{first.save();}catch(Throwable error){failure.set(error);}});
        thread.start();
        try {
            assertTrue(entered.await(10,TimeUnit.SECONDS));assertEquals(1,staged());
            var second=new LocalGame(target,42);second.health=17;second.edits=one(1);second.save();byte[] published=Files.readAllBytes(target);
            assertEquals(17,new LocalGame(target,0).health);assertEquals(1,staged());
            release.countDown();thread.join(10000);assertFalse(thread.isAlive());assertInstanceOf(UncheckedIOException.class,failure.get());
            assertArrayEquals(published,Files.readAllBytes(target));assertEquals(17,new LocalGame(target,0).health);assertEquals(0,staged());assertEquals("Unowned legacy scratch",Files.readString(legacy));
        }finally{release.countDown();thread.join(10000);}
    }
    @Test void successfulOverlappingAttemptsPublishOnlyTheirOwnCompleteFile()throws Exception {
        Path target=folder.resolve("world.dat");var first=new LocalGame(target,42);first.health=3;
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);first.edits=blocked(one(0),entered,release,false);
        var failure=new AtomicReference<Throwable>();var thread=new Thread(()->{try{first.save();}catch(Throwable error){failure.set(error);}});thread.start();
        try {
            assertTrue(entered.await(10,TimeUnit.SECONDS));var second=new LocalGame(target,42);second.health=19;second.edits=one(1);second.save();
            var intermediate=new LocalGame(target,0);assertEquals(19,intermediate.health);assertEquals(List.copyOf(one(1).values()),List.copyOf(intermediate.edits.values()));
            release.countDown();thread.join(10000);assertFalse(thread.isAlive());assertNull(failure.get());
            var last=new LocalGame(target,0);assertEquals(3,last.health);assertEquals(List.copyOf(one(0).values()),List.copyOf(last.edits.values()));assertEquals(0,staged());
        }finally{release.countDown();thread.join(10000);}
    }
    @Test void failedPublicationCleansOnlyItsOwnAttemptAndPreservesTargetContents()throws Exception {
        Path target=folder.resolve("world.dat"),legacy=folder.resolve("world.dat.tmp");var local=new LocalGame(target,42);local.edits=one(0);
        Files.createDirectory(target);Files.writeString(target.resolve("keep"),"Existing target directory");Files.writeString(legacy,"Unowned scratch");
        assertThrows(IOException.class,local::save);assertEquals("Existing target directory",Files.readString(target.resolve("keep")));assertEquals("Unowned scratch",Files.readString(legacy));assertEquals(0,staged());
    }
}
