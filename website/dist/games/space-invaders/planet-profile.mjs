import {Planet,BIOMES} from './terrain.mjs';
import {atmospherePreset,unit} from './atmosphere.mjs';
export const PLANET_SUN=Object.freeze(unit([-.65,.45,-.6]));
const freeze=o=>{for(const v of Object.values(o))if(v&&typeof v==='object'&&!Object.isFrozen(v))freeze(v);return Object.freeze(o);};
export function createPlanetVisualProfile(config={},atmosphere='alien'){
 const world=new Planet(config),air=atmospherePreset(atmosphere);
 return freeze({version:1,generatorVersion:1,revision:JSON.stringify(world.config),seed:world.config.seed,config:world.config,palette:world.config.palette,biomeWeights:world.config.biomes,atmosphere:air,radius:air.radius,metresPerBlock:50,landingHeight:world.surface(0,0).height*50,sun:PLANET_SUN,cloudSeed:world.seed,anchor:{x:0,z:0},moons:[{direction:unit([.25,.13,.96]),phase:.7,color:'#c4b9e6'},{direction:unit([-.30,.055,.95]),phase:.45,color:'#81d4cd'}]});
}
export function sphericalLocal(profile,n){const u=unit(n);return {x:Math.atan2(u[0],u[1])*profile.radius/profile.metresPerBlock,z:Math.atan2(u[2],u[1])*profile.radius/profile.metresPerBlock};}
export function localSphere(profile,x,z){return unit([Math.tan(x*profile.metresPerBlock/profile.radius),1,Math.tan(z*profile.metresPerBlock/profile.radius)]);}
export function macroSample(world,n){
 const [x,y,z]=unit(n),f=world.noise(x*9+15,z*9+y*4,31),ridge=(1-Math.abs(2*world.noise(x*23,z*23+y*7,37)-1))**6;
 const weights=world.config.biomes.map((v,i)=>v*Math.exp(5*world.noise(x*8+i*4,z*8+y*9,40+i))),total=weights.reduce((a,b)=>a+b,0);
 const color=[0,0,0];weights.forEach((v,i)=>color.forEach((_,j)=>color[j]+=v/total*parseInt(world.config.palette[BIOMES[i]][0].slice(1+j*2,3+j*2),16)));
 let height=12+f*20+ridge*20;
 if(!world.entryCraters)world.entryCraters=Array.from({length:12},(_,i)=>{const cy=world.random(i,0,91)*2-1,phi=world.random(i,0,92)*Math.PI*2;return {direction:[Math.sqrt(1-cy*cy)*Math.cos(phi),cy,Math.sqrt(1-cy*cy)*Math.sin(phi)],radius:.08+world.random(i,0,93)*.18};});
 for(const crater of world.entryCraters){const cosine=Math.max(-1,Math.min(1,x*crater.direction[0]+y*crater.direction[1]+z*crater.direction[2])),d=Math.acos(cosine)/crater.radius;if(d>1.5)continue;const strength=world.config.craterDensity,basin=Math.exp(-d*d*4),rim=Math.exp(-(((d-.94)/.15)**2));height+=strength*(-8*basin+6*rim);const pale=Math.min(.55,strength*(basin*.4+rim*.3));color.forEach((v,j)=>color[j]=v*(1-pale)+parseInt(world.config.palette.impact[0].slice(1+j*2,3+j*2),16)*pale);}
 return {height,color:color.map(v=>Math.min(255,v*(.8+ridge*.5))),weights:weights.map(v=>v/total)};
}
export function sharedSample(world,profile,n){
 const local=sphericalLocal(profile,n),d=Math.max(Math.abs(local.x)/2400,Math.abs(local.z-66)/2400),macro=d>.6||n[1]<=0?macroSample(world,n):null;
 if(n[1]<=0||d>=1)return macro;
 const exact=Math.max(Math.abs(local.x)/108,Math.abs(local.z-66)/72)<1,step=exact?1:32,s=world.surface(Math.round(local.x/step)*step,Math.round(local.z/step)*step),color=world.config.palette[s.biome][0].match(/[a-f0-9]{2}/gi).map(x=>parseInt(x,16));if(!macro)return {height:s.height,color,local};const f=Math.max(0,Math.min(1,(d-.6)/.4)),blend=f*f*(3-2*f);
 return {height:s.height*(1-blend)+macro.height*blend,color:color.map((v,i)=>v*(1-blend)+macro.color[i]*blend),local};
}
