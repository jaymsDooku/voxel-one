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
for folder in ('target/shipping-home', 'target/shipping-runtime'):
    shutil.rmtree(root/folder, ignore_errors=True)
for folder in ('target/shipping-smoke', 'target/shipping-home', 'target/tmp', 'target/shipping-runtime'):
    (root/folder).mkdir(parents=True, exist_ok=True)
env['TMPDIR'] = str(root/'target/tmp')
env['XDG_CACHE_HOME'] = str(root/'target/shipping-home/cache')
cache = root/'target/maven-cache'
jars = [str(p) for p in cache.rglob('*.jar') if 'natives-windows' not in p.name]
cp = os.pathsep.join([str(root/'target/classes'),str(root/'target/shipping-smoke'),*jars])
subprocess.run(['javac','-cp',cp,'-d','target/shipping-smoke','deploy/ShippingSmoke.java'],check=True)
result_file=root/'target/shipping-runtime/results.json'
result_file.unlink(missing_ok=True)
with (root/'target/shipping-runtime/private-runtime.txt').open('w') as runtime:
    result=subprocess.run(['java','-Djava.io.tmpdir='+str(root/'target/tmp'),
        '-Duser.home='+str(root/'target/shipping-home'),'-cp',cp,'ShippingSmoke',
        str(root/'target/shipping-runtime')],env=env,stdout=runtime,stderr=runtime,timeout=300)
print('Shipping native playtest exit:',result.returncode)
if result.returncode:
    failure=root/'target/shipping-runtime/failure.txt'
    if failure.exists(): print(failure.read_text())
    raise SystemExit(result.returncode)
shutil.copyfile(result_file,root/'dashboard/evidence/railway-shipping-regression.json')
for name in ('shipping-inland-rejection.png', 'shipping-coastal-menu.png', 'shipping-port-carrier.png', 'shipping-port-reverse.png'):
    shutil.copyfile(root/'target/shipping-runtime'/name, root/'dashboard/evidence'/('railway-regression-'+name))
clips=sorted((root/'target/shipping-home/.voxel-one/recordings').glob('*.mp4'))
if not clips: raise SystemExit('F10 video missing')
clip=clips[-1]
if clip.stat().st_size>6_000_000: raise SystemExit('F10 video exceeds 6 MB')
shutil.copyfile(clip,root/'dashboard/evidence/railway-shipping-regression.mp4')
print('PASS: native coastal port, inland rejection, college regression and F10 recording')
