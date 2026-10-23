package dev.jayms.net;

import java.io.*;
import java.nio.channels.*;
import java.nio.file.*;

/** Private sibling save prefix. Only publish may replace the durable world. */
final class StagedWorldSave implements AutoCloseable {
    @FunctionalInterface interface Writer { void write(DataOutputStream out)throws IOException; }
    private final Path target,temp;
    private boolean closed;
    private StagedWorldSave(Path target,Path temp){this.target=target;this.temp=temp;}
    static StagedWorldSave stage(Path target,Writer prefix)throws IOException {
        Path absolute=target.toAbsolutePath();Files.createDirectories(absolute.getParent());
        var result=new StagedWorldSave(absolute,Files.createTempFile(absolute.getParent(),".structure-save-",".tmp"));
        try {
            try(var out=new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(result.temp),64*1024))){prefix.write(out);}
            return result;
        }catch(IOException|RuntimeException|Error failure){try{result.close();}catch(IOException cleanup){failure.addSuppressed(cleanup);}throw failure;}
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
