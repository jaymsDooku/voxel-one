#!/usr/bin/env python3
"""Use the assigned X11 display and an isolated synthetic game profile."""
import argparse, os, subprocess
from pathlib import Path
parser = argparse.ArgumentParser()
parser.add_argument('--display', required=True)
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
os.chdir(root)
env = os.environ.copy()
env['DISPLAY'] = args.display
env['LIBGL_ALWAYS_SOFTWARE'] = '1'
env.pop('WAYLAND_DISPLAY', None)
work = root / 'target/hud-smoke'
for name in ('classes', 'home', 'tmp', 'world'):
    (work/name).mkdir(parents=True, exist_ok=True)
out = root/'dashboard/evidence'
for name in ('isometric-hud-results.txt', 'isometric-hud-failure.txt'):
    (out/name).unlink(missing_ok=True)
jars = [str(p) for p in (root/'target/maven-cache').rglob('*.jar') if 'natives-windows' not in p.name]
cp = os.pathsep.join([str(root/'target/classes'), str(work/'classes'), *jars])
subprocess.run(['javac', '-cp', cp, '-d', str(work/'classes'), 'deploy/IsometricHudSmoke.java'], check=True)
with (work/'runtime-private.log').open('w') as log:
    result = subprocess.run(['java', '-Xmx768m', '-Dvoxel.gl33=true', '-Dvoxel.renderScale=.5', '-Duser.home='+str(work/'home'),
        '-Djava.io.tmpdir='+str(work/'tmp'), '-cp', cp, 'IsometricHudSmoke', str(out)],
        env=env, stdout=log, stderr=log, timeout=420)
print('Native HUD playtest exit:', result.returncode)
raise SystemExit(result.returncode)
