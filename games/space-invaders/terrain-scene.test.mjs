import {test} from 'node:test';
import assert from 'node:assert/strict';
import {buildTerrainScene} from './terrain-scene.mjs';
import {Planet, BIOMES} from './terrain.mjs';
test('serializable scene preserves cavities, cover, safe objectives and edited geology', () => {
  const config = {seed: 'glass-sea'}, view = {cols: 24, rows: 16, step: 3, centerX: -100, centerZ: 100};
  const a = buildTerrainScene(config, view), b = buildTerrainScene(config, view);
  assert.deepEqual(a.columns, b.columns); assert.deepEqual(a.landmarks, b.landmarks);
  assert.equal(a.stats.seamMismatches, 0); assert.equal(a.stats.seamsChecked, 34);
  assert.equal(a.stats.inaccessibleObjectives, 0); assert.equal(a.stats.openColumns + a.stats.coverColumns, 24 * 16);
  const c = a.columns.find(c => !c.route && c.spans.some(s => s.top === c.height)); assert(c);
  const key = `${c.x},${c.height},${c.z}`, edited = buildTerrainScene(config, view, [key]);
  const e = edited.columns.find(e => e.x === c.x && e.z === c.z);
  assert(!e.spans.some(s => s.bottom <= c.height && s.top >= c.height));
  const p = new Planet(config);
  const before = c.spans.find(s => s.bottom <= c.height - 1 && s.top >= c.height - 1)?.material || 'air';
  assert.equal(before, p.material(c.x, c.height - 1, c.z));
  assert.equal(e.spans.find(s => s.bottom <= c.height - 1 && s.top >= c.height - 1)?.material || 'air', before);
  assert.doesNotThrow(() => JSON.stringify(edited));
});
test('biome profiles differ in palette, height and formations', () => {
  const profiles = [];
  for (let i = 0; i < 4; i++) {
    const config = {seed: 'glass-sea', biomes: BIOMES.map((_, j) => Number(i === j))};
    const p = new Planet(config), heights = [], kinds = new Set();
    for (let x = -256; x <= 256; x += 16) for (let z = -256; z <= 256; z += 16) {
      const s = p.surface(x, z); assert.equal(s.biome, BIOMES[i]); heights.push(s.height);
      for (const f of s.features) kinds.add(f.type);
    }
    profiles.push({height: heights.reduce((a, b) => a + b) / heights.length, colors: p.config.palette[BIOMES[i]], kinds});
  }
  assert(profiles[0].height > profiles[2].height + 8);
  assert(profiles[1].kinds.has('crystal')); assert(profiles[2].kinds.has('fungus'));
  assert(profiles[3].kinds.has('meteorite')); assert(profiles[0].kinds.has('fissure'));
  assert.equal(new Set(profiles.map(p => p.colors[0])).size, 4);
});
