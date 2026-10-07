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
for folder in ('target/shipping-cargo-home', 'target/shipping-cargo-runtime'):
    shutil.rmtree(root/folder, ignore_errors=True)
for folder in ('target/shipping-cargo-smoke', 'target/shipping-cargo-home', 'target/tmp', 'target/shipping-cargo-runtime'):
    (root/folder).mkdir(parents=True, exist_ok=True)
env['TMPDIR'] = str(root/'target/tmp')
env['XDG_CACHE_HOME'] = str(root/'target/shipping-cargo-home/cache')
cache = root/'target/m2'
jars = [str(p) for p in cache.rglob('*.jar') if 'natives-windows' not in p.name]
cp = os.pathsep.join([str(root/'target/classes'),str(root/'target/shipping-cargo-smoke'),*jars])
# Preserve the original driver and historical cargo evidence.
source = (root/'deploy/CargoVehicleSmoke.java').read_text().replace('cargo-', 'shipping-cargo-')
generated = root/'target/shipping-cargo-smoke/CargoVehicleSmoke.java'
generated.write_text(source)
subprocess.run(['javac','-cp',cp,'-d','target/shipping-cargo-smoke',str(generated)],check=True)
result_file=root/'target/shipping-cargo-runtime/results.json'
result_file.unlink(missing_ok=True)
with (root/'target/shipping-cargo-runtime/private-runtime.txt').open('w') as runtime:
    result=subprocess.run(['java','-Djava.io.tmpdir='+str(root/'target/tmp'),
        '-Duser.home='+str(root/'target/shipping-cargo-home'),'-cp',cp,'CargoVehicleSmoke',
        str(root/'target/shipping-cargo-runtime')],env=env,stdout=runtime,stderr=runtime,timeout=300)
print('Cargo native playtest exit:',result.returncode)
if result.returncode:
    failure=root/'target/shipping-cargo-runtime/failure.txt'
    if failure.exists(): print(failure.read_text())
    raise SystemExit(result.returncode)
shutil.copyfile(result_file,root/'dashboard/evidence/shipping-cargo-placement-playtest.json')
for name in ('shipping-cargo-jeep.png', 'shipping-cargo-container.png', 'shipping-cargo-tanker.png', 'shipping-cargo-van.png', 'shipping-cargo-lorry.png'):
    shutil.copyfile(root/'target/shipping-cargo-runtime'/name, root/'dashboard/evidence'/name)
print('PASS: real game vehicle body selection, driving, rejection and save reload; fresh images saved')
clips=sorted((root/'target/shipping-cargo-home/.voxel-one/recordings').glob('*.mp4'))
if not clips: raise SystemExit('F10 video missing')
clip=clips[-1]
if clip.stat().st_size>6_000_000: raise SystemExit('F10 video exceeds 6 MB')
shutil.copyfile(clip,root/'dashboard/evidence/shipping-cargo-container-drive.mp4')
