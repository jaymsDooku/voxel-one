#!/usr/bin/env python3
"""Inherited role display; isolated synthetic profile; native OpenAL loopback PCM."""
import argparse, os, subprocess, shutil
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--display',required=True);a=p.parse_args()
if not a.display or a.display!=os.environ.get('DISPLAY'):raise SystemExit('Inherited assigned DISPLAY required')
root=Path(__file__).resolve().parents[1];os.chdir(root)
for folder in ['target/vehicle-audio-home','target/vehicle-audio-runtime']:
    shutil.rmtree(root/folder,ignore_errors=True)
for folder in ['target/vehicle-audio-home','target/vehicle-audio-runtime','target/vehicle-audio-smoke','target/tmp']:(root/folder).mkdir(parents=True,exist_ok=True)
jdk=Path(os.environ.get('JAVA_HOME','/usr/lib/jvm/jdk-21.0.5-oracle-x64'))
jars=[str(p) for p in (root/'target/maven-cache').rglob('*.jar') if 'natives-windows' not in p.name]
cp=os.pathsep.join([str(root/'target/classes'),str(root/'target/vehicle-audio-smoke'),*jars])
subprocess.run([str(jdk/'bin/javac'),'-cp',cp,'-d','target/vehicle-audio-smoke','deploy/JeepPlaytest.java','deploy/VehicleAudioPlaytest.java'],check=True)
env=os.environ.copy();env.update(DISPLAY=a.display,LIBGL_ALWAYS_SOFTWARE='1',TMPDIR=str(root/'target/tmp'),XDG_CACHE_HOME=str(root/'target/vehicle-audio-home/cache'));env.pop('WAYLAND_DISPLAY',None)
out=root/'target/vehicle-audio-runtime'
with (out/'private-runtime.txt').open('w') as log:
    try:
        result=subprocess.run([str(jdk/'bin/java'),'-Xmx768m','-Dorg.lwjgl.system.SharedLibraryExtractPath='+str(root/'target/lwjgl-natives'),'-Djava.io.tmpdir='+str(root/'target/tmp'),'-Duser.home='+str(root/'target/vehicle-audio-home'),'-cp',cp,'VehicleAudioPlaytest',str(out)],env=env,stdout=log,stderr=log,timeout=240)
    except subprocess.TimeoutExpired:raise SystemExit('Native vehicle audio playtest timed out after 240 seconds; no pass claimed')
print('Native vehicle audio playtest exit:',result.returncode)
if result.returncode:
    failure=out/'failure.txt'
    if failure.exists():print(failure.read_text())
    raise SystemExit(result.returncode)
for f in out.glob('vehicle-audio-*'):
    if f.suffix in ['.png','.wav']:shutil.copy2(f,root/'dashboard/evidence'/f.name)
shutil.copy2(out/'results.json',root/'dashboard/evidence/vehicle-audio-playtest.json')
clips=sorted((root/'target/vehicle-audio-home/.voxel-one/recordings').glob('*.mp4'))
if not clips:raise SystemExit('F10 recording missing')
if clips[-1].stat().st_size>6_000_000:raise SystemExit('F10 recording exceeds 6 MB')
shutil.copy2(clips[-1],root/'dashboard/evidence/vehicle-audio-train.mp4')
print('Sanitized current implementation media saved; pending controller publication')
