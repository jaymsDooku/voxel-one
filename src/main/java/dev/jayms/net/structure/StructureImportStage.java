package dev.jayms.net.structure;

import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.function.*;

/** Worker-owned compressed snapshot. Never publishes a package or changes a world. */
final class StructureImportStage implements AutoCloseable {
    private final Path path;
    private final long size;
    private StructureImportStage(Path path,long size){this.path=path;this.size=size;}

    static StructureImportStage copy(Path source,BooleanSupplier cancel,IntConsumer progress)throws IOException {
        Path temp=null;
        try(var in=FileChannel.open(source,StandardOpenOption.READ)) {
            long size=in.size();
            if(size>StructurePackage.COMPRESSED)throw new IOException("64 MiB compressed structure limit");
            if(size<18)throw new IOException("Invalid structure gzip header");
            StructurePackage.cancelled(cancel);
            temp=Files.createTempFile("voxel-structure-import-",".tmp");
            try(var out=Files.newOutputStream(temp)) {
                var buffer=ByteBuffer.allocate(65536);long offset=0;
                while(offset<size) {
                    StructurePackage.cancelled(cancel);buffer.clear().limit((int)Math.min(buffer.capacity(),size-offset));
                    int count=in.read(buffer);if(count<1)throw new EOFException("Structure file changed while reading; retry import");
                    out.write(buffer.array(),0,count);offset+=count;progress.accept((int)(offset*10/size));StructurePackage.cancelled(cancel);
                }
                if(in.read(ByteBuffer.allocate(1))!=-1)throw new IOException("Structure file changed while reading; retry import");
            }
            StructurePackage.cancelled(cancel);return new StructureImportStage(temp,size);
        } catch(IOException|RuntimeException|Error failure) {
            if(temp!=null)try{Files.deleteIfExists(temp);}catch(IOException cleanup){failure.addSuppressed(cleanup);}throw failure;
        }
    }
    StructurePackage decode(BooleanSupplier cancel,IntConsumer progress)throws IOException {
        try(var file=FileChannel.open(path,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)) {
            if(file.size()!=size)throw new IOException("Structure import stage changed");
            byte[] header=read(file,10);if(header[0]!=(byte)31||header[1]!=(byte)139||header[2]!=8||header[3]!=0)throw new IOException("Invalid structure gzip header");
            file.position(size-8);byte[] footer=read(file,8);file.position(10);
            var payload=new Payload(Channels.newInputStream(file),size-18,cancel);
            return StructurePackage.decodePayload(payload,()->payload.remaining,little(footer,0),little(footer,4),cancel,progress);
        }
    }
    private static byte[] read(FileChannel file,int count)throws IOException {
        var buffer=ByteBuffer.allocate(count);while(buffer.hasRemaining())if(file.read(buffer)<1)throw new EOFException("Truncated structure import stage");return buffer.array();
    }
    private static long little(byte[] b,int p){return (b[p]&255L)|((b[p+1]&255L)<<8)|((b[p+2]&255L)<<16)|((b[p+3]&255L)<<24);}
    private static final class Payload extends FilterInputStream {
        long remaining;final BooleanSupplier cancel;
        Payload(InputStream input,long size,BooleanSupplier cancel){super(input);remaining=size;this.cancel=cancel;}
        @Override public int read()throws IOException {
            StructurePackage.cancelled(cancel);if(remaining==0)return -1;int b=in.read();if(b<0)throw new EOFException("Truncated structure import stage");remaining--;return b;
        }
        @Override public int read(byte[] b,int offset,int length)throws IOException {
            StructurePackage.cancelled(cancel);if(length==0)return 0;if(remaining==0)return -1;
            int count=in.read(b,offset,(int)Math.min(length,remaining));if(count<1)throw new EOFException("Truncated structure import stage");remaining-=count;return count;
        }
    }
    @Override public void close()throws IOException{Files.deleteIfExists(path);}
}
