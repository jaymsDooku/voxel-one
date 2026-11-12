// Browser has only a synthetic loopback origin; Sites service access stays server-side.
const {webkit}=require(process.env.PLAYWRIGHT_MODULE||'/tmp/voxel-web-qa/node_modules/playwright');
const assert=require('assert/strict');const fs=require('fs');
(async()=>{
 const url=process.argv[2];
 if(!/^http:\/\/127\.0\.0\.1:\d+\/games\/space-invaders\/$/.test(url))throw new Error('Expected scoped game proxy');
 fs.writeFileSync('dashboard/evidence/hub-hosted-web-playtest.json',JSON.stringify({passed:false,observed:'Started; browser validation has not completed.'},null,2)+'\n');
 const browser=await webkit.launch({headless:false});
 try{
  const context=await browser.newContext({viewport:{width:1000,height:650}});
  const page=await context.newPage();const errors=[];page.on('pageerror',()=>errors.push('page error'));
  const response=await page.goto(url,{waitUntil:'networkidle',timeout:30000});assert.equal(response.status(),200);
  await page.locator('#start').click();await page.waitForFunction(()=>document.querySelector('#overlay').hidden);
  await page.keyboard.down('ArrowRight');await page.keyboard.down('Space');await page.waitForTimeout(1800);await page.keyboard.up('ArrowRight');
  await page.waitForFunction(()=>Number(document.querySelector('#hud').textContent.match(/Score (\d+)/)?.[1])>0,{},{timeout:15000});
  await page.keyboard.up('Space');await page.locator('#pause').click();
  await page.waitForFunction(()=>document.querySelector('#title').textContent==='Paused');
  const hud=await page.locator('#hud').textContent();
  await page.screenshot({path:'dashboard/evidence/hub-hosted-space-invaders.png'});
  await page.locator('#start').click();await page.waitForFunction(()=>document.querySelector('#overlay').hidden);
  await page.reload({waitUntil:'networkidle'});await page.locator('#start').click();
  await page.waitForFunction(()=>document.querySelector('#overlay').hidden);assert.deepEqual(errors,[]);
  fs.writeFileSync('dashboard/evidence/hub-hosted-web-playtest.json',JSON.stringify({
   playtest:'Playtest: actual deployed game via bounded service-authenticated proxy; Start; move/fire until score increases; pause/resume; reload and start again.',
   expected:'Rendered game responds, score advances, pause/resume and same-build reload work.',observed: hud+'; all assertions passed; no page errors.',
   environment:'Desktop WebKit on inherited Linux display; isolated synthetic browser context; no stored credentials.',passed:true,
   limitations:'Registered service view only, not normal visitor sign-in. No new deployment, cross-deployment freshness, or catalog promotion claim.'},null,2)+'\n');
  console.log('Playtest: deployed service-view game started, movement/fire input sent, score advanced, pause/resume and reload passed.');
 }finally{await browser.close();}
})().catch(error=>{console.error('Hosted browser check failed: '+error.name);process.exit(1)});
