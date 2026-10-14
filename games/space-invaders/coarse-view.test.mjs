import test from 'node:test';
import assert from 'node:assert/strict';
import {Planet} from './terrain.mjs';
import {createPlanetVisualProfile,localSphere} from './planet-profile.mjs';
import {cameraAt} from './entry-sequence.mjs';
import {viewRay,surfaceRay,atlasSample,coarseSample} from './coarse-view.mjs';
test('coarse rays cover upper atmosphere without polar projection singularities',()=>{
 const profile=createPlanetVisualProfile(new Planet().config);
 for(const time of [0,3,4.5,6,9,12])for(const aspect of [1280/900,390/844,844/390,568/320]){
  const camera=cameraAt(time,profile);let hits=0;
  for(let y=0;y<=12;y++)for(let x=0;x<=16;x++){
   const d=viewRay(camera,x/16,y/12,aspect);assert(Math.abs(Math.hypot(...d)-1)<1e-12);
   const h=surfaceRay(camera,d,profile,()=>({height:12,color:[100,90,80]}));if(h){hits++;assert(h.distance>0);assert(h.position.every(Number.isFinite));assert(Math.abs(Math.hypot(...h.position)-(profile.radius+600))<.001);}
  }
  if(time>=3)assert(hits>100,'surface must fill viewport continuously');
 }
});
test('atlas wraps longitude and clamps poles; landing patch keeps anchored samples',()=>{
 const profile=createPlanetVisualProfile(),data=new Uint8Array(4*4*4).fill(100),patch=new Uint8Array(72*48*4).fill(80);
 for(const n of [[0,1,0],[0,-1,0],[1e-9,0,-1],[-1e-9,0,-1]])assert.deepEqual(atlasSample(data,4,4,n).color,[100,100,100]);
 const s=coarseSample(profile,{data,width:4,height:4},patch,localSphere(profile,0,66));assert.deepEqual(s.color,[80,80,80]);assert.equal(s.height,80/255*128);
});
test('near-ground rays intersect raised parent terrain instead of discarding a negative sphere root',()=>{
 const profile=createPlanetVisualProfile(),camera=cameraAt(12,profile),ray=viewRay(camera,.5,.7,1280/900);
 const hit=surfaceRay(camera,ray,profile,n=>({height:12+Math.max(0,n[2])*5000,color:[80,120,100]}));
 assert(hit&&hit.distance>0);assert(Math.abs(Math.hypot(...hit.position)-profile.radius-hit.material.height*50)<20);
});
test('orbital radius correction uses one prepared field lookup',()=>{
 const profile=createPlanetVisualProfile(),camera=cameraAt(1,profile);let queries=0;
 const hit=surfaceRay(camera,viewRay(camera,.5,.5,1.4),profile,()=>{queries++;return {height:40,color:[80,120,100]};});
 assert(hit);assert.equal(queries,1);assert(Math.abs(Math.hypot(...hit.position)-profile.radius-2000)<.001);
});
test('upper-atmosphere aim leaves the curved horizon inside the windshield',()=>{
 const profile=createPlanetVisualProfile();
 for(const time of [3,4.5,6]){const camera=cameraAt(time,profile);
  assert.equal(surfaceRay(camera,viewRay(camera,.5,.05,1.4),profile,()=>({height:12,color:[100,90,80]})),null);
  assert(surfaceRay(camera,viewRay(camera,.5,.95,1.4),profile,()=>({height:12,color:[100,90,80]})));
 }
 for(const time of [3,6,9]){const a=cameraAt(time-1e-5,profile),b=cameraAt(time+1e-5,profile);assert(Math.hypot(...a.forward.map((v,i)=>v-b.forward[i]))<1e-5);}
});
