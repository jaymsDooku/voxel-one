#!/usr/bin/env python3
"""Publishable numeric summaries only; never copies logs, profiles or account stores."""
import csv,math,json,shutil,hashlib
from pathlib import Path
root=Path(__file__).resolve().parents[1];evidence=root/'dashboard/evidence'
def values(rows,key):
 out=[]
 for r in rows:
  try:v=float(r.get(key,''))
  except ValueError:continue
  if math.isfinite(v):out.append(v)
 return out
def percentile(v,q):
 a=sorted(v);return a[max(0,math.ceil(q*len(a))-1)] if a else None
def stats(rows,key):
 a=values(rows,key)
 return {'samples':len(a),'p50':percentile(a,.5),'p95':percentile(a,.95),'p99':percentile(a,.99),'worst':max(a) if a else None,'mean':sum(a)/len(a) if a else None}
def summary(rows):
 result={k:stats(rows,k) for k in rows[0] if k!='frame'}
 a=values(rows,'frame_ms');slow=sorted(a,reverse=True)[:max(1,math.ceil(len(a)*.01))]
 result['onePercentLowFps']=1000/(sum(slow)/len(slow))
 result['frameThresholdCounts']={str(n):sum(v>n for v in a) for n in (33,50,100)}
 combined=[dict(r,tracked_stream_ms=str(sum(float(r.get(k) or 0) for k in ('stream_cpu_ms','snapshot_cpu_ms','upload_cpu_ms')))) for r in rows]
 result['tracked_stream_ms']=stats(combined,'tracked_stream_ms')
 accounted=[]
 for r in rows:
  mesh=float(r.get('mesh_owner_cpu_ms') or 0) if 'mesh_owner_cpu_ms' in r else sum(float(r.get(k) or 0) for k in ('snapshot_cpu_ms','upload_cpu_ms'))
  total=mesh+sum(float(r.get(k) or 0) for k in ('stream_cpu_ms','sky_build_cpu_ms','lighting_cpu_ms','lighting_upload_cpu_ms'))
  accounted.append(dict(r,accounted_stream_ms=str(total)))
 result['accounted_stream_ms']=stats(accounted,'accounted_stream_ms')
 return result
report={'status':'available VPS software-renderer measurements; physical Windows GPU qualification pending','method':'Three matched fresh-profile pairs, baseline trials completed before final feature trials. Selected Balanced640x480, actual framebuffer dimensions in metadata (baseline1280x720 due existing startup fallback), seed748291, draw distance112, horizon2048, dynamic resolution off, scale1. Identical 810 resident-chunk preload plus ten graphics warm-up frames. Video inactive. CPU stage elapsed time can include driver waits. GPU frame metric is delayed EMA, not GPU percentiles.','trials':{},'sourceManifest':'streaming-source-manifest.json'}
lines=['Matched production Sandbox route; Mesa llvmpipe software rendering.','Frame and CPU stage milliseconds. Physical Windows GPU qualification is pending.','trial | samples | frame p50/p95/p99/worst | stream CPU p50/p95/p99/worst | 1% low FPS | frames >33/50/100 ms']
for trial in range(1,4):
 for phase in ('before','after'):
  label=f'{phase}-{trial}';folder=root/'target/streaming-route'/label
  files=list(folder.glob('performance-*.csv'));assert len(files)==1,(label,'Expected one complete export')
  rows=list(csv.DictReader(files[0].open()));assert len(rows)==119,(label,len(rows))
  data=summary(rows);data['segments']={name:summary(rows[a:b]) for name,a,b in [('walking',0,30),('rapidFlight',30,70),('reversal',70,90),('teleport',90,110),('warmRevisit',110,119)]}
  meta=json.loads(files[0].with_suffix('.json').read_text());data['metadata']=meta;report['trials'][label]=data
  for f in (files[0],files[0].with_suffix('.json')):shutil.copyfile(f,evidence/f'streaming-{label}{f.suffix}')
  def show(key):return '/'.join(f'{data[key][p]:.3f}' for p in ('p50','p95','p99','worst'))
  counts=data['frameThresholdCounts'];lines.append(f"{label} | {len(rows)} | {show('frame_ms')} | {show('stream_cpu_ms')} | {data['onePercentLowFps']:.3f} | {counts['33']}/{counts['50']}/{counts['100']}")
report['streamP99ReductionPercentByPair']=[100*(1-report['trials'][f'after-{i}']['stream_cpu_ms']['p99']/report['trials'][f'before-{i}']['stream_cpu_ms']['p99']) for i in range(1,4)]
lines.append('Accounted streaming CPU p99 before/after (baseline lacks whole mesh-owner timing): '+', '.join(f"{report['trials'][f'before-{i}']['accounted_stream_ms']['p99']:.3f}/{report['trials'][f'after-{i}']['accounted_stream_ms']['p99']:.3f} ms" for i in range(1,4)))
lines.append('Stream CPU p99 reduction by matched pair: '+', '.join(f'{v:.2f}%' for v in report['streamP99ReductionPercentByPair']))
(evidence/'streaming-comparison.json').write_text(json.dumps(report,indent=2,allow_nan=False)+'\n')
(evidence/'streaming-comparison.txt').write_text('\n'.join(lines)+'\n')
print('\n'.join(lines))
