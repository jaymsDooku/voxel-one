#!/usr/bin/env python3
"""Run real X11 cheat input on the inherited role display. Never starts an X server.
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
# Fresh synthetic profile on every run; never use the player's account or saves.
for folder in ('target/cheat-home', 'target/cheat-playtest'):
    if (root / folder).exists(): shutil.rmtree(root / folder)
for folder in ('target/cheat-smoke', 'target/cheat-home', 'target/cheat-playtest', 'target/tmp'):
    (root / folder).mkdir(parents=True, exist_ok=True)
jars = [str(f) for f in (root / 'target/maven-cache').rglob('*.jar') if 'natives-windows' not in f.name]
classpath = os.pathsep.join([str(root / 'target/classes'), str(root / 'target/cheat-smoke'), *jars])
subprocess.run([str(java_home / 'bin/javac'), '-cp', classpath, '-d', str(root / 'target/cheat-smoke'), str(root / 'deploy/CheatPlaytest.java')], check=True)
env = os.environ.copy()
env.update(DISPLAY=a.display, LIBGL_ALWAYS_SOFTWARE='1', TMPDIR=str(root / 'target/tmp'))
env.pop('WAYLAND_DISPLAY', None)
out = root / 'target/cheat-playtest'
(out / 'results.json').unlink(missing_ok=True)
with (root / 'target/cheat-runtime-private.txt').open('w') as log:
    result = subprocess.run([str(java_home / 'bin/java'), '-Djava.io.tmpdir=' + str(root / 'target/tmp'),
        '-Duser.home=' + str(root / 'target/cheat-home'), '-cp', classpath, 'CheatPlaytest', str(out)],
        env=env, stdout=log, stderr=log, timeout=420)
print('Cheat application playtest exit:', result.returncode)
if result.returncode:
    # Print only this synthetic harness's assertion, never runtime logs.
    for line in (root / 'target/cheat-runtime-private.txt').read_text().splitlines():
        if 'java.lang.AssertionError:' in line:
            print(line.split('java.lang.AssertionError:', 1)[1].strip())
    raise SystemExit(result.returncode)
evidence = root / 'dashboard/evidence'
for path in out.glob('*.png'):
    shutil.copy2(path, evidence / path.name)
shutil.copy2(out / 'results.json', evidence / 'cheat-playtest.json')
clips = sorted((root / 'target/cheat-home/.voxel-one/recordings').glob('*.mp4'))
if not clips:
    raise SystemExit('F10 recording missing; playtest evidence incomplete.')
clip = clips[-1]
if clip.stat().st_size > 6_000_000:
    raise SystemExit('F10 clip exceeds 6 MB; shorten or encode before publication.')
shutil.copy2(clip, evidence / 'cheat-mode.mp4')
subprocess.run([str(java_home / 'bin/javac'), '-cp', classpath, '-d', str(root / 'target/cheat-smoke'), str(root / 'deploy/VerifyRoadVideo.java')], check=True)
subprocess.run([str(java_home / 'bin/java'), '-Djava.io.tmpdir=' + str(root / 'target/tmp'), '-cp', classpath,
    'VerifyRoadVideo', str(evidence / 'cheat-mode.mp4'), str(out / 'video-decoded.png'), str(evidence / 'cheat-video.json')], check=True)
print('Sanitized cheat screenshots, video and results saved. Controller publication pending.')
