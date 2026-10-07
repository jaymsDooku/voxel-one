#!/usr/bin/env python3
"""GPU point-light checks on the inherited role display and a synthetic home."""
import argparse, os, subprocess
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--display',required=True);a=p.parse_args()
if a.display!=os.environ.get('DISPLAY'):raise SystemExit('Use the assigned inherited DISPLAY.')
root=Path(__file__).resolve().parents[1];os.chdir(root)
for name in ('render-led-home','tmp'):(root/'target'/name).mkdir(parents=True,exist_ok=True)
env=os.environ.copy();env['DISPLAY']=a.display;env['LIBGL_ALWAYS_SOFTWARE']='1';env['MESA_GL_VERSION_OVERRIDE']='3.3';env['MESA_GLSL_VERSION_OVERRIDE']='330';env.pop('WAYLAND_DISPLAY',None)
cmd=['/usr/lib/jvm/jdk-21.0.5-oracle-x64/bin/java','-Duser.home='+str(root/'target/render-led-home'),'-Djava.io.tmpdir='+str(root/'target/tmp'),'-cp','target/voxel-one-1.0-SNAPSHOT-client.jar','deploy/RenderingLedShadowSmoke.java','dashboard/evidence/rendering-led-shadow-checks.txt']
with (root/'target/render-led-runtime-private.txt').open('w') as log:r=subprocess.run(cmd,env=env,stdout=log,stderr=log,timeout=120)
print('Playtest: production LED segment GPU helper; synthetic cells; GL3.3; exit',r.returncode)
if r.returncode:
 for line in (root/'target/render-led-runtime-private.txt').read_text().splitlines():
  if line.startswith('Exception') or 'error:' in line:print(line[:500])
raise SystemExit(r.returncode)
