#!/usr/bin/env python3
"""Use the inherited role display. All synthetic state and runtime output stay in target/."""
import argparse, os, subprocess, shutil
from pathlib import Path
parser = argparse.ArgumentParser()
parser.add_argument('--display', required=True)
parser.add_argument('--mixed-only', action='store_true')
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
os.chdir(root)
env = os.environ.copy()
env['DISPLAY'] = args.display
env['LIBGL_ALWAYS_SOFTWARE'] = '1'
env['MESA_SHADER_CACHE_DISABLE'] = 'true'
env.pop('WAYLAND_DISPLAY', None)
for folder in ('target/road-mixed-home', 'target/road-mixed-runtime'):
    shutil.rmtree(root/folder, ignore_errors=True)
for folder in ('target/road-mixed-smoke', 'target/road-mixed-home', 'target/tmp', 'target/road-mixed-runtime'):
    (root/folder).mkdir(parents=True, exist_ok=True)
env['TMPDIR'] = str(root/'target/tmp')
env['XDG_CACHE_HOME'] = str(root/'target/road-mixed-home/cache')
cache = Path('/tmp/voxel-m2')
jars = [str(p) for p in cache.rglob('*.jar') if 'natives-windows' not in p.name]
cp = os.pathsep.join([str(root/'target/classes'),str(root/'target/road-mixed-smoke'),*jars])
subprocess.run(['javac','-cp',cp,'-d','target/road-mixed-smoke','deploy/MixedRoadWorkflowSmoke.java'],check=True)
result_file=root/'target/road-mixed-runtime/results.json'
result_file.unlink(missing_ok=True)
with (root/'target/road-mixed-runtime/private-runtime.txt').open('w') as runtime:
    result=subprocess.run(['java','-Xmx512m','-Djava.io.tmpdir='+str(root/'target/tmp'),
        '-Duser.home='+str(root/'target/road-mixed-home'),'-cp',cp,'MixedRoadWorkflowSmoke',
        str(root/'target/road-mixed-runtime'),*(['--mixed-only'] if args.mixed_only else [])],env=env,stdout=runtime,stderr=runtime,timeout=600)
print('Road native playtest exit:',result.returncode)
if result.returncode:
    failure=root/'target/road-mixed-runtime/failure.txt'
    if failure.exists(): print(failure.read_text())
    raise SystemExit(result.returncode)
shutil.copyfile(result_file,root/'dashboard/evidence/road-mixed-playtest.json')
for name in ('road-workflow-menu.png', 'road-workflow-guide.png', 'road-workflow-surfaces.png', 'road-workflow-selected.png', 'road-workflow-edited.png', 'road-workflow-snap.png', 'road-mixed-before.png', 'road-mixed-deleted.png', 'road-mixed-narrowed.png', 'road-mixed-restored.png'):
    if args.mixed_only and name.startswith('road-workflow-'):continue
    shutil.copyfile(root/'target/road-mixed-runtime'/name, root/'dashboard/evidence'/name.replace('road-workflow-', 'road-mixed-'))
clips=sorted((root/'target/road-mixed-home/.voxel-one/recordings').glob('*.mp4'))
if not clips: raise SystemExit('F10 video missing')
clip=clips[-1]
if clip.stat().st_size>6_000_000: raise SystemExit('F10 video exceeds 6 MB')
shutil.copyfile(clip,root/'dashboard/evidence/road-mixed-playtest.mp4')
print('PASS: native mixed-width delete/narrow and survivor restoration; F10 video saved' if args.mixed_only else 'PASS: native mixed-width delete/narrow, snap, chains, selection, upgrade and cancellation; F10 video saved')
