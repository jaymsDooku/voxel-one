package dev.jayms.net;

import dev.jayms.net.model.ModelLibrary;
import dev.jayms.net.structure.StructurePackage;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;

/** Worker-written immutable prefix; patch only the fixed survival slot at publication. */
final class StagedOfflineSave implements AutoCloseable {
    private final Path target,temp;
    private long survivalOffset;
    private boolean closed;
    private StagedOfflineSave(Path target,Path temp){this.target=target;this.temp=temp;}
    static StagedOfflineSave stage(Path target,long seed,int generator,ModelLibrary models,
            WorldVoxels.History edits,BooleanSupplier cancel,IntConsumer progress)throws IOException {
        Path absolute=target.toAbsolutePath();Files.createDirectories(absolute.getParent());
        var staged=new StagedOfflineSave(absolute,Files.createTempFile(absolute.getParent(),".structure-offline-",".tmp"));
        try {
            try(var out=new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(staged.temp),64*1024))) {
                StructurePackage.cancelled(cancel);
                out.writeInt(models.hasStructureModels()?9:8);out.writeLong(seed);out.writeInt(generator);
                if(models.hasStructureModels())models.writeAll(out);else models.write(out);
                staged.survivalOffset=out.size();
                new Inventory().write(out);out.writeByte(20);
                out.writeInt(edits.size());int count=0;
                for(var edit:edits.values()){if((count++&1023)==0){StructurePackage.cancelled(cancel);progress.accept((int)(100L*count/Math.max(1,edits.size())));}edit.write(out);}
                StructurePackage.cancelled(cancel);
            }
            progress.accept(100);return staged;
        }catch(IOException|RuntimeException|Error failure){try{staged.close();}catch(IOException cleanup){failure.addSuppressed(cleanup);}throw failure;}
    }
    void publish(Inventory inventory,int health,Iterable<ItemDrop> drops,int dropCount)throws IOException {
        if(closed)throw new IOException("Staged offline save closed");
        try {
            var bytes=new ByteArrayOutputStream();var survival=new DataOutputStream(bytes);
            inventory.write(survival);survival.writeByte(health);survival.flush();
            // Version 8/9 inventory has exactly 36 two-byte slots followed by health.
            if(bytes.size()!=Inventory.SIZE*2+1)throw new IOException("Offline survival slot size changed");
            try(var channel=FileChannel.open(temp,StandardOpenOption.WRITE)) {
                channel.position(survivalOffset);var patch=ByteBuffer.wrap(bytes.toByteArray());
                while(patch.hasRemaining())channel.write(patch);
                channel.position(channel.size());
                var out=new DataOutputStream(new BufferedOutputStream(java.nio.channels.Channels.newOutputStream(channel),64*1024));
                out.writeInt(dropCount);for(var drop:drops)drop.write(out);out.flush();channel.force(true);
            }
            WorldSavePublication.replace(temp,target,true);closed=true;
        }catch(IOException|RuntimeException|Error failure){try{close();}catch(IOException cleanup){failure.addSuppressed(cleanup);}throw failure;}
    }
    @Override public void close()throws IOException {if(!closed){Files.deleteIfExists(temp);closed=true;}}
}
