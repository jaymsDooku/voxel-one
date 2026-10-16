import {test} from 'node:test';
import assert from 'node:assert/strict';
import {Game} from './game.mjs';
import {Planet,SEEDS} from './terrain.mjs';
import {entryPayload,GroundMission} from './ground.mjs';
import {PortalCoordinator} from './portal.mjs';
import {EarthMission} from './earth-scene.mjs';
function source(seed){return new GroundMission(entryPayload(12,new Planet(seed?{seed}:{}),8,170,2));}
function ready(p){p.begin();for(let i=0;i<15;i++)p.update(.1,p.source.player,p.source.player);assert.equal(p.state,'ready',p.error);}
function walk(p,x=0,dir=1){const f=p.frame,n={x:Math.sin(f.yaw),z:Math.cos(f.yaw)},r={x:Math.cos(f.yaw),z:-Math.sin(f.yaw)},point=d=>({...p.source.player,x:f.x+n.x*d+r.x*x,y:f.y,z:f.z+n.z*d+r.z*x});p.update(.1,point(-3*dir),point(3*dir));}
test('last real shot awards once; no auto-transfer or Continue; source remains explorable',()=>{
 const g=new Game();g.restart();g.wave=3;g.newWave();const s=g.ground;for(const e of s.enemies.slice(1))s.defeat(e);const e=s.enemies[0];Object.assign(e,{x:.5,y:13,z:7,health:34});Object.assign(s.player,{x:.5,y:13,z:.5,yaw:0,pitch:0});assert.equal(s.fire(),true);const score=s.score;s.defeat(e);assert.equal(s.score,score);assert.equal(s.completionCount,1);g.update(.1);assert.equal(g.state,'playing');assert.equal(g.ground,s);assert.equal(s.projectiles.length,0);for(let i=0;i<14;i++)g.update(.1);const before=s.player.x;g.update(.1,{side:1});assert(s.player.x>before);assert.equal(g.portal.state,'ready');assert.equal(g.wave,3);
});
test('portal frame is solid, side/standing misses never transfer; sprint crossing commits once with exact resources/orientation',()=>{
 const s=source();Object.assign(s.player,{health:37,ammo:5,yaw:.4,pitch:.15});s.score=900;s.elapsed=123.25;s.tick=7400;const p=new PortalCoordinator(s);ready(p);assert(s.world.blocked(p.frame.x+Math.cos(p.frame.yaw)*1.8,p.frame.y,p.frame.z-Math.sin(p.frame.yaw)*1.8));walk(p,1.65);assert.equal(p.state,'ready');p.update(.1,s.player,s.player);assert.equal(p.state,'ready');const yaw=s.player.yaw;walk(p);assert.equal(p.state,'traversing');walk(p);for(let i=0;i<5;i++)p.update(.1,s.player,s.player);const d=p.commit();assert(d);assert.equal(d.score,900);assert.equal(d.elapsed,123.25);assert.equal(d.tick,7400);assert.equal(d.lives,2);assert.equal(d.player.health,37);assert.equal(d.player.ammo,5);assert.equal(d.player.yaw,yaw-p.frame.yaw);assert.equal(d.player.pitch,.15);assert.equal(d.payload.runId,s.payload.runId);assert.notEqual(d.id,s.id);assert.equal(p.commit(),null);assert.equal(p.commits,1);assert.equal(s.projectiles.length,0);assert.equal(p.source,null);assert.equal(p.prepare,null);assert.equal(s.world.planet.cache.size,0);assert(Object.isFrozen(p.handoff.source.settings));
});
test('preparation failure/retry/cancel retain source and fence stale jobs; deadline bounded by gameplay time',async()=>{
 const s=source(),before={...s.player};let resolve;const p=new PortalCoordinator(s,()=>new Promise(r=>resolve=r));p.begin();for(let i=0;i<82;i++)p.update(.1,s.player,s.player);assert.equal(p.state,'failure');resolve(new EarthMission(s));await Promise.resolve();assert.equal(p.destination,null);assert.deepEqual(s.player,before);p.cancel();assert.equal(p.state,'locked');p.prepare=()=>{throw Error('synthetic failure');};p.begin();assert.equal(p.state,'failure');p.prepare=()=>new EarthMission(s);ready(p);assert.equal(p.state,'ready');
});
test('unsafe spawn fails without replacing source',()=>{const s=source(),p=new PortalCoordinator(s,()=>{const d=new EarthMission(s);d.player.x=-8;d.player.z=-8;return d;});p.begin();assert.equal(p.state,'failure');assert.equal(p.source,s);});
test('all built-in terrain seeds have a clear locally reachable portal',()=>{for(const seed of SEEDS)for(const [x,z]of [[.5,.5],[.5,66.5],[70.5,98.5]]){const s=source(seed);Object.assign(s.player,s.world.safe(x,z));const p=new PortalCoordinator(s);ready(p);const f=p.frame;assert(s.world.safe(f.x,f.z));const entry={x:f.x-Math.sin(f.yaw),y:f.y,z:f.z-Math.cos(f.yaw)};assert(s.world.traversable(s.player,entry)||s.world.path(s.player,entry,2048).route.length);}});
test('Earth is playable: three connected routes, opaque cover, civilians cannot be scored, objectives and checkpoint death',()=>{
 const s=source(),g=new EarthMission(s);assert.equal(g.payload.seed,19640617);assert.equal(g.world.planet.config.seed,19640617);assert.deepEqual(g.payload.terrain.edits,[]);for(const x of [0,18,-13]){const from=g.world.safe(x,0),to=g.world.safe(x,60);assert(from&&to);assert(g.world.path(from,to,2048).route.length>0);}
 assert(g.world.ray({x:8,y:5,z:4},{x:8,y:5,z:12}).blocked);const score=g.score;g.defeat(g.civilians[0]);assert.equal(g.score,score);
 Object.assign(g.player,g.world.safe(-5,1));assert(g.interact());assert.equal(g.objective,1);Object.assign(g.player,g.world.safe(18,39));g.update(.02);assert.equal(g.objective,2);g.player.immune=0;g.damage(100);assert.equal(g.lives,1);assert(g.player.z>30);Object.assign(g.player,g.world.safe(19,59));assert(g.interact());assert.equal(g.status,'complete');assert.equal(g.completionCount,1);
});
test('pause freezes source opening/transition and restart drops preparation',()=>{const g=new Game();g.restart();g.wave=3;g.newWave();for(const e of g.ground.enemies)g.ground.defeat(e);g.update(.1);g.pause();const elapsed=g.portal.elapsed;g.update(5);assert.equal(g.portal.elapsed,elapsed);const p=g.portal;g.restart();assert.equal(g.wave,1);assert.equal(p.source,null);assert.equal(g.portal,null);});

test('quick separate interaction presses survive a frame without a release sample',()=>{
 const g=new EarthMission(source());Object.assign(g.player,g.world.safe(-3,1));g.update(.02,{interact:true,interactPressed:true});assert.equal(g.objective,1);g.update(.02,{interact:true,interactPressed:true});assert.match(g.dialogue,/underground edition/);
});

test('secured source retains jump, looking, fire and reload controls',()=>{const s=source();for(const e of s.enemies)s.defeat(e);const y=s.player.y;s.update(.05,{jump:true});assert(s.player.y>y);s.look(.2,.1);assert.equal(s.player.yaw,.2);s.player.ammo=1;s.update(.05,{fire:true});assert.equal(s.player.ammo,0);s.update(.05,{reload:true});assert(s.player.reload>0);assert.equal(s.status,'complete');});
