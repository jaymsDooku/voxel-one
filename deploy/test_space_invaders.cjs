// Uses the operator's installed Playwright, never a personal browser profile.
const {webkit}=require(process.env.PLAYWRIGHT_MODULE || '/tmp/voxel-web-qa/node_modules/playwright');
const assert=require('node:assert/strict');
const fs=require('node:fs');
(async()=>{
 const browser=await webkit.launch({headless:true});
 const results=[];
 for(const mobile of [false,true]){
  const context=await browser.newContext(mobile?{viewport:{width:390,height:844},isMobile:true,hasTouch:true,deviceScaleFactor:2}:{viewport:{width:1280,height:900}});
  const page=await context.newPage();const errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.goto('http://127.0.0.1:8766/index.html');await page.getByRole('link',{name:'Play Voxel Space Invaders'}).click();
  await page.getByRole('button',{name:'Start game',exact:true}).click();await page.waitForTimeout(100);
  assert(await page.locator('#overlay').isHidden());
  if(mobile){
   const fire=await page.locator('[data-control=fire]').boundingBox();await page.touchscreen.tap(fire.x+fire.width/2,fire.y+fire.height/2);
   await page.locator('[data-control=right]').dispatchEvent('pointerdown',{pointerId:1,pointerType:'touch'});
   await page.locator('[data-control=fire]').dispatchEvent('pointerdown',{pointerId:2,pointerType:'touch'});
   await page.waitForTimeout(1800);
   await page.locator('[data-control=right]').dispatchEvent('pointercancel',{pointerId:1,pointerType:'touch'});
   await page.locator('[data-control=left]').dispatchEvent('pointerdown',{pointerId:3,pointerType:'touch'});
   await page.waitForTimeout(5000);
   await page.locator('[data-control=left]').dispatchEvent('pointerup',{pointerId:3,pointerType:'touch'});
   await page.locator('[data-control=fire]').dispatchEvent('pointerup',{pointerId:2,pointerType:'touch'});
  }else{
   await page.keyboard.down('ArrowRight');await page.keyboard.down('Space');await page.waitForTimeout(1300);await page.keyboard.up('ArrowRight');await page.waitForTimeout(6000);await page.keyboard.up('Space');
  }
  const hud=await page.locator('#hud').innerText();assert(/Score \d+ · Lives [1-3] · Wave 1/.test(hud));assert(Number(hud.match(/Score (\d+)/)[1])>0);
  await page.screenshot({path:`dashboard/evidence/space-invaders-${mobile?'touch':'desktop'}.png`});
  await page.locator('#pause').click();await page.waitForTimeout(100);assert.equal(await page.locator('#title').innerText(),'Paused');
  const pausedHud=await page.locator('#hud').innerText();await page.waitForTimeout(600);
  assert.equal(await page.locator('#hud').innerText(),pausedHud);
  await page.getByRole('button',{name:'Resume',exact:true}).last().click();await page.waitForTimeout(100);assert(await page.locator('#overlay').isHidden());
  await page.evaluate(()=>window.dispatchEvent(new Event('blur')));await page.waitForTimeout(100);assert.equal(await page.locator('#title').innerText(),'Paused');
  assert.equal(await page.locator('.active').count(),0);
  if(mobile){await page.setViewportSize({width:844,height:390});await page.getByRole('button',{name:'Resume',exact:true}).last().click();await page.waitForTimeout(200);const controls=await page.locator('.controls').boundingBox();assert(controls.x>=0&&controls.x+controls.width<=844&&controls.y+controls.height<=390);await page.screenshot({path:'dashboard/evidence/space-invaders-landscape.png'});}
  await page.evaluate(async()=>{const {game}=await import('./app.mjs');game.state='playing';game.shots=[];game.enemyShots=[];game.enemyClock=100;game.invulnerable=2;game.aliens=[{x:460,y:150,points:10,type:'squid'}];game.direction=1;});await page.waitForTimeout(200);
  assert(await page.evaluate(async()=>{const {game}=await import('./app.mjs');return game.direction===-1&&game.aliens[0].y===165;}));
  await page.evaluate(async()=>{const {game}=await import('./app.mjs');game.aliens=[{x:240,y:200,points:30,type:'squid'}];game.shots=[{x:240,y:205}];});await page.waitForTimeout(100);assert((await page.locator('#hud').innerText()).includes('Wave 2'));
  await page.evaluate(async()=>{const {game}=await import('./app.mjs');game.lives=1;game.invulnerable=0;game.enemyShots=[{x:game.player,y:515}];});await page.waitForTimeout(100);assert.equal(await page.locator('#title').innerText(),'Game over');
  await page.getByRole('button',{name:'Play again',exact:true}).click();await page.waitForTimeout(100);assert.equal(await page.locator('#hud').innerText(),'Score 0 · Lives 3 · Wave 1');
  assert.deepEqual(errors,[]);results.push({browser:'Playwright WebKit',mobile,viewport:mobile?'390x844, 844x390':'1280x900',hud,observed:'Navigation, start, move/fire, pause/resume, blur pause, release/cancel input, viewport controls, injected boundary reversal, wave completion, game-over/restart passed',errors});await context.close();
 }
 await browser.close();fs.writeFileSync('dashboard/evidence/space-invaders-browser.json',JSON.stringify({results,limitation:'WebKit mobile emulation, not a physical iPhone/iPad or native iOS app'},null,2));console.log(JSON.stringify(results));
})().catch(e=>{console.error(e.stack);process.exit(1)});
