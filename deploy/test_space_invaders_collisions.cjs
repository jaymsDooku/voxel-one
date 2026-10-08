// Fresh synthetic WebKit contexts; collisions run through requestAnimationFrame.
const {webkit}=require(process.env.PLAYWRIGHT_MODULE || '/tmp/voxel-web-qa/node_modules/playwright');
const assert=require('node:assert/strict');
const fs=require('node:fs');
(async()=>{
 const browser=await webkit.launch({headless:true});const results=[];
 try {
 for(const viewport of [{width:844,height:390},{width:390,height:844}]){
  const context=await browser.newContext({viewport,isMobile:true,hasTouch:true});const page=await context.newPage();const errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.goto('http://127.0.0.1:8766/games/space-invaders/index.html');await page.locator('#start').click();
  for(const target of ['alien','ship','shield','enemy-shield','saucer'])for(const hit of [false,true]){
   const setup=await page.evaluate(async({target,hit,wide})=>{
    const {game}=await import('./app.mjs');game.restart();game.enemyClock=100;game.saucerClock=100;game.invulnerable=0;game.shields=[];game.shots=[];game.enemyShots=[];
    game.aliens=[{x:240,y:200,type:'crab',points:20},{x:400,y:80,type:'squid',points:30}];
    const enemy=target==='ship'||target==='enemy-shield';let y=target==='ship'?520:target.includes('shield')?440:target==='saucer'?38:200;
    let x=240;if(target==='saucer')game.saucer={x:240,y:38};if(target.includes('shield'))game.shields=[{x:240,y:440}];
    if(!hit){
     if(target.includes('shield'))x-=4;
     else if(wide)x+=15; // Old hitbox accepted this invisible gap in landscape.
     else y+=enemy?13:-14; // Portrait's vertical scaling had the same fault.
    }
    game[enemy?'enemyShots':'shots']=[{x,y}];
    return {x,y,renderScale:game.renderScale};
   },{target,hit,wide:viewport.width>viewport.height});
   // Await a real update in this specific scenario before reading its outcome.
   let updated=false;
   for(let frame=0;frame<40&&!updated;frame++){
    await page.waitForTimeout(25);
    updated=await page.evaluate(async({target,y})=>{
     const {game}=await import('./app.mjs');
     const shots=game[target==='ship'||target==='enemy-shield'?'enemyShots':'shots'];
     return shots.length!==1||shots[0].y!==y;
    },{target,y:setup.y});
   }
   assert(updated,'Combat update did not run');
   const observed=await page.evaluate(async()=>{const {game}=await import('./app.mjs');game.pause();return {score:game.score,lives:game.lives,shields:game.shields.length,player:game.player,invulnerable:game.invulnerable,enemyShots:game.enemyShots};});
   if(target==='alien')assert.equal(observed.score,hit?20:0,`${viewport.width} ${target} ${hit}`);
   if(target==='saucer')assert.equal(observed.score,hit?100:0,`${viewport.width} ${target} ${hit}`);
   if(target==='ship')assert.equal(observed.lives,hit?2:3,`${viewport.width} ${target} ${hit}`);
   if(target.includes('shield'))assert.equal(observed.shields,hit?0:1,`${viewport.width} ${target} ${hit}`);
   results.push({viewport,target,hit,setup,observed});
  }
  // Capture a normal live scene from this source after the targeted checks.
  await page.locator('#start').click();
  await page.evaluate(async()=>{const {game}=await import('./app.mjs');game.restart();});await page.waitForTimeout(200);
  await page.screenshot({path:`dashboard/evidence/space-invaders-collision-fix-${viewport.width}.png`});
  assert.deepEqual(errors,[]);await context.close();
 }
 fs.writeFileSync('dashboard/evidence/space-invaders-collision-fix.json',JSON.stringify({browser:'WebKit 26.6',syntheticProfiles:true,results},null,2)+'\n');
 console.log(`Playtest: ${results.length} live alien, ship, shield and saucer hit/miss checks passed in 844x390 and 390x844; no page errors.`);
 }finally{await browser.close();}
})().catch(e=>{console.error(e.stack);process.exit(1)});
