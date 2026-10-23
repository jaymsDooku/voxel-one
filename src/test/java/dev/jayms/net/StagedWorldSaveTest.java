package dev.jayms.net;

import java.io.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class StagedWorldSaveTest {
    @TempDir Path folder;
    long temporaryCount()throws IOException {try(var files=Files.list(folder)){return files.filter(p->p.getFileName().toString().startsWith(".structure-save-")).count();}}
    @Test void publishAppendsCurrentTailAndClosesExactlyOnce()throws Exception {
        var target=folder.resolve("world.dat");Files.write(target,new byte[]{9});
        try(var staged=StagedWorldSave.stage(target,out->out.writeInt(42))) {
            assertArrayEquals(new byte[]{9},Files.readAllBytes(target));assertEquals(1,temporaryCount());
            staged.publish(out->out.writeUTF("current synthetic state"));assertEquals(0,temporaryCount());
            try(var in=new DataInputStream(Files.newInputStream(target))){assertEquals(42,in.readInt());assertEquals("current synthetic state",in.readUTF());assertEquals(-1,in.read());}
            assertThrows(IOException.class,()->staged.publish(out->out.writeByte(7)));
        }
        assertEquals(0,temporaryCount());
    }
    @Test void abandonmentAndWriterFailuresPreserveTargetAndRemovePrivateTemporaryFiles()throws Exception {
        var target=folder.resolve("world.dat");byte[] old={1,2,3};Files.write(target,old);
        try(var staged=StagedWorldSave.stage(target,out->out.writeInt(42))){}assertEquals(0,temporaryCount());
        assertThrows(IOException.class,()->StagedWorldSave.stage(target,out->{out.writeInt(42);throw new IOException("Synthetic prefix failure");}));assertEquals(0,temporaryCount());
        try(var staged=StagedWorldSave.stage(target,out->out.writeInt(42))) {
            assertThrows(IOException.class,()->staged.publish(out->{out.writeInt(7);throw new IOException("Synthetic tail failure");}));
            assertEquals(0,temporaryCount());assertThrows(IOException.class,()->staged.publish(out->{}));
        }
        assertArrayEquals(old,Files.readAllBytes(target));
    }
    @Test void independentStagesDoNotCollideWithOrdinarySaveAndFailedRenameCleansUp()throws Exception {
        var target=folder.resolve("world.dat");var ordinary=folder.resolve("world.dat.tmp");Files.write(ordinary,new byte[]{8});
        try(var first=StagedWorldSave.stage(target,out->out.writeInt(1));var second=StagedWorldSave.stage(target,out->out.writeInt(2))) {
            assertEquals(2,temporaryCount());first.publish(out->{});second.publish(out->{});assertEquals(0,temporaryCount());
            assertArrayEquals(new byte[]{0,0,0,2},Files.readAllBytes(target));assertArrayEquals(new byte[]{8},Files.readAllBytes(ordinary));
        }
        var directory=folder.resolve("nonempty");Files.createDirectory(directory);Files.write(directory.resolve("keep"),new byte[]{3});
        try(var staged=StagedWorldSave.stage(directory,out->out.writeByte(7))){assertThrows(IOException.class,()->staged.publish(out->{}));}
        assertArrayEquals(new byte[]{3},Files.readAllBytes(directory.resolve("keep")));assertEquals(0,temporaryCount());
    }
}
