/** Owner-private Voxel projection; the browser never receives machine credentials. */
const ORIGIN='https://codex-development-dashboard.jamesleaver1.chatgpt.site';
export async function generalProgress(request,env,fetcher=fetch){
 if(!request.headers.get('oai-authenticated-user-id'))return Response.json({error:'Sign in to view development progress.'},{status:401});
 if(!env.GENERAL_DASHBOARD_AUTH||!env.GENERAL_DASHBOARD_BRIDGE_KEY)return Response.json({error:'The shared work queue is unavailable. Recorded progress remains available.'},{status:503});
 try{
  const upstream=await fetcher(ORIGIN+'/api/applications/voxel-one/progress?agentId=voxel-bridge',{headers:{'OAI-Sites-Authorization':'Bearer '+env.GENERAL_DASHBOARD_AUTH,'X-Codex-Agent':'1','X-Codex-Agent-Key':env.GENERAL_DASHBOARD_BRIDGE_KEY},signal:AbortSignal.timeout(6000)});
  if(!upstream.ok)throw Error('Unavailable');
  const text=await upstream.text();if(text.length>300000)throw Error('Too large');const data=JSON.parse(text);
  if(data.schemaVersion!==1||!Array.isArray(data.items)||data.items.length>1000)throw Error('Invalid projection');
  // Whitelist fields even if the central API later adds private prompts or coordination data.
  const items=data.items.map(item=>({id:String(item.id),title:String(item.title),description:typeof item.description==='string'?item.description:'',status:String(item.status),note:typeof item.note==='string'?item.note:'',updatedAt:item.updatedAt,evidence:(item.evidence||[]).filter(e=>typeof e.url==='string'&&e.url.startsWith('https://')).map(e=>({label:String(e.label||'Evidence'),url:e.url,kind:e.kind||(/\.png(?:\?|$)/i.test(e.url)?'image':/\.mp4(?:\?|$)/i.test(e.url)?'video':'report')}))}));
  let snapshot={};try{snapshot=await(await env.ASSETS.fetch(new Request(new URL('/progress.json',request.url)))).json();}catch{}
  return Response.json({schemaVersion:1,project:'Voxel One',updatedAt:data.updatedAt||items.map(i=>i.updatedAt||'').sort().at(-1)||new Date().toISOString(),items,release:snapshot.release,verification:snapshot.verification},{headers:{'Cache-Control':'no-store','X-Content-Type-Options':'nosniff'}});
 }catch{return Response.json({error:'The shared work queue could not be reached. Recorded progress remains available.'},{status:503,headers:{'Cache-Control':'no-store'}});}
}
