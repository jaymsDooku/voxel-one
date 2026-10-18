#!/usr/bin/env python3
"""Three interleaved matched cold-route pairs. Builds must finish before this starts."""
import argparse,subprocess,hashlib,json
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--display',required=True);p.add_argument('--phase',choices=['both','before','after'],default='both');p.add_argument('--trials',default='1,2,3');a=p.parse_args()
root=Path(__file__).resolve().parents[1]
def digest(path):return hashlib.sha256(path.read_bytes()).hexdigest()
files=[p for p in (root/'src').rglob('*.java')]+[root/'deploy/StreamingRoute.java']
source=hashlib.sha256(''.join(str(p.relative_to(root))+digest(p) for p in sorted(files)).encode()).hexdigest()
manifest={'base':'4f8a09aca177062a50d0ab2b68e36735318bfb22','afterSourceSha256':source,'baselineJarSha256':digest(root/'target/streaming-baseline-client.jar'),'afterJarSha256':digest(root/'target/streaming-after-client.jar'),'baselineTimingOverlay':{str(p.relative_to(root)):digest(p) for p in sorted((root/'target/baseline-instrumented-src').rglob('*.java'))},'routeSha256':digest(root/'deploy/StreamingRoute.java'),'Playtest':'Production Main; fixed per-frame route; fresh synthetic profile each trial; no video; ten warm-up frames after matching 810 resident chunks. Mesa llvmpipe, not Windows hardware.'}
(root/'dashboard/evidence/streaming-source-manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
for trial in map(int,a.trials.split(',')):
 for phase,jar,revision in [('before','target/baseline-instrumented-classes:target/streaming-baseline-client.jar','4f8a09a+timing-overlay'),('after','target/streaming-after-client.jar',source)]:
  if a.phase!='both' and a.phase!=phase:continue
  label=f'{phase}-{trial}'
  subprocess.run(['python3','deploy/run_streaming_route.py','--display',a.display,'--label',label,'--jar',jar,'--revision',revision],cwd=root,check=True)
  print('Completed matched route',label,flush=True)
