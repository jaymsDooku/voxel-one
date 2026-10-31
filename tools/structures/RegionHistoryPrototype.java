import dev.jayms.ChunkPos;
import dev.jayms.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.zip.*;

/** Private migration experiment, not a save format or production history replacement. */
public final class RegionHistoryPrototype {
    public record CodecBudget(int bytes,int cells,int regions,int leaves) {
        public CodecBudget {
            if(bytes<28||bytes>8*1024*1024||cells<0||cells>100_000||regions<0||regions>1024||leaves<0||leaves>131_072)
                throw new IllegalArgumentException("Diagnostic codec budget");
        }
    }
    private static final int MAGIC=0x56524850,VERSION=1;
    public record Budget(int edits,int cells,int regions) {
        public Budget {
            if(edits<1||edits>200_000||cells<1||cells>100_000||regions<1||regions>1024)
                throw new IllegalArgumentException("Diagnostic budget");
        }
    }
    private final Map<ChunkPos,List<Protocol.CellState>> regions;
    private final long seed;
    private final int terrainVersion;
    private RegionHistoryPrototype(long seed,int terrainVersion,Map<ChunkPos,List<Protocol.CellState>> regions) {
        this.seed=seed;this.terrainVersion=terrainVersion;
        this.regions=Collections.unmodifiableMap(regions);
    }
    public Map<ChunkPos,List<Protocol.CellState>> regions(){return regions;}
    static final class LimitedOutput extends OutputStream {
        final ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        final int maximum;final BooleanSupplier cancel;int count;
        LimitedOutput(int maximum,BooleanSupplier cancel){this.maximum=maximum;this.cancel=cancel;}
        @Override public void write(int value)throws IOException {
            checkpoint(cancel);if(count>=maximum)throw new IOException("Encoded byte budget");
            bytes.write(value);count++;
        }
        @Override public void write(byte[] data,int offset,int length)throws IOException {
            Objects.checkFromIndexSize(offset,length,data.length);checkpoint(cancel);
            if(length>maximum-count)throw new IOException("Encoded byte budget");
            bytes.write(data,offset,length);count+=length;
        }
    }
    /** Private uncompressed diagnostic wire; not accepted by any game importer. */
    public byte[] encode(CodecBudget budget,BooleanSupplier cancel)throws IOException {
        Objects.requireNonNull(budget);Objects.requireNonNull(cancel);checkpoint(cancel);
        if(regions.size()>budget.regions())throw new IOException("Region budget");
        var sink=new LimitedOutput(budget.bytes(),cancel);var crc=new CRC32();
        var out=new DataOutputStream(new CheckedOutputStream(sink,crc));
        out.writeInt(MAGIC);out.writeInt(VERSION);out.writeLong(seed);out.writeInt(terrainVersion);out.writeInt(regions.size());
        int cells=0;long leaves=0;
        for(var entry:regions.entrySet()) {
            checkpoint(cancel);var region=entry.getKey();var records=entry.getValue();
            if(records.isEmpty()||records.size()>4096||records.size()>budget.cells()-cells)throw new IOException("Cell budget");
            cells+=records.size();out.writeInt(region.chunkX());out.writeInt(region.chunkY());out.writeInt(region.chunkZ());out.writeShort(records.size());
            for(var cell:records) {
                checkpoint(cancel);leaves+=cell.tree().leafCount();if(leaves>budget.leaves())throw new IOException("Leaf budget");
                cell.tree().forEachLeaf((x,y,z,side,color)->{
                    checkpoint(cancel);int material=WorldVoxels.decode(color);
                    if(!Blocks.valid(material)||Blocks.isPiece(material)||material==0||Blocks.isModel(material)&&side!=16)
                        throw new IOException("Invalid world material");
                });
                out.writeShort(localIndex(cell));cell.write(out);
            }
        }
        out.flush();new DataOutputStream(sink).writeInt((int)crc.getValue());checkpoint(cancel);
        var result=sink.bytes.toByteArray();checkpoint(cancel);return result;
    }
    private static int localIndex(Protocol.CellState cell) {
        return (Math.floorMod(cell.y(),16)*16+Math.floorMod(cell.z(),16))*16+Math.floorMod(cell.x(),16);
    }
    /** Decode into a private result. Bounds precede node parsing; no live world is touched. */
    public static RegionHistoryPrototype decode(byte[] bytes,CodecBudget budget,BooleanSupplier cancel)throws IOException {
        Objects.requireNonNull(bytes);Objects.requireNonNull(budget);Objects.requireNonNull(cancel);checkpoint(cancel);
        if(bytes.length<28||bytes.length>budget.bytes())throw new IOException("Encoded byte budget");
        var raw=new ByteArrayInputStream(bytes,0,bytes.length-4);var crc=new CRC32();
        var checked=new CheckedInputStream(raw,crc);
        var input=new FilterInputStream(checked) {
            @Override public int read()throws IOException{checkpoint(cancel);return super.read();}
            @Override public int read(byte[] data,int offset,int length)throws IOException{checkpoint(cancel);return in.read(data,offset,length);}
        };
        var in=new DataInputStream(input);
        if(in.readInt()!=MAGIC||in.readInt()!=VERSION)throw new IOException("Diagnostic format/version");
        long seed=in.readLong();int version=in.readInt();
        if(version<Terrain.LEGACY_VERSION||version>Terrain.CURRENT_VERSION)throw new IOException("Terrain version");
        int count=in.readInt();if(count<0||count>budget.regions())throw new IOException("Region budget");
        var result=new LinkedHashMap<ChunkPos,List<Protocol.CellState>>();int cells=0;long leaves=0;
        for(int i=0;i<count;i++) {
            checkpoint(cancel);var region=new ChunkPos(in.readInt(),in.readInt(),in.readInt());
            if(region.chunkX()<Math.floorDiv(-Terrain.LIMIT,16)||region.chunkX()>Math.floorDiv(Terrain.LIMIT,16)
                    ||region.chunkZ()<Math.floorDiv(-Terrain.LIMIT,16)||region.chunkZ()>Math.floorDiv(Terrain.LIMIT,16)
                    ||region.chunkY()<Math.floorDiv(Protocol.MIN_BUILD_Y,16)||region.chunkY()>Math.floorDiv(Protocol.MAX_BUILD_Y,16)
                    ||result.containsKey(region))throw new IOException("Invalid/duplicate region");
            int size=in.readUnsignedShort();if(size<1||size>4096||size>budget.cells()-cells)throw new IOException("Cell budget");
            cells+=size;var records=new ArrayList<Protocol.CellState>(size);int previous=-1;
            for(int c=0;c<size;c++) {
                checkpoint(cancel);int index=in.readUnsignedShort();
                if(index<=previous||index>=4096)throw new IOException("Invalid/duplicate cell order");
                previous=index;var cell=Protocol.CellState.read(in);
                if(!ChunkPos.fromBlock(cell.x(),cell.y(),cell.z()).equals(region)||localIndex(cell)!=index)throw new IOException("Cell outside region");
                leaves+=cell.tree().leafCount();if(leaves>budget.leaves())throw new IOException("Leaf budget");
                records.add(cell);
            }
            result.put(region,List.copyOf(records));
        }
        if(in.read()!=-1)throw new IOException("Trailing diagnostic data");
        int expected=new DataInputStream(new ByteArrayInputStream(bytes,bytes.length-4,4)).readInt();
        if((int)crc.getValue()!=expected)throw new IOException("Diagnostic CRC");
        checkpoint(cancel);return new RegionHistoryPrototype(seed,version,result);
    }
    private static void checkpoint(BooleanSupplier cancel) {
        if(Thread.currentThread().isInterrupted()||cancel.getAsBoolean())throw new CancellationException();
    }
    /** Source must be a detached replay in canonical order, used only by this worker. */
    public static RegionHistoryPrototype migrate(long seed,int terrainVersion,Iterable<Protocol.Edit> source,Budget budget,BooleanSupplier cancel) {
        Objects.requireNonNull(source);Objects.requireNonNull(budget);Objects.requireNonNull(cancel);
        var world=new WorldVoxels(new Terrain(seed,terrainVersion));
        var touched=new LinkedHashMap<ChunkPos,BitSet>();int edits=0,cells=0;
        checkpoint(cancel);
        for(var edit:source) {
            checkpoint(cancel);
            if(edit==null||!edit.valid())throw new IllegalArgumentException("Invalid source edit");
            if(++edits>budget.edits())throw new IllegalArgumentException("Edit budget");
            var region=ChunkPos.fromBlock(edit.x(),edit.y(),edit.z());
            var bits=touched.get(region);
            if(bits==null) {
                if(touched.size()>=budget.regions())throw new IllegalArgumentException("Region budget");
                bits=new BitSet(4096);touched.put(region,bits);
            }
            int index=(Math.floorMod(edit.y(),16)*16+Math.floorMod(edit.z(),16))*16+Math.floorMod(edit.x(),16);
            if(!bits.get(index)) {
                if(++cells>budget.cells())throw new IllegalArgumentException("Cell budget");
                bits.set(index);
            }
            world.apply(edit);
        }
        var result=new LinkedHashMap<ChunkPos,List<Protocol.CellState>>();
        for(var entry:touched.entrySet()) {
            checkpoint(cancel);var region=entry.getKey();var records=new ArrayList<Protocol.CellState>();
            for(int index=entry.getValue().nextSetBit(0);index>=0;index=entry.getValue().nextSetBit(index+1)) {
                checkpoint(cancel);
                int x=region.chunkX()*16+index%16,y=region.chunkY()*16+index/256,z=region.chunkZ()*16+(index/16)%16;
                // Retain explicit air. Absence would restore implicit seeded terrain.
                records.add(new Protocol.CellState(x,y,z,world.cell(x,y,z).copy().freeze()));
            }
            result.put(region,List.copyOf(records));
        }
        checkpoint(cancel);
        return new RegionHistoryPrototype(seed,terrainVersion,result);
    }
    /** Diagnostic replay into a private world only; never call on a live authority. */
    public WorldVoxels replay() {
        var world=new WorldVoxels(new Terrain(seed,terrainVersion));
        for(var records:regions.values())for(var cell:records)world.replaceCell(cell.x(),cell.y(),cell.z(),cell.tree());
        return world;
    }
}
