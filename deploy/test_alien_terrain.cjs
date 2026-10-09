const {webkit, chromium} = require(process.env.PLAYWRIGHT_MODULE || '/tmp/voxel-web-qa/node_modules/playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const {spawn} = require('node:child_process');
// Own the loopback server for this check. Do not depend on a lingering shell session.
const server = spawn('python3', ['-u', '-c',
  "from http.server import ThreadingHTTPServer, SimpleHTTPRequestHandler\nserver = ThreadingHTTPServer(('127.0.0.1', 0), SimpleHTTPRequestHandler)\nprint(server.server_address[1], flush=True)\nserver.serve_forever()"],
  {cwd: path.resolve(__dirname, '..'), stdio: ['ignore', 'pipe', 'ignore']});
process.once('exit', () => server.kill());
let base;
const output = 'dashboard/evidence';
async function ready(page) {
  await page.waitForFunction(() => document.querySelector('#planet-lab').dataset.ready === 'true', {timeout: 30000});
}
async function snap(page, name) {
  await page.waitForTimeout(100);
  await page.screenshot({path: path.join(output, name)});
}
(async () => {
  const port = await new Promise((resolve, reject) => {
    const timeout = setTimeout(() => reject(new Error('Local terrain server did not start')), 5000);
    server.once('error', e => {clearTimeout(timeout); reject(e);});
    server.stdout.once('data', data => {clearTimeout(timeout); const value = Number(String(data).trim());
      if (Number.isInteger(value) && value > 0) resolve(value); else reject(new Error('Invalid local server port'));});
  });
  base = `http://127.0.0.1:${port}/games/space-invaders/`;
  const results = [];
  for (const [name, type] of [['chromium', chromium], ['webkit', webkit]]) {
    const browser = await type.launch({headless: true});
    const context = await browser.newContext({viewport: {width: 1280, height: 900}});
    const page = await context.newPage(), errors = [];
    assert.deepEqual(page.viewportSize(), {width: 1280, height: 900});
    page.on('pageerror', e => errors.push(e.message));
    await page.goto(base); await ready(page);
    assert.equal(await page.locator('#planet-lab').getAttribute('data-backend'), 'module Worker');
    await page.locator('#start').click();
    await page.evaluate(async () => {const {game} = await import('./app.mjs'); game.wave = 3; game.newWave(); game.invulnerable = 1000; game.enemyClock = 1000;});
    await snap(page, `alien-terrain-${name}-world.png`);
    await page.locator('summary').click();
    // Edge: invalid configuration leaves the last valid world intact.
    const initial = await page.evaluate(async () => (await import('./terrain-view.mjs')).terrainScene.config.seed);
    await page.locator('[name=scale]').fill('0'); await page.locator('[name=generate]').click(); await ready(page);
    assert.equal(await page.locator('#planet-lab [role=status]').innerText(), 'Invalid planet configuration');
    assert.equal(await page.evaluate(async () => (await import('./terrain-view.mjs')).terrainScene.config.seed), initial);
    await page.locator('[name=scale]').fill('64');
    const gallery = [], biomes = [];
    for (const seed of ['acheron', 'glass-sea', 'silent-impact', 'fungal-moon']) {
      await page.locator('[name=gallery]').selectOption(seed); await page.locator('[name=generate]').click(); await ready(page);
      assert.equal(await page.locator('#planet-lab [role=status]').innerText(), 'All 3 objectives reachable');
      const data = await page.evaluate(async () => {const {terrainScene} = await import('./terrain-view.mjs'); return {seed: terrainScene.config.seed, cols: terrainScene.view.cols, rows: terrainScene.view.rows, ...terrainScene.stats};});
      assert.equal(data.seamMismatches, 0); assert.equal(data.seamsChecked, 34);
      assert.equal(data.inaccessibleObjectives, 0); assert(data.openColumns > data.coverColumns);
      gallery.push(data);
      await page.locator('summary').click(); await snap(page, `alien-terrain-${name}-${seed}.png`); await page.locator('summary').click();
    }
    const extremes = [];
    for (const scale of [16, 256]) {
      await page.locator('[name=scale]').fill(String(scale));
      await page.locator('[name=roughness]').fill('2'); await page.locator('[name=craterDensity]').fill('1');
      await page.locator('[name=palette]').selectOption('cold'); await page.locator('[name=generate]').click(); await ready(page);
      const edge = await page.evaluate(async () => {const {terrainScene: s} = await import('./terrain-view.mjs'); return {config: s.config, view: s.view, stats: s.stats};});
      assert.equal(edge.config.scale, scale); assert.equal(edge.config.roughness, 2); assert.equal(edge.config.craterDensity, 1);
      assert.equal(edge.config.palette.obsidian[0], '#252f46'); assert.equal(edge.stats.inaccessibleObjectives, 0); assert.equal(edge.stats.seamMismatches, 0);
      extremes.push(edge);
    }
    await page.locator('[name=scale]').fill('64'); await page.locator('[name=roughness]').fill('1');
    await page.locator('[name=craterDensity]').fill('0.65'); await page.locator('[name=palette]').selectOption('geological');
    // Distinct biome silhouettes and palettes in the actual running renderer.
    for (const [i, biome] of ['obsidian', 'crystal', 'toxic', 'impact'].entries()) {
      for (let j = 0; j < 4; j++) await page.locator(`[name=biome${j}]`).fill(String(Number(i === j)));
      await page.locator('[name=generate]').click(); await ready(page);
      const profile = await page.evaluate(async () => {const {terrainScene: s} = await import('./terrain-view.mjs'); return {stats: s.stats, columns: s.columns.length};});
      assert.equal(profile.stats.biomes[biome], profile.columns); assert.equal(profile.stats.inaccessibleObjectives, 0);
      biomes.push({biome, ...profile.stats});
      // Survey a suitable signature feature so its silhouette can be inspected.
      const landmark = await page.locator('[name=landmarks] option').evaluateAll((options, preferred) =>
        options.find(o => o.textContent.startsWith(preferred))?.value, {obsidian: 'caldera', crystal: 'crystal', toxic: 'fungus', impact: 'meteorite'}[biome]);
      assert(landmark, `no suitable landmark for ${biome}`);
      await page.locator('[name=landmarks]').selectOption(landmark); await ready(page);
      await page.locator('summary').click(); await snap(page, `alien-terrain-${name}-${biome}.png`); await page.locator('summary').click();
      await page.locator('[name=centerX]').fill('0'); await page.locator('[name=centerZ]').fill('66');
    }
    for (let i = 0; i < 4; i++) await page.locator(`[name=biome${i}]`).fill('1');
    await page.locator('[name=seed]').fill('glass-sea'); await page.locator('[name=generate]').click(); await ready(page);
    for (const mode of ['elevation', 'biome', 'slope', 'cave', 'traversal']) {
      await page.locator('[name=overlay]').selectOption(mode);
      assert.equal(await page.evaluate(async () => (await import('./terrain-view.mjs')).terrainMode), mode);
      await snap(page, `alien-terrain-${name}-${mode}-overlay.png`);
    }
    await page.locator('[name=overlay]').selectOption('none');
    // Cutaway and a real canvas click remove one voxel. Its neighbor stays unchanged.
    await page.locator('[name=centerX]').fill('-100'); await page.locator('[name=centerZ]').fill('100');
    await page.locator('[name=cutaway]').check(); await ready(page);
    const edit = await page.evaluate(async () => {
      const {terrainScene: s, planetWorld: w} = await import('./terrain-view.mjs');
      const c = s.columns.find(c => c.row === Math.floor(s.view.rows / 2) && !c.route && c.height > 4 && c.spans.some(span => span.bottom <= c.height - 3 && span.top >= c.height - 3));
      const y = c.height - 3, rect = document.querySelector('canvas').getBoundingClientRect();
      return {x: c.x, z: c.z, y, screenX: (c.col + .5) / s.view.cols * rect.width, screenY: rect.height * .93 - (y + .5) * rect.height * .009, below: w.material(c.x, y - 1, c.z)};
    });
    await page.locator('summary').click();
    await page.mouse.click(edit.screenX, edit.screenY);
    await page.waitForFunction(async ({x, y, z}) => (await import('./terrain-view.mjs')).planetWorld.material(x, y, z) === 'air', edit);
    await page.waitForFunction(async ({x, y, z}) => {const {terrainScene: s} = await import('./terrain-view.mjs'); const c = s.columns.find(c => c.x === x && c.z === z); return c && !c.spans.some(span => span.bottom <= y && span.top >= y);}, edit);
    assert.equal(await page.evaluate(async ({x, y, z}) => (await import('./terrain-view.mjs')).planetWorld.material(x, y - 1, z), edit), edit.below);
    await snap(page, `alien-terrain-${name}-excavation.png`);
    // Latest request wins: an older worker result must not replace newer settings.
    const latest = await page.evaluate(async () => {
      const {configurePlanet} = await import('./terrain-view.mjs');
      await Promise.all([configurePlanet({seed: 'older'}, {cols: 12, rows: 8, step: 3, centerX: 0, centerZ: 66}), configurePlanet({seed: 'newer'}, {cols: 12, rows: 8, step: 3, centerX: 0, centerZ: 66})]);
      return (await import('./terrain-view.mjs')).terrainScene.config.seed;
    });
    assert.equal(latest, 'newer');
    await page.locator('summary').click(); await page.locator('[name=cutaway]').uncheck(); await ready(page);
    await page.locator('[name=centerX]').fill('0'); await page.locator('[name=centerZ]').fill('66');
    await page.locator('[name=generate]').click(); await ready(page); await page.locator('summary').click();
    const responsiveness = await page.evaluate(async () => {
      const view = await import('./terrain-view.mjs');
      let frames = 0, last = performance.now(), gaps = [], running = true;
      const tick = now => {if (!running) return; frames++; gaps.push(now - last); last = now; requestAnimationFrame(tick);};
      requestAnimationFrame(tick);
      const start = performance.now();
      const scene = await view.configurePlanet({seed: 'glass-sea'}, {cols: 72, rows: 48, step: 3, centerX: 0, centerZ: 66, cutaway: false});
      const workerWallMilliseconds = performance.now() - start;
      running = false;
      const canvas = document.createElement('canvas'); canvas.width = 1280; canvas.height = 900;
      const ctx = canvas.getContext('2d'), draws = [], blits = [];
      const cached = document.createElement('canvas'); cached.width = 1280; cached.height = 900;
      for (let i = 0; i < 30; i++) {const t = performance.now(); view.renderTerrain(ctx, 1280, 900); draws.push(performance.now() - t);}
      view.renderTerrain(cached.getContext('2d'), 1280, 900);
      for (let i = 0; i < 30; i++) {const t = performance.now(); ctx.drawImage(cached, 0, 0); blits.push(performance.now() - t);}
      blits.sort((a, b) => a - b);
      draws.sort((a, b) => a - b); gaps.sort((a, b) => a - b);
      return {workerWallMilliseconds, framesDuringGeneration: frames,
        frameGapP95Milliseconds: gaps[Math.floor(gaps.length * .95)],
        terrainDrawP95Milliseconds: draws[Math.floor(draws.length * .95)], cachedBlitP95Milliseconds: blits[Math.floor(blits.length * .95)], maxChunkMilliseconds: scene.stats.maxChunkMilliseconds};
    });
    assert(responsiveness.framesDuringGeneration >= 3, 'Worker blocked animation');
    const chunkBenchmark = await page.evaluate(async () => {
      const {Planet, SEEDS} = await import('./terrain.mjs');
      const times = [];
      for (const seed of SEEDS) {
        const world = new Planet({seed});
        for (let cx = -2; cx < 2; cx++) for (let cz = -2; cz < 2; cz++) {
          const start = performance.now(); world.chunk(cx, cz, 16); times.push(performance.now() - start);
        }
      }
      times.sort((a, b) => a - b);
      return {chunks: times.length, size: 16, maxMilliseconds: times.at(-1), p95Milliseconds: times[Math.floor(times.length * .95)]};
    });
    let budget = null;
    if (name === 'chromium') {
      const cpu = require('node:os').cpus()[0]?.model;
      assert.equal(process.platform, 'linux'); assert.equal(process.arch, 'x64');
      assert.equal(cpu, 'Intel Core Processor (Haswell, no TSX)');
      assert.equal(browser.version(), '153.0.8010.12');
      assert.equal(chunkBenchmark.chunks, 64); assert.equal(chunkBenchmark.size, 16);
      assert.deepEqual(gallery.map(s => s.seed), ['acheron', 'glass-sea', 'silent-impact', 'fungal-moon']);
      assert(gallery.every(s => s.cols === 72 && s.rows === 48));
      assert.deepEqual(extremes.map(e => e.config.scale), [16, 256]);
      assert(extremes.every(e => e.view.cols === 72 && e.view.rows === 48 && e.config.roughness === 2 && e.config.craterDensity === 1));
      assert(chunkBenchmark.p95Milliseconds <= 8, `Chunk p95 ${chunkBenchmark.p95Milliseconds} ms exceeds 8 ms`);
      for (const sample of [...gallery, ...extremes.map(e => e.stats)]) {
        assert(sample.generationMilliseconds <= 1500, `Survey ${sample.generationMilliseconds} ms exceeds 1500 ms`);
      }
      budget = {result: 'PASS', chunkP95LimitMilliseconds: 8, surveyLimitMilliseconds: 1500,
        scope: 'Linux x64 Haswell reference; Chromium 153.0.8010.12; 1280x900; 16-voxel base chunks and 72x48-column Worker surveys'};
    }
    // Regression: real keyboard/frame loop, pause/resume, and classic restart.
    await page.locator('canvas').click({position: {x: 600, y: 100}});
    await page.keyboard.down('ArrowUp'); await page.keyboard.down('Space');
    await page.waitForFunction(async () => {const {game} = await import('./app.mjs'); return game.playerY < 510 && game.shots.length > 0;});
    await page.keyboard.up('ArrowUp'); await page.keyboard.up('Space');
    await page.locator('#pause').click();
    assert.equal(await page.evaluate(async () => (await import('./app.mjs')).game.state), 'paused');
    await page.locator('#start').click();
    await page.evaluate(async () => (await import('./app.mjs')).game.restart()); await page.waitForTimeout(100);
    assert.equal(await page.locator('canvas').getAttribute('data-scene'), 'space');
    assert.deepEqual(errors, []);
    results.push({browser: name, version: browser.version(), gallery, extremes, biomes, responsiveness, chunkBenchmark, budget, excavation: {x: edit.x, y: edit.y, z: edit.z, neighborPreserved: true}, errors, result: 'PASS'});
    await context.close(); await browser.close();
  }
  fs.writeFileSync(`${output}/alien-terrain-playtest.json`, JSON.stringify({
    command: 'PLAYWRIGHT_BROWSERS_PATH=/tmp/voxel-web-qa/browsers TMPDIR="$PWD/.tmp/browser" node deploy/test_alien_terrain.cjs',
    environment: 'Linux, headless desktop Chromium and WebKit, 1280x900, isolated synthetic profiles, assigned worktree source on an ephemeral loopback HTTP port',
    hardware: {architecture: process.arch, cpu: require('node:os').cpus()[0]?.model},
    playtest: 'Playtest: enter synthetic level 3, regenerate fixed seed gallery, reject scale 0, survey all four biome signatures, view five overlays, excavate by canvas click in geology cutaway, check adjacent geology, race worker requests, fly/fire, pause/resume, restart.',
    expected: 'Deterministic terrain, module Worker generation, zero checked seams, all objectives reachable, biome palettes/silhouettes, safe corridors, edited voxel becomes air with adjacent geology retained, latest scene wins, game controls still work.',
    observed: 'PASS for all listed checks; screenshots require visual inspection.', results,
    limitations: 'Performance limits apply to the specified Chromium reference only. WebKit is a workflow regression check. Canvas call timings do not measure GPU completion.',
  }, null, 2));
  server.kill();
  console.log(JSON.stringify(results.map(r => ({browser: r.browser, result: r.result, gallery: r.gallery.map(g => ({seed: g.seed, milliseconds: g.generationMilliseconds}))}))));
})().catch(e => {console.error(e); process.exit(1);});
