import test from 'node:test';
import assert from 'node:assert/strict';
import {bootstrapPlanet} from './bootstrap-planet.mjs';
import {createPlanetVisualProfile} from './planet-profile.mjs';
import {cameraAt} from './entry-sequence.mjs';
const atlas={data:new Uint8Array(8*4).fill(128),width:4,height:2};
test('bootstrap is deterministic, bounded and responds to the shared sun',()=>{
 const p=createPlanetVisualProfile({},'airless'),c=cameraAt(0,p),a=bootstrapPlanet(p,atlas,c,1.4),again=bootstrapPlanet(p,atlas,c,1.4),night=bootstrapPlanet({...p,sun:p.sun.map(x=>-x)},atlas,c,1.4);
 assert.deepEqual(a,again);assert(a.width<=256&&a.height<=160);assert.notDeepEqual(a.pixels,night.pixels);assert(a.pixels.every(Number.isFinite));
});
test('airless bootstrap excludes scattering outside the solid globe',()=>{
 const p=createPlanetVisualProfile({},'airless'),c=cameraAt(0,p),a=bootstrapPlanet(p,atlas,c,1.4),b=bootstrapPlanet({...p,atmosphere:{...p.atmosphere,enabled:true}},atlas,c,1.4);
 // Vacuum corner has the same small space radiance in both profiles.
 assert.deepEqual(a.pixels.slice(0,4),b.pixels.slice(0,4));assert.notDeepEqual(a.pixels,b.pixels);
});
