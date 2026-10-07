#!/usr/bin/env python3
"""Use the inherited role display. All synthetic state and runtime output stay in target/."""
import argparse, os, subprocess, shutil
from pathlib import Path
parser = argparse.ArgumentParser()
parser.add_argument('--display', required=True)
args = parser.parse_args()
if args.display != os.environ.get("DISPLAY"):
    raise SystemExit("Use the inherited assigned role DISPLAY")
root = Path(__file__).resolve().parents[1]
os.chdir(root)
env = os.environ.copy()
env['DISPLAY'] = args.display
env['LIBGL_ALWAYS_SOFTWARE'] = '1'
env['MESA_SHADER_CACHE_DISABLE'] = 'true'
env.pop('WAYLAND_DISPLAY', None)
for folder in ('target/shipping-aviation-home', 'target/shipping-aviation-runtime'):
    shutil.rmtree(root/folder, ignore_errors=True)
for folder in ('target/shipping-aviation-smoke', 'target/shipping-aviation-home', 'target/tmp', 'target/shipping-aviation-runtime'):
    (root/folder).mkdir(parents=True, exist_ok=True)
env['TMPDIR'] = str(root/'target/tmp')
env['XDG_CACHE_HOME'] = str(root/'target/shipping-aviation-home/cache')
cache = root/'target/m2'
jars = [str(p) for p in cache.rglob('*.jar') if 'natives-windows' not in p.name]
cp = os.pathsep.join([str(root/'target/classes'),str(root/'target/shipping-aviation-smoke'),*jars])
# Adapt only the test driver. Keep historical aviation source and evidence intact.
import re
source = (root/'deploy/AviationSmoke.java').read_text()
source = source.replace('(height()-340)/12', '(height()-340)/13').replace('tool==row-1', 'tool==row-2')
source = re.sub(r'airportMenu\((9|10|11)\)', lambda m: 'airportMenu('+str(int(m.group(1))+1)+')', source)
source = source.replace('airport-', 'shipping-airport-')
generated = root/'target/shipping-aviation-smoke/AviationSmoke.java'
generated.write_text(source)
subprocess.run(['javac','-cp',cp,'-d','target/shipping-aviation-smoke',str(generated)],check=True)
result_file=root/'target/shipping-aviation-runtime/results.json'
result_file.unlink(missing_ok=True)
with (root/'target/shipping-aviation-runtime/private-runtime.txt').open('w') as runtime:
    result=subprocess.run(['java','-Xmx512m','-Djava.io.tmpdir='+str(root/'target/tmp'),
        '-Duser.home='+str(root/'target/shipping-aviation-home'),'-cp',cp,'AviationSmoke',
        str(root/'target/shipping-aviation-runtime')],env=env,stdout=runtime,stderr=runtime,timeout=600)
print('Airport native playtest exit:',result.returncode)
if result.returncode:
    failure=root/'target/shipping-aviation-runtime/failure.txt'
    if failure.exists(): print(failure.read_text())
    raise SystemExit(result.returncode)
shutil.copyfile(result_file,root/'dashboard/evidence/shipping-airport-playtest.json')
for name in ('shipping-airport-expanded.png', 'shipping-airport-boarding.png', 'shipping-airport-flight.png', 'shipping-airport-arrival.png', 'shipping-airport-compact-menu.png', 'shipping-airport-regional-regression.png', 'shipping-airport-exchange-rejected.png'):
    shutil.copyfile(root/'target/shipping-aviation-runtime'/name, root/'dashboard/evidence'/name)
clips=sorted((root/'target/shipping-aviation-home/.voxel-one/recordings').glob('*.mp4'))
if not clips: raise SystemExit('F10 video missing')
clip=clips[-1]
if clip.stat().st_size>6_000_000: raise SystemExit('F10 video exceeds 6 MB')
shutil.copyfile(clip,root/'dashboard/evidence/shipping-airport-playtest.mp4')
print('PASS: real game airport permits, expansion, citizen boarding, flight and arrival; F10 video saved')
