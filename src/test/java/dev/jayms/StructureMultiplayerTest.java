package dev.jayms;

import dev.jayms.net.*;
import dev.jayms.net.city.GameConfig;
import dev.jayms.net.model.*;
import dev.jayms.net.structure.*;
import static dev.jayms.net.structure.StructurePackage.*;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;

class StructureMultiplayerTest {
    @TempDir Path folder;
    static final class View {
        final MultiplayerClient client;final WorldVoxels geometry;Protocol.Pose pose;
        View(MultiplayerClient client){this.client=client;pose=client.spawn;geometry=new WorldVoxels(new Terrain(client.seed,client.generatorVersion));for(var e:client.initialEdits)geometry.apply(e);}
        void pump(){client.move(pose);for(var e:client.poll())geometry.apply(e);}
    }
    static void waitFor(List<View> clients,java.util.function.BooleanSupplier condition)throws Exception {
        long deadline=System.nanoTime()+90_000_000_000L;
        while(System.nanoTime()<deadline) {
            for(var view:clients) {
                view.pump();
                assertTrue(view.client.connected(),"Synthetic protocol client disconnected: "+view.client.status());
            }
            if(condition.getAsBoolean())return;
            Thread.sleep(50); // Match Main.nextNetworkUpdate's production movement cadence.
        }
        fail("Timed out waiting for synthetic structure synchronization");
    }
    @Test void serverPlacementAllRotationsEmptyCachesUndoLateJoinAndRestart()throws Exception {
        var tls=SecureTransport.server(folder.resolve("synthetic-tls"));var accounts=new AccountStore(folder.resolve("synthetic-accounts"));var save=folder.resolve("world.dat");
        var definitions=new ArrayList<ModelDefinition>();var cells=new ArrayList<Cell>();
        for(int i=0;i<512;i++) {
            var tree=new SparseVoxelOctree(8);tree.set(1,2,3,0xff000000|i);
            definitions.add(new ModelDefinition("Part "+i,tree));cells.add(new Cell(new Position(i,0,0),null,i));
        }
        var asset=new StructurePackage("Shared road",new Bounds(512,1,1,0,0,0),definitions,cells);
        var views=new ArrayList<View>();
        try(var server=new MultiplayerServer("127.0.0.1",0,save,accounts,tls.context(),42,GameConfig.sandbox())) {
            var thread=new Thread(()->{try{server.run();}catch(Exception ignored){}});thread.setDaemon(true);thread.start();
            try {
                int clients=Integer.getInteger("voxel.structure.clients",3);
                assertTrue(clients>=3&&clients<=32);long firstBatch=System.nanoTime();
                for(int i=0;i<clients;i++) {
                    // Honor the production per-address login budget; never bypass/disable it.
                    if(i==16)while(System.nanoTime()-firstBatch<306_000_000_000L){for(var view:views)view.pump();Thread.sleep(250);}
                    views.add(new View(new MultiplayerClient("127.0.0.1",server.port(),"Struct"+i,"synthetic-password".toCharArray(),true,tls.fingerprint())));
                    for(var view:views)view.client.move(view.client.spawn);
                }
                var player=views.get(0).client;
                for(int r=0;r<4;r++) {
                    var target=new Position(2000+r*2000,128,2000);
                    assertTrue(player.placeStructure(asset.encode(),target,r));
                    waitFor(views,()->!player.structurePending());assertTrue(player.structureMessage.startsWith("Structure placed"),player.structureMessage);
                    var observer=views.get(1);var spawn=observer.client.spawn;
                    observer.pose=new Protocol.Pose(observer.client.id,spawn.x()+.25f,spawn.y(),spawn.z(),spawn.yaw(),spawn.pitch());
                    waitFor(views,()->player.players.containsKey(observer.client.id)&&Math.abs(player.players.get(observer.client.id).x()-observer.pose.x())<.01f);
                    for(var view:views) {
                        int type=WorldVoxels.decode(view.geometry.cell(target.x(),target.y(),target.z()).uniform(0,0,0,16));
                        assertTrue(Blocks.structureModel(type));assertEquals(8,view.client.models.get(type).definition().voxels().size());
                    }
                    assertTrue(player.placeStructure(asset.encode(),target,r));waitFor(views,()->!player.structurePending());assertTrue(player.structureMessage.startsWith("Structure rejected"));
                    if(r==3){assertTrue(player.undoStructure());waitFor(views,()->!player.structurePending());for(var view:views)assertEquals(0,view.geometry.cell(target.x(),target.y(),target.z()).occupied());}
                }
                // A real bounded upload gives the reader time to deliver cancellation while decoding/staging.
                var dense=new SparseVoxelOctree(32);for(int i=0;i<32768;i++)dense.set(i%32,(i/32)%32,i/1024,0xff000000|((i*1664525+1013904223)&0xffffff));
                var cancelModels=new ArrayList<ModelDefinition>();var cancelCells=new ArrayList<Cell>();
                for(int i=0;i<128;i++){cancelModels.add(new ModelDefinition("Cancel part "+i,dense));cancelCells.add(new Cell(new Position(i,0,0),null,i));}
                var cancellable=new StructurePackage("Cancel upload",new Bounds(128,1,1,0,0,0),cancelModels,cancelCells);
                int dependencies=player.models.entries().size();
                assertTrue(player.placeStructure(cancellable.encode(),new Position(12000,128,2000),0));player.cancelStructure();
                waitFor(views,()->!player.structurePending());assertTrue(player.structureMessage.toLowerCase(Locale.ROOT).contains("cancel"),player.structureMessage);
                for(var view:views){assertEquals(dependencies,view.client.models.entries().size());assertEquals(0,view.geometry.cell(12000,128,2000).occupied());}
                // An idle cancellation reply must not complete the next placement request.
                player.cancelStructure();
                // Two authenticated actors race for the same destination. Exactly one may commit.
                var rival=views.get(1).client;var contested=new Position(10000,128,2000);var payload=asset.encode();
                assertTrue(player.placeStructure(payload,contested,0));assertTrue(rival.placeStructure(payload,contested,0));
                waitFor(views,()->!player.structurePending()&&!rival.structurePending());
                assertEquals(1,(player.structureMessage.startsWith("Structure placed")?1:0)+(rival.structureMessage.startsWith("Structure placed")?1:0));
                waitFor(views,()->views.stream().allMatch(v->v.geometry.cell(10511,128,2000).occupied()>0));
                for(var view:views)for(int i=0;i<512;i++)assertTrue(view.geometry.cell(10000+i,128,2000).occupied()>0,"Atomic contested placement");
                views.get(views.size()-1).client.close();views.remove(views.size()-1);Thread.sleep(100);
                var late=new View(new MultiplayerClient("127.0.0.1",server.port(),"LateStructure","synthetic-password".toCharArray(),true,tls.fingerprint()));views.add(late);
                assertTrue(late.geometry.cell(2000,128,2000).occupied()>0);assertTrue(late.client.models.hasStructureModels());
                var timing=server.tickTiming();assertTrue(timing.totalTicks()>1);assertEquals(0,timing.failedTicks());
                assertTrue(timing.windowTicks()<=4096);assertEquals(timing.windowTicks(),timing.work().samples());
                assertEquals(timing.windowTicks()-1,timing.intervals().samples());assertTrue(timing.startsPerSecond()>0);
                assertTrue(timing.work().worstMs()>=timing.work().p95Ms());assertTrue(timing.work().p95Ms()>=timing.work().medianMs());
                var diagnostics=java.util.concurrent.Executors.newSingleThreadExecutor();
                try {
                    synchronized(server) {
                        var snapshot=diagnostics.submit(server::tickTiming).get(5,java.util.concurrent.TimeUnit.SECONDS);
                        assertTrue(snapshot.totalTicks()>=timing.totalTicks());
                    }
                }finally{diagnostics.shutdownNow();}
            }finally{for(var view:views)view.client.close();views.clear();}
        }
        try(var server=new MultiplayerServer("127.0.0.1",0,save,accounts,tls.context(),42,GameConfig.sandbox())) {
            var thread=new Thread(()->{try{server.run();}catch(Exception ignored){}});thread.setDaemon(true);thread.start();
            try(var client=new MultiplayerClient("127.0.0.1",server.port(),"Struct0","synthetic-password".toCharArray(),false,tls.fingerprint())) {
                assertTrue(client.models.hasStructureModels());assertTrue(client.initialEdits.stream().anyMatch(e->e.x()==2000&&e.y()==128&&Blocks.structureModel(e.type())));
                assertEquals(0,client.inventory.countType(Blocks.FLOWER_POT));
            }
        }
    }
}
