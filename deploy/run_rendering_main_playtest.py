#!/usr/bin/env python3
"""Production workflow on the inherited assigned X11 display, with an isolated synthetic home."""
import argparse, os, subprocess, shutil
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--display',required=True);p.add_argument('--gl33',action='store_true');a=p.parse_args()
root=Path(__file__).resolve().parents[1];os.chdir(root)
shutil.rmtree(root/'target/render-main-home',ignore_errors=True)
shutil.rmtree(root/'target/rendering-main',ignore_errors=True)
for saved in (root/'target/render-profile').glob('synthetic-city.dat*'):saved.unlink(missing_ok=True)
for name in ('render-main-home','render-profile','tmp'):(root/'target'/name).mkdir(parents=True,exist_ok=True)
env=os.environ.copy();env['DISPLAY']=a.display;env['LIBGL_ALWAYS_SOFTWARE']='1';env.pop('WAYLAND_DISPLAY',None);env['ALSOFT_DRIVERS']='null'
java='/usr/lib/jvm/jdk-21.0.5-oracle-x64/bin/java'
command=[java,'-Duser.home='+str(root/'target/render-main-home'),'-Djava.io.tmpdir='+str(root/'target/tmp')]
if a.gl33:
 command.append('-Dvoxel.gl33=true');env['MESA_GL_VERSION_OVERRIDE']='3.3';env['MESA_GLSL_VERSION_OVERRIDE']='330'
command+=['-cp','target/voxel-one-1.0-SNAPSHOT-client.jar','deploy/RenderingMainPlaytest.java','target/rendering-main']
with open('target/render-main-runtime-private.txt','w') as log:result=subprocess.run(command,env=env,stdout=log,stderr=log,timeout=300)
print('Playtest: production Main; assigned X11; isolated synthetic home; exit',result.returncode)
if result.returncode:
 for line in Path('target/render-main-runtime-private.txt').read_text().splitlines():
  if line.startswith('Exception') or ('error:' in line and not line.startswith('ALSA')) or line.startswith('Caused by:'):print(line[:500])
raise SystemExit(result.returncode)
