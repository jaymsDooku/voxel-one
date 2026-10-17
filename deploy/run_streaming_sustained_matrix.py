#!/usr/bin/env python3
"""Matched fresh-terrain trials. Keep other CPU/GL checks out of profiling runs."""
import argparse,subprocess,json,hashlib
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--display',required=True);p.add_argument('--phase',choices=['before','after','both'],default='both');p.add_argument('--trials',default='1,2,3');a=p.parse_args()
root=Path(__file__).resolve().parents[1]
def digest(p):return hashlib.sha256(p.read_bytes()).hexdigest()
files=sorted(list((root/'src').rglob('*.java'))+[root/'deploy/StreamingRoute.java'])
source=hashlib.sha256(''.join(str(p.relative_to(root))+digest(p) for p in files).encode()).hexdigest()
if a.phase!='before':
 manifest={'reviewedHead':'d8c9c801312251bac53c59e0e890274ecc9bc58f','baseline':'4f8a09aca177062a50d0ab2b68e36735318bfb22+timing-overlay','sourceSha256':source,'routeSha256':digest(root/'deploy/StreamingRoute.java'),'baselineJarSha256':digest(root/'target/streaming-baseline-client.jar'),'featureJarSha256':digest(root/'target/streaming-after-client.jar'),'method':'Fresh profiles;166 samples;resident mesh and accepted lighting warmup;actual framebuffer in JSON;no video;all sustained cold travel outside original spawn preload.'}
 (root/'dashboard/evidence/streaming-sustained-source.json').write_text(json.dumps(manifest,indent=2)+'\n')
for trial in map(int,a.trials.split(',')):
 for phase,jar,revision in [('before','target/baseline-instrumented-classes:target/streaming-baseline-client.jar','4f8a09a+timing-overlay'),('after','target/streaming-after-client.jar',source)]:
  if a.phase!='both' and phase!=a.phase:continue
  subprocess.run(['python3','deploy/run_streaming_route.py','--display',a.display,'--label',f'sustained-{phase}-{trial}','--revision',revision,'--jar',jar],cwd=root,check=True)
  print('Completed sustained route',phase,trial,flush=True)
