package dev.jayms.net;

import java.io.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.function.BooleanSupplier;
import dev.jayms.net.structure.StructurePackage;

/** Private sibling save prefix. Only publish may replace the durable world. */
final class StagedWorldSave implements AutoCloseable {
    @FunctionalInterface interface Writer { void write(DataOutputStream out)throws IOException; }
    private final Path target,temp;
    private boolean closed;
    private StagedWorldSave(Path target,Path temp){this.target=target;this.temp=temp;}
    static StagedWorldSave stage(Path target,Writer prefix)throws IOException {
        return stage(target,prefix,()->false);
    }
    static StagedWorldSave stage(Path target,Writer prefix,BooleanSupplier cancel)throws IOException {
        StructurePackage.cancelled(cancel);
        Path absolute=target.toAbsolutePath();Files.createDirectories(absolute.getParent());
        var result=new StagedWorldSave(absolute,Files.createTempFile(absolute.getParent(),".structure-save-",".tmp"));
        try {
            try(var out=new DataOutputStream(new BufferedOutputStream(new CancelOutput(Files.newOutputStream(result.temp),cancel),64*1024))){prefix.write(out);StructurePackage.cancelled(cancel);}
            StructurePackage.cancelled(cancel);
            return result;
        }catch(IOException|RuntimeException|Error failure){try{result.close();}catch(IOException cleanup){failure.addSuppressed(cleanup);}throw failure;}
    }
    /** Below buffering: model serialization and oversized writes check each 64 KiB. */
    private static final class CancelOutput extends OutputStream {
        private final OutputStream output;private final BooleanSupplier cancel;
        CancelOutput(OutputStream output,BooleanSupplier cancel){this.output=output;this.cancel=cancel;}
        @Override public void write(int value)throws IOException {StructurePackage.cancelled(cancel);output.write(value);}
        @Override public void write(byte[] bytes,int offset,int length)throws IOException {
            java.util.Objects.checkFromIndexSize(offset,length,bytes.length);
            while(length>0){StructurePackage.cancelled(cancel);int count=Math.min(length,64*1024);output.write(bytes,offset,count);offset+=count;length-=count;}
        }
        @Override public void flush()throws IOException {StructurePackage.cancelled(cancel);output.flush();}
        @Override public void close()throws IOException {output.close();}
    }
    void publish(Writer tail)throws IOException {
        if(closed)throw new IOException("Staged save closed");
        try {
            try(var channel=FileChannel.open(temp,StandardOpenOption.WRITE,StandardOpenOption.APPEND);
                var out=new DataOutputStream(new BufferedOutputStream(Channels.newOutputStream(channel),64*1024))) {
                tail.write(out);out.flush();channel.force(true);
            }
            WorldSavePublication.replace(temp,target,true);closed=true;
        }catch(IOException|RuntimeException|Error failure){try{close();}catch(IOException cleanup){failure.addSuppressed(cleanup);}throw failure;}
    }
    @Override public void close()throws IOException {
        if(!closed){Files.deleteIfExists(temp);closed=true;}
    }
}
