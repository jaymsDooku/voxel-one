import dev.jayms.net.*;
import dev.jayms.net.model.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;

/** Uses actual LocalGame saves; all fields are isolated synthetic fixtures. */
public final class RegionOfflineImportCheck {
    static int rejected;
    interface Action{void run()throws Exception;}
    static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    static void reject(Class<? extends Throwable> type,Action action)throws Exception{try{action.run();}catch(Throwable error){if(type.isInstance(error)){rejected++;return;}throw new AssertionError(error);}throw new AssertionError("Unexpected acceptance");}
    static byte[] patch(byte[] bytes,int offset,int value){var copy=bytes.clone();ByteBuffer.wrap(copy).putInt(offset,value);return copy;}
    public static void main(String[] args)throws Exception {
        Path parent=Path.of("target/structure-offline-import-tests");Files.createDirectories(parent);Path root=Files.createTempDirectory(parent,"synthetic-");
        try{run(root);}finally{try(var paths=Files.walk(root)){for(var path:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(path);}}
    }
    static void run(Path root)throws Exception {
        for(int version:new int[]{8,9})for(int terrain:new int[]{1,2,3}){
            Path source=root.resolve("world-"+version+"-"+terrain);var game=new LocalGame(source,123+terrain);
            var modelTree=new SparseVoxelOctree(8);modelTree.set(1,2,3,0xff112233);var custom=new ModelDefinition("synthetic unplaced",modelTree);
            game.models.accept(new ModelLibrary.Entry(42,"synthetic",custom));game.models.accept(new ModelLibrary.Entry(75,"fixture",custom));
            if(version==9)game.models.acceptWorldEntry(new ModelLibrary.Entry(4096,"structure",custom));
            game.inventory.add(42,3);game.inventory.add(Blocks.STONE,64);game.health=7;game.drops.put(17,new ItemDrop(17,42,2,-0.0f,128.5f,-300.25f));
            WorldVoxels.remember(game.edits,new Protocol.Edit(0,128,0,Blocks.STONE));
            WorldVoxels.remember(game.edits,new Protocol.Edit(1,128,0,version==9?4096:42));
            WorldVoxels.remember(game.edits,new Protocol.Edit(2,Terrain.MIN_Y,0,0));
            WorldVoxels.remember(game.edits,new Protocol.Edit(3,128,0,0));
            WorldVoxels.remember(game.edits,new Protocol.Edit(3,128,0,Blocks.piece(Blocks.GLASS,1),1,1,0,1));
            game.save();byte[] bytes=Files.readAllBytes(source);ByteBuffer.wrap(bytes).putInt(12,terrain);Files.write(source,bytes);
            require(ByteBuffer.wrap(bytes).getInt()==version,"Production save version differs");var legacy=new LocalGame(source,0);
            var candidate=RegionOfflineImportPrototype.decode(bytes,()->false);var restored=candidate.restore(()->false);var metadata=candidate.metadata().orElseThrow();
            require(candidate.geometrySnapshot().terrainSeed()==legacy.seed&&candidate.geometrySnapshot().terrainVersion()==legacy.generatorVersion,"World identity changed");
            var expected=new WorldVoxels(new Terrain(legacy.seed,legacy.generatorVersion));for(var edit:legacy.edits.values())expected.apply(edit);
            for(int[] p:new int[][]{{0,128,0},{1,128,0},{2,Terrain.MIN_Y,0},{3,128,0},{400,0,400}})require(expected.cell(p[0],p[1],p[2]).sameValues(restored.world().cell(p[0],p[1],p[2])),"Geometry differs from production loader");
            for(var entry:legacy.models.entries()){var other=restored.models().get(entry.id());require(other!=null&&other.author().equals(entry.author())&&other.definition().fingerprint().equals(entry.definition().fingerprint()),"Library changed");}
            require(metadata.inventory().countType(42)==3&&metadata.health()==7&&metadata.drops().equals(List.copyOf(legacy.drops.values())),"Survival fields changed");
            var storePath=root.resolve("generation-"+version+"-"+terrain);try(var stage=new RegionGenerationPrototype(storePath).stage(candidate,()->false)){stage.commit(()->false);}
            var restarted=new RegionAuthorityPrototype(storePath,legacy.seed,terrain);require(restarted.metadata().orElseThrow().health()==7&&restarted.models().get(75)!=null,"Generation restart lost fields");
            require(Arrays.equals(bytes,Files.readAllBytes(source)),"Migration modified legacy source");
            // Locate fields using existing codecs rather than assumptions about definition byte lengths.
            var raw=new ByteArrayInputStream(bytes);var in=new DataInputStream(raw);in.readInt();in.readLong();in.readInt();if(version==9)ModelLibrary.readAll(in);else ModelLibrary.read(in);Inventory.read(in);int healthOffset=bytes.length-raw.available();in.readByte();int editOffset=bytes.length-raw.available();int n=in.readInt();for(int i=0;i<n;i++)Protocol.Edit.read(in);int dropOffset=bytes.length-raw.available();
            reject(IOException.class,()->RegionOfflineImportPrototype.decode(patch(bytes,0,2),()->false));reject(IOException.class,()->RegionOfflineImportPrototype.decode(patch(bytes,12,0),()->false));
            var badHealth=bytes.clone();badHealth[healthOffset]=21;reject(IOException.class,()->RegionOfflineImportPrototype.decode(badHealth,()->false));
            for(int count:new int[]{-1,200001})reject(IOException.class,()->RegionOfflineImportPrototype.decode(patch(bytes,editOffset,count),()->false));
            for(int count:new int[]{-1,100001})reject(IOException.class,()->RegionOfflineImportPrototype.decode(patch(bytes,dropOffset,count),()->false));
            reject(IOException.class,()->RegionOfflineImportPrototype.decode(Arrays.copyOf(bytes,bytes.length-1),()->false));reject(IOException.class,()->RegionOfflineImportPrototype.decode(Arrays.copyOf(bytes,bytes.length+1),()->false));
            reject(IOException.class,()->RegionOfflineImportPrototype.decode(patch(bytes,editOffset+4+12,1024),()->false));
            reject(IOException.class,()->RegionOfflineImportPrototype.decode(patch(bytes,editOffset+4,Terrain.LIMIT+1),()->false));
            var duplicate=Arrays.copyOf(bytes,bytes.length+18);System.arraycopy(bytes,bytes.length-18,duplicate,bytes.length,18);duplicate=patch(duplicate,dropOffset,2);final byte[] duplicated=duplicate;
            reject(IOException.class,()->RegionOfflineImportPrototype.decode(duplicated,()->false));
            reject(CancellationException.class,()->RegionOfflineImportPrototype.decode(bytes,()->true));var calls=new AtomicInteger();RegionOfflineImportPrototype.decode(bytes,()->{calls.incrementAndGet();return false;});int middle=calls.get()/2;calls.set(0);reject(CancellationException.class,()->RegionOfflineImportPrototype.decode(bytes,()->calls.incrementAndGet()>middle));
            Thread.currentThread().interrupt();try{reject(CancellationException.class,()->RegionOfflineImportPrototype.decode(bytes,()->false));}finally{Thread.interrupted();}
            require(Arrays.equals(candidate.encode(()->false),RegionOfflineImportPrototype.decode(bytes,()->false).encode(()->false)),"Retry differs");
        }
        reject(IOException.class,()->RegionOfflineImportPrototype.decode(new byte[RegionOfflineImportPrototype.MAX_BYTES+1],()->false));
        System.out.printf("{\"status\":\"passed\",\"offline_versions\":2,\"terrain_versions\":3,\"production_save_fixtures\":6,\"rejection_cases\":%d,\"production_integration\":false,\"qualification\":false}%n",rejected);
    }
}
