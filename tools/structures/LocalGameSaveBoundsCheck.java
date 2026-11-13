import dev.jayms.net.*;
import dev.jayms.net.city.*;
import dev.jayms.net.model.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Actual normal-save failure/retry using isolated synthetic game files. */
public final class LocalGameSaveBoundsCheck {
    static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static Map<String,Protocol.Edit> oversized(){return new AbstractMap<>(){
        @Override public int size(){return 2_000_001;}
        @Override public Set<Entry<String,Protocol.Edit>> entrySet(){throw new AssertionError("Oversized history traversed");}
    };}
    static void rejected(LocalGame game)throws Exception{try{game.save();}catch(IOException expected){return;}throw new AssertionError("Oversized save accepted");}
    public static void main(String[] args)throws Exception {
        Path parent=Path.of("target/structure-save-bounds-tests");Files.createDirectories(parent);Path root=Files.createTempDirectory(parent,"synthetic-");
        try {
            for(boolean component:List.of(false,true)) {
                Path file=root.resolve("world-"+component+".dat");var game=new LocalGame(file,126);
                if(component){var model=new SparseVoxelOctree(8);model.set(1,2,3,0xff123456);game.models.stageStructureModel(new ModelDefinition("synthetic",model));}
                WorldVoxels.remember(game.edits,new Protocol.Edit(1,128,1,Blocks.STONE));game.inventory.add(Blocks.STONE,3);game.health=12;
                var ground=new CitySimulation.Ground(){public int type(int x,int y,int z){return y<=0?Blocks.STONE:0;}public void apply(List<Protocol.Edit> edits){}public boolean occupied(int x,int y,int z,int width,int depth){return false;}};
                game.city=new CitySimulation(GameConfig.cityGame(),ground,new Terrain(126),null);game.city.collectHistory();game.save();
                Path city=file.resolveSibling(file.getFileName()+".city"),history=file.resolveSibling(file.getFileName()+".city.history"),temp=file.resolveSibling(file.getFileName()+".tmp");
                byte[] world=Files.readAllBytes(file),cityBytes=Files.readAllBytes(city),historyBytes=Files.readAllBytes(history),sentinel={1,7,3};Files.write(temp,sentinel);
                var prior=game.edits;game.city.advance(1);game.edits=oversized();rejected(game);
                require(Arrays.equals(world,Files.readAllBytes(file))&&Arrays.equals(cityBytes,Files.readAllBytes(city))&&Arrays.equals(historyBytes,Files.readAllBytes(history))&&Arrays.equals(sentinel,Files.readAllBytes(temp)),"Rejected save changed files");
                game.edits=prior;WorldVoxels.remember(game.edits,new Protocol.Edit(2,128,1,Blocks.piece(Blocks.GLASS,1),1,1,0,1));game.save();var loaded=new LocalGame(file,0);
                require(loaded.health==12&&loaded.inventory.countType(Blocks.STONE)==3&&new ArrayList<>(game.edits.values()).equals(new ArrayList<>(loaded.edits.values()))&&!Files.exists(temp)&&game.city.frame().equals(CitySimulation.load(city)),"Retry/reload changed state");
                require(game.models.entries().size()==loaded.models.entries().size(),"Retry lost models");
            }
            Path absent=root.resolve("absent/world.dat");var fresh=new LocalGame(absent,126);fresh.edits=oversized();rejected(fresh);require(!Files.exists(absent.getParent()),"Rejected new save created directory");
        } finally {try(var paths=Files.walk(root)){for(var p:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}}
        System.out.println("{\"status\":\"passed\",\"rejected_saves\":3,\"save_versions\":[8,9],\"prior_files_unchanged\":true,\"retry_and_reload\":true,\"large_fixture_qualification\":false}");
    }
}
