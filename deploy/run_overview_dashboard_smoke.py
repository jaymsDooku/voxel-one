#!/usr/bin/env python3
"""Bounded native Overview run on the assigned display and a synthetic profile."""
import argparse,os,subprocess,shutil
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--display',required=True);args=p.parse_args()
if not args.display or args.display!=os.environ.get('DISPLAY'):raise SystemExit('Use the assigned inherited DISPLAY.')
root=Path(__file__).resolve().parents[1];os.chdir(root)
tmp=root/'target/history-overview'
if tmp.exists():shutil.rmtree(tmp)
tmp.mkdir(parents=True)
profile=tmp/'profile';profile.mkdir()
cp=os.pathsep.join([str(root/'target/classes'),str(root/'target/test-classes')]+[str(j) for j in Path('/home/debian/.m2/repository').rglob('*.jar') if 'natives-windows' not in j.name and 'natives-macos' not in j.name])
subprocess.run(['javac','-cp',cp,'-d','target/test-classes','deploy/CitySavesSmoke.java','deploy/OverviewDashboardSmoke.java'],check=True)
env=dict(os.environ,LIBGL_ALWAYS_SOFTWARE='1',MESA_SHADER_CACHE_DISABLE='true',TMPDIR=str(tmp),XDG_CACHE_HOME=str(tmp/'cache'))
env.pop('WAYLAND_DISPLAY',None)
command=['java','--enable-native-access=ALL-UNNAMED','-Xmx2g','-Duser.home='+str(profile),'-Djava.io.tmpdir='+str(tmp),'-Dorg.lwjgl.system.SharedLibraryExtractPath='+str(tmp/'natives'),'-cp',cp,'OverviewDashboardSmoke',str(tmp/'worlds'),'dashboard/evidence']
with (tmp/'runtime-private.txt').open('w') as runtime:
    result=subprocess.run(command,env=env,stdout=runtime,stderr=runtime,timeout=180)
if result.returncode:
    failure=tmp/'worlds/assertion-failure.txt'
    if failure.exists():print(failure.read_text().strip())
    raise SystemExit('Overview playtest failed; no pass claimed. Full runtime output remains private.')
clips=list(profile.rglob('*.mp4'))
if len(clips)!=1 or clips[0].stat().st_size>6_000_000:raise SystemExit('Overview F10 clip missing or exceeds 6 MB.')
shutil.copyfile(clips[0],root/'dashboard/evidence/history-overview-f10.mp4')
print('PASS: 57 Overview steps, 12 bottom-card links at normal/small/scaled sizes; fresh screenshots and scaled F10 clip saved.')
