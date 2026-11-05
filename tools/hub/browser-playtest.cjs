const {webkit}=require('/tmp/voxel-web-qa/node_modules/playwright');
const fs=require('fs');
(async()=>{
 const browser=await webkit.launch({headless:false});
 const context=await browser.newContext({viewport:{width:1000,height:650}});
 const page=await context.newPage(); const url='https://voxel-one.jamesleaver1.chatgpt.site/games/space-invaders/';
 const response=await page.goto(url,{waitUntil:'domcontentloaded',timeout:30000});
 // Never retain authentication page content, cookies, storage, redirects or screenshots.
 const start=page.locator('#start');
 if(await start.count()===0){fs.writeFileSync('dashboard/evidence/hub-browser-result.json',JSON.stringify({playtest:'Playtest: open catalog Space Invaders HTTPS URL in fresh desktop WebKit context.',url,observed:'Game start control unavailable. Published game playtest blocked by site access or unavailable route.',passed:false},null,2));await browser.close();return;}
 await start.click();await page.keyboard.down('Space');await page.keyboard.down('ArrowRight');await page.waitForTimeout(1500);await page.keyboard.up('ArrowRight');await page.keyboard.up('Space');
 await page.locator('#pause').click();await page.screenshot({path:'dashboard/evidence/hub-published-space-invaders.png'});
 await start.click();await page.waitForTimeout(500);await page.reload({waitUntil:'domcontentloaded'});
 if(await start.count()===0)throw new Error('Previously played published game did not reopen');
 fs.writeFileSync('dashboard/evidence/hub-browser-result.json',JSON.stringify({playtest:'Playtest: catalog published URL; start; move/fire; pause/resume; reload a previously played game.',url,status:response.status(),passed:true,limitation:'No website-only deployment occurred in this task. Cross-deployment freshness remains untested.'},null,2));await browser.close();
})().catch(e=>{console.error(e.name + ": " + e.message.split("\n")[0]);process.exit(1)});
