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
 let hit=sphere(camera.eye,d,profile.radius+profile.landingHeight);
 if(!hit||hit.near<=0)return null;let distance=hit.near,n,material;
 for(let i=0;i<4;i++){n=unit(camera.eye.map((v,j)=>v+d[j]*distance));material=sample(n);hit=sphere(camera.eye,d,profile.radius+material.height*profile.metresPerBlock);if(!hit||hit.near<=0)return null;distance=hit.near;}
 n=unit(camera.eye.map((v,j)=>v+d[j]*distance));return {distance,normal:n,material:sample(n),position:camera.eye.map((v,j)=>v+d[j]*distance)};
}
