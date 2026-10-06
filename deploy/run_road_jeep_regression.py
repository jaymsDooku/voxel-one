#!/usr/bin/env python3
"""Run the unchanged jeep harness on the assigned display; keep base evidence intact."""
import argparse
import os
from pathlib import Path
import shutil
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument('--display', required=True)
args = parser.parse_args()
if not args.display or args.display != os.environ.get('DISPLAY'):
    raise SystemExit('The inherited assigned DISPLAY is required.')
root = Path(__file__).resolve().parents[1]
os.chdir(root)
for folder in ('target/road-jeep-home', 'target/road-jeep-playtest'):
    shutil.rmtree(root / folder, ignore_errors=True)
for folder in ('target/road-jeep-smoke', 'target/road-jeep-home', 'target/road-jeep-playtest', 'target/tmp'):
    (root / folder).mkdir(parents=True, exist_ok=True)
jars = [str(p) for p in Path('/tmp/voxel-m2').rglob('*.jar') if 'natives-windows' not in p.name]
classpath = os.pathsep.join([str(root / 'target/classes'), str(root / 'target/road-jeep-smoke'), *jars])
subprocess.run(['javac', '-cp', classpath, '-d', 'target/road-jeep-smoke', 'deploy/JeepPlaytest.java'], check=True)
env = os.environ.copy()
env.update(DISPLAY=args.display, LIBGL_ALWAYS_SOFTWARE='1',
           MESA_SHADER_CACHE_DISABLE='true', TMPDIR=str(root / 'target/tmp'),
           XDG_CACHE_HOME=str(root / 'target/road-jeep-home/cache'))
env.pop('WAYLAND_DISPLAY', None)
out = root / 'target/road-jeep-playtest'
with (root / 'target/road-jeep-runtime-private.txt').open('w') as runtime:
    result = subprocess.run(['java', '-Djava.io.tmpdir=' + str(root / 'target/tmp'),
        '-Duser.home=' + str(root / 'target/road-jeep-home'), '-cp', classpath,
        'JeepPlaytest', str(out)], env=env, stdout=runtime, stderr=runtime, timeout=300)
print('Road/jeep regression playtest exit:', result.returncode)
if result.returncode:
    raise SystemExit(result.returncode)
evidence = root / 'dashboard/evidence'
for name in ('jeep-parked.png', 'jeep-driver-seat.png', 'jeep-windshield.png', 'jeep-wall.png', 'results.json'):
    destination = 'road-jeep-playtest.json' if name == 'results.json' else 'road-' + name
    shutil.copyfile(out / name, evidence / destination)
clips = sorted((root / 'target/road-jeep-home/.voxel-one/recordings').glob('*.mp4'))
if not clips:
    raise SystemExit('F10 jeep recording missing.')
clip = clips[-1]
if clip.stat().st_size > 6_000_000:
    raise SystemExit('F10 jeep recording exceeds 6 MB.')
shutil.copyfile(clip, evidence / 'road-jeep-driving.mp4')
print('PASS: unchanged jeep workflow; fresh regression media saved separately from base artifacts.')
