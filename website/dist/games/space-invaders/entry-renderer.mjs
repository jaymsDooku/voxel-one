import {terrainPlan,DETAIL_LIMITS,terrainFeather} from './terrain-detail.mjs';
import {combatCameraAt} from './entry-sequence.mjs';
import {viewRay,coarseSample,surfaceRay} from './coarse-view.mjs';
import {cloudDensity,cloudInterval} from './clouds.mjs';
import {MATERIAL_COLORS} from './terrain.mjs';
import {cameraAt,EntrySequence} from './entry-sequence.mjs';
export {cameraAt} from './entry-sequence.mjs';
// Original browser adapter. SI metres, linear radiance; one display conversion.
// No external assets. Byte textures avoid float-target/filter extension assumptions.
import {createPlanetVisualProfile,macroSample,sharedSample,localSphere,sphericalLocal} from './planet-profile.mjs';
import {integrate,atmospherePreset,dot,sunlight,sampleMultiple} from './atmosphere.mjs';
const vertex=`#version 300 es
in vec2 position;out vec2 uv;
void main(){uv=position*.5+.5;gl_Position=vec4(position,0,1);}`;
const fragment=`#version 300 es
precision highp float;
in vec2 uv;out vec4 outColor;
uniform vec2 viewport;uniform vec3 eye,forward,right,up,sun;
uniform float radius,airHeight,time,enabled,exposure,cloudPhase,baseHeight;uniform int samples;
uniform sampler2D atlas,landingPatch,multipleLut;uniform vec2 multipleSize;uniform vec3 betaR,betaM,betaE,absorption;uniform vec4 scales;
vec2 sphere(vec3 p,vec3 d,float r){float b=dot(p,d),c=(length(p)-r)*(length(p)+r),det=b*b-c;if(det<0.)return vec2(1e20,-1e20);float q=-b-sign(b==0.?1.:b)*sqrt(det);float a=q,z=q==0.?-b:c/q;return vec2(min(a,z),max(a,z));}
vec3 density(float h){return h<0.||h>airHeight?vec3(0):vec3(exp(-h/scales.x),exp(-h/scales.y),max(0.,1.-abs(h-scales.z)/scales.w));}
vec3 sigma(vec3 rho){return betaR*rho.x+betaE*rho.y+absorption*rho.z;}
vec2 path(vec3 p,vec3 d,float dist){if(length(p)<radius-.1)return vec2(1.,0.);vec2 s=sphere(p,d,radius+airHeight),g=sphere(p,d,radius);s=vec2(max(0.,s.x),min(dist,s.y));if(g.y>0.&&g.x>=-.1)s.y=min(s.y,max(0.,g.x));return s;}
vec3 sunlight(vec3 p){vec2 g=sphere(p,sun,radius);if(length(p)<radius||(g.y>0.&&g.x>=-.1))return vec3(0);vec2 s=path(p,sun,2.*(radius+airHeight));vec3 depth=vec3(0);float step=max(0.,s.y-s.x)/8.;for(int i=0;i<8;i++)depth+=sigma(density(length(p+sun*(s.x+(float(i)+.5)*step))-radius))*step;return exp(-depth*enabled);}
vec4 ground(vec3 p){vec3 n=normalize(p);vec2 st=vec2(atan(n.x,n.z)/6.2831853+.5,asin(clamp(n.y,-1.,1.))/3.14159265+.5);vec4 macro=texture(atlas,st);vec2 local=vec2(p.x,p.z)/50.;vec2 pt=vec2(local.x/216.+.5,.5-(local.y-66.)/144.);float blend=n.y>0.?1.-smoothstep(.48,.5,max(abs(pt.x-.5),abs(pt.y-.5))):0.;return mix(macro,texture(landingPatch,clamp(pt,0.,1.)),blend);}
float cloud(vec3 p){vec3 n=normalize(p);vec2 center=vec2(.03*sin(cloudPhase),.03*cos(cloudPhase)),delta=n.xz-center;float storm=.7*exp(-dot(delta,delta)/.04),detail=.25*sin(n.x*15.+n.z*11.+cloudPhase+time*.015)*sin(n.z*21.-n.x*13.+cloudPhase*1.7)+.08*sin(n.x*35.+n.z*39.+cloudPhase*.4);return clamp((storm+detail-.35)/.55,0.,1.);}
vec2 cloudPath(vec3 p,vec3 d,float h,float thick,float dist){vec2 outer=sphere(p,d,radius+h+thick*.5),inner=sphere(p,d,radius+h-thick*.5);float near=max(0.,outer.x),far=min(dist,outer.y);if(inner.y>=inner.x){if(length(p)<radius+h-thick*.5)near=max(near,inner.y);else if(inner.x>=0.)far=min(far,inner.x);}return vec2(near,far);}
vec3 multipleLight(vec3 p){float l=length(p),h=l-radius;if(h<0.||h>airHeight||enabled==0.)return vec3(0);vec2 st=vec2(clamp(dot(p/l,sun)*.5+.5,0.,1.),sqrt(clamp(h/airHeight,0.,1.)))*(multipleSize-1.);ivec2 lo=ivec2(floor(st)),hi=min(lo+1,ivec2(multipleSize)-1);vec2 f=fract(st);return mix(mix(texelFetch(multipleLut,lo,0).rgb,texelFetch(multipleLut,ivec2(hi.x,lo.y),0).rgb,f.x),mix(texelFetch(multipleLut,ivec2(lo.x,hi.y),0).rgb,texelFetch(multipleLut,hi,0).rgb,f.x),f.y);}
void main(){vec2 xy=(uv*2.-1.)*vec2(1.,viewport.y/viewport.x);vec3 d=normalize(forward+right*xy.x*.52+up*xy.y*.52);vec2 hit=sphere(eye,d,radius+baseHeight);float dist=hit.x>0.?hit.x:1e20;vec3 color=vec3(.003,.006,.014);
// Analytic parent sphere always exists; refine shared byte height field without holes.
if(dist<1e19){for(int j=0;j<6;j++){vec4 tex=ground(eye+d*dist);vec2 h=sphere(eye,d,radius+tex.a*6400.);if(h.x>0.)dist=h.x;}vec3 p=eye+d*dist,n=normalize(p);vec4 tex=ground(p);float light=max(0.,dot(n,sun));color=pow(tex.rgb,vec3(2.2))*(vec3(.035)+sunlight(p+n*2.)*light*2.8);}
float mu=dot(d,sun),phaseR=3.*(1.+mu*mu)/(16.*3.14159265),g=.76,phaseM=(1.-g*g)/(4.*3.14159265*pow(1.+g*g-2.*g*mu,1.5));vec2 s=path(eye,d,dist);vec3 depth=vec3(0),air=vec3(0);float step=max(0.,s.y-s.x)/float(samples);
for(int i=0;i<32;i++){if(i>=samples)break;vec3 p=eye+d*(s.x+(float(i)+.5)*step),rho=density(length(p)-radius),sig=sigma(rho)*enabled;vec3 source=(betaR*rho.x*phaseR+betaM*rho.y*phaseM)*enabled;air+=source*exp(-(depth+sig*step*.5))*sunlight(p)*18.*step;air+=(betaR*rho.x+betaM*rho.y)*exp(-(depth+sig*step*.5))*multipleLight(p)*enabled*step;depth+=sig*step;}
// Separate bounded cloud shells; never in airless mode or behind solid ground.
vec3 cloudLight[2];float cloudAlpha[2],cloudDistance[2];for(int j=0;j<2;j++){float h=j==0?4500.:1800.,thick=j==0?600.:450.;vec2 range=cloudPath(eye,d,h,thick,dist);cloudAlpha[j]=0.;cloudDistance[j]=0.;cloudLight[j]=vec3(0);if(range.y>range.x&&enabled>0.){float t=(range.x+range.y)*.5;vec3 p=eye+d*t,solar=sunlight(p);cloudDistance[j]=t;cloudAlpha[j]=min(.55,1.-exp(-cloud(p)*(range.y-range.x)*.6/thick));cloudLight[j]=vec3(.01)+solar*max(0.,dot(normalize(p),sun))*.9;}}
if(cloudDistance[0]<cloudDistance[1]){color=mix(color,cloudLight[1],cloudAlpha[1]);color=mix(color,cloudLight[0],cloudAlpha[0]);}else{color=mix(color,cloudLight[0],cloudAlpha[0]);color=mix(color,cloudLight[1],cloudAlpha[1]);}
color=color*exp(-depth)+air;
if(dist>1e19){float stars=pow(max(0.,sin(dot(floor(xy*800.),vec2(12.9898,78.233)))*43758.5453-floor(sin(dot(floor(xy*800.),vec2(12.9898,78.233)))*43758.5453)),120.);float visibility=1.-smoothstep(.001,.05,dot(air,vec3(.2126,.7152,.0722)));color+=vec3(stars)*exp(-depth)*.4*visibility;}
color=vec3(1)-exp(-max(color,vec3(0))*exposure);outColor=vec4(pow(color,vec3(1./2.2)),1);}`;
const norm=v=>{const n=Math.hypot(...v);return v.map(x=>x/n);};
const cross=(a,b)=>[a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]];
function shader(gl,type,source){const s=gl.createShader(type);gl.shaderSource(s,source);gl.compileShader(s);if(!gl.getShaderParameter(s,gl.COMPILE_STATUS)){const error=gl.getShaderInfoLog(s);gl.deleteShader(s);throw Error('Entry shader compilation failed: '+error);}return s;}
export class EntryRenderer {
 constructor(){this.canvas=document.createElement('canvas');this.backend='Canvas fallback';this.gl=null;this.profile=null;this.quality='Low';this.air='alien';this.forceFallback=true;this.attempted=false;
  this.canvas.addEventListener('webglcontextlost',e=>{e.preventDefault();this.backend='Canvas fallback';this.gl=null;this.attempted=true;});
 }
 initGL(){if(this.attempted)return;this.attempted=true;try{const gl=this.canvas.getContext('webgl2',{alpha:false,antialias:false});if(!gl)return;this.capabilities={webgl2:true,floatTargets:!!gl.getExtension('EXT_color_buffer_float'),floatFiltering:!!gl.getExtension('OES_texture_float_linear'),format:'RGBA8'};this.gl=gl;const p=gl.createProgram(),v=shader(gl,gl.VERTEX_SHADER,vertex),f=shader(gl,gl.FRAGMENT_SHADER,fragment);gl.attachShader(p,v);gl.attachShader(p,f);gl.linkProgram(p);gl.deleteShader(v);gl.deleteShader(f);if(!gl.getProgramParameter(p,gl.LINK_STATUS))throw Error('Entry shader link failed');this.program=p;gl.useProgram(p);const b=gl.createBuffer();this.buffer=b;gl.bindBuffer(gl.ARRAY_BUFFER,b);gl.bufferData(gl.ARRAY_BUFFER,new Float32Array([-1,-1,1,-1,-1,1,-1,1,1,-1,1,1]),gl.STATIC_DRAW);const loc=gl.getAttribLocation(p,'position');gl.enableVertexAttribArray(loc);gl.vertexAttribPointer(loc,2,gl.FLOAT,false,0,0);this.backend='WebGL2';}catch(error){this.failure=error.message;this.dispose();}if(this.atlasData)this.upload();}

 prepare(world,scene,aspect=this.cacheAspect||1.42){
  const started=performance.now();
  if(this.profile?.seed===world.config.seed&&this.revision===scene){if(Math.abs(this.cacheAspect-aspect)>.001)this.startCache(aspect);return;}
  this.surfaceSolar=null;this.cacheComposePeak=0;this.preparationFacePeak=0;this.frozenKey=null;this.revision=scene;this.world=world;const c=world.config;
  this.sequence?.cancel();this.profile=createPlanetVisualProfile(c,this.air||'earth');this.sequence=new EntrySequence(this.profile);this.scene=scene;this.fineScene=null;this.cache=[];
  const w=32,h=16,data=new Uint8Array(w*h*4);for(let y=0;y<h;y++)for(let x=0;x<w;x++){
   const lat=(y/(h-1)-.5)*Math.PI,lon=(x/w-.5)*Math.PI*2,n=[Math.cos(lat)*Math.sin(lon),Math.sin(lat),Math.cos(lat)*Math.cos(lon)],sample=macroSample(world,n),k=(y*w+x)*4;
   sample.color.forEach((v,j)=>data[k+j]=v);data[k+3]=sample.height/128*255;
  }
  const coarseColumns=[],patch=new Uint8Array(72*48*4);for(let row=0;row<48;row++)for(let col=0;col<72;col++){
   const s=(scene?.view.centerX===0&&scene?.view.centerZ===66?scene.columns[row*72+col]:null)||world.surface((col-36)*3,66+(24-row)*3),k=(row*72+col)*4;
   coarseColumns.push({...s,x:(col-36)*3,z:66+(24-row)*3,col,row,spans:s.spans||[]});
   const hex=c.palette[s.biome][0];for(let j=0;j<3;j++)patch[k+j]=parseInt(hex.slice(1+j*2,3+j*2),16);patch[k+3]=Math.max(0,Math.min(255,s.height/128*255));
  }
  this.scene={view:{cols:72,rows:48,step:3,centerX:0,centerZ:66},columns:coarseColumns};this.orderedCoarse=[...coarseColumns].sort((a,b)=>b.z-a.z).map(col=>({...col,source:this.scene,detail:false}));
  this.atlasWidth=w;this.atlasHeight=h;this.atlasData=data;this.patchData=patch;this.upload();this.startCache(aspect);this.prepareMilliseconds=performance.now()-started;
 }
 startCache(aspect=1.42){
  this.cacheWorker?.terminate();this.cacheEpoch=(this.cacheEpoch||0)+1;if(Math.abs((this.cacheAspect||aspect)-aspect)>.001)this.cache=[];else this.cache=this.cache||[];this.cacheAspect=aspect;this.cacheIndex=0;this.cacheFailed=false;
  if(typeof Worker==='undefined')return;
  try{this.cacheWorker=new Worker(new URL('./entry-worker.mjs',import.meta.url),{type:'module'});this.cacheWorker.onmessage=({data})=>{if(data.epoch!==this.cacheEpoch)return;if(data.multiple){this.multipleTable=data.multiple;}if(data.atlas){this.atlasData=data.atlas;this.atlasWidth=128;this.atlasHeight=64;this.upload();}if(data.scene){this.fineScene=data.scene;this.orderedFine=[...data.scene.columns].sort((a,b)=>b.z-a.z).map(col=>({...col,source:data.scene,detail:true}));this.frozenKey=null;}if(data.error){this.cacheFailed=true;this.cacheWorker.terminate();this.cacheWorker=null;return;}const cacheStarted=performance.now(),raw=document.createElement('canvas');raw.width=data.width;raw.height=data.height;raw.getContext('2d').putImageData(new ImageData(data.pixels,data.width,data.height),0,0);const canvas=document.createElement('canvas'),cap=this.quality==='Low'?320:this.quality==='Medium'?480:640;canvas.width=Math.round(Math.min(cap,cap*.75*this.cacheAspect));canvas.height=Math.round(canvas.width/this.cacheAspect);const ctx=canvas.getContext('2d');ctx.drawImage(raw,0,0,canvas.width,canvas.height);const previousFaces=this.faceWork,previousLimit=this.faceLimit;this.faceWork=0;this.faceLimit=DETAIL_LIMITS[this.quality].faces;const camera=cameraAt(data.index/4,this.profile);this.moons(ctx,canvas.width,canvas.height,camera,this.air);this.localTerrain(ctx,canvas.width,canvas.height,camera,this.air);this.preparationFacePeak=Math.max(this.preparationFacePeak||0,this.faceWork);this.faceWork=previousFaces;this.faceLimit=previousLimit;this.cacheComposePeak=Math.max(this.cacheComposePeak||0,performance.now()-cacheStarted);this.cache[data.index]=canvas;this.cacheIndex++;if(this.cacheIndex<=48)this.cacheJob();else {this.cacheWorker.terminate();this.cacheWorker=null;}};this.cacheWorker.onerror=()=>{this.cacheFailed=true;this.cacheWorker?.terminate();this.cacheWorker=null;};this.cacheJob();}catch{this.cacheWorker=null;}
 }
 cacheJob(){this.cacheWorker?.postMessage({key:this.profile.seed+this.cacheEpoch,config:this.profile.config,edits:[...this.world.edits],air:this.air||'earth',time:this.cacheIndex/4,index:this.cacheIndex,epoch:this.cacheEpoch,aspect:this.cacheAspect,quality:this.quality});}
 cancel(){this.sequence?.cancel();this.cacheEpoch=(this.cacheEpoch||0)+1;this.cacheWorker?.terminate();this.cacheWorker=null;this.cache=[];this.profile=null;for(const tex of this.textures||[])this.gl?.deleteTexture(tex);this.textures=[];if(this.multipleTexture)this.gl?.deleteTexture(this.multipleTexture);this.multipleTexture=null;this.multipleTable=null;this.fineScene=null;this.scene=null;this.world=null;this.atlasData=null;this.patchData=null;this.orderedCoarse=[];this.orderedFine=[];this.detailPlan=null;this.planScene=null;this.planFine=null;this.frozenFrame=null;this.worldCanvas=null;this.revision=null;}
 upload(){const gl=this.gl;if(!gl)return;for(const [i,data,w,h] of [[0,this.atlasData,this.atlasWidth,this.atlasHeight],[1,this.patchData,72,48]]){if(this.textures?.[i])gl.deleteTexture(this.textures[i]);this.textures??=[];const tex=gl.createTexture();this.textures[i]=tex;gl.activeTexture(gl.TEXTURE0+i);gl.bindTexture(gl.TEXTURE_2D,tex);gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA,w,h,0,gl.RGBA,gl.UNSIGNED_BYTE,data);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MIN_FILTER,gl.LINEAR);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MAG_FILTER,gl.LINEAR);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_S,i?gl.CLAMP_TO_EDGE:gl.REPEAT);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_T,gl.CLAMP_TO_EDGE);}if(this.multipleTexture)gl.deleteTexture(this.multipleTexture);this.multipleTexture=gl.createTexture();gl.activeTexture(gl.TEXTURE2);gl.bindTexture(gl.TEXTURE_2D,this.multipleTexture);const table=this.multipleTable||{width:1,height:1,data:new Float32Array(4)};gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA32F,table.width,table.height,0,gl.RGBA,gl.FLOAT,table.data);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MIN_FILTER,gl.NEAREST);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MAG_FILTER,gl.NEAREST);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_S,gl.CLAMP_TO_EDGE);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_T,gl.CLAMP_TO_EDGE);}
 renderCombat(ctx,w,h,elapsed){return this.render(ctx,w,h,12,this.air,combatCameraAt(elapsed,this.profile));}
 render(ctx,width,height,t,air=this.air||'earth',override=null){
  if(!this.profile)return;const started=performance.now();this.faceWork=0;this.faceLimit=DETAIL_LIMITS[this.quality].faces;this.lastCamera=override||cameraAt(t,this.profile);if(!this.forceFallback&&!this.gl)this.initGL();const frozenKey=`${override?.handoff??"entry"},${width},${height},${t},${this.air},${this.forceFallback},${this.quality},${!!this.cache?.[Math.min(48,Math.floor(t*4))]}`;if(this.frozenKey===frozenKey&&this.frozenFrame){ctx.drawImage(this.frozenFrame,0,0,width,height);this.renderMilliseconds=performance.now()-started;return this.lastCamera;}if(Math.abs((this.cacheAspect||1)-width/height)>.001)this.startCache(width/height);const camera=this.lastCamera,gl=this.gl;
  if(!gl||this.forceFallback){const cap=this.quality==='Low'?320:this.quality==='Medium'?480:640,scale=Math.min(1,cap/width,cap*.75/height),rw=Math.max(1,Math.round(width*scale)),rh=Math.max(1,Math.round(height*scale));this.worldCanvas??=document.createElement('canvas');if(this.worldCanvas.width!==rw)this.worldCanvas.width=rw;if(this.worldCanvas.height!==rh)this.worldCanvas.height=rh;this.worldResolution={width:rw,height:rh};this.fallback(this.worldCanvas.getContext('2d'),rw,rh,camera,t,air);ctx.drawImage(this.worldCanvas,0,0,width,height);this.frozenFrame=this.worldCanvas;this.frozenKey=frozenKey;this.renderMilliseconds=performance.now()-started;return camera;}
  const scale=this.quality==='Low'?.5:this.quality==='High'?1:.75;const rw=Math.min(640,Math.ceil(width*scale)),rh=Math.min(400,Math.ceil(height*scale));if(this.canvas.width!==rw||this.canvas.height!==rh){this.canvas.width=rw;this.canvas.height=rh;}gl.viewport(0,0,this.canvas.width,this.canvas.height);gl.useProgram(this.program);
  const loc=n=>gl.getUniformLocation(this.program,n),v=(n,a)=>gl.uniform3fv(loc(n),a),f=(n,a)=>gl.uniform1f(loc(n),a);
  gl.uniform2f(loc('viewport'),width,height);for(const n of ['eye','forward','right','up'])v(n,camera[n]);v('sun',this.profile.sun);f('radius',this.profile.radius);f('baseHeight',this.profile.landingHeight);f('time',t);f('cloudPhase',(this.profile.cloudSeed%65536)/65536*Math.PI*2);f('enabled',air==='airless'?0:1);f('airHeight',air==='thin'?50000:100000);f('exposure',1.1+camera.progress*.5);const haze=air==='thin'?.25:air==='hazy'?3:1;v('betaR',this.profile.atmosphere.molecular);v('betaM',[3.996e-6*haze,3.996e-6*haze,3.996e-6*haze]);v('betaE',[4.44e-6*haze,4.44e-6*haze,4.44e-6*haze]);v('absorption',[.65e-6,1.881e-6,.085e-6]);gl.uniform4f(loc('scales'),air==='thin'?4000:8000,air==='thin'?600:1200,air==='thin'?12500:25000,air==='thin'?7500:15000);gl.uniform1i(loc('samples'),this.quality==='Low'?8:this.quality==='High'?32:16);gl.uniform1i(loc('atlas'),0);gl.uniform1i(loc('landingPatch'),1);gl.uniform1i(loc('multipleLut'),2);gl.uniform2f(loc('multipleSize'),this.multipleTable?.width||1,this.multipleTable?.height||1);gl.drawArrays(gl.TRIANGLES,0,6);ctx.drawImage(this.canvas,0,0,width,height);this.moons(ctx,width,height,camera,air);this.localTerrain(ctx,width,height,camera,air);this.renderMilliseconds=performance.now()-started;return camera;
 }
 project(p,c,w,h){const v=p.map((x,i)=>x-c.eye[i]),z=dot(v,c.forward);if(z<=1)return null;return [w*.5+dot(v,c.right)/z*w/(2*.52),h*.5-dot(v,c.up)/z*w/(2*.52),z];}
 polygon(ctx,points,color,c,w,h){
  if(this.faceWork>=this.faceLimit)return;this.faceWork++;
  // Near-plane clip avoids holes when a voxel face straddles the camera.
  const out=[];for(let i=0;i<points.length;i++){const a=points[i],b=points[(i+1)%points.length],za=dot(a.map((x,j)=>x-c.eye[j]),c.forward),zb=dot(b.map((x,j)=>x-c.eye[j]),c.forward);if(za>2)out.push(a);if((za>2)!==(zb>2)){const f=(2-za)/(zb-za);out.push(a.map((x,j)=>x+(b[j]-x)*f));}}
  if(out.length<3)return;let projected=out.map(p=>this.project(p,c,w,h));if(projected.some(p=>!p))return;
  if(Math.max(...projected.map(p=>p[0]))<0||Math.min(...projected.map(p=>p[0]))>w||Math.max(...projected.map(p=>p[1]))<0||Math.min(...projected.map(p=>p[1]))>h)return;
  for(const [axis,bound,sign] of [[0,0,1],[0,w,-1],[1,0,1],[1,h,-1]]){const clipped=[];for(let i=0;i<projected.length;i++){const a=projected[i],b=projected[(i+1)%projected.length],insideA=(a[axis]-bound)*sign>=0,insideB=(b[axis]-bound)*sign>=0;if(insideA)clipped.push(a);if(insideA!==insideB){const f=(bound-a[axis])/(b[axis]-a[axis]);clipped.push(a.map((v,j)=>v+(b[j]-v)*f));}}projected=clipped;}if(projected.length<3)return;
  ctx.fillStyle=color;ctx.beginPath();projected.forEach(([x,y],i)=>i?ctx.lineTo(x,y):ctx.moveTo(x,y));ctx.closePath();ctx.fill();
 }
 localTerrain(ctx,w,h,c,air){
  if(c.altitude>6000||!this.scene)return;
  if(this.planScene!==this.scene||this.planFine!==this.fineScene||this.planQuality!==this.quality){this.detailPlan=terrainPlan(this.scene,this.fineScene,this.quality);this.planScene=this.scene;this.planFine=this.fineScene;this.planQuality=this.quality;}const columns=c.altitude<4000&&this.fineScene?[...this.detailPlan.coarse,...this.detailPlan.fine]:this.detailPlan.coarse;const r=this.profile.radius,mpb=50;
  const solar=this.surfaceSolar??=sunlight(this.profile.atmosphere,[0,r+this.profile.landingHeight+2,0],this.profile.sun,8),topLight=.12+Math.max(0,this.profile.sun[1])*1.4;
  const atmosphere=this.profile.atmosphere,aerialCount=this.quality==='Low'?8:this.quality==='Medium'?12:16,aerialStep=20000/aerialCount,aerial=[];for(let i=0;i<=aerialCount;i++)aerial.push(integrate(atmosphere,c.eye,c.forward,this.profile.sun,i*aerialStep,this.quality==='Low'?4:8,this.quality==='Low'?2:4,this.multipleTable?(p,s)=>sampleMultiple(this.multipleTable,atmosphere,p,s):undefined));
  const alpha=Math.min(1,Math.max(0,(6000-c.altitude)/3000));ctx.save();ctx.globalAlpha=alpha;
  for(const col of columns){const source=col.source;ctx.globalAlpha=alpha*terrainFeather(col)*(col.detail?Math.max(0,Math.min(1,(4000-c.altitude)/2500)):1);const x=col.x*mpb,z=col.z*mpb,size=source.view.step*mpb*col.stride,y=r+col.height*mpb;
   const front=this.project([x,y,z],c,w,h);if(!front)continue;
   const color=this.profile.palette[col.biome][0].match(/[a-f0-9]{2}/gi).map(x=>parseInt(x,16)),distance=Math.hypot(x-c.eye[0],y-c.eye[1],z-c.eye[2]),index=Math.min(aerialCount,Math.max(0,Math.round(distance/aerialStep))),sample=aerial[index];
   const shade=(light,mat=color)=>`rgb(${mat.map((v,i)=>{const surface=(v/255)**2.2*light*(.12+solar[i]*2.8),radiance=surface*sample.transmittance[i]+sample.radiance[i];return Math.round(255*(1-Math.exp(-radiance*(1.1+c.progress*.5)))**(1/2.2));}).join(',')})`;
   const voxel=Math.max(0,Math.min(1,(1500-c.altitude)/1000)),parentCorners=col.parentCorners??=[[-1,-1],[1,-1],[1,1],[-1,1]].map(([dx,dz])=>{const px=x+dx*size/2,pz=z+dz*size/2,parent=sharedSample(this.world,this.profile,localSphere(this.profile,px/mpb,pz/mpb));return [px,r+parent.height*mpb,pz];}),corners=parentCorners.map(([px,py,pz])=>[px,py*(1-voxel)+y*voxel,pz]);
   this.polygon(ctx,corners,shade(topLight),c,w,h);
   // Regional surfaces slope into their parent. Exposed voxel faces grow only near ground.
   const faceAlpha=ctx.globalAlpha;ctx.globalAlpha*=voxel*terrainFeather(col);
   const bottom=r+col.nextHeight*mpb;
   this.polygon(ctx,[[x-size/2,y,z-size/2],[x+size/2,y,z-size/2],[x+size/2,bottom,z-size/2],[x-size/2,bottom,z-size/2]],shade(.12+Math.max(0,-this.profile.sun[2])),c,w,h);
   for(const direction of [-1,1]){const adjacent=source.columns[col.row*source.view.cols+col.col+direction*col.stride];if(!adjacent||adjacent.row!==col.row||adjacent.height>=col.height)continue;const edge=x+direction*size/2,floor=r+adjacent.height*mpb;this.polygon(ctx,[[edge,y,z-size/2],[edge,y,z+size/2],[edge,floor,z+size/2],[edge,floor,z-size/2]],shade(.12+Math.max(0,direction*this.profile.sun[0])*1.4),c,w,h);}
   ctx.globalAlpha=faceAlpha;const feature=col.landmark;for(const span of feature?.spans||[]){const x=feature.x*mpb,z=feature.z*mpb,size=source.view.step*mpb;if(span.top<=feature.height)continue;const top=r+(span.top+1)*mpb,bottom=r+Math.max(span.bottom,feature.height)*mpb,tint=MATERIAL_COLORS[span.material]?.match(/[a-f0-9]{2}/gi).map(v=>parseInt(v,16))||color;
    this.polygon(ctx,[[x-size/2,top,z-size/2],[x+size/2,top,z-size/2],[x+size/2,bottom,z-size/2],[x-size/2,bottom,z-size/2]],shade(.12+Math.max(0,-this.profile.sun[2])*1.4,tint),c,w,h);
    this.polygon(ctx,[[x-size/2,top,z-size/2],[x+size/2,top,z-size/2],[x+size/2,top,z+size/2],[x-size/2,top,z+size/2]],shade(topLight,tint),c,w,h);
    for(const direction of [-1,1]){const edge=x+direction*size/2;this.polygon(ctx,[[edge,top,z-size/2],[edge,top,z+size/2],[edge,bottom,z+size/2],[edge,bottom,z-size/2]],shade(.12+Math.max(0,direction*this.profile.sun[0])*1.4,tint),c,w,h);}
   }
  }
  ctx.restore();
 }
 moons(ctx,w,h,c,air){
  for(const moon of this.profile.moons){const d=moon.direction,z=dot(d,c.forward);if(z<=0)continue;const x=w*.5+dot(d,c.right)/z*w/1.04,y=h*.5-dot(d,c.up)/z*w/1.04;
   const size=Math.min(w,h)*.035;ctx.save();ctx.beginPath();ctx.arc(x,y,size,0,Math.PI*2);ctx.clip();ctx.fillStyle=moon.color;ctx.fill();ctx.fillStyle='#192238';ctx.beginPath();ctx.ellipse(x+size*moon.phase,y,size*(1-moon.phase),size,0,0,Math.PI*2);ctx.fill();ctx.restore();
  }
 }
 fallback(ctx,w,h,c,t,air){
  const index=Math.min(48,Math.floor(t*4)),a=c.combat?null:this.cache?.[index],b=this.cache?.[Math.min(48,index+1)];
  if(a){ctx.drawImage(a,0,0,w,h);if(b&&t<12){ctx.save();ctx.globalAlpha=t*4-index;ctx.drawImage(b,0,0,w,h);ctx.restore();}return;}
  const p=this.profile,config=p.atmosphere,r=p.radius,atlas={data:this.atlasData,width:this.atlasWidth,height:this.atlasHeight};
  const sample=n=>sharedSample(this.world,p,n),exposure=1.1+c.progress*.5;
  const display=color=>`rgb(${color.map(v=>Math.round(255*(1-Math.exp(-Math.max(0,v)*exposure))**(1/2.2))).join(',')})`;
  // Thirteen reference rays supply aerial perspective. No per-pixel integration.
  const rows=[];for(let row=0;row<=12;row++){
   const d=viewRay(c,.5,row/12,w/h),hit=surfaceRay(c,d,p,sample),distance=hit?.distance||2*(Math.hypot(...c.eye)+r+config.height);
   const atmosphere=integrate(config,c.eye,d,p.sun,distance,8,4,this.multipleTable?(q,sun)=>sampleMultiple(this.multipleTable,config,q,sun):undefined);
   const q=hit?hit.position.map((v,i)=>v+hit.normal[i]*2):[0,r+p.landingHeight+2,0];
   rows.push({...atmosphere,solar:sunlight(config,q,p.sun,4)});
  }
  const mix=(a,b,f)=>a.map((v,i)=>v*(1-f)+b[i]*f),cols=this.quality==='Low'?24:this.quality==='Medium'?40:64,meshRows=Math.max(16,Math.min(32,Math.round(cols*h/w)));
  for(let row=0;row<meshRows;row++){
   const y=(row+.5)/meshRows,k=y*12,j=Math.min(11,Math.floor(k)),f=k-j,a=rows[j],b=rows[j+1],trans=mix(a.transmittance,b.transmittance,f),radiance=mix(a.radiance,b.radiance,f),solar=mix(a.solar,b.solar,f);
   for(let col=0;col<cols;col++){
    const d=viewRay(c,(col+.5)/cols,y,w/h),hit=surfaceRay(c,d,p,sample);let color=[.003,.006,.014];
    if(hit){const local=sphericalLocal(p,hit.normal),step=Math.max(Math.abs(local.x)/108,Math.abs(local.z-66)/72)<1?3:32,dx=sample(localSphere(p,local.x+step,local.z)).height-sample(localSphere(p,local.x-step,local.z)).height,dz=sample(localSphere(p,local.x,local.z+step)).height-sample(localSphere(p,local.x,local.z-step)).height,normal=norm([hit.normal[0]-dx/(2*step),hit.normal[1],hit.normal[2]-dz/(2*step)]),light=Math.max(0,dot(normal,p.sun));color=hit.material.color.map((v,i)=>(v/255)**2.2*(.035+solar[i]*light*2.8));}
    if(config.enabled){const layers=[];for(const [altitude,thickness] of [[1800,450],[4500,600]]){const range=cloudInterval(c.eye,d,r,altitude,thickness,hit?.distance||Infinity);if(!range)continue;const distance=(range.near+range.far)/2,n=norm(c.eye.map((v,i)=>v+d[i]*distance)),opacity=Math.min(.55,1-Math.exp(-cloudDensity(n,p.cloudSeed,t)*(range.far-range.near)*.6/thickness));layers.push({distance,opacity,light:solar.map(v=>.01+v*Math.max(0,dot(n,p.sun))*.9)});}layers.sort((a,b)=>b.distance-a.distance);for(const layer of layers)color=color.map((v,i)=>v*(1-layer.opacity)+layer.light[i]*layer.opacity);}
    color=color.map((v,i)=>v*trans[i]+radiance[i]);
    ctx.fillStyle=display(color);ctx.fillRect(col*w/cols,row*h/meshRows,w/cols+.5,h/meshRows+.5);
   }
  }
  this.moons(ctx,w,h,c,air);this.localTerrain(ctx,w,h,c,air);
 }

 dispose(){const gl=this.gl;if(gl){for(const t of this.textures||[])gl.deleteTexture(t);if(this.buffer)gl.deleteBuffer(this.buffer);if(this.program)gl.deleteProgram(this.program);if(this.multipleTexture)gl.deleteTexture(this.multipleTexture);}this.gl=null;this.backend='Canvas fallback';}
}
