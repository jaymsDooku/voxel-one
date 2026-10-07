#!/usr/bin/env python3
"""Production application parcel workflow on the inherited role display; synthetic state only."""
import argparse, os, subprocess, shutil
from pathlib import Path
parser=argparse.ArgumentParser()
parser.add_argument('--display',required=True)
parser.add_argument('--classes',default='target/classes',choices=('target/classes','target/parcel-final-classes'))
args=parser.parse_args()
if args.display!=os.environ.get('DISPLAY'):raise SystemExit('Use inherited assigned role DISPLAY')
root=Path(__file__).resolve().parents[1];os.chdir(root)
runtime=root/'target/parcel-runtime';home=root/'target/parcel-home';classes=root/'target/parcel-classes'
for p in (runtime,home):shutil.rmtree(p,ignore_errors=True)
for p in (runtime,home,classes,root/'target/tmp'):p.mkdir(parents=True,exist_ok=True)
env=os.environ.copy();env['DISPLAY']=args.display;env['LIBGL_ALWAYS_SOFTWARE']='1';env['MESA_SHADER_CACHE_DISABLE']='true';env.pop('WAYLAND_DISPLAY',None)
env['TMPDIR']=str(root/'target/tmp');env['XDG_CACHE_HOME']=str(home/'cache')
jars=[str(p) for p in (root/'target/maven-cache').rglob('*.jar') if 'natives-windows' not in p.name]
cp=os.pathsep.join([str(root/args.classes),str(classes),*jars])
subprocess.run(['javac','-cp',cp,'-d',str(classes),'deploy/ParcelWorkflowSmoke.java'],check=True)
with (runtime/'private-runtime.txt').open('w') as out:
 result=subprocess.run(['java','-Xmx768m','-Djava.io.tmpdir='+str(root/'target/tmp'),'-Duser.home='+str(home),'-cp',cp,'ParcelWorkflowSmoke',str(runtime)],env=env,stdout=out,stderr=out,timeout=600)
evidence=root/'dashboard/evidence'
for name in ('road-diagnostics.txt','failure.txt','parcel-failure-final.png','parcel-road-final.png'):
 p=runtime/name
 if p.exists():shutil.copyfile(p,evidence/('parcel-'+name if name.endswith('.txt') else name))
print('Parcel native exit:',result.returncode)
if result.returncode:
 failure=runtime/'failure.txt'
 if failure.exists():print(failure.read_text())
 raise SystemExit(result.returncode)
evidence=root/'dashboard/evidence';shutil.copyfile(runtime/'results.json',evidence/'parcel-playtest.json')
for p in runtime.glob('parcel-*.png'):shutil.copyfile(p,evidence/p.name)
clips=sorted((home/'.voxel-one/recordings').glob('*.mp4'))
if not clips:raise SystemExit('F10 clip missing')
clip=clips[-1]
if not 0<clip.stat().st_size<=6_000_000:raise SystemExit('F10 clip outside 6 MB limit')
shutil.copyfile(clip,evidence/'parcel-playtest.mp4')
print('PASS: parcel UI, all layouts, edge cases, construction, saves and road regression; fresh F10 media saved')
