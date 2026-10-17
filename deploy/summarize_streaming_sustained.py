#!/usr/bin/env python3
"""Sanitized stage/segment statistics and residency, with no runtime/profile data export."""
import csv,json,math,shutil,argparse
p=argparse.ArgumentParser();p.add_argument("--before-prefix",default="sustained-before");p.add_argument("--after-prefix",default="sustained-after");p.add_argument("--output-prefix",default="streaming-sustained-comparison");args=p.parse_args()
from pathlib import Path
root=Path(__file__).resolve().parents[1];evidence=root/'dashboard/evidence'
def percentile(v,q):
 v=sorted(v);return v[max(0,math.ceil(q*len(v))-1)] if v else None
def stats(rows,key):
 v=[]
 for r in rows:
  try:n=float(r.get(key,''))
  except ValueError:continue
  if math.isfinite(n):v.append(n)
 return {'samples':len(v),'p50':percentile(v,.5),'p95':percentile(v,.95),'p99':percentile(v,.99),'worst':max(v) if v else None}
def summary(rows):
 s={k:stats(rows,k) for k in rows[0] if k!='frame'}
 frames=sorted([float(r['frame_ms']) for r in rows],reverse=True);slow=frames[:max(1,math.ceil(len(frames)*.01))]
 s['onePercentLowFps']=1000/(sum(slow)/len(slow));s['overMs']={str(n):sum(f>n for f in frames) for n in (33,50,100)}
 combined=[]
 for r in rows:
  mesh=float(r.get('mesh_owner_cpu_ms') or 0) if 'mesh_owner_cpu_ms' in r else sum(float(r.get(k) or 0) for k in ('snapshot_cpu_ms','upload_cpu_ms'))
  combined.append({'accounted':str(mesh+sum(float(r.get(k) or 0) for k in ('stream_cpu_ms','sky_build_cpu_ms','lighting_cpu_ms','lighting_upload_cpu_ms','scene_cpu_ms')))})
 s['accountedStreamingCpuMs']=stats(combined,'accounted');return s
report={'method':'Three matched pairs on one available Linux llvmpipe host;no video. Resident meshes and accepted lighting ready before capture;warmup durations exported. Seed748291,profile/settings and actual framebuffer recorded. Sustained flight x136..236 is outside spawn preload endingx79. GPU EMA is not a per-frame GPU distribution. Baseline lacks whole mesh-owner timing,so accounted baseline mesh cost uses snapshot/upload only.','trials':{}}
lines=['Sustained fresh terrain comparison; Mesa llvmpipe software rendering.','trial | segment | samples | frame p50/p95/p99/worst ms | accounted streaming CPU p99 ms | terrain-stream CPU p99 ms | nearby residency']
for trial in range(1,4):
 for phase in ('before','after'):
  label=f'{args.before_prefix}-{trial}' if phase=='before' else f'{args.after_prefix}-{trial}';folder=root/'target/streaming-route'/label;files=list(folder.glob('performance-*.csv'));assert len(files)==1,(label,len(files))
  rows=list(csv.DictReader(files[0].open()));positions=list(csv.DictReader((folder/'residency.csv').open()));assert len(rows)==len(positions)==166,(label,len(rows),len(positions))
  data={'metadata':json.loads(files[0].with_suffix('.json').read_text()),'all':summary(rows),'segments':{}}
  for segment in ('resident','coldEntry','coldWalk','coldFlight','warmReversal','teleport','catchup','transitionTail'):
   selected=[r for r,pos in zip(rows,positions) if pos['segment']==segment];res=[p for p in positions if p['segment']==segment];d=summary(selected)
   d['centerResidentFrames']=sum(p['center_resident']=='true' for p in res);d['nearbyResidentFrames']=sum(p['nearby_resident']=='true' for p in res);d['frameCount']=len(res);data['segments'][segment]=d
   f=d['frame_ms'];lines.append(f"{label} | {segment} | {len(res)} | "+'/'.join(f'{f[k]:.3f}' for k in ('p50','p95','p99','worst'))+f" | {d['accountedStreamingCpuMs']['p99']:.3f} | {d['stream_cpu_ms']['p99']:.3f} | {d['nearbyResidentFrames']}/{len(res)}")
  if phase=='after':data['storeAllocationsDuringCapture']=sum(float(r.get('lighting_store_allocations') or 0) for r in rows)
  report['trials'][label]=data
  for source,suffix in [(files[0],'.csv'),(files[0].with_suffix('.json'),'.json'),(folder/'residency.csv','-residency.csv'),(folder/'startup.json','-startup.json'),(folder/'warmup.json','-warmup.json')]:shutil.copyfile(source,evidence/(label+suffix))
(evidence/(args.output_prefix+'.json')).write_text(json.dumps(report,indent=2,allow_nan=False)+'\n');(evidence/(args.output_prefix+'.txt')).write_text('\n'.join(lines)+'\n');print('\n'.join(lines))
