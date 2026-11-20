package dev.jayms.net.model;

import java.io.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ModelLibraryBindingTest {
    static ModelDefinition definition(String name,int color) {
        var tree=new SparseVoxelOctree(8);tree.set(1,2,3,color);return new ModelDefinition(name,tree);
    }
    static byte[] bytes(ModelLibrary models)throws IOException {
        var out=new ByteArrayOutputStream();models.writeAll(new DataOutputStream(out));return out.toByteArray();
    }
    static ModelLibrary read(ModelLibrary models)throws IOException{return ModelLibrary.readAll(new DataInputStream(new ByteArrayInputStream(bytes(models))));}
    @Test public void equalBindingsSurviveCopySerializationAndDifferentRevisionCounters()throws Exception {
        var live=new ModelLibrary();var staged=live.copy();staged.stageStructureModel(definition("Red",0xffed593c));staged.stageStructureModel(definition("Blue",0xff1234ab));
        live.replaceWith(staged);assertNotEquals(live.revision(),staged.revision());assertTrue(live.sameBindings(staged));assertTrue(staged.sameBindings(live));
        var parsed=read(live);assertNotEquals(live.revision(),parsed.revision());assertTrue(live.sameBindings(parsed));assertTrue(parsed.sameBindings(live));
        assertArrayEquals(bytes(staged),bytes(live));assertArrayEquals(bytes(live),bytes(parsed));assertTrue(live.copy().sameBindings(parsed));
        var rebased=live.copyForSource(parsed);assertEquals(parsed.revision(),rebased.revision());assertTrue(rebased.sameBindings(live));
        for(var entry:live.entries())assertSame(entry.definition(),rebased.get(entry.id()).definition());
        assertNotEquals(live.revision(),rebased.revision());
    }
    @Test public void additionsAreAllowedOnlyForPreservationAndCopiesStayIndependent()throws Exception {
        var baseline=new ModelLibrary();baseline.accept(new ModelLibrary.Entry(9,"author",definition("Original",0xff123456)));var current=baseline.copy();
        current.stageStructureModel(definition("Later",0xffabcdef));assertTrue(current.preservesBindings(baseline));assertFalse(baseline.preservesBindings(current));assertFalse(current.sameBindings(baseline));
        assertThrows(IllegalArgumentException.class,()->current.copyForSource(baseline));
        var rollback=current.copy();current.replaceWith(baseline);assertTrue(current.sameBindings(baseline));assertFalse(current.sameBindings(rollback));
        current.replaceWith(rollback);assertTrue(current.sameBindings(rollback));assertTrue(current.preservesBindings(baseline));
    }
    @Test public void missingReboundRenamedAndReauthoredBindingsReject()throws Exception {
        var baseline=new ModelLibrary();baseline.accept(new ModelLibrary.Entry(9,"author",definition("Original",0xff123456)));
        for(int mode=0;mode<4;mode++) {
            var changed=new ModelLibrary();
            if(mode!=0)changed.accept(new ModelLibrary.Entry(9,mode==3?"other":"author",definition(mode==2?"Renamed":"Original",mode==1?0xffabcdef:0xff123456)));
            assertFalse(changed.preservesBindings(baseline));assertFalse(changed.sameBindings(baseline));assertTrue(changed.copy().sameBindings(read(changed)));
        }
    }
    @Test public void duplicateFingerprintsStillRequireEachExactId()throws Exception {
        var source=new ModelLibrary();var model=definition("Shared",0xff123456);
        source.acceptWorldEntry(new ModelLibrary.Entry(1024,"structure",model));source.acceptWorldEntry(new ModelLibrary.Entry(1025,"structure",model));
        var missing=new ModelLibrary();missing.acceptWorldEntry(new ModelLibrary.Entry(1025,"structure",model));assertFalse(missing.preservesBindings(source));
        var different=new ModelLibrary();different.acceptWorldEntry(new ModelLibrary.Entry(1024,"structure",definition("Other",0xff654321)));different.acceptWorldEntry(new ModelLibrary.Entry(1025,"structure",model));
        assertFalse(different.sameBindings(source));assertTrue(read(source).sameBindings(source));assertTrue(source.copy().sameBindings(source));
    }
    @Test public void mutableCallerEditsCannotChangeStoredIdentityOrWireBytes()throws Exception {
        var source=new ModelLibrary();var model=definition("Mutable caller",0xff123456);source.accept(new ModelLibrary.Entry(9,"author",model));
        var before=bytes(source);var snapshot=source.copy();model.voxels().set(0,0,0,0xffabcdef);
        assertTrue(source.sameBindings(snapshot));assertArrayEquals(before,bytes(source));assertTrue(read(source).sameBindings(snapshot));
        assertThrows(IllegalStateException.class,()->source.get(9).definition().voxels().set(0,0,0,0xffabcdef));
    }
}
