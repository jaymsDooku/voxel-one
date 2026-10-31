import dev.jayms.net.*;
import dev.jayms.net.model.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.CRC32;

/** Synthetic survival identifiers only; never opens account/authentication data or starts a server. */
public final class RegionServerMetadataCheck {
    static int rejected;
    interface Action{void run()throws Exception;}
    static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    static void reject(Class<? extends Throwable> type,Action action)throws Exception{try{action.run();}catch(Throwable error){if(type.isInstance(error)){rejected++;return;}throw new AssertionError(error);}throw new AssertionError("Unexpected acceptance");}
    static byte[] checksum(byte[] bytes){var crc=new CRC32();crc.update(bytes,0,bytes.length-4);ByteBuffer.wrap(bytes).putInt(bytes.length-4,(int)crc.getValue());return bytes;}
    static byte[] patch(byte[] bytes,int offset,int value){var result=bytes.clone();ByteBuffer.wrap(result).putInt(offset,value);return checksum(result);}
    static byte[] inventory(Inventory value)throws IOException{var bytes=new ByteArrayOutputStream();value.write(new DataOutputStream(bytes));return bytes.toByteArray();}
    public static void main(String[] args)throws Exception {
        var models=new ModelLibrary();var voxels=new SparseVoxelOctree(8);voxels.set(1,2,3,0xff123456);models.accept(new ModelLibrary.Entry(42,"synthetic",new ModelDefinition("saved component",voxels)));
        var first=new Inventory();first.add(42,3);first.add(Items.STICKS,5);var second=new Inventory();second.add(Blocks.STONE,64);
        var players=List.of(new RegionServerMetadataPrototype.Player("syntheticA",first,7),new RegionServerMetadataPrototype.Player("SyntheticA",second,20),new RegionServerMetadataPrototype.Player("Ω".repeat(16),new Inventory(),0));
        var drop=new ItemDrop(17,42,2,-0.0f,128.5f,-300.25f);var metadata=RegionServerMetadataPrototype.capture(players,List.of(drop),models,()->false);byte[] bytes=metadata.encode(()->false);
        var decoded=RegionServerMetadataPrototype.decode(bytes,models,()->false);require(Arrays.equals(bytes,decoded.encode(()->false)),"Server metadata byte round trip changed");
        for(int i=0;i<players.size();i++){var expected=players.get(i);var actual=decoded.players().get(i);require(actual.name().equals(expected.name())&&actual.health()==expected.health()&&Arrays.equals(inventory(actual.inventory()),inventory(expected.inventory())),"Saved identifier/inventory/health changed");}
        require(decoded.drops().equals(List.of(drop)),"Drops changed");
        // The payload uses the existing modern server tail layout: count, UTF name, Inventory, health, count, drops.
        var tail=new ByteArrayOutputStream();var out=new DataOutputStream(tail);out.writeInt(players.size());for(var player:players){out.writeUTF(player.name());player.inventory().write(out);out.writeByte(player.health());}out.writeInt(1);drop.write(out);
        require(Arrays.equals(tail.toByteArray(),Arrays.copyOfRange(bytes,8,bytes.length-4)),"Modern server tail layout changed");
        first.take(0,42);metadata.players().get(0).inventory().take(0,42);require(metadata.players().get(0).inventory().countType(42)==3,"Mutable inventory escaped");reject(UnsupportedOperationException.class,()->metadata.players().clear());reject(UnsupportedOperationException.class,()->metadata.drops().clear());
        reject(IOException.class,()->RegionServerMetadataPrototype.capture(List.of(players.get(0),players.get(0)),List.of(),models,()->false));
        reject(IOException.class,()->RegionServerMetadataPrototype.capture(List.of(new RegionServerMetadataPrototype.Player("x".repeat(17),second,20)),List.of(),models,()->false));
        for(int health:new int[]{-1,21})reject(IOException.class,()->RegionServerMetadataPrototype.capture(List.of(new RegionServerMetadataPrototype.Player("synthetic",second,health)),List.of(),models,()->false));
        reject(IOException.class,()->RegionServerMetadataPrototype.decode(bytes,new ModelLibrary(),()->false));
        reject(IOException.class,()->RegionServerMetadataPrototype.capture(players,List.of(drop,drop),models,()->false));
        for(int[] field:new int[][]{{0,0},{4,2},{8,-1},{8,10001}})reject(IOException.class,()->RegionServerMetadataPrototype.decode(patch(bytes,field[0],field[1]),models,()->false));
        for(int length:new int[]{0,19,bytes.length-1,bytes.length+1,RegionServerMetadataPrototype.MAX_BYTES+1})reject(IOException.class,()->RegionServerMetadataPrototype.decode(Arrays.copyOf(bytes,length),models,()->false));
        var broken=bytes.clone();broken[20]^=1;reject(IOException.class,()->RegionServerMetadataPrototype.decode(broken,models,()->false));
        var raw=new ByteArrayInputStream(bytes);var input=new DataInputStream(raw);input.readInt();input.readInt();int playerCount=input.readInt();Protocol.readText(input,16);int inventoryOffset=bytes.length-raw.available();Inventory.read(input);int healthOffset=bytes.length-raw.available();input.readByte();for(int i=1;i<playerCount;i++){Protocol.readText(input,16);Inventory.read(input);input.readByte();}int dropOffset=bytes.length-raw.available();
        var badInventory=bytes.clone();badInventory[inventoryOffset]=0;reject(IOException.class,()->RegionServerMetadataPrototype.decode(checksum(badInventory),models,()->false));
        var badHealth=bytes.clone();badHealth[healthOffset]=(byte)255;reject(IOException.class,()->RegionServerMetadataPrototype.decode(checksum(badHealth),models,()->false));
        reject(IOException.class,()->RegionServerMetadataPrototype.decode(patch(bytes,dropOffset,-1),models,()->false));
        var duplicateName=bytes.clone();duplicateName[14]='S';reject(IOException.class,()->RegionServerMetadataPrototype.decode(checksum(duplicateName),models,()->false));
        var maxPlayers=new ArrayList<RegionServerMetadataPrototype.Player>();for(int i=0;i<10000;i++)maxPlayers.add(new RegionServerMetadataPrototype.Player(String.format(java.util.Locale.ROOT,"p%05d",i),second,20));
        var maxDrops=new ArrayList<ItemDrop>();for(int i=1;i<=100000;i++)maxDrops.add(new ItemDrop(i,Blocks.STONE,1,1,128,1));
        var maximum=RegionServerMetadataPrototype.capture(maxPlayers,maxDrops,models,()->false);byte[] maximumBytes=maximum.encode(()->false);var maximumDecoded=RegionServerMetadataPrototype.decode(maximumBytes,models,()->false);require(maximumDecoded.players().size()==10000&&maximumDecoded.drops().size()==100000,"Capacity boundary changed");
        maxPlayers.add(players.get(0));reject(IOException.class,()->RegionServerMetadataPrototype.capture(maxPlayers,List.of(),models,()->false));maxDrops.add(new ItemDrop(100001,Blocks.STONE,1,1,128,1));reject(IOException.class,()->RegionServerMetadataPrototype.capture(List.of(),maxDrops,models,()->false));
        reject(CancellationException.class,()->metadata.encode(()->true));reject(CancellationException.class,()->RegionServerMetadataPrototype.capture(players,List.of(),models,()->true));reject(CancellationException.class,()->RegionServerMetadataPrototype.decode(bytes,models,()->true));
        var calls=new AtomicInteger();reject(CancellationException.class,()->RegionServerMetadataPrototype.decode(maximumBytes,models,()->calls.incrementAndGet()>30));Thread.currentThread().interrupt();try{reject(CancellationException.class,()->metadata.encode(()->false));}finally{Thread.interrupted();}
        Path parent=Path.of("target/structure-server-metadata-tests");Files.createDirectories(parent);Path root=Files.createTempDirectory(parent,"synthetic-");
        try {
            var geometry=RegionHistoryPrototype.fromCells(123,Terrain.CURRENT_VERSION,List.of(),new RegionHistoryPrototype.CodecBudget(1024,0,0,0),()->false);var candidate=RegionWorldLibraryPrototype.captureServer(geometry,models,metadata,()->false);var store=new RegionGenerationPrototype(root);
            try(var stage=store.stage(candidate,()->false)){stage.commit(()->false);}
            var authority=new RegionAuthorityPrototype(root,123,Terrain.CURRENT_VERSION);require(authority.metadata().isEmpty()&&Arrays.equals(bytes,authority.serverMetadata().orElseThrow().encode(()->false)),"Server state changed across private restart");
            var sidecars=RegionSidecarsPrototype.capture(ByteBuffer.allocate(4).putInt(0x43495441).array(),null,()->false);authority.sidecars(sidecars);
            authority.edit(new Protocol.Edit(0,128,0,Blocks.STONE));authority.commit(authority.models(),Map.of());require(Arrays.equals(bytes,new RegionAuthorityPrototype(root,123,Terrain.CURRENT_VERSION).serverMetadata().orElseThrow().encode(()->false)),"Geometry save lost server metadata");require(Arrays.equals(sidecars.city(),new RegionAuthorityPrototype(root,123,Terrain.CURRENT_VERSION).sidecars().orElseThrow().city()),"Server envelope lost typed sidecar bytes");
            var saved=Files.readAllBytes(root.resolve("current.region"));var offline=RegionOfflineMetadataPrototype.capture(new Inventory(),20,List.of(),models,()->false);var offlineCandidate=RegionWorldLibraryPrototype.capture(geometry,models,offline,()->false);
            reject(IOException.class,()->store.stage(offlineCandidate,()->false));reject(IOException.class,()->authority.metadata(offline));
            var noMetadata=RegionWorldLibraryPrototype.capture(geometry,models,()->false);reject(IOException.class,()->store.stage(noMetadata,()->false));
            var newer=RegionServerMetadataPrototype.capture(List.of(),List.of(),models,()->false);try(var stage=authority.stageCommit(authority.models(),Map.of(),authority.models().revision(),()->false)){authority.serverMetadata(newer);synchronized(authority.lock()){reject(IOException.class,stage::apply);}}
            require(Arrays.equals(saved,Files.readAllBytes(root.resolve("current.region")))&&authority.serverMetadata().orElseThrow().players().isEmpty(),"Stale server state changed disk or root");authority.commit(authority.models(),Map.of());
            require(new RegionAuthorityPrototype(root,123,Terrain.CURRENT_VERSION).serverMetadata().orElseThrow().players().isEmpty(),"Retry lost latest state");
            var offlineRoot=root.resolve("offline");var offlineAuthority=new RegionAuthorityPrototype(offlineRoot,123,Terrain.CURRENT_VERSION);offlineAuthority.metadata(offline);reject(IOException.class,()->offlineAuthority.serverMetadata(metadata));
        }finally{try(var paths=Files.walk(root)){for(var path:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(path);}}
        System.out.printf("{\"status\":\"passed\",\"sample_players\":3,\"maximum_players\":10000,\"maximum_drops\":100000,\"maximum_metadata_bytes\":%d,\"rejection_cases\":%d,\"generation_restart\":true,\"production_integration\":false,\"qualification\":false}%n",maximumBytes.length,rejected);
    }
}
