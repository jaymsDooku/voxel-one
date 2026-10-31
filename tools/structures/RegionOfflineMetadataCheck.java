import dev.jayms.net.*;
import dev.jayms.net.model.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.CRC32;

/** Synthetic offline survival codec and actual private-generation restart check. */
public final class RegionOfflineMetadataCheck {
    private static int rejected;
    interface Action{void run()throws Exception;}
    static void require(boolean value,String text){if(!value)throw new AssertionError(text);}
    static void reject(Class<? extends Throwable> type,Action action)throws Exception{try{action.run();}catch(Throwable error){if(type.isInstance(error)){rejected++;return;}throw new AssertionError(error);}throw new AssertionError("Unexpected acceptance");}
    static byte[] patch(byte[] original,int offset,int value){var result=original.clone();ByteBuffer.wrap(result).putInt(offset,value);return checksum(result);}
    static byte[] checksum(byte[] bytes){var crc=new CRC32();crc.update(bytes,0,bytes.length-4);ByteBuffer.wrap(bytes).putInt(bytes.length-4,(int)crc.getValue());return bytes;}
    public static void main(String[] args)throws Exception {
        var models=new ModelLibrary();var voxels=new SparseVoxelOctree(8);voxels.set(1,2,3,0xff123456);models.accept(new ModelLibrary.Entry(42,"synthetic",new ModelDefinition("unused inventory model",voxels)));
        var inventory=new Inventory();inventory.add(42,3);inventory.add(Blocks.STONE,64);var drop=new ItemDrop(17,42,2,-0.0f,128.5f,-300.25f);
        var metadata=RegionOfflineMetadataPrototype.capture(inventory,7,List.of(drop),models,()->false);byte[] bytes=metadata.encode(()->false);
        require(bytes.length==107,"Offline codec length");var decoded=RegionOfflineMetadataPrototype.decode(bytes,models,()->false);
        require(Arrays.equals(bytes,decoded.encode(()->false))&&decoded.health()==7&&decoded.nextDrop()==17&&decoded.drops().equals(List.of(drop)),"Metadata round trip changed fields");
        inventory.take(0,42);metadata.inventory().take(0,42);require(metadata.inventory().countType(42)==3,"Mutable inventory escaped");reject(UnsupportedOperationException.class,()->metadata.drops().clear());
        reject(IOException.class,()->RegionOfflineMetadataPrototype.capture(inventory,-1,List.of(),models,()->false));reject(IOException.class,()->RegionOfflineMetadataPrototype.capture(inventory,21,List.of(),models,()->false));
        reject(IOException.class,()->RegionOfflineMetadataPrototype.capture(inventory,7,List.of(drop,drop),models,()->false));
        for(var invalid:List.of(new ItemDrop(0,42,2,0,0,0),new ItemDrop(1,256,2,0,0,0),new ItemDrop(1,42,65,0,0,0),new ItemDrop(1,42,2,Float.NaN,0,0),new ItemDrop(1,42,2,0,Float.POSITIVE_INFINITY,0)))reject(IOException.class,()->RegionOfflineMetadataPrototype.capture(inventory,7,List.of(invalid),models,()->false));
        reject(IOException.class,()->RegionOfflineMetadataPrototype.decode(bytes,new ModelLibrary(),()->false));
        var onlyDrops=RegionOfflineMetadataPrototype.capture(new Inventory(),0,List.of(drop),models,()->false);reject(IOException.class,()->RegionOfflineMetadataPrototype.decode(onlyDrops.encode(()->false),new ModelLibrary(),()->false));
        for(int[] change:new int[][]{{0,0},{4,2},{81,-1},{81,100001},{85,0},{91,0x7fc00000}})reject(IOException.class,()->RegionOfflineMetadataPrototype.decode(patch(bytes,change[0],change[1]),models,()->false));
        var invalidInventory=bytes.clone();invalidInventory[8]=0;reject(IOException.class,()->RegionOfflineMetadataPrototype.decode(checksum(invalidInventory),models,()->false));
        var invalidHealth=bytes.clone();invalidHealth[80]=21;reject(IOException.class,()->RegionOfflineMetadataPrototype.decode(checksum(invalidHealth),models,()->false));
        var broken=bytes.clone();broken[20]^=1;reject(IOException.class,()->RegionOfflineMetadataPrototype.decode(broken,models,()->false));
        for(int size:new int[]{0,88,106,108,RegionOfflineMetadataPrototype.MAX_BYTES+1})reject(IOException.class,()->RegionOfflineMetadataPrototype.decode(Arrays.copyOf(bytes,size),models,()->false));
        var many=new ArrayList<ItemDrop>();for(int i=1;i<=100000;i++)many.add(new ItemDrop(i,Blocks.STONE,1,1,128,1));
        var maximum=RegionOfflineMetadataPrototype.capture(new Inventory(),20,many,models,()->false);byte[] maxBytes=maximum.encode(()->false);require(RegionOfflineMetadataPrototype.decode(maxBytes,models,()->false).drops().size()==100000,"Maximum drop count changed");
        many.add(new ItemDrop(100001,Blocks.STONE,1,1,128,1));reject(IOException.class,()->RegionOfflineMetadataPrototype.capture(new Inventory(),20,many,models,()->false));
        reject(CancellationException.class,()->metadata.encode(()->true));reject(CancellationException.class,()->RegionOfflineMetadataPrototype.capture(inventory,7,List.of(drop),models,()->true));reject(CancellationException.class,()->RegionOfflineMetadataPrototype.decode(bytes,models,()->true));
        var calls=new AtomicInteger();reject(CancellationException.class,()->RegionOfflineMetadataPrototype.decode(maxBytes,models,()->calls.incrementAndGet()>20));
        Thread.currentThread().interrupt();try{reject(CancellationException.class,()->metadata.encode(()->false));}finally{Thread.interrupted();}
        require(Arrays.equals(bytes,RegionOfflineMetadataPrototype.decode(bytes,models,()->false).encode(()->false)),"Retry changed metadata");
        Path parent=Path.of("target/structure-offline-metadata-tests");Files.createDirectories(parent);Path root=Files.createTempDirectory(parent,"synthetic-");
        try {
            var authority=new RegionAuthorityPrototype(root,123,Terrain.CURRENT_VERSION);reject(IOException.class,()->authority.metadata(metadata));require(authority.metadata().isEmpty(),"Invalid metadata changed root");authority.commit(models,Map.of());authority.metadata(metadata);
            var tree=new SparseVoxelOctree(16);tree.fill(0,0,0,16,16,16,WorldVoxels.encode(Blocks.STONE));
            var cell=new dev.jayms.net.structure.StructurePackage.Position(0,128,0);
            authority.commit(authority.models(),Map.of(cell,tree));var restarted=new RegionAuthorityPrototype(root,123,Terrain.CURRENT_VERSION);
            require(Arrays.equals(bytes,restarted.metadata().orElseThrow().encode(()->false))&&restarted.cell(0,128,0).sameValues(tree),"Generation lost metadata or geometry");
            require(restarted.models().get(42)!=null,"Inventory model lost across restart");
            var oldBytes=Files.readAllBytes(root.resolve("current.region"));
            var emptyGeometry=RegionHistoryPrototype.fromCells(123,Terrain.CURRENT_VERSION,List.of(),new RegionHistoryPrototype.CodecBudget(1024,0,0,0),()->false);
            var noMetadata=RegionWorldLibraryPrototype.capture(emptyGeometry,models,()->false);
            reject(IOException.class,()->new RegionGenerationPrototype(root).stage(noMetadata,()->false));
            var changed=RegionOfflineMetadataPrototype.capture(new Inventory(),20,List.of(),models,()->false);
            try(var stage=authority.stageCommit(authority.models(),Map.of(),authority.models().revision(),()->false)){
                authority.metadata(changed);synchronized(authority.lock()){reject(IOException.class,stage::apply);}
            }
            require(Arrays.equals(oldBytes,Files.readAllBytes(root.resolve("current.region")))&&authority.metadata().orElseThrow().health()==20,"Stale metadata overwrote newer state");
            authority.commit(authority.models(),Map.of());require(new RegionAuthorityPrototype(root,123,Terrain.CURRENT_VERSION).metadata().orElseThrow().health()==20,"Retry lost current metadata");
        }finally{try(var paths=Files.walk(root)){for(var path:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(path);}}
        System.out.printf("{\"status\":\"passed\",\"metadata_bytes\":%d,\"maximum_drop_count\":100000,\"maximum_metadata_bytes\":%d,\"rejection_cases\":%d,\"generation_restart\":true,\"production_integration\":false,\"qualification\":false}%n",bytes.length,maxBytes.length,rejected);
    }
}
