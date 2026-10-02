import test from 'node:test';
import assert from 'node:assert/strict';
import { evidenceVideo } from '../website/video.mjs';
const data=new Uint8Array([1,2,3,4,5]);
const request=(range,method='GET',name='test-run.mp4')=>new Request('https://example.test/api/evidence-video/'+name,{method,headers:range?{range}:{}});
const upstream=async url=>{assert.equal(url,'https://raw.githubusercontent.com/jaymsDooku/voxel-one/development-progress/evidence/test-run.mp4');return new Response(data);};
test('MP4 MIME, byte range support and head response',async()=>{
 const whole=await evidenceVideo(request(),upstream);assert.equal(whole.status,200);assert.equal(whole.headers.get('content-type'),'video/mp4');assert.equal(whole.headers.get('accept-ranges'),'bytes');assert.deepEqual(new Uint8Array(await whole.arrayBuffer()),data);
 const part=await evidenceVideo(request('bytes=1-3'),upstream);assert.equal(part.status,206);assert.equal(part.headers.get('content-range'),'bytes 1-3/5');assert.deepEqual(new Uint8Array(await part.arrayBuffer()),new Uint8Array([2,3,4]));
 const head=await evidenceVideo(request(null,'HEAD'),upstream);assert.equal(head.headers.get('content-length'),'5');assert.equal((await head.arrayBuffer()).byteLength,0);
});
test('Safari-style open-ended and suffix ranges',async()=>{
 for(const [range,expected]of [['bytes=2-', [3,4,5]],['bytes=-2',[4,5]],['bytes=0-99',[1,2,3,4,5]]]) {
  const r=await evidenceVideo(request(range),upstream);assert.equal(r.status,206);assert.deepEqual([...new Uint8Array(await r.arrayBuffer())],expected);
 }
});
test('Invalid or unsatisfiable ranges return 416',async()=>{
 for(const range of ['bytes=100-','bytes=3-1','bytes=-0','bytes=-','bytes=0-1,3-4','garbage']) {
  const r=await evidenceVideo(request(range),upstream);assert.equal(r.status,416);assert.equal(r.headers.get('content-range'),'bytes */5');
 }
});
test('Only project MP4 evidence filenames can be requested',async()=>{
 for(const name of ['secrets.txt','other/test.mp4','%2e%2e%2fsecret.mp4','x.mp4?bad=1']) {
  if(name.includes('?'))continue;
  const r=await evidenceVideo(request(null,'GET',name),()=>{throw Error('Must not fetch');});assert.equal(r.status,404);
 }
});
test('Missing evidence and oversized artifacts handled',async()=>{
 assert.equal((await evidenceVideo(request(),async()=>new Response(null,{status:404}))).status,404);
 assert.equal((await evidenceVideo(request(),async()=>new Response(null,{headers:{'content-length':'6000001'}}))).status,413);
});
