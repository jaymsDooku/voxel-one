#!/usr/bin/env python3
"""Matched native rendering checks, isolated synthetic audio/profile, assigned display."""
import argparse, json, math, os, struct, subprocess, wave
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--display',required=True);p.add_argument('--label',required=True);p.add_argument('--baseline',action='store_true');p.add_argument('--start',type=int,default=1);p.add_argument('--seconds',type=float,default=20);p.add_argument('--runs',type=int,default=3);a=p.parse_args()
assert a.display and a.display==os.environ.get('DISPLAY')
root=Path(__file__).resolve().parents[1];os.chdir(root)
work=root/'target/landscape-bench';work.mkdir(parents=True,exist_ok=True)
for sub in ('home','tmp','classes'):(work/sub).mkdir(exist_ok=True)
fixture=work/'contrasts.wav'
with wave.open(str(fixture),'wb') as w:
 w.setparams((2,2,22050,0,'NONE',''))
 for i in range(22050*24):
  t=i/22050;hz=100 if t<6 or t>=18 else 1000 if t<12 else 6000
  amp=0 if t>=22 else (.6 if t%0.5<.14 else 0) if t>=18 else .45
  v=int(32767*amp*math.sin(2*math.pi*hz*t));w.writeframesraw(struct.pack('<hh',v,-v))
cp=os.pathsep.join([str(root/'target/classes'),str(work/'classes'),*[str(p) for p in (root/'target/maven-cache').rglob('*.jar') if 'natives-windows' not in p.name]])
if a.baseline:
 baseline=work/'baseline';baseline.mkdir(exist_ok=True)
 sources=[]
 for name in ('src/main/java/dev/jayms/music/MusicVisualizer.java','src/main/java/dev/jayms/music/MusicAnalysis.java','src/main/java/dev/jayms/Mesh.java','src/main/resources/shaders/music.vert','src/main/resources/shaders/music.frag'):
  target=baseline/(('shaders/'+Path(name).name) if '/resources/' in name else Path(name).name)
  target.parent.mkdir(parents=True,exist_ok=True)
  target.write_bytes(subprocess.check_output(['git','show',json.loads((root/'dashboard/evidence/landscape-source-manifest.json').read_text())['baselineHead']+':'+name]))
  if name.endswith('.java'):sources.append(str(target))
 subprocess.run(['javac','-cp',cp,'-d',str(baseline),*sources],check=True)
 cp=str(baseline)+os.pathsep+cp
subprocess.run(['javac','-sourcepath','','-cp',cp,'-d',str(work/'classes'),'deploy/MusicLandscapeBench.java'],check=True)
env=os.environ.copy();env.update(DISPLAY=a.display,LIBGL_ALWAYS_SOFTWARE='1',MESA_SHADER_CACHE_DISABLE='true',ALSOFT_DRIVERS='null',TMPDIR=str(work/'tmp'),XDG_CACHE_HOME=str(work/'home/cache'));env.pop('WAYLAND_DISPLAY',None)
for run in range(a.start-1,a.start-1+a.runs):
 report=root/f'dashboard/evidence/landscape-{a.label}-{run+1}.json'
 with (work/'private-runtime.txt').open('w') as log:
  result=subprocess.run(['java','-Xmx768m','-Djava.io.tmpdir='+str(work/'tmp'),'-Duser.home='+str(work/'home'),'-cp',cp,'MusicLandscapeBench',str(report),str(a.seconds),str(fixture)],env=env,stdout=log,stderr=log,timeout=a.seconds+45)
 if result.returncode:raise SystemExit('Native benchmark failed; no pass recorded')
 print(report.name,report.read_text().strip(),flush=True)
