// Independent destination data. Integration waits for the reviewed ground engine.
const freeze = value => {
  if (value && typeof value === 'object') {
    Object.values(value).forEach(freeze);
    Object.freeze(value);
  }
  return value;
};
export const chapters = freeze([
  {id:'classic', mode:'classic', next:'fighter'},
  {id:'fighter', mode:'fighter', next:'descent'},
  {id:'descent', mode:'descent', next:'alien-ground'},
  {id:'alien-ground', mode:'alien-ground', next:'alternate-earth-ground'},
  {id:'alternate-earth-ground', mode:'alternate-earth-ground', next:null},
]);
export const district = freeze({
  id:'cinder-quay', year:1964, seed:19640617,
  title:'Cinder Quay · Alternate Earth · 1964',
  arrival:'In this world the Axis won the war. Nazi occupation controls this American harbour.',
  newspaper:'17 June 1964. Cinder Quay Free Press, underground edition: In this alternate Earth the Axis won. Twenty years under Nazi occupation. The harbour relay tracks every unauthorized transmission.',
  contact:{id:'mara-vale', faction:'resistance', dialogue:'I am Mara Vale. Keep your blaster low around civilians. Meet me at the old print shop; we can silence the relay together.'},
  factions:{occupation:{hostile:true}, resistance:{hostile:false}, civilian:{hostile:false}},
  objectives:[{id:'safehouse', action:'interact', target:'mara-vale'},
    {id:'checkpoint', action:'reach', target:'rail-crossing'},
    {id:'relay', action:'disable', target:'signal-relay'}],
  routes:['warehouse-lane','tram-cut','waterside'],
  props:['brick-warehouse','period-sedan','tram-rails','street-lamp','print-shop','radio','newspaper','checkpoint-barrier'],
  limits:{actors:24, particles:128, previewPixels:65536, preparationTimeoutMs:8000},
  persistence:'session-only',
});

// Coordinates are local to the portal: x across, y up, z normal.
// Capsule endpoints are feet; radius shrinks the usable rectangular aperture.
export function sweptPortalCrossing(from, to, {halfWidth, height}, {radius, capsuleHeight}) {
  const values = [from.x, from.y, from.z, to.x, to.y, to.z, halfWidth, height, radius, capsuleHeight];
  if (!values.every(Number.isFinite) || radius <= 0 || capsuleHeight <= 0 || halfWidth <= radius || height <= capsuleHeight) return null;
  if (!((from.z > 0 && to.z <= 0) || (from.z < 0 && to.z >= 0))) return null;
  const t = from.z / (from.z - to.z);
  const x = from.x + (to.x - from.x) * t;
  const y = from.y + (to.y - from.y) * t;
  // Strict margins reject frame contact and floor/ceiling grazing.
  if (Math.abs(x) >= halfWidth - radius || y < 0 || y + capsuleHeight >= height) return null;
  return {t, x, y, direction:Math.sign(to.z - from.z)};
}

export function immutableHandoff(source, destination) {
  const required = ['runId','missionId','worldId','score','lives','health','weapon','ammunition','difficulty','settings','transform'];
  for (const key of required) if (source[key] === undefined) throw new Error(`Missing handoff field: ${key}`);
  for (const key of ['score','lives','health','ammunition']) if (!Number.isFinite(source[key]) || source[key] < 0) throw new Error(`Invalid resource: ${key}`);
  if (!destination.missionId || !destination.worldId || destination.missionId === source.missionId || destination.worldId === source.worldId || !Number.isInteger(destination.seed)) throw new Error('Destination requires new mission/world identities and an integer seed');
  return freeze(structuredClone({source:Object.fromEntries(required.map(key=>[key,source[key]])), destination}));
}

export function validateChapters(data=chapters) {
  const modes=new Set(['classic','fighter','descent','alien-ground','alternate-earth-ground']);
  const ids=new Set(data.map(c=>c.id));
  if(ids.size!==data.length)throw new Error('Duplicate chapter identity');
  for(const chapter of data)if(!modes.has(chapter.mode)||!ids.has(chapter.id)||(chapter.next!==null&&!ids.has(chapter.next)))throw new Error('Invalid chapter descriptor');
  for(const first of data){const seen=new Set();let c=first;while(c){if(seen.has(c.id))throw new Error('Campaign cycle');seen.add(c.id);c=data.find(next=>next.id===c.next);}}
  return true;
}
validateChapters();
