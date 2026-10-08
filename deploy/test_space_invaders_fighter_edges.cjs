// Isolated synthetic profiles; real browser frame loop and rendered controls.
const {webkit}=require(process.env.PLAYWRIGHT_MODULE||'/tmp/voxel-web-qa/node_modules/playwright');
const assert=require('node:assert/strict');
const fs=require('node:fs');
(async()=>{
 const browser=await webkit.launch({headless:true});const results=[];
 try{
 for(const viewport of [{width:1280,height:900},{width:390,height:844},{width:844,height:390}]){
  const context=await browser.newContext({viewport,hasTouch:true});const page=await context.newPage();page.setDefaultTimeout(10000);
  const errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.goto('http://127.0.0.1:8767/games/space-invaders/index.html');await page.locator('#start').click();
  await page.evaluate(async()=>{const {game}=await import('./app.mjs');game.wave=2;game.newWave();game.playerY=350;game.enemyClock=100;game.invulnerable=100;});
  await page.waitForFunction(()=>document.querySelector('#hud').textContent.includes('FIGHTER COMBAT'));
  const geometry=await page.evaluate(()=>{
   const rect=e=>{const r=e.getBoundingClientRect();return {x:r.x,y:r.y,width:r.width,height:r.height};};
   return {panels:['header a','#pause','#hud','.controls'].map(s=>({label:s,...rect(document.querySelector(s))})),buttons:[...document.querySelectorAll('.controls button')].map(e=>{const r=rect(e);return {...r,reachable:document.elementFromPoint(r.x+r.width/2,r.y+r.height/2)===e};})};
  });
  for(const r of [...geometry.panels,...geometry.buttons])assert(r.x>=0&&r.y>=0&&r.x+r.width<=viewport.width+1&&r.y+r.height<=viewport.height+1);
  for(const b of geometry.buttons)assert(b.width>=44&&b.height>=44&&b.reachable);
  for(let i=0;i<geometry.panels.length;i++)for(let j=i+1;j<geometry.panels.length;j++){
   const a=geometry.panels[i],b=geometry.panels[j];assert(Math.min(a.x+a.width,b.x+b.width)-Math.max(a.x,b.x)<=1||Math.min(a.y+a.height,b.y+b.height)-Math.max(a.y,b.y)<=1,`${a.label} overlaps ${b.label}`);
  }
  await page.keyboard.down('KeyA');
  await page.locator('[data-control=down]').dispatchEvent('pointerdown',{pointerId:21,pointerType:'touch'});
  await page.locator('[data-control=fire]').dispatchEvent('pointerdown',{pointerId:22,pointerType:'touch'});
  await page.waitForTimeout(250);
  const flying=await page.evaluate(async()=>{const {game}=await import('./app.mjs');return {x:game.player,y:game.playerY,speed:Math.hypot(game.velocity.x,game.velocity.y),shots:game.shots.length};});
  assert(flying.x<240&&flying.y>350&&flying.shots>0&&flying.speed<=280);
  await page.keyboard.up('KeyA');
  for(const [name,id] of [['down',21],['fire',22]])await page.locator(`[data-control=${name}]`).dispatchEvent('pointercancel',{pointerId:id,pointerType:'touch'});
  await page.waitForTimeout(300);
  const drift=await page.evaluate(async()=>{const {game}=await import('./app.mjs');return {x:game.player,y:game.playerY,speed:Math.hypot(game.velocity.x,game.velocity.y)};});
  assert(drift.x<flying.x&&drift.y>flying.y&&drift.speed<flying.speed);assert.equal(await page.locator('.active').count(),0);
  await page.locator('#pause').click();
  const paused=await page.evaluate(async()=>{const {game}=await import('./app.mjs');return [game.player,game.playerY,game.velocity.x,game.velocity.y,game.aliens[0].age];});
  await page.waitForTimeout(200);assert.deepEqual(await page.evaluate(async()=>{const {game}=await import('./app.mjs');return [game.player,game.playerY,game.velocity.x,game.velocity.y,game.aliens[0].age];}),paused);
  await page.locator('#start').click();
  await page.evaluate(async()=>{const {game}=await import('./app.mjs');game.player=240;game.playerY=350;game.velocity={x:0,y:0};game.invulnerable=0;game.enemyShots=[{x:240,y:515,vx:0,vy:210}];});
  await page.waitForTimeout(100);assert.equal(await page.evaluate(async()=>{const {game}=await import('./app.mjs');return game.lives;}),3);
  await page.evaluate(async()=>{const {game}=await import('./app.mjs');game.aliens=[{x:240,y:350,baseX:240,baseY:350,phase:0,age:0,type:'fighter',points:40}];game.enemyShots=[];});
  await page.waitForFunction(async()=>{const {game}=await import('./app.mjs');return game.lives===2;});
  assert.deepEqual(errors,[]);results.push({viewport,geometry,flying,drift,observed:'PASS: level 2 geometry, keyboard+touch down/fire, cancellation and drift decay, full pause freeze, old-altitude miss and fighter contact damage',errors});await context.close();
 }
 }finally{await browser.close();}
 fs.writeFileSync('dashboard/evidence/fighters-edge-playtest.json',JSON.stringify({command:'PLAYWRIGHT_BROWSERS_PATH=/tmp/voxel-web-qa/browsers TMPDIR="$PWD/.tmp/browser" node deploy/test_space_invaders_fighter_edges.cjs',environment:'Linux headless WebKit, synthetic profiles, local source HTTP on port 8767',playtest:'Playtest: enter level 2 with synthetic setup; hold keyboard left with touch down/fire; release/cancel and drift; pause/resume; miss at old altitude; collide with a jet.',expected:'Controls and HUD fit; combined inputs move both axes and fire; drift decays; pause freezes state; hits use current altitude; jet contact removes one life.',results},null,2)+'\n');console.log('Playtest: fighter edge checks passed at all three viewports; no page errors.');
})().catch(e=>{console.error(e);process.exit(1)});
