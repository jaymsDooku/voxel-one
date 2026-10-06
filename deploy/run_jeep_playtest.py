#!/usr/bin/env python3
"""Run real X11 jeep input on the inherited role display. Never starts an X server.
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
for folder in ('target/jeep-smoke', 'target/jeep-home', 'target/jeep-playtest', 'target/tmp'):
    (root / folder).mkdir(parents=True, exist_ok=True)
jars = [str(f) for f in (root / 'target/maven-cache').rglob('*.jar') if 'natives-windows' not in f.name]
classpath = os.pathsep.join([str(root / 'target/classes'), str(root / 'target/jeep-smoke'), *jars])
subprocess.run([str(java_home / 'bin/javac'), '-cp', classpath, '-d', str(root / 'target/jeep-smoke'), str(root / 'deploy/JeepPlaytest.java')], check=True)
env = os.environ.copy()
env.update(DISPLAY=a.display, LIBGL_ALWAYS_SOFTWARE='1', TMPDIR=str(root / 'target/tmp'))
env.pop('WAYLAND_DISPLAY', None)
out = root / 'target/jeep-playtest'
(out / 'results.json').unlink(missing_ok=True)
with (root / 'target/jeep-runtime-private.txt').open('w') as log:
    result = subprocess.run([str(java_home / 'bin/java'), '-Djava.io.tmpdir=' + str(root / 'target/tmp'),
        '-Duser.home=' + str(root / 'target/jeep-home'), '-cp', classpath, 'JeepPlaytest', str(out)],
        env=env, stdout=log, stderr=log, timeout=180)
print('Jeep application playtest exit:', result.returncode)
if result.returncode:
    raise SystemExit(result.returncode)
evidence = root / 'dashboard/evidence'
for name in ('jeep-parked.png', 'jeep-driver-seat.png', 'jeep-windshield.png', 'jeep-wall.png', 'results.json'):
    shutil.copy2(out / name, evidence / ('jeep-playtest.json' if name == 'results.json' else name))
clips = sorted((root / 'target/jeep-home/.voxel-one/recordings').glob('*.mp4'))
if not clips:
    raise SystemExit('F10 recording missing; playtest evidence incomplete.')
clip = clips[-1]
if clip.stat().st_size > 6_000_000:
    raise SystemExit('F10 clip exceeds 6 MB; shorten or encode before publication.')
shutil.copy2(clip, evidence / 'jeep-driving.mp4')
print('Sanitized jeep screenshots, video and results saved. Controller publication pending.')
