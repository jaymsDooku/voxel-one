package dev.jayms.net.structure;

import dev.jayms.net.*;
import dev.jayms.net.model.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.*;
import java.util.zip.*;

/** Experimental, immutable physical construction. No entities, inventory or simulation state. */
public final class StructurePackage {
    public static final int MAGIC=0x56535452, VERSION=2;
    public static final long COMPRESSED=64L*1024*1024, DECODED=512L*1024*1024;
    public static final int MAX_CELLS=4_000_000, MAX_MODELS=512;
    // Development safety caps, NOT supported-city/performance qualifications.
    public static final int MAX_LEAVES=131_072, MAX_INSTANCES=16_384;
    public static final long MAX_DEPENDENCY_QUADS=1_048_576, MAX_INSTANCE_QUADS=1_048_576;
    public record Bounds(int x,int y,int z,int ax,int ay,int az) {
        public Bounds {
            if(x<1||x>1024||y<1||y>256||z<1||z>1024||ax<0||ax>=x||ay<0||ay>=y||az<0||az>=z)
                throw new IllegalArgumentException("Structure dimensions/anchor outside limits");
        }
    }
    public record Position(int x,int y,int z) {}
    public record Cell(Position position,SparseVoxelOctree geometry,int model) {}
    public interface Source {
        SparseVoxelOctree cell(int x,int y,int z);
        /** Conservative snapshot hint: true means empty; false may mean unknown. */
        default boolean emptyCell(int x,int y,int z){return false;}
        ModelLibrary models();
    }
    public final String name;
    public final Bounds bounds;
    public final List<ModelDefinition> models;
    public final List<Cell> cells;
    public final int leafCount, instanceCount;

    public StructurePackage(String name,Bounds bounds,List<ModelDefinition> models,List<Cell> cells) throws IOException {
        this(name,bounds,models,cells,null,()->false);
    }
    /** Only capture/decode may supply a buffer of detached, frozen trees. All validation still runs. */
    private StructurePackage(String name,Bounds bounds,List<ModelDefinition> models,StructureCells.Builder owned) throws IOException {
        this(name,bounds,models,owned.view(),owned,()->false);
    }
    private StructurePackage(String name,Bounds bounds,List<ModelDefinition> models,StructureCells.Builder owned,BooleanSupplier cancel) throws IOException {
        this(name,bounds,models,owned.view(),owned,cancel);
    }
    private StructurePackage(String name,Bounds bounds,List<ModelDefinition> models,List<Cell> cells,StructureCells.Builder owned,BooleanSupplier cancel) throws IOException {
        if(name==null||!name.matches("[A-Za-z0-9 _-]{1,48}"))throw new IOException("Use 1-48 letters, digits, spaces, underscores or dashes");
        if(bounds==null||models.size()>MAX_MODELS||cells.isEmpty()||cells.size()>MAX_CELLS)throw new IOException("Structure count limit");
        this.name=name;this.bounds=bounds;
        var definitions=new ArrayList<ModelDefinition>();var identities=new HashSet<String>();
        int[] quads=new int[models.size()];long dependencyQuads=0,instanceQuads=0;
        for(var model:models) {
            cancelled(cancel);
            var frozen=new ModelDefinition(model.name(),model.voxels().copy().freeze());
            if(frozen.voxels().occupied()==0||!identities.add(frozen.fingerprint()))throw new IOException("Empty or duplicate dependency");
            int faces=ModelMesher.mesh(frozen.voxels()).quads();quads[definitions.size()]=faces;dependencyQuads+=faces;
            if(dependencyQuads>MAX_DEPENDENCY_QUADS)throw new IOException("Experimental dependency surface cap");
            definitions.add(frozen);
        }
        this.models=List.copyOf(definitions);
        var entries=owned==null?new StructureCells.Builder(cells.size()):owned;int leaves=0,instances=0;
        for(var cell:cells) {
            cancelled(cancel);
            var p=cell.position();
            if(p.x<0||p.x>=bounds.x||p.y<0||p.y>=bounds.y||p.z<0||p.z>=bounds.z)throw new IOException("Invalid or duplicate structure cell");
            if(cell.model>=0) {
                if(cell.geometry!=null||cell.model>=models.size()||++instances>MAX_INSTANCES)throw new IOException("Structure instance limit/reference");
                instanceQuads+=quads[cell.model];if(instanceQuads>MAX_INSTANCE_QUADS)throw new IOException("Experimental instance surface cap");
                if(owned==null)entries.add(p,null,cell.model);
            } else {
                if(cell.model!=-1||cell.geometry==null||cell.geometry.size()!=16||cell.geometry.occupied()==0)throw new IOException("Invalid structure geometry");
                var tree=owned==null?cell.geometry.copy().freeze():cell.geometry;
                for(var leaf:tree.leaves()) {
                    int type=WorldVoxels.decode(leaf.color());
                    if(!Blocks.valid(type)||type==0||Blocks.isModel(type)||Blocks.isPiece(type)
                            ||leaf.side()<16&&!Blocks.subdividable(type))throw new IOException("Invalid geometry material");
                    if(++leaves>MAX_LEAVES)throw new IOException("Experimental geometry cap exceeded; city support unqualified");
                }
                if(owned==null)entries.add(p,tree,-1);
            }
        }
        // Spatial partition order: 16-cell sections, then local cell index.
        cancelled(cancel);this.cells=entries.build();cancelled(cancel);leafCount=leaves;instanceCount=instances;
    }
    static long order(Position p) {
        long section=(p.y/16L*64+p.z/16)*64+p.x/16;
        return section*4096+(p.y%16*16+p.z%16)*16+p.x%16;
    }
    public static StructurePackage capture(String name,Bounds bounds,Position origin,Source source,
                                           BooleanSupplier cancel,IntConsumer progress) throws IOException {
        var cells=new StructureCells.Builder();var definitions=new ArrayList<ModelDefinition>();var ids=new HashMap<String,Integer>();int leaves=0,instances=0;
        long volume=(long)bounds.x*bounds.y*bounds.z,done=0;
        for(int y=0;y<bounds.y;y++)for(int z=0;z<bounds.z;z++)for(int x=0;x<bounds.x;x++) {
            cancelled(cancel);var p=new Position(x,y,z);
            if(!new Protocol.Edit(origin.x+x,origin.y+y,origin.z+z,0).valid())throw new IOException("Capture outside world");
            if(!source.emptyCell(origin.x+x,origin.y+y,origin.z+z)) {
                var tree=source.cell(origin.x+x,origin.y+y,origin.z+z);
                int type=WorldVoxels.decode(tree.uniform(0,0,0,16));
                if(Blocks.isModel(type)) {
                    var entry=source.models().get(type);if(entry==null)throw new IOException("Missing captured model");
                    var definition=entry.definition();var fingerprint=definition.fingerprint();Integer id=ids.get(fingerprint);
                    if(id==null) {id=definitions.size();if(id>=MAX_MODELS)throw new IOException("512 dependency limit");ids.put(fingerprint,id);definitions.add(definition);}
                    if(++instances>MAX_INSTANCES)throw new IOException("Experimental instance cap");
                    cells.add(p,null,id);
                } else if(tree.occupied()!=0) {
                    leaves+=tree.leaves().size();if(leaves>MAX_LEAVES)throw new IOException("Experimental geometry cap");
                    cells.add(p,tree.copy().freeze(),-1);
                }
                if(cells.size()>MAX_CELLS)throw new IOException("Occupied cell limit");
            }
            if((++done&1023)==0)progress.accept((int)(done*100/volume));
        }
        cancelled(cancel);progress.accept(100);return new StructurePackage(name,bounds,definitions,cells);
    }
    public static void cancelled(BooleanSupplier cancel) {
        if(cancel.getAsBoolean()||Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException("Structure operation cancelled");
    }
    public byte[] encode() throws IOException {
        var bytes=new ByteArrayOutputStream();
        try(var compressed=new BudgetOutput(bytes,COMPRESSED);var gzip=new GZIPOutputStream(compressed);var out=new DataOutputStream(new BudgetOutput(gzip,DECODED))) {
            out.writeInt(MAGIC);out.writeInt(VERSION);out.writeUTF(name);
            out.writeInt(bounds.x);out.writeInt(bounds.y);out.writeInt(bounds.z);out.writeInt(bounds.ax);out.writeInt(bounds.ay);out.writeInt(bounds.az);
            out.writeInt(models.size());for(var model:models)StructureModelCodec.write(out,model);
            out.writeInt(cells.size());
            for(var cell:cells) {
                out.writeShort(cell.position.x/16);out.writeByte(cell.position.y/16);out.writeShort(cell.position.z/16);
                out.writeShort((cell.position.y%16*16+cell.position.z%16)*16+cell.position.x%16);
                out.writeShort(cell.model);if(cell.model<0)cell.geometry.write(out);
            }
        }
        return bytes.toByteArray();
    }
    public static StructurePackage decode(byte[] bytes) throws IOException {
        return decode(bytes,()->false,p->{});
    }
    /** Worker-thread decode. Cancellation never publishes a package or mutates the world. */
    public static StructurePackage decode(byte[] bytes,BooleanSupplier cancel,IntConsumer progress) throws IOException {
        Objects.requireNonNull(cancel,"cancel");Objects.requireNonNull(progress,"progress");
        cancelled(cancel);progress.accept(0);cancelled(cancel);
        if(bytes.length<1||bytes.length>COMPRESSED)throw new IOException("64 MiB compressed structure limit");
        if(bytes.length<18||bytes[0]!=(byte)31||bytes[1]!=(byte)139||bytes[2]!=8||bytes[3]!=0)
            throw new IOException("Invalid structure gzip header");
        var payload=new ByteArrayInputStream(bytes,10,bytes.length-18);var inflater=new Inflater(true);var crc=new CRC32();
        try(var raw=new InflaterInputStream(payload,inflater);var checked=new CheckedInputStream(raw,crc);var budget=new BudgetInput(checked,DECODED);var in=new DataInputStream(budget)) {
            if(in.readInt()!=MAGIC)throw new IOException("Unsupported structure package");
            int version=in.readInt();if(version!=1&&version!=VERSION)throw new IOException("Unsupported structure package");
            String name=Protocol.readText(in,48);Bounds bounds;
            try {bounds=new Bounds(in.readInt(),in.readInt(),in.readInt(),in.readInt(),in.readInt(),in.readInt());}
            catch(IllegalArgumentException e){throw new IOException(e.getMessage(),e);}
            int n=in.readInt();if(n<0||n>MAX_MODELS)throw new IOException("512 dependency limit");
            var models=new ArrayList<ModelDefinition>();var identities=new HashSet<String>();long dependencyQuads=0;
            for(int i=0;i<n;i++) {
                cancelled(cancel);var model=version==1?ModelDefinition.read(in):StructureModelCodec.read(in);
                if(model.voxels().occupied()==0||!identities.add(model.fingerprint()))throw new IOException("Empty or duplicate dependency");
                dependencyQuads+=ModelMesher.mesh(model.voxels()).quads();
                if(dependencyQuads>MAX_DEPENDENCY_QUADS)throw new IOException("Experimental dependency surface cap");
                models.add(model);progress.accept((i+1)*25/n);cancelled(cancel);
            }
            progress.accept(25);cancelled(cancel);
            n=in.readInt();if(n<1||n>MAX_CELLS)throw new IOException("Occupied cell limit");
            var cells=new StructureCells.Builder();long last=-1;int leaves=0,instances=0;
            for(int i=0;i<n;i++) {
                cancelled(cancel);
                int sx=in.readUnsignedShort(),sy=in.readUnsignedByte(),sz=in.readUnsignedShort(),index=in.readUnsignedShort(),model=in.readShort();
                if(sx>=64||sy>=16||sz>=64||index>=4096||model< -1||model>=models.size())throw new IOException("Invalid section/model reference");
                var p=new Position(sx*16+index%16,sy*16+index/256,sz*16+(index/16)%16);
                long key=order(p);if(key<=last)throw new IOException("Duplicate/unordered structure cells");last=key;
                SparseVoxelOctree tree=null;
                if(model<0){tree=SparseVoxelOctree.read(in,16);if(tree.occupied()==0)throw new IOException("Invalid/empty cell");leaves+=tree.leaves().size();if(leaves>MAX_LEAVES)throw new IOException("Experimental geometry cap");}
                else if(++instances>MAX_INSTANCES)throw new IOException("Experimental instance cap");
                cells.add(p,tree==null?null:tree.freeze(),model);
                if((i&127)==127||i==n-1){progress.accept(25+(int)((long)(i+1)*74/n));cancelled(cancel);}
            }
            if(in.read()!=-1)throw new IOException("Trailing structure bytes");
            if(!inflater.finished()||inflater.getRemaining()!=0||payload.available()!=0
                    ||crc.getValue()!=littleInt(bytes,bytes.length-8)
                    ||((DECODED-budget.remaining)&0xffffffffL)!=littleInt(bytes,bytes.length-4))
                throw new IOException("Structure gzip length/checksum/trailing data");
            cancelled(cancel);var result=new StructurePackage(name,bounds,models,cells,cancel);
            cancelled(cancel);progress.accept(100);cancelled(cancel);return result;
        } finally {inflater.end();}
    }
    private static long littleInt(byte[] bytes,int start) {
        return (bytes[start]&255L)|((bytes[start+1]&255L)<<8)|((bytes[start+2]&255L)<<16)|((bytes[start+3]&255L)<<24);
    }
    public void save(Path path) throws IOException {
        byte[] bytes=encode();Files.createDirectories(path.toAbsolutePath().getParent());
        Path temp=Files.createTempFile(path.toAbsolutePath().getParent(),"structure-",".tmp");
        try {Files.write(temp,bytes);Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
        finally {Files.deleteIfExists(temp);}
    }
    public static StructurePackage load(Path path) throws IOException {
        return load(path,()->false,p->{});
    }
    public static StructurePackage load(Path path,BooleanSupplier cancel,IntConsumer progress) throws IOException {
        cancelled(cancel);
        if(Files.size(path)>COMPRESSED)throw new IOException("64 MiB compressed structure limit");
        try(var in=Files.newInputStream(path)) {return decode(in.readNBytes((int)COMPRESSED+1),cancel,progress);}
    }
    static final class BudgetInput extends FilterInputStream {
        long remaining;BudgetInput(InputStream in,long limit){super(in);remaining=limit;}
        @Override public int read() throws IOException {int v=in.read();if(v>=0&&--remaining<0)throw new IOException("512 MiB decoded structure limit");return v;}
        @Override public int read(byte[] b,int o,int n)throws IOException {int count=in.read(b,o,(int)Math.min(n,remaining+1));if(count>0&& (remaining-=count)<0)throw new IOException("512 MiB decoded structure limit");return count;}
    }
    static final class BudgetOutput extends FilterOutputStream {
        long remaining;BudgetOutput(OutputStream out,long limit){super(out);remaining=limit;}
        @Override public void write(int b)throws IOException {if(--remaining<0)throw new IOException("Structure byte limit");out.write(b);}
        @Override public void write(byte[] b,int o,int n)throws IOException {if((remaining-=n)<0)throw new IOException("Structure byte limit");out.write(b,o,n);}
    }
}
