#!/usr/bin/env python3
"""Use the inherited role display. All synthetic state and runtime output stay in target/."""
import argparse, os, subprocess, shutil
from pathlib import Path
parser = argparse.ArgumentParser()
parser.add_argument('--display', required=True)
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
os.chdir(root)
env = os.environ.copy()
env['DISPLAY'] = args.display
env['LIBGL_ALWAYS_SOFTWARE'] = '1'
env['MESA_SHADER_CACHE_DISABLE'] = 'true'
env.pop('WAYLAND_DISPLAY', None)
for folder in ('target/missile-home', 'target/missile-runtime'):
    shutil.rmtree(root/folder, ignore_errors=True)
for folder in ('target/missile-smoke', 'target/missile-home', 'target/tmp', 'target/missile-runtime'):
    (root/folder).mkdir(parents=True, exist_ok=True)
env['TMPDIR'] = str(root/'target/tmp')
env['XDG_CACHE_HOME'] = str(root/'target/missile-home/cache')
cache = root/'target/maven-cache'
jars = [str(p) for p in cache.rglob('*.jar') if 'natives-windows' not in p.name]
cp = os.pathsep.join([str(root/'target/classes'),str(root/'target/missile-smoke'),*jars])
subprocess.run(['javac','-cp',cp,'-d','target/missile-smoke','deploy/MissileSmoke.java','deploy/VerifyMissileMedia.java'],check=True)
result_file=root/'target/missile-runtime/results.json'
result_file.unlink(missing_ok=True)
with (root/'target/missile-runtime/private-runtime.txt').open('w') as runtime:
    result=subprocess.run(['java','-Xmx512m','-Djava.io.tmpdir='+str(root/'target/tmp'),
        '-Duser.home='+str(root/'target/missile-home'),'-cp',cp,'MissileSmoke',
        str(root/'target/missile-runtime')],env=env,stdout=runtime,stderr=runtime,timeout=600)
print('Missile native playtest exit:',result.returncode)
if result.returncode:
    failure=root/'target/missile-runtime/failure.txt'
    if failure.exists(): print(failure.read_text())
    raise SystemExit(result.returncode)
shutil.copyfile(result_file,root/'dashboard/evidence/city-missile-playtest.json')
for file in (root/'target/missile-runtime').glob('city-missile-*.png'):
    shutil.copyfile(file,root/'dashboard/evidence'/file.name)
clips=sorted((root/'target/missile-home/.voxel-one/recordings').glob('*.mp4'))
if not clips: raise SystemExit('F10 video missing')
clip=clips[-1]
if clip.stat().st_size>6_000_000: raise SystemExit('F10 video exceeds 6 MB')
shutil.copyfile(clip,root/'dashboard/evidence/city-missile.mp4')
print('PASS: cheat gates, falling missile, repeated-drop edge, impact, debris, persistence and budget regression; fresh media saved')

subprocess.run(['java','-cp',cp,'VerifyMissileMedia',str(root/'dashboard/evidence/city-missile.mp4'),str(root/'dashboard/evidence/city-missile-media-check.json')],check=True)
