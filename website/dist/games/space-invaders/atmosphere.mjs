// Port of dev.jayms.render.AtmosphereReference (original project code).
// Positions/distances in metres; RGB optical coefficients in inverse metres.
const add=(a,b)=>a.map((x,i)=>x+b[i]),mul=(a,s)=>a.map(x=>x*s),product=(a,b)=>a.map((x,i)=>x*b[i]);
export const dot=(a,b)=>a.reduce((s,x,i)=>s+x*b[i],0);
export function unit(v){if(!Array.isArray(v)||v.length!==3||v.some(x=>!Number.isFinite(x)))throw RangeError('Invalid direction');const n=Math.hypot(...v);if(n<1e-12)throw RangeError('Zero direction');return mul(v,1/n);}
const freeze=o=>{for(const v of Object.values(o))if(v&&typeof v==='object')freeze(v);return Object.freeze(o);};
export function atmospherePreset(name='earth'){
 if(!['earth','alien','thin','hazy','airless'].includes(name))throw RangeError('Invalid atmosphere preset');
 const thick=name==='thin'?.5:1,haze=name==='thin'?.25:name==='hazy'?3:1;
 return validateAtmosphere({version:1,enabled:name!=='airless',radius:6360000,height:100000*thick,metresPerBlock:50,molecular:name==='alien'?[5.8e-6,22e-6,18e-6]:[5.8e-6,13.5e-6,33.1e-6],aerosolScattering:[3.996e-6,3.996e-6,3.996e-6].map(x=>x*haze),aerosolExtinction:[4.44e-6,4.44e-6,4.44e-6].map(x=>x*haze),absorption:[.65e-6,1.881e-6,.085e-6],molecularScale:8000*thick,aerosolScale:1200*thick,absorptionCentre:25000*thick,absorptionWidth:15000*thick,anisotropy:.76,groundAlbedo:[.1,.1,.1],solarIrradiance:[18,18,18]});
}
export function validateAtmosphere(input){
 const c=structuredClone(input),range=(x,a,b)=>Number.isFinite(x)&&x>=a&&x<=b;
 if(c.version!==1||typeof c.enabled!=='boolean'||!range(c.radius,1000,1e9)||!range(c.height,Math.max(1,c.radius*1e-6),Math.min(c.radius,1e7))||!range(c.metresPerBlock,.001,1e6)||!range(c.anisotropy,-.95,.95))throw RangeError('Invalid atmosphere units/configuration');
 for(const n of ['molecular','aerosolScattering','aerosolExtinction','absorption','solarIrradiance'])if(!Array.isArray(c[n])||c[n].length!==3||c[n].some(x=>!range(x,0,n==='solarIrradiance'?100:.01)))throw RangeError('Invalid atmosphere coefficient');
 if(!Array.isArray(c.groundAlbedo)||c.groundAlbedo.length!==3||c.groundAlbedo.some(x=>!range(x,0,1)))throw RangeError('Invalid ground albedo');
 if(c.aerosolScattering.some((x,i)=>x>c.aerosolExtinction[i]))throw RangeError('Extinction below scattering');
 for(const n of ['molecularScale','aerosolScale','absorptionWidth'])if(!range(c[n],Math.max(1,c.radius*1e-6),c.height))throw RangeError('Invalid density scale');
 if(!range(c.absorptionCentre,0,c.height))throw RangeError('Invalid absorption centre');return freeze(c);
}
export function sphere(p,direction,radius){
 if(!Number.isFinite(radius)||radius<=0)throw RangeError('Invalid radius');const d=unit(direction),b=dot(p,d),r=Math.hypot(...p),c=(r-radius)*(r+radius),disc=b*b-c;
 const magnitude=Math.max(b*b,Math.abs(c)),ulp=magnitude===0?Number.MIN_VALUE:2**(Math.floor(Math.log2(magnitude))-52);
 if(disc < -8*ulp)return null;const root=Math.sqrt(Math.max(0,disc)),q=-b-(b<0?-root:root),a=q===0?-b:q,z=q===0?-b:c/q;return {near:Math.min(a,z)||0,far:Math.max(a,z)||0};
}
export function path(c,p,d,distance){
 if(!Number.isFinite(distance)||distance<0)throw RangeError('Invalid path length');if(Math.hypot(...p)<c.radius-.001)return null;
 const shell=sphere(p,d,c.radius+c.height);if(!shell)return null;let near=Math.max(0,shell.near),far=Math.min(distance,shell.far);const g=sphere(p,d,c.radius);if(g&&g.far>0&&g.near>=-.001)far=Math.min(far,Math.max(0,g.near));return far>near?{near,far}:null;
}
export function density(c,h){return h<0||h>c.height||!c.enabled?[0,0,0]:[Math.exp(-h/c.molecularScale),Math.exp(-h/c.aerosolScale),Math.max(0,1-Math.abs(h-c.absorptionCentre)/c.absorptionWidth)];}
export const extinction=(c,rho)=>add(add(mul(c.molecular,rho[0]),mul(c.aerosolExtinction,rho[1])),mul(c.absorption,rho[2]));
function samples(n){if(!Number.isInteger(n)||n<1||n>4096)throw RangeError('Samples outside 1..4096');}
const expNegative=a=>a.map(x=>Math.exp(-x));
export function opticalDepth(c,p,direction,distance,n){samples(n);const d=unit(direction),s=path(c,p,d,distance);if(!s||!c.enabled)return [0,0,0];const step=(s.far-s.near)/n;let depth=[0,0,0];for(let i=0;i<n;i++)depth=add(depth,mul(extinction(c,density(c,Math.hypot(...add(p,mul(d,s.near+(i+.5)*step)))-c.radius)),step));return depth;}
export const transmittance=(c,p,d,dist,n)=>expNegative(opticalDepth(c,p,d,dist,n));
export function sunlight(c,p,direction,n){const d=unit(direction),g=sphere(p,d,c.radius);if(Math.hypot(...p)<c.radius||g&&g.far>0&&g.near>=-.001)return [0,0,0];return transmittance(c,p,d,2*(c.radius+c.height),n);}
export function integrate(c,p,direction,sunDirection,distance,n,sunSamples,multipleLookup){
 samples(n);samples(sunSamples);const d=unit(direction),sun=unit(sunDirection),s=path(c,p,d,distance);if(!s||!c.enabled)return {transmittance:[1,1,1],radiance:[0,0,0]};
 const mu=Math.max(-1,Math.min(1,dot(d,sun))),g=c.anisotropy,phaseR=3*(1+mu*mu)/(16*Math.PI),phaseM=(1-g*g)/(4*Math.PI*(1+g*g-2*g*mu)**1.5),step=(s.far-s.near)/n;let depth=[0,0,0],radiance=[0,0,0];
 for(let i=0;i<n;i++){const q=add(p,mul(d,s.near+(i+.5)*step)),rho=density(c,Math.hypot(...q)-c.radius),sigma=extinction(c,rho),source=add(mul(c.molecular,rho[0]*phaseR),mul(c.aerosolScattering,rho[1]*phaseM)),visibility=product(expNegative(add(depth,mul(sigma,step*.5))),sunlight(c,q,sun,sunSamples));radiance=add(radiance,mul(product(product(source,visibility),c.solarIrradiance),step));if(multipleLookup){const incoming=multipleLookup(q,sun),scatter=add(mul(c.molecular,rho[0]),mul(c.aerosolScattering,rho[1]));radiance=add(radiance,mul(product(product(scatter,expNegative(add(depth,mul(sigma,step*.5)))),incoming),step));}depth=add(depth,mul(sigma,step));}
 return {transmittance:expNegative(depth),radiance};
}
// Original AtmosphereReference isotropic closure; feedback is conservatively capped at .95.
export function multiple(c,altitude,sunCosine,directions,n,sunSamples){
 if(!Number.isFinite(altitude)||altitude<0||altitude>c.height||!Number.isFinite(sunCosine)||sunCosine<-1||sunCosine>1||!Number.isInteger(directions)||directions<4||directions>256)throw RangeError('Invalid multiple-scattering coordinate');samples(n);samples(sunSamples);if(!c.enabled)return [0,0,0];
 const p=[0,c.radius+Math.max(1,altitude),0],sun=[Math.sqrt(1-sunCosine*sunCosine),sunCosine,0];let meanLight=[0,0,0],meanFeedback=[0,0,0];
 for(let j=0;j<directions;j++){const y=1-2*(j+.5)/directions,phi=j*2.39996323,d=[Math.sqrt(1-y*y)*Math.cos(phi),y,Math.sqrt(1-y*y)*Math.sin(phi)],single=integrate(c,p,d,sun,2*(c.radius+c.height),n,sunSamples);let light=single.radiance,feedback=[0,0,0],viewT=[1,1,1];const range=path(c,p,d,2*(c.radius+c.height));
  if(range){const step=(range.far-range.near)/n;for(let i=0;i<n;i++){const q=add(p,mul(d,range.near+(i+.5)*step)),rho=density(c,Math.hypot(...q)-c.radius),sigma=extinction(c,rho),scatter=add(mul(c.molecular,rho[0]),mul(c.aerosolScattering,rho[1])),stepT=expNegative(mul(sigma,step)),fraction=stepT.map((v,k)=>(1-v)*scatter[k]/Math.max(1e-11,sigma[k]));feedback=add(feedback,product(viewT,fraction));viewT=product(viewT,stepT);}
   const ground=sphere(p,d,c.radius);if(ground&&ground.near>0&&ground.near<=range.far+1){const q=add(p,mul(d,ground.near)),up=unit(q),bounced=mul(product(product(c.groundAlbedo,c.solarIrradiance),sunlight(c,add(q,mul(up,2)),sun,sunSamples)),Math.max(0,dot(up,sun))/Math.PI);light=add(light,product(single.transmittance,bounced));feedback=add(feedback,product(viewT,c.groundAlbedo));}
  }
  meanLight=add(meanLight,mul(light,1/directions));meanFeedback=add(meanFeedback,mul(feedback,1/directions));
 }
 return meanLight.map((v,i)=>v/(1-Math.min(.95,meanFeedback[i])));
}
export function buildMultipleTable(c,quality='Medium'){
 const width=quality==='High'?16:8,height=quality==='Low'?8:16,directions=quality==='Low'?4:quality==='High'?16:8,data=new Float32Array(width*height*4);
 for(let y=0;y<height;y++)for(let x=0;x<width;x++){const light=multiple(c,(y/(height-1))**2*c.height,x/(width-1)*2-1,directions,8,4),k=(y*width+x)*4;light.forEach((v,i)=>data[k+i]=v);}
 return {width,height,data};
}
export function sampleMultiple(table,c,p,sun){
 const length=Math.hypot(...p),altitude=length-c.radius;if(!table||!c.enabled||altitude<0||altitude>c.height)return [0,0,0];const x=Math.max(0,Math.min(table.width-1,(dot(p,sun)/length*.5+.5)*(table.width-1))),y=Math.sqrt(altitude/c.height)*(table.height-1),x0=Math.floor(x),y0=Math.floor(y),x1=Math.min(table.width-1,x0+1),y1=Math.min(table.height-1,y0+1),u=x-x0,v=y-y0;
 return [0,1,2].map(i=>{const value=(a,b)=>table.data[(b*table.width+a)*4+i];return (value(x0,y0)*(1-u)+value(x1,y0)*u)*(1-v)+(value(x0,y1)*(1-u)+value(x1,y1)*u)*v;});
}
