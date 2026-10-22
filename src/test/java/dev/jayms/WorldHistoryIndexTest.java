package dev.jayms;

import dev.jayms.net.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorldHistoryIndexTest {
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

}
