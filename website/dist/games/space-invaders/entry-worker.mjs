import {cloudDensity,cloudInterval} from './clouds.mjs';
// Reduced-quality world-pass cache. Bounded Worker jobs, no integration on animation thread.
import {Planet} from './terrain.mjs';
import {buildTerrainScene} from './terrain-scene.mjs';
import {createPlanetVisualProfile,sharedSample,macroSample} from './planet-profile.mjs';
import {cameraAt} from './entry-sequence.mjs';
import {integrate,sphere,unit,dot,sunlight,buildMultipleTable,sampleMultiple} from './atmosphere.mjs';
let world,profile,key,fineScene,atlas,multipleTable;
self.onmessage=({data})=>{try{
 if(key!==data.key){world=new Planet(data.config);for(const edit of data.edits||[])world.edits.add(edit);fineScene=buildTerrainScene(data.config,{cols:96,rows:64,step:1,centerX:0,centerZ:30},data.edits||[]);profile=createPlanetVisualProfile(data.config,data.air);key=data.key;multipleTable=buildMultipleTable(profile.atmosphere,data.quality);atlas=new Uint8Array(128*64*4);for(let y=0;y<64;y++)for(let x=0;x<128;x++){const lat=(y/63-.5)*Math.PI,lon=(x/128-.5)*Math.PI*2,s=macroSample(world,[Math.cos(lat)*Math.sin(lon),Math.sin(lat),Math.cos(lat)*Math.cos(lon)]),k=(y*128+x)*4;s.color.forEach((v,i)=>atlas[k+i]=v);atlas[k+3]=Math.max(0,Math.min(255,s.height/128*255));}}
 const c=cameraAt(data.time,profile),width=data.quality==='Low'?64:data.quality==='High'?128:96,height=data.quality==='Low'?40:data.quality==='High'?96:64,pixels=new Uint8ClampedArray(width*height*4),r=profile.radius,air=profile.atmosphere;
 for(let y=0;y<height;y++)for(let x=0;x<width;x++){
  const dx=(2*(x+.5)/width-1)*.52,dy=(1-2*(y+.5)/height)*.52/data.aspect,d=unit(c.forward.map((v,i)=>v+c.right[i]*dx+c.up[i]*dy));
  let hit=sphere(c.eye,d,r+profile.landingHeight),dist=hit&&hit.near>0?hit.near:2*(Math.hypot(...c.eye)+r+air.height),color=[.003,.006,.014],stellar=0;
  if(!hit||hit.near<=0){const hash=Math.sin(Math.floor(d[0]*3000)*12.9898+Math.floor(d[1]*3000)*78.233+Math.floor(d[2]*3000)*39.425)*43758.5453,star=Math.pow(hash-Math.floor(hash),160)*.4;stellar=star;}
  if(hit&&hit.near>0){for(let j=0;j<4;j++){const q=c.eye.map((v,i)=>v+d[i]*dist),sample=sharedSample(world,profile,unit(q)),h=sphere(c.eye,d,r+sample.height*50);if(h&&h.near>0)dist=h.near;}
   const q=c.eye.map((v,i)=>v+d[i]*dist),n=unit(q),sample=sharedSample(world,profile,n),solar=sunlight(air,q.map((v,i)=>v+n[i]*2),profile.sun,4),light=Math.max(0,dot(n,profile.sun));color=sample.color.map((v,i)=>(v/255)**2.2*(.035+solar[i]*light*2.8));
  }
  const a=integrate(air,c.eye,d,profile.sun,dist,data.quality==='High'?8:data.quality==='Low'?3:4,data.quality==='High'?4:2,(q,s)=>sampleMultiple(multipleTable,air,q,s));const brightness=a.radiance[0]*.2126+a.radiance[1]*.7152+a.radiance[2]*.0722,f=Math.max(0,Math.min(1,(brightness-.001)/.049)),starVisibility=1-f*f*(3-2*f);color=color.map((v,i)=>v*a.transmittance[i]+a.radiance[i]+stellar*a.transmittance[i]*starVisibility);
  if(air.enabled){const layers=[];for(const [altitude,thickness] of [[1800,450],[4500,600]]){const range=cloudInterval(c.eye,d,r,altitude,thickness,dist);if(!range)continue;const t=(range.near+range.far)/2,q=c.eye.map((v,i)=>v+d[i]*t),n=unit(q),opacity=Math.min(.55,1-Math.exp(-cloudDensity(n,profile.cloudSeed,data.time)*(range.far-range.near)*.6/thickness)),solar=sunlight(air,q,profile.sun,4);layers.push({t,opacity,light:solar.map(v=>.01+v*Math.max(0,dot(n,profile.sun))*.9)});}layers.sort((a,b)=>b.t-a.t);for(const layer of layers)color=color.map((v,i)=>v*(1-layer.opacity)+layer.light[i]*layer.opacity);}

  const k=(y*width+x)*4;color.forEach((v,i)=>pixels[k+i]=255*(1-Math.exp(-Math.max(0,v)*(1.1+c.progress*.5)))**(1/2.2));pixels[k+3]=255;
 }
 self.postMessage({epoch:data.epoch,index:data.index,pixels,width,height,scene:data.index===0?fineScene:null,atlas:data.index===0?atlas:null,multiple:data.index===0?multipleTable:null},[pixels.buffer]);
 }catch{self.postMessage({epoch:data.epoch,error:'Entry cache generation failed'});}};
