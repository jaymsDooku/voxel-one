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

public class RegionReplayGeometryWorldBridgeTest {
 @TempDir Path folder;
 ReadbackMetadataTest fixture(String name)throws Exception{var f=new ReadbackMetadataTest();f.folder=Files.createDirectories(folder.resolve(name));return f;}
 @Test public void sceneBridgeTracksDesiredSourceAndCloseWithoutReadinessPromotion()throws Exception {
  var f=fixture("scene");var worker=f.worker(f.fixture());var a=new ChunkPos(0,10,0);var b=new ChunkPos(1,10,0);
  try {
   ReadbackMetadataTest.ready(worker);var local=worker.metadataResult(ReadbackMetadataTest.REV);
   try(var world=new World(local.seed,local.models,local.generatorVersion);var foreign=new World(local.seed,local.models,local.generatorVersion);var lighting=new WorldLighting()) {
    var binding=worker.bindTarget(local,world);var scene=binding.openScene(world,1,131072);var p=binding.openLightingPump(world,2,8192,16384);assertNull(world.replayGeometrySnapshot());assertTrue(world.replayLightingChunks().isEmpty());p.desired(List.of(a,b));assertNull(world.replayGeometrySnapshot());RegionReplayLightingPumpTest.ready(p,2);
    var held=world.replayGeometrySnapshot();assertNotNull(held);assertTrue(held.current(world));assertFalse(held.current(foreign));assertEquals(Set.of(a,b),held.roots().keySet());assertEquals(held.roots(),world.replayLightingChunks());assertSame(held.key(),world.replayGeometrySnapshot().key());assertThrows(UnsupportedOperationException.class,()->held.roots().clear());
    for(int i=0;i<3;i++)assertNull(lighting.update(world,0,0,1,false,null,null,Long.MAX_VALUE));assertEquals(0,lighting.submitted);assertFalse(SkyVisibilityScheduler.ceilingReady(null,world,0,0));assertFalse(world.isLoaded(0,160,0));assertFalse(world.replayDataReady(0,160,0));assertEquals(0,world.editsVersion());assertFalse(local.replayComplete());assertThrows(IOException.class,local::save);
    p.desired(List.of(b));assertFalse(held.current(world));assertThrows(IllegalStateException.class,held::roots);var subset=world.replayGeometrySnapshot();assertEquals(Set.of(b),subset.roots().keySet());p.desired(List.of(a,b));assertFalse(subset.current(world));assertNull(world.replayGeometrySnapshot());RegionReplayLightingPumpTest.ready(p,2);var current=world.replayGeometrySnapshot();assertNotSame(held.key(),current.key());scene.close();assertFalse(current.current(world));assertThrows(IllegalStateException.class,current::roots);assertThrows(IllegalStateException.class,world::replayGeometrySnapshot);
   }
  }finally{RegionReplayWorkerTest.closed(worker);f.noSpools();}
 }
 @Test public void ownerModelsAndWorldCloseRejectHeldGeometry()throws Exception {
  var f=fixture("fence");var worker=f.worker(f.fixture());
  try {
   ReadbackMetadataTest.ready(worker);var local=worker.metadataResult(ReadbackMetadataTest.REV);
   try(var world=new World(local.seed,local.models,local.generatorVersion)) {
    var binding=worker.bindTarget(local,world);binding.openScene(world,1,131072);var p=binding.openLightingPump(world,1,8192,8192);p.desired(List.of(new ChunkPos(0,10,0)));RegionReplayLightingPumpTest.ready(p,1);var held=world.replayGeometrySnapshot();var error=new AtomicReference<Throwable>();var t=new Thread(()->{try{assertFalse(held.current(world));assertThrows(IllegalStateException.class,held::roots);}catch(Throwable e){error.set(e);}});t.start();t.join(5000);assertFalse(t.isAlive());assertNull(error.get());assertTrue(held.current(world));local.models=local.models.copy();assertFalse(held.current(world));assertThrows(IllegalStateException.class,held::roots);world.close();assertFalse(held.current(world));assertThrows(IllegalStateException.class,world::replayGeometrySnapshot);
   }
  }finally{RegionReplayWorkerTest.closed(worker);f.noSpools();}
 }
 static World.ReplayReadView view(AtomicReference<World.ReplayGeometrySource> source){return new World.ReplayReadView(){public boolean ready(int x,int y,int z){return false;}public SparseVoxelOctree cell(int x,int y,int z){throw new World.UnloadedReplayCellException(x,y,z);}public World.ReplayGeometrySource geometrySource(){return source.get();}public void close(){}};}
 static World.ReplayGeometrySource source(World w,Map<ChunkPos,SparseVoxelOctree> roots,Object key,AtomicBoolean valid){return new World.ReplayGeometrySource(){public World owner(){return w;}public Map<ChunkPos,SparseVoxelOctree> roots(){return roots;}public Object key(){return key;}public boolean current(){return valid.get();}};}
 @Test public void absentAndMalformedProvidersNeverSupplyGeometry()throws Exception {
  try(var world=new World();var other=new World()) {
   assertNull(world.replayGeometrySnapshot());assertNull(world.replayLightingChunks());var supplied=new AtomicReference<World.ReplayGeometrySource>();world.attachReplayReadView(view(supplied));assertNull(world.replayGeometrySnapshot());var roots=new LinkedHashMap<ChunkPos,SparseVoxelOctree>();var a=new ChunkPos(0,0,0);roots.put(a,new SparseVoxelOctree(256).freeze());var valid=new AtomicBoolean(true);
   supplied.set(source(other,roots,new Object(),valid));assertThrows(IllegalStateException.class,world::replayGeometrySnapshot);supplied.set(source(world,roots,null,valid));assertThrows(IllegalStateException.class,world::replayGeometrySnapshot);supplied.set(source(world,Map.of(),new Object(),valid));assertThrows(IllegalStateException.class,world::replayGeometrySnapshot);
   roots.put(a,new SparseVoxelOctree(256));supplied.set(source(world,roots,new Object(),valid));assertThrows(IllegalStateException.class,world::replayGeometrySnapshot);roots.put(a,new SparseVoxelOctree(16).freeze());assertThrows(IllegalStateException.class,world::replayGeometrySnapshot);roots.put(a,new SparseVoxelOctree(256).freeze());valid.set(false);assertNull(world.replayGeometrySnapshot());valid.set(true);var held=world.replayGeometrySnapshot();assertTrue(held.current(world));roots.put(a,new SparseVoxelOctree(256).freeze());assertFalse(held.current(world));assertThrows(IllegalStateException.class,held::roots);
   roots.clear();for(int i=0;i<289;i++)roots.put(new ChunkPos(i,0,0),new SparseVoxelOctree(256).freeze());assertThrows(IllegalStateException.class,world::replayGeometrySnapshot);
  }
 }
}
