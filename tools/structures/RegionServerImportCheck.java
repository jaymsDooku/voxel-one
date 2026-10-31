import dev.jayms.net.*;
import dev.jayms.net.model.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;

/** Hand-built saved-world schema fixtures, not real accounts or a running server. */
public final class RegionServerImportCheck {
    static int rejected,fixtures;
    interface Action{void run()throws Exception;}
    static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    static void reject(Class<? extends Throwable> type,Action action)throws Exception{try{action.run();}catch(Throwable error){if(type.isInstance(error)){rejected++;return;}throw new AssertionError(error);}throw new AssertionError("Unexpected acceptance");}
    static byte[] patch(byte[] bytes,int offset,int value){var copy=bytes.clone();ByteBuffer.wrap(copy).putInt(offset,value);return copy;}
    record Fixture(byte[] bytes,int editCount,int playerCount,int health,int dropCount,WorldVoxels world,ModelLibrary models,Inventory inventory,int component){}
    static Fixture fixture(int version,int terrain)throws IOException {
        var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);out.writeInt(Protocol.MAGIC);out.writeInt(-version);out.writeLong(123+terrain);if(version>=7)out.writeInt(terrain);
        var models=new ModelLibrary();int component=Blocks.FLOWER_POT;
        if(version>=4){var tree=new SparseVoxelOctree(8);tree.set(1,2,3,0xff112233);var definition=new ModelDefinition("synthetic",tree);models.accept(new ModelLibrary.Entry(42,"fixture",definition));models.accept(new ModelLibrary.Entry(75,"unused",definition));component=42;if(version==9){models.acceptWorldEntry(new ModelLibrary.Entry(4096,"placed",definition));}if(version==9)models.writeAll(out);else models.write(out);}
        var edits=new ArrayList<Protocol.Edit>();edits.add(new Protocol.Edit(0,128,0,Blocks.STONE));edits.add(new Protocol.Edit(1,128,0,version==9?4096:component));edits.add(new Protocol.Edit(2,Terrain.MIN_Y,0,0));
        if(version>=5){edits.add(new Protocol.Edit(3,128,0,0));edits.add(new Protocol.Edit(3,128,0,Blocks.piece(Blocks.GLASS,1),1,1,0,1));}
        edits.add(new Protocol.Edit(4,128,0,Blocks.LED).withColor(0x123456));int editCount=bytes.size();out.writeInt(edits.size());
        var expected=new WorldVoxels(new Terrain(123+terrain,terrain));
        for(var edit:edits){var wire=new ByteArrayOutputStream();edit.write(new DataOutputStream(wire));byte[] encoded=Arrays.copyOf(wire.toByteArray(),version<5?16:version==5?20:24);out.write(encoded);var input=new DataInputStream(new ByteArrayInputStream(encoded));expected.apply(version>=6?Protocol.Edit.read(input):version==5?Protocol.Edit.readV7(input):Protocol.Edit.readLegacy(input));}
        int playerCount=bytes.size();out.writeInt(2);out.writeUTF("synthetic");var inventory=new Inventory();inventory.add(component,3);inventory.add(Blocks.STONE,64);inventory.write(out);int health=bytes.size();out.writeByte(7);out.writeUTF("\u03a9".repeat(16));new Inventory().write(out);out.writeByte(0);
        int dropCount=bytes.size();out.writeInt(1);new ItemDrop(17,component,2,-0.0f,128.5f,-300.25f).write(out);
        return new Fixture(bytes.toByteArray(),editCount,playerCount,health,dropCount,expected,models,inventory,component);
    }
    public static void main(String[] args)throws Exception {
        Path parent=Path.of("target/structure-server-import-tests");Files.createDirectories(parent);Path root=Files.createTempDirectory(parent,"synthetic-");
        try{
            for(int version=3;version<=9;version++)for(int terrain=1;terrain<=(version>=7?3:1);terrain++){
                var f=fixture(version,terrain);byte[] bytes=f.bytes(),original=bytes.clone();var candidate=RegionServerImportPrototype.decode(bytes,()->false);fixtures++;
                var restored=candidate.restore(()->false);var metadata=candidate.serverMetadata().orElseThrow();require(candidate.metadata().isEmpty(),"Wrong survival mode");require(candidate.geometrySnapshot().terrainSeed()==123+terrain&&candidate.geometrySnapshot().terrainVersion()==terrain,"Identity changed");
                for(int[] p:new int[][]{{0,128,0},{1,128,0},{2,Terrain.MIN_Y,0},{3,128,0},{4,128,0},{400,0,400}})require(f.world().cell(p[0],p[1],p[2]).sameValues(restored.world().cell(p[0],p[1],p[2])),"Geometry/color changed");
                for(var entry:f.models().entries()){var actual=restored.models().get(entry.id());require(actual!=null&&actual.author().equals(entry.author())&&actual.definition().fingerprint().equals(entry.definition().fingerprint()),"Model library changed");}
                require(metadata.players().size()==2&&metadata.players().get(0).name().equals("synthetic")&&metadata.players().get(0).health()==7&&metadata.players().get(0).inventory().countType(f.component())==3&&metadata.players().get(0).inventory().countType(Blocks.STONE)==64&&metadata.players().get(1).name().equals("\u03a9".repeat(16))&&metadata.players().get(1).health()==0,"Player state changed");
                require(metadata.drops().equals(List.of(new ItemDrop(17,f.component(),2,-0.0f,128.5f,-300.25f))),"Drops changed");
                Path destination=root.resolve("v"+version+"-"+terrain);try(var stage=new RegionGenerationPrototype(destination).stage(candidate,()->false)){stage.commit(()->false);}var authority=new RegionAuthorityPrototype(destination,123+terrain,terrain);require(Arrays.equals(metadata.encode(()->false),authority.serverMetadata().orElseThrow().encode(()->false)),"Restart changed metadata");
                byte[] saved=Files.readAllBytes(destination.resolve("current.region"));
                for(int invalid:new int[]{0,1})reject(IOException.class,()->RegionServerImportPrototype.decode(patch(bytes,0,invalid),()->false));
                for(int invalid:new int[]{0,-2,-10,Integer.MIN_VALUE})reject(IOException.class,()->RegionServerImportPrototype.decode(patch(bytes,4,invalid),()->false));
                for(int invalid:new int[]{-1,200001})reject(IOException.class,()->RegionServerImportPrototype.decode(patch(bytes,f.editCount(),invalid),()->false));
                for(int invalid:new int[]{-1,10001})reject(IOException.class,()->RegionServerImportPrototype.decode(patch(bytes,f.playerCount(),invalid),()->false));
                for(int invalid:new int[]{-1,100001})reject(IOException.class,()->RegionServerImportPrototype.decode(patch(bytes,f.dropCount(),invalid),()->false));
                var badHealth=bytes.clone();badHealth[f.health()]=21;reject(IOException.class,()->RegionServerImportPrototype.decode(badHealth,()->false));
                reject(IOException.class,()->RegionServerImportPrototype.decode(patch(bytes,f.editCount()+4+12,1024),()->false));
                for(int size:new int[]{0,27,bytes.length-1,bytes.length+1})reject(IOException.class,()->RegionServerImportPrototype.decode(Arrays.copyOf(bytes,size),()->false));
                if(version>=7)reject(IOException.class,()->RegionServerImportPrototype.decode(patch(bytes,16,0),()->false));
                reject(CancellationException.class,()->RegionServerImportPrototype.decode(bytes,()->true));var calls=new AtomicInteger();RegionServerImportPrototype.decode(bytes,()->{calls.incrementAndGet();return false;});int middle=calls.get()/2;calls.set(0);reject(CancellationException.class,()->RegionServerImportPrototype.decode(bytes,()->calls.incrementAndGet()>middle));
                Thread.currentThread().interrupt();try{reject(CancellationException.class,()->RegionServerImportPrototype.decode(bytes,()->false));}finally{Thread.interrupted();}
                require(Arrays.equals(candidate.encode(()->false),RegionServerImportPrototype.decode(bytes,()->false).encode(()->false)),"Retry changed");require(Arrays.equals(original,bytes)&&Arrays.equals(saved,Files.readAllBytes(destination.resolve("current.region"))),"Decode changed source/destination");
                var sidecars=RegionSidecarsPrototype.capture(ByteBuffer.allocate(4).putInt(0x43495441).array(),null,()->false);require(Arrays.equals(sidecars.city(),RegionServerImportPrototype.decode(bytes,sidecars,()->false).sidecars().orElseThrow().city()),"Typed bytes lost");
            }
            reject(IOException.class,()->RegionServerImportPrototype.decode(new byte[RegionServerImportPrototype.MAX_BYTES+1],()->false));
            var empty=new ByteArrayOutputStream();var out=new DataOutputStream(empty);out.writeInt(Protocol.MAGIC);out.writeInt(-3);out.writeLong(123);out.writeInt(0);out.writeInt(0);out.writeInt(0);require(empty.size()==28&&RegionServerImportPrototype.decode(empty.toByteArray(),()->false).serverMetadata().orElseThrow().players().isEmpty(),"Minimum source rejected");
        }finally{try(var paths=Files.walk(root)){for(var path:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(path);}}
        System.out.printf("{\"status\":\"passed\",\"server_formats\":7,\"schema_fixtures\":%d,\"minimum_bytes\":28,\"rejection_cases\":%d,\"production_integration\":false,\"qualification\":false}%n",fixtures,rejected);
    }
}
