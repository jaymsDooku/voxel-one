#!/usr/bin/env python3
"""Run named-city UI regression on the inherited display with fresh synthetic data."""
import argparse, os, shutil, subprocess
from pathlib import Path
root = Path(__file__).resolve().parents[1]
p = argparse.ArgumentParser()
p.add_argument('--display', required=True)
a = p.parse_args()
if not a.display or a.display != os.environ.get('DISPLAY'):
    raise SystemExit('The inherited assigned DISPLAY is required.')
profile = root/'target/railway-named-saves-profile'
out = root/'target/railway-named-saves-playtest'
for folder in (profile,out):
    shutil.rmtree(folder, ignore_errors=True)
    folder.mkdir(parents=True)
(root/'target/tmp').mkdir(parents=True, exist_ok=True)
cp = os.pathsep.join([str(root/'target/classes'),str(root/'target/test-classes')]+[str(f) for f in (root/'target/maven-cache').rglob('*.jar') if 'natives-windows' not in f.name])
subprocess.run(['javac','-cp',cp,'-d',str(root/'target/test-classes'),str(root/'deploy/RailwayCitySavesSmoke.java')],check=True)
env = dict(os.environ,DISPLAY=a.display,LIBGL_ALWAYS_SOFTWARE='1',TMPDIR=str(root/'target/tmp'))
env.pop('WAYLAND_DISPLAY',None)
with (root/'target/railway-named-saves-private-runtime.txt').open('w') as log:
    r = subprocess.run(['java','-Duser.home='+str(profile),'-Djava.io.tmpdir='+str(root/'target/tmp'),'-cp',cp,'RailwayCitySavesSmoke',str(profile/'worlds'),str(out)],env=env,stdout=log,stderr=log,timeout=600)
print('Native named-city saves playtest exit:',r.returncode)
if r.returncode:
    stage = profile/'worlds/playtest-stage.txt'
    if stage.exists(): print(stage.read_text().strip())
    raise SystemExit(r.returncode)
for name in ('city-saves-menu.png','city-saves-duplicate.png','city-saves-playtest.txt'):
    shutil.copy2(out/name,root/'dashboard/evidence'/('railway-'+name))
print('Named save, copy, duplicate, new, load, window reuse and rail-state regression passed. Media pending controller publication.')
