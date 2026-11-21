package dev.jayms.net;
import dev.jayms.*;
import dev.jayms.net.model.*;
import dev.jayms.render.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

public class RegionReplaySkyWorldBridgeTest {
 @TempDir Path folder;
 ReadbackMetadataTest fixture(String name)throws Exception{var f=new ReadbackMetadataTest();f.folder=Files.createDirectories(folder.resolve(name));return f;}
 @Test void actualSceneBridgeTracksSourceAndDesiredGenerationWithoutLightingAdmission()throws Exception {
  var f=fixture("scene");var path=f.fixture();var worker=f.worker(path);
  try {
   ReadbackMetadataTest.ready(worker);var local=worker.metadataResult(ReadbackMetadataTest.REV);
   try(var world=new World(local.seed,local.models,local.generatorVersion);var foreign=new World(local.seed,local.models,local.generatorVersion)) {
    var binding=worker.bindTarget(local,world);var scene=binding.openScene(world,1,131072);var p=binding.openSkyPump(world,1,8000000,RegionReplaySkyWorkerTest.LIMITS);
    assertNull(world.replaySkySnapshot());p.desired(List.of(new ChunkPos(0,0,0)));p.requestMerge();assertNull(world.replaySkySnapshot());RegionReplaySkyCoverageTest.merged(p);
    var old=world.replaySkySnapshot();assertNotNull(old);assertTrue(old.current(world));assertFalse(old.current(foreign));assertEquals(0,old.data().sampleKnown(.5f,150,.5f,-1));assertSame(old.key(),world.replaySkySnapshot().key());
    assertFalse(SkyVisibilityScheduler.ceilingReady(null,world,0,0));assertTrue(world.replayLightingChunks().isEmpty());assertFalse(world.isLoaded(0,160,0));assertFalse(world.replayDataReady(0,160,0));assertEquals(0,world.editsVersion());assertFalse(local.replayComplete());assertThrows(IOException.class,local::save);
    p.desired(List.of(new ChunkPos(2,0,0)));assertFalse(old.current(world));assertThrows(IllegalStateException.class,old::data);assertNull(world.replaySkySnapshot());RegionReplaySkyCoverageTest.merged(p);var current=world.replaySkySnapshot();assertNotSame(old.key(),current.key());assertEquals(-1,current.data().sampleKnown(.5f,150,.5f,-1));assertEquals(1,current.data().sampleKnown(32.5f,150,.5f,-1));
    scene.close();assertFalse(current.current(world));assertThrows(IllegalStateException.class,current::data);assertThrows(IllegalStateException.class,world::replaySkySnapshot);
   }
  }finally{RegionReplayWorkerTest.closed(worker);f.noSpools();}
 }
 @Test void modelMutationOwnerAndWorldCloseInvalidateHeldSnapshot()throws Exception {
  var f=fixture("fences");var path=f.fixture();var worker=f.worker(path);
  try {
   ReadbackMetadataTest.ready(worker);var local=worker.metadataResult(ReadbackMetadataTest.REV);
   try(var world=new World(local.seed,local.models,local.generatorVersion)) {
    var b=worker.bindTarget(local,world);b.openScene(world,1,131072);var p=b.openSkyPump(world,1,8000000,RegionReplaySkyWorkerTest.LIMITS);p.desired(List.of(new ChunkPos(0,0,0)));p.requestMerge();RegionReplaySkyCoverageTest.merged(p);var held=world.replaySkySnapshot();
    var error=new AtomicReference<Throwable>();var t=new Thread(()->{try{assertFalse(held.current(world));assertThrows(IllegalStateException.class,held::data);}catch(Throwable e){error.set(e);}});t.start();t.join(5000);assertFalse(t.isAlive());assertNull(error.get());assertTrue(held.current(world));
    local.models=local.models.copy();assertFalse(held.current(world));assertThrows(IllegalStateException.class,held::data);assertThrows(IllegalStateException.class,world::replaySkySnapshot);world.close();assertFalse(held.current(world));
   }
  }finally{RegionReplayWorkerTest.closed(worker);f.noSpools();}
 }
 static World.ReplayReadView view(AtomicReference<World.ReplaySkySource> source){return new World.ReplayReadView(){public boolean ready(int x,int y,int z){return false;}public SparseVoxelOctree cell(int x,int y,int z){throw new World.UnloadedReplayCellException(x,y,z);}public World.ReplaySkySource skySource(){return source.get();}public void close(){}};}
 static WorldSkyVisibility known(){return WorldSkyVisibility.build(new WorldSkyVisibility.Input(List.of(new WorldSkyVisibility.Geometry(new ChunkPos(0,0,0),new SparseVoxelOctree(256).freeze())),List.of(),new ModelLibrary()));}
 static World.ReplaySkySource source(World owner,WorldSkyVisibility data,Object key,AtomicBoolean valid){return new World.ReplaySkySource(){public World owner(){return owner;}public WorldSkyVisibility data(){return data;}public Object key(){return key;}public boolean current(){return valid.get();}};}
 @Test void residentAndAbsentProvidersStayUnknownAndBadProviderRejects()throws Exception {
  try(var world=new World(42,new ModelLibrary(),Terrain.CURRENT_VERSION);var other=new World(42,new ModelLibrary(),Terrain.CURRENT_VERSION)) {
   assertNull(world.replaySkySnapshot());var supplied=new AtomicReference<World.ReplaySkySource>();world.attachReplayReadView(view(supplied));assertNull(world.replaySkySnapshot());var valid=new AtomicBoolean(true);var data=known();
   supplied.set(source(other,data,new Object(),valid));assertThrows(IllegalStateException.class,world::replaySkySnapshot);
   supplied.set(source(world,data,null,valid));assertThrows(IllegalStateException.class,world::replaySkySnapshot);
   var empty=WorldSkyVisibility.build(new WorldSkyVisibility.Input(List.of(),List.of(),new ModelLibrary()));supplied.set(source(world,empty,new Object(),valid));assertThrows(IllegalStateException.class,world::replaySkySnapshot);
   supplied.set(source(world,data,new Object(),valid));valid.set(false);assertNull(world.replaySkySnapshot());valid.set(true);var held=world.replaySkySnapshot();assertTrue(held.current(world));valid.set(false);assertFalse(held.current(world));assertThrows(IllegalStateException.class,held::data);world.close();assertThrows(IllegalStateException.class,world::replaySkySnapshot);
  }
 }
}
