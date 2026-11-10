package dev.jayms.net;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ReadbackFileChangeTest {
    @TempDir Path folder;
    static final RegionReplayWorker.Revision REV=new RegionReplayWorker.Revision(1,2,3);
    Path world()throws Exception {
        var path=folder.resolve("world.dat");var rows=new WorldVoxels.History();
        WorldVoxels.remember(rows,new Protocol.Edit(0,160,0,Blocks.STONE));
        try(var staged=StagedOfflineSave.stageRegions(path,42,Terrain.CURRENT_VERSION,new dev.jayms.net.model.ModelLibrary(),rows,()->false,n->{})) {
            staged.publish(new Inventory(),20,List.of(),0);
        }return path;
    }
    interface Change {void apply(Path path)throws IOException;}
    void rejects(Change change,String message)throws Exception {
        Path path=world(),spool=Files.createDirectory(folder.resolve("spool"));var changed=new AtomicBoolean();
        IOException error=assertThrows(IOException.class,()->LocalGame.openRegionalReadback(path,spool,REV,()->false,n->{
            if(n>0&&changed.compareAndSet(false,true))try{change.apply(path);}catch(IOException failure){throw new UncheckedIOException(failure);}
        }));
        assertTrue(changed.get());if(message!=null)assertEquals(message,error.getMessage());
        try(var files=Files.list(spool)){assertEquals(0,files.count());}
    }
    @Test void sameSizeInPlaceChangeRejectsAndCleansPrivateSpool()throws Exception {
        rejects(path->{
            var before=Files.getLastModifiedTime(path);
            try(var file=new RandomAccessFile(path.toFile(),"rw")){file.seek(4);file.writeLong(43);}
            Files.setLastModifiedTime(path,FileTime.fromMillis(before.toMillis()+2000));
        },"Saved world changed during regional readback");
    }
    @Test void atomicReplacementWithIdenticalBytesAndTimesRejects()throws Exception {
        rejects(path->{
            var oldTime=Files.getLastModifiedTime(path);var replacement=folder.resolve("replacement.dat");
            Files.write(replacement,Files.readAllBytes(path));Files.setLastModifiedTime(replacement,oldTime);
            Files.move(replacement,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        },"Saved world changed during regional readback");
    }
    @Test void deletedSourceRejectsAndCleansPrivateSpool()throws Exception {rejects(Files::delete,null);}
    @Test void nonRegularSourceRejectsBeforeSpooling()throws Exception {
        var spool=Files.createDirectory(folder.resolve("spool"));
        var error=assertThrows(IOException.class,()->LocalGame.openRegionalReadback(folder,spool,REV,()->false,n->fail()));
        assertEquals("Regional readback requires a regular file",error.getMessage());
        try(var files=Files.list(spool)){assertEquals(0,files.count());}
    }
    @Test void unchangedSourceAndPostOpenReplacementRetainDetachedSnapshot()throws Exception {
        var path=world();var before=Files.readAllBytes(path);var spool=Files.createDirectory(folder.resolve("spool"));
        var source=LocalGame.openRegionalReadback(path,spool,REV,()->false,n->{});
        try(var owned=source.spool()) {
            assertArrayEquals(before,Files.readAllBytes(path));Files.delete(path);
            assertEquals(1,owned.size());assertEquals(42,source.terrain().seed);
            assertFalse(source.metadata().transfer().replayComplete());
        }
        try(var files=Files.list(spool)){assertEquals(0,files.count());}
    }
}
