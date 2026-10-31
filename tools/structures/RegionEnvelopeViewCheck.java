import dev.jayms.net.*;
import dev.jayms.net.city.*;
import dev.jayms.net.model.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.zip.CRC32;

/** All private envelope versions exercise bounded library/geometry section views. */
public final class RegionEnvelopeViewCheck {
    static int rejects;
    interface Action{void run()throws Exception;}
    static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static void reject(Class<? extends Throwable> type,Action action)throws Exception{try{action.run();}catch(Throwable e){if(type.isInstance(e)){rejects++;return;}throw e;}throw new AssertionError("Unexpected frame acceptance");}
    static byte[] crc(byte[] data){var c=new CRC32();c.update(data,0,data.length-4);ByteBuffer.wrap(data).putInt(data.length-4,(int)c.getValue());return data;}
    static byte[] patch(byte[] bytes,int offset,int value){var copy=bytes.clone();ByteBuffer.wrap(copy).putInt(offset,value);return crc(copy);}
    public static void main(String[] args)throws Exception {
        var models=new ModelLibrary();var model=new SparseVoxelOctree(8);model.set(1,2,3,0xff123456);
        models.accept(new ModelLibrary.Entry(42,"synthetic",new ModelDefinition("unused",model)));
        models.acceptWorldEntry(new ModelLibrary.Entry(4096,"structure",new ModelDefinition("placed",model)));
        var whole=new SparseVoxelOctree(16);whole.fill(0,0,0,16,16,16,WorldVoxels.encode(4096));
        var fraction=new SparseVoxelOctree(16);fraction.fill(0,0,0,2,2,2,WorldVoxels.encode(Blocks.GLASS));
        var geometry=RegionHistoryPrototype.fromCells(126,Terrain.CURRENT_VERSION,List.of(new Protocol.CellState(0,128,0,whole.freeze()),new Protocol.CellState(1,128,0,new SparseVoxelOctree(16).freeze()),new Protocol.CellState(-1,128,-1,fraction.freeze())),new RegionHistoryPrototype.CodecBudget(8192,3,2,131_072),()->false);
        var inventory=new Inventory();var offline=RegionOfflineMetadataPrototype.capture(inventory,17,List.of(),models,()->false);
        var server=RegionServerMetadataPrototype.capture(List.of(new RegionServerMetadataPrototype.Player("Fixture",inventory,15)),List.of(),models,()->false);
        var cityBytes=new ByteArrayOutputStream();var cityOut=new DataOutputStream(cityBytes);cityOut.writeInt(0x43495441);CityFrame.empty(GameConfig.cityGame()).write(cityOut,17);
        var sidecars=RegionSidecarsPrototype.capture(cityBytes.toByteArray(),null,()->false);
        var base=RegionWorldLibraryPrototype.capture(geometry,models,()->false);var survival=RegionWorldLibraryPrototype.capture(geometry,models,offline,()->false);
        var candidates=List.of(base,survival,survival.withSidecars(sidecars,()->false),RegionWorldLibraryPrototype.captureServer(geometry,models,server,()->false).withSidecars(sidecars,()->false));
        long bytesTotal=0;int version=0;
        for(var candidate:candidates) {
            byte[] bytes=candidate.encode(()->false),saved=bytes.clone();bytesTotal+=bytes.length;version++;
            require(ByteBuffer.wrap(bytes).getInt(4)==version,"Fixture envelope version");
            var decoded=RegionWorldLibraryPrototype.decode(bytes,()->false);require(Arrays.equals(bytes,decoded.encode(()->false)),"Section framing changed bytes");
            var restored=decoded.restoreComplete(GameConfig.cityGame(),()->false);
            require(restored.world().cell(0,128,0).sameValues(whole)&&restored.world().cell(1,128,0).occupied()==0&&restored.world().cell(-1,128,-1).sameValues(fraction),"Geometry/air changed");
            require(restored.models().get(42)!=null&&restored.models().get(4096)!=null,"Library section lost used/unused models");
            if(version==2||version==3)require(restored.offline().orElseThrow().health()==17,"Offline metadata boundary consumed");
            if(version>=3)require(restored.city().orElseThrow().frame().config().city()&&Arrays.equals(decoded.sidecars().orElseThrow().city(),sidecars.city()),"City section changed");
            if(version==4)require(restored.server().orElseThrow().players().get(0).health()==15,"Server metadata boundary consumed");
            int librarySize=ByteBuffer.wrap(bytes).getInt(8),regionOffset=12+librarySize,regionSize=ByteBuffer.wrap(bytes).getInt(regionOffset);
            for(int size:new int[]{-1,librarySize-1,librarySize+1,RegionWorldLibraryPrototype.MAX_LIBRARY_BYTES+1})reject(IOException.class,()->RegionWorldLibraryPrototype.decode(patch(bytes,8,size),()->false));
            for(int size:new int[]{-1,regionSize-1,regionSize+1,8*1024*1024+1})reject(IOException.class,()->RegionWorldLibraryPrototype.decode(patch(bytes,regionOffset,size),()->false));
            var innerBad=bytes.clone();innerBad[regionOffset+4+regionSize-1]^=1;crc(innerBad);reject(IOException.class,()->RegionWorldLibraryPrototype.decode(innerBad,()->false));
            reject(CancellationException.class,()->RegionWorldLibraryPrototype.decode(bytes,()->true));
            require(Arrays.equals(bytes,saved)&&Arrays.equals(bytes,RegionWorldLibraryPrototype.decode(bytes,()->false).encode(()->false)),"Rejected parse changed source/retry");
        }
        System.out.println("{\"status\":\"passed\",\"versions\":4,\"rejection_cases\":"+rejects+",\"total_fixture_bytes\":"+bytesTotal+",\"byte_exact\":true,\"geometry_and_air\":true,\"library_preserved\":true,\"metadata_boundaries\":true,\"source_unchanged\":true,\"retry\":true,\"qualification\":false}");
    }
}
