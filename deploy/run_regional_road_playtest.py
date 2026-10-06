#!/usr/bin/env python3
"""Run retained road UI on the inherited display; preserve every prior road artifact.

Run after Maven test-compile. Fresh evidence gets the regional-road prefix. Runtime
logs, synthetic saves and profile stay private under target/.
"""
import argparse
import os
import shutil
import subprocess
from pathlib import Path

p = argparse.ArgumentParser()
p.add_argument('--display', required=True)
args = p.parse_args()
if not args.display or args.display != os.environ.get('DISPLAY'):
    raise SystemExit('Use the assigned inherited DISPLAY.')
root = Path(__file__).resolve().parents[1]
os.chdir(root)
work = root / 'target/regional-road-playtest'
if work.exists():
    shutil.rmtree(work)
work.mkdir(parents=True)
classes = work / 'classes'
profile = work / 'profile'
runtime = work / 'runtime'
for directory in (classes, profile, runtime):
    directory.mkdir()
jars = [str(j) for j in sorted(Path('/home/debian/.m2/repository').rglob('*.jar'))
        if 'natives-windows' not in j.name and 'natives-macos' not in j.name]
cp = os.pathsep.join([str(root / 'target/classes'), str(classes), *jars])
compiled = subprocess.run(['javac', '-cp', cp, '-d', str(classes), 'deploy/RoadPlacementSmoke.java'])
if compiled.returncode:
    raise SystemExit('Road driver compilation failed.')
env = dict(os.environ, LIBGL_ALWAYS_SOFTWARE='1', MESA_SHADER_CACHE_DISABLE='true',
           TMPDIR=str(work), XDG_CACHE_HOME=str(profile / 'cache'))
env.pop('WAYLAND_DISPLAY', None)
with (work / 'runtime-private.txt').open('w') as log:
    try:
        result = subprocess.run(['java', '--enable-native-access=ALL-UNNAMED', '-Xmx2g',
                                 '-Djava.io.tmpdir=' + str(work), '-Duser.home=' + str(profile),
                                 '-cp', cp, 'RoadPlacementSmoke', str(runtime)],
                                env=env, stdout=log, stderr=log, timeout=300)
    except subprocess.TimeoutExpired:
        raise SystemExit('Road workflow timed out after 300 seconds.')
if result.returncode:
    # This file contains only the driver's synthetic assertion, never account logs.
    failure = runtime / 'failure.txt'
    if failure.exists():
        print(failure.read_text())
    raise SystemExit('Road workflow failed.')
evidence = root / 'dashboard/evidence'
for name in ('road-menu.png', 'road-guide.png', 'road-surfaces.png'):
    shutil.copyfile(runtime / name, evidence / ('regional-' + name))
shutil.copyfile(runtime / 'results.json', evidence / 'regional-road-playtest.json')
clips = sorted((profile / '.voxel-one/recordings').glob('*.mp4'))
if not clips:
    raise SystemExit('Road F10 clip is missing.')
if clips[-1].stat().st_size > 6_000_000:
    raise SystemExit('Road F10 clip exceeds the 6 MB limit.')
shutil.copyfile(clips[-1], evidence / 'regional-road-main.mp4')
print('PASS: inherited-display road workflow; prior road artifacts unchanged.')
