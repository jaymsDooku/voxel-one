import {Planet, BIOMES} from './terrain.mjs';
export const VIEW = Object.freeze({cols: 72, rows: 48, step: 3, centerX: 0, centerZ: 66, cutaway: false});
// Pure serializable scene builder shared by the Worker, local fallback and tests.
export function buildTerrainScene(config, requested = {}, edits = []) {
  const started = performance.now(), world = new Planet(config), view = {...VIEW, ...requested};
  if (![view.cols, view.rows, view.step].every(Number.isInteger) || view.cols < 2 || view.cols > 96 ||
      view.rows < 2 || view.rows > 64 || view.step < 1 || view.step > 4 ||
      ![view.centerX, view.centerZ].every(Number.isFinite)) throw new RangeError('Invalid terrain view');
  for (const key of edits) {
    if (typeof key !== 'string' || !/^-?\d+,-?\d+,-?\d+$/.test(key)) throw new RangeError('Invalid voxel edit');
    const [x, y, z] = key.split(',').map(Number);
    world.excavate(x, y, z);
  }
  const columns = [], biomes = Object.fromEntries(BIOMES.map(n => [n, 0]));
  // First pass: all base terrain. Second pass: geology and decorations.
  for (let row = 0; row < view.rows; row++) for (let col = 0; col < view.cols; col++) {
    const x = Math.round(view.centerX + (col - view.cols / 2) * view.step);
    const z = Math.round(view.centerZ + (view.rows / 2 - row) * view.step);
    const s = world.surface(x, z);
    columns.push({x, z, col, row, ...s}); biomes[s.biome]++;
  }
  const landmarks = new Map(), hazards = {acid: 0, vent: 0, 'radiation-crystal': 0};
  let caves = 0, cover = 0, open = 0;
  for (const c of columns) {
    c.slope = world.slope(c.x, c.z, c);
    c.spans = [];
    const maxY = Math.max(c.height + 12, ...c.features.filter(f => Math.hypot(c.x - f.x, c.z - f.z) < f.r + 3).map(f => (f.y ?? 0) + 40));
    // Bedrock to sky: separate spans retain caves, arches, hollows and overhangs.
    for (let y = 0; y <= maxY; y++) {
      const mat = world.material(c.x, y, c.z, c, c.slope);
      if (mat === 'air') { if (y < c.height) caves++; continue; }
      const last = c.spans.at(-1);
      if (last?.material === mat && last.top === y - 1) last.top = y;
      else c.spans.push({bottom: y, top: y, material: mat});
      if (hazards[mat] !== undefined) hazards[mat]++;
    }
    c.cave = c.spans.some((span, i) => i > 0 && span.bottom > c.spans[i - 1].top + 1 && span.bottom < c.height);
    c.surfaceMaterial = world.material(c.x, c.height, c.z, c, c.slope);
    if (c.spans.some(span => span.top > c.height + 1)) cover++; else open++;
    for (const f of c.features) {
      if (f.type !== 'crater' && Math.hypot(c.x - f.x, c.z - f.z) < f.r + 3) landmarks.set(f.id, f);
    }
    delete c.features;
  }
  const validation = world.validate();
  const chunk = world.chunk(Math.floor(view.centerX / 16), Math.floor(view.centerZ / 16));
  world.checkSeam(chunk, world.chunk(chunk.cx + 1, chunk.cz));
  world.checkSeam(chunk, world.chunk(chunk.cx, chunk.cz + 1));
  const stats = {generationMilliseconds: performance.now() - started, ...world.metrics, biomes, hazards,
    caveVoxels: caves, openColumns: open, coverColumns: cover, landmarkCount: landmarks.size};
  return {config: world.config, view, columns, landmarks: [...landmarks.values()], objectives: world.objectives, validation, stats};
}
