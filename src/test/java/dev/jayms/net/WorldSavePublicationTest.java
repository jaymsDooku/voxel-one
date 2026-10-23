package dev.jayms.net;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class WorldSavePublicationTest {
    @TempDir Path folder;
    @Test void unsupportedStructurePublicationNeverFallsBackOrChangesTheSave() throws Exception {
        Path source=folder.resolve("world.tmp"),target=folder.resolve("world.dat");
        Files.writeString(source,"staged");Files.writeString(target,"original");
        var unsupported=new AtomicMoveNotSupportedException(source.toString(),target.toString(),"synthetic unsupported atomic move");
        var calls=new ArrayList<List<CopyOption>>();
        assertSame(unsupported,assertThrows(AtomicMoveNotSupportedException.class,()->WorldSavePublication.replace(source,target,true,(a,b,options)->{
            calls.add(List.of(options));throw unsupported;
        })));
        assertEquals(List.of(List.of(StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)),calls);
        assertEquals("original",Files.readString(target));assertEquals("staged",Files.readString(source));
    }
    @Test void ordinarySavesRetainCompatibilityFallback() throws Exception {
        Path source=folder.resolve("world.tmp"),target=folder.resolve("world.dat");
        Files.writeString(source,"staged");Files.writeString(target,"original");var calls=new ArrayList<List<CopyOption>>();
        WorldSavePublication.replace(source,target,false,(a,b,options)->{
            calls.add(List.of(options));
            if(calls.size()==1)throw new AtomicMoveNotSupportedException(a.toString(),b.toString(),"synthetic");
            Files.move(a,b,options);
        });
        assertEquals(List.of(List.of(StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING),List.of(StandardCopyOption.REPLACE_EXISTING)),calls);
        assertEquals("staged",Files.readString(target));assertFalse(Files.exists(source));
    }
    @Test void otherFailuresNeverRetryAndPreserveOriginalFiles() throws Exception {
        for(boolean strict:new boolean[]{false,true}) {
            Path source=folder.resolve("world-"+strict+".tmp"),target=folder.resolve("world-"+strict+".dat");
            Files.writeString(source,"staged");Files.writeString(target,"original");var failure=new IOException("synthetic write denied");var calls=new int[1];
            assertSame(failure,assertThrows(IOException.class,()->WorldSavePublication.replace(source,target,strict,(a,b,options)->{calls[0]++;throw failure;})));
            assertEquals(1,calls[0]);assertEquals("original",Files.readString(target));assertEquals("staged",Files.readString(source));
        }
    }
    @Test void realAtomicReplacementPublishesCompleteFile() throws Exception {
        Path source=folder.resolve("world.tmp"),target=folder.resolve("world.dat");
        byte[] bytes=new byte[131073];new Random(20261023).nextBytes(bytes);
        Files.write(source,bytes);Files.writeString(target,"original");WorldSavePublication.replace(source,target,true);
        assertArrayEquals(bytes,Files.readAllBytes(target));assertFalse(Files.exists(source));
    }
}
