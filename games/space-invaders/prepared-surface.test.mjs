import test from 'node:test';
import assert from 'node:assert/strict';
import {Planet} from './terrain.mjs';
import {createPlanetVisualProfile,localSphere} from './planet-profile.mjs';
import {surfaceRay} from './coarse-view.mjs';
import {prepareRegion,preparedSample,scheduleSlices} from './prepared-surface.mjs';
test('prepared region retains seeded heights and never generates terrain while sampling',()=>{
 const world=new Planet(),profile=createPlanetVisualProfile(world.config),it=prepareRegion(world,profile);let step;do{step=it.next();}while(!step.done);const region=step.value,expected=world.surface(300,364).height;
 world.surface=()=>{throw Error('playback generation');};
 const atlas={data:new Uint8Array(8*4).fill(120),width:4,height:2};
 assert.equal(preparedSample(profile,atlas,null,region,localSphere(profile,300,364)).height,expected);
 assert(Number.isFinite(preparedSample(profile,atlas,null,region,[0,-1,0]).height));
});
test('slice cancellation rejects stale completion',async()=>{
 let steps=0,completed=false;const cancel=scheduleSlices((function*(){while(steps<100){steps++;yield;}return 1;})(),()=>{completed=true;});cancel();await new Promise(r=>setTimeout(r,15));assert.equal(steps,0);assert.equal(completed,false);
});
test('slice runner yields between bounded work and completes once',async()=>{
 let completed=0;await new Promise(resolve=>scheduleSlices((function*(){for(let i=0;i<4;i++){const end=performance.now()+1;while(performance.now()<end){}yield;}return 7;})(),value=>{assert.equal(value,7);completed++;resolve();}));assert.equal(completed,1);
});

test('coarse parent cannot raise the protected landing patch above the approach camera',()=>{
 const profile=createPlanetVisualProfile(),atlas={data:new Uint8Array(8*4).fill(128),width:4,height:2},patch=new Uint8Array(72*48*4).fill(128),data=new Float32Array(33*33*4);
 for(let k=0;k<patch.length;k+=4)patch[k+3]=23;
 for(let k=0;k<data.length;k+=4){data[k]=120;data[k+1]=data[k+2]=data[k+3]=128;}
 const region={size:33,step:150,data},sample=n=>preparedSample(profile,atlas,patch,region,n),expected=23/255*128;
 assert.equal(sample(localSphere(profile,0,0)).height,expected);assert.equal(sample(localSphere(profile,0,-5)).height,expected);
 const camera={eye:[0,profile.radius+expected*50+12,0],altitude:12};assert(surfaceRay(camera,[0,-1,0],profile,sample));
});
test('regional LOD has no nearest-cell height or colour jump',()=>{
 const profile=createPlanetVisualProfile(),atlas={data:new Uint8Array(8*4).fill(128),width:4,height:2},data=new Float32Array(33*33*4);
 for(let z=0;z<33;z++)for(let x=0;x<33;x++){const k=(z*33+x)*4;data[k]=x%2?120:0;data[k+1]=x%2?200:20;data[k+2]=100;data[k+3]=80;}
 const region={size:33,step:150,data},a=preparedSample(profile,atlas,null,region,localSphere(profile,225-1e-5,364)),b=preparedSample(profile,atlas,null,region,localSphere(profile,225+1e-5,364));
 assert(Math.abs(a.height-b.height)<.001);a.color.forEach((v,i)=>assert(Math.abs(v-b.color[i])<.001));
});
test('landing colour detail grows without changing the sampled surface height',()=>{
 const profile=createPlanetVisualProfile(),atlas={data:new Uint8Array(8*4).fill(128),width:4,height:2},patch=new Uint8Array(72*48*4).fill(80),region={size:33,step:150,data:new Float32Array(33*33*4).fill(120)},n=localSphere(profile,0,0);
 const far=preparedSample(profile,atlas,patch,region,n,0),mid=preparedSample(profile,atlas,patch,region,n,.5),near=preparedSample(profile,atlas,patch,region,n,1);
 assert.equal(far.height,near.height);assert.equal(mid.height,near.height);assert.deepEqual(near.color,[80,80,80]);assert(mid.color.every((v,i)=>v>=near.color[i]&&v<=far.color[i]));assert.notDeepEqual(far.color,near.color);
});
