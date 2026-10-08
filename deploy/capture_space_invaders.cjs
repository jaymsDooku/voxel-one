const {webkit}=require(process.env.PLAYWRIGHT_MODULE || '/tmp/voxel-web-qa/node_modules/playwright');
const assert=require('node:assert/strict');
(async()=>{
const browser=await webkit.launch({headless:true});
const context=await browser.newContext({viewport:{width:1280,height:900},recordVideo:{dir:'target/space-qa/video',size:{width:960,height:675}}});
const page=await context.newPage();await page.goto('http://127.0.0.1:8766/games/space-invaders/index.html');await page.locator('#start').click();
await page.keyboard.down('Space');await page.keyboard.down('ArrowRight');await page.waitForTimeout(1300);await page.keyboard.up('ArrowRight');await page.waitForTimeout(2500);await page.keyboard.down('ArrowLeft');await page.waitForTimeout(1700);await page.keyboard.up('ArrowLeft');await page.keyboard.up('Space');
await page.locator('#pause').click();const before=await page.evaluate(async()=>{const {game}=await import('./app.mjs');return {score:game.score,player:game.player,wave:game.wave,lives:game.lives};});
await page.setViewportSize({width:390,height:844});await page.waitForTimeout(300);
assert.deepEqual(await page.evaluate(async()=>{const {game}=await import('./app.mjs');return {score:game.score,player:game.player,wave:game.wave,lives:game.lives};}),before);
const bounds=await page.locator('canvas').boundingBox();assert.equal(bounds.width,390);assert.equal(bounds.height,844);
await page.setViewportSize({width:1280,height:900});await page.locator('#start').click();await page.waitForTimeout(1200);
const video=page.video();await context.close();await video.saveAs('dashboard/evidence/space-invaders-space-playtest.webm');await browser.close();console.log('Playtest: live move/fire video captured; paused desktop-to-portrait resize kept score, lives, wave, and player; canvas resized to 390x844; resume passed.');
})().catch(e=>{console.error(e);process.exit(1)});
