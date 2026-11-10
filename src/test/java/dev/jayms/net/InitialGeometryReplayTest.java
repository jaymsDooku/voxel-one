package dev.jayms.net;

import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InitialGeometryReplayTest {
    static long revision(WorldVoxels world)throws Exception {
        var field=WorldVoxels.class.getDeclaredField("geometryRevision");field.setAccessible(true);return field.getLong(world);
    }
    static void same(WorldVoxels expected,WorldVoxels actual,List<Protocol.Edit> rows)throws Exception {
        var cells=new HashSet<WorldVoxels.CellPosition>();
        for(var row:rows)cells.add(new WorldVoxels.CellPosition(row.x(),row.y(),row.z()));
        for(var p:cells)assertTrue(expected.cell(p.x(),p.y(),p.z()).sameValues(actual.cell(p.x(),p.y(),p.z())),p.toString());
        assertEquals(revision(expected),revision(actual));
    }
    @Test void denseRepeatedOverlappingAndTerrainBackedCellsMatchPerRowApply()throws Exception {
        var rows=new ArrayList<>(RegionReplayLoaderTest.fragmented(-17));
        rows.add(new Protocol.Edit(32,0,-1,Blocks.STONE));
        rows.add(new Protocol.Edit(-17,180,-1,Blocks.piece(Blocks.GLASS,1),1,0,0,0));
        rows.add(new Protocol.Edit(32,0,-1,0,1,1,0,0));
        rows.add(new Protocol.Edit(-17,180,-1,Blocks.piece(Blocks.LED,4),4,0,0,0,0xffabcd));
        var expected=new WorldVoxels(new Terrain(42));rows.forEach(expected::apply);
        var actual=new WorldVoxels(new Terrain(42));actual.replayInitial(rows,()->false);same(expected,actual,rows);
        var snapshot=actual.snapshot();var original=snapshot.cell(-17,180,-1).copy();
        var later=new Protocol.Edit(-17,180,-1,0);actual.apply(later);expected.apply(later);
        same(expected,actual,rows);assertTrue(original.sameValues(snapshot.cell(-17,180,-1)));
    }
    @Test void seededMixedDepthColorsAndNoncontiguousRunsMatch()throws Exception {
        var random=new Random(739180);var rows=new ArrayList<Protocol.Edit>();
        for(int i=0;i<4000;i++) {
            int depth=random.nextInt(5),width=1<<depth,material=random.nextBoolean()?Blocks.LED:Blocks.GLASS;
            rows.add(new Protocol.Edit(random.nextInt(3)-1,180,-17,
                    Blocks.piece(material,depth),depth,
                    random.nextInt(width),random.nextInt(width),random.nextInt(width),material==Blocks.LED?random.nextInt(0x1000000):0));
        }
        var expected=new WorldVoxels(new Terrain(73));rows.forEach(expected::apply);
        var actual=new WorldVoxels(new Terrain(73));actual.replayInitial(rows,()->false);same(expected,actual,rows);
    }
    @Test void emptyFreshOnlyAndInvalidInputKeepGuardrails()throws Exception {
        var world=new WorldVoxels(new Terrain(42));world.replayInitial(List.of(),()->false);assertEquals(0,revision(world));
        world.apply(new Protocol.Edit(0,180,0,Blocks.STONE));
        assertThrows(IllegalStateException.class,()->world.replayInitial(List.of(),()->false));
        var invalid=new WorldVoxels(new Terrain(42));
        assertThrows(IllegalArgumentException.class,()->invalid.replayInitial(List.of(new Protocol.Edit(0,180,0,Blocks.STONE,5,0,0,0)),()->false));
        assertEquals(0,revision(invalid));
    }
    @Test void cancellationBeforeInputAndBeforeCellPublication()throws Exception {
        var world=new WorldVoxels(new Terrain(42));
        assertThrows(CancellationException.class,()->world.replayInitial(List.of(),()->true));
        assertEquals(0,revision(world));
        var checks=new AtomicInteger();var row=new Protocol.Edit(0,180,0,Blocks.STONE);
        assertThrows(CancellationException.class,()->world.replayInitial(List.of(row),()->checks.incrementAndGet()==2));
        assertEquals(0,revision(world));assertEquals(0,world.cell(0,180,0).get(0,0,0));
    }
}
