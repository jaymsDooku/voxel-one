#!/usr/bin/env python3
"""Runs preserved harnesses without overwriting their historical evidence."""
import argparse, os, shutil, subprocess
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--display',required=True);a=p.parse_args()
if a.display!=os.environ.get('DISPLAY'):raise SystemExit('Use the assigned inherited DISPLAY.')
root=Path(__file__).resolve().parents[1];os.chdir(root)
out=root/'target/render-restored-stress';shutil.rmtree(out,ignore_errors=True)
for name in ('profile','worlds','evidence','classes','tmp'):(out/name).mkdir(parents=True)
jdk=Path('/usr/lib/jvm/jdk-21.0.5-oracle-x64/bin')
jar=root/'target/voxel-one-1.0-SNAPSHOT-client.jar'
subprocess.run([str(jdk/'javac'),'-cp',str(jar),'-d',str(out/'classes'),*[str(root/'deploy'/n) for n in ('CitySavesSmoke.java','StressGridSmoke.java','PopulatedStressSmoke.java','RenderingRestoredStressSmoke.java')]],check=True)
env=os.environ.copy();env['DISPLAY']=a.display;env['LIBGL_ALWAYS_SOFTWARE']='1';env['MESA_GL_VERSION_OVERRIDE']='3.3';env['MESA_GLSL_VERSION_OVERRIDE']='330';env['ALSOFT_DRIVERS']='null';env.pop('WAYLAND_DISPLAY',None)
cmd=[str(jdk/'java'),'-Xmx2g','-Dvoxel.gl33=true','-Duser.home='+str(out/'profile'),'-Djava.io.tmpdir='+str(out/'tmp'),'-cp',os.pathsep.join([str(out/'classes'),str(jar)]),'RenderingRestoredStressSmoke',str(out/'worlds'),str(out/'evidence')]
with (out/'runtime-private.txt').open('w') as log:
 try:r=subprocess.run(cmd,env=env,stdout=log,stderr=log,timeout=900)
 except subprocess.TimeoutExpired:raise SystemExit('Playtest timed out after 900 seconds; no pass claimed.')
print('Playtest: restored stress workflow; assigned X11; isolated synthetic profile; exit',r.returncode)
if r.returncode and (out/'worlds/assertion-failure.txt').exists():print((out/'worlds/assertion-failure.txt').read_text().strip())
raise SystemExit(r.returncode)
