// Bounded screen-space surface mesh. Rays avoid polar/near-plane polygon fans.
import {sphere,unit} from './atmosphere.mjs';
import {sphericalLocal} from './planet-profile.mjs';
export function viewRay(camera,x,y,aspect){return unit(camera.forward.map((v,i)=>v+camera.right[i]*(2*x-1)*.52+camera.up[i]*(1-2*y)*.52/aspect));}
export function atlasSample(data,width,height,n){
 const u=((Math.atan2(n[0],n[2])/(2*Math.PI)+.5)%1+1)%1,v=Math.asin(Math.max(-1,Math.min(1,n[1])))/Math.PI+.5;
 const x=u*width,y=v*(height-1),ix=Math.floor(x),iy=Math.floor(y),fx=x-ix,fy=y-iy;
 const out=[0,0,0,0];for(const [dx,dy,weight] of [[0,0,(1-fx)*(1-fy)],[1,0,fx*(1-fy)],[0,1,(1-fx)*fy],[1,1,fx*fy]]){const k=(Math.min(height-1,iy+dy)*width+(ix+dx)%width)*4;for(let j=0;j<4;j++)out[j]+=data[k+j]*weight;}
 return {height:out[3]/255*128,color:out.slice(0,3)};
}
export function coarseSample(profile,atlas,patch,n){
 const macro=atlasSample(atlas.data,atlas.width,atlas.height,n),p=sphericalLocal(profile,n),edge=Math.max(Math.abs(p.x)/108,Math.abs(p.z-66)/72);
 if(n[1]<=0||edge>=1||!patch)return macro;
 const col=Math.max(0,Math.min(71,Math.round(p.x/3+36))),row=Math.max(0,Math.min(47,Math.round(24-(p.z-66)/3))),k=(row*72+col)*4,f=Math.max(0,Math.min(1,(edge-.96)/.04)),blend=f*f*(3-2*f);
 return {height:patch[k+3]/255*128*(1-blend)+macro.height*blend,color:[0,1,2].map(i=>patch[k+i]*(1-blend)+macro.color[i]*blend)};
}
export function surfaceRay(camera,d,profile,sample){
 const at=t=>{const position=camera.eye.map((v,i)=>v+d[i]*t),normal=unit(position),material=sample(normal);return {distance:t,normal,material,position,residual:Math.hypot(...position)-profile.radius-material.height*profile.metresPerBlock};};
 const outer=sphere(camera.eye,d,profile.radius+6400),inner=sphere(camera.eye,d,profile.radius);if(!outer||outer.far<=0)return null;
 const near=Math.max(0,outer.near),far=inner&&inner.near>near?inner.near:outer.far;
 if(camera.altitude<60000){let previous=at(near);for(let i=1;i<=24;i++){const current=at(near+(far-near)*(i/24)**2);if(previous.residual>=0&&current.residual<=0){let lo=previous.distance,hi=current.distance;for(let j=0;j<8;j++){const mid=(lo+hi)/2;if(at(mid).residual>0)lo=mid;else hi=mid;}const point=at((lo+hi)/2),shell=sphere(camera.eye,d,profile.radius+point.material.height*profile.metresPerBlock);return shell&&shell.near>=lo&&shell.near<=hi?at(shell.near):point;}previous=current;}return null;}
 // At orbital distances the relief is subpixel. One field lookup and one
 // analytic radius correction avoid repeatedly sampling the same atlas texel.
 // Close rays retain the full bounded march above.
 let hit=sphere(camera.eye,d,profile.radius+profile.landingHeight);if(!hit||hit.near<=0)return null;
 const point=at(hit.near);hit=sphere(camera.eye,d,profile.radius+point.material.height*profile.metresPerBlock);if(!hit||hit.near<=0)return null;
 const position=camera.eye.map((v,i)=>v+d[i]*hit.near);
 return {distance:hit.near,position,normal:unit(position),material:point.material,residual:0};
}
