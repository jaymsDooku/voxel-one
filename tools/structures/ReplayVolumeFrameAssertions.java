package dev.jayms.render;
import dev.jayms.*;
import static org.lwjgl.opengl.GL33.*;
import static org.junit.jupiter.api.Assertions.*;

public final class ReplayVolumeFrameAssertions {
 static int uniform(ShaderProgram shader,String name)throws Exception{int p=ReplaySkyPipelineAssertions.program(shader);return glGetUniformi(p,glGetUniformLocation(p,name));}
 static void frame(RenderPipeline p,World w){p.update(w,0,0,20_000_000L);assertTrue(p.lightingUploadBytes>=0&&p.lightingUploadBytes<=262144);assertTrue(p.skyGpuBytes>=0&&p.skyGpuBytes<=131072);assertFalse(p.nearLightingReady(w,0,0));glFlush();}
 static void complete(RenderPipeline p,World w)throws Exception{int steps=0;while(!p.replayVolumeReady(w)&&steps++<10000){frame(p,w);Thread.sleep(1);}assertTrue(p.replayVolumeReady(w));}
 public static void run()throws Exception {
  try(var world=new World();var resident=new World();var p=new RenderPipeline(GraphicsProfile.preset(GraphicsProfile.Preset.LOW));var shader=new ShaderProgram("shaders/voxel.vert","shaders/voxel.frag")) {
   var fixture=new ReplayJointLightingCoverageTest.Fixture();fixture.attach(world);fixture.skyValid.set(false);for(int i=0;i<3;i++)frame(p,world);assertEquals(0,p.lightingCoverage(world,0,0).submitted());assertFalse(p.lightingReady());fixture.skyValid.set(true);p.update(world,0,0,0);assertFalse(p.lightingReady());complete(p,world);ReplaySkyPipelineAssertions.bind(p,shader);assertEquals(1,uniform(shader,"uHasIrradiance"));assertEquals(2,uniform(shader,"uRootBuffer"));assertEquals(9216,ReplaySkyPipelineAssertions.columns(shader));assertTrue(p.lightingReady());long submitted=p.lightingCoverage(world,0,0).submitted();for(int i=0;i<3;i++)frame(p,world);assertEquals(submitted,p.lightingCoverage(world,0,0).submitted(),"Stable coverage must not rebake");
   fixture.geometryKey.set(new Object());assertFalse(p.lightingReady());ReplaySkyPipelineAssertions.bind(p,shader);assertEquals(0,uniform(shader,"uHasIrradiance"));complete(p,world);assertTrue(p.lightingCoverage(world,0,0).submitted()>submitted);
   fixture.skyKey.set(new Object());assertFalse(p.lightingReady());ReplaySkyPipelineAssertions.bind(p,shader);assertEquals(0,uniform(shader,"uHasIrradiance"));assertEquals(0,ReplaySkyPipelineAssertions.columns(shader));complete(p,world);assertTrue(p.lightingReady());assertFalse(SkyVisibilityScheduler.ceilingReady(null,world,0,0));assertTrue(world.getLoadedChunks().isEmpty());assertEquals(0,world.editsVersion());
   p.update(world,dev.jayms.net.Terrain.LIMIT,0,20_000_000L);assertFalse(p.lightingReady());p.update(resident,0,0,20_000_000L);ReplaySkyPipelineAssertions.bind(p,shader);assertEquals(0,uniform(shader,"uHasIrradiance"));complete(p,world);ReplaySkyPipelineAssertions.bind(p,shader);assertEquals(1,uniform(shader,"uHasIrradiance"));world.close();assertFalse(p.lightingReady());ReplaySkyPipelineAssertions.bind(p,shader);assertEquals(0,uniform(shader,"uHasIrradiance"));assertEquals(GL_NO_ERROR,glGetError());glActiveTexture(GL_TEXTURE0);
  }
 }
}
