#!/usr/bin/env python3
"""Capture current held grips on the assigned X11 display using synthetic local data."""
import argparse, os, subprocess, shutil
from pathlib import Path
root=Path(__file__).resolve().parents[1]
os.chdir(root)
p=argparse.ArgumentParser(); p.add_argument('--display',required=True); p.add_argument('--phase',choices=['before','after'],required=True); p.add_argument('--output',default='dashboard/evidence'); p.add_argument('--compatibility',action='store_true'); p.add_argument('--quick',action='store_true'); p.add_argument('--framing',action='store_true'); a=p.parse_args()
for d in ('target/tool-profile','target/tool-home','target/tool-harness','target/tmp'): (root/d).mkdir(parents=True,exist_ok=True)
profile=root/'target/tool-home/.voxel-one'; profile.mkdir(parents=True,exist_ok=True)
(profile/'graphics.properties').write_text('version=2\npreset=LOW\nDETAIL=32\nHORIZON=512\nSHADOWS=OFF\nAA=OFF\n')
jars=[str(j) for j in (root/'target/maven-cache').rglob('*.jar') if 'natives-windows' not in j.name]
cp='target/classes:target/tool-harness:'+':'.join(jars)
subprocess.run(['javac','--release','17','-cp',cp,'-d','target/tool-harness','deploy/SandboxPlaytest.java','deploy/ToolGripPlaytest.java','deploy/ToolGripCompatibilityPlaytest.java'],check=True)
if a.phase=='before':
    baseline=root/'target/tool-baseline-src/dev/jayms/player'; baseline.mkdir(parents=True,exist_ok=True)
    sources=[]
    for name in ('PlayerModel','HeldToolPose','PlayerAnimation'):
        source=baseline/(name+'.java')
        source.write_bytes(subprocess.check_output(['git','show','HEAD:src/main/java/dev/jayms/player/'+name+'.java']))
        sources.append(str(source))
    (root/'target/tool-baseline').mkdir(exist_ok=True)
    subprocess.run(['javac','--release','17','-cp',cp,'-d','target/tool-baseline',*sources],check=True)
    cp='target/tool-baseline:'+cp
env=dict(os.environ,DISPLAY=a.display,LIBGL_ALWAYS_SOFTWARE='1'); env.pop('WAYLAND_DISPLAY',None)
with open('target/tool-runtime-private.txt','w') as log:
    r=subprocess.run(['/usr/lib/jvm/jdk-21.0.5-oracle-x64/bin/java','-Xmx768m','-Dvoxel.gl33=true','-Dvoxel.renderScale=.5','-Duser.home='+str(root/'target/tool-home'),'-Djava.io.tmpdir='+str(root/'target/tmp'),'-cp',cp,'ToolGripCompatibilityPlaytest' if a.compatibility else 'ToolGripPlaytest',a.output,a.phase,'framing' if a.framing else 'quick' if a.quick else 'all'],env=env,stdout=log,stderr=log,timeout=600)
if r.returncode != 0: raise SystemExit(r.returncode)
report=root/a.output/('compatibility.txt' if a.compatibility else 'tool-grip-'+a.phase+'-result.txt')
if report.exists(): print(report.read_text())
clips=list((profile/'recordings').glob('*.mp4'))
if a.phase=='after' and not a.compatibility and not a.framing and clips: shutil.copy2(max(clips,key=lambda j:j.stat().st_mtime),root/a.output/'tool-grip-after.mp4')
raise SystemExit(r.returncode)
