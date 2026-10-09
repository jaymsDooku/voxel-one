#!/usr/bin/env python3
"""Run the engine's atmosphere workflow on the assigned display and synthetic profile."""
import argparse,os,pathlib,subprocess
p=argparse.ArgumentParser();p.add_argument('--display',required=True);p.add_argument('--regression',action='store_true');a=p.parse_args()
root=pathlib.Path(__file__).resolve().parents[1]
for name in ('atmosphere-home','atmosphere-tmp','atmosphere-native'):(root/'target'/name).mkdir(parents=True,exist_ok=True)
# Public runtime libraries only. No runner files, credentials or account data.
library=pathlib.Path('/home/debian/.m2/repository')
jars=[]
for group in ('org/lwjgl','org/joml','org/jcodec'):
 jars.extend(str(x) for x in (library/group).rglob('*.jar') if 'natives-' not in x.name or 'natives-linux' in x.name)
classpath=os.pathsep.join([str(root/'target/classes'),*jars])
env=os.environ.copy();env['DISPLAY']=a.display;env['LIBGL_ALWAYS_SOFTWARE']='1';env['MESA_GL_VERSION_OVERRIDE']='3.3';env['MESA_GLSL_VERSION_OVERRIDE']='330';env.pop('WAYLAND_DISPLAY',None)
source='RenderingSmoke.java' if a.regression else 'AtmospherePlaytest.java'
output=root/'target/atmosphere-render-regression' if a.regression else root/'dashboard/evidence'
command=['java','-Djava.io.tmpdir='+str(root/'target/atmosphere-tmp'),'-Duser.home='+str(root/'target/atmosphere-home'),'-Dorg.lwjgl.system.SharedLibraryExtractPath='+str(root/'target/atmosphere-native'),'-Dvoxel.msaa=1','-Dvoxel.gl33=true','-cp',classpath,str(root/'deploy'/source),str(output)]
logfile=root/'target'/('atmosphere-regression-runtime-private.txt' if a.regression else 'atmosphere-runtime-private.txt')
with logfile.open('w') as log:
 result=subprocess.run(command,cwd=root,env=env,stdout=log,stderr=log,timeout=240)
print('Playtest: GL 3.3 atmosphere; assigned X11 display; isolated synthetic profile; exit',result.returncode)
if result.returncode:
 for line in logfile.read_text().splitlines():
  if line.startswith(('Exception','java.lang.')) or 'error:' in line or 'AssertionError:' in line:print(line[:400])
raise SystemExit(result.returncode)
