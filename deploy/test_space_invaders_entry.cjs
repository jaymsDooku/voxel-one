const {webkit}=require(process.env.PLAYWRIGHT_MODULE||'/tmp/voxel-web-qa/node_modules/playwright');
const assert=require('node:assert/strict');const fs=require('node:fs');
(async()=>{
 const browser=await webkit.launch({headless:true});const results=[];
 for(const viewport of [{width:1280,height:900},{width:390,height:844},{width:844,height:390}]){
  const context=await browser.newContext({viewport,hasTouch:true,recordVideo:viewport.width===1280?{dir:'.tmp/entry-video',size:{width:960,height:675}}:undefined});const page=await context.newPage();const errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.goto('http://127.0.0.1:8779/games/space-invaders/index.html');await page.locator('#start').click();await page.evaluate(async()=>{window.__qaGame=(await import('./app.mjs')).game;});
  assert.equal(await page.locator('canvas').getAttribute('data-scene'),'space');
  // Synthetic last fighter; the real Fire key and animation loop score the kill.
  await page.evaluate(async()=>{const {game}=await import('./app.mjs');game.wave=2;game.newWave();game.score=90;game.lives=2;game.invulnerable=100;game.enemyClock=100;game.aliens=[{x:240,y:490,baseX:240,baseY:490,phase:0,age:0,type:'fighter',points:40}];});
  await page.keyboard.down('Space');await page.waitForFunction(()=>document.querySelector('canvas').dataset.scene==='crash');await page.keyboard.up('Space');
  for(const phase of ['approach','atmosphere','clouds','descent','impact']){
   await page.waitForFunction(p=>document.querySelector('canvas').dataset.entryPhase===p,phase);
   assert.equal(await page.locator('canvas').getAttribute('data-perspective'),'cockpit');
   if(phase==='clouds'){await page.locator('#pause').click();const frozen=await page.evaluate(()=>window.__qaGame.sceneTime);await page.waitForTimeout(300);assert.equal(await page.evaluate(()=>window.__qaGame.sceneTime),frozen);await page.locator('#start').click();}
   if(phase!=='impact')await page.screenshot({path:`dashboard/evidence/entry-${phase}-${viewport.width}.png`});
  }
  await page.screenshot({path:`dashboard/evidence/entry-crash-${viewport.width}.png`});
  await page.waitForFunction(()=>document.querySelector('canvas').dataset.scene==='blackout');
  assert.equal(await page.evaluate(async()=>{const {game}=await import('./app.mjs');return game.wave;}),2);
  await page.waitForFunction(()=>document.querySelector('canvas').dataset.scene==='waking');await page.waitForFunction(()=>{const game=window.__qaGame;if(game.scene==='waking'&&game.sceneTime>=1.9){return true;}return false;});
  assert(await page.evaluate(async()=>{const {game}=await import('./app.mjs');return game.wave===3&&game.aliens.length===0&&game.score===130&&game.lives===2;}));
  await page.screenshot({path:`dashboard/evidence/entry-waking-${viewport.width}.png`});
  await page.waitForFunction(()=>document.querySelector('canvas').dataset.scene==='planet');
  await page.keyboard.down('ArrowUp');await page.keyboard.down('Space');await page.waitForFunction(()=>{const game=window.__qaGame;return game.playerY<520&&game.shots.length>0;});await page.keyboard.up('ArrowUp');await page.keyboard.up('Space');
  // Edge: backgrounding while waking pauses the story, then restart clears it.
  await page.evaluate(async()=>{const {game}=await import('./app.mjs');game.scene='waking';game.sceneTime=.3;window.dispatchEvent(new Event('blur'));});await page.waitForTimeout(100);
  assert(await page.evaluate(async()=>{const {game}=await import('./app.mjs');return game.state==='paused'&&game.sceneTime===.3;}));
  await page.locator('#start').click();await page.evaluate(async()=>{const {game}=await import('./app.mjs');game.restart();});await page.waitForTimeout(100);
  assert.equal(await page.locator('canvas').getAttribute('data-scene'),'space');assert.match(await page.locator('#hud').innerText(),/Score 0 · Lives 3 · Wave 1/);assert.deepEqual(errors,[]);
  results.push({viewport,result:'PASS: real last-fighter kill, first-person approach, atmosphere, clouds, voxel descent, impact, pause/resume, blackout, awakening, score/lives, level 3 flight/fire, blur pause and restart; no browser errors'});
  const video=page.video();await context.close();if(video)await video.saveAs('dashboard/evidence/entry-playtest.webm');
 }
 await browser.close();fs.writeFileSync('dashboard/evidence/entry-playtest.json',JSON.stringify({command:'PLAYWRIGHT_BROWSERS_PATH=/tmp/voxel-web-qa/browsers TMPDIR="$PWD/.tmp/browser" node deploy/test_space_invaders_entry.cjs',environment:'Linux headless WebKit; fresh synthetic contexts; local HTTP source on 127.0.0.1:8779',playtest:'Playtest: start, set synthetic final level 2 fighter, fire through real keyboard/frame loop; watch first-person approach, atmosphere, clouds, voxel descent, impact, pause/resume, unconscious blackout, wake under two moons, fly/fire on level 3, blur while waking, restart.',expected:'8-second first-person descent at end of level 2; planet approach, atmosphere, cloud passage and voxel impact in order; unconscious interval; level 3 opens on voxel landscape with two moons; no combat during story; pause freezes story; score/lives survive; restart restores level 1.',results},null,2));console.log(JSON.stringify(results));
})().catch(e=>{console.error(e);process.exit(1)});
