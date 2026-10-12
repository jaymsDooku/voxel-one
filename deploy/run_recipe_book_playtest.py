#!/usr/bin/env python3
"""Run the recipe book in the production game on the assigned display."""
import argparse, os, subprocess, shutil
from pathlib import Path
root=Path(__file__).resolve().parents[1]
os.chdir(root)
p=argparse.ArgumentParser();p.add_argument('--display',required=True);p.add_argument('--network',action='store_true');args=p.parse_args()
for name in ('recipe-profile','recipe-home','recipe-smoke'):
 shutil.rmtree(root/'target'/name,ignore_errors=True);(root/'target'/name).mkdir(parents=True)
(root/'target/tmp').mkdir(exist_ok=True)
home=root/'target/recipe-home';profile=home/'.voxel-one';profile.mkdir()
(profile/'graphics.properties').write_text('version=2\npreset=LOW\nDETAIL=32\nHORIZON=512\nSHADOWS=OFF\nAA=OFF\n')
jars=[str(x) for x in (root/'target/maven-cache').rglob('*.jar') if 'natives-' not in x.name or 'natives-linux' in x.name]
cp=':'.join([str(root/'target/classes'),str(root/'target/recipe-smoke')]+jars)
subprocess.run(['javac','--release','17','-cp',cp,'-d','target/recipe-smoke','deploy/SandboxPlaytest.java','deploy/RecipeBookPlaytest.java','deploy/RecipeNetworkPlaytest.java'],check=True)
env=dict(os.environ,DISPLAY=args.display,LIBGL_ALWAYS_SOFTWARE='1');env.pop('WAYLAND_DISPLAY',None)
report=root/('dashboard/evidence/recipe-network-playtest.txt' if args.network else 'dashboard/evidence/recipe-book-playtest.txt');report.unlink(missing_ok=True)
with open(root/'target/recipe-runtime-private.txt','w') as runtime:
 result=subprocess.run(['java','-Xmx768m','-Dvoxel.gl33=true','-Dvoxel.renderScale=.5','-Djava.io.tmpdir='+str(root/'target/tmp'),'-Duser.home='+str(home),'-cp',cp,'RecipeNetworkPlaytest' if args.network else 'RecipeBookPlaytest',str(root/'dashboard/evidence')],env=env,stdout=runtime,stderr=runtime,timeout=600)
print('Game playtest exit:',result.returncode)
if report.exists():print(report.read_text().strip())
if result.returncode==0 and not args.network:
 clips=list((profile/'recordings').glob('*.mp4'))
 if clips:shutil.copy2(max(clips,key=lambda x:x.stat().st_mtime),root/'target/recipe-f10-full.mp4')
raise SystemExit(result.returncode)
