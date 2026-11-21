package dev.jayms.net;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class RegionalOfflineLoadTest {
    @TempDir Path folder;
    private byte[] body(byte[] tail)throws Exception {
        var bytes=new ByteArrayOutputStream();
        try(var out=new DataOutputStream(bytes)) {
            RegionReplay.write(out,RegionReplayTest.fixture(),()->false,n->{});out.write(tail);
        }
        return bytes.toByteArray();
    }
    private byte[] drops()throws Exception {
        var bytes=new ByteArrayOutputStream();
        try(var out=new DataOutputStream(bytes)) {
            out.writeInt(3);
            new ItemDrop(3,Blocks.STONE,2,1,2,3).write(out);
            new ItemDrop(7,Blocks.GLASS,1,4,5,6).write(out);
            new ItemDrop(3,Blocks.BRICKS,1,7,8,9).write(out);
        }
        return bytes.toByteArray();
    }
    private void noSpool()throws Exception {
        try(var files=Files.list(folder)){assertFalse(files.anyMatch(p->p.getFileName().toString().startsWith(".replay-spool-")));}
    }
    @Test void validatesTailBeforeFirstHistoryWriteAndPreservesReplayAndDropOrder()throws Exception {
        byte[] bytes=body(drops());var input=new ByteArrayInputStream(bytes);
        var target=new LinkedHashMap<String,Protocol.Edit>() {
            @Override public Protocol.Edit put(String key,Protocol.Edit value) {
                assertEquals(0,input.available(),"History expanded before source tail validation");
                return super.put(key,value);
            }
        };
        var progress=new ArrayList<Integer>();
        var loaded=LocalGame.readRegionalBody(folder,new DataInputStream(input),target,()->false,progress::add);
        assertEquals(new ArrayList<>(RegionReplayTest.fixture().values()),new ArrayList<>(target.values()));
        assertEquals(List.of(3,7),new ArrayList<>(loaded.drops().keySet()));
        assertEquals(Blocks.BRICKS,loaded.drops().get(3).type());assertEquals(7,loaded.nextDrop());
        assertThrows(UnsupportedOperationException.class,()->loaded.drops().clear());
        assertEquals(0,progress.get(0));assertEquals(1102,progress.get(progress.size()-1));
        for(int i=1;i<progress.size();i++)assertTrue(progress.get(i)>=progress.get(i-1));
        noSpool();
    }
    @Test void invalidTailNeverExpandsHistoryAndAlwaysDeletesSpool()throws Exception {
        var tail=drops();var trailing=Arrays.copyOf(tail,tail.length+1);trailing[tail.length]=1;
        var invalid=new ByteArrayOutputStream();new DataOutputStream(invalid).writeInt(100001);
        for(byte[] bad:List.of(new byte[0],new byte[]{0,0},invalid.toByteArray(),Arrays.copyOf(tail,tail.length-1),trailing)) {
            var target=new WorldVoxels.History();var progress=new ArrayList<Integer>();
            assertThrows(IOException.class,()->LocalGame.readRegionalBody(folder,
                    new DataInputStream(new ByteArrayInputStream(body(bad))),target,()->false,progress::add));
            assertTrue(target.isEmpty());assertEquals(1102,progress.get(progress.size()-1));noSpool();
        }
    }
    @Test void cancellationDuringStagingAndDuringPrivateDrainCleansUp()throws Exception {
        byte[] bytes=body(drops());var cancel=new AtomicBoolean();var target=new WorldVoxels.History();
        assertThrows(CancellationException.class,()->LocalGame.readRegionalBody(folder,
                new DataInputStream(new ByteArrayInputStream(bytes)),target,cancel::get,n->{if(n>=128)cancel.set(true);}));
        assertTrue(target.isEmpty());noSpool();cancel.set(false);
        var puts=new AtomicInteger();
        var privateTarget=new LinkedHashMap<String,Protocol.Edit>() {
            @Override public Protocol.Edit put(String key,Protocol.Edit value) {
                puts.incrementAndGet();cancel.set(true);return super.put(key,value);
            }
        };
        assertThrows(CancellationException.class,()->LocalGame.readRegionalBody(folder,
                new DataInputStream(new ByteArrayInputStream(bytes)),privateTarget,cancel::get,n->{}));
        assertEquals(1,puts.get());noSpool();
        // A drain prefix is private and must be discarded, never published/reused.
    }
    @Test void callbackFailureAndAlreadyCancelledDoNotLeakTemporaryFiles()throws Exception {
        byte[] bytes=body(drops());var target=new WorldVoxels.History();var failure=new IllegalStateException("Synthetic callback failure");
        for(int at:List.of(0,128,1102)) {
            assertSame(failure,assertThrows(IllegalStateException.class,()->LocalGame.readRegionalBody(folder,
                    new DataInputStream(new ByteArrayInputStream(bytes)),target,()->false,n->{if(n>=at)throw failure;})));
            assertTrue(target.isEmpty());noSpool();
        }
        assertThrows(CancellationException.class,()->LocalGame.readRegionalBody(folder,
                new DataInputStream(new ByteArrayInputStream(bytes)),target,()->true,n->fail()));noSpool();
    }
    @Test void rejectsNonemptyPrivateTargetBeforeReadingSource()throws Exception {
        var target=RegionReplayTest.fixture();
        var input=new DataInputStream(new InputStream(){public int read(){fail("Source read");return -1;}});
        assertThrows(IllegalArgumentException.class,()->LocalGame.readRegionalBody(folder,input,target,()->false,n->fail()));
        noSpool();
    }
}
