package dev.jayms.render;
import dev.jayms.*;
import dev.jayms.net.*;
import dev.jayms.net.model.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.lwjgl.opengl.GL33.*;
import static org.junit.jupiter.api.Assertions.*;

public final class ReplaySkyFrameAssertions {
 static WorldSkyVisibility large(){var chunks=new ArrayList<WorldSkyVisibility.Geometry>();for(int i=0;i<128;i++){var t=new SparseVoxelOctree(256);t.fill(0,0,0,256,256,256,WorldVoxels.encode(Blocks.GLASS));chunks.add(new WorldSkyVisibility.Geometry(new ChunkPos(0,10+i*2,0),t.freeze()));}return WorldSkyVisibility.build(new WorldSkyVisibility.Input(chunks,List.of(),new ModelLibrary()));}
 static void frame(RenderPipeline p,World w){p.update(w,0,0,20_000_000L);assertTrue(p.skyGpuBytes>=0&&p.skyGpuBytes<=262144);assertFalse(p.nearLightingReady(w,0,0));}
 static void complete(RenderPipeline p,World w){int steps=0;while(!p.replaySkyAtlasReady(w)&&steps++<200){frame(p,w);glFlush();}assertTrue(p.replaySkyAtlasReady(w));}
 public static void run()throws Exception {
  var key=new AtomicReference<Object>(new Object());var data=new AtomicReference<WorldSkyVisibility>(large());var present=new AtomicBoolean(true);
  try(var world=new World(42,new ModelLibrary(),Terrain.CURRENT_VERSION);var resident=new World(42,new ModelLibrary(),Terrain.CURRENT_VERSION);var second=new World(43,new ModelLibrary(),Terrain.CURRENT_VERSION);var p=new RenderPipeline(GraphicsProfile.preset(GraphicsProfile.Preset.LOW));var shader=new ShaderProgram("shaders/voxel.vert","shaders/voxel.frag")) {
   world.attachReplayReadView(new World.ReplayReadView(){public boolean ready(int x,int y,int z){return false;}public SparseVoxelOctree cell(int x,int y,int z){throw new World.UnloadedReplayCellException(x,y,z);}public World.ReplaySkySource skySource(){if(!present.get())return null;return new World.ReplaySkySource(){public World owner(){return world;}public WorldSkyVisibility data(){return data.get();}public Object key(){return key.get();}public boolean current(){return present.get();}};}public void close(){}});
   second.attachReplayReadView(new World.ReplayReadView(){public boolean ready(int x,int y,int z){return false;}public SparseVoxelOctree cell(int x,int y,int z){throw new World.UnloadedReplayCellException(x,y,z);}public World.ReplaySkySource skySource(){if(!present.get())return null;return new World.ReplaySkySource(){public World owner(){return second;}public WorldSkyVisibility data(){return data.get();}public Object key(){return key.get();}public boolean current(){return present.get();}};}public void close(){}});
   p.update(world,0,0,0);assertFalse(p.replaySkyAtlasReady(world));frame(p,world);assertFalse(p.replaySkyAtlasReady(world));assertTrue(p.skyGpuBytes>0);ReplaySkyPipelineAssertions.bind(p,shader);assertEquals(0,ReplaySkyPipelineAssertions.columns(shader));
   complete(p,world);ReplaySkyPipelineAssertions.bind(p,shader);assertEquals(256,ReplaySkyPipelineAssertions.columns(shader));int program=ReplaySkyPipelineAssertions.program(shader);assertEquals(0,glGetUniformi(program,glGetUniformLocation(program,"uHasIrradiance")));assertEquals(0,glGetUniformi(program,glGetUniformLocation(program,"uClusterReady")));assertEquals(0,p.lightingCoverage(world,0,0).submitted());assertFalse(SkyVisibilityScheduler.ceilingReady(null,world,0,0));
   int prior=ReplaySkyPipelineAssertions.atlas(p);key.set(new Object());data.set(ReplaySkyPipelineAssertions.sky(Blocks.STONE));ReplaySkyPipelineAssertions.bind(p,shader);assertEquals(0,ReplaySkyPipelineAssertions.columns(shader));assertTrue(glIsTexture(prior));complete(p,world);ReplaySkyPipelineAssertions.bind(p,shader);assertEquals(256,ReplaySkyPipelineAssertions.columns(shader));
   present.set(false);frame(p,world);assertFalse(p.replaySkyAtlasReady(world));ReplaySkyPipelineAssertions.bind(p,shader);assertEquals(0,ReplaySkyPipelineAssertions.columns(shader));
   p.update(resident,0,0,20_000_000L);assertFalse(p.replaySkyAtlasReady(world));present.set(true);key.set(new Object());complete(p,world);ReplaySkyPipelineAssertions.bind(p,shader);assertEquals(256,ReplaySkyPipelineAssertions.columns(shader));assertEquals(0,glGetUniformi(program,glGetUniformLocation(program,"uHasIrradiance")));assertFalse(p.nearLightingReady(world,0,0));assertEquals(0,world.editsVersion());assertTrue(world.getLoadedChunks().isEmpty());
   // Switch with a partial candidate, then switch again after a committed atlas.
   key.set(new Object());data.set(large());frame(p,world);assertFalse(p.replaySkyAtlasReady(world));
   frame(p,second);assertFalse(p.replaySkyAtlasReady(world));assertFalse(p.replaySkyAtlasReady(second));
   complete(p,second);assertFalse(p.replaySkyAtlasReady(world));ReplaySkyPipelineAssertions.bind(p,shader);assertEquals(256,ReplaySkyPipelineAssertions.columns(shader));
   complete(p,world);assertFalse(p.replaySkyAtlasReady(second));assertTrue(p.replaySkyAtlasReady(world));
   world.close();ReplaySkyPipelineAssertions.bind(p,shader);assertEquals(0,ReplaySkyPipelineAssertions.columns(shader));assertEquals(GL_NO_ERROR,glGetError());glActiveTexture(GL_TEXTURE0);
  }
 }
}
