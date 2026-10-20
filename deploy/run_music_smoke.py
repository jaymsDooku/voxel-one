#!/usr/bin/env python3
"""Run on assigned display only. No account, other display, or private runtime data is read."""
import argparse, os, subprocess, shutil
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--display',required=True);a=p.parse_args()
if not a.display or a.display!=os.environ.get('DISPLAY'):raise SystemExit('Inherited assigned DISPLAY required')
root=Path(__file__).resolve().parents[1];os.chdir(root)
for name in ('music-home','music-runtime','music-smoke','tmp'):(root/'target'/name).mkdir(parents=True,exist_ok=True)
(root/'target/music-runtime/failure.txt').unlink(missing_ok=True)
jars=[str(p) for p in (root/'target/maven-cache').rglob('*.jar') if 'natives-windows' not in p.name]
cp=os.pathsep.join([str(root/'target/classes'),str(root/'target/music-smoke'),*jars])
subprocess.run(['javac','-cp',cp,'-d','target/music-smoke','deploy/MusicSmoke.java'],check=True)
env=os.environ.copy();env.update(DISPLAY=a.display,LIBGL_ALWAYS_SOFTWARE='1',MESA_SHADER_CACHE_DISABLE='true',ALSOFT_DRIVERS='null',TMPDIR=str(root/'target/tmp'),XDG_CACHE_HOME=str(root/'target/music-home/cache'));env.pop('WAYLAND_DISPLAY',None)
with (root/'target/music-runtime/private-runtime.txt').open('w') as runtime:
    result=subprocess.run(['java','-Xmx768m','-Djava.io.tmpdir='+str(root/'target/tmp'),'-Duser.home='+str(root/'target/music-home'),'-cp',cp,'MusicSmoke',str(root/'target/music-runtime')],env=env,stdout=runtime,stderr=runtime,timeout=160)
print('Music native playtest exit:',result.returncode)
if result.returncode:
    failure=root/"target/music-runtime/failure.txt"
    if failure.exists():print(failure.read_text())
    raise SystemExit(result.returncode)
for file in (root/'target/music-runtime').glob('music-*.png'):shutil.copyfile(file,root/'dashboard/evidence'/file.name)
shutil.copyfile(root/'target/music-runtime/music-playtest.json',root/'dashboard/evidence/music-playtest.json')
clips=sorted((root/'target/music-home/.voxel-one/recordings').glob('*.mp4'))
if not clips:raise SystemExit('F10 clip missing')
clip=clips[-1]
if clip.stat().st_size>6_000_000:raise SystemExit('F10 clip exceeds 6 MB')
shutil.copyfile(clip,root/'dashboard/evidence/music-visualizer.mp4')
print('PASS: native catalog/audio/visualizer workflow; fresh F10 MP4 bytes:',clip.stat().st_size)
