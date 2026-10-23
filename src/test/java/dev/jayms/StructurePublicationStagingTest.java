package dev.jayms;

import dev.jayms.net.*;
import dev.jayms.net.model.*;
import dev.jayms.net.structure.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StructurePublicationStagingTest {
    static class Authority extends StructureValidationStampTest.Authority {
        Runnable staging=()->{};int stages;
        Authority(){super(true);}
        @Override public StructureEngine.Commit stageCommit(ModelLibrary staged,Map<StructurePackage.Position,SparseVoxelOctree> cells,long revision,BooleanSupplier cancel)throws IOException {
            assertFalse(Thread.holdsLock(lock()));stages++;staging.run();
            return ()->{assertTrue(Thread.holdsLock(lock()));commit(staged,cells);};
        }
    }
    static void place(StructureEngine engine,Authority authority,BooleanSupplier cancel)throws Exception {
        try(var plan=engine.prepare(StructureValidationStampTest.asset(),StructureValidationStampTest.DEST,0,authority.snapshot(),cancel,p->{})){plan.commit("test",authority);}
    }
    @Test void placementAndUndoStageOutsideLockAndPublishInside()throws Exception {
        var engine=new StructureEngine();var authority=new Authority();place(engine,authority,()->false);engine.undo("test",authority);
        assertEquals(2,authority.stages);assertEquals(2,authority.commits);assertFalse(engine.busy());
        var p=StructureValidationStampTest.DEST;assertEquals(0,authority.geometry.type(p.x(),p.y(),p.z()));
    }
    @Test void concurrentDestinationEditCanAcquireLockDuringStagingAndRejectsCommit()throws Exception {
        var engine=new StructureEngine();var authority=new Authority();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        authority.staging=()->{entered.countDown();try{assertTrue(release.await(10,TimeUnit.SECONDS));}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}};
        var worker=Executors.newSingleThreadExecutor();
        try(var plan=engine.prepare(StructureValidationStampTest.asset(),StructureValidationStampTest.DEST,0,authority.snapshot(),()->false,p->{})) {
            var result=worker.submit(()->assertThrows(IOException.class,()->plan.commit("test",authority)));
            try {
                assertTrue(entered.await(10,TimeUnit.SECONDS));assertTrue(engine.busy());
                synchronized(authority.lock()){var p=StructureValidationStampTest.DEST;authority.geometry.apply(new Protocol.Edit(p.x(),p.y(),p.z(),Blocks.BRICKS));}
            }finally{release.countDown();}
            result.get(10,TimeUnit.SECONDS);assertEquals(0,authority.commits);assertFalse(engine.busy());
            var p=StructureValidationStampTest.DEST;assertEquals(Blocks.BRICKS,authority.geometry.type(p.x(),p.y(),p.z()));
            assertThrows(IOException.class,()->engine.undo("test",authority));
        }finally{release.countDown();worker.shutdownNow();}
    }
    @Test void failedOrCancelledPublicationStageLeavesNoPlacementAndAllowsRetry()throws Exception {
        for(boolean cancellation:new boolean[]{false,true}) {
            var engine=new StructureEngine();var authority=new Authority();var cancel=new AtomicBoolean();
            authority.staging=()->{if(cancellation)cancel.set(true);else throw new IllegalStateException("Synthetic staging failure");};
            if(cancellation)assertThrows(CancellationException.class,()->place(engine,authority,cancel::get));
            else assertThrows(IllegalStateException.class,()->place(engine,authority,cancel::get));
            assertEquals(0,authority.commits);assertFalse(engine.busy());
            assertThrows(IOException.class,()->engine.undo("test",authority));
            authority.staging=()->{};cancel.set(false);place(engine,authority,cancel::get);assertEquals(1,authority.commits);
        }
    }
    @Test void undoRechecksCellsAndModelsAndRetainsHistoryAfterRejectionOrCancellation()throws Exception {
        for(int mode=0;mode<3;mode++) {
            var engine=new StructureEngine();var authority=new Authority();place(engine,authority,()->false);var cancel=new AtomicBoolean();int selected=mode;
            authority.staging=()->{synchronized(authority.lock()) {
                if(selected==0){var p=StructureValidationStampTest.DEST;authority.geometry.apply(new Protocol.Edit(p.x(),p.y(),p.z(),Blocks.BRICKS));}
                if(selected==1){var tree=new SparseVoxelOctree(8);tree.set(0,0,0,0xff123456);try{authority.models.register(new ModelDefinition("During staging",tree),"test");}catch(IOException e){throw new java.io.UncheckedIOException(e);}}
                if(selected==2)cancel.set(true);
            }};
            if(mode==2)assertThrows(CancellationException.class,()->engine.undo("test",authority,cancel::get));
            else assertThrows(IOException.class,()->engine.undo("test",authority,cancel::get));
            assertEquals(1,authority.commits);assertFalse(engine.busy());authority.staging=()->{};cancel.set(false);
            if(mode==0){var p=StructureValidationStampTest.DEST;authority.geometry.apply(new Protocol.Edit(p.x(),p.y(),p.z(),Blocks.STONE));}
            engine.undo("test",authority,cancel::get);assertEquals(2,authority.commits);
            assertThrows(IOException.class,()->engine.undo("test",authority));
        }
    }
    @Test void serializationFailureCannotPublishOrConsumeUndoHistory()throws Exception {
        var fail=new AtomicBoolean();
        var authority=new Authority(){
            @Override public StructureEngine.Commit stageCommit(ModelLibrary staged,Map<StructurePackage.Position,SparseVoxelOctree> cells,long revision,BooleanSupplier cancel)throws IOException {
                if(fail.get())throw new IOException("Synthetic serialization failure");
                return super.stageCommit(staged,cells,revision,cancel);
            }
        };
        var engine=new StructureEngine();fail.set(true);assertThrows(IOException.class,()->place(engine,authority,()->false));
        assertFalse(engine.busy());assertEquals(0,authority.commits);
        fail.set(false);place(engine,authority,()->false);fail.set(true);
        assertThrows(IOException.class,()->engine.undo("test",authority));assertFalse(engine.busy());assertEquals(1,authority.commits);
        fail.set(false);engine.undo("test",authority);assertEquals(2,authority.commits);
    }
    @Test void wireCancellationPreservesBytesAndAllowsRetry()throws Exception {
        var tree=new SparseVoxelOctree(16);tree.fill(0,0,0,16,16,16,WorldVoxels.encode(Blocks.STONE));
        var delta=new StructureWire.Delta(List.of(),List.of(new Protocol.CellState(1,128,2,tree),new Protocol.CellState(2,128,2,tree)));
        var expected=StructureWire.encode(delta);assertArrayEquals(expected,StructureWire.encode(delta,()->false));
        for(int at=1;at<=4;at++){var count=new AtomicInteger();int selected=at;assertThrows(CancellationException.class,()->StructureWire.encode(delta,()->count.incrementAndGet()==selected));assertArrayEquals(expected,StructureWire.encode(delta));}
        assertEquals(2,StructureWire.decode(expected).cells().size());
        Thread.currentThread().interrupt();try{assertThrows(CancellationException.class,()->StructureWire.encode(delta));}finally{Thread.interrupted();}
        assertArrayEquals(expected,StructureWire.encode(delta));
        var model=new SparseVoxelOctree(8);model.set(0,0,0,0xff123456);
        var entry=new ModelLibrary.Entry(1024,"test",new ModelDefinition("Wire component",model));
        var withModel=new StructureWire.Delta(List.of(entry),delta.cells());var modelBytes=StructureWire.encode(withModel);
        for(int at=1;at<=5;at++){var count=new AtomicInteger();int selected=at;assertThrows(CancellationException.class,()->StructureWire.encode(withModel,()->count.incrementAndGet()==selected));assertArrayEquals(modelBytes,StructureWire.encode(withModel));}
        var decoded=StructureWire.decode(modelBytes);assertEquals(1,decoded.models().size());assertEquals(entry.definition().fingerprint(),decoded.models().get(0).definition().fingerprint());
    }
}
