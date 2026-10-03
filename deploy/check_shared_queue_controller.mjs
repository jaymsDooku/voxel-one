import assert from 'node:assert/strict';
import fs from 'node:fs';
const {default:worker}=await import(new URL('../website/dist/server/index.js',import.meta.url));
let text='';for await(const c of process.stdin)text+=c;
const p=JSON.parse(text);
assert.equal(p.generalUrl,'https://codex-development-dashboard.jamesleaver1.chatgpt.site');
const url=p.generalUrl+'/api/applications/voxel-one/progress?agentId=voxel-bridge';
const upstream=await fetch(url,{redirect:'error',headers:{'OAI-Sites-Authorization':'Bearer '+p.generalBearer,'X-Codex-Agent':'1','X-Codex-Agent-Key':p.bridgeKey}});
assert.equal(upstream.status,200);const projection=await upstream.json();assert.equal(projection.schemaVersion,1);
console.log(JSON.stringify({stage:'real-upstream',status:200,application:projection.application,items:projection.items.length}));
const response=await worker.fetch(new Request('https://local-controller.example/api/general-progress',{headers:{'oai-authenticated-user-id':'local-authenticated-fixture'}}),{GENERAL_DASHBOARD_AUTH:p.generalBearer,GENERAL_DASHBOARD_BRIDGE_KEY:p.bridgeKey});
assert.equal(response.status,200);const data=await response.json();assert.equal(data.project,'Voxel One');assert.deepEqual(data.items.map(i=>i.id),projection.items.map(i=>i.id));
assert(!JSON.stringify(data).includes(p.bridgeKey));assert(!JSON.stringify(data).includes(p.generalBearer));
fs.writeFileSync('/tmp/voxel-controller-projection.json',JSON.stringify(data),{mode:0o600});
console.log(JSON.stringify({stage:'exact-packaged-route-real-upstream',status:200,project:data.project,items:data.items.length,credentialsAbsent:true,browserIdentity:'local-fixture'}));

const denied=await worker.fetch(new Request('https://local-controller.example/api/general-progress'),{GENERAL_DASHBOARD_AUTH:p.generalBearer,GENERAL_DASHBOARD_BRIDGE_KEY:p.bridgeKey});assert.equal(denied.status,401);console.log(JSON.stringify({stage:'unauthenticated-route-regression',status:401,credentialsAbsent:true}));
