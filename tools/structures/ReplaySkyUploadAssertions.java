package dev.jayms.render;
import dev.jayms.*;
import dev.jayms.net.*;
import dev.jayms.net.model.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.lwjgl.system.MemoryUtil;
import static org.lwjgl.opengl.GL33.*;
import static org.junit.jupiter.api.Assertions.*;

/** Native-context assertions only; no application startup or readiness promotion. */
public final class ReplaySkyUploadAssertions {
 static long deadline(){return System.nanoTime()+100_000_000L;}
 static int atlas(SceneLightingUpload upload)throws Exception{var f=SceneLightingUpload.class.getDeclaredField("atlas");f.setAccessible(true);return f.getInt(upload);}
 static void complete(SceneLightingUpload upload){int steps=0;while(!upload.ready()&&steps++<10000){int bytes=upload.pump(deadline(),256,256);assertTrue(bytes>=0&&bytes<=256);}assertTrue(upload.ready());}
 public static void run()throws Exception {
  try(var world=new World(42,new ModelLibrary(),Terrain.CURRENT_VERSION);var other=new World(42,new ModelLibrary(),Terrain.CURRENT_VERSION)) {
   var root=new SparseVoxelOctree(256);root.fill(0,0,0,16,16,16,WorldVoxels.encode(Blocks.STONE));var data=WorldSkyVisibility.build(new WorldSkyVisibility.Input(List.of(new WorldSkyVisibility.Geometry(new ChunkPos(0,10,0),root.freeze())),List.of(),new ModelLibrary()));var key=new AtomicReference<Object>(new Object());
   world.attachReplayReadView(new World.ReplayReadView(){public boolean ready(int x,int y,int z){return false;}public SparseVoxelOctree cell(int x,int y,int z){throw new World.UnloadedReplayCellException(x,y,z);}public World.ReplaySkySource skySource(){return new World.ReplaySkySource(){public World owner(){return world;}public WorldSkyVisibility data(){return data;}public Object key(){return key.get();}public boolean current(){return true;}};}public void close(){}});
   int prior=0,transferred=0;
   try {
    try(var old=new SceneLightingUpload(world,null,null,false)){complete(old);prior=old.take().atlas();}assertTrue(glIsTexture(prior));
    var snapshot=world.replaySkySnapshot();int partial;
    try(var upload=SceneLightingUpload.replaySky(world,snapshot,null)) {
     assertTrue(upload.valid(world));assertFalse(upload.valid(other));assertEquals(0,upload.pump(System.nanoTime()-1,256,256));assertEquals(0,upload.pump(deadline(),255,256));assertFalse(upload.ready());
     assertEquals(256,upload.pump(deadline(),256,256));partial=atlas(upload);assertTrue(glIsTexture(partial));assertTrue(glIsTexture(prior));assertThrows(IllegalStateException.class,upload::take);
     var error=new AtomicReference<Throwable>();var thread=new Thread(()->{try{assertThrows(IllegalStateException.class,upload::ready);assertThrows(IllegalStateException.class,upload::close);}catch(Throwable e){error.set(e);}});thread.start();thread.join(5000);assertFalse(thread.isAlive());assertNull(error.get());
     key.set(new Object());assertFalse(upload.valid(world));assertFalse(upload.ready());assertThrows(IllegalStateException.class,()->upload.pump(deadline(),256,256));assertThrows(IllegalStateException.class,upload::take);
    }assertFalse(glIsTexture(partial));assertTrue(glIsTexture(prior));
    try(var upload=SceneLightingUpload.replaySky(world,world.replaySkySnapshot(),null)) {
     complete(upload);assertTrue(upload.valid(world));var resources=upload.take();transferred=resources.atlas();assertTrue(glIsTexture(transferred));assertFalse(upload.ready());assertFalse(upload.valid(world));assertThrows(IllegalStateException.class,upload::take);assertThrows(IllegalStateException.class,()->upload.pump(deadline(),256,256));
     var readback=MemoryUtil.memAllocInt(upload.width*upload.height*upload.depth);
     try {glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_3D,transferred);glGetTexImage(GL_TEXTURE_3D,0,GL_RED_INTEGER,GL_INT,readback);for(int i=0;i<data.data.length;i++)assertEquals(data.data[i],readback.get(i));for(int i=data.data.length;i<readback.capacity();i++)assertEquals(0,readback.get(i));}finally{MemoryUtil.memFree(readback);}
    }assertTrue(glIsTexture(transferred));assertTrue(glIsTexture(prior));
    try(var upload=SceneLightingUpload.replaySky(world,world.replaySkySnapshot(),null)){complete(upload);int staged=atlas(upload);key.set(new Object());assertFalse(upload.ready());assertThrows(IllegalStateException.class,upload::take);assertTrue(glIsTexture(staged));}
    for(boolean direct:new boolean[]{true,false})try(var stores=new LightingStores(direct);var upload=SceneLightingUpload.replaySky(world,world.replaySkySnapshot(),stores)) {
     int steps=0;while(!upload.ready()&&steps++<10000){int sent=upload.pump(deadline(),768,768);assertTrue(sent>=0&&sent<=768);}assertTrue(upload.ready());int texture=upload.take().atlas();
     glFinish();glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_3D,texture);int w=glGetTexLevelParameteri(GL_TEXTURE_3D,0,GL_TEXTURE_WIDTH),h=glGetTexLevelParameteri(GL_TEXTURE_3D,0,GL_TEXTURE_HEIGHT),d=glGetTexLevelParameteri(GL_TEXTURE_3D,0,GL_TEXTURE_DEPTH);assertTrue((long)w*h*d<=1000000);var buffer=MemoryUtil.memAllocInt(w*h*d);
     try{glGetTexImage(GL_TEXTURE_3D,0,GL_RED_INTEGER,GL_INT,buffer);for(int i=0;i<data.data.length;i++)assertEquals(data.data[i],buffer.get(i));}finally{MemoryUtil.memFree(buffer);stores.releaseTexture(texture);}
    }
    assertTrue(glIsTexture(prior));assertEquals(GL_NO_ERROR,glGetError());assertFalse(SkyVisibilityScheduler.ceilingReady(null,world,0,0));
   }finally{if(prior!=0)glDeleteTextures(prior);if(transferred!=0)glDeleteTextures(transferred);glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_3D,0);}
  }
 }
}
