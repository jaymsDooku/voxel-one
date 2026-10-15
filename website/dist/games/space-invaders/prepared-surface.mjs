import {atlasSample} from './coarse-view.mjs';
import {sphericalLocal,macroSample} from './planet-profile.mjs';
// A bounded parent field. Sampling never calls the terrain generator during playback.
export function* prepareRegion(world,profile){
 const size=33,step=150,data=new Float32Array(size*size*4);
 for(let z=0;z<size;z++)for(let x=0;x<size;x++){
  const s=world.surface((x-16)*step,(z-16)*step+64),k=(z*size+x)*4;
  data[k]=s.height;world.config.palette[s.biome][0].match(/[a-f0-9]{2}/gi).forEach((v,i)=>data[k+i+1]=parseInt(v,16));
  yield;
 }
 return {size,step,data};
}
export function patchColorDetail(altitude){const f=Math.max(0,Math.min(1,(6000-altitude)/4500));return f*f*(3-2*f);}
export function preparedSample(profile,atlas,patch,region,n,colorDetail=1){
 const macro=atlasSample(atlas.data,atlas.width,atlas.height,n);
 // The local region is less than 0.02 radians wide around the north pole.
 // Distant normals cannot hit it, so avoid its mapping and patch work.
 if(n[1]<=0||Math.abs(n[0])>.02||Math.abs(n[2])>.02)return macro;
 const local=sphericalLocal(profile,n),edge=Math.max(Math.abs(local.x)/2400,Math.abs(local.z-66)/2400);
 let material=macro;
 if(region&&n[1]>0&&edge<1){
  const snap=v=>Math.abs(v-Math.round(v))<1e-9?Math.round(v):v,x=snap(Math.max(0,Math.min(region.size-1,local.x/region.step+(region.size-1)/2))),z=snap(Math.max(0,Math.min(region.size-1,(local.z-64)/region.step+(region.size-1)/2))),ix=Math.floor(x),iz=Math.floor(z),fx=x-ix,fz=z-iz,f=Math.max(0,Math.min(1,(edge-.6)/.4)),blend=f*f*(3-2*f);
  const sample=channel=>{let value=0;for(const [dx,dz,weight] of [[0,0,(1-fx)*(1-fz)],[1,0,fx*(1-fz)],[0,1,(1-fx)*fz],[1,1,fx*fz]]){const k=(Math.min(region.size-1,iz+dz)*region.size+Math.min(region.size-1,ix+dx))*4;value+=region.data[k+channel]*weight;}return value;};
  const colorBlend=1-(1-blend)*Math.max(0,Math.min(1,colorDetail));
  material={height:sample(0)*(1-blend)+macro.height*blend,color:[0,1,2].map(i=>sample(i+1)*(1-colorBlend)+macro.color[i]*colorBlend)};
 }
 const e=Math.max(Math.abs(local.x)/108,Math.abs(local.z-66)/72);
 if(n[1]<=0||e>=1||!patch)return material;
 const x=Math.max(0,Math.min(71,Math.round(local.x/3+36))),z=Math.max(0,Math.min(47,Math.round(24-(local.z-66)/3))),k=(z*72+x)*4,f=Math.max(0,Math.min(1,(e-.7)/.3)),distance=Math.max(Math.abs(local.x-(profile.anchor?.x||0)),Math.abs(local.z-(profile.anchor?.z||0))),a=Math.max(0,Math.min(1,(distance-16)/16)),blend=f*f*(3-2*f)*a*a*(3-2*a);
 const colorBlend=1-(1-blend)*Math.max(0,Math.min(1,colorDetail));
 return {height:patch[k+3]/255*128*(1-blend)+material.height*blend,color:[0,1,2].map(i=>patch[k+i]*(1-colorBlend)+material.color[i]*colorBlend)};
}
// Each generator step is one small unit. Jobs yield to input/animation after 2 ms.
export function scheduleSlices(iterator,done,current=()=>true,measure=()=>{}){
 let canceled=false,timer=null;
 // Message tasks yield to input/rendering without nested setTimeout's 4 ms
 // minimum delay. Only one continuation is queued for each bounded job.
 const channel=typeof MessageChannel==='function'?new MessageChannel():null;
 const close=()=>{if(timer!==null)clearTimeout(timer);channel?.port1.close();channel?.port2.close();};
 const queue=()=>{if(channel)channel.port2.postMessage(0);else timer=setTimeout(run,0);};
 const run=()=>{if(canceled||!current()){close();return;}const started=performance.now(),end=started+2;let step;
  do{step=iterator.next();if(step.done){measure(performance.now()-started);close();done(step.value);return;}}while(performance.now()<end);
  measure(performance.now()-started);queue();
 };if(channel)channel.port1.onmessage=run;queue();return ()=>{canceled=true;close();iterator.return?.();};
}

export function* prepareAtlas(world,width=128,height=64){
 const data=new Uint8Array(width*height*4);
 for(let y=0;y<height;y++)for(let x=0;x<width;x++){
  const lat=(y/(height-1)-.5)*Math.PI,lon=(x/width-.5)*Math.PI*2,s=macroSample(world,[Math.cos(lat)*Math.sin(lon),Math.sin(lat),Math.cos(lat)*Math.cos(lon)]),k=(y*width+x)*4;
  s.color.forEach((v,i)=>data[k+i]=v);data[k+3]=Math.max(0,Math.min(255,s.height/128*255));yield;
 }
 return {data,width,height};
}
