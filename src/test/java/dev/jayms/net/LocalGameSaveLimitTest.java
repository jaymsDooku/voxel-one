package dev.jayms.net;

import dev.jayms.net.city.*;
import dev.jayms.net.model.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class LocalGameSaveLimitTest {
    @TempDir Path folder;
    private static Map<String,Protocol.Edit> oversized() {
        return new AbstractMap<>() {
            @Override public int size(){return 2_000_001;}
            @Override public Set<Entry<String,Protocol.Edit>> entrySet(){throw new AssertionError("Oversized history traversed");}
        };
    }
    private static CitySimulation city(long seed) {
        var ground=new CitySimulation.Ground(){
            public int type(int x,int y,int z){return y<=0?Blocks.STONE:0;}
            public void apply(List<Protocol.Edit> edits){}
            public boolean occupied(int x,int y,int z,int width,int depth){return false;}
        };
        var city=new CitySimulation(GameConfig.cityGame(),ground,new Terrain(seed),null);city.collectHistory();return city;
    }
    @Test void oversizedNormalSavePreservesPriorWorldAndCityFilesThenAllowsRetry()throws Exception {
        for(boolean component:List.of(false,true)) {
            Path path=folder.resolve("world-"+component+".dat");var game=new LocalGame(path,126);
            if(component){var voxels=new SparseVoxelOctree(8);voxels.set(1,2,3,0xff123456);game.models.stageStructureModel(new ModelDefinition("save guard component",voxels));}
            var edit=new Protocol.Edit(1,128,1,Blocks.STONE);WorldVoxels.remember(game.edits,edit);
            game.inventory.add(Blocks.STONE,3);game.health=12;game.city=city(game.seed);game.save();
            Path city=path.resolveSibling(path.getFileName()+".city"),history=path.resolveSibling(path.getFileName()+".city.history"),temp=path.resolveSibling(path.getFileName()+".tmp");
            byte[] worldBytes=Files.readAllBytes(path),cityBytes=Files.readAllBytes(city),historyBytes=Files.readAllBytes(history),sentinel={1,7,3};Files.write(temp,sentinel);
            var prior=game.edits;game.city.advance(1);game.edits=oversized();
            assertThrows(IOException.class,game::save);
            assertArrayEquals(worldBytes,Files.readAllBytes(path));assertArrayEquals(cityBytes,Files.readAllBytes(city));assertArrayEquals(historyBytes,Files.readAllBytes(history));assertArrayEquals(sentinel,Files.readAllBytes(temp));
            game.edits=prior;var fractional=new Protocol.Edit(2,128,1,Blocks.piece(Blocks.GLASS,1),1,1,0,1);WorldVoxels.remember(game.edits,fractional);game.save();
            var loaded=new LocalGame(path,0);assertEquals(game.seed,loaded.seed);assertEquals(12,loaded.health);assertEquals(3,loaded.inventory.countType(Blocks.STONE));assertEquals(new ArrayList<>(game.edits.values()),new ArrayList<>(loaded.edits.values()));assertArrayEquals(sentinel,Files.readAllBytes(temp));
            try(var files=Files.list(folder)){assertFalse(files.anyMatch(p->p.getFileName().toString().startsWith(".world-save-")));}
            assertEquals(game.city.frame(),CitySimulation.load(city));
        }
    }
    @Test void oversizedNewWorldDoesNotCreateDirectoryOrPartialFiles()throws Exception {
        Path path=folder.resolve("not-created/world.dat");var game=new LocalGame(path,126);game.edits=oversized();
        assertThrows(IOException.class,game::save);assertFalse(Files.exists(path.getParent()));
    }
}
