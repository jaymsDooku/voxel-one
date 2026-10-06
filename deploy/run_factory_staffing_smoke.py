#!/usr/bin/env python3
"""Native automatic factory regression; assigned display and synthetic profile only."""
import argparse, os, subprocess, shutil
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--display',required=True);p.add_argument('--max-spacing',action='store_true');a=p.parse_args()
if not a.display or a.display!=os.environ.get('DISPLAY'):raise SystemExit('Inherited assigned DISPLAY required.')
r=Path(__file__).resolve().parents[1];os.chdir(r)
for f in ('target/factory-home','target/factory-native'):shutil.rmtree(r/f,ignore_errors=True)
for f in ('target/factory-home','target/factory-native','target/spacing-smoke','target/tmp'):(r/f).mkdir(parents=True,exist_ok=True)
cp=os.pathsep.join([str(r/'target/classes'),str(r/'target/spacing-smoke'),*[str(f) for f in (r/'target/maven-cache').rglob('*.jar') if 'natives-windows' not in f.name]])
j='/usr/lib/jvm/jdk-21.0.5-oracle-x64/bin/'
subprocess.run([j+'javac','-cp',cp,'-d','target/spacing-smoke','deploy/RoadSpacingSmoke.java','deploy/FactoryStaffingSmoke.java'],check=True)
e=os.environ.copy();e.update(DISPLAY=a.display,LIBGL_ALWAYS_SOFTWARE='1',MESA_SHADER_CACHE_DISABLE='true',TMPDIR=str(r/'target/tmp'));e.pop('WAYLAND_DISPLAY',None)
with (r/'target/factory-native/private-runtime.txt').open('w') as log:
 x=subprocess.run([j+'java','-Djava.io.tmpdir='+str(r/'target/tmp'),'-Duser.home='+str(r/'target/factory-home'),'-cp',cp,'FactoryStaffingSmoke',str(r/'target/factory-native')]+(['2'] if a.max_spacing else []),env=e,stdout=log,stderr=log,timeout=180)
print('Factory native playtest exit:',x.returncode)
if x.returncode:raise SystemExit(x.returncode)
for src,dst in [('results.json','road-spacing-factory-results.json'),('factory.png','road-spacing-factory.png')]:shutil.copyfile(r/'target/factory-native'/src,r/'dashboard/evidence'/dst)
f=max((r/'target/factory-home/.voxel-one/recordings').glob('*.mp4'),key=lambda f:f.stat().st_mtime)
if f.stat().st_size>=6_000_000:raise SystemExit('F10 video exceeds 6 MB.')
shutil.copyfile(f,r/'dashboard/evidence/road-spacing-factory.mp4');print('PASS: automatic staffing, commute, production and trade; fresh F10 evidence saved.')
