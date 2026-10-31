import dev.jayms.net.*;
import dev.jayms.net.model.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;

/** Hand-built old-format synthetic fixtures checked against the actual production loader. */
public final class RegionOfflineLegacyCheck {
    static int rejected,fixtures;
    interface Action{void run()throws Exception;}
    static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    static void reject(Class<? extends Throwable> type,Action action)throws Exception{try{action.run();}catch(Throwable error){if(type.isInstance(error)){rejected++;return;}throw new AssertionError(error);}throw new AssertionError("Unexpected acceptance");}
    record Fixture(byte[] bytes,int inventoryOffset,int dropRecordOffset){}
    static byte[] inventory(Inventory value)throws IOException{var bytes=new ByteArrayOutputStream();value.write(new DataOutputStream(bytes));return bytes.toByteArray();}
    static Fixture fixture(int version,int terrain)throws IOException {
        var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);out.writeInt(version);out.writeLong(123+terrain);if(version>=7)out.writeInt(terrain);
        var models=new ModelLibrary();int component=Blocks.FLOWER_POT;
        if(version>=4){var tree=new SparseVoxelOctree(8);tree.set(1,2,3,0xff112233);var model=new ModelDefinition("old synthetic",tree);models.accept(new ModelLibrary.Entry(42,"synthetic",model));models.accept(new ModelLibrary.Entry(75,"fixture",model));models.write(out);component=42;}
        int inventoryOffset=bytes.size();var inventory=new Inventory();inventory.add(component,3);inventory.add(Blocks.STONE,64);inventory.write(out);out.writeByte(7);
        var edits=new ArrayList<Protocol.Edit>();edits.add(new Protocol.Edit(0,128,0,Blocks.STONE));edits.add(new Protocol.Edit(1,128,0,component));edits.add(new Protocol.Edit(2,Terrain.MIN_Y,0,0));
        if(version>=5){edits.add(new Protocol.Edit(3,128,0,0));edits.add(new Protocol.Edit(3,128,0,Blocks.piece(Blocks.GLASS,1),1,1,0,1));}
        var led=new Protocol.Edit(4,128,0,Blocks.LED);if(version>=6)led=led.withColor(0x123456);edits.add(led);
        out.writeInt(edits.size());for(var edit:edits){require(edit.valid(),"Invalid synthetic edit");var wire=new ByteArrayOutputStream();edit.write(new DataOutputStream(wire));out.write(wire.toByteArray(),0,version<5?16:version==5?20:24);}
        out.writeInt(1);int dropOffset=bytes.size();new ItemDrop(17,component,2,-0.0f,128.5f,-300.25f).write(out);
        return new Fixture(bytes.toByteArray(),inventoryOffset,dropOffset);
    }
    public static void main(String[] args)throws Exception {
        Path parent=Path.of("target/structure-offline-legacy-tests");Files.createDirectories(parent);Path root=Files.createTempDirectory(parent,"synthetic-");
        try{run(root);}finally{try(var paths=Files.walk(root)){for(var path:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(path);}}
    }
    static void run(Path root)throws Exception {
        for(int version=3;version<=7;version++)for(int terrain=1;terrain<=(version==7?3:1);terrain++){
            var fixture=fixture(version,terrain);var bytes=fixture.bytes();Path source=root.resolve("world-"+version+"-"+terrain);Files.write(source,bytes);var legacy=new LocalGame(source,0);
            var candidate=RegionOfflineImportPrototype.decode(bytes,()->false);var restored=candidate.restore(()->false);var metadata=candidate.metadata().orElseThrow();fixtures++;
            require(candidate.geometrySnapshot().terrainSeed()==legacy.seed&&candidate.geometrySnapshot().terrainVersion()==legacy.generatorVersion,"Old world identity changed");
            var expected=new WorldVoxels(new Terrain(legacy.seed,legacy.generatorVersion));for(var edit:legacy.edits.values())expected.apply(edit);
            for(int[] p:new int[][]{{0,128,0},{1,128,0},{2,Terrain.MIN_Y,0},{3,128,0},{4,128,0},{400,0,400}})require(expected.cell(p[0],p[1],p[2]).sameValues(restored.world().cell(p[0],p[1],p[2])),"Old geometry/default color changed");
            require(Arrays.equals(inventory(legacy.inventory),inventory(metadata.inventory()))&&metadata.health()==legacy.health&&metadata.drops().equals(List.copyOf(legacy.drops.values())),"Old survival fields changed");
            for(var entry:legacy.models.entries()){var other=restored.models().get(entry.id());require(other!=null&&other.author().equals(entry.author())&&other.definition().fingerprint().equals(entry.definition().fingerprint()),"Old library changed");}
            var storePath=root.resolve("generation-"+version+"-"+terrain);try(var stage=new RegionGenerationPrototype(storePath).stage(candidate,()->false)){stage.commit(()->false);}
            var restarted=new RegionAuthorityPrototype(storePath,legacy.seed,legacy.generatorVersion);require(Arrays.equals(metadata.encode(()->false),restarted.metadata().orElseThrow().encode(()->false)),"Old survival restart changed");
            if(version>=4)require(restarted.models().get(75)!=null,"Unused old alias lost");
            for(int badVersion:new int[]{0,2,10}){var invalid=bytes.clone();ByteBuffer.wrap(invalid).putInt(0,badVersion);reject(IOException.class,()->RegionOfflineImportPrototype.decode(invalid,()->false));}
            var newItem=bytes.clone();newItem[fixture.inventoryOffset()]=(byte)Items.STICKS;reject(IOException.class,()->RegionOfflineImportPrototype.decode(newItem,()->false));
            var newDrop=bytes.clone();newDrop[fixture.dropRecordOffset()+4]=(byte)Items.STICKS;reject(IOException.class,()->RegionOfflineImportPrototype.decode(newDrop,()->false));
            for(int size:new int[]{0,92,bytes.length-1,bytes.length+1})reject(IOException.class,()->RegionOfflineImportPrototype.decode(Arrays.copyOf(bytes,size),()->false));
            reject(CancellationException.class,()->RegionOfflineImportPrototype.decode(bytes,()->true));
            var calls=new AtomicInteger();RegionOfflineImportPrototype.decode(bytes,()->{calls.incrementAndGet();return false;});int middle=calls.get()/2;calls.set(0);reject(CancellationException.class,()->RegionOfflineImportPrototype.decode(bytes,()->calls.incrementAndGet()>middle));
            require(Arrays.equals(candidate.encode(()->false),RegionOfflineImportPrototype.decode(bytes,()->false).encode(()->false)),"Old retry changed candidate");require(Arrays.equals(bytes,Files.readAllBytes(source)),"Old source was mutated");
        }
        // Minimal v3 file has no model table or edits/drops; default models must still be restored.
        var empty=new ByteArrayOutputStream();var out=new DataOutputStream(empty);out.writeInt(3);out.writeLong(123);new Inventory().write(out);out.writeByte(20);out.writeInt(0);out.writeInt(0);
        require(empty.size()==93&&RegionOfflineImportPrototype.decode(empty.toByteArray(),()->false).restore(()->false).models().entries().size()==1,"Minimal v3 changed");
        System.out.printf("{\"status\":\"passed\",\"legacy_versions\":5,\"legacy_fixtures\":%d,\"minimum_v3_bytes\":93,\"rejection_cases\":%d,\"production_integration\":false,\"qualification\":false}%n",fixtures,rejected);
    }
}
