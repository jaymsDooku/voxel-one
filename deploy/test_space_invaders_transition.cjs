const {webkit}=require(process.env.PLAYWRIGHT_MODULE||'/tmp/voxel-web-qa/node_modules/playwright');
const assert=require('node:assert/strict');
const fs=require('node:fs');
(async()=>{
 const browser=await webkit.launch({headless:true}),results=[];
 for(const viewport of [{width:1280,height:900},{width:390,height:844},{width:844,height:390}]){
  const context=await browser.newContext({viewport,hasTouch:true,...(viewport.width===1280?{recordVideo:{dir:'.tmp/transition-video',size:{width:960,height:675}}}:{})});
  const page=await context.newPage(),errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.goto('http://127.0.0.1:8778/games/space-invaders/index.html');await page.locator('#start').click();await page.evaluate(async()=>{window.testGame=(await import('./app.mjs')).game;});
  assert.equal(await page.locator('canvas').getAttribute('data-transition'),'none');
  await page.screenshot({path:`dashboard/evidence/transition-start-${viewport.width}.png`});
  await page.evaluate(async()=>{const {game}=await import('./app.mjs');game.player=240;game.direction=0;game.aliens=[{x:240,y:480,type:'squid',points:30}];game.shields=[];game.enemyClock=100;game.enemyShots=[];});
  await page.keyboard.down('Space');
  await page.waitForFunction(()=>{const game=window.testGame;if(game.transition&&game.cameraMix>.25){game.pause();return true;}return false;});
  await page.keyboard.up('Space');
  const snapshot=await page.evaluate(async()=>{const {game,project}=await import('./app.mjs');return {elapsed:game.transition.elapsed,mix:game.cameraMix,aliens:game.aliens,player:game.player,point:project(120,180),score:game.score,lives:game.lives};});
  assert(snapshot.mix>0&&snapshot.mix<1);assert.equal(snapshot.score,30);assert.equal(snapshot.lives,3);
  await page.waitForTimeout(200);
  assert.equal(await page.evaluate(()=>{const game=window.testGame;return game.transition.elapsed;}),snapshot.elapsed);
  await page.screenshot({path:`dashboard/evidence/transition-mid-${viewport.width}.png`});
  await page.keyboard.press('KeyP');
  await page.keyboard.down('ArrowRight');await page.keyboard.down('Space');
  await page.waitForFunction(()=>{const game=window.testGame;return game.transition&&game.cameraMix>.8;});
  assert(await page.evaluate(async before=>{const {game}=await import('./app.mjs');return game.player===before.player&&JSON.stringify(game.aliens)===JSON.stringify(before.aliens)&&game.shots.length===0;},snapshot));
  await page.waitForFunction(()=>document.querySelector('canvas').dataset.transition==='none');
  await page.waitForFunction(()=>window.testGame.player>245&&window.testGame.shots.length>0);await page.keyboard.up('ArrowRight');await page.keyboard.up('Space');
  assert(await page.evaluate(async()=>{const {game,project}=await import('./app.mjs');return game.cameraMix===1&&game.player>240&&game.shots.length>0&&Math.abs(project(game.player,200)[0]-innerWidth/2)<.01;}));
  await page.screenshot({path:`dashboard/evidence/transition-end-${viewport.width}.png`});
  // Restart while a second synthetic level completion is midway through its camera move.
  await page.evaluate(async()=>{const {game}=await import('./app.mjs');game.restart();game.aliens=[];});
  await page.waitForFunction(()=>{const game=window.testGame;return game.transition&&game.cameraMix>.2;});
  await page.evaluate(async()=>{const {game}=await import('./app.mjs');game.restart();});
  await page.waitForTimeout(100);
  assert(await page.evaluate(async()=>{const {game}=await import('./app.mjs');return !game.transition&&game.wave===1&&game.cameraMix===0&&game.score===0&&game.lives===3;}));
  await page.locator('[data-control=left]').dispatchEvent('pointerdown',{pointerId:8,pointerType:'touch'});await page.waitForTimeout(150);
  await page.locator('[data-control=left]').dispatchEvent('pointercancel',{pointerId:8,pointerType:'touch'});
  assert(await page.evaluate(async()=>{const {game}=await import('./app.mjs');return game.player<240;}));assert.equal(await page.locator('.active').count(),0);
  assert.deepEqual(errors,[]);results.push({viewport,result:'PASS: real last kill, camera midpoint, frozen combat/input, pause/resume, cockpit combat, restart during transition, classic touch regression',errors});
  const video=page.video();await context.close();if(video)await video.saveAs('dashboard/evidence/transition-playtest.webm');
 }
 await browser.close();
 fs.writeFileSync('dashboard/evidence/transition-playtest.json',JSON.stringify({command:'PLAYWRIGHT_BROWSERS_PATH=/tmp/voxel-web-qa/browsers TMPDIR="$PWD/.tmp/browser" node deploy/test_space_invaders_transition.cjs',environment:'Linux headless WebKit; isolated synthetic browser contexts; exact worktree HTTP server on port 8778',playtest:'Playtest: start level 1; arrange final alien; kill with real Fire key; inspect moving camera; pause and resume mid-transition; hold movement/fire during transition; play cockpit; restart during transition; test touch movement/cancel in classic mode.',expected:'Camera moves smoothly for 1.6 seconds; combat holds; pause freezes camera; first-person combat starts at completion; restart clears transition; classic input still works.',results},null,2));console.log(JSON.stringify(results));
})().catch(e=>{console.error(e);process.exit(1)});
