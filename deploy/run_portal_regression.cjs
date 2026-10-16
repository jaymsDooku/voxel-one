// Run existing regression scenarios without replacing their historical artifacts.
const fs=require('node:fs');
const allowed=new Set(['test_space_invaders_entry.cjs','test_space_invaders_entry_regression.cjs','test_space_invaders_fighters.cjs','test_space_invaders_collisions.cjs','test_space_invaders_ground.cjs','test_alien_terrain.cjs','test_orbital_launcher.cjs']);
const file=process.argv[2];if(!allowed.has(file))throw Error('Unknown regression');
let source=fs.readFileSync('deploy/'+file,'utf8').replaceAll('http://127.0.0.1:8766/','http://127.0.0.1:8779/').replaceAll('http://127.0.0.1:8767/','http://127.0.0.1:8779/').replaceAll('dashboard/evidence/','dashboard/evidence/'+(process.env.GROUND_ORBITAL==='1'?'portal-regression-orbital-':'portal-regression-'));
source=source.replace('path.join(output, name)',"path.join(output, 'portal-regression-'+name)").replace('${output}/alien-terrain-playtest.json','${output}/portal-regression-alien-terrain-playtest.json');
source=source.replace("'../.tmp/qa/node_modules/playwright'","'/tmp/voxel-web-qa/node_modules/playwright'");
new Function('require','process','__dirname',source)(require,process,process.cwd()+'/deploy');
