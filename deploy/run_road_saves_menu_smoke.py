#!/usr/bin/env python3
"""Run the preserved City saves workflow with fresh synthetic data and evidence."""
import argparse, os, shutil, subprocess
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument('--display', required=True)
args = parser.parse_args()
if args.display != os.environ.get('DISPLAY'):
    raise SystemExit('Use inherited assigned role DISPLAY')
root = Path(__file__).resolve().parents[1]
os.chdir(root)
runtime = root / 'target/road-saves-menu-runtime'
home = root / 'target/road-saves-menu-home'
classes = root / 'target/road-saves-menu-classes'
for path in (runtime, home):
    shutil.rmtree(path, ignore_errors=True)
for path in (runtime, home, classes, root / 'target/tmp'):
    path.mkdir(parents=True, exist_ok=True)
capture = runtime / 'evidence'
capture.mkdir()
jars = [str(p) for p in Path('/tmp/voxel-m2').rglob('*.jar') if 'natives-windows' not in p.name]
cp = os.pathsep.join([str(root / 'target/classes'), str(classes), *jars])
subprocess.run(['javac', '-cp', cp, '-d', str(classes), 'deploy/CitySavesSmoke.java'], check=True)
env = os.environ.copy()
env.update(DISPLAY=args.display, LIBGL_ALWAYS_SOFTWARE='1', MESA_SHADER_CACHE_DISABLE='true',
           TMPDIR=str(root / 'target/tmp'), XDG_CACHE_HOME=str(home / 'cache'))
env.pop('WAYLAND_DISPLAY', None)
with (runtime / 'private-runtime.txt').open('w') as log:
    result = subprocess.run(['java', '-Xmx512m', '-Duser.home=' + str(home),
                             '-Djava.io.tmpdir=' + str(root / 'target/tmp'), '-cp', cp,
                             'CitySavesSmoke', str(runtime / 'worlds'), str(capture)],
                            env=env, stdout=log, stderr=log, timeout=300)
print('City saves native exit:', result.returncode)
if result.returncode:
    stage = runtime / 'worlds/playtest-stage.txt'
    if stage.exists():
        print(stage.read_text().strip())
    raise SystemExit(result.returncode)
for path in capture.iterdir():
    shutil.copyfile(path, root / 'dashboard/evidence' / ('road-saves-final-' + path.name))
print('PASS: save, copy, duplicate rejection, new, list, load, resume and view regression')
