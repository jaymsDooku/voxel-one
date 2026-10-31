import dev.jayms.net.*;
import dev.jayms.net.model.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.zip.CRC32;
import java.util.zip.CheckedOutputStream;

/** Detached physical-world/library envelope with optional offline survival fields. Not a production save path. */
public final class RegionWorldLibraryPrototype {
    static final int MAX_BYTES=16*1024*1024, MAX_LIBRARY_BYTES=8*1024*1024;
    private static final int MAGIC=0x5652574c, VERSION=1;
    private static final RegionHistoryPrototype.CodecBudget BUDGET=new RegionHistoryPrototype.CodecBudget(8*1024*1024,100_000,1024,131_072);
    private final RegionHistoryPrototype geometry;
    private final ModelLibrary library;
    private final RegionOfflineMetadataPrototype metadata;
    private RegionWorldLibraryPrototype(RegionHistoryPrototype geometry,ModelLibrary library){this(geometry,library,null);}
    private RegionWorldLibraryPrototype(RegionHistoryPrototype geometry,ModelLibrary library,RegionOfflineMetadataPrototype metadata){this.geometry=geometry;this.library=library;this.metadata=metadata;}
    public Optional<RegionOfflineMetadataPrototype> metadata(){return Optional.ofNullable(metadata);}
    RegionHistoryPrototype geometrySnapshot(){return geometry;}
    private static void check(BooleanSupplier cancel){if(Thread.currentThread().isInterrupted()||cancel.getAsBoolean())throw new CancellationException();}
    private static byte[] libraryBytes(ModelLibrary library,BooleanSupplier cancel)throws IOException {
        var sink=new RegionHistoryPrototype.LimitedOutput(MAX_LIBRARY_BYTES,cancel);
        library.writeAll(new DataOutputStream(sink));check(cancel);return sink.bytes.toByteArray();
    }
    private static ModelLibrary readLibrary(byte[] bytes,BooleanSupplier cancel)throws IOException {
        var raw=new ByteArrayInputStream(bytes);
        var input=new DataInputStream(new FilterInputStream(raw){
            @Override public int read()throws IOException{check(cancel);return super.read();}
            @Override public int read(byte[] data,int offset,int length)throws IOException{check(cancel);return in.read(data,offset,length);}
        });
        var result=ModelLibrary.readAll(input);if(input.read()!=-1)throw new IOException("Trailing library data");check(cancel);return result;
    }
    private static void references(RegionHistoryPrototype geometry,ModelLibrary library,BooleanSupplier cancel)throws IOException {
        for(var region:geometry.regions().values())for(var cell:region){check(cancel);cell.tree().forEachLeaf((x,y,z,side,color)->{
            check(cancel);int id=WorldVoxels.decode(color);
            if(Blocks.isModel(id)&&library.get(id)==null)throw new IOException("Missing world model");
        });}
    }
    /** Round-trip the bounded existing world codec to validate and detach every entry, including unused entries. */
    public static RegionWorldLibraryPrototype capture(RegionHistoryPrototype geometry,ModelLibrary library,BooleanSupplier cancel)throws IOException {
        Objects.requireNonNull(geometry);Objects.requireNonNull(library);Objects.requireNonNull(cancel);check(cancel);
        var frozen=readLibrary(libraryBytes(library,cancel),cancel);references(geometry,frozen,cancel);
        return new RegionWorldLibraryPrototype(geometry,frozen);
    }
    public static RegionWorldLibraryPrototype capture(RegionHistoryPrototype geometry,ModelLibrary library,RegionOfflineMetadataPrototype metadata,BooleanSupplier cancel)throws IOException {
        Objects.requireNonNull(metadata);var base=capture(geometry,library,cancel);metadata.validateModels(base.library,cancel);check(cancel);
        return new RegionWorldLibraryPrototype(base.geometry,base.library,metadata);
    }
    public byte[] encode(BooleanSupplier cancel)throws IOException {
        Objects.requireNonNull(cancel);check(cancel);byte[] models=libraryBytes(library,cancel),cells=geometry.encode(BUDGET,cancel);
        var sink=new RegionHistoryPrototype.LimitedOutput(MAX_BYTES,cancel);var crc=new CRC32();var out=new DataOutputStream(new CheckedOutputStream(sink,crc));
        out.writeInt(MAGIC);out.writeInt(metadata==null?VERSION:2);out.writeInt(models.length);out.write(models);out.writeInt(cells.length);out.write(cells);
        if(metadata!=null){byte[] survival=metadata.encode(cancel);out.writeInt(survival.length);out.write(survival);}
        out.flush();new DataOutputStream(sink).writeInt((int)crc.getValue());
        byte[] result=sink.bytes.toByteArray();check(cancel);return result;
    }
    public static RegionWorldLibraryPrototype decode(byte[] bytes,BooleanSupplier cancel)throws IOException {
        Objects.requireNonNull(bytes);Objects.requireNonNull(cancel);check(cancel);
        if(bytes.length<52||bytes.length>MAX_BYTES)throw new IOException("World envelope byte budget");
        var crc=new CRC32();for(int offset=0;offset<bytes.length-4;offset+=65536){check(cancel);crc.update(bytes,offset,Math.min(65536,bytes.length-4-offset));}
        var tail=new DataInputStream(new ByteArrayInputStream(bytes,bytes.length-4,4));
        if((int)crc.getValue()!=tail.readInt())throw new IOException("World envelope CRC");
        var raw=new ByteArrayInputStream(bytes,0,bytes.length-4);var in=new DataInputStream(raw);
        if(in.readInt()!=MAGIC)throw new IOException("World envelope format/version");
        int version=in.readInt();if(version!=1&&version!=2)throw new IOException("World envelope format/version");
        int size=in.readInt();if(size<4||size>MAX_LIBRARY_BYTES||size>raw.available()-4)throw new IOException("Library byte budget");
        var library=readLibrary(in.readNBytes(size),cancel);
        size=in.readInt();if(size<28||size>BUDGET.bytes()||(version==1?size!=raw.available():size>raw.available()-4))throw new IOException("World region byte budget");
        var geometry=RegionHistoryPrototype.decode(in.readNBytes(size),BUDGET,cancel);references(geometry,library,cancel);
        RegionOfflineMetadataPrototype metadata=null;
        if(version==2){size=in.readInt();if(size<89||size>RegionOfflineMetadataPrototype.MAX_BYTES||size!=raw.available())throw new IOException("Metadata byte budget");metadata=RegionOfflineMetadataPrototype.decode(in.readNBytes(size),library,cancel);}
        check(cancel);return new RegionWorldLibraryPrototype(geometry,library,metadata);
    }
    public record Restored(WorldVoxels world,ModelLibrary models){}
    /** Exact saved IDs; does not merge into another world's library or mutate either input. */
    public Restored restore(BooleanSupplier cancel)throws IOException {
        Objects.requireNonNull(cancel);check(cancel);var world=new WorldVoxels(new Terrain(geometry.terrainSeed(),geometry.terrainVersion()));
        for(var region:geometry.regions().values())for(var cell:region){check(cancel);world.replaceCell(cell.x(),cell.y(),cell.z(),cell.tree());}
        check(cancel);return new Restored(world,library.copy());
    }
}
