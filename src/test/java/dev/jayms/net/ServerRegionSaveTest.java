package dev.jayms.net;

import dev.jayms.net.city.GameConfig;
import dev.jayms.net.model.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.net.ssl.SSLContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ServerRegionSaveTest {
    @TempDir Path folder;
    MultiplayerServer open(Path path)throws Exception {
        return new MultiplayerServer("127.0.0.1",0,path,new AccountStore(folder.resolve("synthetic-accounts")),SSLContext.getDefault(),0,GameConfig.sandbox());
    }
    byte[] fixture(boolean missingModel)throws Exception {
        var models=new ModelLibrary();var tree=new SparseVoxelOctree(8);tree.set(1,2,3,0xff123456);
        int id=models.stageStructureModel(new ModelDefinition("Saved component",tree)).id();
        var edits=RegionReplayTest.fixture();
        // Keep the persisted drop stable even when the real item ticker runs.
        WorldVoxels.remember(edits,new Protocol.Edit(1,179,2,Blocks.STONE));WorldVoxels.remember(edits,new Protocol.Edit(32,180,0,id));
        var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);
        out.writeInt(Protocol.MAGIC);out.writeInt(-10);out.writeLong(42);out.writeInt(Terrain.CURRENT_VERSION);
        if(missingModel)new ModelLibrary().writeAll(out);else models.writeAll(out);
        RegionReplay.write(out,edits,()->false,p->{});
        out.writeInt(1);out.writeUTF("Synthetic");var inventory=new Inventory();inventory.add(Blocks.STONE,4);inventory.write(out);out.writeByte(13);
        out.writeInt(1);new ItemDrop(9,Blocks.STONE,2,1,180,2).write(out);out.flush();return bytes.toByteArray();
    }
    void awaitTicks(MultiplayerServer server)throws Exception {
        long start=System.nanoTime();
        while(server.tickTiming().totalTicks()<3&&System.nanoTime()-start<5_000_000_000L)Thread.sleep(5);
        assertTrue(server.tickTiming().totalTicks()>=3,"Server ticker did not run");
    }
    @Test void startupAndOrdinaryResavePreserveRegionalHistoryModelsAndPlayerTail()throws Exception {
        var path=folder.resolve("world.dat");byte[] before=fixture(false);Files.write(path,before);
        try(var server=open(path)){awaitTicks(server);}
        assertArrayEquals(before,Files.readAllBytes(path));
        try(var server=open(path)){awaitTicks(server);}
        assertArrayEquals(before,Files.readAllBytes(path));
    }
    @Test void malformedRegionalWorldAndMissingDependenciesRejectWithoutReplacingInput()throws Exception {
        var path=folder.resolve("world.dat");byte[] valid=fixture(false);
        for(byte[] invalid:List.of(fixture(true),Arrays.copyOf(valid,valid.length-1),Arrays.copyOf(valid,valid.length+1))) {
            Files.write(path,invalid);assertThrows(IOException.class,()->open(path));assertArrayEquals(invalid,Files.readAllBytes(path));
        }
    }
}
