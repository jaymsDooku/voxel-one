import {test} from 'node:test';
import assert from 'node:assert/strict';
import {chapters,district,sweptPortalCrossing,immutableHandoff} from './alternate-earth.mjs';
const aperture={halfWidth:2,height:3}, capsule={radius:.3,capsuleHeight:1.8};
const cross=(a,b)=>sweptPortalCrossing(a,b,aperture,capsule);
test('explicit chapters end at Earth without wave parity',()=>{
 assert.equal(chapters[3].next,chapters[4].id); assert.equal(chapters[4].next,null);
 assert.equal(district.year,1964); assert.equal(district.factions.civilian.hostile,false);
 assert.ok(Object.isFrozen(district.objectives));
});
test('swept crossing handles both directions and low-frame sprint',()=>{
 assert.equal(cross({x:0,y:0,z:20},{x:0,y:0,z:-20}).t,.5);
 assert.equal(cross({x:0,y:0,z:-20},{x:0,y:0,z:20}).direction,1);
});
test('frame, side, plane standing and vertical misses do not transfer',()=>{
 for(const [a,b] of [
  [{x:1.7,y:0,z:1},{x:1.7,y:0,z:-1}],
  [{x:3,y:0,z:1},{x:3,y:0,z:-1}],
  [{x:0,y:0,z:0},{x:0,y:0,z:-1}],
  [{x:0,y:2,z:1},{x:0,y:2,z:-1}],
  [{x:0,y:0,z:1},{x:0,y:0,z:.1}],
 ]) assert.equal(cross(a,b),null);
});
test('handoff copies resources, freezes nested data and rejects same world',()=>{
 const source={runId:'run',missionId:'alien',worldId:'source',score:123,lives:2,health:37,weapon:'blaster',ammunition:9,difficulty:'normal',settings:{reducedMotion:true},transform:{yaw:1}};
 const destination={missionId:'earth',worldId:'dest',seed:1};
 const result=immutableHandoff(source,destination);source.health=100;
 assert.equal(result.source.health,37);assert.equal(result.source.ammunition,9);
 assert.ok(Object.isFrozen(result.source.settings));
 assert.throws(()=>immutableHandoff(source,{...destination,worldId:'source'}));
});
