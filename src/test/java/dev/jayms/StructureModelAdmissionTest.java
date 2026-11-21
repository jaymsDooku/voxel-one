package dev.jayms;

import dev.jayms.net.model.*;
import java.io.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StructureModelAdmissionTest {
    private static SparseVoxelOctree tree(int size,boolean fragmented) {
        var tree=new SparseVoxelOctree(size);
        for(int y=0;y<size;y++)for(int z=0;z<size;z++)for(int x=0;x<size;x++)
            if(!fragmented||((x+y+z)&1)==0)tree.set(x,y,z,0xff000000|((x*7)<<16)|((y*7)<<8)|z*7);
        return tree;
    }
    private static byte[] bytes(ModelLibrary library)throws IOException {
        var buffer=new ByteArrayOutputStream();library.writeAll(new DataOutputStream(buffer));return buffer.toByteArray();
    }
    @Test void admittedDependenciesKeepIdsFingerprintsFrozenValuesAndWorldBytes()throws Exception {
        var original=new ModelLibrary();long revision=original.revision();byte[] initial=bytes(original);
        var staged=original.copy();var received=new ModelLibrary();
        for(int size:new int[]{8,16,32}) {
            var source=tree(size,false);var definition=new ModelDefinition("Part "+size,source);
            int expected=ModelMesher.mesh(source).quads();assertEquals(expected,ModelMesher.count(source));
            var entry=staged.stageStructureModel(definition);String fingerprint=definition.fingerprint();
            long stagedRevision=staged.revision();assertEquals(entry.id(),staged.stageStructureModel(definition).id());assertEquals(stagedRevision,staged.revision());
            received.acceptWorldEntry(entry);source.set(0,0,0,0xffabcdef);
            assertEquals(fingerprint,entry.definition().fingerprint());assertEquals(fingerprint,received.get(entry.id()).definition().fingerprint());
            assertThrows(IllegalStateException.class,()->entry.definition().voxels().set(0,0,0,0xff123456));
        }
        assertEquals(revision,original.revision());assertArrayEquals(initial,bytes(original));assertArrayEquals(bytes(staged),bytes(received));
        var restored=ModelLibrary.readAll(new DataInputStream(new ByteArrayInputStream(bytes(staged))));
        assertArrayEquals(bytes(staged),bytes(restored));
        for(var entry:staged.entries())assertEquals(entry.definition().fingerprint(),restored.get(entry.id()).definition().fingerprint());
    }
    @Test void tooManyFacesStillRejectBeforeAnyLibraryMutation()throws Exception {
        var definition=new ModelDefinition("Fragmented",tree(16,true));
        String expected=assertThrows(IOException.class,()->ModelMesher.mesh(definition.voxels())).getMessage();
        assertEquals(expected,assertThrows(IOException.class,()->ModelMesher.count(definition.voxels())).getMessage());
        var library=new ModelLibrary();byte[] before=bytes(library);long revision=library.revision();
        assertEquals(expected,assertThrows(IOException.class,()->library.stageStructureModel(definition)).getMessage());
        assertEquals(expected,assertThrows(IOException.class,()->library.acceptWorldEntry(new ModelLibrary.Entry(1024,"structure",definition))).getMessage());
        assertArrayEquals(before,bytes(library));assertEquals(revision,library.revision());assertNull(library.get(1024));
        var encoded=new ByteArrayOutputStream();var out=new DataOutputStream(encoded);
        out.writeInt(1);out.writeInt(1024);out.writeUTF("structure");definition.write(out);
        assertEquals(expected,assertThrows(IOException.class,()->ModelLibrary.readAll(new DataInputStream(new ByteArrayInputStream(encoded.toByteArray())))).getMessage());
        assertArrayEquals(before,bytes(library));
    }
}
