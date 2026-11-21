package dev.jayms.render;
import dev.jayms.*;
import dev.jayms.net.*;
import dev.jayms.net.model.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.lwjgl.system.MemoryUtil;
import static org.lwjgl.opengl.GL33.*;
import static org.junit.jupiter.api.Assertions.*;

public final class ReplaySkyPipelineAssertions {
 static long deadline(){return System.nanoTime()+100_000_000L;}
 static WorldSkyVisibility sky(int block){var tree=new SparseVoxelOctree(256);tree.fill(0,0,0,16,16,16,WorldVoxels.encode(block));return WorldSkyVisibility.build(new WorldSkyVisibility.Input(List.of(new WorldSkyVisibility.Geometry(new ChunkPos(0,10,0),tree.freeze())),List.of(),new ModelLibrary()));}
 static int program(ShaderProgram shader)throws Exception{var f=ShaderProgram.class.getDeclaredField("programId");f.setAccessible(true);return f.getInt(shader);}
 static void bind(RenderPipeline pipeline,ShaderProgram shader)throws Exception{shader.bind();var m=RenderPipeline.class.getDeclaredMethod("bindSceneLighting",ShaderProgram.class);m.setAccessible(true);m.invoke(pipeline,shader);}
 static int columns(ShaderProgram shader)throws Exception{return glGetUniformi(program(shader),glGetUniformLocation(program(shader),"uSkyColumns"));}
 static int atlas(RenderPipeline pipeline)throws Exception{var f=RenderPipeline.class.getDeclaredField("replaySkyAtlas");f.setAccessible(true);return f.getInt(pipeline);}
 static void complete(RenderPipeline pipeline,World world){int steps=0;while(!pipeline.replaySkyAtlasReady(world)&&steps++<10000){int n=pipeline.pumpReplaySky(world,deadline(),768);assertTrue(n>=0&&n<=768);}assertTrue(pipeline.replaySkyAtlasReady(world));}
 public static void run()throws Exception {
  var key=new AtomicReference<Object>(new Object());var payload=new AtomicReference<WorldSkyVisibility>(sky(Blocks.STONE));var present=new AtomicBoolean(true);
  try(var world=new World(42,new ModelLibrary(),Terrain.CURRENT_VERSION);var other=new World(42,new ModelLibrary(),Terrain.CURRENT_VERSION);var pipeline=new RenderPipeline(GraphicsProfile.preset(GraphicsProfile.Preset.LOW));var shader=new ShaderProgram("shaders/voxel.vert","shaders/voxel.frag")) {
   world.attachReplayReadView(new World.ReplayReadView(){public boolean ready(int x,int y,int z){return false;}public SparseVoxelOctree cell(int x,int y,int z){throw new World.UnloadedReplayCellException(x,y,z);}public World.ReplaySkySource skySource(){if(!present.get())return null;return new World.ReplaySkySource(){public World owner(){return world;}public WorldSkyVisibility data(){return payload.get();}public Object key(){return key.get();}public boolean current(){return present.get();}};}public void close(){}});
   assertFalse(pipeline.replaySkyAtlasReady(world));assertThrows(IllegalArgumentException.class,()->pipeline.pumpReplaySky(other,deadline(),768));assertThrows(IllegalArgumentException.class,()->pipeline.pumpReplaySky(world,deadline(),262145));assertEquals(0,pipeline.pumpReplaySky(world,System.nanoTime()-1,768));assertEquals(0,pipeline.pumpReplaySky(world,deadline(),0));
   assertEquals(768,pipeline.pumpReplaySky(world,deadline(),768));assertFalse(pipeline.replaySkyAtlasReady(world));bind(pipeline,shader);assertEquals(0,columns(shader));complete(pipeline,world);bind(pipeline,shader);assertEquals(256,columns(shader));assertEquals(0,glGetUniformi(program(shader),glGetUniformLocation(program(shader),"uHasIrradiance")));int prior=atlas(pipeline);assertTrue(glIsTexture(prior));
   assertEquals(0,pipeline.pumpReplaySky(world,deadline(),768));var error=new AtomicReference<Throwable>();var thread=new Thread(()->{try{assertThrows(IllegalStateException.class,()->pipeline.pumpReplaySky(world,deadline(),768));}catch(Throwable e){error.set(e);}});thread.start();thread.join(5000);assertFalse(thread.isAlive());assertNull(error.get());
   key.set(new Object());payload.set(sky(Blocks.GLASS));assertFalse(pipeline.replaySkyAtlasReady(world));bind(pipeline,shader);assertEquals(0,columns(shader));int sent=pipeline.pumpReplaySky(world,deadline(),768);assertTrue(sent>=0&&sent<=768);assertEquals(prior,atlas(pipeline));assertTrue(glIsTexture(prior));assertFalse(pipeline.replaySkyAtlasReady(world));
   complete(pipeline,world);bind(pipeline,shader);assertEquals(256,columns(shader));int current=atlas(pipeline);assertNotEquals(prior,current);glFinish();glActiveTexture(GL_TEXTURE4);assertEquals(current,glGetInteger(GL_TEXTURE_BINDING_3D));int w=glGetTexLevelParameteri(GL_TEXTURE_3D,0,GL_TEXTURE_WIDTH),h=glGetTexLevelParameteri(GL_TEXTURE_3D,0,GL_TEXTURE_HEIGHT);assertTrue((long)w*h<1000000);assertTrue(payload.get().data.length<=w*h);var pixels=MemoryUtil.memAllocInt(w*h);int priorRead=glGetInteger(GL_READ_FRAMEBUFFER_BINDING),readFbo=glGenFramebuffers();try{glBindFramebuffer(GL_READ_FRAMEBUFFER,readFbo);glFramebufferTextureLayer(GL_READ_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,current,0,0);assertEquals(GL_FRAMEBUFFER_COMPLETE,glCheckFramebufferStatus(GL_READ_FRAMEBUFFER));glReadBuffer(GL_COLOR_ATTACHMENT0);glReadPixels(0,0,w,h,GL_RED_INTEGER,GL_INT,pixels);for(int i=0;i<payload.get().data.length;i++)assertEquals(payload.get().data[i],pixels.get(i));}finally{glBindFramebuffer(GL_READ_FRAMEBUFFER,priorRead);glDeleteFramebuffers(readFbo);MemoryUtil.memFree(pixels);}
   present.set(false);assertFalse(pipeline.replaySkyAtlasReady(world));assertEquals(0,pipeline.pumpReplaySky(world,deadline(),768));bind(pipeline,shader);assertEquals(0,columns(shader));assertTrue(glIsTexture(current));assertFalse(SkyVisibilityScheduler.ceilingReady(null,world,0,0));assertEquals(GL_NO_ERROR,glGetError());glActiveTexture(GL_TEXTURE0);
  }
 }
}
