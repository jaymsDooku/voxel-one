package dev.jayms.net;

import dev.jayms.net.model.ModelLibrary;
import dev.jayms.net.structure.StructurePackage;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.Predicate;
import java.util.Objects;

/** Worker-written immutable prefix; patch only the fixed survival slot at publication. */
final class StagedOfflineSave implements AutoCloseable {
    private final Path target,temp;
    private long survivalOffset;
    private boolean closed;
    private boolean regional;
    boolean regional(){return regional;}
    private StagedOfflineSave(Path target,Path temp){this.target=target;this.temp=temp;}
    static StagedOfflineSave stage(Path target,long seed,int generator,ModelLibrary models,
            WorldVoxels.History edits,BooleanSupplier cancel,IntConsumer progress)throws IOException {
        return stage(target,seed,generator,models,edits,cancel,progress,false);
    }
    static StagedOfflineSave stageRegions(Path target,long seed,int generator,ModelLibrary models,
            WorldVoxels.History edits,BooleanSupplier cancel,IntConsumer progress)throws IOException {
        return stage(target,seed,generator,models,edits,cancel,progress,true);
    }
    private static StagedOfflineSave stage(Path target,long seed,int generator,ModelLibrary models,
            WorldVoxels.History edits,BooleanSupplier cancel,IntConsumer progress,boolean regional)throws IOException {
        return stagePrefix(target,seed,generator,models,cancel,progress,regional,out->{
            if(regional)RegionReplay.write(out,edits,cancel,progress);
            else {
                out.writeInt(edits.size());int count=0;
                for(var edit:edits.values()){if((count++&1023)==0){StructurePackage.cancelled(cancel);progress.accept((int)(100L*count/Math.max(1,edits.size())));}edit.write(out);}
            }
        });
    }
    /** Worker-owned unchanged-source staging. Preserves global section/row order
     * without materializing History. Not partial-world save authority or CAS. */
    static StagedOfflineSave stageRegions(Path target,long seed,int generator,ModelLibrary models,
            RegionReplaySpool source,Predicate<Protocol.Edit> allowed,BooleanSupplier cancel,IntConsumer progress)throws IOException {
        return stageRegions(target,seed,generator,models,source,RegionReplayOverlay.empty(),allowed,cancel,progress);
    }
    /** Append an immutable bounded overlay after the complete base, keeping all
     * original source and overlay replay order. This does not compact history. */
    static StagedOfflineSave stageRegions(Path target,long seed,int generator,ModelLibrary models,
            RegionReplaySpool source,RegionReplayOverlay overlay,Predicate<Protocol.Edit> allowed,
            BooleanSupplier cancel,IntConsumer progress)throws IOException {
        Objects.requireNonNull(source);Objects.requireNonNull(overlay);Objects.requireNonNull(allowed);Objects.requireNonNull(cancel);Objects.requireNonNull(progress);
        int baseRows=source.size();long combined=(long)baseRows+overlay.rows().size();
        if(combined>LocalGame.MAX_SAVED_EDITS)throw new IOException("World edit persistence limit");
        int total=(int)combined;
        return stagePrefix(target,seed,generator,models,cancel,progress,true,out->{
            StructurePackage.cancelled(cancel);progress.accept(0);StructurePackage.cancelled(cancel);
            out.writeInt(total);int[] count={0};
            var bytes=new ByteArrayOutputStream(RegionReplay.MAX_BODY);var body=new DataOutputStream(bytes);
            source.visit(allowed,cancel,(region,section)->{
                bytes.reset();
                for(var edit:section) {
                    StructurePackage.cancelled(cancel);
                    if(count[0]>=baseRows)throw new IOException("Replay source row count changed");
                    edit.write(body);count[0]++;
                    replayProgress(count[0],total,cancel,progress);
                }
                RegionReplay.writeSection(out,region,section.size(),bytes);
            });
            if(count[0]!=baseRows)throw new IOException("Incomplete replay source");
            bytes.reset();dev.jayms.ChunkPos region=null;int sectionCount=0;
            for(var edit:overlay.rows()) {
                StructurePackage.cancelled(cancel);
                if(!allowed.test(edit))throw new IOException("Replay overlay policy rejected");
                var position=dev.jayms.ChunkPos.fromBlock(edit.x(),edit.y(),edit.z());
                if(sectionCount>0&&(!position.equals(region)||sectionCount==RegionReplay.MAX_RECORDS)) {
                    RegionReplay.writeSection(out,region,sectionCount,bytes);bytes.reset();sectionCount=0;
                }
                region=position;edit.write(body);sectionCount++;count[0]++;
                replayProgress(count[0],total,cancel,progress);
            }
            if(sectionCount>0)RegionReplay.writeSection(out,region,sectionCount,bytes);
            if(count[0]!=total)throw new IOException("Incomplete replay overlay");
            StructurePackage.cancelled(cancel);
        });
    }
    private static void replayProgress(int count,int total,BooleanSupplier cancel,IntConsumer progress) {
        if((count&127)==0||count==total){progress.accept((int)(100L*count/Math.max(1,total)));StructurePackage.cancelled(cancel);}
    }
    @FunctionalInterface private interface ReplayWriter {void write(DataOutputStream out)throws IOException;}
    private static StagedOfflineSave stagePrefix(Path target,long seed,int generator,ModelLibrary models,
            BooleanSupplier cancel,IntConsumer progress,boolean regional,ReplayWriter replay)throws IOException {
        Path absolute=target.toAbsolutePath();Files.createDirectories(absolute.getParent());
        var staged=new StagedOfflineSave(absolute,Files.createTempFile(absolute.getParent(),".structure-offline-",".tmp"));
        staged.regional=regional;
        try {
            try(var out=new DataOutputStream(new BufferedOutputStream(new CancelOutput(Files.newOutputStream(staged.temp),cancel),64*1024))) {
                StructurePackage.cancelled(cancel);
                out.writeInt(regional?10:models.hasStructureModels()?9:8);out.writeLong(seed);out.writeInt(generator);
                if(regional||models.hasStructureModels())models.writeAll(out);else models.write(out);
                staged.survivalOffset=out.size();
                new Inventory().write(out);out.writeByte(20);
                replay.write(out);StructurePackage.cancelled(cancel);
            }
            progress.accept(100);return staged;
        }catch(IOException|RuntimeException|Error failure){try{staged.close();}catch(IOException cleanup){failure.addSuppressed(cleanup);}throw failure;}
    }
    void publish(Inventory inventory,int health,Iterable<ItemDrop> drops,int dropCount)throws IOException {
        publishTail(inventory,health,drops,dropCount,null,()->false);
    }
    /** Worker-side guarded publication for cooperating JVM writers. Does not
     * fence foreign writers or atomically commit separate city sidecars. */
    void publish(Inventory inventory,int health,Iterable<ItemDrop> drops,int dropCount,
            WorldSavePublication.Generation expected,BooleanSupplier cancel)throws IOException {
        Objects.requireNonNull(expected);Objects.requireNonNull(cancel);
        publishTail(inventory,health,drops,dropCount,expected,cancel);
    }
    private void publishTail(Inventory inventory,int health,Iterable<ItemDrop> drops,int dropCount,
            WorldSavePublication.Generation expected,BooleanSupplier cancel)throws IOException {
        if(closed)throw new IOException("Staged offline save closed");
        try {
            StructurePackage.cancelled(cancel);
            if(dropCount<0||dropCount>Crafting.MAX_DROPS)throw new IOException("Offline drop persistence limit");
            Objects.requireNonNull(drops);
            var bytes=new ByteArrayOutputStream();var survival=new DataOutputStream(bytes);
            inventory.write(survival);survival.writeByte(health);survival.flush();
            // Version 8/9/10 inventory has exactly 36 two-byte slots followed by health.
            if(bytes.size()!=Inventory.SIZE*2+1)throw new IOException("Offline survival slot size changed");
            try(var channel=FileChannel.open(temp,StandardOpenOption.WRITE)) {
                channel.position(survivalOffset);var patch=ByteBuffer.wrap(bytes.toByteArray());
                while(patch.hasRemaining())channel.write(patch);
                channel.position(channel.size());
                var out=new DataOutputStream(new BufferedOutputStream(java.nio.channels.Channels.newOutputStream(channel),64*1024));
                out.writeInt(dropCount);int written=0;
                for(var drop:drops){
                    StructurePackage.cancelled(cancel);
                    if(written>=dropCount||drop==null)throw new IOException("Offline drop count changed");
                    drop.write(out);written++;
                }
                if(written!=dropCount)throw new IOException("Offline drop count changed");
                out.flush();StructurePackage.cancelled(cancel);channel.force(true);
            }
            StructurePackage.cancelled(cancel);
            if(expected==null)WorldSavePublication.replace(temp,target,true);
            else WorldSavePublication.replaceIfUnchanged(temp,target,expected,cancel);
            closed=true;
        }catch(IOException|RuntimeException|Error failure){try{close();}catch(IOException cleanup){failure.addSuppressed(cleanup);}throw failure;}
    }
    /** Below buffering: cancellation also interrupts model bytes before history starts. */
    private static final class CancelOutput extends OutputStream {
        private final OutputStream output;
        private final BooleanSupplier cancel;
        CancelOutput(OutputStream output,BooleanSupplier cancel){this.output=output;this.cancel=cancel;}
        @Override public void write(int value)throws IOException {StructurePackage.cancelled(cancel);output.write(value);}
        @Override public void write(byte[] bytes,int offset,int length)throws IOException {
            java.util.Objects.checkFromIndexSize(offset,length,bytes.length);
            while(length>0) {
                StructurePackage.cancelled(cancel);int count=Math.min(length,64*1024);
                output.write(bytes,offset,count);offset+=count;length-=count;
            }
        }
        @Override public void flush()throws IOException {output.flush();}
        @Override public void close()throws IOException {output.close();}
    }
    @Override public void close()throws IOException {if(!closed){Files.deleteIfExists(temp);closed=true;}}
}
