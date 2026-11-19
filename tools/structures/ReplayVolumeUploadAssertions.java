package dev.jayms.render;
import dev.jayms.*;
import java.util.concurrent.atomic.*;
import org.lwjgl.system.MemoryUtil;
import static org.lwjgl.opengl.GL33.*;
import static org.junit.jupiter.api.Assertions.*;

/** Actual replay CPU bake and GL staging; no Main or render admission. */
public final class ReplayVolumeUploadAssertions {
 static long deadline(){return System.nanoTime()+100_000_000L;}
 static int texture(SceneLightingUpload upload,String field)throws Exception{var f=SceneLightingUpload.class.getDeclaredField(field);f.setAccessible(true);return f.getInt(upload);}
 static LightVolume bake(WorldLighting lighting,World world)throws Exception{assertNull(lighting.updateReplay(world,0,0,1,false,Long.MAX_VALUE));ReplayLightingBakeTest.baked(lighting);var volume=lighting.updateReplay(world,0,0,1,false,Long.MAX_VALUE,false);assertNotNull(volume);return volume;}
 static void complete(SceneLightingUpload upload){int steps=0;while(!upload.ready()&&steps++<10000){int n=upload.pump(deadline(),262144);assertTrue(n>=0&&n<=262144);}assertTrue(upload.ready());}
 static void release(SceneLightingUpload.Resources r){if(r==null)return;glDeleteTextures(r.atlas());glDeleteTextures(r.irradiance());glDeleteTextures(r.fineTexture());glDeleteBuffers(r.fineBuffer());if(r.transport()!=null)r.transport().close();}
 public static void run()throws Exception {
  try(var world=new World();var other=new World();var lighting=new WorldLighting()) {
   var fixture=new ReplayJointLightingCoverageTest.Fixture();fixture.attach(world);var volume=bake(lighting,world);var fence=lighting.acceptedFence();
   assertThrows(IllegalStateException.class,()->SceneLightingUpload.replayVolume(other,volume,null,fence));
   int prior=glGenTextures();glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_3D,prior);glTexImage3D(GL_TEXTURE_3D,0,GL_R32I,1,1,1,0,GL_RED_INTEGER,GL_INT,(java.nio.IntBuffer)null);SceneLightingUpload.Resources transferred=null;
   try {
    int partial;
    try(var stage=SceneLightingUpload.replayVolume(world,volume,null,fence)) {
     assertTrue(stage.valid(world));assertFalse(stage.valid(other));assertEquals(0,stage.pump(System.nanoTime()-1,262144));assertEquals(0,stage.pump(deadline(),383));assertFalse(stage.ready());assertTrue(stage.pump(deadline(),262144)>0);partial=texture(stage,"irradiance");assertTrue(glIsTexture(partial));assertTrue(glIsTexture(prior));assertThrows(IllegalStateException.class,stage::take);
     var error=new AtomicReference<Throwable>();var thread=new Thread(()->{try{assertThrows(IllegalStateException.class,stage::ready);assertThrows(IllegalStateException.class,stage::close);}catch(Throwable e){error.set(e);}});thread.start();thread.join(5000);assertFalse(thread.isAlive());assertNull(error.get());fixture.geometryKey.set(new Object());assertFalse(stage.valid(world));assertFalse(stage.ready());assertThrows(IllegalStateException.class,()->stage.pump(deadline(),262144));assertThrows(IllegalStateException.class,stage::take);
    }assertFalse(glIsTexture(partial));assertTrue(glIsTexture(prior));assertThrows(IllegalStateException.class,()->SceneLightingUpload.replayVolume(world,volume,null,fence));
    var fresh=bake(lighting,world);var current=lighting.acceptedFence();
    try(var stage=SceneLightingUpload.replayVolume(world,fresh,null,current)) {
     complete(stage);transferred=stage.take();assertEquals(0,transferred.atlas());assertTrue(glIsTexture(transferred.irradiance()));assertTrue(glIsTexture(transferred.fineTexture()));assertNotNull(transferred.transport());assertFalse(stage.ready());assertFalse(stage.valid(world));assertThrows(IllegalStateException.class,stage::take);assertThrows(IllegalStateException.class,()->stage.pump(deadline(),262144));
     var bytes=MemoryUtil.memAlloc(fresh.rgba.length);try{glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_3D,transferred.irradiance());assertEquals(96,glGetTexLevelParameteri(GL_TEXTURE_3D,0,GL_TEXTURE_WIDTH));assertEquals(128,glGetTexLevelParameteri(GL_TEXTURE_3D,0,GL_TEXTURE_HEIGHT));assertEquals(96,glGetTexLevelParameteri(GL_TEXTURE_3D,0,GL_TEXTURE_DEPTH));glGetTexImage(GL_TEXTURE_3D,0,GL_RGBA,GL_UNSIGNED_BYTE,bytes);var actual=new byte[bytes.remaining()];bytes.get(actual);assertArrayEquals(fresh.rgba,actual);}finally{MemoryUtil.memFree(bytes);}
    }assertTrue(glIsTexture(transferred.irradiance()));assertTrue(glIsTexture(prior));
    try(var stage=SceneLightingUpload.replayVolume(world,fresh,null,current)){complete(stage);fixture.skyKey.set(new Object());assertFalse(stage.ready());assertThrows(IllegalStateException.class,stage::take);assertTrue(glIsTexture(prior));}
    assertEquals(GL_NO_ERROR,glGetError());assertFalse(SkyVisibilityScheduler.ceilingReady(null,world,0,0));assertTrue(world.getLoadedChunks().isEmpty());
   }finally{release(transferred);glDeleteTextures(prior);glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_3D,0);}
  }
 }
}
