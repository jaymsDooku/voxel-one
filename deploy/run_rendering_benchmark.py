#!/usr/bin/env python3
"""Portable Windows/Linux deterministic replay, isolated from the player's saves/preferences."""
import argparse,os,subprocess,shutil
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--display');p.add_argument('--java',default='java');p.add_argument('--route',choices=['GROUND','FLIGHT','TELEPORT','ISOMETRIC','CITY','INTERIOR','FRACTIONAL'],default='GROUND');p.add_argument('--preset',choices=['LOW','BALANCED','HIGH','ULTRA'],default='BALANCED');p.add_argument('--runs',type=int,default=3);p.add_argument('--warmup',type=int,default=120);p.add_argument('--frames',type=int,default=600);p.add_argument('--streaming',action='store_true');p.add_argument('--gl33',action='store_true');a=p.parse_args()
if not 1<=a.runs<=10 or not 0<=a.warmup<=10000 or not 1<=a.frames<=60000:p.error('runs 1..10, warm-up 0..10000, frames 1..60000')
root=Path(__file__).resolve().parents[1];os.chdir(root);folder=root/'target'/'performance-replay'/a.route;folder.mkdir(parents=True,exist_ok=True)
env=os.environ.copy()
if a.display:env['DISPLAY']=a.display;env.pop('WAYLAND_DISPLAY',None)
env['ALSOFT_DRIVERS']='null'
for run in range(a.runs):
 home=folder/f'home-{run+1}';shutil.rmtree(home,ignore_errors=True);home.mkdir();out=folder/f'run-{run+1}';out.mkdir(exist_ok=True);(root/'target/tmp').mkdir(parents=True,exist_ok=True)
 command=[a.java,'-Duser.home='+str(home),'-Djava.io.tmpdir='+str(root/'target/tmp')]
 if a.gl33:command+=['-Dvoxel.gl33=true'];env['MESA_GL_VERSION_OVERRIDE']='3.3';env['MESA_GLSL_VERSION_OVERRIDE']='330'
 command+=['-cp','target/voxel-one-1.0-SNAPSHOT-client.jar','deploy/RenderingBenchmark.java',str(out),a.route,str(a.warmup),str(a.frames),str(a.streaming).lower(),a.preset]
 # Raw synthetic runtime output is ignored. Only fixture assertions reach the caller.
 with (out/'runtime-private.txt').open('w') as log:result=subprocess.run(command,env=env,stdout=log,stderr=log,timeout=1800)
 print(f'Playtest: {a.route} replay run {run+1}/{a.runs}; exit {result.returncode}')
 if result.returncode:raise SystemExit(result.returncode)
 reports=list(out.glob('performance-*.json'))
 if not reports:raise SystemExit('No engine performance report produced')
print('Reports:',folder.relative_to(root))
