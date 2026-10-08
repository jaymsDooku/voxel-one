#!/usr/bin/env python3
"""Run the synthetic HUD playtest on the assigned inherited X11 display."""
import argparse, os, subprocess
from pathlib import Path
root = Path(__file__).resolve().parents[1]
os.chdir(root)
parser = argparse.ArgumentParser()
parser.add_argument('--display', required=True)
parser.add_argument('--sandbox', action='store_true', help='Check non-city sky-view HUD regression')
args = parser.parse_args()
for folder in ('target/hud-profile', 'target/hud-home', 'target/tmp', 'target/hud-smoke'):
    (root / folder).mkdir(parents=True, exist_ok=True)
jars = [str(p) for p in (root / 'target/maven-cache').rglob('*.jar') if 'natives-windows' not in p.name]
cp = str(root / 'target/classes') + ':' + str(root / 'target/hud-smoke') + ':' + ':'.join(jars)
subprocess.run(['javac', '--release', '17', '-cp', cp, '-d', 'target/hud-smoke', 'deploy/HudActionBarSmoke.java'], check=True)
env = dict(os.environ, DISPLAY=args.display, LIBGL_ALWAYS_SOFTWARE='1')
env.pop('WAYLAND_DISPLAY', None)
report = root / 'dashboard/evidence' / ('hud-sandbox-smoke-result.txt' if args.sandbox else 'hud-smoke-result.txt')
report.unlink(missing_ok=True)
with open('target/hud-runtime-private.txt', 'w') as runtime:
    try:
        result = subprocess.run(['/usr/lib/jvm/jdk-21.0.5-oracle-x64/bin/java',
        *(['-Xmx768m', '-Dvoxel.gl33=true', '-Dvoxel.renderScale=.5'] if args.sandbox else []),
        '-Djava.io.tmpdir=' + str(root / 'target/tmp'), '-Duser.home=' + str(root / 'target/hud-home'),
        '-cp', cp, 'HudActionBarSmoke', str(root / 'dashboard/evidence'),
        'sandbox' if args.sandbox else 'city'],
            env=env, stdout=runtime, stderr=runtime, timeout=300)
    except subprocess.TimeoutExpired:
        print('HUD game playtest timed out after 300 seconds.')
        raise SystemExit(1)
print('HUD game playtest exit:', result.returncode)
if report.exists(): print(report.read_text().strip())
raise SystemExit(result.returncode)
