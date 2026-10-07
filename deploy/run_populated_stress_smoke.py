#!/usr/bin/env python3
"""Run on the inherited role display. Never starts X11 or reads its authentication cookie."""
import argparse, os, shutil, subprocess
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--display',required=True);args=p.parse_args()
if not args.display or args.display!=os.environ.get('DISPLAY'):
    raise SystemExit('Use the assigned inherited DISPLAY.')
root=Path(__file__).resolve().parents[1];os.chdir(root)
tmp=root/'target/populated-stress-playtest'
if tmp.exists(): shutil.rmtree(tmp)
profile=tmp/'profile';profile.mkdir(parents=True)
(root/'target/test-classes').mkdir(exist_ok=True)
jars=sorted(Path('/home/debian/.m2/repository').rglob('*.jar'))
cp=os.pathsep.join([str(root/'target/classes'),str(root/'target/test-classes')]+[str(j) for j in jars if 'natives-windows' not in j.name and 'natives-macos' not in j.name])
subprocess.run(['javac','-cp',cp,'-d','target/test-classes','deploy/CitySavesSmoke.java','deploy/StressGridSmoke.java','deploy/PopulatedStressSmoke.java'],check=True)
env=dict(os.environ,LIBGL_ALWAYS_SOFTWARE='1',MESA_SHADER_CACHE_DISABLE='true',TMPDIR=str(tmp),XDG_CACHE_HOME=str(tmp/'cache'))
env.pop('WAYLAND_DISPLAY',None)
command=['java','--enable-native-access=ALL-UNNAMED','-Xmx2g','-Duser.home='+str(profile),'-Djava.io.tmpdir='+str(tmp),'-Dorg.lwjgl.system.SharedLibraryExtractPath='+str(tmp/'natives'),'-cp',cp,'PopulatedStressSmoke',str(tmp/'worlds'),'dashboard/evidence']
with (tmp/'runtime-private.txt').open('w') as runtime:
    try: result=subprocess.run(command,env=env,stdout=runtime,stderr=runtime,timeout=600)
    except subprocess.TimeoutExpired: raise SystemExit('Native workflow timed out after 600 seconds; no pass claimed.')
if result.returncode:
    failure=tmp/'worlds/assertion-failure.txt'
    if failure.exists(): print(failure.read_text().strip())
    raise SystemExit('Native workflow failed. Full runtime log remains private.')
clips=sorted(profile.rglob('*.mp4'))
if not clips: raise SystemExit('Production F10 recording missing.')
clip=clips[-1]
if clip.stat().st_size>6_000_000: raise SystemExit('F10 recording exceeds artifact limit.')
shutil.copyfile(clip,root/'dashboard/evidence/populated-stress-main.mp4')
print('PASS: native development action, repeat edge, four populated districts, reload and original-city regression. Fresh screenshots and F10 clip saved.')
