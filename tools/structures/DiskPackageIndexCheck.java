import dev.jayms.net.*;
import dev.jayms.net.model.*;
import dev.jayms.net.structure.*;
import java.io.*;
import java.nio.file.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.*;

/** Exercises the private read-only index; never opens a game profile. */
public final class DiskPackageIndexCheck {
    static void require(boolean ok){if(!ok)throw new AssertionError();}
    interface Action{void run()throws Exception;}
    static int rejects;
    static void reject(Action action)throws Exception{try{action.run();throw new AssertionError("Expected rejection");}catch(IOException|CancellationException expected){rejects++;}}
    static byte[] unzip(byte[] bytes)throws Exception{try(var in=new GZIPInputStream(new ByteArrayInputStream(bytes))){return in.readAllBytes();}}
    static byte[] zip(byte[] bytes)throws Exception{var out=new ByteArrayOutputStream();try(var gzip=new GZIPOutputStream(out)){gzip.write(bytes);}return out.toByteArray();}
    static void selfTest(Path dir)throws Exception {
        var cells=new ArrayList<StructurePackage.Cell>();
        for(int x:new int[]{0,15,16,1023}){var tree=new SparseVoxelOctree(16);tree.fill(0,0,0,x==15?8:16,16,16,WorldVoxels.encode(new Protocol.Edit(0,0,0,Blocks.LED,0,0,0,0,0x123456+x)));cells.add(new StructurePackage.Cell(new StructurePackage.Position(x,0,0),tree,-1));}
        var voxels=new SparseVoxelOctree(8);voxels.set(1,2,3,0xff456789);var model=new ModelDefinition("Indexed model",voxels);
        cells.add(new StructurePackage.Cell(new StructurePackage.Position(17,1,1),null,0));
        var expected=new StructurePackage("Disk index check",new StructurePackage.Bounds(1024,2,2,1,1,1),List.of(model),cells);
        byte[] original=expected.encode();long firstRegion,secondHeader,modelOffset;Path source=dir.resolve("sample.vstr");Files.write(source,original);Path owned;
        try(var index=DiskPackageIndexPrototype.open(source,dir,()->false)) {
            owned=index.decoded;firstRegion=index.regions.get(0).offset();modelOffset=index.models.get(0).offset();var firstTree=new ByteArrayOutputStream();expected.cells.get(0).geometry().write(new DataOutputStream(firstTree));secondHeader=firstRegion+9+firstTree.size();require(index.stats.cells()==5&&index.regions.size()==3&&index.stats.instances()==1&&index.stats.leaves()==expected.leafCount);
            var restored=new ArrayList<StructurePackage.Cell>();for(int i=0;i<index.regions.size();i++)index.visitRegion(i,restored::add,()->false);
            require(restored.equals(expected.cells));require(index.model(0,()->false).fingerprint().equals(model.fingerprint()));
            reject(()->index.visitRegion(0,c->{throw new AssertionError("Cancelled callback");},()->true));
            reject(()->index.model(0,()->true));
            var region=index.regions.get(0);try(var file=new RandomAccessFile(owned.toFile(),"rw")){file.seek(region.offset());int value=file.read();file.seek(region.offset());file.write(value^1);}
            reject(()->index.visitRegion(0,c->{throw new AssertionError("Corrupt callback");},()->false));
        }
        require(!Files.exists(owned));
        var closed=DiskPackageIndexPrototype.open(source,dir,()->false);closed.close();reject(()->closed.model(0,()->false));
        reject(()->DiskPackageIndexPrototype.open(source,dir,()->true));
        var calls=new AtomicInteger();reject(()->DiskPackageIndexPrototype.open(source,dir,()->calls.incrementAndGet()>5));
        byte[] broken=original.clone();broken[broken.length-8]^=1;Files.write(source,broken);reject(()->DiskPackageIndexPrototype.open(source,dir,()->false));
        Files.write(source,Arrays.copyOf(original,original.length-1));reject(()->DiskPackageIndexPrototype.open(source,dir,()->false));
        Files.write(source,Arrays.copyOf(original,original.length+1));reject(()->DiskPackageIndexPrototype.open(source,dir,()->false));
        byte[] decoded=unzip(original);
        for(int variant=0;variant<6;variant++) {
            byte[] altered=decoded.clone();var buffer=ByteBuffer.wrap(altered);int header=(int)firstRegion;
            switch(variant) {
                case 0 -> buffer.putShort(header,(short)64);
                case 1 -> buffer.putShort(header+7,(short)1);
                case 2 -> buffer.putShort((int)secondHeader+5,(short)0);
                case 3 -> buffer.putInt(10+expected.name.length(),0);
                case 4 -> buffer.putInt(10+expected.name.length()+24,513);
                case 5 -> altered[8+2]='!';
            }
            Files.write(source,zip(altered));reject(()->DiskPackageIndexPrototype.open(source,dir,()->false));
        }
        Files.write(source,original);try(var index=DiskPackageIndexPrototype.open(source,dir,()->false)) {
            try(var file=new RandomAccessFile(index.decoded.toFile(),"rw")){file.seek(modelOffset);int b=file.read();file.seek(modelOffset);file.write(b^1);}
            reject(()->index.model(0,()->false));
        }
        byte[] raw=unzip(original);raw[0]^=1;Files.write(source,zip(raw));reject(()->DiskPackageIndexPrototype.open(source,dir,()->false));
        Files.write(source,zip(Arrays.copyOf(unzip(original),unzip(original).length+1)));reject(()->DiskPackageIndexPrototype.open(source,dir,()->false));
        Files.write(source,original);try(var index=DiskPackageIndexPrototype.open(source,dir,()->false)){require(index.stats.cells()==5);}
        require(Arrays.equals(original,Files.readAllBytes(source)));try(var paths=Files.list(dir)){require(paths.noneMatch(p->p.getFileName().toString().endsWith(".decoded")));}
        System.out.println("{\"status\":\"passed\",\"cells\":5,\"regions\":3,\"rejection_cases\":"+rejects+",\"production_byte_roundtrip\":true,\"cache_cleanup\":true,\"qualification\":false}");
    }
    static void corpus(Path source,Path dir)throws Exception {
        long start=System.nanoTime();long[] cells={0},leaves={0},instances={0};DiskPackageIndexPrototype.Stats stats;double indexed,visited;
        try(var index=DiskPackageIndexPrototype.open(source,dir,()->false)) {
            indexed=(System.nanoTime()-start)/1e9;stats=index.stats;long visit=System.nanoTime();
            for(int i=0;i<index.regions.size();i++)index.visitRegion(i,c->{cells[0]++;if(c.model()<0)leaves[0]+=c.geometry().leafCount();else instances[0]++;},()->false);
            for(int i=0;i<index.models.size();i++)require(index.model(i,()->false).fingerprint().equals(index.models.get(i).fingerprint()));
            visited=(System.nanoTime()-visit)/1e9;require(cells[0]==stats.cells()&&leaves[0]==stats.leaves()&&instances[0]==stats.instances());
        }
        System.out.printf(Locale.ROOT,"{\"status\":\"passed\",\"fixture\":\"%s\",\"cells\":%d,\"leaves\":%d,\"instances\":%d,\"models\":%d,\"regions\":%d,\"decoded_bytes\":%d,\"dependency_quads\":%d,\"instance_quads\":%d,\"index_seconds\":%.6f,\"visit_seconds\":%.6f,\"all_regions_and_models_read\":true,\"production_admission\":false,\"qualification\":false}%n",source.getFileName(),stats.cells(),stats.leaves(),stats.instances(),stats.models(),stats.regions(),stats.decodedBytes(),stats.dependencyQuads(),stats.instanceQuads(),indexed,visited);
    }
    public static void main(String[] args)throws Exception {
        Path dir=Files.createTempDirectory(Path.of("target"),"disk-index-check-");try{if(args.length==0)selfTest(dir);else corpus(Path.of(args[0]),dir);}finally{try(var files=Files.list(dir)){for(var p:files.toList())Files.delete(p);}Files.delete(dir);}
    }
}
