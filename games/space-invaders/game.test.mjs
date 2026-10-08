import test from 'node:test';
import assert from 'node:assert/strict';
import {Game,models} from './game.mjs';
test('models contain voxel cells and three distinct aliens',()=>{assert.equal(new Set([models.squid.join(),models.crab.join(),models.octopus.join()]).size,3);for(const rows of Object.values(models))assert(rows.every(r=>r.length===rows[0].length&&/^[01]+$/.test(r)));});
test('movement clamps, fire cooldown, pause and restart',()=>{const g=new Game();g.restart();for(let i=0;i<100;i++)g.update(.05,{axis:-1,fire:true});assert.equal(g.player,20);assert(g.shots.length<20);g.pause();const x=g.player;g.update(.05,{axis:1});assert.equal(g.player,x);g.restart();assert.equal(g.lives,3);assert.equal(g.score,0);});
test('alien kill scores once; last kill advances and resets',()=>{const g=new Game();g.restart();g.aliens=[{x:240,y:200,points:30}];g.shots=[{x:240,y:205},{x:240,y:205}];g.update(.01);assert.equal(g.score,30);assert.equal(g.wave,2);assert.equal(g.aliens.length,16);assert.equal(g.shots.length,0);assert.equal(g.enemyShots.length,0);});
test('both sides erode shields',()=>{for(const enemy of [false,true]){const g=new Game();g.restart();g.shields=[{x:100,y:440},{x:105,y:440}];g[enemy?'enemyShots':'shots']=[{x:100,y:enemy?435:445}];g.update(.01);assert.equal(g.shields.length,0);assert.equal(g[enemy?'enemyShots':'shots'].length,0);}});
test('life loss, immunity, game over and invasion',()=>{const g=new Game();g.restart();g.enemyShots=[{x:240,y:515}];g.update(.01);assert.equal(g.lives,2);g.enemyShots=[{x:240,y:515}];g.update(.01);assert.equal(g.lives,2);g.invulnerable=0;g.lives=1;g.enemyShots=[{x:240,y:515}];g.update(.01);assert.equal(g.state,'over');g.restart();g.aliens[0].y=490;g.update(.01);assert.equal(g.state,'over');});
test('fleet reverses and descends; bottom shooters; saucer bonus',()=>{const g=new Game(()=>0);g.restart();g.aliens=[{x:460,y:100,points:10},{x:460,y:150,points:10}];g.enemyClock=0;g.update(.05);assert.equal(g.direction,-1);assert.equal(g.aliens[0].y,115);assert(g.enemyShots[0].y>165);g.saucer={x:240,y:38};g.shots=[{x:240,y:42}];g.update(.01);assert.equal(g.score,100);assert.equal(g.saucer,null);});
test('large background delta bounded and offscreen projectiles recycled',()=>{const g=new Game();g.restart();g.shots=[{x:10,y:1}];g.enemyShots=[{x:10,y:569}];g.update(100,{axis:1});assert(g.player<=252);assert.equal(g.shots.length,0);assert.equal(g.enemyShots.length,0);});

test('slow frame projectile sweeps across alien and shield',()=>{const g=new Game();g.restart();g.aliens=[{x:240,y:200,points:30}];g.shots=[{x:240,y:205}];g.update(.05);assert.equal(g.wave,2);assert.equal(g.score,30);g.shields=[{x:100,y:440}];g.shots=[{x:100,y:450}];g.update(.05);assert.equal(g.shields.length,0);});

test('levels alternate classic and cockpit; restart restores classic',()=>{const g=new Game();g.restart();for(let wave=1;wave<=4;wave++){assert.equal(g.wave,wave);assert.equal(g.perspective,wave%2?'classic':'cockpit');g.aliens=[];g.update(.01);while(g.scene)g.update(.05);}g.restart();assert.equal(g.wave,1);assert.equal(g.perspective,'classic');});

test('fighter flight has momentum, diagonal speed limit, bounds and aimed attacks',()=>{
 const g=new Game(()=>0);g.restart();g.wave=2;g.newWave();assert.equal(g.shields.length,0);assert(g.aliens.every(a=>a.type==='fighter'));
 const before=g.aliens.map(a=>({...a}));g.enemyClock=0;g.update(.05,{axis:1,vertical:-1,fire:true});assert(g.player>240&&g.playerY<520);assert(g.enemyShots[0].vx>0);assert(g.enemyShots[0].vy>0);assert.equal(g.shots[0].x,g.player);assert(g.shots[0].y<g.playerY);
 assert.notEqual(g.aliens[0].x-before[0].x,g.aliens[1].x-before[1].x);assert.notEqual(g.aliens[0].y,before[0].y);
 const x=g.player;g.update(.05);assert(g.player>x);g.enemyClock=100;g.invulnerable=100;
 for(let i=0;i<300;i++)g.update(.05,{axis:1,vertical:-1});assert.equal(g.player,460);assert.equal(g.playerY,280);assert(Math.hypot(g.velocity.x,g.velocity.y)<=280);
 g.pause();const y=g.playerY;g.update(.05,{vertical:1});assert.equal(g.playerY,y);g.restart();assert.equal(g.playerY,520);assert.equal(g.flight,false);assert(g.shields.length>0);
});
test('fighter hits use current altitude and diagonal swept projectiles',()=>{
 const g=new Game();g.restart();g.wave=2;g.newWave();g.playerY=350;g.invulnerable=0;g.enemyClock=100;g.enemyShots=[{x:240,y:340,vx:0,vy:210}];g.update(.05);assert.equal(g.lives,2);
 g.invulnerable=0;g.enemyShots=[{x:240,y:515,vx:0,vy:210}];g.update(.01);assert.equal(g.lives,2);
});

test('level 2 crash, unconscious blackout and level 3 wake preserve progress and freeze combat',()=>{
 const g=new Game();g.restart();g.wave=2;g.newWave();g.score=800;g.lives=2;g.aliens=[];g.update(.01);
 assert.equal(g.scene,'crash');assert.equal(g.wave,2);
 const x=g.player;g.update(.05,{axis:1,fire:true});assert.equal(g.player,x);assert.equal(g.shots.length,0);
 g.pause();const time=g.sceneTime;g.update(10);assert.equal(g.sceneTime,time);g.pause();
 while(g.scene==='crash')g.update(.05);assert.equal(g.scene,'blackout');assert.equal(g.wave,2);
 while(g.scene==='blackout')g.update(.05);assert.equal(g.scene,'waking');assert.equal(g.wave,3);assert.equal(g.aliens.length,0);
 while(g.scene)g.update(.05);assert.equal(g.aliens.length,18);assert.equal(g.score,800);assert.equal(g.lives,2);
 g.wave=2;g.aliens=[];g.update(.01);g.restart();assert.equal(g.scene,null);assert.equal(g.wave,1);
});
