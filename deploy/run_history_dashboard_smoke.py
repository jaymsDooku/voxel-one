#!/usr/bin/env python3
"""Uses the inherited role display and an isolated synthetic game profile."""
import argparse,os,subprocess,shutil
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--display',required=True);args=p.parse_args()
if not args.display or args.display!=os.environ.get('DISPLAY'):raise SystemExit('Use the assigned inherited DISPLAY.')
root=Path(__file__).resolve().parents[1];os.chdir(root)
tmp=root/'target/history-native'
if tmp.exists():shutil.rmtree(tmp)
tmp.mkdir(parents=True)
profile=tmp/'profile';profile.mkdir(exist_ok=True)
cp=os.pathsep.join([str(root/'target/classes'),str(root/'target/test-classes')]+[str(j) for j in Path('/home/debian/.m2/repository').rglob('*.jar') if 'natives-windows' not in j.name and 'natives-macos' not in j.name])
subprocess.run(['javac','-cp',cp,'-d','target/test-classes','deploy/CitySavesSmoke.java','deploy/HistoryDashboardSmoke.java','deploy/SandboxHistoryRegressionSmoke.java'],check=True)
env=dict(os.environ,LIBGL_ALWAYS_SOFTWARE='1',MESA_SHADER_CACHE_DISABLE='true',TMPDIR=str(tmp),XDG_CACHE_HOME=str(tmp/'cache'))
env.pop('WAYLAND_DISPLAY',None)
sandbox_profile=tmp/'sandbox-profile';sandbox_profile.mkdir()
sandbox_command=['java','--enable-native-access=ALL-UNNAMED','-Xmx2g','-Duser.home='+str(sandbox_profile),'-Djava.io.tmpdir='+str(tmp),'-Dorg.lwjgl.system.SharedLibraryExtractPath='+str(tmp/'natives'),'-cp',cp,'SandboxHistoryRegressionSmoke',str(tmp/'sandbox-worlds'),'dashboard/evidence']
with (tmp/'sandbox-runtime-private.txt').open('w') as runtime:
    sandbox_result=subprocess.run(sandbox_command,env=env,stdout=runtime,stderr=runtime,timeout=180)
if sandbox_result.returncode:raise SystemExit('Sandbox F9 regression failed; no pass claimed. Full runtime output remains private.')
command=['java','--enable-native-access=ALL-UNNAMED','-Xmx2g','-Duser.home='+str(profile),'-Djava.io.tmpdir='+str(tmp),'-Dorg.lwjgl.system.SharedLibraryExtractPath='+str(tmp/'natives'),'-cp',cp,'HistoryDashboardSmoke',str(tmp/'worlds'),'dashboard/evidence']
with (tmp/'runtime-private.txt').open('w') as runtime:
    result=subprocess.run(command,env=env,stdout=runtime,stderr=runtime,timeout=600)
if result.returncode:
    failure=tmp/'worlds/assertion-failure.txt'
    if failure.exists():print(failure.read_text().strip())
    raise SystemExit('Native history playtest failed; no pass claimed. Full runtime output remains private.')
clips=sorted(profile.rglob('*.mp4'))
if not clips:raise SystemExit('F10 recording missing.')
if len(clips)!=2:raise SystemExit('Expected two actual F10 segments across application reload.')
for clip,name in zip(clips,('history-dashboard-f10.mp4','history-company-f10.mp4')):
    if clip.stat().st_size>6_000_000:raise SystemExit('F10 segment exceeds 6 MB.')
    shutil.copyfile(clip,root/'dashboard/evidence'/name)
print('PASS: native dashboard, time range, comparison, tooltip, fixed/Live, no-data, reload, 900x600 and world-control regression. Fresh media saved.')
