#!/usr/bin/env python3
"""Run real X11 railway input on the inherited role display. Never starts an X server.
Runtime output and the synthetic profile stay in target/. Only sanitized media
and a test result are copied into dashboard/evidence after a successful run.
"""
import argparse
import os
from pathlib import Path
import shutil
import subprocess

root = Path(__file__).resolve().parents[1]
p = argparse.ArgumentParser()
p.add_argument('--display', required=True)
a = p.parse_args()
if not a.display or a.display != os.environ.get('DISPLAY'):
    raise SystemExit('The inherited assigned DISPLAY is required.')
java_home = Path(os.environ.get('JAVA_HOME', '/usr/lib/jvm/jdk-21.0.5-oracle-x64'))
# Each run starts with fresh synthetic state; old fixture saves may use older formats.
for folder in ('target/railway-home', 'target/railway-playtest'):
    shutil.rmtree(root / folder, ignore_errors=True)
for folder in ('target/railway-smoke', 'target/railway-home', 'target/railway-playtest', 'target/tmp'):
    (root / folder).mkdir(parents=True, exist_ok=True)
jars = [str(f) for f in (root / 'target/maven-cache').rglob('*.jar') if 'natives-windows' not in f.name]
classpath = os.pathsep.join([str(root / 'target/classes'), str(root / 'target/test-classes'), str(root / 'target/railway-smoke'), *jars])
subprocess.run([str(java_home / 'bin/javac'), '-cp', classpath, '-d', str(root / 'target/railway-smoke'), str(root / 'deploy/RailwayPlaytest.java')], check=True)
env = os.environ.copy()
env.update(DISPLAY=a.display, LIBGL_ALWAYS_SOFTWARE='1', TMPDIR=str(root / 'target/tmp'))
env.pop('WAYLAND_DISPLAY', None)
out = root / 'target/railway-playtest'
(out / 'results.json').unlink(missing_ok=True)
(out / 'failure.txt').unlink(missing_ok=True)
with (root / 'target/railway-runtime-private.txt').open('w') as log:
    result = subprocess.run([str(java_home / 'bin/java'), '-Djava.io.tmpdir=' + str(root / 'target/tmp'),
        '-Duser.home=' + str(root / 'target/railway-home'), '-cp', classpath, 'RailwayPlaytest', str(out)],
        env=env, stdout=log, stderr=log, timeout=600)
print('Railway application playtest exit:', result.returncode)
if result.returncode:
    raise SystemExit(result.returncode)
evidence = root / 'dashboard/evidence'
for name in ('railway-built.png', 'railway-passenger.png', 'railway-arrival.png', 'railway-bend-preview.png', 'railway-bend-built.png', 'results.json'):
    shutil.copy2(out / name, evidence / ('railway-playtest.json' if name == 'results.json' else name))
clips = sorted((root / 'target/railway-home/.voxel-one/recordings').glob('*.mp4'))
if len(clips) < 2:
    raise SystemExit('F10 recording missing; playtest evidence incomplete.')
clip = clips[-1]
if clip.stat().st_size > 6_000_000:
    raise SystemExit('F10 clip exceeds 6 MB; shorten or encode before publication.')
shutil.copy2(clip, evidence / 'railway-service.mp4')
bend_clip = clips[-2]
if bend_clip.stat().st_size > 6_000_000:
    raise SystemExit('Rail bend F10 clip exceeds 6 MB.')
shutil.copy2(bend_clip, evidence / 'railway-bend.mp4')
print('Sanitized railway screenshots, video and results saved. Controller publication pending.')
