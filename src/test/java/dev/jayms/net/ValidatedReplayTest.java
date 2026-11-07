package dev.jayms.net;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ValidatedReplayTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory;
    private static Protocol.Edit leaf(int x,int z,int index,int type) {
        return new Protocol.Edit(x,128,z,Blocks.piece(type,4),4,index%16,index/256,index/16%16);
    }
    @Test void denseBoundaryCellsRetainEveryLeafAndExactReplayOrder() {
        for(int position:new int[]{-Terrain.LIMIT,0,Terrain.LIMIT}) {
            var actual=new WorldVoxels.History();var expected=new LinkedHashMap<String,Protocol.Edit>();
            var ancestor=new Protocol.Edit(position,128,position,Blocks.STONE);
            WorldVoxels.rememberValidated(actual,ancestor);WorldVoxels.remember(expected,ancestor);
            for(int i=0;i<4096;i++) {
                var edit=leaf(position,position,i,Blocks.BRICKS);
                assertTrue(edit.valid());WorldVoxels.rememberValidated(actual,edit);WorldVoxels.remember(expected,edit);
            }
            assertEquals(4097,actual.size());assertEquals(new ArrayList<>(expected.entrySet()),new ArrayList<>(actual.entrySet()));
            var snapshot=WorldVoxels.History.copyOf(actual);var before=new ArrayList<>(snapshot.entrySet());
            for(int index:new int[]{0,2048,4095,0}) {
                var edit=leaf(position,position,index,Blocks.GLASS);
                WorldVoxels.rememberValidated(actual,edit);WorldVoxels.remember(expected,edit);
                assertEquals(new ArrayList<>(expected.entrySet()),new ArrayList<>(actual.entrySet()));
            }
            assertEquals(before,new ArrayList<>(snapshot.entrySet()));
            var parent=new Protocol.Edit(position,128,position,Blocks.piece(Blocks.WOOD,1),1,0,0,0);
            WorldVoxels.rememberValidated(actual,parent);WorldVoxels.remember(expected,parent);
            assertEquals(new ArrayList<>(expected.entrySet()),new ArrayList<>(actual.entrySet()));
            WorldVoxels.rememberValidated(actual,ancestor);WorldVoxels.remember(expected,ancestor);
            assertEquals(List.of(ancestor),new ArrayList<>(actual.values()));
        }
    }
    @Test void flatAndRegionalWorldLoadsPreserveDenseHistoryAndSavedBytes()throws Exception {
        for(boolean regional:List.of(false,true)) {
            var path=directory.resolve(regional?"regional.dat":"flat.dat");var game=new LocalGame(path,781);
            var ancestor=new Protocol.Edit(-Terrain.LIMIT,128,Terrain.LIMIT,Blocks.STONE);
            game.edits.put(ancestor.key(),ancestor);
            for(int i=0;i<4096;i++){var edit=leaf(-Terrain.LIMIT,Terrain.LIMIT,i,Blocks.BRICKS);game.edits.put(edit.key(),edit);}
            var last=leaf(-Terrain.LIMIT,Terrain.LIMIT,0,Blocks.GLASS);
            WorldVoxels.rememberValidated(game.edits,last);
            if(regional)try(var staged=StagedOfflineSave.stageRegions(path,game.seed,game.generatorVersion,game.models,(WorldVoxels.History)game.edits,()->false,p->{})) {
                staged.publish(game.inventory,game.health,List.of(),0);
            }else game.save();
            byte[] before=java.nio.file.Files.readAllBytes(path);var progress=new ArrayList<Integer>();
            var loaded=new LocalGame(path,0,()->false,progress::add);
            assertEquals(4097,progress.get(progress.size()-1));
            assertEquals(new ArrayList<>(game.edits.entrySet()),new ArrayList<>(loaded.edits.entrySet()));
            loaded.save();assertArrayEquals(before,java.nio.file.Files.readAllBytes(path));
        }
    }
    @Test void randomizedValidatedRecordsMatchGenericCoverageAndMapOrdering() {
        for(Map<String,Protocol.Edit> actual:List.of(new WorldVoxels.History(),new LinkedHashMap<String,Protocol.Edit>())) {
            var expected=new LinkedHashMap<String,Protocol.Edit>();var random=new Random(6713);
            for(int i=0;i<5000;i++) {
                int depth=random.nextInt(5),side=1<<depth;
                var edit=new Protocol.Edit(random.nextInt(3)-1,128,random.nextInt(3)-1,
                        i%7==0?0:Blocks.piece(Blocks.STONE,depth),depth,random.nextInt(side),random.nextInt(side),random.nextInt(side));
                assertTrue(edit.valid());WorldVoxels.rememberValidated(actual,edit);WorldVoxels.remember(expected,edit);
                assertEquals(new ArrayList<>(expected.entrySet()),new ArrayList<>(actual.entrySet()));
            }
            var before=new ArrayList<>(actual.entrySet());
            var bad=new Protocol.Edit(0,128,0,Blocks.STONE,5,0,0,0);
            assertThrows(IllegalArgumentException.class,()->WorldVoxels.rememberValidated(actual,bad));
            assertEquals(before,new ArrayList<>(actual.entrySet()));
        }
    }
}
