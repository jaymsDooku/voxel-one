// Absolute voxel coordinates determine all geometry. Chunks never own random state.
export const SEEDS = ['acheron', 'glass-sea', 'silent-impact', 'fungal-moon'];
export const BIOMES = ['obsidian', 'crystal', 'toxic', 'impact'];
export const PALETTES = {
  obsidian: ['#262936', '#595269', '#ff9b64'],
  crystal: ['#365466', '#80beca', '#b4f3ee'],
  toxic: ['#414f49', '#7c9267', '#c7e87c'],
  impact: ['#9b8876', '#dbc6a3', '#e6d5ba'],
};
export const MATERIAL_COLORS = {
  basalt: '#30333f', shale: '#756f83', rock: '#64717a', mineral: '#bbada0',
  'emissive-vein': '#88d5d5', acid: '#c7e87c', vent: '#ff9b64',
  'radiation-crystal': '#d5b8f3', 'crystal-spire': '#91d4de', fungus: '#b1a3c0',
  meteorite: '#554655', 'glow-cover': '#a7dcb0',
};
export const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
const smooth = t => t * t * (3 - 2 * t);
const distance = (x, z, f) => Math.hypot(x - f.x, z - f.z);
function seedHash(s) {
  let n = 2166136261;
  for (const c of s) n = Math.imul(n ^ c.charCodeAt(0), 16777619);
  return n >>> 0;
}
function freezeFeature(f) { return Object.freeze(f); }
export class Planet {
  constructor(config = {}) {
    const c = {seed: 'acheron', scale: 64, roughness: 1, craterDensity: .65,
      biomes: [1, 1, 1, 1], palette: PALETTES, ...config};
    if (typeof c.seed !== 'string' || !c.seed.length || c.seed.length > 128 ||
        !Number.isFinite(c.scale) || c.scale < 16 || c.scale > 256 ||
        !Number.isFinite(c.roughness) || c.roughness < 0 || c.roughness > 2 ||
        !Number.isFinite(c.craterDensity) || c.craterDensity < 0 || c.craterDensity > 1 ||
        !Array.isArray(c.biomes) || c.biomes.length !== 4 ||
        c.biomes.some(v => !Number.isFinite(v) || v < 0 || v > 10) || !c.biomes.some(v => v > 0)) {
      throw new RangeError('Invalid planet configuration');
    }
    for (const name of BIOMES) {
      if (!Array.isArray(c.palette?.[name]) || c.palette[name].length !== 3 ||
          c.palette[name].some(v => !/^#[0-9a-f]{6}$/i.test(v))) {
        throw new RangeError('Invalid biome palette');
      }
    }
    c.biomes = Object.freeze([...c.biomes]);
    c.palette = Object.freeze(Object.fromEntries(BIOMES.map(n => [n, Object.freeze([...c.palette[n]])])));
    this.config = Object.freeze(c);
    this.seed = seedHash(c.seed);
    this.cache = new Map();
    this.featureCache = new Map();
    this.surfaceCache = new Map();
    this.decorationCache = new Map();
    this.pending = new Map();
    this.edits = new Set();
    this.revision = 0;
    this.metrics = {chunks: 0, milliseconds: 0, maxChunkMilliseconds: 0,
      seamsChecked: 0, seamMismatches: 0, inaccessibleObjectives: 0};
    this.objectives = Object.freeze([
      Object.freeze({x: 0, z: 0, name: 'landing'}),
      Object.freeze({x: 0, z: 96, name: 'relay'}),
      Object.freeze({x: 96, z: 96, name: 'extraction'}),
    ]);
  }
  random(x, z, salt = 0) {
    let h = this.seed ^ Math.imul(x, 374761393) ^ Math.imul(z, 668265263) ^ Math.imul(salt, 1442695041);
    h = Math.imul(h ^ (h >>> 13), 1274126177);
    return ((h ^ (h >>> 16)) >>> 0) / 4294967296;
  }
  noise(x, z, salt = 0) {
    const ix = Math.floor(x), iz = Math.floor(z), u = smooth(x - ix), v = smooth(z - iz);
    const r = (a, b) => this.random(a, b, salt);
    return (r(ix, iz) * (1 - u) + r(ix + 1, iz) * u) * (1 - v) +
      (r(ix, iz + 1) * (1 - u) + r(ix + 1, iz + 1) * u) * v;
  }
  noise3(x, y, z, salt) {
    const iy = Math.floor(y), t = smooth(y - iy);
    return this.noise(x, z, salt + iy * 73) * (1 - t) + this.noise(x, z, salt + (iy + 1) * 73) * t;
  }
  routeDistance(x, z) {
    return Math.min(Math.hypot(x - clamp(x, -8, 8), z),
      Math.hypot(x, z - clamp(z, 0, 96)), Math.hypot(x - clamp(x, 0, 96), z - 96));
  }
  weights(x, z) {
    const scale = this.config.scale * 1.6;
    const w = this.config.biomes.map((v, i) => v * Math.exp(9 * this.noise(x / scale, z / scale, 20 + i)));
    const total = w.reduce((a, b) => a + b, 0);
    return w.map(v => v / total);
  }
  rawHeight(x, z, w = this.weights(x, z)) {
    const s = this.config.scale;
    const wx = x + s * .3 * (this.noise(x / s, z / s, 8) - .5);
    const wz = z + s * .3 * (this.noise(x / s, z / s, 9) - .5);
    const broad = this.noise(wx / (s * 2), wz / (s * 2), 10);
    const ridge = 1 - Math.abs(2 * this.noise(wx / s, wz / s, 11) - 1);
    const chain = 1 - Math.abs(2 * this.noise(wx / (s * .45), wz / (s * 1.7), 13) - 1);
    // Broad plains/basins stay quiet. Obsidian has sharp chains; toxic terrain sinks.
    return 9 + 22 * broad + this.config.roughness *
      ((ridge ** 4 * 12 + chain ** 8 * 19) * (1.1 * w[0] + .4 * w[1] + .25 * w[3]) +
        (this.noise(x / 10, z / 10, 12) - .5) * 2) - 10 * w[2];
  }
  craterCandidates(gx, gz) {
    const out = [];
    for (let a = gx - 2; a <= gx + 2; a++) for (let b = gz - 2; b <= gz + 2; b++) {
      // Small and large independently placed impacts can overlap, including across cells.
      for (let i = 0; i < 2; i++) {
        const x = (a + this.random(a, b, 100 + i)) * 64;
        const z = (b + this.random(a, b, 102 + i)) * 64;
        const desert = this.weights(x, z)[3];
        if (this.random(a, b, 104 + i) < this.config.craterDensity * (.45 + desert * .55)) {
          out.push(freezeFeature({id: `crater:${a}:${b}:${i}`, type: 'crater', x, z,
            r: i ? 18 + this.random(a, b, 106 + i) * 20 : 5 + this.random(a, b, 106 + i) * 9,
            erosion: this.random(a, b, 108 + i)}));
        }
      }
    }
    return out;
  }
  craterDelta(x, z, f) {
    const d = distance(x, z, f) / f.r;
    if (d >= 1.5) return 0;
    const strength = (1 - .65 * f.erosion) * f.r / 3;
    return strength * (-Math.exp(-d * d * 4) + .48 * Math.exp(-(((d - .94) / (.12 + .08 * f.erosion)) ** 2)) +
      .12 * Math.exp(-(((d - 1.2) / .18) ** 2)) + (f.r > 25 ? .34 * Math.exp(-d * d * 60) : 0));
  }
  baseHeight(x, z) {
    let h = this.rawHeight(x, z);
    for (const f of this.craterCandidates(Math.floor(x / 64), Math.floor(z / 64))) h += this.craterDelta(x, z, f);
    return h;
  }
  landmarkCandidate(a, b) {
    if (this.random(a, b, 6) >= .42) return null;
    const x = (a + .2 + .6 * this.random(a, b, 1)) * 80;
    const z = (b + .2 + .6 * this.random(a, b, 2)) * 80;
    const w = this.weights(x, z), biome = BIOMES[w.indexOf(Math.max(...w))];
    const types = {obsidian: ['caldera', 'fissure', 'arch', 'overhang'],
      crystal: ['crystal', 'arch', 'hollow', 'crystal'], toxic: ['fungus', 'hollow', 'fungus', 'arch'],
      impact: ['meteorite', 'arch', 'caldera', 'overhang']}[biome];
    const type = types[Math.floor(this.random(a, b, 7) * types.length)];
    const r = ['arch', 'caldera', 'hollow'].includes(type) ? 14 + this.random(a, b, 8) * 9 : 7 + this.random(a, b, 8) * 6;
    if (this.routeDistance(x, z) <= r + 16) return null;
    return {id: `landmark:${a}:${b}`, type, biome, x, z, r, priority: this.random(a, b, 9)};
  }
  features(x, z) {
    const gx = Math.floor(x / 64), gz = Math.floor(z / 64), key = `${gx},${gz}`;
    if (this.featureCache.has(key)) return this.featureCache.get(key);
    const out = this.craterCandidates(gx, gz), la = Math.floor(gx * 64 / 80), lb = Math.floor(gz * 64 / 80);
    for (let a = la - 2; a <= la + 2; a++) for (let b = lb - 2; b <= lb + 2; b++) {
      const f = this.landmarkCandidate(a, b);
      if (!f) continue;
      let excluded = false;
      for (let da = -1; da <= 1 && !excluded; da++) for (let db = -1; db <= 1; db++) {
        if (!da && !db) continue;
        const neighbor = this.landmarkCandidate(a + da, b + db);
        if (neighbor && distance(f.x, f.z, neighbor) < f.r + neighbor.r + 10 &&
            (neighbor.priority < f.priority || (neighbor.priority === f.priority && neighbor.id < f.id))) {
          excluded = true; break;
        }
      }
      if (!excluded) out.push(freezeFeature({...f, y: Math.round(this.baseHeight(f.x, f.z))}));
    }
    // Canonical search bounds depend solely on the cache cell, including 80-cell edges.
    const result = Object.freeze(out);
    this.featureCache.set(key, result);
    if (this.featureCache.size > 64) this.featureCache.delete(this.featureCache.keys().next().value);
    return result;
  }
  surface(x, z) {
    const key = `${x},${z}`;
    if (this.surfaceCache.has(key)) return this.surfaceCache.get(key);
    const weights = this.weights(x, z), features = this.features(x, z);
    let h = this.rawHeight(x, z, weights);
    for (const f of features) {
      const d = distance(x, z, f) / f.r;
      if (f.type === 'crater') h += this.craterDelta(x, z, f);
      if (f.type === 'caldera' && d < 1.4) h += 14 * Math.exp(-(((d - .85) / .18) ** 2)) - 10 * Math.exp(-d * d * 4);
      if (f.type === 'fissure' && Math.abs(x - f.x + 2 * Math.sin((z - f.z) / 6)) < 2 && Math.abs(z - f.z) < f.r) h -= 15;
    }
    const routeDistance = this.routeDistance(x, z), blend = smooth(clamp((routeDistance - 6) / 12, 0, 1));
    h = 12 + (h - 12) * blend;
    const result = Object.freeze({height: Math.max(3, Math.round(h)), weights: Object.freeze(weights),
      biome: BIOMES[weights.indexOf(Math.max(...weights))], route: routeDistance <= 6, features});
    this.surfaceCache.set(key, result);
    if (this.surfaceCache.size > 16384) this.surfaceCache.delete(this.surfaceCache.keys().next().value);
    return result;
  }
  slope(x, z, s = this.surface(x, z)) {
    return Math.max(Math.abs(this.surface(x + 1, z).height - s.height),
      Math.abs(this.surface(x - 1, z).height - s.height), Math.abs(this.surface(x, z + 1).height - s.height),
      Math.abs(this.surface(x, z - 1).height - s.height));
  }
  vegetation(x, z, s, slope) {
    if (s.route || slope > 2 || s.height < 9 || s.height > 35 || this.noise(x / 18, z / 18, 35) < .63) return null;
    const density = {obsidian: .015, crystal: .16, toxic: .30, impact: .025}[s.biome];
    if (this.random(x, z, 36) > density) return null;
    return {type: s.biome === 'crystal' ? 'crystal' : s.biome === 'toxic' ? 'branch' : 'glow-cover',
      height: s.biome === 'toxic' ? 5 + Math.floor(this.random(x, z, 37) * 7) : 3 + Math.floor(this.random(x, z, 37) * 5)};
  }
  formationMaterial(x, y, z, f) {
    const dx = x - f.x, dz = z - f.z, dy = y - f.y, d = Math.hypot(dx, dz);
    if (d > f.r + 3 || dy < -4 || dy > 48) return 'air';
    if (f.type === 'crystal') {
      // Giant central crystal with a ring of lower clustered mineral spires.
      const spikes = [[0, 0, 34, 4]];
      for (let i = 0; i < 6; i++) spikes.push([Math.cos(i * Math.PI / 3) * 6, Math.sin(i * Math.PI / 3) * 6, 13 + i % 3 * 4, 2]);
      for (const [cx, cz, height, radius] of spikes) {
        if (dy >= 0 && dy < height && Math.hypot(dx - cx - dy * .035, dz - cz) < radius * (1 - dy / height)) {
          return cx === 0 && cz === 0 ? 'radiation-crystal' : 'crystal-spire';
        }
      }
    }
    if (f.type === 'fungus' && dy >= 0 && dy < 19) {
      for (const [cx, cz, height] of [[0, 0, 18], [-5, 2, 11], [4, -3, 13]]) {
        const r = Math.hypot(dx - cx, dz - cz);
        if ((r < 1.7 && dy < height) || (r < 5 && dy >= height - 3 && dy < height) ||
            (dy > 4 && dy < 9 && Math.abs(dz - cz) < 1 && Math.abs(dx - cx - (dy - 4) * .7) < 1.3)) return 'fungus';
      }
    }
    if (f.type === 'meteorite' && dy >= -3 && (dx * dx + dz * dz) / (f.r * f.r) + dy * dy / ((f.r * .8) ** 2) < 1) return 'meteorite';
    if (f.type === 'arch' && Math.abs(dz) < 3 && dy >= -3 && dy < f.r + 4 &&
        Math.abs(Math.hypot(dx, Math.max(0, dy)) - f.r) < 3) return 'rock';
    if (f.type === 'hollow' && dy >= -2 && dy < f.r && Math.abs(Math.hypot(dx, dy * 1.3, dz) - f.r) < 2 &&
        !(Math.abs(dx) < 4 && dz < 0 && dy < 7)) return 'rock';
    if (f.type === 'overhang' && ((Math.abs(dx) < 3 && Math.abs(dz) < 3 && dy < 16) ||
        (Math.abs(dx) < f.r && Math.abs(dz) < f.r * .5 && dy >= 12 && dy < 16))) return 'basalt';
    return 'air';
  }
  cave(x, y, z, s) {
    if (s.route || y < 2 || y >= s.height - 1) return false;
    const region = this.noise(x / 70, z / 70, 40);
    const suitability = s.weights[0] + s.weights[1] + .35 * s.weights[3];
    return region > .53 && suitability > .35 &&
      this.noise3(x / 13, y / 8, z / 13, 41) > .58 && this.noise3(x / 7, y / 6, z / 7, 42) > .44;
  }
  geologicalMaterial(x, y, z, s) {
    if (y < s.height - 2 && this.noise3(x / 18, y / 10, z / 18, 44) > .81 &&
        Math.abs(this.noise3(x / 7, y / 5, z / 7, 45) - .5) < .05) return 'emissive-vein';
    if (y < s.height - 4 && this.random(Math.floor(x / 3), Math.floor(z / 3), Math.floor(y / 3) + 46) > .955) return 'mineral';
    const tilt = Math.floor(this.noise(x / 80, z / 80, 47) * 3);
    return y < s.height - 2 ? ['basalt', 'shale', 'rock'][((Math.floor((y + tilt) / 4) % 3) + 3) % 3] : s.biome;
  }
  material(x, y, z, s = this.surface(x, z), slope = null) {
    if (this.edits.has(`${x},${y},${z}`)) return 'air';
    if (s.route) return y > s.height ? 'air' : this.geologicalMaterial(x, y, z, s);
    if (y <= s.height - 2) return this.cave(x, y, z, s) ? 'air' : this.geologicalMaterial(x, y, z, s);
    for (const f of s.features) {
      if (f.type === 'crater' || f.type === 'caldera' || f.type === 'fissure') continue;
      const material = this.formationMaterial(x, y, z, f);
      if (material !== 'air' && y > s.height - 2) return material;
    }
    if (y > s.height) {
      if (y > s.height + 12) return 'air';
      const key = `${x},${z}`;
      if (!this.decorationCache.has(key)) {
        const levels = new Map(), v = this.vegetation(x, z, s, slope ?? this.slope(x, z, s));
        if (v && v.type !== 'glow-cover') for (let dy = 1; dy <= v.height; dy++) {
          if (v.type === 'crystal') levels.set(s.height + dy, 'crystal-spire');
          else if (dy <= 4 || dy === v.height || dy % 3 === 0) levels.set(s.height + dy, 'fungus');
        }
        if (s.biome === 'toxic') for (const [dx, dz] of [[-1, 0], [1, 0], [0, -1], [0, 1]]) {
          const ns = this.surface(x + dx, z + dz);
          const neighbor = this.vegetation(x + dx, z + dz, ns, this.slope(x + dx, z + dz, ns));
          if (neighbor?.type === 'branch') levels.set(ns.height + neighbor.height - 2, 'fungus');
        }
        this.decorationCache.set(key, levels);
        if (this.decorationCache.size > 4096) this.decorationCache.delete(this.decorationCache.keys().next().value);
      }
      return this.decorationCache.get(key).get(y) || 'air';
    }
    if (this.cave(x, y, z, s)) return 'air';
    if (y === s.height) {
      if (s.biome === 'toxic' && s.height < 17 && this.noise(x / 12, z / 12, 50) > .4) return 'acid';
      if (s.biome === 'obsidian' && this.noise(x / 9, z / 9, 51) > .82) return 'vent';
      if (this.vegetation(x, z, s, slope ?? this.slope(x, z, s))?.type === 'glow-cover') return 'glow-cover';
    }
    return this.geologicalMaterial(x, y, z, s);
  }
  excavate(x, y, z) {
    if (![x, y, z].every(Number.isInteger)) throw new RangeError('Voxel edits need integer coordinates');
    // Essential corridors cannot be damaged by the terrain lab.
    if (this.routeDistance(x, z) <= 6) return false;
    this.edits.add(`${x},${y},${z}`); this.revision++;
    return true;
  }
  chunk(cx, cz, size = 16) {
    if (!Number.isInteger(size) || size < 1 || size > 64 || !Number.isInteger(cx) || !Number.isInteger(cz)) throw new RangeError('Invalid chunk');
    const key = `${cx},${cz},${size}`;
    if (this.cache.has(key)) return this.cache.get(key);
    const start = performance.now(), cells = [];
    // Shared edge samples are included; decoration queries follow base generation.
    for (let z = 0; z <= size; z++) for (let x = 0; x <= size; x++) {
      const wx = cx * size + x, wz = cz * size + z;
      cells.push(Object.freeze({x: wx, z: wz, ...this.surface(wx, wz)}));
    }
    const chunk = Object.freeze({cx, cz, size, cells: Object.freeze(cells)});
    this.cache.set(key, chunk);
    if (this.cache.size > 64) this.cache.delete(this.cache.keys().next().value);
    const milliseconds = performance.now() - start;
    this.metrics.chunks++; this.metrics.milliseconds += milliseconds;
    this.metrics.maxChunkMilliseconds = Math.max(this.metrics.maxChunkMilliseconds, milliseconds);
    return chunk;
  }
  async chunkAsync(cx, cz, size = 16) {
    const key = `${cx},${cz},${size}`;
    if (this.pending.has(key)) return this.pending.get(key);
    const promise = new Promise((resolve, reject) => setTimeout(() => {
      try { resolve(this.chunk(cx, cz, size)); } catch (e) { reject(e); } finally { this.pending.delete(key); }
    }, 0));
    this.pending.set(key, promise); return promise;
  }
  checkSeam(a, b) {
    const map = new Map(a.cells.map(c => [`${c.x},${c.z}`, c]));
    let checked = 0, mismatches = 0;
    for (const c of b.cells) {
      const other = map.get(`${c.x},${c.z}`);
      if (!other) continue;
      checked++;
      if (c.height !== other.height || c.biome !== other.biome || c.weights.some((v, i) => v !== other.weights[i])) mismatches++;
      for (let y = 0; y <= Math.max(c.height, other.height) + 40; y++) {
        if (this.material(c.x, y, c.z, c) !== this.material(other.x, y, other.z, other)) { mismatches++; break; }
      }
    }
    this.metrics.seamsChecked += checked; this.metrics.seamMismatches += mismatches;
    return {checked, mismatches};
  }
  validate() {
    const sampled = new Map();
    const sample = (x, z) => {const key = `${x},${z}`; if (!sampled.has(key)) sampled.set(key, this.surface(x, z)); return sampled.get(key);};
    const clear = (x, z) => {const s = sample(x, z); return this.material(x, s.height, z, s) !== 'air' &&
      !['acid', 'vent', 'radiation-crystal'].includes(this.material(x, s.height, z, s)) &&
      [1, 2, 3, 4].every(n => this.material(x, s.height + n, z, s) === 'air');};
    const queue = clear(0, 0) ? [[0, 0]] : [], visited = new Set(queue.length ? ['0,0'] : []);
    for (let i = 0; i < queue.length; i++) {
      const [x, z] = queue[i];
      for (const [dx, dz] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
        const a = x + dx, b = z + dz, key = `${a},${b}`;
        if (a < -6 || a > 102 || b < -6 || b > 102 || visited.has(key) || this.routeDistance(a, b) > 6) continue;
        if (Math.abs(sample(a, b).height - sample(x, z).height) > 1 || !clear(a, b)) continue;
        visited.add(key); queue.push([a, b]);
      }
    }
    const reachable = this.objectives.map(o => ({...o, reachable: visited.has(`${o.x},${o.z}`)}));
    this.metrics.inaccessibleObjectives = reachable.filter(o => !o.reachable).length;
    return {reachable, visited: visited.size, playerClearance: 4, enemyClearance: 4, maxStep: 1};
  }
}
