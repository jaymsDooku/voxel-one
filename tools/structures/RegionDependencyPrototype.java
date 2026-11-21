import dev.jayms.net.*;
import dev.jayms.net.model.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.zip.*;

/** Detached diagnostic manifest. No production file format or live-world importer. */
public final class RegionDependencyPrototype {
    static final int MAX_BYTES=16*1024*1024,MAX_DEFINITION_BYTES=8*1024*1024,MAX_DEFINITIONS=512,MAX_BINDINGS=1024;
    private static final int MAGIC=0x56524450,VERSION=1;
    private static final RegionHistoryPrototype.CodecBudget REGION_BUDGET=new RegionHistoryPrototype.CodecBudget(8*1024*1024,100_000,1024,131_072);
    private final RegionHistoryPrototype geometry;
    private final Map<Integer,ModelDefinition> bindings;
    private RegionDependencyPrototype(RegionHistoryPrototype geometry,Map<Integer,ModelDefinition> bindings){this.geometry=geometry;this.bindings=Collections.unmodifiableMap(bindings);}
    public Map<Integer,ModelDefinition> bindings(){return bindings;}
    RegionHistoryPrototype geometrySnapshot(){return geometry;}
    private static void check(BooleanSupplier cancel){if(Thread.currentThread().isInterrupted()||cancel.getAsBoolean())throw new CancellationException();}
    private static Set<Integer> references(RegionHistoryPrototype geometry,BooleanSupplier cancel)throws IOException {
        var ids=new TreeSet<Integer>();
        for(var region:geometry.regions().values())for(var cell:region) {
            check(cancel);cell.tree().forEachLeaf((x,y,z,side,color)->{
                check(cancel);int material=WorldVoxels.decode(color);
                if(Blocks.isModel(material)) {
                    if(side!=16)throw new IOException("Fractional model reference");
                    ids.add(material);if(ids.size()>MAX_BINDINGS)throw new IOException("Binding budget");
                }
            });
        }
        return ids;
    }
    private static byte[] definitionBytes(ModelDefinition definition)throws IOException {
        var bytes=new ByteArrayOutputStream();definition.write(new DataOutputStream(bytes));return bytes.toByteArray();
    }
    /** Source definitions must be detached; no authors or account fields are captured. */
    public static RegionDependencyPrototype capture(RegionHistoryPrototype geometry,Map<Integer,ModelDefinition> definitions,BooleanSupplier cancel)throws IOException {
        Objects.requireNonNull(geometry);Objects.requireNonNull(definitions);Objects.requireNonNull(cancel);check(cancel);
        var result=new LinkedHashMap<Integer,ModelDefinition>();var unique=new HashMap<String,ModelDefinition>();long bytes=0;
        for(int id:references(geometry,cancel)) {
            check(cancel);var source=definitions.get(id);if(source==null)throw new IOException("Missing model dependency");
            var frozen=new ModelDefinition(source.name(),source.voxels().copy().freeze());
            if(frozen.voxels().occupied()==0)throw new IOException("Empty dependency");
            String fingerprint=frozen.fingerprint();var canonical=unique.get(fingerprint);
            if(canonical==null) {
                if(unique.size()>=MAX_DEFINITIONS)throw new IOException("Definition budget");
                bytes+=definitionBytes(frozen).length;if(bytes>MAX_DEFINITION_BYTES)throw new IOException("Definition byte budget");
                ModelMesher.count(frozen.voxels());check(cancel);unique.put(fingerprint,frozen);canonical=frozen;
            }
            result.put(id,canonical);
        }
        check(cancel);return new RegionDependencyPrototype(geometry,result);
    }
    public byte[] encode(BooleanSupplier cancel)throws IOException {
        Objects.requireNonNull(cancel);check(cancel);
        var indices=new LinkedHashMap<String,Integer>();var definitions=new ArrayList<ModelDefinition>();
        for(var model:bindings.values()){check(cancel);String fingerprint=model.fingerprint();if(!indices.containsKey(fingerprint)){indices.put(fingerprint,definitions.size());definitions.add(model);}}
        var sink=new RegionHistoryPrototype.LimitedOutput(MAX_BYTES,cancel);var crc=new CRC32();
        var out=new DataOutputStream(new CheckedOutputStream(sink,crc));out.writeInt(MAGIC);out.writeInt(VERSION);out.writeInt(definitions.size());
        for(var model:definitions){check(cancel);out.write(HexFormat.of().parseHex(model.fingerprint()));model.write(out);}
        out.writeInt(bindings.size());
        for(var entry:bindings.entrySet()){check(cancel);out.writeInt(entry.getKey());out.writeShort(indices.get(entry.getValue().fingerprint()));}
        var region=geometry.encode(REGION_BUDGET,cancel);out.writeInt(region.length);out.write(region);out.flush();
        new DataOutputStream(sink).writeInt((int)crc.getValue());var result=sink.bytes.toByteArray();check(cancel);return result;
    }
    public static RegionDependencyPrototype decode(byte[] bytes,BooleanSupplier cancel)throws IOException {
        Objects.requireNonNull(bytes);Objects.requireNonNull(cancel);check(cancel);
        if(bytes.length<24||bytes.length>MAX_BYTES)throw new IOException("Manifest byte budget");
        var raw=new ByteArrayInputStream(bytes,0,bytes.length-4);var crc=new CRC32();var checked=new CheckedInputStream(raw,crc);
        var in=new DataInputStream(new FilterInputStream(checked){
            @Override public int read()throws IOException{check(cancel);return super.read();}
            @Override public int read(byte[] data,int offset,int length)throws IOException{check(cancel);return this.in.read(data,offset,length);}
        });
        if(in.readInt()!=MAGIC||in.readInt()!=VERSION)throw new IOException("Manifest format/version");
        int count=in.readInt();if(count<0||count>MAX_DEFINITIONS)throw new IOException("Definition budget");
        var definitions=new ArrayList<ModelDefinition>();var fingerprints=new HashSet<String>();int definitionBytes=0;
        for(int i=0;i<count;i++) {
            check(cancel);byte[] expected=in.readNBytes(32);if(expected.length!=32)throw new EOFException();
            int before=raw.available();var model=ModelDefinition.read(in);definitionBytes+=before-raw.available();
            if(definitionBytes>MAX_DEFINITION_BYTES)throw new IOException("Definition byte budget");
            String fingerprint=model.fingerprint();
            if(!fingerprint.equals(HexFormat.of().formatHex(expected))||!fingerprints.add(fingerprint)||model.voxels().occupied()==0)throw new IOException("Dependency identity");
            ModelMesher.count(model.voxels());check(cancel);definitions.add(new ModelDefinition(model.name(),model.voxels().copy().freeze()));
        }
        int size=in.readInt();if(size<0||size>MAX_BINDINGS)throw new IOException("Binding budget");
        var bindings=new LinkedHashMap<Integer,ModelDefinition>();var used=new BitSet(count);int previous=-1;
        for(int i=0;i<size;i++) {
            check(cancel);int id=in.readInt(),index=in.readUnsignedShort();
            if(!Blocks.isModel(id)||id<=previous||index>=count)throw new IOException("Invalid/duplicate binding");
            previous=id;bindings.put(id,definitions.get(index));used.set(index);
        }
        if(used.cardinality()!=count)throw new IOException("Unused definition");
        int length=in.readInt();if(length<28||length>REGION_BUDGET.bytes()||length>raw.available())throw new IOException("Region byte budget");
        var region=in.readNBytes(length);if(region.length!=length)throw new EOFException();
        if(in.read()!=-1)throw new IOException("Trailing manifest data");
        int expected=new DataInputStream(new ByteArrayInputStream(bytes,bytes.length-4,4)).readInt();if((int)crc.getValue()!=expected)throw new IOException("Manifest CRC");
        var geometry=RegionHistoryPrototype.decode(region,REGION_BUDGET,cancel);
        if(!references(geometry,cancel).equals(bindings.keySet()))throw new IOException("Missing/extra model bindings");
        check(cancel);return new RegionDependencyPrototype(geometry,bindings);
    }
    public record Restored(WorldVoxels world,ModelLibrary models){}
    /** Stage into private copies only. This does not publish to a live world. */
    public Restored restore(ModelLibrary destination,BooleanSupplier cancel)throws IOException {
        Objects.requireNonNull(destination);Objects.requireNonNull(cancel);check(cancel);
        var models=destination.copy();var remap=new HashMap<Integer,Integer>();
        for(var entry:bindings.entrySet()){check(cancel);remap.put(entry.getKey(),models.stageStructureModel(entry.getValue()).id());}
        var world=geometry.replay();var trees=new HashMap<Integer,SparseVoxelOctree>();
        for(var region:geometry.regions().values())for(var cell:region) {
            check(cancel);int id=WorldVoxels.decode(cell.tree().uniform(0,0,0,16));
            if(Blocks.isModel(id)) {
                int mapped=remap.get(id);var tree=trees.get(mapped);
                if(tree==null){tree=new SparseVoxelOctree(16);tree.fill(0,0,0,16,16,16,WorldVoxels.encode(mapped));tree.freeze();trees.put(mapped,tree);}
                world.replaceCell(cell.x(),cell.y(),cell.z(),tree);
            }
        }
        check(cancel);return new Restored(world,models);
    }
}
