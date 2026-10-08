// Synthetic browser profiles. Test the exact local game source without publishing.
const {webkit}=require(process.env.PLAYWRIGHT_MODULE||'/tmp/voxel-web-qa/node_modules/playwright');
const assert=require('node:assert/strict');
const fs=require('node:fs');
(async()=>{
 const browser=await webkit.launch({headless:true});const results=[];
 for(const viewport of [{width:1280,height:900},{width:390,height:844},{width:844,height:390}]){
  const context=await browser.newContext({viewport,hasTouch:true});const page=await context.newPage();const errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.goto('http://127.0.0.1:8767/games/space-invaders/index.html');await page.locator('#start').click();
  await page.waitForTimeout(120);assert.equal(await page.locator('canvas').getAttribute('data-perspective'),'classic');
  if(viewport.width===1280)await page.screenshot({path:'dashboard/evidence/invaders-level-1-classic.png'});
  // Set up the last alien, then kill it with the real Fire key and frame loop.
  await page.evaluate(async()=>{const {game}=await import('./app.mjs');game.player=240;game.direction=0;game.aliens=[{x:240,y:480,type:'squid',points:30}];game.shields=[];game.enemyShots=[];game.enemyClock=100;});
  await page.keyboard.down('Space');await page.waitForFunction(async()=>{const {game}=await import('./app.mjs');return game.wave===2;});await page.keyboard.up('Space');
  await page.waitForFunction(()=>document.querySelector('canvas').dataset.perspective==='cockpit');assert.equal(await page.locator('canvas').getAttribute('data-perspective'),'cockpit');assert.match(await page.locator('#hud').innerText(),/Wave 2/);
  await page.evaluate(async()=>{const {game}=await import('./app.mjs');game.enemyClock=100;});
  await page.keyboard.down('ArrowRight');await page.waitForTimeout(250);await page.keyboard.up('ArrowRight');
  assert(await page.evaluate(async()=>{const {game,project}=await import('./app.mjs');return game.player>240&&Math.abs(project(game.player,200)[0]-innerWidth/2)<.01;}));
  await page.evaluate(async()=>{const {game}=await import('./app.mjs');game.cooldown=0;});await page.keyboard.down('Space');await page.waitForTimeout(40);await page.keyboard.up('Space');assert(await page.evaluate(async()=>{const {game}=await import('./app.mjs');return game.shots.length>0;}));
  await page.screenshot({path:`dashboard/evidence/invaders-level-2-${viewport.width}.png`});
  await page.locator('#pause').click();const before=await page.evaluate(async()=>{const {game}=await import('./app.mjs');return game.aliens[0].x;});await page.waitForTimeout(150);assert.equal(await page.evaluate(async()=>{const {game}=await import('./app.mjs');return game.aliens[0].x;}),before);
  await page.locator('#start').click();
  // Edge: death in the cockpit, then restart must return to classic level 1.
  await page.evaluate(async()=>{const {game}=await import('./app.mjs');game.lives=1;game.invulnerable=0;game.enemyShots=[{x:game.player,y:515}];});await page.waitForTimeout(100);assert.equal(await page.locator('#title').innerText(),'Game over');
  await page.locator('#start').click();await page.waitForTimeout(100);assert.equal(await page.locator('canvas').getAttribute('data-perspective'),'classic');assert.equal(await page.locator('#hud').innerText(),'Score 0 · Lives 3 · Wave 1');
  // Regression: touch movement and cancellation still work.
  await page.locator('[data-control=left]').dispatchEvent('pointerdown',{pointerId:8,pointerType:'touch'});await page.waitForTimeout(120);await page.locator('[data-control=left]').dispatchEvent('pointercancel',{pointerId:8,pointerType:'touch'});assert.equal(await page.locator('.active').count(),0);
  // Next level switches back; score and lives survive level completion.
  await page.evaluate(async()=>{const {game}=await import('./app.mjs');game.wave=2;game.score=90;game.lives=2;game.direction=0;game.cooldown=0;game.shields=[];game.enemyShots=[];game.enemyClock=100;game.aliens=[{x:game.player,y:480,type:'crab',points:10}];});await page.waitForFunction(()=>document.querySelector('canvas').dataset.perspective==='cockpit');await page.keyboard.down('Space');await page.waitForFunction(()=>document.querySelector('#hud').textContent.includes('Wave 3'));await page.keyboard.up('Space');assert.equal(await page.locator('canvas').getAttribute('data-perspective'),'classic');assert.equal(await page.locator('#hud').innerText(),'Score 100 · Lives 2 · Wave 3');
  assert.deepEqual(errors,[]);results.push({viewport,observed:'PASS: classic start, real last-kill transition to cockpit, camera follows movement, fire, pause/resume, cockpit death/restart, touch cancel, level 3 and score/lives carryover',errors});await context.close();
 }
 await browser.close();fs.writeFileSync('dashboard/evidence/invaders-levels-playtest.json',JSON.stringify({command:'PLAYWRIGHT_BROWSERS_PATH=/tmp/voxel-web-qa/browsers TMPDIR="$PWD/.tmp/browser" node deploy/test_space_invaders_levels.cjs',environment:'Linux, headless Playwright WebKit, isolated synthetic contexts; HTTP source server on 127.0.0.1:8767',playtest:'Playtest: Start level 1, fire to clear final alien, play first-person level 2, move/fire, pause/resume, lose final life, restart, cancel touch input, advance to level 3.',expected:'Levels alternate views, shots and movement stay aligned, pause freezes combat, restart restores classic, scores/lives carry forward.',results,limitation:'No physical iOS device test; this task changes the existing browser game.'},null,2));console.log(JSON.stringify(results));
})().catch(e=>{console.error(e);process.exit(1)});
