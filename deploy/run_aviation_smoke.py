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
for folder in ('target/aviation-home', 'target/aviation-runtime'):
    shutil.rmtree(root/folder, ignore_errors=True)
for folder in ('target/aviation-smoke', 'target/aviation-home', 'target/tmp', 'target/aviation-runtime'):
    (root/folder).mkdir(parents=True, exist_ok=True)
env['TMPDIR'] = str(root/'target/tmp')
env['XDG_CACHE_HOME'] = str(root/'target/aviation-home/cache')
cache = root/'target/maven-cache'
jars = [str(p) for p in cache.rglob('*.jar') if 'natives-windows' not in p.name]
cp = os.pathsep.join([str(root/'target/classes'),str(root/'target/aviation-smoke'),*jars])
subprocess.run(['javac','-cp',cp,'-d','target/aviation-smoke','deploy/AviationSmoke.java'],check=True)
result_file=root/'target/aviation-runtime/results.json'
result_file.unlink(missing_ok=True)
with (root/'target/aviation-runtime/private-runtime.txt').open('w') as runtime:
    result=subprocess.run(['java','-Xmx512m','-Djava.io.tmpdir='+str(root/'target/tmp'),
        '-Duser.home='+str(root/'target/aviation-home'),'-cp',cp,'AviationSmoke',
        str(root/'target/aviation-runtime')],env=env,stdout=runtime,stderr=runtime,timeout=600)
print('Airport native playtest exit:',result.returncode)
if result.returncode:
    failure=root/'target/aviation-runtime/failure.txt'
    if failure.exists(): print(failure.read_text())
    raise SystemExit(result.returncode)
shutil.copyfile(result_file,root/'dashboard/evidence/airport-playtest.json')
for name in ('airport-expanded.png', 'airport-boarding.png', 'airport-flight.png', 'airport-arrival.png', 'airport-compact-menu.png'):
    shutil.copyfile(root/'target/aviation-runtime'/name, root/'dashboard/evidence'/name)
clips=sorted((root/'target/aviation-home/.voxel-one/recordings').glob('*.mp4'))
if not clips: raise SystemExit('F10 video missing')
clip=clips[-1]
if clip.stat().st_size>6_000_000: raise SystemExit('F10 video exceeds 6 MB')
shutil.copyfile(clip,root/'dashboard/evidence/airport-playtest.mp4')
print('PASS: real game airport permits, expansion, citizen boarding, flight and arrival; F10 video saved')
