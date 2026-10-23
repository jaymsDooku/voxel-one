#!/usr/bin/env python3
"""Run 1800 real seconds; final report exists only after assertions and resource release pass."""
import argparse, math, os, struct, subprocess, wave
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--display',required=True);a=p.parse_args()
assert a.display and a.display==os.environ.get('DISPLAY')
root=Path(__file__).resolve().parents[1];os.chdir(root)
work=root/'target/landscape-soak';work.mkdir(parents=True,exist_ok=True)
for sub in ('home','tmp','classes'):(work/sub).mkdir(exist_ok=True)
# 8 kHz mono source PCM stays within 32 MiB for all 1800 seconds. Original tones only.
rate=8000;chunk=bytearray()
for i in range(rate*30):
 t=i/rate
 if t<6:v=.55*math.sin(2*math.pi*100*t)
 elif t<12:v=.55*math.sin(2*math.pi*1000*t)
 elif t<18:v=.55*math.sin(2*math.pi*3000*t)
 elif t<24:v=(.2*sum(math.sin(2*math.pi*f*t) for f in (100,1000,3000))) if t%0.25<.10 else 0
 elif t<27:v=.025*math.sin(2*math.pi*1000*t)
 else:v=0
 chunk.extend(struct.pack('<h',int(v*32767)))
fixture=work/'thirty-minutes.wav'
with wave.open(str(fixture),'wb') as w:
 w.setparams((1,2,rate,0,'NONE',''))
 for _ in range(60):w.writeframesraw(chunk)
cp=os.pathsep.join([str(root/'target/classes'),str(work/'classes'),*[str(p) for p in (root/'target/maven-cache').rglob('*.jar') if 'natives-windows' not in p.name]])
subprocess.run(['javac','-cp',cp,'-d',str(work/'classes'),'deploy/MusicLandscapeSoak.java'],check=True)
env=os.environ.copy();env.update(DISPLAY=a.display,LIBGL_ALWAYS_SOFTWARE='1',MESA_SHADER_CACHE_DISABLE='true',ALSOFT_DRIVERS='null',TMPDIR=str(work/'tmp'),XDG_CACHE_HOME=str(work/'home/cache'));env.pop('WAYLAND_DISPLAY',None)
report=root/'dashboard/evidence/landscape-soak.json';report.unlink(missing_ok=True)
Path(str(report)+'.failure').unlink(missing_ok=True)
Path(str(report)+'.partial').unlink(missing_ok=True)
with (work/'private-runtime.txt').open('w') as log:
 result=subprocess.run(['java','-Xmx768m','-Djava.io.tmpdir='+str(work/'tmp'),'-Duser.home='+str(work/'home'),'-cp',cp,'MusicLandscapeSoak',str(report),'1800',str(fixture)],env=env,stdout=log,stderr=log,timeout=1860)
if result.returncode:
 failure=Path(str(report)+'.failure')
 if failure.exists():print(failure.read_text()[:500])
 raise SystemExit('Native soak failed; no pass recorded.')
Path(str(report)+'.partial').unlink(missing_ok=True)
print('PASS: 1800-second native soak; inspect resource samples in',report.name)
