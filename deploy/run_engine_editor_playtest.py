#!/usr/bin/env python3
"""Run on assigned inherited X11 display. Never starts an X server."""
import argparse, os, subprocess
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--display',required=True);a=p.parse_args()
root=Path(__file__).resolve().parents[1];os.chdir(root)
env=os.environ.copy();env['DISPLAY']=a.display;env['LIBGL_ALWAYS_SOFTWARE']='1'
env.pop('WAYLAND_DISPLAY',None)
for path in ('target/editor-driver','target/editor-profile','target/editor-home','target/tmp'):Path(path).mkdir(parents=True,exist_ok=True)
jdk=Path('/usr/lib/jvm/jdk-21.0.5-oracle-x64/bin')
jars=[str(p) for p in Path('target/maven-cache').rglob('*.jar') if 'natives-windows' not in p.name]
cp=':'.join(['target/classes','target/editor-driver']+jars)
subprocess.run([str(jdk/'javac'),'-cp',cp,'-d','target/editor-driver','deploy/EngineEditorPlaytest.java'],check=True)
for name in ('engine-editor-workspace.png','engine-editor-city-play.png','engine-editor-assets.png','engine-editor-playtest-result.txt'):
    (root/'dashboard/evidence'/name).unlink(missing_ok=True)
with open('target/editor-runtime-private.txt','w') as log:
    result=subprocess.run([str(jdk/'java'),'-Djava.io.tmpdir='+str(root/'target/tmp'),'-Duser.home='+str(root/'target/editor-home'),'-cp',cp,'EngineEditorPlaytest',str(root/'dashboard/evidence')],env=env,stdout=log,stderr=log,timeout=180)
print('Native editor Playtest exit:',result.returncode)
raise SystemExit(result.returncode)
