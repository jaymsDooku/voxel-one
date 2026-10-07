#!/usr/bin/env python3
"""Run preserved base native harnesses using fresh synthetic profiles and cached dependencies."""
import argparse,os,subprocess,shutil,json
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--display',required=True);p.add_argument('--scenario',choices=('cheat','industrial'),required=True);a=p.parse_args()
if a.display!=os.environ.get('DISPLAY'):raise SystemExit('Use inherited assigned DISPLAY')
root=Path(__file__).resolve().parents[1];os.chdir(root)
prefix='road-industrial-final-'+a.scenario
home=root/'target'/f'{prefix}-home';out=root/'target'/f'{prefix}-runtime';classes=root/'target'/f'{prefix}-classes'
for folder in (home,out):shutil.rmtree(folder,ignore_errors=True)
for folder in (home,out,classes,root/'target/tmp'):folder.mkdir(parents=True,exist_ok=True)
jars=[str(f) for f in Path('/tmp/voxel-m2').rglob('*.jar') if 'natives-windows' not in f.name]
cp=os.pathsep.join([str(root/'target/classes'),str(root/'target/test-classes'),str(classes),*jars])
name='CheatPlaytest' if a.scenario=='cheat' else 'ResourceProgressionSmoke'
subprocess.run(['javac','-cp',cp,'-d',str(classes),str(root/'deploy'/f'{name}.java')],check=True)
env=os.environ.copy();env.update(DISPLAY=a.display,LIBGL_ALWAYS_SOFTWARE='1',MESA_SHADER_CACHE_DISABLE='true',TMPDIR=str(root/'target/tmp'));env.pop('WAYLAND_DISPLAY',None)
args=[str(out)] if a.scenario=='cheat' else [str(out),str(home/'synthetic-city')]
with (out/'private-runtime.txt').open('w') as log:
 r=subprocess.run(['java','-Xmx512m','-Djava.io.tmpdir='+str(root/'target/tmp'),'-Duser.home='+str(home),'-cp',cp,name if a.scenario=='cheat' else 'dev.jayms.'+name,*args],env=env,stdout=log,stderr=log,timeout=600)
print(a.scenario,'native exit:',r.returncode)
if r.returncode:raise SystemExit(r.returncode)
evidence=root/'dashboard/evidence'
for f in out.iterdir():
 if f.suffix in ('.png','.json'):shutil.copyfile(f,evidence/f'{prefix}-{f.name}')
if a.scenario=='cheat':
 clips=sorted((home/'.voxel-one/recordings').glob('*.mp4'))
 if not clips:raise SystemExit('F10 clip missing')
 if not 0<clips[-1].stat().st_size<=6_000_000:raise SystemExit('F10 clip outside 6 MB limit')
 shutil.copyfile(clips[-1],evidence/f'{prefix}-playtest.mp4')
print('PASS: fresh synthetic base workflow and sanitized evidence saved')
