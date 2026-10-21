#!/usr/bin/env python3
"""Preserve Asset Manager launcher/intake checks with separate synthetic landscape evidence."""
import argparse, os, subprocess, shutil
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--display',required=True);a=p.parse_args()
if not a.display or a.display!=os.environ.get('DISPLAY'):raise SystemExit('Inherited assigned DISPLAY required')
root=Path(__file__).resolve().parents[1];os.chdir(root)
for name in ('asset-manager-native.txt','asset-manager-launcher.png','asset-manager-tower-engine.png','asset-manager-inventory.png'):
 (root/'dashboard/evidence'/('landscape-restored-'+name)).unlink(missing_ok=True)
# Existing AssetManagerSmoke writes its non-private loading-stage marker here.
(root/'target/asset-native').mkdir(parents=True,exist_ok=True)
work=root/'target/landscape-asset-regression'
shutil.rmtree(work,ignore_errors=True)
for name in ('classes','home','tmp','evidence'):(work/name).mkdir(parents=True,exist_ok=True)
cp=os.pathsep.join([str(root/'target/classes'),str(work/'classes'),*[str(p) for p in (root/'target/maven-cache').rglob('*.jar') if not any(n in p.name for n in ('natives-windows','natives-macos','sources','javadoc'))]])
subprocess.run(['javac','-sourcepath','','-cp',cp,'-d',str(work/'classes'),'deploy/AssetManagerSmoke.java'],check=True)
env=os.environ.copy();env.update(LIBGL_ALWAYS_SOFTWARE='1',MESA_SHADER_CACHE_DISABLE='true',ALSOFT_DRIVERS='null',TMPDIR=str(work/'tmp'),XDG_CACHE_HOME=str(work/'home/cache'));env.pop('WAYLAND_DISPLAY',None)
with (work/'private-runtime.txt').open('w') as log:
 result=subprocess.run(['java','-Xmx768m','-Dvoxel.gl33=true','-Dvoxel.renderScale=.5','-Djava.io.tmpdir='+str(work/'tmp'),'-Duser.home='+str(work/'home'),'-cp',cp,'AssetManagerSmoke',str(work/'evidence')],env=env,stdout=log,stderr=log,timeout=180)
if result.returncode:
 failure=work/'evidence/asset-manager-native-failure.txt'
 if failure.exists():print(failure.read_text())
 raise SystemExit(result.returncode)
for name in ('asset-manager-native.txt','asset-manager-launcher.png','asset-manager-tower-engine.png','asset-manager-inventory.png'):
 shutil.copyfile(work/'evidence'/name,root/'dashboard/evidence'/('landscape-restored-'+name))
print('PASS: restored production Asset Manager chooser, command, model intake, real hotbar/inventory input and persistence')
