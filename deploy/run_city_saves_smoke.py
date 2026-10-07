#!/usr/bin/env python3
"""Use the inherited role display; never start a display server or read its cookie."""
import argparse, os, subprocess
from pathlib import Path
parser = argparse.ArgumentParser()
parser.add_argument('--display', required=True)
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
os.chdir(root)
profile = root / 'target/city-saves-profile'
if profile.exists():
    raise SystemExit('Use a clean target/city-saves-profile for this test.')
profile.mkdir(parents=True)
(root / 'target/tmp').mkdir(parents=True, exist_ok=True)
cp = ':'.join([str(root/'target/classes'), str(root/'target/test-classes')] +
              [str(p) for p in (root/'target/maven-cache').rglob('*.jar') if 'natives-windows' not in p.name])
subprocess.run(['javac', '-cp', cp, '-d', 'target/test-classes', 'deploy/CitySavesSmoke.java'], check=True)
env = dict(os.environ, DISPLAY=args.display, LIBGL_ALWAYS_SOFTWARE='1')
env.pop('WAYLAND_DISPLAY', None)
with (root/'target/city-saves-runtime-private.txt').open('w') as runtime:
    try:
        result = subprocess.run(['java', '-Duser.home='+str(profile), '-Djava.io.tmpdir='+str(root/'target/tmp'),
                             '-cp', cp, 'CitySavesSmoke', str(profile/'worlds'), 'dashboard/evidence'],
                                env=env, stdout=runtime, stderr=runtime, timeout=300)
    except subprocess.TimeoutExpired:
        print("Native city saves playtest timed out after 300 seconds.")
        stage = profile / "worlds/playtest-stage.txt"
        if stage.exists(): print(stage.read_text().strip())
        raise SystemExit(1)
print('Native city saves playtest exit:', result.returncode)
if result.returncode:
    # Only the synthetic driver checkpoint is read; runtime logs remain private.
    stage = profile / "worlds/playtest-stage.txt"
    if stage.exists(): print(stage.read_text().strip())
    raise SystemExit(result.returncode)
