import {prepareRegion,preparedSample,patchColorDetail} from './prepared-surface.mjs';
import {surfaceRay,viewRay} from './coarse-view.mjs';
import {cloudDensity,cloudInterval} from './clouds.mjs';
// Reduced-quality world-pass cache. Bounded Worker jobs, no integration on animation thread.
import {Planet} from './terrain.mjs';
import {buildTerrainScene} from './terrain-scene.mjs';
import {createPlanetVisualProfile,macroSample,localSphere,sphericalLocal} from './planet-profile.mjs';
import {cameraAt} from './entry-sequence.mjs';
import {integrate,sphere,unit,dot,sunlight,buildMultipleTable,sampleMultiple} from './atmosphere.mjs';
let world,profile,key,fineScene,atlas,multipleTable,region,patch,solarTables;
self.onmessage=({data})=>{try{
 if(key!==data.key){world=new Planet(data.config);for(const edit of data.edits||[])world.edits.add(edit);fineScene=buildTerrainScene(data.config,{cols:96,rows:64,step:1,centerX:0,centerZ:30},data.edits||[]);profile=createPlanetVisualProfile(data.config,data.air);key=data.key;multipleTable=buildMultipleTable(profile.atmosphere,data.quality);atlas=new Uint8Array(128*64*4);for(let y=0;y<64;y++)for(let x=0;x<128;x++){const lat=(y/63-.5)*Math.PI,lon=(x/128-.5)*Math.PI*2,s=macroSample(world,[Math.cos(lat)*Math.sin(lon),Math.sin(lat),Math.cos(lat)*Math.cos(lon)]),k=(y*128+x)*4;s.color.forEach((v,i)=>atlas[k+i]=v);atlas[k+3]=Math.max(0,Math.min(255,s.height/128*255));}}
 if(!region||region.key!==key){const iterator=prepareRegion(world,profile);let result;do{result=iterator.next();}while(!result.done);region={...result.value,key};patch=new Uint8Array(72*48*4);for(let row=0;row<48;row++)for(let col=0;col<72;col++){const s=world.surface((col-36)*3,66+(24-row)*3),k=(row*72+col)*4;world.config.palette[s.biome][0].match(/[a-f0-9]{2}/gi).forEach((v,i)=>patch[k+i]=parseInt(v,16));patch[k+3]=Math.max(0,Math.min(255,s.height/128*255));}}
 const sampleAt=n=>preparedSample(profile,{data:atlas,width:128,height:64},patch,region,n,patchColorDetail(c.altitude));
 if(solarTables?.key!==key){const table=height=>Array.from({length:33},(_,i)=>{const mu=i/16-1;return sunlight(profile.atmosphere,[profile.radius+height,0,0],[mu,Math.sqrt(Math.max(0,1-mu*mu)),0],4);});solarTables={key,ground:table(profile.landingHeight+2),clouds:[table(1800),table(4500)]};}
 const mix=(a,b,f)=>a.map((v,i)=>v*(1-f)+b[i]*f),solarAt=(table,mu)=>{const k=(Math.max(-1,Math.min(1,mu))+1)*16,j=Math.min(31,Math.floor(k));return mix(table[j],table[j+1],k-j);};
 const c=cameraAt(data.time,profile),width=data.time<3?Math.min(192,Math.round(128*data.aspect)):data.quality==='Low'?48:data.quality==='High'?128:96,height=data.time<3?Math.min(128,Math.round(width/data.aspect)):data.quality==='Low'?32:data.quality==='High'?96:64,pixels=new Uint8ClampedArray(width*height*4),r=profile.radius,air=profile.atmosphere;
 const ax=c.altitude>60000?12:8,ay=c.altitude>60000?8:6,airGrid=[];
 for(let y=0;y<=ay;y++)for(let x=0;x<=ax;x++){const d=viewRay(c,x/ax,y/ay,data.aspect),hit=surfaceRay(c,d,profile,sampleAt),distance=hit?.distance||2*(Math.hypot(...c.eye)+r+air.height);airGrid.push(integrate(air,c.eye,d,profile.sun,distance,data.quality==='High'?8:4,data.quality==='High'?4:2,(q,s)=>sampleMultiple(multipleTable,air,q,s)));}
 for(let y=0;y<height;y++)for(let x=0;x<width;x++){
  const dx=(2*(x+.5)/width-1)*.52,dy=(1-2*(y+.5)/height)*.52/data.aspect,d=unit(c.forward.map((v,i)=>v+c.right[i]*dx+c.up[i]*dy));
  const surface=surfaceRay(c,d,profile,sampleAt);let hit=surface?{near:surface.distance}:null,dist=hit&&hit.near>0?hit.near:2*(Math.hypot(...c.eye)+r+air.height),color=[.003,.006,.014],stellar=0;
  if(!hit||hit.near<=0){const hash=Math.sin(Math.floor(d[0]*3000)*12.9898+Math.floor(d[1]*3000)*78.233+Math.floor(d[2]*3000)*39.425)*43758.5453,star=Math.pow(hash-Math.floor(hash),160)*.4;stellar=star;}
  if(hit&&hit.near>0){
   const n=surface.normal,sample=surface.material,solar=solarAt(solarTables.ground,dot(n,profile.sun));let normal=n;
   if(c.altitude<=60000){const local=sphericalLocal(profile,n),step=Math.max(Math.abs(local.x)/108,Math.abs(local.z-66)/72)<1?3:32,dx=sampleAt(localSphere(profile,local.x+step,local.z)).height-sampleAt(localSphere(profile,local.x-step,local.z)).height,dz=sampleAt(localSphere(profile,local.x,local.z+step)).height-sampleAt(localSphere(profile,local.x,local.z-step)).height;normal=unit([n[0]-dx/(2*step),n[1],n[2]-dz/(2*step)]);}
   const light=Math.max(0,dot(normal,profile.sun));color=sample.color.map((v,i)=>(v/255)**2.2*(.035+solar[i]*light*2.8));
  }
  const gx=(x+.5)/width*ax,gy=(y+.5)/height*ay,ix=Math.min(ax-1,Math.floor(gx)),iy=Math.min(ay-1,Math.floor(gy)),fx=gx-ix,fy=gy-iy,at=(dx,dy)=>airGrid[(iy+dy)*(ax+1)+ix+dx],interpolate=k=>mix(mix(at(0,0)[k],at(1,0)[k],fx),mix(at(0,1)[k],at(1,1)[k],fx),fy);
  let a={radiance:interpolate('radiance'),transmittance:interpolate('transmittance')};const shell=sphere(c.eye,d,r+air.height);
  if(!shell||shell.far<=0)a={radiance:[0,0,0],transmittance:[1,1,1]};else if(!surface&&c.altitude>60000)a=integrate(air,c.eye,d,profile.sun,dist,8,4);

  if(air.enabled){const layers=[];for(const [altitude,thickness] of [[1800,450],[4500,600]]){const range=cloudInterval(c.eye,d,r,altitude,thickness,dist);if(!range)continue;const t=(range.near+range.far)/2,q=c.eye.map((v,i)=>v+d[i]*t),n=unit(q),opacity=Math.min(.25,1-Math.exp(-cloudDensity(n,profile.cloudSeed,data.time)*(range.far-range.near)*.6/thickness)),solar=solarAt(solarTables.clouds[altitude===1800?0:1],dot(n,profile.sun));layers.push({t,opacity,light:solar.map(v=>.01+v*Math.max(0,dot(n,profile.sun))*.9)});}layers.sort((a,b)=>b.t-a.t);for(const layer of layers){color=color.map((v,i)=>v*(1-layer.opacity)+layer.light[i]*layer.opacity);stellar*=1-layer.opacity;}}

  const sky=color.map((v,i)=>v*a.transmittance[i]+a.radiance[i]),brightness=sky[0]*.2126+sky[1]*.7152+sky[2]*.0722,f=Math.max(0,Math.min(1,(brightness-.005)/.025)),starVisibility=1-f*f*(3-2*f),starCoverage=(width/640)**2;
  color=sky.map((v,i)=>v+stellar*a.transmittance[i]*starVisibility*starCoverage);
  const k=(y*width+x)*4;color.forEach((v,i)=>pixels[k+i]=255*(1-Math.exp(-Math.max(0,v)*(1.1+c.progress*.5)))**(1/2.2));pixels[k+3]=255;
 }
 self.postMessage({epoch:data.epoch,index:data.index,pixels,width,height,scene:data.index===0?fineScene:null,atlas:data.index===0?atlas:null,multiple:data.index===0?multipleTable:null},[pixels.buffer]);
 }catch{self.postMessage({epoch:data.epoch,error:'Entry cache generation failed'});}};
