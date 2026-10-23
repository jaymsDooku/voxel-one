package dev.jayms;

import dev.jayms.net.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorldHistoryIndexTest {
    @Test void capturedKeysCopyWithoutAliasingMutableEntriesOrWeakeningPublicValidation() {
        var source=new WorldVoxels.History();var reference=new LinkedHashMap<String,Protocol.Edit>();
        for(int i=0;i<100;i++) {
            int depth=i%2==0?4:0;var edit=new Protocol.Edit(i/4,180,0,Blocks.piece(Blocks.STONE,depth),depth,depth==0?0:i%4,0,0,0xffffff);
            source.put(edit.key(),edit);reference.put(edit.key(),edit);
        }
        var batch=source.copyCursor().nextBatch();var target=new WorldVoxels.History();long revision=target.revision();
        batch.copyTo(target,()->{});assertTrue(target.revision()>revision);assertEquals(new ArrayList<>(reference.entrySet()),new ArrayList<>(target.entrySet()));
        var first=batch.get(0);source.put(first.key(),new Protocol.Edit(first.x(),first.y(),first.z(),Blocks.piece(Blocks.BRICKS,first.depth()),first.depth(),first.ix(),first.iy(),first.iz()));
        assertEquals(first,target.get(first.key())); // No live Map.Entry aliases in the batch.
        assertThrows(UnsupportedOperationException.class,()->batch.set(0,new Protocol.Edit(0,0,0,Blocks.STONE)));
        assertThrows(IllegalArgumentException.class,()->target.put("invalid",first));
        var whole=new Protocol.Edit(first.x(),first.y(),first.z(),Blocks.BRICKS);WorldVoxels.remember(target,whole);WorldVoxels.remember(reference,whole);
        assertEquals(new ArrayList<>(reference.entrySet()),new ArrayList<>(target.entrySet()));
        var privateCopy=new WorldVoxels.History();var checks=new java.util.concurrent.atomic.AtomicInteger();
        assertThrows(java.util.concurrent.CancellationException.class,()->batch.copyTo(privateCopy,()->{if(checks.incrementAndGet()==10)throw new java.util.concurrent.CancellationException();}));
        assertEquals(9,privateCopy.size());assertEquals(Blocks.BRICKS,Blocks.material(source.get(first.key()).type()));
        var empty=new WorldVoxels.History().copyCursor().nextBatch();empty.copyTo(target,()->fail("Empty batch callback"));
    }
    @Test void copyCursorBoundsBatchesAndRebuildsIndependentOrderedHistory() {
        var source=new WorldVoxels.History();
        for(int i=0;i<2051;i++){var edit=new Protocol.Edit(i%512,180,i/512,Blocks.STONE);source.put(edit.key(),edit);}
        var cursor=source.copyCursor();var result=new WorldVoxels.History();var sizes=new ArrayList<Integer>();
        // COW owner rotation changes no values and must not invalidate a read cursor.
        var shared=WorldVoxels.History.copyOf(source);
        while(true) {
            var batch=cursor.nextBatch();if(batch.isEmpty())break;sizes.add(batch.size());
            assertThrows(UnsupportedOperationException.class,()->batch.clear());
            for(var edit:batch)result.put(edit.key(),edit);
        }
        assertEquals(List.of(1024,1024,3),sizes);assertEquals(new ArrayList<>(source.entrySet()),new ArrayList<>(result.entrySet()));
        var key=result.keySet().iterator().next();result.remove(key);assertTrue(source.containsKey(key));assertEquals(source,shared);
        source.remove(key);assertThrows(ConcurrentModificationException.class,cursor::nextBatch);
    }
    @Test void copyCursorRejectsEveryMutationBeforeIteratorResume() {
        for(int action=0;action<5;action++) {
            var source=new WorldVoxels.History();var edit=new Protocol.Edit(0,180,0,Blocks.STONE);source.put(edit.key(),edit);
            var cursor=source.copyCursor();
            switch(action) {
                case 0 -> source.put(edit.key(),edit); // Value replacement also invalidates.
                case 1 -> source.remove(edit.key());
                case 2 -> source.clear();
                case 3 -> source.compute(edit.key(),(k,v)->new Protocol.Edit(0,180,0,Blocks.BRICKS));
                default -> WorldVoxels.rememberCell(source,new Protocol.CellState(0,180,0,new dev.jayms.net.model.SparseVoxelOctree(16)));
            }
            assertThrows(ConcurrentModificationException.class,cursor::nextBatch);
        }
        var source=new WorldVoxels.History();var cursor=source.copyCursor();assertTrue(cursor.nextBatch().isEmpty());
        var edit=new Protocol.Edit(0,180,0,Blocks.STONE);source.put(edit.key(),edit);assertThrows(ConcurrentModificationException.class,cursor::nextBatch);
    }
    @Test void revisionCoversMutatingMapDefaultsAndCompleteCellReplacement() {
        var history=new WorldVoxels.History();var a=new Protocol.Edit(0,180,0,Blocks.STONE);long stamp=history.revision();
        history.put(a.key(),a);assertTrue(history.revision()>stamp);stamp=history.revision();
        var snapshot=WorldVoxels.History.copyOf(history);assertEquals(stamp,history.revision());
        history.compute(a.key(),(k,v)->new Protocol.Edit(0,180,0,Blocks.BRICKS));assertTrue(history.revision()>stamp);stamp=history.revision();
        assertThrows(UnsupportedOperationException.class,()->history.replaceAll((k,v)->a));assertEquals(stamp,history.revision());
        var tree=new dev.jayms.net.model.SparseVoxelOctree(16);tree.fill(0,0,0,16,16,16,WorldVoxels.encode(Blocks.STONE));
        WorldVoxels.rememberCell(history,new Protocol.CellState(0,180,0,tree));assertTrue(history.revision()>stamp);stamp=history.revision();
        history.remove(a.key());assertTrue(history.revision()>stamp);stamp=history.revision();
        history.remove("missing");history.clear();assertEquals(stamp,history.revision());
        history.putAll(snapshot);stamp=history.revision();history.clear();assertTrue(history.revision()>stamp);
        assertEquals(a,snapshot.get(a.key()));
    }
    @Test void promotionDemotionAndReplacementPreserveReplayOrder() {
        var actual=new WorldVoxels.History();var expected=new LinkedHashMap<String,Protocol.Edit>();
        var a=new Protocol.Edit(-17,128,-1,Blocks.piece(Blocks.STONE,4),4,0,0,0,0xffffff);
        var b=new Protocol.Edit(-17,128,-1,Blocks.piece(Blocks.BRICKS,4),4,1,0,0,0xffffff);
        for(var edit:List.of(a,b,a,new Protocol.Edit(4,128,3,Blocks.STONE))) {
            assertEquals(expected.put(edit.key(),edit),actual.put(edit.key(),edit));
            assertEquals(new ArrayList<>(expected.entrySet()),new ArrayList<>(actual.entrySet()));
        }
        assertEquals(expected.remove(b.key()),actual.remove(b.key()));
        actual.put(b.key(),b);expected.put(b.key(),b);
        var whole=new Protocol.Edit(-17,128,-1,Blocks.BRICKS);
        WorldVoxels.remember(actual,whole);WorldVoxels.remember(expected,whole);
        assertEquals(new ArrayList<>(expected.entrySet()),new ArrayList<>(actual.entrySet()));
        assertThrows(UnsupportedOperationException.class,()->actual.entrySet().iterator().next().setValue(a));
        assertThrows(UnsupportedOperationException.class,()->actual.values().clear());
        assertThrows(IllegalArgumentException.class,()->actual.put("wrong",a));
        actual.clear();assertTrue(actual.isEmpty());actual.put(a.key(),a);assertEquals(a,actual.remove(a.key()));assertTrue(actual.isEmpty());
    }
    @Test void seededMixedOperationsMatchUnindexedHistory() {
        var actual=new WorldVoxels.History();var expected=new LinkedHashMap<String,Protocol.Edit>();var random=new Random(20261022);
        for(int step=0;step<5000;step++) {
            int depth=random.nextInt(5),side=1<<depth;
            var edit=new Protocol.Edit(random.nextInt(5)-2,128,random.nextInt(5)-2,
                    Blocks.piece(random.nextBoolean()?Blocks.STONE:Blocks.BRICKS,depth),depth,
                    random.nextInt(side),random.nextInt(side),random.nextInt(side),0xffffff);
            if(step%101==0){actual.clear();expected.clear();}
            else if(step%7==0&&!expected.isEmpty()) {
                var key=new ArrayList<>(expected.keySet()).get(random.nextInt(expected.size()));assertEquals(expected.remove(key),actual.remove(key));
            } else if(step%5==0){assertEquals(expected.put(edit.key(),edit),actual.put(edit.key(),edit));}
            else {WorldVoxels.remember(actual,edit);WorldVoxels.remember(expected,edit);}
            assertEquals(new ArrayList<>(expected.entrySet()),new ArrayList<>(actual.entrySet()),"step "+step);
        }
    }
    @Test void nestedTransactionCopiesKeepBothSidesIndependent() {
        var original=new WorldVoxels.History();
        var a=new Protocol.Edit(-17,128,-1,Blocks.piece(Blocks.STONE,4),4,0,0,0,0xffffff);
        var b=new Protocol.Edit(-17,128,-1,Blocks.piece(Blocks.BRICKS,4),4,1,0,0,0xffffff);
        var c=new Protocol.Edit(4,128,3,Blocks.STONE);
        for(var edit:List.of(a,b,c))original.put(edit.key(),edit);
        var expected=new LinkedHashMap<>(original);var fork=WorldVoxels.History.copyOf(original);
        var nested=WorldVoxels.History.copyOf(fork);
        original.remove(a.key());fork.remove(b.key());nested.clear();
        var whole=new Protocol.Edit(-17,128,-1,Blocks.BRICKS);WorldVoxels.remember(fork,whole);
        assertEquals(List.of(b,c),new ArrayList<>(original.values()));
        assertEquals(List.of(c,whole),new ArrayList<>(fork.values()));assertTrue(nested.isEmpty());
        nested.put(a.key(),a);assertEquals(List.of(a),new ArrayList<>(nested.values()));
        var generic=WorldVoxels.History.copyOf(expected);expected.clear();
        assertEquals(List.of(a,b,c),new ArrayList<>(generic.values()));
        assertThrows(IllegalArgumentException.class,()->WorldVoxels.History.copyOf(Map.of("wrong",a)));
        assertThrows(UnsupportedOperationException.class,()->fork.values().clear());
        assertThrows(UnsupportedOperationException.class,()->fork.entrySet().iterator().next().setValue(a));
    }
    @Test void seededForksAndMixedEditsMatchIndependentOrderedMaps() {
        var histories=new ArrayList<WorldVoxels.History>();var references=new ArrayList<LinkedHashMap<String,Protocol.Edit>>();
        histories.add(new WorldVoxels.History());references.add(new LinkedHashMap<>());var random=new Random(20261023);
        for(int step=0;step<5000;step++) {
            int index=random.nextInt(histories.size());var actual=histories.get(index);var expected=references.get(index);
            if(step%43==0&&histories.size()<12){histories.add(WorldVoxels.History.copyOf(actual));references.add(new LinkedHashMap<>(expected));}
            else if(step%101==0){actual.clear();expected.clear();}
            else {
                int depth=random.nextInt(5),side=1<<depth;
                var edit=new Protocol.Edit(random.nextInt(5)-2,128,random.nextInt(5)-2,
                        Blocks.piece(random.nextBoolean()?Blocks.STONE:Blocks.BRICKS,depth),depth,
                        random.nextInt(side),random.nextInt(side),random.nextInt(side),0xffffff);
                if(step%7==0&&!expected.isEmpty()) {
                    var key=new ArrayList<>(expected.keySet()).get(random.nextInt(expected.size()));assertEquals(expected.remove(key),actual.remove(key));
                }else if(step%5==0){assertEquals(expected.put(edit.key(),edit),actual.put(edit.key(),edit));}
                else {WorldVoxels.remember(actual,edit);WorldVoxels.remember(expected,edit);}
            }
            for(int i=0;i<histories.size();i++)assertEquals(new ArrayList<>(references.get(i).entrySet()),new ArrayList<>(histories.get(i).entrySet()),"step "+step+" fork "+i);
        }
    }

    @Test void completeCellReplacementMatchesLegacyOrderAndKeepsCopiesIndependent() {
        var indexed=new WorldVoxels.History();var legacy=new LinkedHashMap<String,Protocol.Edit>();var generic=new LinkedHashMap<String,Protocol.Edit>();var random=new Random(82);
        for(int step=0;step<80;step++) {
            int x=step%5-2;var tree=new dev.jayms.net.model.SparseVoxelOctree(16);
            if(step%4==0)tree.fill(0,0,0,16,16,16,WorldVoxels.encode(Blocks.STONE));
            if(step%4==1)for(int i=0;i<40;i++){int a=random.nextInt(16),b=random.nextInt(16),c=random.nextInt(16);tree.fill(a,b,c,a+1,b+1,c+1,0xfe000000|random.nextInt(0x1000000));}
            if(step%4==2)tree.fill(0,0,0,16,16,16,WorldVoxels.encode(1024));
            var state=new Protocol.CellState(x,128,-1,tree);var before=new ArrayList<>(indexed.entrySet());var copy=WorldVoxels.History.copyOf(indexed);
            WorldVoxels.rememberCell(copy,state);assertEquals(before,new ArrayList<>(indexed.entrySet()));
            WorldVoxels.rememberCell(indexed,state);WorldVoxels.rememberCell(generic,state);for(var edit:state.edits())WorldVoxels.remember(legacy,edit);
            assertEquals(new ArrayList<>(legacy.entrySet()),new ArrayList<>(indexed.entrySet()));assertEquals(new ArrayList<>(legacy.entrySet()),new ArrayList<>(generic.entrySet()));assertEquals(new ArrayList<>(legacy.entrySet()),new ArrayList<>(copy.entrySet()));
            var edit=new Protocol.Edit(x,128,-1,Blocks.piece(Blocks.BRICKS,4),4,1,2,3,0);
            WorldVoxels.remember(indexed,edit);WorldVoxels.remember(generic,edit);WorldVoxels.remember(legacy,edit);
            assertEquals(new ArrayList<>(legacy.entrySet()),new ArrayList<>(indexed.entrySet()));
            assertNotEquals(new ArrayList<>(indexed.entrySet()),new ArrayList<>(copy.entrySet()));
        }
    }
    @Test void denseCellReplacementAndInvalidResolutionPreserveCanonicalHistory() {
        var indexed=new WorldVoxels.History();var legacy=new LinkedHashMap<String,Protocol.Edit>();var tree=new dev.jayms.net.model.SparseVoxelOctree(16);
        for(int i=0;i<4096;i++){int x=i%16,y=i/256,z=i/16%16;tree.fill(x,y,z,x+1,y+1,z+1,0xfe000000|i);}
        var state=new Protocol.CellState(-17,128,-1,tree);WorldVoxels.rememberCell(indexed,state);for(var edit:state.edits())WorldVoxels.remember(legacy,edit);
        assertEquals(new ArrayList<>(legacy.entrySet()),new ArrayList<>(indexed.entrySet()));var before=new ArrayList<>(indexed.entrySet());
        assertThrows(IllegalArgumentException.class,()->WorldVoxels.rememberCell(indexed,new Protocol.CellState(-17,128,-1,new dev.jayms.net.model.SparseVoxelOctree(8))));assertEquals(before,new ArrayList<>(indexed.entrySet()));
        var snapshot=WorldVoxels.History.copyOf(indexed);WorldVoxels.rememberCell(indexed,new Protocol.CellState(-17,128,-1,new dev.jayms.net.model.SparseVoxelOctree(16)));
        assertEquals(1,indexed.size());assertEquals(before,new ArrayList<>(snapshot.entrySet()));
    }
}
