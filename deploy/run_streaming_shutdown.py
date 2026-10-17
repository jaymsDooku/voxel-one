#!/usr/bin/env python3
"""Assigned-display active-work switch and faulted cleanup; no live profiles or logs exported."""
import argparse,os,subprocess,shutil
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--display',required=True);p.add_argument('--mode',choices=['active-switch','failure-switch','failure-run'],required=True);a=p.parse_args()
r=Path(__file__).resolve().parents[1];os.chdir(r);out=r/'target/streaming-shutdown'/a.mode
shutil.rmtree(out,ignore_errors=True);out.mkdir(parents=True);(out/'home').mkdir()
env=os.environ.copy();env.update(DISPLAY=a.display,LIBGL_ALWAYS_SOFTWARE='1',MESA_GL_VERSION_OVERRIDE='3.3',MESA_GLSL_VERSION_OVERRIDE='330')
cmd=['/usr/lib/jvm/jdk-21.0.5-oracle-x64/bin/java','-Xmx1536m','-Duser.home='+str(out/'home'),'-Djava.io.tmpdir='+str(r/'target/tmp'),'-cp','target/streaming-after-client.jar','deploy/StreamingShutdownWorkflow.java',str(out),a.mode]
with (out/'runtime-private.txt').open('w') as log:result=subprocess.run(cmd,env=env,stdout=log,stderr=log,timeout=300)
print('Playtest:',a.mode,'exit',result.returncode)
if result.returncode==0:print((out/'result.txt').read_text())
raise SystemExit(result.returncode)
