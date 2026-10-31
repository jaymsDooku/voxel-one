import dev.jayms.net.*;
import dev.jayms.net.model.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.zip.CRC32;
import java.util.zip.CheckedOutputStream;

/** Detached physical-world/library envelope with optional offline or server survival fields. Not a production save path. */
public final class RegionWorldLibraryPrototype {
    static final int MAX_BYTES=16*1024*1024, MAX_LIBRARY_BYTES=8*1024*1024;
    private static final int MAGIC=0x5652574c, VERSION=1;
    private static final RegionHistoryPrototype.CodecBudget BUDGET=new RegionHistoryPrototype.CodecBudget(8*1024*1024,100_000,1024,131_072);
    private final RegionHistoryPrototype geometry;
    private final ModelLibrary library;
    private final RegionOfflineMetadataPrototype metadata;
    private final RegionSidecarsPrototype sidecars;
    private final RegionServerMetadataPrototype serverMetadata;
    private RegionWorldLibraryPrototype(RegionHistoryPrototype geometry,ModelLibrary library){this(geometry,library,null);}
    private RegionWorldLibraryPrototype(RegionHistoryPrototype geometry,ModelLibrary library,RegionOfflineMetadataPrototype metadata){this(geometry,library,metadata,null);}
    private RegionWorldLibraryPrototype(RegionHistoryPrototype geometry,ModelLibrary library,RegionOfflineMetadataPrototype metadata,RegionSidecarsPrototype sidecars){this(geometry,library,metadata,sidecars,null);}
    private RegionWorldLibraryPrototype(RegionHistoryPrototype geometry,ModelLibrary library,RegionOfflineMetadataPrototype metadata,RegionSidecarsPrototype sidecars,RegionServerMetadataPrototype serverMetadata){this.geometry=geometry;this.library=library;this.metadata=metadata;this.sidecars=sidecars;this.serverMetadata=serverMetadata;}
    public Optional<RegionServerMetadataPrototype> serverMetadata(){return Optional.ofNullable(serverMetadata);}
    public int survivalKind(){return serverMetadata!=null?2:metadata!=null?1:0;}
    public Optional<RegionSidecarsPrototype> sidecars(){return Optional.ofNullable(sidecars);}
    public RegionWorldLibraryPrototype withSidecars(RegionSidecarsPrototype value,BooleanSupplier cancel)throws IOException{Objects.requireNonNull(value);check(cancel);if(metadata==null&&serverMetadata==null)throw new IOException("Survival metadata required with sidecars");return new RegionWorldLibraryPrototype(geometry,library,metadata,value,serverMetadata);}
    public Optional<RegionOfflineMetadataPrototype> metadata(){return Optional.ofNullable(metadata);}
    RegionHistoryPrototype geometrySnapshot(){return geometry;}
    private static void check(BooleanSupplier cancel){if(Thread.currentThread().isInterrupted()||cancel.getAsBoolean())throw new CancellationException();}
    private static byte[] libraryBytes(ModelLibrary library,BooleanSupplier cancel)throws IOException {
        var sink=new RegionHistoryPrototype.LimitedOutput(MAX_LIBRARY_BYTES,cancel);
        library.writeAll(new DataOutputStream(sink));check(cancel);return sink.bytes.toByteArray();
    }
    private static ModelLibrary readLibrary(byte[] bytes,BooleanSupplier cancel)throws IOException {
        return readLibrary(new ByteArrayInputStream(bytes),cancel);
    }
    private static ModelLibrary readLibrary(InputStream raw,BooleanSupplier cancel)throws IOException {
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
    public static RegionWorldLibraryPrototype captureServer(RegionHistoryPrototype geometry,ModelLibrary library,RegionServerMetadataPrototype metadata,BooleanSupplier cancel)throws IOException {
        Objects.requireNonNull(metadata);var base=capture(geometry,library,cancel);metadata.validateModels(base.library,cancel);check(cancel);
        return new RegionWorldLibraryPrototype(base.geometry,base.library,null,null,metadata);
    }
    public byte[] encode(BooleanSupplier cancel)throws IOException {
        Objects.requireNonNull(cancel);check(cancel);byte[] models=libraryBytes(library,cancel),cells=geometry.encode(BUDGET,cancel);
        var sink=new RegionHistoryPrototype.LimitedOutput(MAX_BYTES,cancel);var crc=new CRC32();var out=new DataOutputStream(new CheckedOutputStream(sink,crc));
        out.writeInt(MAGIC);out.writeInt(serverMetadata!=null?4:sidecars!=null?3:metadata==null?VERSION:2);out.writeInt(models.length);out.write(models);out.writeInt(cells.length);out.write(cells);
        if(metadata!=null){byte[] survival=metadata.encode(cancel);out.writeInt(survival.length);out.write(survival);}
        if(serverMetadata!=null){byte[] data=serverMetadata.encode(cancel);out.writeInt(data.length);out.write(data);}
        if(sidecars!=null){byte[] data=sidecars.encode(cancel);out.writeInt(data.length);out.write(data);}else if(serverMetadata!=null)out.writeInt(0);
        out.flush();new DataOutputStream(sink).writeInt((int)crc.getValue());
        byte[] result=sink.bytes.toByteArray();check(cancel);return result;
    }
    /** Bounded view over the CRC-checked envelope, not another encoded-section copy.
     * Parent excludes its four-byte CRC; all callers validate their section budget first. */
    private static InputStream section(byte[] bytes,int bodyEnd,ByteArrayInputStream parent,int size)throws IOException {
        if(size<0||size>parent.available())throw new IOException("World section byte budget");
        int offset=bodyEnd-parent.available();
        var view=new ByteArrayInputStream(bytes,offset,size);parent.skipNBytes(size);return view;
    }
    public static RegionWorldLibraryPrototype decode(byte[] bytes,BooleanSupplier cancel)throws IOException {
        Objects.requireNonNull(bytes);return decode(bytes,0,bytes.length,cancel);
    }
    /** Decode one bounded frame inside a caller-owned array. Prefix/suffix bytes are
     * excluded from budgets, CRC and section views. Caller must not mutate during decode. */
    public static RegionWorldLibraryPrototype decode(byte[] bytes,int offset,int length,BooleanSupplier cancel)throws IOException {
        Objects.requireNonNull(bytes);Objects.requireNonNull(cancel);check(cancel);
        if(offset<0||length<52||length>MAX_BYTES||offset>bytes.length-length)throw new IOException("World envelope byte budget/range");
        int bodyEnd=offset+length-4;
        var crc=new CRC32();int cursor=offset,remaining=length-4;
        while(remaining>0){check(cancel);int n=Math.min(65536,remaining);crc.update(bytes,cursor,n);cursor+=n;remaining-=n;}
        if((int)crc.getValue()!=java.nio.ByteBuffer.wrap(bytes).getInt(bodyEnd))throw new IOException("World envelope CRC");
        var raw=new ByteArrayInputStream(bytes,offset,length-4);var in=new DataInputStream(raw);
        if(in.readInt()!=MAGIC)throw new IOException("World envelope format/version");
        int version=in.readInt();if(version<1||version>4)throw new IOException("World envelope format/version");
        int size=in.readInt();if(size<4||size>MAX_LIBRARY_BYTES||size>raw.available()-4)throw new IOException("Library byte budget");
        var library=readLibrary(section(bytes,bodyEnd,raw,size),cancel);
        size=in.readInt();if(size<28||size>BUDGET.bytes()||(version==1?size!=raw.available():size>raw.available()-4))throw new IOException("World region byte budget");
        var geometry=RegionHistoryPrototype.decode(section(bytes,bodyEnd,raw,size),BUDGET,cancel);references(geometry,library,cancel);
        RegionOfflineMetadataPrototype metadata=null;
        if(version==2||version==3){size=in.readInt();if(size<89||size>RegionOfflineMetadataPrototype.MAX_BYTES||(version==2?size!=raw.available():size>raw.available()-4))throw new IOException("Metadata byte budget");metadata=RegionOfflineMetadataPrototype.decode(in.readNBytes(size),library,cancel);}
        RegionServerMetadataPrototype serverMetadata=null;
        if(version==4){size=in.readInt();if(size<20||size>RegionServerMetadataPrototype.MAX_BYTES||size>raw.available()-4)throw new IOException("Server metadata byte budget");serverMetadata=RegionServerMetadataPrototype.decode(in.readNBytes(size),library,cancel);}
        RegionSidecarsPrototype sidecars=null;
        if(version==4){size=in.readInt();if(size<0||size>RegionSidecarsPrototype.MAX_BYTES||size!=raw.available())throw new IOException("Sidecar byte budget");if(size!=0)sidecars=RegionSidecarsPrototype.decode(in.readNBytes(size),cancel);}
        if(version==3){size=in.readInt();if(size<24||size>RegionSidecarsPrototype.MAX_BYTES||size!=raw.available())throw new IOException("Sidecar byte budget");sidecars=RegionSidecarsPrototype.decode(in.readNBytes(size),cancel);}
        check(cancel);return new RegionWorldLibraryPrototype(geometry,library,metadata,sidecars,serverMetadata);
    }
    public record CompleteRestored(WorldVoxels world,ModelLibrary models,Optional<RegionOfflineMetadataPrototype> offline,Optional<RegionServerMetadataPrototype> server,Optional<RegionCityRestorePrototype.Restored> city){}
    /** Detached candidate only. Strict city/history decode completes before geometry restoration. */
    public CompleteRestored restoreComplete(BooleanSupplier cancel)throws IOException {
        return restoreComplete(null,cancel);
    }
    /** Expected game mode follows LocalGame policy. Absence of legacy sidecars remains valid. */
    public CompleteRestored restoreComplete(dev.jayms.net.city.GameConfig expected,BooleanSupplier cancel)throws IOException {
        Objects.requireNonNull(cancel);check(cancel);
        var city=sidecars==null?Optional.<RegionCityRestorePrototype.Restored>empty():Optional.of(RegionCityRestorePrototype.decode(sidecars,cancel));
        if(expected!=null&&city.isPresent()&&city.orElseThrow().frame().config().city()!=expected.city())
            throw new IOException("World belongs to another game; select that game or use a different world path");
        var physical=restore(cancel);check(cancel);return new CompleteRestored(physical.world(),physical.models(),metadata(),serverMetadata(),city);
    }
    public record Restored(WorldVoxels world,ModelLibrary models){}
    /** Exact saved IDs; does not merge into another world's library or mutate either input. */
    public Restored restore(BooleanSupplier cancel)throws IOException {
        Objects.requireNonNull(cancel);check(cancel);var world=new WorldVoxels(new Terrain(geometry.terrainSeed(),geometry.terrainVersion()));
        for(var region:geometry.regions().values())for(var cell:region){check(cancel);world.replaceCell(cell.x(),cell.y(),cell.z(),cell.tree());}
        check(cancel);return new Restored(world,library.copy());
    }
}
