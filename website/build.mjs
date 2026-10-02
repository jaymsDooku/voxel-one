import fs from 'node:fs';
fs.rmSync('dist/client',{recursive:true,force:true});fs.mkdirSync('dist/client',{recursive:true});
for(const entry of fs.readdirSync('dist',{withFileTypes:true})){if(['client','server','.openai'].includes(entry.name))continue;fs.cpSync('dist/'+entry.name,'dist/client/'+entry.name,{recursive:true});}
fs.rmSync('dist/server',{recursive:true,force:true});fs.mkdirSync('dist/server',{recursive:true});fs.copyFileSync('worker.mjs','dist/server/index.js');fs.copyFileSync('video.mjs','dist/server/video.mjs');fs.copyFileSync('db/storage.mjs','dist/server/storage.mjs');
fs.writeFileSync('dist/server/wrangler.json',JSON.stringify({name:'voxel-one-dashboard',main:'./index.js',compatibility_date:'2026-05-15',assets:{directory:'../client',binding:'ASSETS',run_worker_first:['/api/*']},d1_databases:[{binding:'DB',database_name:'voxel-one-dashboard',database_id:'00000000-0000-4000-8000-000000000000',migrations_dir:'../../drizzle'}]},null,2));
