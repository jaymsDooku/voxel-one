#!/usr/bin/env python3
"""Run Sandbox mechanics or City regression plus launch selection on the inherited X11 display."""
import argparse, os, subprocess
from pathlib import Path
root = Path(__file__).resolve().parents[1]
os.chdir(root)
parser = argparse.ArgumentParser()
parser.add_argument('--display', required=True)
parser.add_argument('--sandbox', action='store_true', help='Check non-city sky-view HUD regression')
args = parser.parse_args()
import shutil
shutil.rmtree(root / "target/sandbox-profile", ignore_errors=True)
shutil.rmtree(root / "target/sandbox-home", ignore_errors=True)
for folder in ('target/sandbox-profile', 'target/sandbox-home', 'target/tmp', 'target/sandbox-smoke'):
    (root / folder).mkdir(parents=True, exist_ok=True)
profile = root / 'target/sandbox-home/.voxel-one'
profile.mkdir(parents=True, exist_ok=True)
(profile / 'graphics.properties').write_text('version=2\npreset=LOW\nDETAIL=32\nHORIZON=512\nSHADOWS=OFF\nAA=OFF\n')
jars = [str(p) for p in (root / 'target/maven-cache').rglob('*.jar') if 'natives-windows' not in p.name]
cp = str(root / 'target/classes') + ':' + str(root / 'target/sandbox-smoke') + ':' + ':'.join(jars)
subprocess.run(['javac', '--release', '17', '-cp', cp, '-d', 'target/sandbox-smoke', 'deploy/SandboxPlaytest.java', 'deploy/SandboxSelectionPlaytest.java'], check=True)
env = dict(os.environ, DISPLAY=args.display, LIBGL_ALWAYS_SOFTWARE='1')
env.pop('WAYLAND_DISPLAY', None)
report = root / 'dashboard/evidence' / ('voxel-sandbox-smoke-result.txt' if args.sandbox else 'voxel-city-entry-result.txt')
report.unlink(missing_ok=True)
with open('target/sandbox-selection-runtime-private.txt', 'w') as runtime:
    subprocess.run(['java', '-Djava.io.tmpdir=' + str(root / 'target/tmp'),
        '-Duser.home=' + str(root / 'target/sandbox-home'), '-cp', cp,
        'SandboxSelectionPlaytest', str(root / 'dashboard/evidence')], env=env,
        stdout=runtime, stderr=runtime, timeout=60, check=True)
print((root / 'dashboard/evidence/voxel-sandbox-selection-result.txt').read_text().strip())
with open('target/sandbox-runtime-private.txt', 'w') as runtime:
    try:
        result = subprocess.run(['/usr/lib/jvm/jdk-21.0.5-oracle-x64/bin/java',
        '-Xmx768m', '-Dvoxel.gl33=true', '-Dvoxel.renderScale=.5',
        '-Djava.io.tmpdir=' + str(root / 'target/tmp'), '-Duser.home=' + str(root / 'target/sandbox-home'),
        '-cp', cp, 'SandboxPlaytest', str(root / 'dashboard/evidence'),
        'sandbox' if args.sandbox else 'city'],
            env=env, stdout=runtime, stderr=runtime, timeout=300)
    except subprocess.TimeoutExpired:
        print('HUD game playtest timed out after 300 seconds.')
        raise SystemExit(1)
if args.sandbox and result.returncode == 0:
    clips = list((root / 'target/sandbox-home/.voxel-one/recordings').glob('*.mp4'))
    if clips:
        shutil.copy2(max(clips, key=lambda p:p.stat().st_mtime), root / 'dashboard/evidence/voxel-sandbox-place-break.mp4')
print('Game playtest exit:', result.returncode)
if report.exists(): print(report.read_text().strip())
raise SystemExit(result.returncode)
