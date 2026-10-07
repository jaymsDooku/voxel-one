// Synthetic WebKit profiles only. No account or personal browser data.
const { webkit } = require(process.env.PLAYWRIGHT_MODULE || '/tmp/voxel-web-qa/node_modules/playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs');

async function checkLayout(page, viewport, state) {
  const geometry = await page.evaluate(() => {
    const rect = selector => {
      const r = document.querySelector(selector).getBoundingClientRect();
      return { x: r.x, y: r.y, width: r.width, height: r.height };
    };
    const panels = Object.fromEntries(['header', '#hud', '.arena', '.controls'].map(s => [s, rect(s)]));
    const targets = [...document.querySelectorAll('button:not(:disabled)')]
      .filter(b => b.getClientRects().length)
      .map(b => {
        const r = b.getBoundingClientRect();
        const top = document.elementFromPoint(r.x + r.width / 2, r.y + r.height / 2);
        return { label: b.textContent, x: r.x, y: r.y, width: r.width, height: r.height, reachable: top === b || b.contains(top) };
      });
    const overlay = document.querySelector('#overlay').hidden ? null :
      Object.fromEntries(['#title', '#message', '#start'].map(s => [s, rect(s)]));
    return { panels, targets, overlay, scrollWidth: document.documentElement.scrollWidth, scrollHeight: document.documentElement.scrollHeight };
  });
  const inside = (r, box, label) => {
    assert(r.width > 0 && r.height > 0, `${state}: ${label} has no size`);
    assert(r.x >= box.x - 1 && r.y >= box.y - 1 && r.x + r.width <= box.x + box.width + 1 && r.y + r.height <= box.y + box.height + 1,
      `${viewport.width}x${viewport.height} ${state}: ${label} outside bounds ${JSON.stringify(r)}`);
  };
  const noOverlap = (entries) => {
    for (let a = 0; a < entries.length; a++) for (let b = a + 1; b < entries.length; b++) {
      const [al, ar] = entries[a], [bl, br] = entries[b];
      const width = Math.min(ar.x + ar.width, br.x + br.width) - Math.max(ar.x, br.x);
      const height = Math.min(ar.y + ar.height, br.y + br.height) - Math.max(ar.y, br.y);
      assert(width <= 1 || height <= 1, `${viewport.width}x${viewport.height} ${state}: ${al} overlaps ${bl}`);
    }
  };
  const screen = { x: 0, y: 0, ...viewport };
  for (const [label, r] of Object.entries(geometry.panels)) inside(r, screen, label);
  noOverlap(Object.entries(geometry.panels));
  for (const target of geometry.targets) {
    inside(target, screen, target.label);
    assert(target.height >= 44 && target.width >= 44, `${state}: ${target.label} touch target too small`);
    assert(target.reachable, `${state}: ${target.label} obscured`);
  }
  if (geometry.overlay) {
    for (const [label, r] of Object.entries(geometry.overlay)) inside(r, geometry.panels['.arena'], label);
    noOverlap(Object.entries(geometry.overlay));
  }
  assert(geometry.scrollWidth <= viewport.width + 1 && geometry.scrollHeight <= viewport.height + 1, `${state}: viewport scroll overflow`);
  if (viewport.width === 568 || viewport.width === 667) {
    await page.screenshot({ path: `dashboard/evidence/space-invaders-${viewport.width}x${viewport.height}-${state}.png` });
  }
  return { viewport, state, observed: 'Panels do not overlap; all controls fit and are reachable; overlay content fits arena; no viewport overflow', geometry };
}

(async () => {
  const browser = await webkit.launch({ headless: true });
  const results = [];
  try {
    for (const viewport of [{ width: 568, height: 320 }, { width: 667, height: 375 }, { width: 844, height: 390 }, { width: 390, height: 844 }]) {
      const context = await browser.newContext({ viewport, isMobile: true, hasTouch: true, deviceScaleFactor: 2 });
      const page = await context.newPage();
      const errors = [];
      page.on('pageerror', e => errors.push(e.message));
      await page.goto('http://127.0.0.1:8766/games/space-invaders/index.html');
      await page.waitForFunction(() => !document.querySelector('#overlay').hidden);
      results.push(await checkLayout(page, viewport, 'start'));
      await page.locator('#start').tap();
      await page.waitForFunction(() => document.querySelector('#overlay').hidden);
      // Exercise held movement and fire through the actual rendered controls.
      await page.locator('[data-control=left]').dispatchEvent('pointerdown', { pointerId: 1, pointerType: 'touch' });
      await page.locator('[data-control=fire]').dispatchEvent('pointerdown', { pointerId: 2, pointerType: 'touch' });
      await page.waitForTimeout(500);
      await page.locator('[data-control=left]').dispatchEvent('pointercancel', { pointerId: 1, pointerType: 'touch' });
      await page.locator('[data-control=fire]').dispatchEvent('pointerup', { pointerId: 2, pointerType: 'touch' });
      assert(await page.evaluate(async () => (await import('./app.mjs')).game.player < 240));
      results.push(await checkLayout(page, viewport, 'playing'));
      await page.locator('#pause').tap();
      await page.waitForFunction(() => document.querySelector('#title').textContent === 'Paused');
      results.push(await checkLayout(page, viewport, 'paused'));
      await page.locator('#start').tap();
      await page.waitForFunction(() => document.querySelector('#overlay').hidden);
      // Deterministic enemy hit, resolved by the running game's collision/update loop.
      await page.evaluate(async () => {
        const { game } = await import('./app.mjs');
        game.lives = 1; game.invulnerable = 0;
        game.enemyShots = [{ x: game.player, y: 515 }];
      });
      await page.waitForFunction(() => document.querySelector('#title').textContent === 'Game over');
      results.push(await checkLayout(page, viewport, 'game-over'));
      await page.locator('#start').tap();
      await page.waitForFunction(() => document.querySelector('#overlay').hidden && document.querySelector('#hud').textContent === 'Score 0 · Lives 3 · Wave 1');
      assert.deepEqual(errors, []);
      await context.close();
    }
    fs.writeFileSync('dashboard/evidence/space-invaders-layout.json', JSON.stringify({ browser: 'Playwright WebKit', syntheticProfiles: true, limitation: 'Mobile browser emulation; physical iOS devices untested', results }, null, 2) + '\n');
    console.log(`Playtest: ${results.length} viewport/state layout checks passed; touch start/move/fire/pause/resume/game-over/restart passed; no page errors.`);
  } finally {
    await browser.close();
  }
})().catch(e => { console.error(e.stack); process.exit(1); });
