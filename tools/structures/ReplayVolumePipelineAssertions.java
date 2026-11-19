package dev.jayms.render;
import dev.jayms.*;
import java.util.concurrent.atomic.*;
import static org.lwjgl.opengl.GL33.*;
import static org.junit.jupiter.api.Assertions.*;

public final class ReplayVolumePipelineAssertions {
 static long deadline(){return System.nanoTime()+100_000_000L;}
 static int uniform(ShaderProgram shader,String name)throws Exception{int program=ReplaySkyPipelineAssertions.program(shader);return glGetUniformi(program,glGetUniformLocation(program,name));}
 static int irradiance(RenderPipeline p)throws Exception{var f=RenderPipeline.class.getDeclaredField("irradiance");f.setAccessible(true);return f.getInt(p);}
 static void sky(RenderPipeline p,World w){int steps=0;while(!p.replaySkyAtlasReady(w)&&steps++<10000){assertTrue(p.pumpReplaySky(w,deadline(),262144)<=262144);glFlush();}assertTrue(p.replaySkyAtlasReady(w));}
 static void volume(RenderPipeline p,World w)throws Exception{int steps=0;while(!p.replayVolumeReady(w)&&steps++<10000){int n=p.pumpReplayVolume(w,0,0,deadline(),262144);assertTrue(n>=0&&n<=262144);glFlush();Thread.sleep(1);}assertTrue(p.replayVolumeReady(w));}
 public static void run()throws Exception {
  try(var world=new World();var foreign=new World();var p=new RenderPipeline(GraphicsProfile.preset(GraphicsProfile.Preset.LOW));var shader=new ShaderProgram("shaders/voxel.vert","shaders/voxel.frag")) {
   var fixture=new ReplayJointLightingCoverageTest.Fixture();fixture.attach(world);assertThrows(IllegalStateException.class,()->p.pumpReplayVolume(world,0,0,deadline(),262144));sky(p,world);assertFalse(p.replayVolumeReady(world));assertEquals(0,p.pumpReplayVolume(world,0,0,0,262144));assertEquals(0,p.pumpReplayVolume(world,0,0,deadline(),0));assertThrows(IllegalArgumentException.class,()->p.pumpReplayVolume(world,0,0,deadline(),262145));assertThrows(IllegalStateException.class,()->p.pumpReplayVolume(foreign,0,0,deadline(),262144));
   var error=new AtomicReference<Throwable>();var t=new Thread(()->{try{assertThrows(IllegalStateException.class,()->p.pumpReplayVolume(world,0,0,deadline(),262144));}catch(Throwable e){error.set(e);}});t.start();t.join(5000);assertFalse(t.isAlive());assertNull(error.get());
   int steps=0,sent=0;while(sent==0&&steps++<10000){sent=p.pumpReplayVolume(world,0,0,deadline(),262144);glFlush();Thread.sleep(1);}assertTrue(sent>0&&sent<=262144);assertFalse(p.replayVolumeReady(world));ReplaySkyPipelineAssertions.bind(p,shader);assertEquals(0,uniform(shader,"uHasIrradiance"));fixture.geometryKey.set(new Object());assertFalse(p.replayVolumeReady(world));
   volume(p,world);assertTrue(p.lightingReady());assertFalse(p.replayVolumeReady(foreign));ReplaySkyPipelineAssertions.bind(p,shader);assertEquals(1,uniform(shader,"uHasIrradiance"));assertEquals(2,uniform(shader,"uRootBuffer"));assertEquals(9216,ReplaySkyPipelineAssertions.columns(shader));int prior=irradiance(p);assertTrue(glIsTexture(prior));
   fixture.skyKey.set(new Object());assertFalse(p.replayVolumeReady(world));assertFalse(p.lightingReady());ReplaySkyPipelineAssertions.bind(p,shader);assertEquals(0,uniform(shader,"uHasIrradiance"));assertEquals(0,ReplaySkyPipelineAssertions.columns(shader));assertTrue(glIsTexture(prior));sky(p,world);assertFalse(p.replayVolumeReady(world));ReplaySkyPipelineAssertions.bind(p,shader);assertEquals(0,uniform(shader,"uHasIrradiance"));volume(p,world);ReplaySkyPipelineAssertions.bind(p,shader);assertEquals(1,uniform(shader,"uHasIrradiance"));
   assertFalse(p.nearLightingReady(world,0,0));assertFalse(SkyVisibilityScheduler.ceilingReady(null,world,0,0));assertTrue(world.getLoadedChunks().isEmpty());world.close();assertFalse(p.lightingReady());ReplaySkyPipelineAssertions.bind(p,shader);assertEquals(0,uniform(shader,"uHasIrradiance"));p.update(foreign,0,0,20_000_000L);assertFalse(p.lightingReady());ReplaySkyPipelineAssertions.bind(p,shader);assertEquals(0,uniform(shader,"uHasIrradiance"));assertEquals(GL_NO_ERROR,glGetError());glActiveTexture(GL_TEXTURE0);
  }
 }
}
