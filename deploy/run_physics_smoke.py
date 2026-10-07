#!/usr/bin/env python3
"""PhysicsLab playtest on the inherited role display; no display server or account data."""
import argparse, os, shutil, subprocess
from pathlib import Path
parser=argparse.ArgumentParser();parser.add_argument('--display',required=True);args=parser.parse_args()
root=Path(__file__).resolve().parents[1];os.chdir(root)
env=os.environ.copy();env['DISPLAY']=args.display;env['LIBGL_ALWAYS_SOFTWARE']='1';env['MESA_SHADER_CACHE_DISABLE']='true';env.pop('WAYLAND_DISPLAY',None)
for folder in ('target/physics-home','target/physics-runtime'):shutil.rmtree(root/folder,ignore_errors=True)
for folder in ('target/physics-home','target/physics-runtime','target/physics-smoke','target/tmp'):(root/folder).mkdir(parents=True,exist_ok=True)
env['TMPDIR']=str(root/'target/tmp');env['XDG_CACHE_HOME']=str(root/'target/physics-home/cache')
jars=[str(p) for p in (root/'target/maven-cache').rglob('*.jar') if 'natives-windows' not in p.name]
cp=os.pathsep.join([str(root/'target/classes'),str(root/'target/physics-smoke'),*jars])
subprocess.run(['javac','-cp',cp,'-d','target/physics-smoke','deploy/PhysicsSmoke.java','deploy/WalkerPhysicsSmoke.java','deploy/VerifyPhysicsMedia.java'],check=True)
with (root/'target/physics-runtime/private-runtime.txt').open('w') as runtime:
    result=subprocess.run(['java','-Xmx512m','-Djava.io.tmpdir='+str(root/'target/tmp'),'-Duser.home='+str(root/'target/physics-home'),'-cp',cp,'PhysicsSmoke',str(root/'target/physics-runtime')],env=env,stdout=runtime,stderr=runtime,timeout=120)
print('Physics native playtest exit:',result.returncode)
if result.returncode:
    failure=root/'target/physics-runtime/failure.txt'
    if failure.exists():print(failure.read_text())
    raise SystemExit(result.returncode)
for file in (root/'target/physics-runtime').glob('physics-*.png'):shutil.copyfile(file,root/'dashboard/evidence'/file.name)
shutil.copyfile(root/'target/physics-runtime/results.json',root/'dashboard/evidence/physics-playtest.json')
shutil.copyfile(root/'target/physics-runtime/force-results.json',root/'dashboard/evidence/physics-force-playtest.json')
clips=sorted((root/'target/physics-home/.voxel-one/recordings').glob('*.mp4'))
if not clips:raise SystemExit('F10 clip missing')
clip=clips[-1]
if clip.stat().st_size>6_000_000:raise SystemExit('F10 clip exceeds 6 MB')
shutil.copyfile(clip,root/'dashboard/evidence/physics-collapse.mp4')
print('PASS: collapse, safe repeated cut/reset, joints, fluid/fire, cloth/SPH/GPU and sprung vehicle; fresh media saved')

with (root/'target/physics-runtime/walker-private-runtime.txt').open('w') as runtime:
    result=subprocess.run(['java','-Xmx512m','-Djava.io.tmpdir='+str(root/'target/tmp'),'-Duser.home='+str(root/'target/physics-home'),'-cp',cp,'WalkerPhysicsSmoke',str(root/'target/physics-runtime')],env=env,stdout=runtime,stderr=runtime,timeout=120)
print('Sandbox walker native playtest exit:',result.returncode)
if result.returncode:
    failure=root/'target/physics-runtime/walker-failure.txt'
    if failure.exists():print(failure.read_text())
    raise SystemExit(result.returncode)
for file in (root/'target/physics-runtime').glob('physics-walker-*.png'):shutil.copyfile(file,root/'dashboard/evidence'/file.name)
shutil.copyfile(root/'target/physics-runtime/walker-results.json',root/'dashboard/evidence/physics-walker-playtest.json')
print('PASS: production sandbox walker partial stair, wall edge and jump/landing regression')

subprocess.run(['java','-Djava.io.tmpdir='+str(root/'target/tmp'),'-cp',cp,'VerifyPhysicsMedia',str(root/'dashboard/evidence/physics-collapse.mp4'),str(root/'dashboard/evidence/physics-media-check.json')],check=True)
