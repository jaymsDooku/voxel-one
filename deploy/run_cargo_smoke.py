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
for folder in ('target/cargo-home', 'target/cargo-runtime'):
    shutil.rmtree(root/folder, ignore_errors=True)
for folder in ('target/cargo-smoke', 'target/cargo-home', 'target/tmp', 'target/cargo-runtime'):
    (root/folder).mkdir(parents=True, exist_ok=True)
env['TMPDIR'] = str(root/'target/tmp')
env['XDG_CACHE_HOME'] = str(root/'target/cargo-home/cache')
cache = root/'target/m2'
jars = [str(p) for p in cache.rglob('*.jar') if 'natives-windows' not in p.name]
cp = os.pathsep.join([str(root/'target/classes'),str(root/'target/cargo-smoke'),*jars])
subprocess.run(['javac','-cp',cp,'-d','target/cargo-smoke','deploy/CargoVehicleSmoke.java'],check=True)
result_file=root/'target/cargo-runtime/results.json'
result_file.unlink(missing_ok=True)
with (root/'target/cargo-runtime/private-runtime.txt').open('w') as runtime:
    result=subprocess.run(['java','-Djava.io.tmpdir='+str(root/'target/tmp'),
        '-Duser.home='+str(root/'target/cargo-home'),'-cp',cp,'CargoVehicleSmoke',
        str(root/'target/cargo-runtime')],env=env,stdout=runtime,stderr=runtime,timeout=300)
print('Cargo native playtest exit:',result.returncode)
if result.returncode:
    failure=root/'target/cargo-runtime/failure.txt'
    if failure.exists(): print(failure.read_text())
    raise SystemExit(result.returncode)
shutil.copyfile(result_file,root/'dashboard/evidence/cargo-placement-playtest.json')
for name in ('cargo-jeep.png', 'cargo-container.png', 'cargo-tanker.png', 'cargo-van.png', 'cargo-lorry.png'):
    shutil.copyfile(root/'target/cargo-runtime'/name, root/'dashboard/evidence'/name)
print('PASS: real game vehicle body selection, driving, rejection and save reload; fresh images saved')
clips=sorted((root/'target/cargo-home/.voxel-one/recordings').glob('*.mp4'))
if not clips: raise SystemExit('F10 video missing')
clip=clips[-1]
if clip.stat().st_size>6_000_000: raise SystemExit('F10 video exceeds 6 MB')
shutil.copyfile(clip,root/'dashboard/evidence/cargo-container-drive.mp4')
