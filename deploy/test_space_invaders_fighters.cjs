// Synthetic browser profiles. Test the exact local game source without publishing.
const {webkit}=require(process.env.PLAYWRIGHT_MODULE||'/tmp/voxel-web-qa/node_modules/playwright');
const assert=require('node:assert/strict');
const fs=require('node:fs');
(async()=>{
 const browser=await webkit.launch({headless:true});const results=[];
 for(const viewport of [{width:1280,height:900},{width:390,height:844},{width:844,height:390}].filter(v=>!process.env.SPACE_INVADERS_VIEWPORT_WIDTH||v.width===Number(process.env.SPACE_INVADERS_VIEWPORT_WIDTH))){
  const context=await browser.newContext({viewport,hasTouch:true,...(viewport.width===1280?{recordVideo:{dir:'.tmp/fighter-video',size:{width:960,height:675}}}:{})});const page=await context.newPage();page.setDefaultTimeout(10000);const errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.goto(process.env.SPACE_INVADERS_URL||'http://127.0.0.1:8767/games/space-invaders/index.html');await page.locator('#start').click();await page.evaluate(async()=>{window.__qaGame=(await import('./app.mjs')).game;});
  await page.waitForTimeout(120);assert.equal(await page.locator('canvas').getAttribute('data-perspective'),'classic');
  if(viewport.width===1280)await page.screenshot({path:'dashboard/evidence/fighters-level-1-regression.png'});
  // Set up the last alien, then kill it with the real Fire key and frame loop.
  await page.evaluate(async()=>{const {game}=await import('./app.mjs');game.player=240;game.direction=0;game.aliens=[{x:240,y:480,type:'squid',points:30}];game.shields=[];game.enemyShots=[];game.enemyClock=100;});
  await page.keyboard.down('Space');await page.waitForFunction(()=>{const game=window.__qaGame;return game.wave===2;});await page.keyboard.up('Space');
  await page.waitForFunction(()=>document.querySelector('canvas').dataset.perspective==='cockpit');assert.equal(await page.locator('canvas').getAttribute('data-perspective'),'cockpit');assert.match(await page.locator('#hud').innerText(),/Wave 2.*FIGHTER COMBAT/);
  assert(await page.evaluate(async()=>{const {game}=await import('./app.mjs');return game.shields.length===0&&game.aliens.every(a=>a.type==='fighter');}));
  const positions=await page.evaluate(async()=>{const {game}=await import('./app.mjs');return game.aliens.map(a=>({x:a.x,y:a.y}));});
  await page.keyboard.down('ArrowUp');await page.waitForFunction(()=>{const game=window.__qaGame;return game.playerY<450;});await page.keyboard.up('ArrowUp');
  assert(await page.evaluate(async before=>{const {game}=await import('./app.mjs');return game.playerY<450&&Math.abs((game.aliens[0].x-before[0].x)-(game.aliens[1].x-before[1].x))>1;},positions));
  await page.evaluate(async()=>{const {game}=await import('./app.mjs');game.enemyClock=100;});
  await page.keyboard.down('ArrowRight');await page.waitForTimeout(250);await page.keyboard.up('ArrowRight');
  assert(await page.evaluate(async()=>{const {game,project}=await import('./app.mjs');return game.player>240&&Math.abs(project(game.player,200)[0]-innerWidth/2)<.01;}));
  await page.evaluate(async()=>{const {game}=await import('./app.mjs');game.cooldown=0;});await page.keyboard.down('Space');await page.waitForTimeout(40);await page.keyboard.up('Space');assert(await page.evaluate(async()=>{const {game}=await import('./app.mjs');return game.shots.length>0;}));
  // Real enemy update emits aimed fire, then a hit resolves at the flown altitude.
  await page.evaluate(async()=>{const {game}=await import('./app.mjs');game.enemyClock=0;});await page.waitForTimeout(100);
  assert(await page.evaluate(async()=>{const {game}=await import('./app.mjs');return game.enemyShots.some(s=>Number.isFinite(s.vx)&&Number.isFinite(s.vy)&&Math.abs(s.vx)>0);}));
  await page.screenshot({path:`dashboard/evidence/fighters-level-2-${viewport.width}.png`});
  await page.evaluate(async()=>{const {game}=await import('./app.mjs');game.lives=3;game.invulnerable=0;game.enemyClock=100;game.enemyShots=[{x:game.player,y:game.playerY-5,vx:0,vy:210}];});
  await page.waitForFunction(()=>{const game=window.__qaGame;return game.lives===2;});

  await page.locator('#pause').click();const before=await page.evaluate(async()=>{const {game}=await import('./app.mjs');return game.aliens[0].x;});await page.waitForTimeout(150);assert.equal(await page.evaluate(async()=>{const {game}=await import('./app.mjs');return game.aliens[0].x;}),before);
  await page.locator('#start').click();
  // Edge: death in the cockpit, then restart must return to classic level 1.
  await page.evaluate(async()=>{const {game}=await import('./app.mjs');game.lives=1;game.invulnerable=0;game.enemyShots=[{x:game.player,y:game.playerY-5}];});await page.waitForTimeout(100);assert.equal(await page.locator('#title').innerText(),'Game over');
  await page.locator('#start').click();await page.waitForTimeout(100);assert.equal(await page.locator('canvas').getAttribute('data-perspective'),'classic');assert.equal(await page.locator('#hud').innerText(),'Score 0 · Lives 3 · Wave 1');
  // Regression: touch movement and cancellation still work.
  await page.locator('[data-control=left]').dispatchEvent('pointerdown',{pointerId:8,pointerType:'touch'});await page.waitForTimeout(120);await page.locator('[data-control=left]').dispatchEvent('pointercancel',{pointerId:8,pointerType:'touch'});assert.equal(await page.locator('.active').count(),0);
  // Next level switches back; score and lives survive level completion.
  await page.evaluate(async()=>{const {game}=await import('./app.mjs');game.wave=2;game.score=90;game.lives=2;game.invulnerable=2;game.direction=0;game.cooldown=0;game.shields=[];game.enemyShots=[];game.enemyClock=100;game.aliens=[{x:game.player,y:game.playerY-15,type:'fighter',points:10}];});await page.waitForFunction(()=>document.querySelector('canvas').dataset.perspective==='cockpit');await page.keyboard.down('Space');await page.waitForFunction(()=>document.querySelector('#hud').textContent.includes('Wave 3'));await page.keyboard.up('Space');await page.waitForFunction(()=>{const game=window.__qaGame;return game.wave===3&&!game.scene;},null,{timeout:30000});assert.equal(await page.locator('canvas').getAttribute('data-perspective'),'classic');assert.equal(await page.locator('#hud').innerText(),'Score 100 · Lives 2 · Wave 3 · FIGHTER COMBAT');
  // Flight persists in level 3; touch up cancels and pause freezes both axes.
  await page.locator('[data-control=up]').dispatchEvent('pointerdown',{pointerId:9,pointerType:'touch'});await page.waitForTimeout(300);await page.locator('[data-control=up]').dispatchEvent('pointercancel',{pointerId:9,pointerType:'touch'});
  assert(await page.evaluate(async()=>{const {game}=await import('./app.mjs');return game.flight&&game.playerY<520;}));assert.equal(await page.locator('.active').count(),0);
  await page.keyboard.down('ArrowUp');await page.keyboard.down('ArrowRight');await page.waitForTimeout(1600);await page.keyboard.up('ArrowUp');await page.keyboard.up('ArrowRight');
  assert(await page.evaluate(async()=>{const {game}=await import('./app.mjs');return game.player<=460&&game.playerY>=280;}));
  await page.locator('#pause').click();await page.screenshot({path:`dashboard/evidence/fighters-level-3-${viewport.width}.png`});
  assert.deepEqual(errors,[]);results.push({viewport,observed:'PASS: level 1 regression, actual last-kill transition, level 2 independent jet paths and two-axis flight/fire, aimed enemy fire and altitude hit, cockpit camera, pause/resume, death/restart, level 3 fighter combat with score/lives, touch up/cancel and boundaries',errors});const video=page.video();await context.close();if(video)await video.saveAs('dashboard/evidence/fighters-playtest.webm');
 }
 await browser.close();fs.writeFileSync('dashboard/evidence/fighters-playtest.json',JSON.stringify({command:(process.env.SPACE_INVADERS_VIEWPORT_WIDTH?'SPACE_INVADERS_VIEWPORT_WIDTH='+process.env.SPACE_INVADERS_VIEWPORT_WIDTH+' ':'')+(process.env.SPACE_INVADERS_URL?'SPACE_INVADERS_URL='+process.env.SPACE_INVADERS_URL+' ':'')+'PLAYWRIGHT_BROWSERS_PATH=/tmp/voxel-web-qa/browsers TMPDIR="$PWD/.tmp/browser" node deploy/test_space_invaders_fighters.cjs',environment:'Linux, headless Playwright WebKit, isolated synthetic contexts; HTTP source server on 127.0.0.1:8779',playtest:'Playtest: Start level 1, fire to clear final alien, play first-person level 2, fly up/right with momentum and fire, pause/resume, lose final life, restart, cancel touch input, advance to level 3, fly by touch, test flight boundaries.',expected:'Level 2+ has independent jet attack runs, two-axis keyboard/touch flight and aimed fire; views alternate, shots and movement stay aligned, pause freezes combat, restart restores classic, scores/lives carry forward.',results,limitation:'No physical iOS device test; this task changes the existing browser game.'},null,2));console.log(JSON.stringify(results));
})().catch(e=>{console.error(e);process.exit(1)});
