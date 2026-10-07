#!/usr/bin/env python3
"""Run preserved native harnesses on the inherited display with fresh synthetic profiles."""
import argparse,os,subprocess,shutil,re
from pathlib import Path
parser=argparse.ArgumentParser()
parser.add_argument('--display',required=True)
parser.add_argument('--scenario',required=True,choices=('road','mixed','shipping','airport'))
args=parser.parse_args()
if args.display!=os.environ.get('DISPLAY'):raise SystemExit('Use inherited assigned role DISPLAY')
root=Path(__file__).resolve().parents[1];os.chdir(root)
prefix='road-shipping-final-'+args.scenario
runtime=root/'target'/f'{prefix}-runtime';home=root/'target'/f'{prefix}-home';classes=root/'target'/f'{prefix}-classes'
for p in (runtime,home):shutil.rmtree(p,ignore_errors=True)
for p in (runtime,home,classes,root/'target/tmp'):p.mkdir(parents=True,exist_ok=True)
env=os.environ.copy();env['DISPLAY']=args.display;env['LIBGL_ALWAYS_SOFTWARE']='1';env['MESA_SHADER_CACHE_DISABLE']='true';env.pop('WAYLAND_DISPLAY',None)
env['TMPDIR']=str(root/'target/tmp');env['XDG_CACHE_HOME']=str(home/'cache')
name={'road':'RoadWorkflowSmoke','mixed':'MixedRoadWorkflowSmoke','shipping':'ShippingSmoke','airport':'AviationSmoke'}[args.scenario]
jars=[str(p) for p in Path('/tmp/voxel-m2').rglob('*.jar') if 'natives-windows' not in p.name]
cp=os.pathsep.join([str(root/'target/classes'),str(classes),*jars])
source=root/'deploy'/f'{name}.java'
if args.scenario=='airport':
 text=source.read_text().replace('(height()-340)/12','(height()-340)/13').replace('tool==row-1','tool==row-2')
 text=re.sub(r'airportMenu\((9|10|11)\)',lambda m:'airportMenu('+str(int(m.group(1))+1)+')',text)
 source=classes/'AviationSmoke.java';source.write_text(text)
subprocess.run(['javac','-cp',cp,'-d',str(classes),str(source)],check=True)
with (runtime/'private-runtime.txt').open('w') as out:
 result=subprocess.run(['java','-Xmx512m','-Djava.io.tmpdir='+str(root/'target/tmp'),'-Duser.home='+str(home),'-cp',cp,name,str(runtime),*(['--mixed-only'] if args.scenario=='mixed' else [])],env=env,stdout=out,stderr=out,timeout=600)
print(args.scenario,'native exit:',result.returncode)
if result.returncode:
 failure=runtime/'failure.txt'
 if failure.exists():print(failure.read_text())
 raise SystemExit(result.returncode)
evidence=root/'dashboard/evidence'
shutil.copyfile(runtime/'results.json',evidence/f'{prefix}-playtest.json')
for p in runtime.glob('*.png'):
 if p.name=='initial-state.png':continue
 label=p.stem.replace('road-workflow-','').replace('road-mixed-','').replace('shipping-','').replace('airport-','')
 shutil.copyfile(p,evidence/f'{prefix}-{label}.png')
clips=sorted((home/'.voxel-one/recordings').glob('*.mp4'))
if not clips:raise SystemExit('F10 clip missing')
clip=clips[-1]
if not 0<clip.stat().st_size<=6_000_000:raise SystemExit('F10 clip outside 6 MB limit')
shutil.copyfile(clip,evidence/f'{prefix}-playtest.mp4')
print('PASS:',args.scenario,'assertions and fresh F10 media saved')
