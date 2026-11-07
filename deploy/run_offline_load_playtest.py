#!/usr/bin/env python3
"""Native offline startup checks on the assigned display; synthetic profile only."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess

parser=argparse.ArgumentParser()
parser.add_argument('--display',required=True)
args=parser.parse_args()
root=Path(__file__).resolve().parents[1]
folder=root/'target/structure-startup'
folder.mkdir(exist_ok=False)
home=folder/'home'
profile=home/'.voxel-one'
profile.mkdir(parents=True)
(profile/'graphics.properties').write_text('version=2\npreset=LOW\nDETAIL=32\nHORIZON=512\nSHADOWS=OFF\nAA=OFF\nDYNAMIC=OFF\nSCALE=0.5\nMIN_SCALE=0.5\nMAX_SCALE=0.5\n')
classes=folder/'classes'
classes.mkdir()
temporary=folder/'tmp'
temporary.mkdir()
sources=sorted((root/'src/main/java').rglob('*.java'))+[root/'deploy/OfflineLoadPlaytest.java']
hashes={str(p.relative_to(root)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sources}
(root/'dashboard/evidence/structure-startup-source.json').write_text(json.dumps({'schema':1,'source_sha256':hashes},indent=2)+'\n')
jars=[]
for repo in (root/'target/structure-m2',Path('/home/debian/.m2/repository')):
    for group in ('org/lwjgl','org/joml','org/jcodec','com/googlecode/soundlibs'):
        jars.extend(str(p) for p in (repo/group).rglob('*.jar') if 'natives-windows' not in p.name and 'natives-macos' not in p.name)
classpath=str(root/'target/classes')+':'+':'.join(jars)
subprocess.run(['javac','--release','17','-cp',classpath,'-d',str(classes),'deploy/OfflineLoadPlaytest.java'],cwd=root,check=True)
env=os.environ.copy()
env['DISPLAY']=args.display
env.pop('WAYLAND_DISPLAY',None)
env['ALSOFT_DRIVERS']='null'
env['MAGICK_TMPDIR']=str(temporary)
env['LP_NUM_THREADS']='2'
env['LIBGL_ALWAYS_SOFTWARE']='1'
for mode in ('world','replay','success'):
    command=['java','-Xmx1g','-Dvoxel.gl33=true','-Djava.io.tmpdir='+str(temporary),'-Duser.home='+str(home),'-cp',str(classes)+':'+classpath,'OfflineLoadPlaytest',mode,str(folder/(mode+'.dat')),str(root/'dashboard/evidence')]
    with (folder/(mode+'-runtime-private.txt')).open('w') as log:
        result=subprocess.run(command,cwd=root,env=env,stdout=log,stderr=log,timeout=300)
    print('Playtest: startup',mode,'exit',result.returncode,flush=True)
    if result.returncode:
        raise SystemExit(result.returncode)
    print((root/'dashboard/evidence'/('structure-startup-'+mode+'.json')).read_text(),flush=True)
if any(hashlib.sha256(p.read_bytes()).hexdigest()!=hashes[str(p.relative_to(root))] for p in sources):
    raise SystemExit('Startup source changed during playtest; rerun required')
print('All native startup source hashes match.',flush=True)
