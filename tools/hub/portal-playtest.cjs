// Bounded browser regression for restored source-portal-Earth gameplay.
const {webkit}=require(process.env.PLAYWRIGHT_MODULE||'/tmp/voxel-web-qa/node_modules/playwright');
const fs=require('fs'),path=require('path'),http=require('http'),assert=require('assert/strict');
(async()=>{
 const root=path.resolve('website/dist'),out=path.resolve('dashboard/evidence');
 const server=http.createServer((req,res)=>{
  let name=new URL(req.url,'http://localhost').pathname;if(name.endsWith('/'))name+='index.html';
  const file=path.resolve(root,'.'+name);if(!file.startsWith(root+path.sep)||!fs.existsSync(file)){res.writeHead(404);res.end();return;}
  res.setHeader('Content-Type',file.endsWith('.mjs')?'text/javascript':file.endsWith('.css')?'text/css':file.endsWith('.json')?'application/json':'text/html');res.end(fs.readFileSync(file));
 });await new Promise(r=>server.listen(0,'127.0.0.1',r));let browser;
 const report={command:'TMPDIR="$PWD/target/hub-tmp" PLAYWRIGHT_BROWSERS_PATH="$PWD/target/hub-browsers" node tools/hub/portal-playtest.cjs',playtest:'Playtest: restored portal scene; synthetic first11 defeats and camera setup; actual final F shot, opening/ready portal, normal W crossing, Earth arrival and retained resources; pause/resume edge',environment:'Linux software WebKit; fresh synthetic context; loopback public website assets; no production publication or physical GPU claim',passed:false};
 try{
  browser=await webkit.launch({headless:false});const page=await browser.newPage({viewport:{width:1280,height:720}});const errors=[];page.on('pageerror',()=>errors.push('page error'));
  await page.goto(`http://127.0.0.1:${server.address().port}/games/space-invaders/?devScene=portal`);
  await page.evaluate(async()=>{
   window.__hubGame=(await import('./app.mjs')).game;
   document.querySelector('canvas:not(#ground-world)').requestPointerLock=()=>Promise.reject(new DOMException('Synthetic denied capture','NotAllowedError'));
   const g=window.__hubGame,s=g.ground;for(const e of s.enemies.slice(0,-1))s.defeat(e);
   Object.assign(s.enemies.at(-1),{x:.5,y:13,z:16.5,health:34,state:'patrol',clock:100,path:[],home:{x:.5,y:13,z:16.5}});
   Object.assign(s.player,{x:.5,y:13,z:10.5,yaw:0,pitch:0,health:63,ammo:8,immune:100});s.previous={...s.player};s.score=1000;s.lives=2;g.score=1000;g.lives=2;
  });
  await page.locator('#start').click();await page.keyboard.down('KeyF');await page.waitForTimeout(100);await page.keyboard.up('KeyF');
  await page.waitForFunction(()=>window.__hubGame.portal?.state==='ready',null,{timeout:20000});
  const read=()=>page.evaluate(()=>{const g=window.__hubGame;return {state:g.state,wave:g.wave,score:g.score,lives:g.lives,health:g.ground.player.health,ammo:g.ground.player.ammo,portal:g.portal.state};});
  const source=await read();assert.deepEqual(source,{state:'playing',wave:3,score:1075,lives:2,health:63,ammo:7,portal:'ready'});
  await page.screenshot({path:path.join(out,'hub-restored-portal-ready.png')});
  await page.locator('#pause').click();const elapsed=await page.evaluate(()=>window.__hubGame.portal.elapsed);await page.waitForTimeout(300);assert.equal(await page.evaluate(()=>window.__hubGame.portal.elapsed),elapsed);await page.locator('#start').click();
  await page.keyboard.down('KeyW');try{await page.waitForFunction(()=>window.__hubGame.wave===4,null,{timeout:20000});}finally{await page.keyboard.up('KeyW');}
  const arrival=await read();assert.equal(arrival.wave,4);assert.equal(arrival.state,'paused');for(const k of ['score','lives','health','ammo'])assert.equal(arrival[k],source[k]);assert.equal(await page.evaluate(()=>window.__hubGame.portal.commits),1);
  await page.locator('#start').click();await page.waitForTimeout(300);await page.screenshot({path:path.join(out,'hub-restored-earth.png')});assert.deepEqual(errors,[]);
  report.passed=true;report.observed='Final shot retained playing source with ready portal; pause froze it; W crossing arrived at Earth once, paused safely, retained score/lives/health/ammo and resumed without page errors.';report.source=source;report.arrival=arrival;
 }catch(e){report.observed=e.name+': '+e.message.split('\n')[0];throw e;}
 finally{if(browser)await browser.close();await new Promise(r=>server.close(r));fs.writeFileSync(path.join(out,'hub-portal-playtest.json'),JSON.stringify(report,null,2)+'\n');}
})().catch(e=>{console.error(e.name+': '+e.message.split('\n')[0]);process.exit(1);});
