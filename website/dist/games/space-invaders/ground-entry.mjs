import {createPlanetVisualProfile} from './planet-profile.mjs';
import {entryPayload} from './ground.mjs';
// The orbital view is a cartographic enlargement. Ground cells are authored
// one-metre gameplay cells; terrain indices, edits and landmarks never change.
export function groundEntryPayload(game,world,revision,profile,sky){
 const visualProfile=profile||createPlanetVisualProfile(world.config,'alien');
 if(visualProfile.seed!==world.config.seed||JSON.stringify(visualProfile.config)!==JSON.stringify(world.config))throw new Error('Ground entry profile does not match frozen terrain');
 const anchor=visualProfile.anchor;
 const transform=Object.freeze({orbitalMetresPerCell:visualProfile.metresPerBlock,groundMetresPerCell:1,anchor:Object.freeze({...anchor}),kind:'cartographic-cell-preserving'});
 return entryPayload(game.runId,world,revision,game.score,game.lives,{x:anchor.x,z:anchor.z,yaw:0},{profile:visualProfile,transform,sky:Object.freeze([...sky]),sun:visualProfile.sun,moons:visualProfile.moons});
}
