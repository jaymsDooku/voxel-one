#!/usr/bin/env python3
"""Sustained native GL streaming/edit checks on the assigned display, synthetic data only."""
import argparse,os,subprocess,shutil
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--display',required=True);p.add_argument('--cycles',type=int,default=64);a=p.parse_args()
if not 32<=a.cycles<=512:p.error('cycles must be 32..512')
root=Path(__file__).resolve().parents[1];os.chdir(root);out=root/'target/streaming-soak';shutil.rmtree(out,ignore_errors=True);out.mkdir()
(root/'target/tmp').mkdir(exist_ok=True);(root/'target/soak-home').mkdir(exist_ok=True)
env=os.environ.copy();env.update(DISPLAY=a.display,LIBGL_ALWAYS_SOFTWARE='1',MESA_GL_VERSION_OVERRIDE='3.3',MESA_GLSL_VERSION_OVERRIDE='330');env.pop('WAYLAND_DISPLAY',None)
cmd=['/usr/lib/jvm/jdk-21.0.5-oracle-x64/bin/java','-Xmx768m','-Duser.home='+str(root/'target/soak-home'),'-Djava.io.tmpdir='+str(root/'target/tmp'),'-cp','target/voxel-one-1.0-SNAPSHOT-client.jar','deploy/StreamingSoak.java',str(out),str(a.cycles)]
with (out/'runtime-private.txt').open('w') as log:r=subprocess.run(cmd,env=env,stdout=log,stderr=log,timeout=1800)
print('Playtest: production streaming/edit GL soak; exit',r.returncode)
if r.returncode==0:print((out/'streaming-soak-results.txt').read_text())
elif (out/'failure.txt').exists():print((out/'failure.txt').read_text()[:500])
raise SystemExit(r.returncode)
