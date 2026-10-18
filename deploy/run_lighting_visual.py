#!/usr/bin/env python3
"""Matched ground-level rendered coverage; screenshots and depth readbacks excluded from profiling."""
import argparse,os,subprocess,json,hashlib,shutil
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--display',required=True);p.add_argument('--phase',choices=['before','after'],required=True);p.add_argument('--manifest',default='lighting-throughput-source.json');p.add_argument('--prefix',default='lighting-throughput-visual');a=p.parse_args()
r=Path(__file__).resolve().parents[1];os.chdir(r);out=r/'target'/f'{a.prefix}-{a.phase}'
if out.exists():raise SystemExit('Existing visual run retained: '+str(out))
out.mkdir();profile_home=out/'home';profile_home.mkdir()
env=os.environ.copy();env.update(DISPLAY=a.display,LIBGL_ALWAYS_SOFTWARE='1',MESA_GL_VERSION_OVERRIDE='3.3',MESA_GLSL_VERSION_OVERRIDE='330')
jar='target/baseline-instrumented-classes:target/streaming-baseline-client.jar' if a.phase=='before' else 'target/streaming-after-client.jar'
fixture_before=hashlib.sha256((r/'deploy/LightingVisualRoute.java').read_bytes()).hexdigest()
revision='4f8a09a+timing-overlay' if a.phase=='before' else json.loads((r/'dashboard/evidence'/a.manifest).read_text())['sourceSha256']
cmd=['/usr/lib/jvm/jdk-21.0.5-oracle-x64/bin/java','-Xmx1536m','-Djava.io.tmpdir='+str(r/'target/tmp'),'-Duser.home='+str(profile_home),'-cp',jar,'deploy/LightingVisualRoute.java',str(out),revision,'visual']
with (out/'runtime-private.txt').open('w') as log:result=subprocess.run(cmd,env=env,stdout=log,stderr=log,timeout=2400)
print('Playtest: matched ground-view visual/depth route',a.phase,'exit',result.returncode)
if result.returncode==0:
 if hashlib.sha256((r/'deploy/LightingVisualRoute.java').read_bytes()).hexdigest()!=fixture_before:raise SystemExit('Visual fixture changed during execution; no exact-fixture receipt')
 for f in list(out.glob('visual-*.png'))+list(out.glob('depth-*.json'))+list(out.glob('surface-*.csv.gz'))+[out/'coverage.csv']:
  shutil.copyfile(f,r/'dashboard/evidence'/f'{a.prefix}-{a.phase}-{f.name}')
 report={'phase':a.phase,'revision':revision,'fixtureSha256':fixture_before,'canonicalRouteSha256':hashlib.sha256((r/'deploy/StreamingRoute.java').read_bytes()).hexdigest(),'note':'Same seed, x positions and graphics settings; ground+4m feet,25degree down view,180degree look turn throughout reversal. Every frame uses resolved visible depth and actual pre-UI linear HDR surface RGB; compressed probes retain pixel/world coordinates and GI/sky source flags; regular images expose transient near GI. All visual-run performance exports excluded from comparisons.','samples':[json.loads(f.read_text()) for f in sorted(out.glob('depth-*.json'))]}
 (r/'dashboard/evidence'/f'{a.prefix}-{a.phase}-report.json').write_text(json.dumps(report,indent=2)+'\n')
raise SystemExit(result.returncode)
