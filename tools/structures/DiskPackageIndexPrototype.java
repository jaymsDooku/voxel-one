import dev.jayms.net.*;
import dev.jayms.net.model.*;
import dev.jayms.net.structure.*;
import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.*;
import java.util.zip.*;

/** Read-only diagnostic for future region storage, never a game importer or admission path.
 * Owns a private decoded cache. No other writer may modify that cache while open. */
public final class DiskPackageIndexPrototype implements AutoCloseable {
    static final int COMPRESSED=64*1024*1024,DECODED=512*1024*1024;
    static final long ANALYSIS_LEAVES=8_388_608; // Diagnostic corpus bound, not a supported limit.
    public record Region(int section,long offset,long bytes,int cells,long crc){}
    public record Model(long offset,long bytes,long crc,String fingerprint,int quads){}
    public record Stats(int cells,long leaves,int instances,int models,int regions,long decodedBytes,long dependencyQuads,long instanceQuads){}
    private record Header(int sx,int sy,int sz,int index,int model) {
        int section(){return (sy*64+sz)*64+sx;}
        long key(){return section()*4096L+index;}
        StructurePackage.Position position(){return new StructurePackage.Position(sx*16+index%16,sy*16+index/256,sz*16+(index/16)%16);}
        void crc(CRC32 crc){for(int value:new int[]{sx>>>8,sx,sy,sz>>>8,sz,index>>>8,index,model>>>8,model})crc.update(value);}
    }
    private static final class Count extends FilterInputStream {
        long count;final CRC32 crc=new CRC32();final BooleanSupplier cancel;
        Count(InputStream in,BooleanSupplier cancel){super(in);this.cancel=cancel;}
        @Override public int read()throws IOException{check(cancel);int b=in.read();if(b!=-1){count++;crc.update(b);}return b;}
        @Override public int read(byte[] b,int o,int n)throws IOException{check(cancel);int read=in.read(b,o,n);if(n>0&&read==0)throw new IOException("No package read progress");if(read>0){count+=read;crc.update(b,o,read);}return read;}
    }
    final Path decoded;public final String name;public final StructurePackage.Bounds bounds;
    public final List<Region> regions;public final List<Model> models;public final Stats stats;
    private final int version;private boolean closed;
    private DiskPackageIndexPrototype(Path path,String name,StructurePackage.Bounds bounds,int version,List<Region> regions,List<Model> models,Stats stats){decoded=path;this.name=name;this.bounds=bounds;this.version=version;this.regions=List.copyOf(regions);this.models=List.copyOf(models);this.stats=stats;}
    static void check(BooleanSupplier cancel){if(Thread.currentThread().isInterrupted()||cancel.getAsBoolean())throw new CancellationException();}
    private static long little(byte[] bytes,int p){return (bytes[p]&255L)|(bytes[p+1]&255L)<<8|(bytes[p+2]&255L)<<16|(bytes[p+3]&255L)<<24;}
    private static long inflate(Path source,Path cache,BooleanSupplier cancel)throws IOException {
        byte[] bytes;
        try(var file=FileChannel.open(source,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)) {
            long size=file.size();if(size<18||size>COMPRESSED)throw new IOException("Compressed diagnostic budget");bytes=new byte[(int)size];int offset=0;
            while(offset<bytes.length){check(cancel);int n=file.read(ByteBuffer.wrap(bytes,offset,Math.min(65536,bytes.length-offset)));if(n<1)throw new EOFException("Truncated package");offset+=n;}
            if(file.read(ByteBuffer.allocate(1))!=-1)throw new IOException("Package grew during read");
        }
        if(bytes[0]!=(byte)31||bytes[1]!=(byte)139||bytes[2]!=8||bytes[3]!=0)throw new IOException("Unsupported gzip header");
        var payload=new ByteArrayInputStream(bytes,10,bytes.length-18);var inflater=new Inflater(true);var crc=new CRC32();long count=0;
        try(var input=new InflaterInputStream(payload,inflater,65536);var out=Files.newOutputStream(cache)) {
            byte[] buffer=new byte[65536];for(int n;(n=input.read(buffer))!=-1;){check(cancel);if(n<1)throw new IOException("No inflate progress");if(n>DECODED-count)throw new IOException("Decoded diagnostic budget");out.write(buffer,0,n);crc.update(buffer,0,n);count+=n;}
            if(!inflater.finished()||inflater.getRemaining()!=0||payload.available()!=0||crc.getValue()!=little(bytes,bytes.length-8)||count!=little(bytes,bytes.length-4))throw new IOException("Gzip checksum/length/trailing data");
            check(cancel);return count;
        } finally {inflater.end();}
    }
    private static Header header(DataInputStream in,StructurePackage.Bounds bounds,int models)throws IOException {
        var h=new Header(in.readUnsignedShort(),in.readUnsignedByte(),in.readUnsignedShort(),in.readUnsignedShort(),in.readShort());
        if(h.sx>=64||h.sy>=16||h.sz>=64||h.index>=4096||h.model< -1||h.model>=models)throw new IOException("Section/model reference");
        var p=h.position();if(p.x()>=bounds.x()||p.y()>=bounds.y()||p.z()>=bounds.z())throw new IOException("Cell bounds");return h;
    }
    private static StructurePackage.Cell cell(DataInputStream in,Header header,BooleanSupplier cancel)throws IOException {
        SparseVoxelOctree tree=null;
        if(header.model<0){tree=SparseVoxelOctree.read(in,16);if(tree.occupied()==0)throw new IOException("Empty geometry");tree.forEachLeaf((x,y,z,side,color)->{check(cancel);int type=WorldVoxels.decode(color);if(!Blocks.valid(type)||type==0||Blocks.isModel(type)||Blocks.isPiece(type)||side<16&&!Blocks.subdividable(type))throw new IOException("Geometry material");});tree.freeze();}
        return new StructurePackage.Cell(header.position(),tree,header.model);
    }
    public static DiskPackageIndexPrototype open(Path source,Path cacheDirectory,BooleanSupplier cancel)throws IOException {
        Objects.requireNonNull(cancel);check(cancel);Files.createDirectories(cacheDirectory);Path cache=Files.createTempFile(cacheDirectory,"diagnostic-",".decoded");
        try {
            long decodedBytes=inflate(source,cache,cancel);
            try(var count=new Count(new BufferedInputStream(Files.newInputStream(cache),65536),cancel)) {
                var in=new DataInputStream(count);if(in.readInt()!=StructurePackage.MAGIC)throw new IOException("Package magic");int version=in.readInt();if(version!=1&&version!=2)throw new IOException("Package version");
                String name=Protocol.readText(in,48);if(!name.matches("[A-Za-z0-9 _-]{1,48}"))throw new IOException("Package name");StructurePackage.Bounds bounds;try{bounds=new StructurePackage.Bounds(in.readInt(),in.readInt(),in.readInt(),in.readInt(),in.readInt(),in.readInt());}catch(IllegalArgumentException invalid){throw new IOException("Package bounds",invalid);}
                int n=in.readInt();if(n<0||n>512)throw new IOException("Dependency count");var models=new ArrayList<Model>();var identities=new HashSet<String>();long dependencyQuads=0;
                for(int i=0;i<n;i++){check(cancel);long start=count.count;count.crc.reset();var model=version==1?ModelDefinition.read(in):StructureModelCodec.read(in);if(model.voxels().occupied()==0||!identities.add(model.fingerprint()))throw new IOException("Empty/duplicate model");int quads=ModelMesher.count(model.voxels());if(quads>8192)throw new IOException("Per-model surface limit");dependencyQuads+=quads;models.add(new Model(start,count.count-start,count.crc.getValue(),model.fingerprint(),quads));}
                int cells=in.readInt();if(cells<1||cells>4_000_000)throw new IOException("Cell count");var regions=new ArrayList<Region>();long leaves=0,instanceQuads=0,last=-1,start=0;int instances=0,section=-1,regionCells=0;
                for(int i=0;i<cells;i++) {
                    check(cancel);long offset=count.count,beforeCRC=count.crc.getValue();var h=header(in,bounds,n);if(h.key()<=last)throw new IOException("Duplicate/unordered cell");last=h.key();
                    if(h.section()!=section){if(section>=0)regions.add(new Region(section,start,offset-start,regionCells,beforeCRC));if(regions.size()>=65536)throw new IOException("Region index budget");section=h.section();start=offset;regionCells=0;count.crc.reset();h.crc(count.crc);}regionCells++;
                    var cell=cell(in,h,cancel);if(cell.model()<0){leaves+=cell.geometry().leafCount();if(leaves>ANALYSIS_LEAVES)throw new IOException("Diagnostic geometry budget");}else{if(++instances>16384)throw new IOException("Instance count");instanceQuads+=models.get(cell.model()).quads();}
                }
                if(section>=0)regions.add(new Region(section,start,count.count-start,regionCells,count.crc.getValue()));
                if(in.read()!=-1)throw new IOException("Trailing package data");check(cancel);
                return new DiskPackageIndexPrototype(cache,name,bounds,version,regions,models,new Stats(cells,leaves,instances,n,regions.size(),decodedBytes,dependencyQuads,instanceQuads));
            }
        } catch(IOException|RuntimeException|Error failure){try{Files.deleteIfExists(cache);}catch(IOException cleanup){failure.addSuppressed(cleanup);}throw failure;}
    }
    private FileChannel channel()throws IOException{if(closed)throw new IOException("Diagnostic cache closed");var file=FileChannel.open(decoded,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS);if(file.size()!=stats.decodedBytes()){file.close();throw new IOException("Cache size changed");}return file;}
    private static void verify(FileChannel file,long offset,long bytes,long expected,BooleanSupplier cancel)throws IOException {
        file.position(offset);var crc=new CRC32();var buffer=ByteBuffer.allocate(65536);long remaining=bytes;
        while(remaining>0){check(cancel);buffer.clear().limit((int)Math.min(buffer.capacity(),remaining));int n=file.read(buffer);if(n<1)throw new EOFException("Truncated cache region");crc.update(buffer.array(),0,n);remaining-=n;}if(crc.getValue()!=expected)throw new IOException("Cache CRC changed");file.position(offset);
    }
    private static InputStream view(FileChannel file,long bytes) {
        return new FilterInputStream(Channels.newInputStream(file)) {long remaining=bytes;
            @Override public int read()throws IOException{if(remaining==0)return -1;int b=in.read();if(b==-1)throw new EOFException();remaining--;return b;}
            @Override public int read(byte[] b,int o,int n)throws IOException{if(n==0)return 0;if(remaining==0)return -1;int read=in.read(b,o,(int)Math.min(n,remaining));if(read<1)throw new EOFException();remaining-=read;return read;}
        };
    }
    /** Caller may inspect/stage each cell, never mutate a live world during this visit. */
    public void visitRegion(int number,Consumer<StructurePackage.Cell> visitor,BooleanSupplier cancel)throws IOException {
        Objects.requireNonNull(visitor);Objects.requireNonNull(cancel);check(cancel);var region=regions.get(number);try(var file=channel()){verify(file,region.offset(),region.bytes(),region.crc(),cancel);var in=new DataInputStream(new BufferedInputStream(view(file,region.bytes()),65536));long last=-1;
            for(int i=0;i<region.cells();i++){check(cancel);var h=header(in,bounds,models.size());if(h.section()!=region.section()||h.key()<=last)throw new IOException("Cache region order");last=h.key();visitor.accept(cell(in,h,cancel));}if(in.read()!=-1)throw new IOException("Cache region trailing data");check(cancel);}
    }
    public ModelDefinition model(int number,BooleanSupplier cancel)throws IOException {
        Objects.requireNonNull(cancel);check(cancel);var expected=models.get(number);try(var file=channel()){verify(file,expected.offset(),expected.bytes(),expected.crc(),cancel);var in=new DataInputStream(new BufferedInputStream(view(file,expected.bytes()),65536));var model=version==1?ModelDefinition.read(in):StructureModelCodec.read(in);if(in.read()!=-1||!model.fingerprint().equals(expected.fingerprint()))throw new IOException("Cache model changed");check(cancel);return model;}
    }
    @Override public void close()throws IOException{if(!closed){Files.deleteIfExists(decoded);closed=true;}}
}
