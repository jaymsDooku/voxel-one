import {viewRay,atlasSample} from './coarse-view.mjs';
import {sphere,unit,dot,integrate,sunlight} from './atmosphere.mjs';
// One small prepared orbital image exists before the entry can start. This
// bootstrap never samples terrain or traces a height field, and is replaced
// by the spatial atmosphere/terrain parent as its bounded job completes.
export function bootstrapPlanet(profile,atlas,camera,aspect){
 const width=Math.min(256,Math.max(64,Math.round(160*aspect))),height=Math.round(width/aspect),pixels=new Uint8ClampedArray(width*height*4),r=profile.radius;
 const solar=Array.from({length:33},(_,i)=>{const mu=i/16-1;return sunlight(profile.atmosphere,[r+profile.landingHeight+2,0,0],[mu,Math.sqrt(Math.max(0,1-mu*mu)),0],4);});
 for(let y=0;y<height;y++)for(let x=0;x<width;x++){
  const d=viewRay(camera,(x+.5)/width,(y+.5)/height,aspect),hit=sphere(camera.eye,d,r+profile.landingHeight);let color=[.003,.006,.014];
  if(hit?.near>0){const n=unit(camera.eye.map((v,i)=>v+d[i]*hit.near)),sample=atlasSample(atlas.data,atlas.width,atlas.height,n),light=Math.max(0,dot(n,profile.sun)),k=(dot(n,profile.sun)+1)*16,j=Math.min(31,Math.max(0,Math.floor(k))),f=Math.max(0,Math.min(1,k-j));color=sample.color.map((v,i)=>(v/255)**2.2*(.035+light*2.8*(solar[j][i]*(1-f)+solar[j+1][i]*f)));}
  else if(profile.atmosphere.enabled){const shell=sphere(camera.eye,d,r+profile.atmosphere.height);if(shell?.near>0){const air=integrate(profile.atmosphere,camera.eye,d,profile.sun,4*(r+Math.hypot(...camera.eye)),8,4);color=color.map((v,i)=>v*air.transmittance[i]+air.radiance[i]);}}
  const k=(y*width+x)*4;for(let i=0;i<3;i++)pixels[k+i]=255*(1-Math.exp(-Math.max(0,color[i])*1.1))**(1/2.2);pixels[k+3]=255;
 }
 return {pixels,width,height};
}
