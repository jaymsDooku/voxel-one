package dev.jayms.net;

import dev.jayms.net.model.SparseVoxelOctree;
import dev.jayms.net.model.ModelLibrary;
import dev.jayms.net.city.*;
import dev.jayms.net.structure.*;
import java.io.*;
import java.nio.file.Path;
import org.junit.jupiter.api.io.TempDir;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HistoryJoinDeltaTest {
    private static void replace(WorldVoxels.History history,int x,int color) {
        var tree=new SparseVoxelOctree(16);if(color!=0)tree.fill(1,2,3,2,3,4,color);
        WorldVoxels.rememberCell(history,new Protocol.CellState(x,180,-1,tree));
    }
    @Test void removedFractionalRowsAreSentWhenTheClearRowDidNotChange() {
        var current=new WorldVoxels.History();replace(current,-17,0xfeabcdef);
        var snapshot=WorldVoxels.History.copyOf(current);var before=new LinkedHashMap<>(snapshot);
        replace(current,-17,0);
        assertEquals(snapshot.get("-17,180,-1"),current.get("-17,180,-1"));
        assertEquals(Set.of(new WorldVoxels.CellPosition(-17,180,-1)),MultiplayerServer.changedCellPositions(current,snapshot));
        assertEquals(before,new LinkedHashMap<>(snapshot));
    }
    @Test void addedChangedRemovedCellsAreDeduplicatedAcrossBothDirections() {
        var current=new WorldVoxels.History();replace(current,0,0xfe123456);replace(current,1,0xfeffffff);
        var snapshot=WorldVoxels.History.copyOf(current);
        replace(current,0,0);replace(current,1,0xfe000000);replace(current,2,0xfe345678);
        assertEquals(Set.of(new WorldVoxels.CellPosition(0,180,-1),new WorldVoxels.CellPosition(1,180,-1),new WorldVoxels.CellPosition(2,180,-1)),MultiplayerServer.changedCellPositions(current,snapshot));
        assertTrue(MultiplayerServer.changedCellPositions(snapshot,WorldVoxels.History.copyOf(snapshot)).isEmpty());
        assertEquals(MultiplayerServer.changedCellPositions(current,snapshot),MultiplayerServer.changedCellPositions(new LinkedHashMap<>(current),new LinkedHashMap<>(snapshot)));
    }
    @TempDir Path folder;
    private static void waitFor(MultiplayerClient client,java.util.function.BooleanSupplier done)throws Exception {
        long deadline=System.nanoTime()+30_000_000_000L;
        while(System.nanoTime()<deadline){client.move(client.spawn);client.poll();assertTrue(client.connected());if(done.getAsBoolean())return;Thread.sleep(50);}
        fail("Synthetic join workflow timed out");
    }
    @Test void realTlsJoinPausedBeforeReadyReceivesUndoOfFinalFractionalLeaf()throws Exception {
        var tls=SecureTransport.server(folder.resolve("tls"));var accounts=new AccountStore(folder.resolve("accounts"));
        var secret=UUID.randomUUID().toString().toCharArray();
        try(var server=new MultiplayerServer("127.0.0.1",0,folder.resolve("world.dat"),accounts,tls.context(),42,GameConfig.sandbox())) {
            var thread=new Thread(()->{try{server.run();}catch(Exception ignored){}});thread.setDaemon(true);thread.start();
            try(var actor=new MultiplayerClient("127.0.0.1",server.port(),"JoinActor",secret,true,tls.fingerprint())) {
                var tree=new SparseVoxelOctree(16);tree.fill(1,2,3,2,3,4,0xfeabcdef);
                var target=new StructurePackage.Position(2000,180,2000);
                var asset=new StructurePackage("Join leaf",new StructurePackage.Bounds(1,1,1,0,0,0),List.of(),List.of(new StructurePackage.Cell(new StructurePackage.Position(0,0,0),tree,-1)));
                assertTrue(actor.placeStructure(asset.encode(),target,0));waitFor(actor,()->!actor.structurePending());assertTrue(actor.structureMessage.startsWith("Structure placed"));
                try(var socket=SecureTransport.connect("127.0.0.1",server.port(),tls.fingerprint())) {
                    socket.setSoTimeout(30000);var in=new DataInputStream(new BufferedInputStream(socket.getInputStream()));var out=new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
                    out.writeInt(Protocol.MAGIC);out.writeInt(Protocol.VERSION);out.writeByte(Protocol.REGISTER);out.writeUTF("JoinLate");out.writeUTF(new String(secret));out.flush();
                    assertEquals(Protocol.MAGIC,in.readInt());assertEquals(Protocol.VERSION,in.readInt());assertTrue(in.readBoolean());Protocol.readText(in,256);
                    in.readInt();Protocol.Pose.read(in);Protocol.readText(in,16);long seed=in.readLong();int generator=in.readInt();
                    ModelLibrary.readAll(in);Inventory.read(in,true);in.readUnsignedByte();
                    var geometry=new WorldVoxels(new Terrain(seed,generator));int count=in.readInt();assertTrue(count>0&&count<100);
                    for(int i=0;i<count;i++)geometry.apply(Protocol.Edit.read(in));
                    count=in.readInt();for(int i=0;i<count;i++){Protocol.Pose.read(in);Protocol.readText(in,16);}
                    count=in.readInt();for(int i=0;i<count;i++)ItemDrop.read(in,true);
                    CityFrame.read(in,17);assertEquals(1,geometry.cell(target.x(),target.y(),target.z()).occupied());
                    // This socket has its initial world but is not a broadcast peer yet.
                    assertTrue(actor.undoStructure());waitFor(actor,()->!actor.structurePending());assertTrue(actor.structureMessage.toLowerCase(Locale.ROOT).contains("undone"));
                    out.writeByte(Protocol.READY);out.flush();assertEquals(Protocol.READY,in.readUnsignedByte());
                    assertEquals(Protocol.STRUCTURE_COMMIT,in.readUnsignedByte());
                    var delta=StructureWire.decode(StructureWire.readPayload(in));assertEquals(1,delta.cells().size());
                    for(var cell:delta.cells())geometry.replaceCell(cell.x(),cell.y(),cell.z(),cell.tree());
                    assertTrue(geometry.cell(target.x(),target.y(),target.z()).isEmpty());
                }
            }
        } finally {Arrays.fill(secret,'\0');}
    }
}
