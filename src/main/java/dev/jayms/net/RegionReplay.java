package dev.jayms.net;

import dev.jayms.ChunkPos;
import dev.jayms.net.structure.StructurePackage;
import java.io.*;
import java.util.*;
import java.util.function.*;
import java.util.zip.CRC32;

/** Version10 offline replay sections. File order is replay order; regions may recur.
 * Only one bounded section is buffered. This does not replace the live history map. */
final class RegionReplay {
    static final int MAX_RECORDS=1024,EDIT_BYTES=24,MAX_BODY=MAX_RECORDS*EDIT_BYTES;
    private RegionReplay(){}

    static void write(DataOutputStream out,Map<String,Protocol.Edit> edits,BooleanSupplier cancel,IntConsumer progress)throws IOException {
        Objects.requireNonNull(out);Objects.requireNonNull(edits);Objects.requireNonNull(cancel);Objects.requireNonNull(progress);
        StructurePackage.cancelled(cancel);progress.accept(0);StructurePackage.cancelled(cancel);
        int total=edits.size();if(total<0||total>LocalGame.MAX_SAVED_EDITS)throw new IOException("World edit persistence limit");
        out.writeInt(total);int count=0,sectionCount=0;ChunkPos region=null;
        var bytes=new ByteArrayOutputStream(MAX_BODY);var body=new DataOutputStream(bytes);
        for(var edit:edits.values()) {
            StructurePackage.cancelled(cancel);
            if(count>=total||edit==null||!edit.valid())throw new IOException("Invalid regional replay edit count/value");
            var position=ChunkPos.fromBlock(edit.x(),edit.y(),edit.z());
            if(sectionCount>0&&(!position.equals(region)||sectionCount==MAX_RECORDS)) {
                section(out,region,sectionCount,bytes);bytes.reset();sectionCount=0;
            }
            region=position;edit.write(body);sectionCount++;count++;
            if((count&127)==0||count==total){progress.accept((int)(100L*count/Math.max(1,total)));StructurePackage.cancelled(cancel);}
        }
        if(count!=total)throw new IOException("Incomplete regional replay");
        if(sectionCount>0)section(out,region,sectionCount,bytes);
        StructurePackage.cancelled(cancel);
    }
    static void section(DataOutputStream out,ChunkPos region,int count,ByteArrayOutputStream bytes)throws IOException {
        if(count<1||count>MAX_RECORDS||bytes.size()!=count*EDIT_BYTES)throw new IOException("Regional replay section budget");
        byte[] payload=bytes.toByteArray();var crc=new CRC32();crc.update(payload);
        out.writeInt(region.chunkX());out.writeInt(region.chunkY());out.writeInt(region.chunkZ());
        out.writeShort(count);out.writeShort(payload.length);out.writeInt((int)crc.getValue());out.write(payload);
    }
    /** Reads exactly the declared replay; the caller reads and validates the remaining world tail. */
    static void read(DataInputStream in,Map<String,Protocol.Edit> edits)throws IOException {
        read(in,edits,edit->true);
    }
    static void read(DataInputStream in,Map<String,Protocol.Edit> edits,Predicate<Protocol.Edit> allowed)throws IOException {
        read(in,edits,allowed,()->false,count->{});
    }
    /** Worker load adapter. Progress counts decoded records, not unique keys. */
    static void read(DataInputStream in,Map<String,Protocol.Edit> edits,Predicate<Protocol.Edit> allowed,
            BooleanSupplier cancel,IntConsumer progress)throws IOException {
        Objects.requireNonNull(edits);Objects.requireNonNull(progress);
        StructurePackage.cancelled(cancel);progress.accept(0);int[] count={0};
        visit(in,allowed,cancel,(region,section)->{
            for(var edit:section) {
                StructurePackage.cancelled(cancel);WorldVoxels.remember(edits,edit);
                if((++count[0]&127)==0)progress.accept(count[0]);
            }
        });
        StructurePackage.cancelled(cancel);progress.accept(count[0]);StructurePackage.cancelled(cancel);
    }
    /** A worker-owned staging sink. Sections retain file order, including recurring
     * regions. The sink must not publish live state: a later section may fail. */
    @FunctionalInterface interface SectionSink {
        void accept(ChunkPos region,List<Protocol.Edit> section)throws IOException;
    }
    /** Decode one bounded, fully validated immutable section at a time without
     * rebuilding a global history map. Leaves the caller's stream and world tail
     * open/unread. Cancellation is checked before I/O, per record and before each
     * sink call; blocking reads themselves still belong on a worker. Earlier sink
     * calls are private staged prefixes, not a transaction commit. */
    static void visit(DataInputStream in,Predicate<Protocol.Edit> allowed,BooleanSupplier cancel,
            SectionSink sink)throws IOException {
        Objects.requireNonNull(in);Objects.requireNonNull(allowed);
        Objects.requireNonNull(cancel);Objects.requireNonNull(sink);
        StructurePackage.cancelled(cancel);
        int total=in.readInt();if(total<0||total>LocalGame.MAX_SAVED_EDITS)throw new IOException("Invalid world");
        int count=0;
        while(count<total) {
            StructurePackage.cancelled(cancel);
            var region=new ChunkPos(in.readInt(),in.readInt(),in.readInt());
            int records=in.readUnsignedShort(),length=in.readUnsignedShort();long expected=Integer.toUnsignedLong(in.readInt());
            if(records<1||records>MAX_RECORDS||records>total-count||length!=records*EDIT_BYTES||length>MAX_BODY)throw new IOException("Regional replay section budget");
            byte[] bytes=in.readNBytes(length);if(bytes.length!=length)throw new EOFException("Truncated regional replay section");
            var crc=new CRC32();crc.update(bytes);if(crc.getValue()!=expected)throw new IOException("Regional replay checksum");
            var section=new ArrayList<Protocol.Edit>(records);
            try(var body=new DataInputStream(new ByteArrayInputStream(bytes))) {
                for(int i=0;i<records;i++) {
                    StructurePackage.cancelled(cancel);
                    var edit=Protocol.Edit.read(body);
                    if(!edit.valid()||!allowed.test(edit)||!ChunkPos.fromBlock(edit.x(),edit.y(),edit.z()).equals(region))throw new IOException("Invalid regional replay position/value");
                    section.add(edit);
                }
                if(body.read()!=-1)throw new IOException("Trailing regional replay section");
            }
            // No record escapes before its entire section passes validation.
            StructurePackage.cancelled(cancel);
            sink.accept(region,List.copyOf(section));
            StructurePackage.cancelled(cancel);
            count+=records;
        }
        StructurePackage.cancelled(cancel);
    }
}
