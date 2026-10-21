package dev.jayms;

import dev.jayms.net.*;
import dev.jayms.net.city.*;
import dev.jayms.net.model.*;
import dev.jayms.net.structure.*;
import static dev.jayms.net.structure.StructurePackage.*;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

class StructureOccupancyTest {
    @TempDir Path folder;
    static final Position TARGET=new Position(320,128,320);
    static CitySimulation city(boolean horse) {
        var f=new CityFrame(GameConfig.sandbox(),0,List.of(),List.of(),List.of(),List.of(),
                horse?List.of(new CityFrame.Horse(1,320.5f,128,320.5f,0,0,0)):List.of());
        return new CitySimulation(GameConfig.sandbox(),new CitySimulation.Ground(){public int type(int x,int y,int z){return 0;}public void apply(List<Protocol.Edit> edits){}public boolean occupied(int x,int y,int z,int width,int depth){return false;}},new Terrain(42),f);
    }
    static StructurePackage asset() throws Exception {
        var tree=new SparseVoxelOctree(8);tree.set(0,0,0,0xff123456);
        return new StructurePackage("Body test",new Bounds(2,1,1,0,0,0),List.of(new ModelDefinition("New body test",tree)),
                List.of(new Cell(new Position(0,0,0),null,0),new Cell(new Position(1,0,0),solid(),-1)));
    }
    static SparseVoxelOctree solid(){var t=new SparseVoxelOctree(16);t.fill(0,0,0,16,16,16,WorldVoxels.encode(Blocks.STONE));return t.freeze();}
    static CityFrame bodyFrame(int kind,float x,float y,float z) {
        var base=CityFrame.empty(GameConfig.sandbox());
        var citizens=kind==0?List.of(new CityFrame.Citizen(1,"Synthetic",0,x,y,z,0,0,0,0,0,0,0,"Idle")):List.<CityFrame.Citizen>of();
        var horses=kind==1?List.of(new CityFrame.Horse(1,x,y,z,0,0,0)):List.<CityFrame.Horse>of();
        var cows=kind==2?List.of(new Agriculture.Cow(1,0,0,true,x,y,z,0,0,2,1)):List.<Agriculture.Cow>of();
        return new CityFrame(base.config(),0,List.of(),List.of(),List.of(),citizens,horses,base.economy(),base.addresses(),
            new Agriculture.State(false,false,List.of(),List.of(),cows,List.of()));
    }
    @Test void bodyLocalProbesMatchExhaustiveReferenceAtNegativeAndFractionalBoundaries() throws Exception {
        var random=new Random(91723);var cells=new HashMap<Position,SparseVoxelOctree>();
        for(int y=-3;y<=3;y++)for(int z=-3;z<=3;z++)for(int x=-3;x<=3;x++)
            if(random.nextBoolean())cells.put(new Position(x,y,z),random.nextBoolean()?solid():new SparseVoxelOctree(16));
        for(int kind=0;kind<4;kind++)for(int trial=0;trial<200;trial++) {
            float x=(random.nextInt(49)-24)/4f,y=(random.nextInt(49)-24)/4f,z=(random.nextInt(49)-24)/4f;
            var frame=bodyFrame(kind,x,y,z);var players=kind==3?List.of(new Protocol.Pose(1,x,y,z,0,0)):List.<Protocol.Pose>of();
            boolean expected=false;
            for(var entry:cells.entrySet())if(!entry.getValue().isEmpty()) {
                var p=entry.getKey();
                expected|=CityOccupancy.overlaps(frame,p.x(),p.y(),p.z(),1,1,1);
                for(var player:players)expected|=player.x()+.3>p.x()&&player.x()-.3<(double)p.x()+1
                    &&player.y()+1.8>p.y()&&player.y()<(double)p.y()+1
                    &&player.z()+.3>p.z()&&player.z()-.3<(double)p.z()+1;
            }
            boolean rejected=false;try{StructureOccupancy.validate(cells,frame,players);}catch(IOException blocked){rejected=true;}
            assertEquals(expected,rejected,"body kind="+kind+" at "+x+","+y+","+z);
        }
    }
    @Test void collisionWorkIsBodyLocalAndExtremeFinitePositionsCannotWrap()throws Exception {
        int[] probes={0};
        Map<Position,SparseVoxelOctree> cells=new AbstractMap<>() {
            @Override public Set<Entry<Position,SparseVoxelOctree>> entrySet(){throw new AssertionError("Must not scan structure cells");}
            @Override public SparseVoxelOctree get(Object key){probes[0]++;return null;}
        };
        StructureOccupancy.validate(cells,bodyFrame(1,-.5f,0,-.5f),List.of(new Protocol.Pose(1,-.5f,0,-.5f,0,0)));
        assertTrue(probes[0]>0&&probes[0]<=64,"Work bounded by body size, independent of placement size");
        probes[0]=0;
        for(float far:new float[]{Float.MAX_VALUE,-Float.MAX_VALUE})
            StructureOccupancy.validate(cells,bodyFrame(1,far,far,far),List.of(new Protocol.Pose(1,far,far,far,0,0)));
        assertEquals(0,probes[0],"Finite out-of-grid coordinates must not wrap into huge loops");
        assertThrows(IOException.class,()->StructureOccupancy.validate(cells,null,List.of(new Protocol.Pose(1,Float.NaN,0,0,0,0))));
    }
    @Test void exactBodyFaceAndEmptyUndoDoNotBlockButOccupiedNegativeCellDoes()throws Exception {
        var player=new Protocol.Pose(1,-.5f,0,-.5f,0,0);
        StructureOccupancy.validate(Map.of(new Position(-1,-1,-1),solid()),null,List.of(player));
        StructureOccupancy.validate(Map.of(new Position(-1,0,-1),new SparseVoxelOctree(16)),null,List.of(player));
        assertThrows(IOException.class,()->StructureOccupancy.validate(Map.of(new Position(-1,0,-1),solid()),null,List.of(player)));
    }
    @Test void offlineHorseEnteringAfterStagingRejectsWithoutDependenciesGeometryOrSave()throws Exception {
        var save=folder.resolve("offline.dat");var local=new LocalGame(save,42);local.city=city(false);
        var world=new World(42,local.models);var engine=new StructureEngine();int models=local.models.entries().size();
        try(var prepared=engine.prepare(asset(),TARGET,0,world.structureSnapshot(),()->false,p->{})) {
            local.city=city(true);
            assertThrows(IOException.class,()->prepared.commit("owner",local.structureAuthority(world)));
        }
        assertEquals(models,local.models.entries().size());assertTrue(local.edits.isEmpty());assertFalse(Files.exists(save));
        assertEquals(0,world.cell(320,128,320).occupied());assertEquals(0,world.cell(321,128,320).occupied());
        assertThrows(IOException.class,()->engine.undo("owner",local.structureAuthority(world)));
    }
    @Test void offlinePlayerEnteringAfterStagingRejectsAndCanRetryAfterLeaving()throws Exception {
        var local=new LocalGame(folder.resolve("player.dat"),42);var world=new World(42,local.models);var engine=new StructureEngine();
        var pose=new AtomicReference<Protocol.Pose>();var authority=local.structureAuthority(world,pose::get);
        try(var prepared=engine.prepare(asset(),TARGET,0,world.structureSnapshot(),()->false,p->{})) {
            pose.set(new Protocol.Pose(1,320.5f,128,320.5f,0,0));
            assertThrows(IOException.class,()->prepared.commit("owner",authority));
        }
        assertTrue(local.edits.isEmpty());pose.set(null);
        try(var prepared=engine.prepare(asset(),TARGET,0,world.structureSnapshot(),()->false,p->{})){prepared.commit("owner",authority);}
        pose.set(new Protocol.Pose(1,320.5f,128,320.5f,0,0));engine.undo("owner",authority);
        assertEquals(0,world.cell(320,128,320).occupied(),"Undo removing geometry is safe around bodies");
    }
    @Test void serverHorseEnteringAfterStagingRejectsAtomically()throws Exception {
        var tls=SecureTransport.server(folder.resolve("synthetic-tls"));var save=folder.resolve("server.dat");
        try(var server=new MultiplayerServer("127.0.0.1",0,save,new AccountStore(folder.resolve("synthetic-accounts")),tls.context(),42,GameConfig.sandbox())) {
            var method=MultiplayerServer.class.getDeclaredMethod("structureAuthority");method.setAccessible(true);
            var authority=(StructureEngine.Authority)method.invoke(server);var engine=new StructureEngine();
            var field=MultiplayerServer.class.getDeclaredField("city");field.setAccessible(true);field.set(server,city(false));
            int models=authority.models().entries().size();
            try(var prepared=engine.prepare(asset(),TARGET,0,authority,()->false,p->{})) {
                synchronized(authority.lock()){field.set(server,city(true));}
                assertThrows(IOException.class,()->prepared.commit("owner",authority));
            }
            assertEquals(models,authority.models().entries().size());assertEquals(0,authority.cell(320,128,320).occupied());
            assertEquals(0,authority.cell(321,128,320).occupied());assertFalse(Files.exists(save));
            // The same authoritative commit guard covers undo restoring nonempty geometry.
            synchronized(authority.lock()) {
                assertThrows(IOException.class,()->authority.commit(authority.models().copy(),Map.of(TARGET,solid())));
            }
        }
    }
    @Test void productionProtocolRejectsHorseThenPlacesAndUndoesAfterItLeaves()throws Exception {
        var tls=SecureTransport.server(folder.resolve("network-tls"));
        try(var server=new MultiplayerServer("127.0.0.1",0,folder.resolve("network.dat"),new AccountStore(folder.resolve("network-accounts")),tls.context(),42,GameConfig.sandbox())) {
            var field=MultiplayerServer.class.getDeclaredField("city");field.setAccessible(true);
            synchronized(server){field.set(server,city(true));}
            var thread=new Thread(()->{try{server.run();}catch(Exception ignored){}});thread.setDaemon(true);thread.start();
            try(var client=new MultiplayerClient("127.0.0.1",server.port(),"CollisionPlayer","synthetic-password".toCharArray(),true,tls.fingerprint())) {
                var view=new StructureMultiplayerTest.View(client);var views=List.of(view);
                int models=client.models.entries().size();
                assertTrue(client.placeStructure(asset().encode(),TARGET,0));
                StructureMultiplayerTest.waitFor(views,()->!client.structurePending());
                assertTrue(client.structureMessage.contains("city entity"),client.structureMessage);
                assertEquals(models,client.models.entries().size());assertEquals(0,view.geometry.cell(320,128,320).occupied());
                synchronized(server){field.set(server,city(false));}
                assertTrue(client.placeStructure(asset().encode(),TARGET,0));
                StructureMultiplayerTest.waitFor(views,()->!client.structurePending());
                assertTrue(client.structureMessage.startsWith("Structure placed"),client.structureMessage);
                synchronized(server){field.set(server,city(true));}
                assertTrue(client.undoStructure());StructureMultiplayerTest.waitFor(views,()->!client.structurePending());
                assertTrue(client.structureMessage.contains("undone"),client.structureMessage);
                assertEquals(0,view.geometry.cell(320,128,320).occupied());
            }
        }
    }
    @Test void offlineUndoRestoreGuardRejectsSolidButAllowsEmptyCells()throws Exception {
        var local=new LocalGame(folder.resolve("undo.dat"),42);local.city=city(true);var world=new World(42,local.models);
        var authority=local.structureAuthority(world);
        synchronized(authority.lock()) {
            assertThrows(IOException.class,()->authority.commit(local.models.copy(),Map.of(TARGET,solid())));
            authority.commit(local.models.copy(),Map.of(TARGET,new SparseVoxelOctree(16)));
        }
        assertEquals(0,world.cell(320,128,320).occupied());
    }
}
