#!/usr/bin/env python3
import argparse,os,subprocess,shutil
from pathlib import Path
root=Path(__file__).resolve().parents[1];os.chdir(root)
p=argparse.ArgumentParser();p.add_argument('--display',required=True);p.add_argument('--baseline',action='store_true');a=p.parse_args()
(root/'target/tool-harness').mkdir(parents=True,exist_ok=True)
cp='target/classes:target/tool-harness:'+':'.join(str(j) for j in (root/'target/maven-cache').rglob('*.jar') if 'natives-windows' not in j.name)
subprocess.run(['javac','--release','17','-cp',cp,'-d','target/tool-harness','deploy/ToolGripRenderCheck.java','deploy/ToolGripNetworkFixture.java'],check=True)
if a.baseline:
    baseline=root/'target/tool-baseline-src/dev/jayms/player';baseline.mkdir(parents=True,exist_ok=True)
    (baseline/'PlayerModel.java').write_bytes(subprocess.check_output(['git','show','HEAD:src/main/java/dev/jayms/player/PlayerModel.java']))
    (root/'target/tool-baseline').mkdir(exist_ok=True)
    subprocess.run(['javac','--release','17','-cp',cp,'-d','target/tool-baseline',str(baseline/'PlayerModel.java')],check=True)
    cp='target/tool-baseline:'+cp
env=dict(os.environ,DISPLAY=a.display,LIBGL_ALWAYS_SOFTWARE='1');env.pop('WAYLAND_DISPLAY',None)
with open('target/tool-render-runtime-private.txt','w') as log:
    r=subprocess.run(['/usr/lib/jvm/jdk-21.0.5-oracle-x64/bin/java','-Dvoxel.gl33=true','-Djava.io.tmpdir='+str(root/'target/tmp'),'-cp',cp,'ToolGripRenderCheck','target/tool-baseline-media' if a.baseline else 'dashboard/evidence','baseline' if a.baseline else 'final'],env=env,stdout=log,stderr=log,timeout=180)
if a.baseline:
    for t in (191,192):
        for v in range(3):shutil.copy2(root/f'target/tool-baseline-media/grip-render-{t}-view{v}.png',root/f'dashboard/evidence/tool-grip-baseline-{t}-view{v}.png')
    raise SystemExit(r.returncode)
report=root/'dashboard/evidence/tool-render-result.txt' 
if report.exists():print(report.read_text())
clips=list((root/'target/tool-render-recordings').glob('*.mp4'))
if clips:shutil.copy2(max(clips,key=lambda j:j.stat().st_mtime),root/'dashboard/evidence/tool-grip-render.mp4')
raise SystemExit(r.returncode)
