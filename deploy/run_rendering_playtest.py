#!/usr/bin/env python3
"""Assigned X11 display only; synthetic render fixture; no account or server access."""
import argparse, os, subprocess
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--display',required=True);p.add_argument('--gl33',action='store_true');p.add_argument('--msaa',choices=['1','4'],default='1');a=p.parse_args()
root=Path(__file__).resolve().parents[1];os.chdir(root)
for name in ('render-driver','render-home','render-profile','tmp'):(root/'target'/name).mkdir(parents=True,exist_ok=True)
env=os.environ.copy();env['DISPLAY']=a.display;env['LIBGL_ALWAYS_SOFTWARE']='1';env.pop('WAYLAND_DISPLAY',None)
java='/usr/lib/jvm/jdk-21.0.5-oracle-x64/bin/java'
command=[java,'-Duser.home='+str(root/'target/render-home'),'-Djava.io.tmpdir='+str(root/'target/tmp'),'-Dvoxel.msaa='+a.msaa]
if a.gl33:
 command.append('-Dvoxel.gl33=true');env['MESA_GL_VERSION_OVERRIDE']='3.3';env['MESA_GLSL_VERSION_OVERRIDE']='330'
command+=['-cp','target/voxel-one-1.0-SNAPSHOT-client.jar','deploy/RenderingSmoke.java','target/rendering-expansion-'+('gl33' if a.gl33 else 'modern')+'-'+a.msaa+'x']
with open('target/render-native-private.txt','w') as log:
 result=subprocess.run(command,env=env,stdout=log,stderr=log,timeout=240)
print('Playtest: renderer fixture; inherited role X11 display; isolated synthetic profile; exit',result.returncode)
if result.returncode:
 # Emit only the exception and shader compiler diagnostics, never the whole runtime log.
 lines=Path('target/render-native-private.txt').read_text().splitlines()
 for line in lines:
  if line.startswith('Exception') or 'error:' in line or line.startswith('java.lang.'):print(line[:500])
raise SystemExit(result.returncode)
