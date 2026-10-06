#!/usr/bin/env python3
"""Run the native city-scale workflow on the assigned inherited display. Never starts an X server."""
import argparse, json, os, shutil, subprocess
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--display',required=True);args=p.parse_args()
if args.display!=os.environ.get('DISPLAY') or not args.display:
    raise SystemExit('Use the assigned inherited DISPLAY.')
root=Path(__file__).resolve().parents[1];os.chdir(root)
tmp=root/'target/regional-playtest';tmp.mkdir(parents=True,exist_ok=True)
profile=tmp/'profile'
if profile.exists(): shutil.rmtree(profile)
profile.mkdir()
for name in ('world.dat','world.dat.city','world.dat.jeep'):
    (tmp/name).unlink(missing_ok=True)
jars=sorted((Path('/home/debian/.m2/repository')).rglob('*.jar'))
cp=os.pathsep.join([str(root/'target/classes'),str(root/'target/test-classes')]+[str(j) for j in jars if 'natives-windows' not in j.name and 'natives-macos' not in j.name])
compiled=subprocess.run(['javac','-cp',cp,'-d',str(root/'target/test-classes'),str(root/'deploy/SpecialBuildingsSmoke.java'),str(root/'deploy/RegionalMainPlaytest.java')])
if compiled.returncode: raise SystemExit('Compile the game and test classes before running the native driver.')
env=dict(os.environ,LIBGL_ALWAYS_SOFTWARE='1',MESA_SHADER_CACHE_DISABLE='true',TMPDIR=str(tmp),XDG_CACHE_HOME=str(tmp/'cache'))
out=root/'dashboard/evidence';out.mkdir(exist_ok=True)
command=['java','--enable-native-access=ALL-UNNAMED','-Xmx2g','-Duser.home='+str(profile),'-Djava.io.tmpdir='+str(tmp),'-Dorg.lwjgl.system.SharedLibraryExtractPath='+str(tmp/'natives'),'-cp',cp,'dev.jayms.RegionalMainPlaytest',str(out)]
# Runtime logs stay private under target; only the explicit assertion report is evidence.
with (tmp/'runtime-private.txt').open('w') as log:
    try: result=subprocess.run(command,env=env,stdout=log,stderr=log,timeout=180)
    except subprocess.TimeoutExpired: raise SystemExit('Native workflow timed out after 180 seconds.')
if result.returncode: raise SystemExit('Native workflow failed; inspect only relevant test exception lines in target/regional-playtest/runtime-private.txt.')
clips=sorted(profile.rglob('*.mp4'))
if not clips: raise SystemExit('F10 recording was not produced.')
clip=clips[-1]
if clip.stat().st_size>6_000_000: raise SystemExit('F10 recording exceeds evidence limit.')
shutil.copyfile(clip,out/'regional-scale-main.mp4')
print('PASS: native workflow; screenshots and F10 clip saved under dashboard/evidence.')
