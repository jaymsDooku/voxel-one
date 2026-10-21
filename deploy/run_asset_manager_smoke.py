#!/usr/bin/env python3
"""Run only on inherited role display, with isolated synthetic profiles."""
import argparse, os, subprocess, shutil
from pathlib import Path
parser=argparse.ArgumentParser();parser.add_argument('--display',required=True);args=parser.parse_args()
if not args.display or args.display!=os.environ.get('DISPLAY'):raise SystemExit('Inherited assigned DISPLAY required')
root=Path(__file__).resolve().parents[1];os.chdir(root)
work=root/'target/asset-native'
if (work/'home').exists():shutil.rmtree(work/'home')
for name in ('classes','home','tmp'):(work/name).mkdir(parents=True,exist_ok=True)
out=root/'dashboard/evidence'
for name in ('asset-manager-native.txt','asset-manager-native-failure.txt'):(out/name).unlink(missing_ok=True)
repo=Path('/home/debian/.m2/repository')
jars=[]
for group in ('org/lwjgl','org/joml','org/jcodec'):
    jars.extend(str(p) for p in (repo/group).rglob('*.jar') if not any(x in p.name for x in ('natives-windows','natives-macos','sources','javadoc')))
cp=os.pathsep.join([str(root/'target/classes'),str(work/'classes'),*jars])
subprocess.run(['javac','-cp',cp,'-d',str(work/'classes'),'deploy/AssetManagerSmoke.java'],check=True)
env=os.environ.copy();env.update(LIBGL_ALWAYS_SOFTWARE='1',MESA_SHADER_CACHE_DISABLE='true',ALSOFT_DRIVERS='null',TMPDIR=str(work/'tmp'),XDG_CACHE_HOME=str(work/'home/cache'));env.pop('WAYLAND_DISPLAY',None)
with (work/'private-runtime.txt').open('w') as runtime:
    result=subprocess.run(['java','-Xmx768m','-Dvoxel.gl33=true','-Dvoxel.renderScale=.5','-Djava.io.tmpdir='+str(work/'tmp'),'-Duser.home='+str(work/'home'),'-cp',cp,'AssetManagerSmoke',str(out)],env=env,stdout=runtime,stderr=runtime,timeout=180)
print('Native asset workflow exit:',result.returncode)
if result.returncode:
    failure=out/'asset-manager-native-failure.txt'
    if failure.exists():print(failure.read_text())
    raise SystemExit(result.returncode)
clips=sorted((work/'home/.voxel-one/recordings').glob('*.mp4'))
if not clips:raise SystemExit('F10 clip missing')
clip=clips[-1]
if clip.stat().st_size>6_000_000:raise SystemExit('F10 clip exceeds 6 MB')
shutil.copyfile(clip,out/'asset-manager-inventory.mp4')
print('PASS: native asset workflow and F10 media',clip.stat().st_size,'bytes')
