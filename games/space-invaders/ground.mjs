import {Planet} from './terrain.mjs';
export const WEAPONS=Object.freeze({pulse:Object.freeze({magazine:12,damage:34,range:90,rate:.22,reload:1.25})});
export const RULES=Object.freeze({weapon:'pulse',...WEAPONS.pulse,health:100,speed:4.8,sprint:7.8,jump:7,gravity:20,step:.55,immunity:3,pilots:12,rifleHealth:68,outpostHealth:100,rifleSpeed:1.8,rushSpeed:3.2,rifleRange:24,rushRange:5,rifleRate:1.8,rushRate:1.1,plasmaDamage:20,plasmaSpeed:15,windup:.55,repath:.8,fallSafeSpeed:12,fallDamageScale:12});
export const PILOT_SHAPE=Object.freeze({radius:.45,height:2.2});
function missionRules(overrides){const weapon=WEAPONS[overrides.weapon||RULES.weapon];if(!weapon)throw new RangeError('Unknown ground weapon');const rules={...RULES,...weapon,...overrides};if(!Number.isInteger(rules.pilots)||rules.pilots<1||rules.pilots>24)throw new RangeError('Ground pilot count must be 1–24');if(!Number.isInteger(rules.magazine)||rules.magazine<1||rules.magazine>64)throw new RangeError('Ground magazine must be 1–64');for(const key of Object.keys(RULES)){if(['weapon','pilots','magazine'].includes(key))continue;const value=rules[key];if(!Number.isFinite(value)||value<=0||value>200)throw new RangeError(`Invalid ground rule: ${key}`);}if(rules.step>1||rules.range>128||rules.rate<.05||rules.plasmaSpeed>60||rules.gravity>40||rules.rifleSpeed>12||rules.rushSpeed>12||rules.speed>12||rules.sprint>16)throw new RangeError('Ground movement or weapon rule exceeds its bounded limit');return rules;}
const clamp=(v,a,b)=>Math.max(a,Math.min(b,v));
export const direction=(yaw,pitch=0)=>({x:Math.sin(yaw)*Math.cos(pitch),y:Math.sin(pitch),z:Math.cos(yaw)*Math.cos(pitch)});
export function weaponPose(aspect=1){return {right:.25*Math.min(1,aspect*aspect),down:.14,forward:.35};}
export function weaponOrigin(player,camera,aspect=1,drop=0){const pose=weaponPose(aspect),sy=Math.sin(player.yaw),cy=Math.cos(player.yaw),sp=Math.sin(player.pitch),cp=Math.cos(player.pitch),down=pose.down+drop;return {x:camera.x+cy*pose.right+sy*sp*down,y:camera.y-cp*down,z:camera.z-sy*pose.right+cy*sp*down};}
export function entryPayload(runId,planet,revision,score,lives,landing={x:0,z:0,yaw:0},visual={}){
 return Object.freeze({runId,profile:planet.config,seed:planet.config.seed,terrain:Object.freeze({revision,edits:Object.freeze([...planet.edits])}),landing:Object.freeze({...landing,y:planet.surface(landing.x,landing.z).height+1}),score,lives,visual:Object.freeze({sky:Object.freeze(['#102637','#5b859a','#9abcc2']),...visual})});
}
// One frozen CPU world owns rendering, movement, navigation and every shot.
// All materials, including crystals and fungi, are opaque solids in this mission.
export class GroundWorld{
 constructor(payload){this.planet=new Planet(payload.profile);for(const e of payload.terrain.edits)this.planet.edits.add(e);this.revision=payload.terrain.revision;this.cells=new Map();this.boxes=[];this.origin=payload.landing;this.bounds={minX:-32,maxX:128,minZ:-32,maxZ:128};this.build();}
 box(x,y,z,w,h,d,color,name='cover'){const b={x,y,z,w,h,d,color,name};this.boxes.push(b);return b;}
 build(){
  const p=this.planet,add=(x,z,w,h,d,c,n)=>this.box(x,p.surface(x,z).height+1,z,w,h,d,c,n);
  // The damaged hull, shuttle and open-door outpost use terrain coordinates.
  add(-6,-3,4,2,7,'#61778b','wreck');add(-7,-1,7,.6,3,'#92abc0','wreck wing');add(-5,-2,2,.8,2,'#fba16b','wreck engine');
  add(7,62,6,2,10,'#6a668d','shuttle');add(5,65,10,.6,3,'#a7a1c2','shuttle wing');
  add(64,102,14,4,1,'#597d87','outpost');add(64,95,1,4,7,'#597d87','outpost');add(77,95,1,4,8,'#597d87','outpost');add(64,95,4,4,1,'#597d87','outpost doorway');add(73,95,5,4,1,'#597d87','outpost doorway');add(64,95,14,.7,8,'#728fa0','outpost roof');
  // Raise roof, leaving a low service awning for jump/head clearance checks.
  this.boxes.at(-1).y+=4;add(66,90,5,.4,3,'#91afb9','low awning');this.boxes.at(-1).y+=2.15;
  for(const [x,z] of [[-3,10],[4,25],[-4,41],[3,55],[15,94],[35,99],[53,93],[82,99]])add(x,z,2,1.5+(p.random(x,z)*1.5),2,p.random(x,z)>.5?'#91d4de':'#756f83');
  // Alternating blocks make the solid arena perimeter visible.
  for(let n=-32;n<128;n+=8){const color=n%16===0?'#957754':'#c9ab67';add(n,-32,8,4,1,color,'boundary');add(n,127,8,4,1,color,'boundary');add(-32,n,1,4,8,color,'boundary');add(127,n,1,4,8,color,'boundary');}
 }
 material(x,y,z){x=Math.floor(x);y=Math.floor(y);z=Math.floor(z);if(y<0)return 'basalt';if(x< -32||x>=128||z< -32||z>=128)return 'rock';const k=`${x},${y},${z}`;if(this.cells.has(k))return this.cells.get(k);const m=this.planet.material(x,y,z);if(this.cells.size<250000)this.cells.set(k,m);return m;}
 solid(x,y,z){return this.material(x,y,z)!=='air';}
 blocked(x,y,z,r=.32,h=1.8){
  const f=this.portalFrame;if(f){const dx=x-f.x,dz=z-f.z,a=dx*Math.cos(f.yaw)-dz*Math.sin(f.yaw),b=dx*Math.sin(f.yaw)+dz*Math.cos(f.yaw);if(Math.abs(b)<r+.22&&y+h>f.y&&y<f.y+3.6&&((Math.abs(a)>1.65-r&&Math.abs(a)<2+r)||(y+h>f.y+3.2&&Math.abs(a)<2+r)))return true;}
  for(const b of this.boxes)if(x+r>b.x&&x-r<b.x+b.w&&y+h>b.y&&y<b.y+b.h&&z+r>b.z&&z-r<b.z+b.d)return true;
  for(let a=Math.floor(x-r);a<=Math.floor(x+r);a++)for(let c=Math.floor(z-r);c<=Math.floor(z+r);c++)for(let b=Math.floor(y+.001);b<=Math.floor(y+h-.001);b++)if(this.solid(a,b,c))return true;
  return false;
 }
 safe(x,z,shape=PILOT_SHAPE){const y=this.planet.surface(Math.floor(x),Math.floor(z)).height+1;if(this.blocked(x,y,z,shape.radius,shape.height))return null;for(const dx of [-shape.radius,shape.radius])for(const dz of [-shape.radius,shape.radius]){const material=this.material(x+dx,y-.01,z+dz);if(material==='air'||['acid','vent','radiation-crystal'].includes(material))return null;}return {x,y,z};}
 traversable(from,to,step=.55,shape=PILOT_SHAPE){let previous=from;const parts=Math.max(1,Math.ceil(Math.hypot(to.x-from.x,to.z-from.z)/.2));for(let i=1;i<=parts;i++){const t=i/parts,safe=this.safe(from.x+(to.x-from.x)*t,from.z+(to.z-from.z)*t,shape);if(!safe||Math.abs(safe.y-previous.y)>step||this.blocked(safe.x,Math.max(safe.y,previous.y),safe.z,shape.radius,shape.height))return false;previous=safe;}return true;}
 ray(from,to){const dx=to.x-from.x,dy=to.y-from.y,dz=to.z-from.z,length=Math.hypot(dx,dy,dz);let best=1;
  // Exact slab intersection against structures and visited voxel cells.
  const intersect=(x,y,z,w=1,h=1,d=1)=>{let lo=0,hi=best;for(const [o,v,min,max] of [[from.x,dx,x,x+w],[from.y,dy,y,y+h],[from.z,dz,z,z+d]]){if(Math.abs(v)<1e-9){if(o<min||o>max)return;}else{let a=(min-o)/v,b=(max-o)/v;if(a>b)[a,b]=[b,a];lo=Math.max(lo,a);hi=Math.min(hi,b);if(lo>hi)return;}}if(lo>=0&&lo<=best)best=lo;};
  for(const b of this.boxes)intersect(b.x,b.y,b.z,b.w,b.h,b.d);
  // DDA visits each intersected cell; no thin wall can be skipped.
  let x=Math.floor(from.x),y=Math.floor(from.y),z=Math.floor(from.z),t=0;
  const axis=(o,v,c)=>v===0?{step:0,next:Infinity,delta:Infinity}:{step:Math.sign(v),next:((v>0?c+1:c)-o)/v,delta:Math.abs(1/v)};
  const a=axis(from.x,dx,x),b=axis(from.y,dy,y),c=axis(from.z,dz,z);
  for(let i=0;i<1024&&t<=best;i++){if(this.solid(x,y,z))intersect(x,y,z);if(a.next<=b.next&&a.next<=c.next){t=a.next;a.next+=a.delta;x+=a.step;}else if(b.next<=c.next){t=b.next;b.next+=b.delta;y+=b.step;}else{t=c.next;c.next+=c.delta;z+=c.step;}if(!Number.isFinite(t))break;}
  return {t:best,distance:length*best,point:{x:from.x+dx*best,y:from.y+dy*best,z:from.z+dz*best},blocked:best<1};
 }
 path(from,to,budget=512,step=.55){const snap=p=>[Math.round(p.x/2)*2,Math.round(p.z/2)*2],start=snap(from),goal=snap(to),key=(x,z)=>`${x},${z}`,q=[start],seen=new Map([[key(...start),null]]);let end=null,visited=0;if(!this.safe(goal[0]+.5,goal[1]+.5)){let alternate=null;for(const [dx,dz] of [[-2,0],[2,0],[0,-2],[0,2],[-4,0],[4,0],[0,-4],[0,4]])if(this.safe(goal[0]+dx+.5,goal[1]+dz+.5)){alternate=[goal[0]+dx,goal[1]+dz];break;}if(!alternate)return {route:[],visited:0};goal[0]=alternate[0];goal[1]=alternate[1];}
  while(q.length&&visited++<budget){q.sort((a,b)=>Math.hypot(a[0]-goal[0],a[1]-goal[1])-Math.hypot(b[0]-goal[0],b[1]-goal[1]));const [x,z]=q.shift();if(Math.hypot(x-goal[0],z-goal[1])<1.5){end=[x,z];break;}const s=this.safe(x+.5,z+.5);if(!s)continue;for(const [dx,dz] of [[2,0],[-2,0],[0,2],[0,-2]]){const a=x+dx,c=z+dz,k=key(a,c);if(a< -30||a>126||c< -30||c>126||seen.has(k))continue;const next=this.safe(a+.5,c+.5);if(!next||!this.traversable(s,next,step))continue;seen.set(k,[x,z]);q.push([a,c]);}}
  const route=[];while(end&&seen.get(key(...end))){const s=this.safe(end[0]+.5,end[1]+.5);route.unshift(s);end=seen.get(key(...end));}return {route,visited:Math.min(visited,budget)};
 }
}
export class GroundMission{
 constructor(payload,rules={},options={}){this.payload=payload;this.id=`${payload.runId}:ground`;this.rules=missionRules(rules);this.viewAspect=1;this.world=options.world||new GroundWorld(payload);this.score=payload.score;this.lives=payload.lives;this.status='active';this.elapsed=0;this.accumulator=0;this.tick=0;this.completionCount=0;this.lifeEpoch=0;this.projectiles=[];this.trails=[];this.pickups=[];this.events=[];this.navVisits=0;this.spawn= options.spawn||this.world.safe(payload.landing.x+.5,payload.landing.z+.5);if(!this.spawn)throw new Error('Landing anchor has no safe standing clearance');this.player={...this.spawn,yaw:payload.landing.yaw||0,pitch:0,vy:0,health:this.rules.health,ammo:this.rules.magazine,reload:0,cooldown:0,immune:this.rules.immunity,radius:.32,height:1.8,grounded:true,damage:0,hit:0};this.previous={...this.player};this.enemies=[];
  const positions=options.positions||[[-4,22],[3,30],[-3,35],[3,43],[-3,64],[3,72],[-3,80],[4,88],[45,94],[54,98],[70,98],[84,94]];
  for(let i=0;i<this.rules.pilots;i++){const [x,z]=positions[i%positions.length];let s=null;for(const [dx,dz] of [[0,0],[-2,0],[2,0],[0,-2],[0,2],[-4,0],[4,0],[0,-4],[0,4],[-2,-2],[2,2]]){const candidate=this.world.safe(x+.5+dx,z+.5+dz);if(candidate&&this.enemies.every(e=>Math.hypot(e.x-candidate.x,e.z-candidate.z)>1.5)){s=candidate;break;}}if(!s)throw new Error(`No safe pilot spawn ${i}`);const encounter=Math.floor((i%positions.length)/4);this.enemies.push({...s,id:i,encounter,radius:PILOT_SHAPE.radius,height:PILOT_SHAPE.height,vy:0,grounded:true,home:{...s},role:i%3===2?'rush':'rifle',health:encounter===2?this.rules.outpostHealth:this.rules.rifleHealth,state:'patrol',yaw:Math.PI,clock:.8+i*.1,windup:0,path:[],repath:0,age:0,fade:1,stuck:0});}
 }
 get remaining(){return this.enemies.filter(e=>e.state!=='defeated').length;}
 look(dx,dy){if(!['active','complete'].includes(this.status))return;this.player.yaw+=dx;this.player.pitch=clamp(this.player.pitch+dy,-1.45,1.45);}
 reload(){const p=this.player;if(['active','complete'].includes(this.status)&&!p.reload&&p.ammo<this.rules.magazine)p.reload=this.rules.reload;}
 damage(amount){const p=this.player;if(this.status!=='active'||p.immune>0)return false;p.health=Math.max(0,p.health-amount);p.damage=.5;if(p.health>0)return true;this.lives--;this.lifeEpoch++;this.projectiles=[];this.events.push('life');if(this.lives<=0){this.status='over';return true;}Object.assign(p,this.spawn,{health:this.rules.health,ammo:this.rules.magazine,vy:0,reload:0,immune:this.rules.immunity,pitch:0,jumpQueued:false,grounded:true,cooldown:0});this.previous={...p};for(const e of this.enemies)if(e.state!=='defeated'&&Math.hypot(e.x-p.x,e.z-p.z)<12){Object.assign(e,e.home);e.path=[];e.state='patrol';e.windup=0;e.lastSeen=null;e.coverTarget=null;e.coverTime=0;e.detour=null;e.vy=0;e.grounded=true;e.stuck=0;e.repath=this.rules.repath;}return true;}
 defeat(e){if(!this.enemies.includes(e)||e.state==='defeated'||this.status!=='active')return;e.health=0;e.state='defeated';e.fade=1;this.score+=e.role==='rush'?75:50;this.events.push('kill');if(this.pickups.length<16)this.pickups.push({x:e.x,y:e.y+.3,z:e.z,kind:e.id%4===0?'health':'energy'});if(!this.remaining){this.status='complete';this.completionCount++;this.projectiles=[];this.events.push('complete');}}
 fire(){const p=this.player;if(!['active','complete'].includes(this.status)||p.cooldown>0||p.reload||p.ammo<=0)return false;p.ammo--;p.cooldown=this.rules.rate;this.events.push('fire');for(const e of this.enemies)if(e.state==='patrol'&&Math.hypot(e.x-p.x,e.z-p.z)<20){e.lastSeen={x:p.x,y:p.y,z:p.z,seenAt:this.elapsed};e.state='investigate';}const dir=direction(p.yaw,p.pitch),from={x:p.x,y:p.y+1.6,z:p.z},to={x:from.x+dir.x*this.rules.range,y:from.y+dir.y*this.rules.range,z:from.z+dir.z*this.rules.range};let hit=this.world.ray(from,to),target=null;
  for(const e of this.enemies){if(e.state==='defeated')continue;let near=0,far=this.rules.range;for(const [axis,min,max] of [['x',e.x-.55,e.x+.55],['y',e.y,e.y+e.height],['z',e.z-.55,e.z+.55]]){const v=dir[axis],o=from[axis];if(Math.abs(v)<1e-9){if(o<min||o>max){near=Infinity;break;}}else{let a=(min-o)/v,b=(max-o)/v;if(a>b)[a,b]=[b,a];near=Math.max(near,a);far=Math.min(far,b);if(near>far){near=Infinity;break;}}}if(near<=hit.distance&&near<=far){hit={distance:near,point:{x:from.x+dir.x*near,y:from.y+dir.y*near,z:from.z+dir.z*near}};target=e;}}
  const pose=weaponPose(this.viewAspect),origin=weaponOrigin(p,from,this.viewAspect),muzzle={x:origin.x+dir.x*pose.forward,y:origin.y+dir.y*pose.forward,z:origin.z+dir.z*pose.forward},obstruction=this.world.ray(muzzle,hit.point);if(obstruction.blocked){target=null;hit.point=obstruction.point;}
  this.trails.push({from:muzzle,to:hit.point,life:.12});if(this.trails.length>12)this.trails.shift();if(target){target.health-=this.rules.damage;target.hit=.2;p.hit=.18;target.state='pursue';if(target.health<=0)this.defeat(target);}return true;
 }
 move(body,dx,dz,step=true){const w=this.world,radius=body.radius||.32,height=body.height||1.8;for(const [axis,delta] of [['x',dx],['z',dz]]){const parts=Math.max(1,Math.ceil(Math.abs(delta)/.15));for(let i=0;i<parts;i++){const n=body[axis]+delta/parts,x=axis==='x'?n:body.x,z=axis==='z'?n:body.z;
   // Pilots cannot move closer into another pilot's body; escaping overlap is allowed.
   if(body!==this.player&&this.enemies.some(e=>e!==body&&e.state!=='defeated'&&Math.abs(e.y-body.y)<2&&Math.hypot(e.x-x,e.z-z)<.9&&Math.hypot(e.x-x,e.z-z)<Math.hypot(e.x-body.x,e.z-body.z)-1e-6))break;
   if(!w.blocked(x,body.y,z,radius,height)){body[axis]=n;}else if(step&&body.grounded&&!w.blocked(x,body.y+this.rules.step,z,radius,height)&&!w.blocked(body.x,body.y+this.rules.step,body.z,radius,height)){body.y+=this.rules.step;body[axis]=n;}else break;}}}
 vertical(body,dt){const radius=body.radius||.32,height=body.height||1.8;body.vy=(body.vy||0)-this.rules.gravity*dt;const dy=body.vy*dt,parts=Math.max(1,Math.ceil(Math.abs(dy)/.1));body.grounded=false;let impact=0;for(let i=0;i<parts;i++){if(this.world.blocked(body.x,body.y+dy/parts,body.z,radius,height)){body.grounded=dy<0;if(body.grounded)impact=-body.vy;body.vy=0;break;}body.y+=dy/parts;}return impact;}
 roam(dt,input){
  const t=clamp(dt,0,.1),p=this.player;
  this.previous={...p};this.elapsed+=t;
  for(const e of this.enemies)if(e.state==='defeated')e.fade=Math.max(0,e.fade-t);
  for(const key of ['cooldown','hit','damage'])p[key]=Math.max(0,p[key]-t);
  this.trails=this.trails.filter(trail=>(trail.life-=t)>0);
  if(p.reload){p.reload=Math.max(0,p.reload-t);if(!p.reload)p.ammo=this.rules.magazine;}
  if(input.reload)this.reload();if(input.fire)this.fire();
  const forward=input.forward||0,side=input.side||0,n=Math.max(1,Math.hypot(forward,side)),speed=input.sprint?this.rules.sprint:this.rules.speed;
  p.yaw+=(input.turn||0)*t*1.8;p.pitch=clamp(p.pitch+(input.pitchTurn||0)*t*1.8,-1.45,1.45);
  this.move(p,(Math.sin(p.yaw)*forward+Math.cos(p.yaw)*side)/n*speed*t,(Math.cos(p.yaw)*forward-Math.sin(p.yaw)*side)/n*speed*t);
  if((input.jump||p.jumpQueued)&&p.grounded){p.jumpQueued=false;p.vy=this.rules.jump;p.grounded=false;}
  this.vertical(p,t);this.accumulator=0;
 }
 update(dt,input={}){if(this.status==='complete'){this.roam(dt,input);return;}if(this.status!=='active')return;this.accumulator+=clamp(dt,0,.1);let count=0;while(this.accumulator>=1/60&&count++<6){this.previous={...this.player};this.step(1/60,input);this.accumulator-=1/60;if(this.status!=='active')break;}return this.accumulator*60;}
 step(dt,input){this.tick++;this.elapsed+=dt;const p=this.player,w=this.world;for(const k of ['cooldown','immune','damage','hit'])p[k]=Math.max(0,p[k]-dt);if(p.reload){p.reload=Math.max(0,p.reload-dt);if(!p.reload)p.ammo=this.rules.magazine;}if(input.reload)this.reload();if(input.fire)this.fire();
  const forward=input.forward||0,side=input.side||0,n=Math.max(1,Math.hypot(forward,side)),speed=input.sprint?this.rules.sprint:this.rules.speed;p.yaw+=(input.turn||0)*dt*1.8;p.pitch=clamp(p.pitch+(input.pitchTurn||0)*dt*1.8,-1.45,1.45);
  this.move(p,(Math.sin(p.yaw)*forward+Math.cos(p.yaw)*side)/n*speed*dt,(Math.cos(p.yaw)*forward-Math.sin(p.yaw)*side)/n*speed*dt);
  if((input.jump||p.jumpQueued)&&p.grounded){p.jumpQueued=false;p.vy=this.rules.jump;p.grounded=false;}const impact=this.vertical(p,dt);if(impact>this.rules.fallSafeSpeed)this.damage((impact-this.rules.fallSafeSpeed)*this.rules.fallDamageScale);
  if(p.y<1||['acid','vent','radiation-crystal'].includes(w.material(p.x,p.y-.08,p.z)))this.damage(100);
  this.trails=this.trails.filter(t=>(t.life-=dt)>0);this.pickups=this.pickups.filter(e=>{if(Math.hypot(e.x-p.x,e.z-p.z)<1.3){if(e.kind==='health')p.health=Math.min(this.rules.health,p.health+30);else p.ammo=Math.min(this.rules.magazine,p.ammo+4);this.events.push('pickup');return false;}return true;});
  if(this.status!=='active')return;
  let navBudget=1;
  for(const e of this.enemies){e.previous={x:e.x,y:e.y,z:e.z,yaw:e.yaw};e.age+=dt;e.hit=Math.max(0,(e.hit||0)-dt);if(e.state==='defeated'){e.fade=Math.max(0,e.fade-dt);continue;}e.clock-=dt;e.repath-=dt;if(e.detour&&(e.detour.ttl-=dt)<=0)e.detour=null;e.coverTime=Math.max(0,(e.coverTime||0)-dt);const distance=Math.hypot(e.x-p.x,e.z-p.z),eye={x:e.x,y:e.y+1.5,z:e.z},playerEye={x:p.x,y:p.y+1.1,z:p.z},angle=Math.atan2(p.x-e.x,p.z-e.z),cone=Math.cos(angle-e.yaw)>.15,visible=distance<38&&(!w.ray(eye,playerEye).blocked)&&cone;
   if(p.immune>0){e.windup=0;e.state='patrol';continue;}
   if(e.coverTime>0&&e.coverTarget){e.state='reposition';}else if(visible){e.yaw=angle;e.state=distance<(e.role==='rush'?this.rules.rushRange:this.rules.rifleRange)?'attack':'pursue';e.lastSeen={x:p.x,y:p.y,z:p.z,seenAt:this.elapsed};}else if(e.lastSeen&&this.elapsed-(e.lastSeen.seenAt||0)<5){e.state='investigate';}else{e.lastSeen=null;e.state='patrol';}
   if(e.state==='attack'){if(e.clock<=0&&!e.windup)e.windup=this.rules.windup;if(e.windup){e.windup-=dt;if(e.windup<=0){if(visible&&this.projectiles.length<32){const len=Math.hypot(playerEye.x-eye.x,playerEye.y-eye.y,playerEye.z-eye.z);this.projectiles.push({...eye,vx:(playerEye.x-eye.x)/len*this.rules.plasmaSpeed,vy:(playerEye.y-eye.y)/len*this.rules.plasmaSpeed,vz:(playerEye.z-eye.z)/len*this.rules.plasmaSpeed,life:4,damage:this.rules.plasmaDamage});this.events.push('plasma');}e.clock=e.role==='rush'?this.rules.rushRate:this.rules.rifleRate;e.windup=0;e.state='reposition';e.coverTime=1.3;e.coverTarget=null;for(const b of w.boxes){if(b.name==='boundary'||Math.hypot(b.x-e.x,b.z-e.z)>8)continue;const dx=b.x+b.w/2-p.x,dz=b.z+b.d/2-p.z,len=Math.max(1,Math.hypot(dx,dz)),candidate=w.safe(b.x+b.w/2+dx/len*(b.w/2+1),b.z+b.d/2+dz/len*(b.d/2+1));if(candidate){e.coverTarget=candidate;break;}}if(!e.coverTarget)e.coverTarget=w.safe(e.x+Math.cos(e.yaw)*2,e.z-Math.sin(e.yaw)*2);e.repath=0;}}}
   else{e.windup=0;const target=e.detour||(e.coverTime>0&&e.coverTarget)||e.lastSeen||{x:e.home.x+Math.sin(e.age*.2+e.id)*2,y:e.home.y,z:e.home.z};if(e.repath<=0&&navBudget){const path=w.path(e,target,512,this.rules.step);e.path=path.route;this.navVisits=path.visited;e.repath=this.rules.repath;navBudget--;}const next=e.path[0];if(next){let dx=next.x-e.x,dz=next.z-e.z;for(const other of this.enemies){if(other===e||other.state==='defeated')continue;const distance=Math.hypot(other.x-e.x,other.z-e.z);if(distance>0&&distance<1.4){dx+=(e.x-other.x)/distance*.7;dz+=(e.z-other.z)/distance*.7;}}const len=Math.hypot(dx,dz);if(len<.4)e.path.shift();else{const old={x:e.x,z:e.z};e.grounded=true;this.move(e,dx/len*(e.role==='rush'?this.rules.rushSpeed:this.rules.rifleSpeed)*dt,dz/len*(e.role==='rush'?this.rules.rushSpeed:this.rules.rifleSpeed)*dt);e.yaw=Math.atan2(dx,dz);if(Math.hypot(old.x-e.x,old.z-e.z)<.001)e.stuck+=dt;else e.stuck=0;if(e.stuck>1){e.path=[];e.repath=.2;e.stuck=0;const side=e.id%2?1:-1,candidate=w.safe(e.x+Math.cos(e.yaw)*side*2,e.z-Math.sin(e.yaw)*side*2);if(candidate&&w.traversable(e,candidate,this.rules.step))e.detour={...candidate,ttl:1.5};}}}if(e.detour&&Math.hypot(e.x-e.detour.x,e.z-e.detour.z)<.5)e.detour=null;if(e.lastSeen&&Math.hypot(e.x-e.lastSeen.x,e.z-e.lastSeen.z)<2)e.lastSeen=null;}
   this.vertical(e,dt);
  }
  const epoch=this.lifeEpoch;
  this.projectiles=this.projectiles.filter(s=>{if(this.status!=='active')return false;const to={x:s.x+s.vx*dt,y:s.y+s.vy*dt,z:s.z+s.vz*dt};const hit=w.ray(s,to);const dx=to.x-s.x,dy=to.y-s.y,dz=to.z-s.z,ox=s.x-p.x,oy=s.y-(p.y+1),oz=s.z-p.z,a=dx*dx+dy*dy+dz*dz,b=2*(ox*dx+oy*dy+oz*dz),c=ox*ox+oy*oy+oz*oz-.65*.65,discriminant=b*b-4*a*c,t=c<=0?0:a>0&&discriminant>=0?(-b-Math.sqrt(discriminant))/(2*a):Infinity;if(t>=0&&t<=1&&t<=hit.t){this.damage(s.damage||this.rules.plasmaDamage);return false;}if(hit.blocked)return false;Object.assign(s,to);return(s.life-=dt)>0;});if(epoch!==this.lifeEpoch||this.status!=='active')this.projectiles=[];
 }
}
