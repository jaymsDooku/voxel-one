#!/usr/bin/env python3
"""Native application checks, separate from timing trials. Synthetic profile only."""
import argparse,os,subprocess,shutil
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--display',required=True);p.add_argument('--mode',choices=['offline','multiplayer','city','switch','travel'],required=True);p.add_argument('--video',action='store_true');a=p.parse_args()
root=Path(__file__).resolve().parents[1];os.chdir(root)
label=a.mode+('-video' if a.video else '-plain');out=root/'target/streaming-workflow'/label;shutil.rmtree(out,ignore_errors=True);out.mkdir(parents=True,exist_ok=True);home=out/'home';home.mkdir(exist_ok=True)
env=os.environ.copy();env.update(DISPLAY=a.display,LIBGL_ALWAYS_SOFTWARE='1',MESA_GL_VERSION_OVERRIDE='3.3',MESA_GLSL_VERSION_OVERRIDE='330')
cmd=['/usr/lib/jvm/jdk-21.0.5-oracle-x64/bin/java','-Xmx1536m','-Duser.home='+str(home),'-Djava.io.tmpdir='+str(root/'target/tmp'),'-cp','target/streaming-after-client.jar','deploy/StreamingWorkflow.java',str(out),a.mode,str(a.video).lower()]
with (out/'runtime-private.txt').open('w') as log:r=subprocess.run(cmd,env=env,stdout=log,stderr=log,timeout=2400)
print('Playtest:',label,'exit',r.returncode)
if (out/'failure.txt').exists():print((out/'failure.txt').read_text()[:500])
if r.returncode==0:print((out/'result.txt').read_text())
raise SystemExit(r.returncode)
