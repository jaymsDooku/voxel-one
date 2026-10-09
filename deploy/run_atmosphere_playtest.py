#!/usr/bin/env python3
"""Run the engine's atmosphere workflow on the assigned display and synthetic profile."""
import argparse,os,pathlib,subprocess
p=argparse.ArgumentParser();p.add_argument('--display',required=True);p.add_argument('--regression',action='store_true');p.add_argument('--main',action='store_true');p.add_argument('--limb',action='store_true');p.add_argument('--performance',action='store_true');p.add_argument('--performance-quality',choices=['LOW','MEDIUM','HIGH']);a=p.parse_args()
root=pathlib.Path(__file__).resolve().parents[1]
for name in ('atmosphere-home','atmosphere-tmp','atmosphere-native'):(root/'target'/name).mkdir(parents=True,exist_ok=True)
# Public runtime libraries only. No runner files, credentials or account data.
library=pathlib.Path('/home/debian/.m2/repository')
jars=[]
for group in ('org/lwjgl','org/joml','org/jcodec'):
 jars.extend(str(x) for x in (library/group).rglob('*.jar') if 'natives-' not in x.name or 'natives-linux' in x.name)
import shutil
runtime=root/'target/atmosphere-runtime-classes'
shutil.rmtree(runtime,ignore_errors=True);shutil.copytree(root/'target/classes',runtime)
classpath=os.pathsep.join([str(runtime),*jars])
env=os.environ.copy();env['DISPLAY']=a.display;env['LIBGL_ALWAYS_SOFTWARE']='1';env['MESA_GL_VERSION_OVERRIDE']='3.3';env['MESA_GLSL_VERSION_OVERRIDE']='330';env.pop('WAYLAND_DISPLAY',None);env['ALSOFT_DRIVERS']='null'
source='AtmospherePerformance.java' if a.performance else 'AtmosphereMainPlaytest.java' if a.main else 'RenderingSmoke.java' if a.regression else 'AtmospherePlaytest.java'
output=root/'target/atmosphere-main' if a.main else root/'target/atmosphere-render-regression' if a.regression else root/'dashboard/evidence'
if a.main:
 import shutil
 shutil.rmtree(root/'target/atmosphere-home',ignore_errors=True);(root/'target/atmosphere-home').mkdir()
 (root/'target/atmosphere-main-profile').mkdir(exist_ok=True)
 for saved in (root/'target/atmosphere-main-profile').glob('synthetic-city.dat*'):saved.unlink(missing_ok=True)
command=['java',*(['-Dvoxel.limbOnly=true'] if a.limb else []),*(['-Dvoxel.performanceQuality='+a.performance_quality] if a.performance_quality else []),'-Djava.io.tmpdir='+str(root/'target/atmosphere-tmp'),'-Duser.home='+str(root/'target/atmosphere-home'),'-Dorg.lwjgl.system.SharedLibraryExtractPath='+str(root/'target/atmosphere-native'),'-Dvoxel.msaa=1','-Dvoxel.gl33=true','-cp',classpath,str(root/'deploy'/source),str(output)]
logfile=root/'target'/('atmosphere-regression-runtime-private.txt' if a.regression else 'atmosphere-runtime-private.txt')
with logfile.open('w') as log:
 try:result=subprocess.run(command,cwd=root,env=env,stdout=log,stderr=log,timeout=900 if a.performance else 420 if a.main or a.limb else 300)
 except subprocess.TimeoutExpired:
  print('Playtest: timed out; partial reports retained; no pass claimed.');raise SystemExit(124)
print('Playtest: GL 3.3 atmosphere; assigned X11 display; isolated synthetic profile; exit',result.returncode)
if result.returncode:
 with logfile.open('rb') as diagnostics:
  diagnostics.seek(0,2);diagnostics.seek(max(0,diagnostics.tell()-8192));tail=diagnostics.read(8192).decode('utf-8','replace')
 for line in tail.splitlines():
  if line.startswith(('Exception','java.lang.')) or 'error:' in line or 'AssertionError:' in line:print(line[:400])
raise SystemExit(result.returncode)
