const {webkit}=require('/tmp/voxel-web-qa/node_modules/playwright');
const assert=require('assert/strict');const fs=require('fs');const path=require('path');const http=require('http');const cp=require('child_process');const crypto=require('crypto');
(async()=>{
 const root=process.cwd(), work=path.join(root,'target/hub-web-freshness');fs.mkdirSync(work,{recursive:true});
 const source=path.join(work,'source');fs.cpSync('games/space-invaders',source,{recursive:true});
 const build=output=>cp.execFileSync('python3',['tools/hub/fingerprint_web.py','--source',source,'--output',output],{encoding:'utf8'}).trim();
 const first=path.join(work,'first'),second=path.join(work,'second');const version1=build(first);
 const client='target/voxel-one-1.0-SNAPSHOT-client.jar';const nativeBefore=crypto.createHash('sha256').update(fs.readFileSync(client)).digest('hex');
 let current=first;const server=http.createServer((req,res)=>{
  const parsed=new URL(req.url,'http://localhost');let relative=parsed.pathname.replace(/^\/games\/space-invaders\//,'');if(relative==='')relative='index.html';
  const file=path.resolve(current,relative);if(!file.startsWith(current+path.sep)){res.writeHead(403);res.end();return;}
  if(!fs.existsSync(file)){res.writeHead(404);res.end();return;}
  res.setHeader('Content-Type',file.endsWith('.mjs')?'text/javascript':file.endsWith('.css')?'text/css':'text/html');
  res.setHeader('Cache-Control','public, max-age=31536000, immutable');res.end(fs.readFileSync(file));
 });await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
 let browser;try{
  browser=await webkit.launch({headless:false});const context=await browser.newContext({viewport:{width:1000,height:650}});const page=await context.newPage();const errors=[];page.on('pageerror',()=>errors.push('page error'));
  const url='http://127.0.0.1:'+server.address().port+'/games/space-invaders/';
  await page.goto(url+'?build='+version1);await page.locator('#start').click();await page.keyboard.down('Space');await page.keyboard.down('ArrowRight');await page.waitForTimeout(1200);await page.keyboard.up('ArrowRight');await page.keyboard.up('Space');await page.locator('#pause').click();
  const oldModule=await page.locator('script[type=module]').getAttribute('src');assert(oldModule.includes(version1));
  fs.appendFileSync(path.join(source,'app.mjs'),'\n// Cache integration fixture: second web build.\n');const version2=build(second);assert.notEqual(version1,version2);current=second;
  await page.goto(url+'?build='+version2);const newModule=await page.locator('script[type=module]').getAttribute('src');assert(newModule.includes(version2));assert.notEqual(oldModule,newModule);
  await page.locator('#start').click();await page.keyboard.down('Space');await page.waitForTimeout(800);await page.keyboard.up('Space');await page.locator('#pause').click();await page.screenshot({path:'dashboard/evidence/hub-web-freshness.png'});await page.locator('#start').click();await page.waitForTimeout(300);
  assert.deepEqual(errors,[]);const nativeAfter=crypto.createHash('sha256').update(fs.readFileSync(client)).digest('hex');assert.equal(nativeBefore,nativeAfter);
  fs.writeFileSync('dashboard/evidence/hub-web-freshness.json',JSON.stringify({playtest:'Playtest: play the first fingerprinted build in desktop WebKit; move/fire and pause; replace hosted files with a second web-only build; reopen in the same browser context through its new build URL; start/fire/pause/resume.',expected:'Fresh HTML URL and fresh ESM filenames bypass primed immutable caches; native artifact stays unchanged.',observed:'Both builds played; module URLs changed; pause/resume worked; no page errors; native client SHA-256 stayed unchanged.',versions:[version1,version2],nativeClientSha256:nativeAfter,environment:'Linux desktop WebKit; assigned inherited display; fresh synthetic browser context; localhost static host deliberately uses one-year immutable caching.',limitation:'Local hosting integration only. No production website deployment or owner-private sign-in was performed.'},null,2));
 }finally{if(browser)await browser.close();await new Promise(resolve=>server.close(resolve));}
})().catch(e=>{console.error(e.name+': '+e.message.split('\n')[0]);process.exit(1)});
