#!/usr/bin/env python3
"""Matched readiness, coverage and fresh-travel qualification; no concurrent tests or recording."""
import argparse,hashlib,json,subprocess
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--display',required=True);p.add_argument('--trials',default='1,2,3');p.add_argument('--prefix',default='lighting-flow');p.add_argument('--phase',choices=['before','after','both'],default='both');p.add_argument('--manifest-only',action='store_true');a=p.parse_args()
root=Path(__file__).resolve().parents[1]
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
files=sorted([p for p in (root/'src').rglob('*') if p.is_file()]+[root/'deploy/StreamingRoute.java'])
source=hashlib.sha256(''.join(str(f.relative_to(root))+sha(f) for f in files).encode()).hexdigest()
manifest={'priorReviewedHead':subprocess.check_output(['git','rev-parse','HEAD'],cwd=root,text=True).strip(),'sourceSha256':source,'routeSha256':sha(root/'deploy/StreamingRoute.java'),'baseline':'4f8a09a+timing-overlay','baselineJarSha256':sha(root/'target/streaming-baseline-client.jar'),'featureJarSha256':sha(root/'target/streaming-after-client.jar'),'method':'Fresh synthetic profiles, same seed/settings/JVM/route; no media capture. Coverage probes are horizontal 3x3 at spacing24, not screen-pixel visibility. Source age starts at capture; valid static fields may be old.'}
(root/'dashboard/evidence'/f'{a.prefix}-source.json').write_text(json.dumps(manifest,indent=2)+'\n')
if a.manifest_only:
 print('Recorded exact-source manifest; no trial executed.');raise SystemExit(0)
for trial in map(int,a.trials.split(',')):
 for phase,jar,revision in [('before','target/baseline-instrumented-classes:target/streaming-baseline-client.jar','4f8a09a+timing-overlay'),('after','target/streaming-after-client.jar',source)]:
  if a.phase!='both' and a.phase!=phase:continue
  label=f'{a.prefix}-{phase}-{trial}'
  folder=root/'target/streaming-route'/label
  if folder.exists():raise SystemExit('Existing trial retained; choose only remaining trials after inspection: '+label)
  subprocess.run(['python3','deploy/run_streaming_route.py','--display',a.display,'--label',label,'--revision',revision,'--jar',jar],cwd=root,check=True)
  print('Completed readiness route',phase,trial,flush=True)
