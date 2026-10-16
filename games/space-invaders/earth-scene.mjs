import {GroundWorld,GroundMission} from './ground.mjs';
import {district} from './alternate-earth.mjs';
const prop=(out,x,y,z,w,h,d,color,name='detail')=>out.push({x,y,z,w,h,d,color,name});
export class EarthWorld extends GroundWorld {
 build(){
  this.details=[];this.signs=[];
  const wall=(x,z,w,h,d,c='#884e3d',name='brick warehouse')=>this.box(x,4,z,w,h,d,c,name);
  const detail=(...args)=>prop(this.details,...args);
  // Three linked north/south routes: tram street, warehouse lane, harbour quay.
  for(const [x,z,w,d] of [[-10,8,8,18],[5,8,10,16],[-10,34,9,15],[5,33,10,18],[22,10,9,19],[23,40,12,18]]){
   wall(x,z,w,6,.6);wall(x,z,.6,6,d);wall(x+w-.6,z,.6,6,d);wall(x,z+d-.6,w,6,.6);detail(x-.2,10,z-.2,w+.4,.4,d+.4,'#343940','warehouse roof');
   for(let a=x+1;a<x+w-1;a+=2){detail(a,7,z-.04,1,1.6,.08,'#a5b2a3','window');for(let y=4.8;y<10;y+=.8)detail(a, y,z-.07,1.8,.07,.08,'#ad7054','brick course');}
  }
  for(let z=-12;z<70;z+=12){wall(-1.7,z,.18,3.8,.18,'#394047','street lamp');detail(-2.2,7.7,z-.3,.8,.25,.7,'#f7d38a','lamp');}
  for(const x of [-.8,.8])detail(x,4.005,-16,.08,.03,88,'#a4a7a0','tram rail');
  for(let z=-16;z<72;z+=2)detail(-1,4,z,2,.025,.12,'#7d7467','rail sleeper');
  for(const [x,z,c]of [[-4,15,'#486d79'],[18,29,'#a69063'],[-4,47,'#5d6756']]){
   wall(x,z,2,1,4,c,'1960s sedan');detail(x+.15,5,z+1.1,1.7,.7,1.8,c,'sedan cabin');detail(x+.22,5.2,z+1,1.5,.4,.08,'#9fbbbf','windshield');
   for(const a of [-.15,1.85])for(const b of [.6,3])detail(x+a,4.2,z+b,.3,.6,.6,'#24262b','tyre');
  }
  // Checkpoint admits three routes; barriers and rubble provide opaque cover.
  for(const [x,z,w,d] of [[-4,30,2,1],[2.5,30,2,1],[17,35,2,2],[20,47,2,2],[-6,52,2,2]])wall(x,z,w,1.3,d,'#76766b','checkpoint cover');
  wall(4,29,.3,5,.3,'#41464b','checkpoint post');detail(4,8.2,29,3,.5,.2,'#d9cdb5','checkpoint sign');
  wall(18,61,4,2,3,'#4e5b5b','signal relay');wall(19.8,61,.2,7,.2,'#939a96','relay aerial');detail(18.3,5,60.9,.8,.6,.1,'#eabf5c','relay controls');
  // Sheltered arrival yard, resistance print shop, dockside seawall.
  wall(-8,-8,.6,3,7,'#875345');wall(-8,-8,5,3,.6,'#875345');
  wall(-6,4,3,1,1,'#715c4c','print shop counter');detail(-5.7,5,4,.7,.4,.4,'#383b38','radio');detail(-5.5,5.03,3.6,1,.03,.6,'#ddd0ac','newspaper');
  for(let z=-12;z<75;z+=8)wall(36,z,.5,1,8,'#626e75','harbour seawall');
  detail(36.5,4.03,-16,60,.02,92,'#446c78','harbour water');detail(32,4,64,1,9,1,'#9a8764','dock crane');detail(32,12,57,.6,.6,9,'#9a8764','crane arm');
  for(let i=0;i<3;i++)detail(-6+i*.3,5.4,3.9,.18,.4,.1,'#78a69b','resistance mark');
  detail(4.4,7.1,29.05,1.2,.9,.05,'#394239','occupation banner');
  this.signs=[{x:-5,y:6,z:4,text:'VALE PRINT · FREE PRESS'}, {x:4,y:8.9,z:29,text:'OCCUPATION CHECKPOINT · PAPERS REQUIRED'}, {x:19,y:7,z:61,text:'SIGNAL RELAY'}, {x:-5,y:5.6,z:3,text:'17 JUNE 1964 · UNDERGROUND EDITION'}];
 }
 constructor(payload){super(payload);this.revision=`${payload.runId}:earth:${district.seed}`;const config=this.planet.config;this.planet={config:{...config,seed:payload.seed,palette:{...config.palette,rock:['#62676a']}},random:()=>.5,surface:()=>({height:3,features:[],biome:'rock'})};this.cells.clear();}
 material(x,y,z){if(x< -32||x>=128||z< -32||z>=128)return 'rock';return Math.floor(y)<4?'rock':'air';}
}
export function humanVoxels(actor){
 const out=[],civil=actor.faction==='civilian',contact=actor.faction==='resistance',coat=civil?'#b1916f':contact?'#57857d':'#646c58',x=actor.x,y=actor.y,z=actor.z;
 const add=(dx,dy,dz,w,h,d,color)=>prop(out,x+dx,y+dy,z+dz,w,h,d,color);
 add(-.34,.65,-.22,.68,.85,.44,coat);add(-.21,1.5,-.2,.42,.42,.4,'#bc9276');
 add(-.31,1.91,-.25,.62,.12,.5,civil?'#593d33':contact?'#284f4d':'#303b32');
 for(const dx of [-.27,.08])add(dx,0,-.16,.2,.65,.3,'#343c41');
 for(const dx of [-.49,.32])add(dx,.8,-.18,.17,.6,.3,coat);
 if(!civil&&!contact){add(.25,1.03,.1,.16,.15,.85,'#343639');add(-.36,1.18,-.25,.13,.13,.03,'#b0aa8f');}
 if(civil)add(-.39,.1,-.25,.78,.65,.5,'#8c6a59');
 return out;
}
export class EarthMission extends GroundMission {
 constructor(source){
  const payload={runId:source.payload.runId,profile:Object.freeze({seed:String(district.seed)}),terrain:Object.freeze({revision:`${source.payload.runId}:earth:${district.seed}`,edits:Object.freeze([])}),score:source.score,lives:source.lives,seed:district.seed,landing:{x:.5,y:4,z:-3.5,yaw:0},visual:{sky:['#a6a598','#a6a598','#d5c9ae'],sun:[-.4,.7,.3],moons:[]}};
  const world=new EarthWorld(payload);
  super(payload,{pilots:6,plasmaDamage:12,plasmaSpeed:18},{world,spawn:world.safe(.5,-3.5),positions:[[1,24],[18,26],[-4,40],[18,45],[1,54],[25,58]]});
  this.id=`${source.payload.runId}:earth:${district.seed}`;this.chapter='alternate-earth-ground';this.year=1964;this.objective=0;this.dialogue=district.arrival;this.arrivalTime=9;
  this.civilians=[{x:-4,y:4,z:2,faction:'resistance',name:'Mara Vale'},{x:16,y:4,z:13,faction:'civilian',name:'Dock worker'},{x:-13,y:4,z:33,faction:'civilian',name:'Print courier'}];
  for(const c of this.civilians)this.world.boxes.push({x:c.x-.5,y:c.y,z:c.z-.4,w:1,h:2.1,d:.8,color:'#000000',invisible:true,name:'noncombatant'});
  for(const e of this.enemies){e.faction='occupation';e.health=68;e.role='rifle';}
 }
 defeat(e){if(this.status!=='active'||!this.enemies.includes(e)||e.state==='defeated')return;const last=this.remaining===1;super.defeat(e);if(last){this.status='active';this.completionCount=0;this.events=this.events.filter(e=>e!=='complete');}}
 interact(){
  this.dialogueAnchor={x:this.player.x,z:this.player.z};
  const p=this.player,near=(x,z,r=3.5)=>Math.hypot(p.x-x,p.z-z)<r;
  if(near(-5,3)){this.dialogue=district.newspaper;if(this.objective===0){this.objective=1;this.spawn=this.world.safe(-3,1);this.dialogue=district.contact.dialogue;}this.events.push('contact');return true;}
  for(const c of this.civilians)if(near(c.x,c.z,2)){this.dialogue=c.faction==='resistance'?district.contact.dialogue:'Civilian: We cannot fight. Please keep the street clear.';return true;}
  if(this.objective===2&&near(19,61)){this.objective=3;this.status='complete';this.projectiles=[];this.completionCount=1;this.events.push('complete');this.dialogue='Relay silenced. The harbour signal is safe. Chapter complete.';return true;}
  return false;
 }
 update(dt,input={}){super.update(dt,input);if(this.status!=='active')return;this.arrivalTime=Math.max(0,this.arrivalTime-Math.min(.1,dt));if((this.dialogueAnchor&&Math.hypot(this.player.x-this.dialogueAnchor.x,this.player.z-this.dialogueAnchor.z)>5)||(!this.arrivalTime&&this.dialogue===district.arrival)){this.dialogue='';this.dialogueAnchor=null;}if(this.objective===1&&this.player.z>36){this.objective=2;this.spawn=this.world.safe(18,39);this.dialogue='Checkpoint crossed. Disable the relay at the north end of the quay.';}if(input.interactPressed||(input.interact&&!this.interactHeld))this.interact();this.interactHeld=!!input.interact;}
 get objectiveText(){return ['Meet Mara Vale at Vale Print (west, 9 m)','Cross the occupation checkpoint (north)','Disable the signal relay (north quay)','Chapter complete'][this.objective];}
}
