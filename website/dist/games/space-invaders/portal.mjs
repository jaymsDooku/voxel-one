import {sweptPortalCrossing,immutableHandoff} from './alternate-earth.mjs';
import {EarthMission} from './earth-scene.mjs';
// One CPU destination is retained during preparation. No second GL world is drawn.
export class PortalCoordinator {
 constructor(source,prepare=()=>new EarthMission(source)){
  this.source=source;this.prepare=prepare;this.state='locked';this.epoch=0;this.elapsed=0;this.pending=null;this.destination=null;this.error='';this.commits=0;this.frame=null;this.opening=0;
 }
 locate(){
  const w=this.source.world,p=this.source.player;
  // Search a fully walkable local volume; never edit the frozen source terrain.
  for(const distance of [4,6,8,3,10])for(let i=0;i<16;i++){
   const yaw=p.yaw+i*Math.PI/8,x=p.x+Math.sin(yaw)*distance,z=p.z+Math.cos(yaw)*distance,s=w.safe(x,z,{radius:.32,height:3.6});if(!s)continue;
   const right={x:Math.cos(yaw),z:-Math.sin(yaw)},normal={x:Math.sin(yaw),z:Math.cos(yaw)};
   const points=[];let valid=true;
   for(const side of [-1.9,-1.4,-.9,-.4,0,.4,.9,1.4,1.9])for(const depth of [-1,0,1]){const q=w.safe(x+right.x*side+normal.x*depth,z+right.z*side+normal.z*depth,{radius:.32,height:3.6});if(!q||Math.abs(q.y-s.y)>.15){valid=false;break;}points.push(q);}
   if(!valid)continue;
   const entry={x:x-normal.x,y:s.y,z:z-normal.z};
   if(!w.traversable(p,entry)){const route=w.path(p,entry,2048).route;if(!route.length||!w.traversable(p,route[0])||!w.traversable(route.at(-1),entry))continue;}
   this.frame={...s,yaw,halfWidth:1.65,height:3.2};this.source.world.portalFrame=this.frame;return this.frame;
  }
  throw new Error('No clear portal route near this position. Walk to the open corridor and Retry.');
 }
 local(p){const f=this.frame,dx=p.x-f.x,dz=p.z-f.z;return {x:dx*Math.cos(f.yaw)-dz*Math.sin(f.yaw),y:p.y-f.y,z:dx*Math.sin(f.yaw)+dz*Math.cos(f.yaw)};}
 begin(){
  if(!['locked','failure'].includes(this.state))return false;
  this.state='opening';this.error='';this.elapsed=0;this.opening=0;const epoch=++this.epoch;
  try{if(!this.frame)this.locate();const start=performance.now(),result=this.prepare();this.prepareCallMs=performance.now()-start;if(result&&typeof result.then==='function')this.pending=Promise.resolve(result).then(value=>this.prepared(value,epoch),error=>this.fail(error,epoch));else this.prepared(result,epoch);}catch(error){this.fail(error,epoch);}
  return true;
 }
 prepared(value,epoch){if(epoch!==this.epoch||this.state!=='opening')return;if(!value?.world||!value?.player||value.payload.runId!==this.source.payload.runId||value.world.blocked(value.player.x,value.player.y,value.player.z))return this.fail(new Error('Destination spawn validation failed'),epoch);if(value.enemies.some(e=>Math.hypot(e.x-value.player.x,e.z-value.player.z)<14))return this.fail(new Error('Destination guard too close'),epoch);this.destination=value;this.pending=null;}
 fail(error,epoch=this.epoch){if(epoch!==this.epoch)return;this.epoch++;this.state='failure';this.error=String(error.message||error);this.pending=null;this.destination=null;}
 cancel(){if(!['failure','opening'].includes(this.state))return;this.epoch++;this.state='locked';this.destination=null;this.pending=null;this.elapsed=0;}
 update(dt,from,to,settings={}){
  dt=Math.max(0,Math.min(.1,dt));
  if(this.state==='opening'){this.elapsed+=dt;this.opening=Math.min(1,this.elapsed/1.3);if(this.elapsed>=8&&!this.destination)this.fail(new Error('Preparation timed out. Retry or cancel to the crash site.'));else if(this.destination&&this.opening===1){this.state='ready';this.source.events.push('portal');}}
  if(this.state==='ready'&&sweptPortalCrossing(this.local(from),this.local(to),this.frame,{radius:to.radius,capsuleHeight:to.height})){
   // Latch before any handoff operation or browser callback can run again.
   this.state='traversing';this.elapsed=0;this.source.events.push('portal');
   try{const s=this.source,p=s.player,d=this.destination;
    this.handoff=immutableHandoff({runId:s.payload.runId,missionId:s.id,worldId:String(s.world.revision),score:s.score,lives:s.lives,health:p.health,weapon:s.rules.weapon,ammunition:p.ammo,difficulty:settings.difficulty||'normal',settings,transform:{x:p.x,y:p.y,z:p.z,yaw:p.yaw,pitch:p.pitch}}, {missionId:d.id,worldId:String(d.world.revision),seed:d.payload.seed,transform:{...d.spawn,yaw:0}});
    this.arrivalYaw=p.yaw-this.frame.yaw;this.arrivalPitch=p.pitch;
   }catch(error){this.fail(error);}
  }
  else if(this.state==='traversing')this.elapsed+=dt;
 }
 commit(){
  if(this.state!=='traversing'||this.elapsed<.45)return null;
  const d=this.destination,s=this.handoff.source;
  d.score=s.score;d.lives=s.lives;d.rules.weapon=s.weapon;d.difficulty=s.difficulty;d.elapsed=this.source.elapsed;d.tick=this.source.tick;
  Object.assign(d.player,{health:s.health,ammo:s.ammunition,yaw:this.arrivalYaw,pitch:this.arrivalPitch,immune:3,vy:0,reload:0,cooldown:0,jumpQueued:false});d.previous={...d.player};
  this.source.projectiles=[];this.source.trails=[];this.source.pickups=[];this.source.events=[];this.source.world.cells.clear();
  this.source.world.planet.cache?.clear();this.source.world.planet.featureCache?.clear();this.prepare=null;this.source=null;this.destination=null;this.pending=null;this.state='consumed';this.commits++;return d;
 }
 dispose(){this.epoch++;this.pending=null;this.destination=null;this.prepare=null;this.source?.world.cells.clear();this.source?.world.planet.cache?.clear();this.source?.world.planet.featureCache?.clear();this.source=null;}
 get objective(){return this.state==='ready'?'Enter the time portal':this.state==='failure'?'Portal preparation failed · Retry or Cancel':this.state==='locked'?'Activate portal device · E / INTERACT':'Opening time portal…';}
}
export function portalVoxels(portal){
 const f=portal.frame;if(!f)return [];const growth=portal.state==='opening'?Math.max(.05,portal.opening):1;const out=[],add=(x,y,z,w,h,d,color)=>{ // All authored pieces align in portal local axes.
  y*=growth;h*=growth;const cx=x+w/2,cz=z+d/2,sy=Math.sin(f.yaw),cy=Math.cos(f.yaw);out.push({x:f.x+cx*cy+cz*sy-w/2,y:f.y+y,z:f.z-cx*sy+cz*cy-d/2,w,h,d,color,yaw:f.yaw,local:{x,y,z,w,h,d}});
 };
 add(-2,0,-.22,.35,3.6,.44,'#577b8e');add(1.65,0,-.22,.35,3.6,.44,'#577b8e');add(-2,3.2,-.22,4,.4,.44,'#94d7e5');add(-2.6,0,-1,.45,.8,.5,'#74e1e3');
 if(['opening','ready','traversing'].includes(portal.state)){
  // Bounded original street preview, not a live second renderer.
  add(-1.65,.02,.12,3.3,3.15,.06,'#5e8290');add(-1.65,.02,.04,3.3,.65,.06,'#515e63');
  for(const x of [-1.6,.65]){add(x,.67,0,.95,1.9,.06,'#92583e');for(let y=1;y<2.5;y+=.6)add(x+.15,y,-.05,.2,.3,.04,'#c4c79c');}
  add(-.4,.7,-.04,.65,.2,.06,'#76999c');add(-.35,.9,-.04,.5,.14,.06,'#a2bfc1');add(.3,.65,-.03,.04,1.8,.06,'#dbc482');
 }
 return out;
}
