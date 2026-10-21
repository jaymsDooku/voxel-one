import dev.jayms.net.*;
import dev.jayms.net.model.*;
import dev.jayms.net.structure.*;
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.*;

/** Trusted benchmark-corpus converter. Not an importer, admission bypass or world editor. */
public final class NativeFixture {
    static final int RECORD_BYTES=16, INDEX_BITS=23, INDEX_MASK=(1<<INDEX_BITS)-1;
    record Profile(String name,int x,int y,int z,int leaves,int instances,int models,int resolution,int cells) {
        String header(){return "{\"bounds\":["+x+","+y+","+z+"],\"fixture\":\""+name+"\",\"format\":\"VXBF\",\"instances\":"+instances+",\"leaves\":"+leaves+",\"model_resolution\":"+resolution+",\"models\":"+models+",\"seed\":87129111,\"version\":1}";}
    }
    static Profile profile(String name){return switch(name){
        case "city" -> new Profile(name,512,128,512,968048,10240,256,32,978288);
        case "maximum" -> new Profile(name,1024,256,1024,7967232,16384,512,16,4000000);
        default -> throw new IllegalArgumentException("city or maximum required");
    };}
    static final class Budget extends FilterOutputStream {
        final long limit;long count;
        Budget(OutputStream out,long limit){super(out);this.limit=limit;}
        public void write(int b)throws IOException{if(count==limit)throw new IOException("Fixture output byte limit");out.write(b);count++;}
        public void write(byte[] b,int o,int n)throws IOException{if(n>limit-count)throw new IOException("Fixture output byte limit");out.write(b,o,n);count+=n;}
    }
    static long order(int x,int y,int z){return (((long)(y/16)*64+z/16)*64+x/16)*4096+(y%16*16+z%16)*16+x%16;}
    static int u16(ByteBuffer b,int p){return b.getShort(p)&65535;}
    static long key(ByteBuffer b,int p,Profile f)throws IOException{
        int x=u16(b,p),y=u16(b,p+2),z=u16(b,p+4);
        if(x>=f.x||y>=f.y||z>=f.z)throw new IOException("Fixture coordinate bounds");
        return order(x,y,z);
    }
    static void cellHeader(DataOutputStream out,long key,int model)throws IOException{
        long section=key/4096;
        out.writeShort((int)(section%64));out.writeByte((int)(section/4096));out.writeShort((int)(section/64%64));
        out.writeShort((int)(key%4096));out.writeShort(model);
    }
    static void leaf(SparseVoxelOctree tree,ByteBuffer b,int offset)throws IOException{
        int depth=b.get(offset+6)&255,ix=b.get(offset+7)&255,iy=b.get(offset+8)&255,iz=b.get(offset+9)&255;
        int material=u16(b,offset+10),rgb=b.getInt(offset+12);
        if((depth!=0&&depth!=1&&depth!=4)||Math.max(ix,Math.max(iy,iz))>=(1<<depth)
                ||(material!=180&&material!=187&&material!=188)||(rgb&0xff000000)!=0)throw new IOException("Fixture leaf fields");
        int side=16>>depth,x=ix*side,y=iy*side,z=iz*side;
        int color=WorldVoxels.encode(new Protocol.Edit(0,0,0,material,0,0,0,0,rgb));
        tree.fill(x,y,z,x+side,y+side,z+side,color);
    }
    static void selfTest()throws Exception {
        var cells=new ArrayList<StructurePackage.Cell>();
        int[][] positions={{1023,255,1023},{0,0,0},{16,1,31}};
        for(int i=0;i<positions.length;i++) {
            int[] p=positions[i];var tree=new SparseVoxelOctree(16);
            byte[] raw=new byte[16];var b=ByteBuffer.wrap(raw);
            b.putShort(10,(short)Blocks.LED);b.putInt(12,0x123456+i);
            if(i==1){b.put(6,(byte)4);b.put(7,(byte)15);b.put(8,(byte)3);b.put(9,(byte)7);}
            if(i==2){b.put(6,(byte)1);b.put(7,(byte)1);}
            leaf(tree,b,0);cells.add(new StructurePackage.Cell(new StructurePackage.Position(p[0],p[1],p[2]),tree,-1));
        }
        var voxels=new SparseVoxelOctree(8);voxels.set(1,2,3,0xff456789);
        var definition=new ModelDefinition("Orientation",StructureEngine.rotate(voxels,1));
        cells.add(new StructurePackage.Cell(new StructurePackage.Position(17,2,1),null,0));
        var expected=new StructurePackage("Native self test",new StructurePackage.Bounds(1024,256,1024,0,0,0),List.of(definition),cells);
        var bytes=new ByteArrayOutputStream();
        try(var gzip=new GZIPOutputStream(bytes);var out=new DataOutputStream(gzip)) {
            out.writeInt(StructurePackage.MAGIC);out.writeInt(StructurePackage.VERSION);out.writeUTF(expected.name);
            for(int n:new int[]{1024,256,1024,0,0,0})out.writeInt(n);
            out.writeInt(1);definition.write(out);out.writeInt(expected.cells.size());
            for(var cell:expected.cells){var p=cell.position();cellHeader(out,order(p.x(),p.y(),p.z()),cell.model());if(cell.model()<0)cell.geometry().write(out);}
        }
        if(!Arrays.equals(expected.encode(),bytes.toByteArray()))throw new AssertionError("Native wire differs from production encoder");
        var decoded=StructurePackage.decode(bytes.toByteArray());
        if(!Arrays.equals(decoded.encode(),expected.encode())||decoded.models.get(0).voxels().get(4,2,1)!=0xff456789)
            throw new AssertionError("Native roundtrip/orientation");
        System.out.println("{\"self_test\":\"passed\",\"checks\":\"production byte identity, spatial sections, fractional LED colour, model quarter turn and decode roundtrip\"}");
    }
    public static void main(String[] args)throws Exception{
        if(args.length==1&&args[0].equals("--self-test")){selfTest();return;}
        if(args.length!=3)throw new IllegalArgumentException("city|maximum input.vxbf.gz output.vstr");
        var f=profile(args[0]);Path input=Path.of(args[1]),output=Path.of(args[2]);
        if(Files.size(input)>StructurePackage.COMPRESSED)throw new IOException("Fixture compressed input limit");
        if(Files.exists(output))throw new IOException("Output already exists");
        Files.createDirectories(output.toAbsolutePath().getParent());
        Path temp=Files.createTempFile(output.toAbsolutePath().getParent(),"native-fixture-",".tmp");
        long decodedBytes;int cells=0;
        try {
            try(var in=new DataInputStream(new BufferedInputStream(new GZIPInputStream(Files.newInputStream(input))));
                var compressed=new Budget(new BufferedOutputStream(Files.newOutputStream(temp)),StructurePackage.COMPRESSED);
                var gzip=new GZIPOutputStream(compressed);
                var decoded=new Budget(gzip,StructurePackage.DECODED);
                var out=new DataOutputStream(decoded)) {
                if(!Arrays.equals(in.readNBytes(8),"VXBF0001".getBytes(java.nio.charset.StandardCharsets.US_ASCII)))throw new IOException("Fixture magic");
                int length=in.readInt();if(length<1||length>4096)throw new IOException("Fixture header limit");
                if(!new String(in.readNBytes(length),java.nio.charset.StandardCharsets.US_ASCII).equals(f.header()))throw new IOException("Exact versioned fixture header required");
                out.writeInt(StructurePackage.MAGIC);out.writeInt(StructurePackage.VERSION);out.writeUTF("Native "+f.name);
                out.writeInt(f.x);out.writeInt(f.y);out.writeInt(f.z);out.writeInt(0);out.writeInt(0);out.writeInt(0);out.writeInt(f.models);
                for(int id=0;id<f.models;id++) {
                    if(in.readUnsignedShort()!=id||in.readUnsignedByte()!=f.resolution)throw new IOException("Fixture dependency order/resolution");
                    int n=in.readUnsignedByte();if(n<1||n>32)throw new IOException("Fixture model name limit");
                    var name=new String(in.readNBytes(n),java.nio.charset.StandardCharsets.US_ASCII);var tree=new SparseVoxelOctree(f.resolution);
                    for(int v=0;v<f.resolution*f.resolution*f.resolution;v++)tree.set(v%f.resolution,(v/f.resolution)%f.resolution,v/(f.resolution*f.resolution),in.readInt());
                    // This corpus uses the same quarter turn for every occurrence of a dependency.
                    new ModelDefinition(name,StructureEngine.rotate(tree,id%4)).write(out);
                }
                byte[] raw=in.readNBytes(Math.multiplyExact(f.leaves,RECORD_BYTES));
                if(raw.length!=f.leaves*RECORD_BYTES)throw new EOFException("Fixture leaves");
                byte[] instances=in.readNBytes(f.instances*9);if(instances.length!=f.instances*9||in.read()!=-1)throw new IOException("Fixture instances/trailing bytes");
                var geometry=ByteBuffer.wrap(raw);var models=ByteBuffer.wrap(instances);
                long[] sorted=new long[f.leaves+f.instances];
                for(int i=0;i<f.leaves;i++)sorted[i]=(key(geometry,i*RECORD_BYTES,f)<<INDEX_BITS)|i;
                for(int i=0;i<f.instances;i++) {
                    int p=i*9,id=u16(models,p+6),rotation=models.get(p+8)&255;
                    if(id>=f.models||rotation!=id%4)throw new IOException("Fixture dependency/rotation");
                    sorted[f.leaves+i]=(key(models,p,f)<<INDEX_BITS)|(f.leaves+i);
                }
                Arrays.sort(sorted);out.writeInt(f.cells);
                for(int i=0;i<sorted.length;) {
                    long key=sorted[i]>>>INDEX_BITS;int record=(int)(sorted[i]&INDEX_MASK);int end=i+1;
                    while(end<sorted.length&&sorted[end]>>>INDEX_BITS==key)end++;
                    if(record>=f.leaves) {
                        if(end!=i+1)throw new IOException("Fixture model overlap");
                        cellHeader(out,key,u16(models,(record-f.leaves)*9+6));
                    } else {
                        if(end-i!=(f.name.equals("maximum")?2:1))throw new IOException("Fixture geometry overlap");
                        var tree=new SparseVoxelOctree(16);
                        for(int j=i;j<end;j++) {
                            int r=(int)(sorted[j]&INDEX_MASK);if(r>=f.leaves)throw new IOException("Fixture geometry/model overlap");
                            leaf(tree,geometry,r*RECORD_BYTES);
                        }
                        cellHeader(out,key,-1);tree.write(out);
                    }
                    cells++;i=end;
                }
                if(cells!=f.cells)throw new IOException("Fixture occupied count");
                out.flush();decodedBytes=decoded.count;
            }
            Files.move(temp,output,StandardCopyOption.ATOMIC_MOVE);
        } finally {Files.deleteIfExists(temp);}
        String admission;
        try {StructurePackage.load(output);admission="accepted";}
        catch(IOException rejected){admission="rejected: "+rejected.getMessage();}
        String sha;
        try(var in=Files.newInputStream(output)){sha=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes()));}
        System.out.printf(Locale.ROOT,"{\"fixture\":\"%s\",\"cells\":%d,\"decoded_bytes\":%d,\"compressed_bytes\":%d,\"sha256\":\"%s\",\"production_decode\":\"%s\",\"qualification\":\"native wire fixture only; no placement or performance qualification\"}%n",f.name,cells,decodedBytes,Files.size(output),sha,admission);
    }
}
