package dev.jayms.net;

import dev.jayms.ChunkPos;
import dev.jayms.net.model.*;
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.CRC32;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class RegionReplayTest {
    @TempDir Path folder;
    static WorldVoxels.History fixture() {
        var result=new WorldVoxels.History();
        for(int i=0;i<1100;i++){var e=new Protocol.Edit(i&15,128+(i>>>8),(i>>>4)&15,Blocks.STONE);WorldVoxels.remember(result,e);}
        WorldVoxels.remember(result,new Protocol.Edit(-1,128,-1,Blocks.piece(Blocks.LED,1),1,1,0,1,0x123456));
        WorldVoxels.remember(result,new Protocol.Edit(0,133,0,0));return result;
    }
    static byte[] encode(Map<String,Protocol.Edit> edits)throws Exception {
        var bytes=new ByteArrayOutputStream();RegionReplay.write(new DataOutputStream(bytes),edits,()->false,p->{});return bytes.toByteArray();
    }
    static void crc(byte[] bytes) {
        int n=Short.toUnsignedInt(ByteBuffer.wrap(bytes).getShort(18));var crc=new CRC32();crc.update(bytes,24,n);ByteBuffer.wrap(bytes).putInt(20,(int)crc.getValue());
    }
    @Test void spatialSectionsBoundBatchesPreserveOrderAndLeaveWorldTailUnread()throws Exception {
        var source=fixture();byte[] bytes=encode(source);var counts=new ArrayList<Integer>();var chunks=new ArrayList<ChunkPos>();
        try(var in=new DataInputStream(new ByteArrayInputStream(bytes))) {
            assertEquals(1102,in.readInt());int total=0;
            while(total<1102){var chunk=new ChunkPos(in.readInt(),in.readInt(),in.readInt());int n=in.readUnsignedShort(),length=in.readUnsignedShort();long expected=Integer.toUnsignedLong(in.readInt());assertTrue(n>0&&n<=1024);assertEquals(n*24,length);assertTrue(length<=24576);byte[] payload=in.readNBytes(length);var crc=new CRC32();crc.update(payload);assertEquals(expected,crc.getValue());counts.add(n);chunks.add(chunk);total+=n;}
            assertEquals(-1,in.read());
        }
        assertEquals(List.of(1024,76,1,1),counts);assertEquals(chunks.get(0),chunks.get(1));assertEquals(chunks.get(0),chunks.get(3));assertEquals(new ChunkPos(-1,8,-1),chunks.get(2));
        var framed=new ByteArrayOutputStream();framed.write(bytes);new DataOutputStream(framed).writeInt(0x12345678);
        var restored=new WorldVoxels.History();try(var in=new DataInputStream(new ByteArrayInputStream(framed.toByteArray()))){RegionReplay.read(in,restored);assertEquals(0x12345678,in.readInt());assertEquals(-1,in.read());}
        assertEquals(new ArrayList<>(source.entrySet()),new ArrayList<>(restored.entrySet()));assertArrayEquals(bytes,encode(restored));
        assertArrayEquals(new byte[4],encode(new WorldVoxels.History()));
    }
    @Test void malformedSectionsRejectBeforeApplyingTheirRecords()throws Exception {
        var source=new WorldVoxels.History();var edit=new Protocol.Edit(0,128,0,Blocks.STONE);source.put(edit.key(),edit);byte[] valid=encode(source);
        for(int variant=0;variant<9;variant++) {
            byte[] bytes=valid.clone();var b=ByteBuffer.wrap(bytes);
            switch(variant){case 0->b.putInt(0,-1);case 1->b.putInt(0,LocalGame.MAX_SAVED_EDITS+1);case 2->b.putShort(16,(short)0);case 3->b.putShort(16,(short)1025);case 4->b.putShort(18,(short)65535);case 5->bytes[20]^=1;case 6->b.putInt(4,99);case 7->{b.putInt(24,Integer.MAX_VALUE);crc(bytes);}case 8->{bytes[40]=(byte)255;crc(bytes);}}
            var target=new WorldVoxels.History();var old=new Protocol.Edit(900,180,900,Blocks.GLASS);target.put(old.key(),old);var before=new ArrayList<>(target.entrySet());
            assertThrows(IOException.class,()->RegionReplay.read(new DataInputStream(new ByteArrayInputStream(bytes)),target));assertEquals(before,new ArrayList<>(target.entrySet()));
        }
        for(int length:new int[]{0,4,23,valid.length-1})assertThrows(IOException.class,()->RegionReplay.read(new DataInputStream(new ByteArrayInputStream(Arrays.copyOf(valid,length))),new WorldVoxels.History()));
    }
    @Test void applicationPredicateRejectsWholeSectionBeforeHistoryChanges()throws Exception {
        var source=new WorldVoxels.History();WorldVoxels.remember(source,new Protocol.Edit(0,128,0,Blocks.STONE));WorldVoxels.remember(source,new Protocol.Edit(1,128,0,Blocks.GLASS));
        var target=new WorldVoxels.History();byte[] bytes=encode(source);
        assertThrows(IOException.class,()->RegionReplay.read(new DataInputStream(new ByteArrayInputStream(bytes)),target,e->e.type()!=Blocks.GLASS));assertTrue(target.isEmpty());
        RegionReplay.read(new DataInputStream(new ByteArrayInputStream(bytes)),target,e->true);assertEquals(new ArrayList<>(source.entrySet()),new ArrayList<>(target.entrySet()));
    }
    @Test void cancellationCountMismatchAndProgressFailureDoNotPublishAndAllowRetry()throws Exception {
        var source=fixture();byte[] before=encode(source);var cancel=new AtomicBoolean();var partial=new ByteArrayOutputStream();
        assertThrows(CancellationException.class,()->RegionReplay.write(new DataOutputStream(partial),source,cancel::get,p->{if(p>0)cancel.set(true);}));
        var empty=new ByteArrayOutputStream();assertThrows(CancellationException.class,()->RegionReplay.write(new DataOutputStream(empty),source,()->true,p->fail()));assertEquals(0,empty.size());
        assertThrows(IllegalStateException.class,()->RegionReplay.write(new DataOutputStream(new ByteArrayOutputStream()),source,()->false,p->{if(p>0)throw new IllegalStateException("Synthetic progress failure");}));
        var wrong=new AbstractMap<String,Protocol.Edit>(){public int size(){return 2;}public Set<Entry<String,Protocol.Edit>> entrySet(){return Set.of(Map.entry("one",new Protocol.Edit(0,128,0,Blocks.STONE)));}};
        assertThrows(IOException.class,()->encode(wrong));assertArrayEquals(before,encode(source));
    }
    @Test void versionTenStagingRestoresModelsSurvivalAndExactHistoryThenRemainsRegional()throws Exception {
        var path=folder.resolve("world.dat");var local=new LocalGame(path,42);local.edits=fixture();
        var voxels=new SparseVoxelOctree(8);voxels.set(1,2,3,0xff123456);int id=local.models.stageStructureModel(new ModelDefinition("Regional model",voxels)).id();WorldVoxels.remember(local.edits,new Protocol.Edit(32,180,0,id));
        try(var staged=StagedOfflineSave.stageRegions(path,local.seed,local.generatorVersion,local.models,(WorldVoxels.History)local.edits,()->false,p->{})) {
            assertFalse(Files.exists(path));local.inventory.add(Blocks.STONE,4);local.health=13;local.drops.put(1,new ItemDrop(1,Blocks.STONE,2,1,180,2));staged.publish(local.inventory,local.health,local.drops.values(),local.drops.size());
        }
        byte[] bytes=Files.readAllBytes(path);assertEquals(10,ByteBuffer.wrap(bytes).getInt());var loaded=new LocalGame(path,0);
        assertEquals(local.seed,loaded.seed);assertEquals(local.generatorVersion,loaded.generatorVersion);assertEquals(13,loaded.health);assertEquals(4,loaded.inventory.countType(Blocks.STONE));assertEquals(local.drops,loaded.drops);assertEquals(new ArrayList<>(local.edits.entrySet()),new ArrayList<>(loaded.edits.entrySet()));assertEquals(local.models.get(id).definition().fingerprint(),loaded.models.get(id).definition().fingerprint());
        loaded.save();assertArrayEquals(bytes,Files.readAllBytes(path));
        var original=loaded.edits;var invalidEdit=new Protocol.Edit(Integer.MAX_VALUE,128,0,Blocks.STONE);loaded.edits=new WorldVoxels.History();loaded.edits.put(invalidEdit.key(),invalidEdit);
        assertThrows(IOException.class,loaded::save);assertArrayEquals(bytes,Files.readAllBytes(path));assertFalse(Files.exists(path.resolveSibling(path.getFileName()+".tmp")));
        loaded.edits=original;loaded.save();assertArrayEquals(bytes,Files.readAllBytes(path));
        Files.write(path,Arrays.copyOf(bytes,bytes.length+1));byte[] invalid=Files.readAllBytes(path);assertThrows(IOException.class,()->new LocalGame(path,0));assertArrayEquals(invalid,Files.readAllBytes(path));
    }
    @Test void regionalStagingCancellationAndPublicationFailureCleanPrivateFiles()throws Exception {
        var path=folder.resolve("world.dat");var local=new LocalGame(path,42);local.save();byte[] before=Files.readAllBytes(path);var source=fixture();var cancel=new AtomicBoolean();
        assertThrows(CancellationException.class,()->StagedOfflineSave.stageRegions(path,42,local.generatorVersion,local.models,source,cancel::get,p->{if(p>0)cancel.set(true);}));assertArrayEquals(before,Files.readAllBytes(path));
        try(var staged=StagedOfflineSave.stageRegions(path,42,local.generatorVersion,local.models,source,()->false,p->{})){}
        var directory=folder.resolve("directory");Files.createDirectory(directory);Files.writeString(directory.resolve("keep"),"keep");
        try(var staged=StagedOfflineSave.stageRegions(directory,42,local.generatorVersion,local.models,source,()->false,p->{})){assertThrows(IOException.class,()->staged.publish(local.inventory,20,List.of(),0));}
        assertEquals("keep",Files.readString(directory.resolve("keep")));assertArrayEquals(before,Files.readAllBytes(path));
        try(var files=Files.list(folder)){assertTrue(files.noneMatch(p->p.getFileName().toString().startsWith(".structure-offline-")));}
    }
}
