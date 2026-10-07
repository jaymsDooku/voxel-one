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
for folder in ('target/carrier-routes-home', 'target/carrier-routes-runtime'):
    shutil.rmtree(root/folder, ignore_errors=True)
for folder in ('target/carrier-routes-smoke', 'target/carrier-routes-home', 'target/tmp', 'target/carrier-routes-runtime'):
    (root/folder).mkdir(parents=True, exist_ok=True)
env['TMPDIR'] = str(root/'target/tmp')
env['XDG_CACHE_HOME'] = str(root/'target/carrier-routes-home/cache')
cache = root/'target/m2'
jars = [str(p) for p in cache.rglob('*.jar') if 'natives-windows' not in p.name]
cp = os.pathsep.join([str(root/'target/classes'),str(root/'target/carrier-routes-smoke'),*jars])
subprocess.run(['javac','-cp',cp,'-d','target/carrier-routes-smoke','deploy/CarrierRoutesSmoke.java'],check=True)
result_file=root/'target/carrier-routes-runtime/results.json'
result_file.unlink(missing_ok=True)
with (root/'target/carrier-routes-runtime/private-runtime.txt').open('w') as runtime:
    result=subprocess.run(['java','-Djava.io.tmpdir='+str(root/'target/tmp'),
        '-Duser.home='+str(root/'target/carrier-routes-home'),'-cp',cp,'CarrierRoutesSmoke',
        str(root/'target/carrier-routes-runtime')],env=env,stdout=runtime,stderr=runtime,timeout=300)
print('Shipping native playtest exit:',result.returncode)
if result.returncode:
    failure=root/'target/carrier-routes-runtime/failure.txt'
    if failure.exists(): print(failure.read_text())
    raise SystemExit(result.returncode)
shutil.copyfile(result_file,root/'dashboard/evidence/carrier-routes-placement-playtest.json')
for name in ('carrier-routes-inland-rejection.png', 'carrier-routes-coastal-menu.png', 'carrier-routes-port-carrier.png', 'carrier-routes-port-reverse.png', 'carrier-routes-sailing.png', 'carrier-routes-moved.png', 'carrier-routes-bridge.png', 'carrier-routes-arrival.png', 'carrier-routes-return.png'):
    shutil.copyfile(root/'target/carrier-routes-runtime'/name, root/'dashboard/evidence'/name)
clips=sorted((root/'target/carrier-routes-home/.voxel-one/recordings').glob('*.mp4'))
if not clips: raise SystemExit('F10 video missing')
clip=clips[-1]
if clip.stat().st_size>6_000_000: raise SystemExit('F10 video exceeds 6 MB')
shutil.copyfile(clip,root/'dashboard/evidence/carrier-routes-placement-playtest.mp4')
print('PASS: native coastal port, inland rejection, college regression and F10 recording')
