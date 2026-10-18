#!/usr/bin/env python3
"""Production Main route on inherited role display. No live profile or private logs exported."""
import argparse,os,subprocess
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--display',required=True);p.add_argument('--label',required=True);p.add_argument('--revision',required=True);p.add_argument('--jar',default='target/voxel-one-1.0-SNAPSHOT-client.jar');p.add_argument('--warmup-only',action='store_true');p.add_argument('--visual',action='store_true');p.add_argument('--trace',action='store_true');p.add_argument('--jfr',action='store_true');p.add_argument('--hosttrace',action='store_true');p.add_argument('--samples',type=int,default=166);a=p.parse_args()
root=Path(__file__).resolve().parents[1];os.chdir(root)
if (root/'target/streaming-matrix-stop').exists():raise SystemExit('Streaming matrix stopped at a safe trial boundary.')
out=root/'target/streaming-route'/a.label;out.mkdir(parents=True,exist_ok=True)
home=out/'home';home.mkdir(exist_ok=True)
env=os.environ.copy();env.update(DISPLAY=a.display,LIBGL_ALWAYS_SOFTWARE='1',MESA_GL_VERSION_OVERRIDE='3.3',MESA_GLSL_VERSION_OVERRIDE='330')
cmd=['/usr/lib/jvm/jdk-21.0.5-oracle-x64/bin/java','-Xmx1536m',*(['-Dvoxel.streaming.trace=true'] if a.trace else []),'-Dvoxel.route.samples='+str(a.samples),*(['-Dvoxel.streaming.hosttrace=true'] if a.hosttrace else []),*(['-XX:StartFlightRecording=filename='+str(out/'owner-attribution-private.jfr')+',settings='+str(root/'target/owner-attribution.jfc')+',maxsize=64m,dumponexit=true'] if a.jfr else []),'-Djava.io.tmpdir='+str(root/'target/tmp'),*(['-XX:FlightRecorderOptions=repository='+str(out/'jfr-repository')] if a.jfr else []),'-Duser.home='+str(home),'-cp',a.jar,'deploy/StreamingRoute.java',str(out),a.revision]+(['warmup'] if a.warmup_only else ['visual'] if a.visual else [])
with (out/'runtime-private.txt').open('w') as log:r=subprocess.run(cmd,env=env,stdout=log,stderr=log,timeout=1800)
print('Playtest: fixed production Sandbox route',a.label,'exit',r.returncode)
if r.returncode==0:
    for f in out.glob('performance-*'):
        if f.suffix in ('.csv','.json'):print(f)
raise SystemExit(r.returncode)
