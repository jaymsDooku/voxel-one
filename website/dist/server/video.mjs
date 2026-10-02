// Serve only published MP4 evidence from this project's milestone branch.
// Correct MIME and byte ranges let Safari play and seek GitHub-hosted recordings.
export async function evidenceVideo(request, upstreamFetch=fetch) {
  const name=new URL(request.url).pathname.slice('/api/evidence-video/'.length);
  if(!/^[a-zA-Z0-9][a-zA-Z0-9_.-]{0,150}\.mp4$/.test(name))return new Response('Unknown recording',{status:404});
  const range=request.headers.get('range');
  const upstream=await upstreamFetch('https://raw.githubusercontent.com/jaymsDooku/voxel-one/development-progress/evidence/'+name);
  if(!upstream.ok)return new Response('Recording unavailable',{status:upstream.status===404?404:502});
  if(Number(upstream.headers.get('content-length'))>6_000_000)return new Response('Recording exceeds evidence limit',{status:413});
  const data=await upstream.arrayBuffer();
  if(data.byteLength>6_000_000)return new Response('Recording exceeds evidence limit',{status:413});
  const headers={'Content-Type':'video/mp4','Accept-Ranges':'bytes','Cache-Control':'private, max-age=3600','X-Content-Type-Options':'nosniff'};
  let start=0,end=data.byteLength-1,status=200;
  if(range){
    const match=/^bytes=(\d*)-(\d*)$/.exec(range);
    if(!match||!match[1]&&!match[2])return new Response(null,{status:416,headers:{...headers,'Content-Range':'bytes */'+data.byteLength}});
    if(!match[1])start=Math.max(0,data.byteLength-Number(match[2]));
    else {start=Number(match[1]);if(match[2])end=Math.min(end,Number(match[2]));}
    if(!Number.isSafeInteger(start)||!Number.isSafeInteger(end)||start>end||start>=data.byteLength)return new Response(null,{status:416,headers:{...headers,'Content-Range':'bytes */'+data.byteLength}});
    status=206;headers['Content-Range']=`bytes ${start}-${end}/${data.byteLength}`;
  }
  headers['Content-Length']=String(end-start+1);
  return new Response(request.method==='HEAD'?null:data.slice(start,end+1),{status,headers});
}
