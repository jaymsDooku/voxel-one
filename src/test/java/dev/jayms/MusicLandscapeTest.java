package dev.jayms;
import dev.jayms.music.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MusicLandscapeTest {
 @Test void capUsesActualSceneHeightRange(){
  double low=Double.POSITIVE_INFINITY,high=Double.NEGATIVE_INFINITY;
  for(int x=-MusicLandscape.RADIUS;x<=MusicLandscape.RADIUS;x++)for(int z=-MusicLandscape.RADIUS;z<=MusicLandscape.RADIUS;z++){
   double value=MusicLandscape.base(x,z);low=Math.min(low,value);high=Math.max(high,value);
  }
  assertEquals(high-low,MusicLandscape.HEIGHT_RANGE,1e-12);
  assertEquals((high-low)*.08,MusicLandscape.MAX_DISPLACEMENT,1e-12);
 }
 @Test void poolsAndAggregateDisplacementStayBoundedForThirtyMinutes(){
  var scene=new MusicLandscape();int maxRipple=0,maxParticle=0,maxMemory=0;
  for(int frame=0;frame<30*60*60;frame++){
   scene.update(1d/60,new MusicAnalysis.Bands(1,1,1,frame%14==0),2,false);
   maxRipple=Math.max(maxRipple,scene.ripples.count());maxParticle=Math.max(maxParticle,scene.particles.count());maxMemory=Math.max(maxMemory,scene.memory.count());
   assertTrue(Math.abs(scene.displacement(frame%25-12,frame%23-11,2))<=MusicLandscape.MAX_DISPLACEMENT);
  }
  assertEquals(8,maxRipple);assertEquals(256,maxParticle);assertEquals(128,maxMemory);
  for(int frame=0;frame<21*60;frame++)scene.update(1d/60,new MusicAnalysis.Bands(0,0,0,false),1,false);
  assertEquals(0,scene.ripples.count()+scene.particles.count()+scene.memory.count());
 }
 @Test void pauseFreezesSeekResetsAndZeroMotionIsCalm(){
  var scene=new MusicLandscape();scene.update(.1,new MusicAnalysis.Bands(1,1,1,true),2,false);
  double y=scene.surface(3,4,2),time=scene.time,atmosphere=scene.atmosphere;
  for(int i=0;i<600;i++)scene.update(.1,new MusicAnalysis.Bands(0,0,0,true),2,true);
  assertEquals(time,scene.time);assertEquals(y,scene.surface(3,4,2));assertEquals(atmosphere,scene.atmosphere);
  assertEquals(MusicLandscape.base(3,4),scene.surface(3,4,0));
  scene.reset();assertEquals(0,scene.time);assertEquals(0,scene.ripples.count()+scene.particles.count()+scene.memory.count());
  assertEquals(MusicLandscape.base(3,4),scene.surface(3,4,2));
 }
}
