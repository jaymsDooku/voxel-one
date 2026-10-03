import assert from 'node:assert/strict';
import test from 'node:test';
import fs from 'node:fs';
import worker from '../dist/server/index.js';

const request=()=>new Request('https://voxel-one.example/api/general-progress',{headers:{'oai-authenticated-user-id':'test-owner'}});
const env={GENERAL_DASHBOARD_AUTH:'fictional-auth',GENERAL_DASHBOARD_BRIDGE_KEY:'fictional-bridge-key'};

test('deployment package includes the current queue worker and bridge',()=>{
 for(const [source,built] of [['worker.mjs','index.js'],['progress_bridge.mjs','progress_bridge.mjs']]){
  assert.equal(fs.readFileSync(new URL('../'+source,import.meta.url),'utf8'),fs.readFileSync(new URL('../dist/server/'+built,import.meta.url),'utf8'));
 }
 assert.match(fs.readFileSync(new URL('../dist/client/progress.js',import.meta.url),'utf8'),/requestProgress\('\/api\/general-progress'\)/);
});

test('packaged route returns all Voxel queue items without question database or saved assets',async()=>{
 const original=globalThis.fetch;
 const items=Array.from({length:46},(_,i)=>({id:'voxel-'+i,title:'Queue work '+i,status:i%2?'queued':'in_progress',evidence:[]}));
 globalThis.fetch=async(url,options)=>{
  assert.equal(url,'https://codex-development-dashboard.jamesleaver1.chatgpt.site/api/applications/voxel-one/progress?agentId=voxel-bridge');
  assert.equal(options.redirect,'error');
  return Response.json({schemaVersion:1,items});
 };
 try{
  const response=await worker.fetch(request(),env);
  assert.equal(response.status,200);assert.equal(response.headers.get('cache-control'),'no-store');
  const data=await response.json();assert.equal(data.project,'Voxel One');assert.deepEqual(data.items.map(i=>i.id),items.map(i=>i.id));
 }finally{globalThis.fetch=original;}
});

test('packaged queue route preserves browser authentication requirement',async()=>{
 const response=await worker.fetch(new Request('https://voxel-one.example/api/general-progress',{headers:{'x-voxel-agent':'1'}}),env);
 assert.equal(response.status,401);
});
