package dev.jayms.net.model;

import java.io.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ModelAllocationTest {
    static ModelDefinition definition(int n) {
        var tree=new SparseVoxelOctree(8);tree.set(1,2,3,0xff000000|n+1);
        return new ModelDefinition("Allocation "+n,tree);
    }
    static byte[] bytes(ModelLibrary models)throws IOException {
        var out=new ByteArrayOutputStream();models.writeAll(new DataOutputStream(out));return out.toByteArray();
    }
    static ModelLibrary read(ModelLibrary models)throws IOException {return ModelLibrary.readAll(new DataInputStream(new ByteArrayInputStream(bytes(models))));}
    static int lowest(ModelLibrary models) {
        var used=new HashSet<Integer>();for(var entry:models.entries())used.add(entry.id());
        for(int i=1024;i<66560;i++)if(!used.contains(i))return i;
        throw new AssertionError("Test budget must leave a free ID");
    }
    static void stage(ModelLibrary models,int n)throws Exception {
        int expected=lowest(models);long revision=models.revision();var d=definition(n);var entry=models.stageStructureModel(d);
        assertEquals(expected,entry.id());assertEquals(revision+1,models.revision());assertSame(entry,models.stageStructureModel(d));assertEquals(revision+1,models.revision());
    }
    @Test void outOfOrderImportsAndHighestValidIdKeepTheLowestHole()throws Exception {
        var models=new ModelLibrary();
        for(int id:new int[]{66559,1027,1025,1024,1028})models.acceptWorldEntry(new ModelLibrary.Entry(id,"structure",definition(id)));
        assertEquals(1026,lowest(models));stage(models,100000);assertEquals(1029,lowest(models));
        var parsed=read(models);assertArrayEquals(bytes(models),bytes(parsed));stage(parsed,100001);stage(models,100001);assertArrayEquals(bytes(models),bytes(parsed));
    }
    @Test void aliasesReuseTheirExistingIdAndLegacyIdsDoNotAdvanceExtendedAllocation()throws Exception {
        var models=new ModelLibrary();var shared=definition(10);models.accept(new ModelLibrary.Entry(9,"author",shared));
        assertEquals(9,models.stageStructureModel(shared).id());assertEquals(10,models.nextId());
        models.acceptWorldEntry(new ModelLibrary.Entry(1026,"structure",shared));models.acceptWorldEntry(new ModelLibrary.Entry(1025,"structure",shared));
        long revision=models.revision();assertEquals(1025,models.stageStructureModel(shared).id());assertEquals(revision,models.revision());
        stage(models,100000);stage(models,100001);assertEquals(1028,lowest(models));assertEquals(10,models.nextId());
    }
    @Test void copyReplacementRollbackAndSourceRebaseRetainIndependentLowestIds()throws Exception {
        var original=new ModelLibrary();stage(original,1);var prepared=original.copy();stage(prepared,2);stage(prepared,3);
        assertEquals(1025,lowest(original));assertEquals(1027,lowest(prepared));original.replaceWith(prepared);stage(original,4);
        assertEquals(1027,lowest(prepared));var saved=read(prepared);var rebased=prepared.copyForSource(saved);stage(rebased,5);assertEquals(1027,lowest(prepared));
        original.replaceWith(new ModelLibrary());assertEquals(1024,lowest(original));stage(original,6);original.replaceWith(prepared);stage(original,7);
        assertEquals(1027,lowest(prepared));assertEquals(1028,lowest(original));
    }
    @Test void failedAdmissionConsumesNoIdOrSerializedState()throws Exception {
        var models=new ModelLibrary();stage(models,1);byte[] before=bytes(models);long revision=models.revision();
        assertThrows(IOException.class,()->models.acceptWorldEntry(new ModelLibrary.Entry(1025,"structure",new ModelDefinition("Empty",new SparseVoxelOctree(8)))));
        assertArrayEquals(before,bytes(models));assertEquals(revision,models.revision());stage(models,2);assertEquals(1026,lowest(models));
        before=bytes(models);assertThrows(IOException.class,()->models.acceptWorldEntry(new ModelLibrary.Entry(1025,"structure",definition(3))));assertArrayEquals(before,bytes(models));
    }
    @Test void differentialAllocationSurvivesRandomImportsCopiesAndReloads()throws Exception {
        var models=new ModelLibrary();var random=new Random(0x5177);
        for(int n=0;n<160;n++) {
            if(n%4==0) {
                int id=1024+random.nextInt(400);if(models.get(id)==null)models.acceptWorldEntry(new ModelLibrary.Entry(id,"structure",definition(200000+n)));
            }else stage(models,100000+n);
            if(n%7==0)models=models.copy();
            if(n%11==0){var parsed=read(models);assertArrayEquals(bytes(models),bytes(parsed));models=parsed;}
        }
        stage(models,300000);
    }
    @Test void fullWorldBudgetStillAllowsDedupAndRejectsAnotherNewDefinition()throws Exception {
        var models=new ModelLibrary();for(int i=0;i<2048;i++)assertEquals(1024+i,models.stageStructureModel(definition(i)).id());
        byte[] before=bytes(models);long revision=models.revision();assertEquals(1024,models.stageStructureModel(definition(0)).id());
        assertThrows(IOException.class,()->models.stageStructureModel(definition(300000)));assertEquals(revision,models.revision());assertArrayEquals(before,bytes(models));
        var copy=models.copy();copy.replaceWith(new ModelLibrary());stage(copy,400000);assertEquals(3072,lowest(models));
    }
}
