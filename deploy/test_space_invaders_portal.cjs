// Source-only, synthetic profiles. First 11 kills are fixture setup; final kill,
// traversal, district walking, guard shot and all objective interactions use controls.
const {chromium,webkit}=require(process.env.PLAYWRIGHT_MODULE||'/tmp/voxel-web-qa/node_modules/playwright');
const fs=require('node:fs'),assert=require('node:assert/strict');
const URL='http://127.0.0.1:8779/games/space-invaders/';
const read=(page,fn)=>page.evaluate(async code=>{const app=await import('./app.mjs');return Function('app',`return (${code})(app)`)(app);},fn.toString());
const wait=(page,fn,timeout=15000)=>page.waitForFunction(fn,null,{timeout});
async function resume(page){await page.locator('#start').click();await page.waitForTimeout(100);assert.equal(await read(page,({game})=>game.state),'playing');}
async function key(page,code,ms){await page.keyboard.down(code);await page.waitForTimeout(ms);await page.keyboard.up(code);}
async function interact(page,mobile){if(!mobile)return page.keyboard.press('KeyE',{delay:90});const button=page.locator('[data-ground=interact]');await button.dispatchEvent('pointerdown',{pointerType:'touch',pointerId:37});await page.waitForTimeout(100);await button.dispatchEvent('pointerup',{pointerType:'touch',pointerId:37});}
async function moveTo(page,x,z){
 console.log(`walk to ${x},${z}`);
 await page.evaluate(async({x,z})=>{const {game}=await import('./app.mjs'),p=game.ground.player;p.yaw=Math.atan2(x-p.x,z-p.z);p.pitch=0;},{x,z});
 await page.keyboard.down('KeyW');
 try{await page.waitForFunction(({x,z})=>{const p=window.__game.ground.player;return Math.hypot(p.x-x,p.z-z)<.3;},{x,z},{timeout:25000});}
 catch(error){console.log(await read(page,({game})=>({state:game.state,player:game.ground.player})));throw error;}
 finally{await page.keyboard.up('KeyW');}
}

(async()=>{
 const report={command:'TMPDIR="$PWD/.tmp/browser" PLAYWRIGHT_BROWSERS_PATH=/tmp/voxel-web-qa/browsers node deploy/test_space_invaders_portal.cjs',sourceManifest:'portal-source-sha256.json',environment:'Linux VPS; sandboxed headless Chromium and WebKit; software rendering; isolated synthetic profiles; source HTTP 8779',results:[]};
 for(const [name,engine]of Object.entries({chromium,webkit})){
  if(process.env.PORTAL_ENGINE&&process.env.PORTAL_ENGINE!==name)continue;
  const browser=await engine.launch({headless:true,chromiumSandbox:true});
  for(const mobile of [false,true]){
   if(process.env.PORTAL_TOUCH!==undefined&&mobile!==(process.env.PORTAL_TOUCH==='1'))continue;
   const viewport=mobile?(process.env.PORTAL_LANDSCAPE==='1'?{width:667,height:375}:{width:390,height:844}):{width:1280,height:720},tag=`${name}-${mobile?(process.env.PORTAL_LANDSCAPE==='1'?'touch-landscape':'touch'):'desktop'}`;
   const context=await browser.newContext({viewport,hasTouch:mobile,recordVideo:name==='chromium'&&!mobile?{dir:'.tmp/portal-video',size:{width:960,height:540}}:undefined});
   const page=await context.newPage(),errors=[];page.on('pageerror',e=>errors.push(e.message));
   await page.goto(URL+'?devScene=portal');await page.evaluate(async()=>{window.__game=(await import('./app.mjs')).game;const c=document.querySelector('canvas:not(#ground-world)');window.__lockRequests=0;c.requestPointerLock=()=>{window.__lockRequests++;return Promise.reject(new DOMException('Synthetic denied capture','NotAllowedError'));};});
   if(process.env.PORTAL_REDUCED==='1')await read(page,app=>{app.groundSettings.motion=false;app.groundSettings.quality='low';});
   await read(page,({game})=>{const s=game.ground;for(const e of s.enemies.slice(0,-1))s.defeat(e);const e=s.enemies.at(-1);Object.assign(e,{x:.5,y:13,z:16.5,health:34,state:'patrol',clock:100,path:[],home:{x:.5,y:13,z:16.5}});Object.assign(s.player,{x:.5,y:13,z:10.5,yaw:0,pitch:0,health:63,ammo:8,immune:100});s.previous={...s.player};s.score=1000;s.lives=2;game.score=1000;game.lives=2;});
   await resume(page);await key(page,'KeyF',100);await wait(page,()=>window.__game.portal?.state==='ready');
   const snapshot=await read(page,({game})=>({score:game.score,lives:game.lives,health:game.ground.player.health,ammo:game.ground.player.ammo}));assert.deepEqual(snapshot,{score:1075,lives:2,health:63,ammo:7});assert.equal(await read(page,({game})=>game.state),'playing');assert.equal(await read(page,({game})=>game.ground.completionCount),1);
   console.log(tag+' source ready');await page.screenshot({path:`dashboard/evidence/portal-source-${tag}.png`});
   // Shots do not traverse. Side-of-aperture sweep is covered by unit tests.
   await key(page,'KeyF',100);assert.equal(await read(page,({game})=>game.wave),3);snapshot.ammo=await read(page,({game})=>game.ground.player.ammo);
   const requests=await page.evaluate(()=>window.__lockRequests);
   if(mobile){
    const stick=page.locator('#move-stick'),look=page.locator('#look-pad');await stick.dispatchEvent('pointerdown',{pointerType:'touch',pointerId:11,clientX:60,clientY:700});await stick.dispatchEvent('pointermove',{pointerType:'touch',pointerId:11,clientX:60,clientY:658});await look.dispatchEvent('pointerdown',{pointerType:'touch',pointerId:12,clientX:300,clientY:600});await look.dispatchEvent('pointermove',{pointerType:'touch',pointerId:12,clientX:302,clientY:600});await wait(page,()=>window.__game.wave===4);await stick.dispatchEvent('pointercancel',{pointerType:'touch',pointerId:11});await look.dispatchEvent('pointercancel',{pointerType:'touch',pointerId:12});
   }else{await page.keyboard.down('KeyW');await wait(page,()=>window.__game.wave===4);await page.keyboard.up('KeyW');}
   assert.equal(await read(page,({game})=>game.state),'paused');assert.equal(await page.evaluate(()=>window.__lockRequests),requests);
   assert.deepEqual(await read(page,({game})=>({score:game.score,lives:game.lives,health:game.ground.player.health,ammo:game.ground.player.ammo})),snapshot);
   assert.equal(await read(page,({game})=>game.portal.commits),1);await resume(page);console.log(tag+' arrived');await page.screenshot({path:`dashboard/evidence/portal-arrival-${tag}.png`});
   // Walk to contact; real interaction gives objective and distinct newspaper text.
   await moveTo(page,-3,1);await interact(page,mobile);await wait(page,()=>window.__game.ground.objective===1);
   await interact(page,mobile);assert(await read(page,({game})=>game.ground.dialogue.includes('underground edition')));
   await page.screenshot({path:`dashboard/evidence/portal-contact-${tag}.png`});
   // Travel down the tram route. AI runs normally; protection fixture isolates controls.
   await read(page,({game})=>{game.ground.player.immune=100;});await moveTo(page,.5,17);
   // Aim at nearest visible guard and fire through the shared wall/muzzle raycast.
   await read(page,({game})=>{game.ground.player.immune=0;});await wait(page,()=>window.__game.ground.projectiles.length>0,10000);await page.screenshot({path:`dashboard/evidence/portal-guard-fire-${tag}.png`});await read(page,({game})=>{game.ground.player.immune=100;});
   const target=await read(page,({game})=>{const g=game.ground,p=g.player;const e=g.enemies.filter(e=>e.state!=='defeated'&&!g.world.ray({x:p.x,y:p.y+1.6,z:p.z},{x:e.x,y:e.y+1,z:e.z}).blocked).sort((a,b)=>Math.hypot(a.x-p.x,a.z-p.z)-Math.hypot(b.x-p.x,b.z-p.z))[0];if(!e)return null;p.yaw=Math.atan2(e.x-p.x,e.z-p.z);p.pitch=Math.atan2(e.y+1.2-p.y-1.6,Math.hypot(e.x-p.x,e.z-p.z));return e.id;});assert.notEqual(target,null);
   const diagnostics=await read(page,app=>({...app.groundDiagnostics(),prepareCallMs:app.game.portal.prepareCallMs,props:app.game.ground.world.boxes.length+app.game.ground.world.details.length,actors:app.game.ground.enemies.length+app.game.ground.civilians.length}));
   const frameMs=await page.evaluate(()=>new Promise(resolve=>{const samples=[];let last=performance.now();function next(t){samples.push(t-last);last=t;if(samples.length===60)resolve(samples.sort((a,b)=>a-b));else requestAnimationFrame(next);}requestAnimationFrame(next);}));
   const remaining=await read(page,({game})=>game.ground.remaining);await page.keyboard.down('KeyF');await wait(page,()=>window.__game.ground.remaining<6);await page.keyboard.up('KeyF');assert.equal(await read(page,({game})=>game.ground.remaining),remaining-1);
   await page.screenshot({path:`dashboard/evidence/portal-combat-${tag}.png`});
   await moveTo(page,.5,28);await moveTo(page,17,28);await moveTo(page,17,34.2);await moveTo(page,20,34.2);await moveTo(page,20,40);assert.equal(await read(page,({game})=>game.ground.objective),2);await moveTo(page,18,40);await moveTo(page,19,58.5);await interact(page,mobile);await wait(page,()=>window.__game.state==='secured');assert.equal(await read(page,({game})=>game.ground.completionCount),1);console.log(tag+' objective complete');await page.screenshot({path:`dashboard/evidence/portal-complete-${tag}.png`});
   // Repeat run: cancel/failure, pause, reduced settings, restart during loading.
   await page.locator('#start').click();assert.equal(await read(page,({game})=>game.wave),1);await page.goto(URL+'?devScene=portal');await page.evaluate(async()=>{window.__game=(await import('./app.mjs')).game;document.querySelector('canvas:not(#ground-world)').requestPointerLock=()=>Promise.reject(Error('synthetic denied'));});
   await read(page,({game})=>{game.prepareEarth=()=>{throw Error('Synthetic preparation failure');};for(const e of game.ground.enemies)game.ground.defeat(e);});await resume(page);await wait(page,()=>window.__game.portal?.state==='failure');const saved=await read(page,({game})=>({x:game.ground.player.x,z:game.ground.player.z,id:game.ground.id}));await page.screenshot({path:`dashboard/evidence/portal-failure-${tag}.png`});
   await page.locator('#portal-cancel').click();assert.equal(await read(page,({game})=>game.portal.state),'locked');await read(page,({game})=>{game.prepareEarth=undefined;game.portal.prepare=()=>{throw Error('Synthetic second failure');};});await interact(page,mobile);await wait(page,()=>window.__game.portal.state==='failure');
   await page.evaluate(async()=>{const {EarthMission}=await import('./earth-scene.mjs');window.__game.portal.prepare=()=>new EarthMission(window.__game.ground);});await page.locator('#portal-retry').click();await wait(page,()=>window.__game.portal.state==='ready');assert.deepEqual(await read(page,({game})=>({x:game.ground.player.x,z:game.ground.player.z,id:game.ground.id})),saved);await page.screenshot({path:`dashboard/evidence/portal-retry-${tag}.png`});
   await read(page,({game})=>{game.portal.fail(Error('Synthetic pending-load scenario'));game.portal.prepare=()=>new Promise(()=>{});});await page.locator('#portal-retry').click();await page.keyboard.press('Escape');const elapsed=await read(page,({game})=>game.portal.elapsed);await page.waitForTimeout(300);assert.equal(await read(page,({game})=>game.portal.elapsed),elapsed);assert.deepEqual(await read(page,({game})=>({x:game.ground.player.x,z:game.ground.player.z,id:game.ground.id})),saved);
   if(mobile){await page.setViewportSize({width:844,height:390});await page.evaluate(()=>dispatchEvent(new Event('orientationchange')));assert.equal(await read(page,({game})=>game.state),'paused');await page.setViewportSize(viewport);}
   else{const loss=await page.evaluate(()=>{const e=document.querySelector('#ground-world').getContext('webgl').getExtension('WEBGL_lose_context');if(!e)return false;e.loseContext();setTimeout(()=>e.restoreContext(),250);return true;});assert(loss);await page.waitForTimeout(800);assert.equal(await read(page,({game})=>game.state),'paused');}
   await read(page,app=>{app.groundSettings.motion=false;app.groundSettings.quality='low';app.game.portal.prepare=()=>new Promise(()=>{});});await resume(page);await page.screenshot({path:`dashboard/evidence/portal-reduced-${tag}.png`});await read(page,({game})=>{game.restart();});assert.equal(await read(page,({game})=>game.wave),1);assert.equal(await read(page,({game})=>game.portal),null);
   // Death stays in destination; no source rewards recur. Then zero-life restart.
   await page.goto(URL+'?devScene=earth');await page.evaluate(async()=>{window.__game=(await import('./app.mjs')).game;document.querySelector('canvas:not(#ground-world)').requestPointerLock=()=>Promise.reject(Error('synthetic denied'));});await resume(page);await read(page,({game})=>{const g=game.ground;g.objective=2;g.spawn=g.world.safe(18,39);g.player.immune=0;g.damage(100);});assert(await read(page,({game})=>game.wave===4&&game.ground.player.z>30&&game.ground.lives===2));await read(page,({game})=>{const g=game.ground;g.lives=1;g.player.immune=0;g.damage(100);});await wait(page,()=>window.__game.state==='over');await page.locator('#start').click();assert(await read(page,({game})=>game.wave===1&&game.lives===3&&game.score===0));
   assert.deepEqual(errors,[]);report.results.push({engine:name,version:browser.version(),viewport,reduced:process.env.PORTAL_REDUCED==='1',observed:'PASS',diagnostics,frameMs:{p50:frameMs[30],p95:frameMs[57],max:frameMs[59]},playtest:'Playtest: synthetic 11-pilot setup; real last-pilot F shot and duplicate scoring guard; shot against portal; normal-speed keyboard or simultaneous touch move/look crossing; no automatic pointer lock; denied-lock resume; retained resources; walk to resistance contact/newspaper, actual occupation-guard projectile, real guard kill and three district objectives; chapter end; repeat run failure/cancel/successful retry, pause during loading, context recovery or rotation, reduced graphics/motion, restart loading, destination death and campaign restart.',limitations:'Injected camera yaw for route steering; synthetic immunity isolates workflow. Failure, damage and context loss fixtures are explicit. Headless software browsers and synthetic touch events do not establish physical phone/GPU performance.'});
   const video=page.video();await context.close();if(video)await video.saveAs('dashboard/evidence/portal-journey.webm');
  }
  await browser.close();
 }
 fs.writeFileSync(`dashboard/evidence/portal-browser-report-${process.env.PORTAL_ENGINE||'all'}-${process.env.PORTAL_TOUCH||'0'}${process.env.PORTAL_LANDSCAPE==='1'?'-landscape':''}.json`,JSON.stringify(report,null,2)+'\n');console.log(report.results.map(r=>r.engine+' '+r.viewport.width+': '+r.observed).join('\n'));
})().catch(e=>{console.error(e);process.exit(1);});
