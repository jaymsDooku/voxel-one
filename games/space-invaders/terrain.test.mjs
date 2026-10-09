import {test} from 'node:test';
import assert from 'node:assert/strict';
import {Planet,SEEDS} from './terrain.mjs';
test('fixed gallery: generation order, negative borders, async parity and objective clearance',async()=>{
 for(const seed of SEEDS){const a=new Planet({seed}),b=new Planet({seed});const left=a.chunk(-1,0),right=a.chunk(0,0);b.chunk(0,0);assert.deepEqual(left,b.chunk(-1,0));assert.deepEqual(right,await b.chunkAsync(0,0));for(let z=0;z<=16;z++)assert.deepEqual(left.cells[z*17+16],right.cells[z*17]);assert(a.validate().reachable.every(o=>o.reachable));for(const o of a.objectives){const s=a.surface(o.x,o.z);assert.equal(s.height,12);for(let y=13;y<17;y++)assert.equal(a.material(o.x,y,o.z,s),'air');}}
});
test('configuration changes worlds, rejects invalid settings and preserves geology on repeat sampling',()=>{assert.throws(()=>new Planet({scale:0}));assert.throws(()=>new Planet({biomes:[0,0,0,0]}));const a=new Planet(),b=new Planet({seed:'other',roughness:2});assert.notDeepEqual(a.chunk(3,4),b.chunk(3,4));for(let y=0;y<40;y++)assert.equal(a.material(91,y,-23),new Planet().material(91,y,-23));});
test('biome selection and sparse bounded cache',()=>{for(let i=0;i<4;i++){const p=new Planet({biomes:[0,0,0,0].map((_,j)=>Number(i===j))});assert.equal(p.surface(53,78).weights[i],1);}const p=new Planet();for(let i=0;i<70;i++)p.chunk(i,0,1);assert.equal(p.cache.size,64);console.log(JSON.stringify({benchmark:'70 cold 1-voxel chunks',milliseconds:p.metrics.milliseconds}));});

test('canonical feature caches do not change samples at 64/80 grid intersections', () => {
  for (const seed of SEEDS) {
    const forward = new Planet({seed}), reverse = new Planet({seed});
    const points = [[63, 79], [64, 80], [79, 64], [80, 64], [-1, -81], [-64, -80], [127, 159]];
    const expected = points.map(([x, z]) => forward.surface(x, z));
    for (const [x, z] of [...points].reverse()) reverse.surface(x, z);
    for (const [i, [x, z]] of points.entries()) assert.deepEqual(reverse.surface(x, z), expected[i]);
    for (const [cx, cz] of [[-5, -4], [3, 4], [4, 5], [0, 0]]) {
      const a = forward.chunk(cx, cz), b = forward.chunk(cx + 1, cz), c = forward.chunk(cx, cz + 1);
      assert.deepEqual(forward.checkSeam(a, b), {checked: 17, mismatches: 0});
      assert.deepEqual(forward.checkSeam(a, c), {checked: 17, mismatches: 0});
    }
  }
});

test('landmarks have biome suitability, exclusion and fixed world-space hollow volumes', () => {
  const p = new Planet(), found = new Map();
  for (let x = -480; x <= 480; x += 64) for (let z = -480; z <= 480; z += 64) {
    for (const f of p.features(x, z)) if (f.type !== 'crater') found.set(f.id, f);
  }
  const features = [...found.values()];
  assert(features.length > 10);
  for (const [i, f] of features.entries()) {
    assert(p.routeDistance(f.x, f.z) > f.r + 16);
    for (const other of features.slice(i + 1)) assert(Math.hypot(f.x - other.x, f.z - other.z) >= f.r + other.r + 10);
    if (f.type === 'crystal') assert.equal(f.biome, 'crystal');
    if (f.type === 'fungus') assert.equal(f.biome, 'toxic');
  }
  for (const type of ['crystal', 'fungus', 'arch', 'hollow', 'overhang', 'caldera', 'meteorite', 'fissure']) {
    assert(features.some(f => f.type === type), `missing ${type}`);
  }
  const arch = features.find(f => f.type === 'arch'), hollow = features.find(f => f.type === 'hollow');
  assert.equal(p.formationMaterial(arch.x, arch.y + arch.r, arch.z, arch), 'rock');
  assert.equal(p.formationMaterial(arch.x, arch.y + arch.r / 2, arch.z, arch), 'air');
  assert.equal(p.formationMaterial(hollow.x, hollow.y + 5, hollow.z, hollow), 'air');
  assert.equal(p.formationMaterial(hollow.x + hollow.r, hollow.y, hollow.z, hollow), 'rock');
});

test('excavation changes only selected voxels, keeps strata and protects objectives', () => {
  const p = new Planet();
  const x = -96, z = 33, y = p.surface(x, z).height;
  const below = p.material(x, y - 1, z), adjacent = p.material(x + 1, y, z);
  assert(p.excavate(x, y, z));
  assert.equal(p.material(x, y, z), 'air');
  assert.equal(p.material(x, y - 1, z), below);
  assert.equal(p.material(x + 1, y, z), adjacent);
  assert.equal(p.excavate(0, 12, 0), false);
  assert(p.validate().reachable.every(o => o.reachable));
  const base = p.material.bind(p);
  p.material = (x, y, z, ...args) => x === 0 && z === 0 ? 'air' : base(x, y, z, ...args);
  assert.equal(p.validate().reachable.some(o => o.reachable), false);
  assert.equal(p.metrics.inaccessibleObjectives, 3);
});

test('craters have multiple sizes, rims, central peaks, erosion and overlaps', () => {
  const p = new Planet({craterDensity: 1, biomes: [0, 0, 0, 1]});
  const craters = p.features(500, 500).filter(f => f.type === 'crater');
  assert(craters.some(f => f.r < 15)); assert(craters.some(f => f.r > 25));
  assert(craters.some((a, i) => craters.slice(i + 1).some(b => Math.hypot(a.x - b.x, a.z - b.z) < a.r + b.r)));
  for (const f of craters) {
    assert(p.craterDelta(f.x, f.z, f) < 0);
    assert(p.craterDelta(f.x + f.r * .94, f.z, f) > 0);
    assert(Math.abs(p.craterDelta(f.x, f.z, {...f, erosion: 1})) < Math.abs(p.craterDelta(f.x, f.z, {...f, erosion: 0})));
  }
  const f = craters.find(f => f.r > 25);
  assert(p.craterDelta(f.x, f.z, f) > p.craterDelta(f.x + f.r * .16, f.z, f));
});

test('vegetation obeys biome, slope, altitude and route constraints', () => {
  const p = new Planet({biomes: [0, 0, 1, 0]}); let plants = 0;
  for (let x = -256; x <= 256; x += 4) for (let z = -256; z <= 256; z += 4) {
    const s = p.surface(x, z), slope = p.slope(x, z, s), v = p.vegetation(x, z, s, slope);
    if (v) {plants++; assert.equal(v.type, 'branch'); assert(slope <= 2 && s.height >= 9 && s.height <= 35);}
    assert.equal(p.vegetation(x, z, {...s, route: true}, slope), null);
    assert.equal(p.vegetation(x, z, {...s, height: 80}, slope), null);
    assert.equal(p.vegetation(x, z, s, 3), null);
  }
  assert(plants > 0);
});
