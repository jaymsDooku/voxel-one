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

class LegacyServerHistoryTest {
    @TempDir Path folder;
    record Fixture(int version,ModelLibrary models,List<Protocol.Edit> rows) {}
    static Fixture fixture(int version)throws Exception {
        var models=new ModelLibrary();var rows=new ArrayList<>(RegionReplayLoaderTest.fragmented(-17).subList(0,129));
        rows.addAll(RegionReplayLoaderTest.fragmented(32).subList(0,65));
        rows.add(new Protocol.Edit(-17,180,-1,0));
        rows.add(new Protocol.Edit(-17,180,-1,Blocks.piece(Blocks.GLASS,1),1,0,0,0));
        rows.add(rows.get(1).withColor(0xabcdef));
        if(version==9) {
            var tree=new SparseVoxelOctree(32);tree.set(31,30,29,0xff123456);
            int id=models.stageStructureModel(new ModelDefinition("Synthetic keyed dependency",tree)).id();
            rows.add(new Protocol.Edit(256,180,-17,id));
        }
        return new Fixture(version,models,List.copyOf(rows));
    }
    static byte[] encode(Fixture f,List<Protocol.Edit> rows,boolean missingModel)throws Exception {
        var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);
        out.writeInt(Protocol.MAGIC);out.writeInt(-f.version());out.writeLong(42);out.writeInt(Terrain.CURRENT_VERSION);
        var models=missingModel?new ModelLibrary():f.models();
        if(f.version()==9)models.writeAll(out);else models.write(out);
        out.writeInt(rows.size());for(var row:rows)row.write(out);
        out.writeInt(1);out.writeUTF("SyntheticSaved");var inventory=new Inventory();inventory.add(Blocks.STONE,4);inventory.write(out);out.writeByte(13);
        out.writeInt(0);out.flush();return bytes.toByteArray();
    }
    static WorldVoxels.History history(MultiplayerServer server)throws Exception {
        var field=MultiplayerServer.class.getDeclaredField("edits");field.setAccessible(true);return (WorldVoxels.History)field.get(server);
    }
    static WorldVoxels geometry(MultiplayerServer server)throws Exception {
        var field=MultiplayerServer.class.getDeclaredField("voxels");field.setAccessible(true);return (WorldVoxels)field.get(server);
    }
    MultiplayerServer open(Path path,SSLContext tls)throws Exception {
        return new MultiplayerServer("127.0.0.1",0,path,new AccountStore(folder.resolve("synthetic-accounts")),tls,0,GameConfig.sandbox());
    }
    @Test void legacyServerLoadAndResaveRetainPurePutOrderDependenciesTailAndRestart()throws Exception {
        for(int version:new int[]{8,9}) {
            var f=fixture(version);var expected=KeyedReplayLoaderTest.puts(f.rows());
            var path=folder.resolve("v"+version+".dat");Files.write(path,encode(f,f.rows(),false));
            try(var server=open(path,SSLContext.getDefault())) {
                KeyedReplayLoaderTest.same(expected,history(server));
                var oracle=new WorldVoxels(new Terrain(42));expected.values().forEach(oracle::apply);
                for(var cell:List.of(new WorldVoxels.CellPosition(-17,180,-1),new WorldVoxels.CellPosition(32,180,-1)))
                    assertTrue(oracle.cell(cell.x(),cell.y(),cell.z()).sameValues(geometry(server).cell(cell.x(),cell.y(),cell.z())));
            }
            var normalized=encode(f,new ArrayList<>(expected.values()),false);assertArrayEquals(normalized,Files.readAllBytes(path));
            try(var server=open(path,SSLContext.getDefault())) {
                KeyedReplayLoaderTest.same(KeyedReplayLoaderTest.puts(new ArrayList<>(expected.values())),history(server));
            }
            assertArrayEquals(normalized,Files.readAllBytes(path));
        }
    }
    @Test void missingDependenciesAndBadTailRejectWithoutReplacingInput()throws Exception {
        var f=fixture(9);var good=encode(f,f.rows(),false);var path=folder.resolve("bad.dat");
        for(var bytes:List.of(encode(f,f.rows(),true),Arrays.copyOf(good,good.length-1))) {
            Files.write(path,bytes);assertThrows(IOException.class,()->open(path,SSLContext.getDefault()));
            assertArrayEquals(bytes,Files.readAllBytes(path));
        }
    }
    @Test void realTlsClientsAndLateJoinReceiveExactLegacyKeyedHistoryAndModels()throws Exception {
        var f=fixture(9);var expected=KeyedReplayLoaderTest.puts(f.rows());var path=folder.resolve("tls-world.dat");Files.write(path,encode(f,f.rows(),false));
        var tls=SecureTransport.server(folder.resolve("synthetic-tls"));var secret=UUID.randomUUID().toString().toCharArray();
        try(var server=open(path,tls.context())) {
            var thread=new Thread(()->{try{server.run();}catch(Exception ignored){}});thread.setDaemon(true);thread.start();
            try {
                for(String name:List.of("KeyedFirst","KeyedLate"))try(var client=new MultiplayerClient("127.0.0.1",server.port(),name,secret,true,tls.fingerprint())) {
                    assertEquals(new ArrayList<>(expected.values()),client.initialEdits);
                    for(var entry:f.models().entries())assertEquals(entry.definition().fingerprint(),client.models.get(entry.id()).definition().fingerprint());
                    assertTrue(client.connected());
                }
            }finally{server.close();thread.join(5000);assertFalse(thread.isAlive());Arrays.fill(secret,'\0');}
        }
    }
}
