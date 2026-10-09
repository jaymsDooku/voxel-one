import {Planet, BIOMES, SEEDS, MATERIAL_COLORS} from './terrain.mjs';
import {buildTerrainScene, VIEW} from './terrain-scene.mjs';
export let planetWorld = new Planet();
export let terrainScene = null;
export let terrainRevision = 0;
export let terrainMode = 'none';
let nextId = 0, worker = null, newest = 0, editing = false;
const pending = new Map(), listeners = new Set();
let currentView = {...VIEW};
const changed = () => {terrainRevision++; for (const f of listeners) f();};
function requestScene(world, view) {
  const id = ++nextId;
  if (typeof Worker === 'undefined') return new Promise((resolve, reject) => setTimeout(() => {
    try { resolve({...buildTerrainScene(world.config, view, [...world.edits]), backend: 'main-thread fallback'}); }
    catch (e) { reject(e); }
  }, 0));
  if (!worker) {
    worker = new Worker(new URL('./terrain-worker.mjs', import.meta.url), {type: 'module'});
    worker.onmessage = ({data}) => {
      const task = pending.get(data.id); if (!task) return;
      pending.delete(data.id);
      if (data.error) task.reject(new Error(data.error));
      else task.resolve({...data.scene, backend: 'module Worker'});
    };
    worker.onerror = () => {
      for (const task of pending.values()) task.reject(new Error('Terrain worker failed. Reload to retry.'));
      pending.clear(); worker.terminate(); worker = null;
    };
  }
  return new Promise((resolve, reject) => {
    pending.set(id, {resolve, reject});
    worker.postMessage({id, config: world.config, view, edits: [...world.edits]});
  });
}
export async function configurePlanet(config = planetWorld.config, view = currentView, reuse = false) {
  const world = reuse instanceof Planet ? reuse : reuse ? planetWorld : new Planet(config), ticket = ++newest;
  const scene = await requestScene(world, view);
  if (ticket !== newest) return null;
  planetWorld = world; currentView = {...scene.view}; terrainScene = scene; changed();
  return scene;
}
export function setTerrainMode(mode) {
  if (!['none', 'elevation', 'biome', 'slope', 'cave', 'traversal'].includes(mode)) throw new RangeError('Invalid overlay');
  terrainMode = mode; changed();
}
function color(material, column, palette) {
  if (MATERIAL_COLORS[material]) return MATERIAL_COLORS[material];
  if (BIOMES.includes(material)) {
    const channels = column.weights.reduce((out, w, i) => out.map((v, j) =>
      v + w * parseInt(palette[BIOMES[i]][0].slice(1 + j * 2, 3 + j * 2), 16)), [0, 0, 0]);
    return `rgb(${channels.map(Math.round).join(',')})`;
  }
  return '#607380';
}
function overlayColor(c) {
  if (terrainMode === 'biome') return terrainScene.config.palette[c.biome][1];
  if (terrainMode === 'elevation') return `hsl(${240 - c.height * 5} 45% 50%)`;
  if (terrainMode === 'slope') return c.slope > 2 ? '#e87e68' : '#77958c';
  if (terrainMode === 'traversal') return c.route ? '#80cfb1' : '#493d56';
  if (terrainMode === 'cave') return c.cave ? '#76ceda' : '#544760';
  return null;
}
function drawSpan(ctx, x, base, cell, vertical, span, c, cutaway) {
  const y = base - (span.top + 1) * vertical, bottom = base - span.bottom * vertical;
  ctx.fillStyle = overlayColor(c) || color(span.material, c, terrainScene.config.palette);
  ctx.fillRect(x, y, cell + .5, Math.max(1, bottom - y));
  if (cutaway) return;
  // Fixed north-west light: lit horizontal faces and dark east faces.
  if (span.top < c.height) return;
  ctx.fillStyle = '#e5e2db30'; ctx.fillRect(x, y, cell, 2);
  ctx.fillStyle = '#11152150'; ctx.fillRect(x + cell * .78, y + 2, cell * .22, Math.max(1, bottom - y - 2));
}
export function planetSky() {
  const palettes = {obsidian: ['#17182f', '#765a78', '#a78787'], crystal: ['#102637', '#5b859a', '#9abcc2'],
    toxic: ['#172b30', '#6b887d', '#afbc92'], impact: ['#201d38', '#a58286', '#d6b699']};
  if (!terrainScene) return palettes.obsidian;
  const dominant = BIOMES.reduce((a, b) => terrainScene.stats.biomes[a] >= terrainScene.stats.biomes[b] ? a : b);
  return palettes[dominant];
}
export function renderTerrain(ctx, width, height) {
  if (!terrainScene) {
    ctx.fillStyle = '#e0dae4'; ctx.font = '12px monospace'; ctx.fillText('Surveying planet…', 12, height * .65); return;
  }
  const scene = terrainScene, {cols, rows, cutaway} = scene.view;
  const cell = width / cols, vertical = cutaway ? height * .009 : height * .0037;
  if (cutaway) {
    ctx.fillStyle = '#111a28'; ctx.fillRect(0, height * .30, width, height * .70);
    const row = Math.floor(rows / 2);
    for (const c of scene.columns.filter(c => c.row === row)) {
      for (const span of c.spans) drawSpan(ctx, c.col * cell, height * .93, cell, vertical, span, c, true);
    }
    ctx.fillStyle = '#e9f4ed'; ctx.font = '12px monospace';
    ctx.fillText(`Geology section z=${scene.view.centerZ} · click to excavate · corridors protected`, 12, height * .34);
  } else {
    const rowHeight = height * .44 / rows;
    for (const c of scene.columns) {
      const base = height * .51 + c.row * rowHeight, x = c.col * cell;
      const groundTop = base - (c.height + 1) * vertical;
      const ground = overlayColor(c) || color(c.biome, c, scene.config.palette);
      ctx.fillStyle = ground; ctx.fillRect(x, groundTop, cell + 1, height - groundTop);
      const near = scene.columns[(c.row + 1) * cols + c.col];
      if (near && c.height > near.height + 2) {
        const floor = near.height + 1;
        ctx.fillStyle = '#252733'; ctx.fillRect(x, groundTop + vertical, cell, (c.height - floor) * vertical);
        for (const span of c.spans) {
          const clipped = {...span, bottom: Math.max(span.bottom, floor), top: Math.min(span.top, c.height - 1)};
          if (clipped.top >= clipped.bottom) drawSpan(ctx, x, base, cell, vertical, clipped, c, false);
        }
      }
      ctx.fillStyle = scene.config.palette[c.biome][1]; ctx.globalAlpha = .18;
      ctx.fillRect(x, groundTop, cell + 1, Math.max(2, rowHeight * .4)); ctx.globalAlpha = 1;
      if (['acid', 'vent', 'glow-cover'].includes(c.surfaceMaterial) && terrainMode === 'none') {
        ctx.fillStyle = MATERIAL_COLORS[c.surfaceMaterial]; ctx.fillRect(x, groundTop, cell, Math.max(2, rowHeight * .45));
      }
      for (const span of c.spans) {
        if (span.top <= c.height) continue;
        const raised = {...span, bottom: Math.max(span.bottom, c.height + 1)};
        drawSpan(ctx, x, base, cell, vertical, raised, c, false);
      }
    }
    const fog = ctx.createLinearGradient(0, height * .4, 0, height * .86);
    fog.addColorStop(0, '#b6a0bc70'); fog.addColorStop(1, '#b6a0bc00');
    ctx.fillStyle = fog; ctx.fillRect(0, height * .4, width, height * .5);
    // Existing objectives have a quiet, distinct marker in the terrain projection.
    for (const o of scene.objectives) {
      const col = (o.x - scene.view.centerX) / scene.view.step + cols / 2;
      const row = rows / 2 - (o.z - scene.view.centerZ) / scene.view.step;
      if (col < 0 || col >= cols || row < 0 || row >= rows) continue;
      const x = col * cell, y = height * .51 + row * rowHeight - 13 * vertical;
      ctx.fillStyle = '#9fe6cf'; ctx.fillRect(x, y - 6, 3, 7);
      ctx.font = '10px monospace'; ctx.fillText(o.name, x + 5, y - 2);
    }
  }
  if (terrainMode !== 'none') {
    ctx.fillStyle = '#e8f8ec'; ctx.font = '12px monospace';
    ctx.fillText(`Terrain ${terrainMode} · seed ${scene.config.seed}`, 12, height * .46);
  }
}
export async function excavateScreen(px, py, width, height) {
  if (!terrainScene?.view.cutaway || editing) return false;
  const {cols, rows} = terrainScene.view;
  const col = Math.floor(px / width * cols), row = Math.floor(rows / 2);
  const c = terrainScene.columns.find(c => c.col === col && c.row === row);
  const y = Math.floor((height * .93 - py) / (height * .009));
  if (!c || y < 0 || c.route) return false;
  const material = planetWorld.material(c.x, y, c.z);
  if (material === 'air') return false;
  const edited = new Planet(planetWorld.config);
  for (const key of planetWorld.edits) edited.edits.add(key);
  edited.excavate(c.x, y, c.z);
  editing = true;
  try {
    const scene = await configurePlanet(edited.config, currentView, edited);
    return scene ? {x: c.x, y, z: c.z, material} : false;
  } finally { editing = false; }
}
export function attachTerrainControls(parent, onChange) {
  listeners.add(onChange);
  const panel = document.createElement('details'); panel.id = 'planet-lab';
  panel.innerHTML = `<summary>Planet lab</summary><div class="planet-fields">
    <label>Seed <input name="seed" value="acheron" maxlength="128"></label>
    <label>Scale <input name="scale" type="number" min="16" max="256" value="64"></label>
    <label>Roughness <input name="roughness" type="number" min="0" max="2" step="0.1" value="1"></label>
    <label>Craters <input name="craterDensity" type="number" min="0" max="1" step="0.05" value="0.65"></label>
    <label>Palette <select name="palette"><option>geological</option><option>cold</option></select></label>
    <label>Test seed gallery <select name="gallery">${SEEDS.map(n => `<option>${n}</option>`).join('')}</select></label>
    <label>Terrain overlay <select name="overlay">${['none', 'elevation', 'biome', 'slope', 'cave', 'traversal'].map(n => `<option>${n}</option>`).join('')}</select></label>
    <label>Geology cutaway <input name="cutaway" type="checkbox"></label>
    <label>Survey X <input name="centerX" type="number" value="0" min="-100000" max="100000"></label>
    <label>Survey Z <input name="centerZ" type="number" value="66" min="-100000" max="100000"></label>
    </div><button type="button" name="generate">Generate</button>
    <label>Landmarks <select name="landmarks"><option value="">Choose a survey landmark</option></select></label>
    <p role="status">Surveying planet…</p><output class="planet-metrics"></output>
    <p class="planet-key">Acid: lime pools · Vents: orange rock · Radiation: violet crystals</p>`;
  const fields = panel.querySelector('.planet-fields');
  for (const [i, name] of BIOMES.entries()) {
    const label = document.createElement('label'); label.textContent = `${name} weight `;
    const input = document.createElement('input'); input.name = 'biome' + i; input.type = 'number';
    input.min = '0'; input.max = '10'; input.step = '.1'; input.value = '1';
    label.append(input); fields.append(label);
  }
  const input = name => panel.querySelector(`[name=${name}]`);
  const result = panel.querySelector('[role=status]'), metrics = panel.querySelector('output'), apply = input('generate');
  let uiJob = 0;
  function describe(scene) {
    const stats = scene.stats;
    result.textContent = scene.validation.reachable.every(o => o.reachable) ? 'All 3 objectives reachable' : 'Unreachable objective';
    metrics.textContent = `${stats.generationMilliseconds.toFixed(1)} ms · ${scene.backend}\n` +
      `${stats.seamsChecked} border samples · ${stats.seamMismatches} seams · ${stats.inaccessibleObjectives} inaccessible\n` +
      `${stats.openColumns} open · ${stats.coverColumns} cover · ${stats.landmarkCount} landmarks`;
    const options = input('landmarks'); options.replaceChildren(new Option('Choose a survey landmark', ''));
    // Survey a fixed surrounding region, so rare formations can be inspected on every seed.
    const found = new Map();
    for (let x = -240; x <= 240; x += 80) for (let z = -240; z <= 240; z += 80) {
      for (const f of planetWorld.features(x, z)) if (f.type !== 'crater') found.set(f.id, f);
    }
    for (const f of [...found.values()].sort((a, b) => a.id.localeCompare(b.id)).slice(0, 60)) {
      options.add(new Option(`${f.type} (${Math.round(f.x)}, ${Math.round(f.z)})`, `${Math.round(f.x)},${Math.round(f.z)}`));
    }
    panel.dataset.backend = scene.backend; panel.dataset.ready = 'true';
  }
  async function generate(reuse = false) {
    const job = ++uiJob; apply.disabled = true; result.textContent = 'Surveying planet…'; panel.dataset.ready = 'false';
    try {
      const config = {};
      for (const n of ['seed', 'scale', 'roughness', 'craterDensity']) config[n] = n === 'seed' ? input(n).value : Number(input(n).value);
      config.biomes = BIOMES.map((_, i) => Number(input('biome' + i).value));
      if (input('palette').value === 'cold') config.palette = Object.fromEntries(BIOMES.map((n, i) => [n,
        [['#252f46', '#394c60', '#455660', '#69788b'][i], '#a5becb', '#d4ede9']]));
      const view = {...currentView, centerX: Number(input('centerX').value), centerZ: Number(input('centerZ').value), cutaway: input('cutaway').checked};
      const scene = await configurePlanet(config, view, reuse);
      if (job === uiJob && scene) describe(scene);
    } catch (e) { if (job === uiJob) {result.textContent = e.message; panel.dataset.ready = 'true';} }
    finally {if (job === uiJob) apply.disabled = false;}
  }
  input('gallery').onchange = () => {input('seed').value = input('gallery').value;};
  input('overlay').onchange = () => setTerrainMode(input('overlay').value);
  input('cutaway').onchange = () => generate(true);
  input('landmarks').onchange = () => {
    if (!input('landmarks').value) return;
    const [x, z] = input('landmarks').value.split(','); input('centerX').value = x; input('centerZ').value = z; generate(true);
  };
  apply.onclick = () => generate();
  parent.append(panel);
  generate();
  return panel;
}
