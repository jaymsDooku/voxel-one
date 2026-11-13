import dev.jayms.net.*;
import dev.jayms.net.model.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.CRC32;

/** Synthetic exact-ID and unplaced-library regression CLI. */
public final class RegionWorldLibraryCheck {
    static int rejected;
    interface Action{void run()throws Exception;}
    static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    static void reject(Class<? extends Throwable> type,Action action)throws Exception{
        try{action.run();}catch(Throwable error){if(type.isInstance(error)){rejected++;return;}throw new AssertionError(error);}throw new AssertionError("Unexpected acceptance");
    }
    static byte[] patch(byte[] source,int offset,int value){var bytes=source.clone();ByteBuffer.wrap(bytes).putInt(offset,value);var crc=new CRC32();crc.update(bytes,0,bytes.length-4);ByteBuffer.wrap(bytes).putInt(bytes.length-4,(int)crc.getValue());return bytes;}
    static ModelDefinition model(String name,int color){var tree=new SparseVoxelOctree(8);tree.set(1,2,3,color);return new ModelDefinition(name,tree);}
    public static void main(String[] args)throws Exception{
        var models=new ModelLibrary();
        models.accept(new ModelLibrary.Entry(42,"synthetic",model("unplaced",0xff123456)));
        models.accept(new ModelLibrary.Entry(75,"fixture",model("alias",0xff123456)));
        models.acceptWorldEntry(new ModelLibrary.Entry(4096,"structure",model("placed",0xff654321)));
        var tree=new SparseVoxelOctree(16);tree.fill(0,0,0,16,16,16,WorldVoxels.encode(4096));
        var air=new SparseVoxelOctree(16);
        var geometry=RegionHistoryPrototype.fromCells(123,Terrain.CURRENT_VERSION,List.of(new Protocol.CellState(0,128,0,tree),new Protocol.CellState(1,0,0,air)),new RegionHistoryPrototype.CodecBudget(8192,2,2,2),()->false);
        var envelope=RegionWorldLibraryPrototype.capture(geometry,models,()->false);var bytes=envelope.encode(()->false);
        var decoded=RegionWorldLibraryPrototype.decode(bytes,()->false);require(Arrays.equals(bytes,decoded.encode(()->false)),"Round-trip bytes changed");
        var restored=decoded.restore(()->false);require(restored.models().entries().size()==4,"Lost unused model or alias");
        for(var entry:models.entries()) {var other=restored.models().get(entry.id());require(other!=null&&other.author().equals(entry.author())&&other.definition().fingerprint().equals(entry.definition().fingerprint()),"Lost exact model ID, author or definition");}
        require(restored.world().cell(0,128,0).sameValues(tree),"Model ID was remapped");require(restored.world().cell(1,0,0).occupied()==0,"Explicit air lost");
        models.register(model("later source",0xff123499),"synthetic");restored.models().register(model("later restored",0xff998877),"fixture");
        require(decoded.restore(()->false).models().entries().size()==4,"Source or restored library mutated snapshot");
        reject(IOException.class,()->RegionWorldLibraryPrototype.capture(geometry,new ModelLibrary(),()->false));
        reject(IOException.class,()->RegionWorldLibraryPrototype.decode(new byte[RegionWorldLibraryPrototype.MAX_BYTES+1],()->false));
        reject(IOException.class,()->RegionWorldLibraryPrototype.decode(Arrays.copyOf(bytes,bytes.length-1),()->false));
        var corrupt=bytes.clone();corrupt[24]^=1;reject(IOException.class,()->RegionWorldLibraryPrototype.decode(corrupt,()->false));
        for(int[] change:new int[][]{{0,0},{4,2},{8,-1},{8,RegionWorldLibraryPrototype.MAX_LIBRARY_BYTES+1},{12,2177},{12,-1},{16,8}}){var bad=patch(bytes,change[0],change[1]);reject(IOException.class,()->RegionWorldLibraryPrototype.decode(bad,()->false));}
        int librarySize=ByteBuffer.wrap(bytes).getInt(8);reject(IOException.class,()->RegionWorldLibraryPrototype.decode(patch(bytes,12+librarySize,0),()->false));
        var trailing=Arrays.copyOf(bytes,bytes.length+1);var crc=new CRC32();crc.update(trailing,0,trailing.length-4);ByteBuffer.wrap(trailing).putInt(trailing.length-4,(int)crc.getValue());reject(IOException.class,()->RegionWorldLibraryPrototype.decode(trailing,()->false));
        reject(CancellationException.class,()->RegionWorldLibraryPrototype.capture(geometry,models,()->true));
        reject(CancellationException.class,()->envelope.encode(()->true));reject(CancellationException.class,()->RegionWorldLibraryPrototype.decode(bytes,()->true));reject(CancellationException.class,()->decoded.restore(()->true));
        var callbacks=new AtomicInteger();RegionWorldLibraryPrototype.decode(bytes,()->{callbacks.incrementAndGet();return false;});int middle=callbacks.get()/2;callbacks.set(0);
        reject(CancellationException.class,()->RegionWorldLibraryPrototype.decode(bytes,()->callbacks.incrementAndGet()>middle));
        var empty=RegionHistoryPrototype.fromCells(123,Terrain.CURRENT_VERSION,List.of(),new RegionHistoryPrototype.CodecBudget(1024,0,0,0),()->false);
        var defaultOnly=RegionWorldLibraryPrototype.capture(empty,new ModelLibrary(),()->false);
        require(RegionWorldLibraryPrototype.decode(defaultOnly.encode(()->false),()->false).restore(()->false).models().entries().size()==1,"Lost default library");
        var invalidAuthor=new ModelLibrary();invalidAuthor.accept(new ModelLibrary.Entry(42,"synthetic-author-too-long",model("unused",0xff123456)));
        reject(IOException.class,()->RegionWorldLibraryPrototype.capture(empty,invalidAuthor,()->false));
        // Parse the existing library record to locate the second ID, independent of definition size.
        var position=new ByteArrayInputStream(bytes,12,librarySize);var input=new DataInputStream(position);input.readInt();input.readInt();input.readUTF();ModelDefinition.read(input);
        int secondIdOffset=12+librarySize-position.available();reject(IOException.class,()->RegionWorldLibraryPrototype.decode(patch(bytes,secondIdOffset,42),()->false));
        for(int length:new int[]{0,4,8,12,16,bytes.length-4})reject(IOException.class,()->RegionWorldLibraryPrototype.decode(Arrays.copyOf(bytes,length),()->false));
        Thread.currentThread().interrupt();try{reject(CancellationException.class,()->RegionWorldLibraryPrototype.decode(bytes,()->false));}finally{Thread.interrupted();}
        require(Arrays.equals(bytes,RegionWorldLibraryPrototype.decode(bytes,()->false).encode(()->false)),"Retry changed envelope");
        System.out.printf("{\"status\":\"passed\",\"model_entries\":4,\"unplaced_custom_entries\":2,\"envelope_bytes\":%d,\"rejection_cases\":%d,\"production_integration\":false,\"qualification\":false}%n",bytes.length,rejected);
    }
}
