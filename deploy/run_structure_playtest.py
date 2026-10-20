#!/usr/bin/env python3
"""Use only the assigned inherited display, with an isolated synthetic profile."""
import argparse
import json
import hashlib
import os
from pathlib import Path
import shutil
import subprocess

parser=argparse.ArgumentParser();parser.add_argument('--display',required=True);args=parser.parse_args()
root=Path(__file__).resolve().parents[1]
source_files=sorted((root/'src/main/java').rglob('*.java'))+[root/'deploy/StructurePlaytest.java']
source_hashes={str(p.relative_to(root)):hashlib.sha256(p.read_bytes()).hexdigest() for p in source_files}
source_manifest=dict(schema=1,source_sha256=source_hashes,scope='Production Java plus native workflow harness; isolated synthetic profile')
(root/'dashboard/evidence/structure-native-source.json').write_text(json.dumps(source_manifest,indent=2)+'\n')
folder=root/'target/structure-native';shutil.rmtree(folder,ignore_errors=True);folder.mkdir(parents=True)
home=folder/'home';profile=home/'.voxel-one';profile.mkdir(parents=True)
(profile/'graphics.properties').write_text('version=2\npreset=LOW\nDETAIL=32\nHORIZON=512\nSHADOWS=OFF\nAA=OFF\nDYNAMIC=OFF\nSCALE=0.5\nMIN_SCALE=0.5\nMAX_SCALE=0.5\n')
for name in ('structure-native-result.json','structure-native-failure.txt'):
    (root/'dashboard/evidence'/name).unlink(missing_ok=True)
classes=folder/'classes';classes.mkdir();temporary=folder/'tmp';temporary.mkdir()
jars=[]
for repo in (root/'target/structure-m2', Path('/home/debian/.m2/repository')):
    for group in ('org/lwjgl','org/joml','org/jcodec','com/googlecode/soundlibs'):
        jars.extend(str(p) for p in (repo/group).rglob('*.jar') if 'natives-windows' not in p.name and 'natives-macos' not in p.name)
classpath=str(root/'target/classes')+':'+':'.join(jars)
subprocess.run(['javac','--release','17','-cp',classpath,'-d',str(classes),'deploy/StructurePlaytest.java'],cwd=root,check=True)
env=os.environ.copy();env['DISPLAY']=args.display;env.pop('WAYLAND_DISPLAY',None);env['ALSOFT_DRIVERS']='null';env['MAGICK_TMPDIR']=str(temporary);env['LP_NUM_THREADS']='2';env['LIBGL_ALWAYS_SOFTWARE']='1'
command=['java','-Xmx1g','-Dvoxel.gl33=true','-Djava.io.tmpdir='+str(temporary),'-Duser.home='+str(home),'-cp',str(classes)+':'+classpath,'StructurePlaytest',str(root/'dashboard/evidence')]
with (folder/'runtime-private.txt').open('w') as log:
    result=subprocess.run(command,cwd=root,env=env,stdout=log,stderr=log,timeout=600)
print('Playtest: production Main structure workflow; exit',result.returncode)
if result.returncode:
    failure=root/'dashboard/evidence/structure-native-failure.txt'
    if failure.exists():print(failure.read_text())
    raise SystemExit(result.returncode)
recordings=list((profile/'recordings').glob('*.mp4'))
if recordings:
    clip=max(recordings,key=lambda p:p.stat().st_mtime)
    if clip.stat().st_size<6_000_000:shutil.copyfile(clip,root/'dashboard/evidence/structure-workflow.mp4')
if any(hashlib.sha256(p.read_bytes()).hexdigest()!=source_hashes[str(p.relative_to(root))] for p in source_files):
    raise SystemExit('Native source changed during playtest; rerun required')
report_path=root/'dashboard/evidence/structure-native-result.json'
report=json.loads(report_path.read_text())
report['source_manifest']='structure-native-source.json'
report['source_manifest_sha256']=hashlib.sha256(json.dumps(source_hashes,sort_keys=True).encode()).hexdigest()
report['command']='python3 deploy/run_structure_playtest.py --display "$DISPLAY"'
report['profile']='Synthetic offline profile; LOW with detail32/horizon512, scale0.5, shadows/AA/dynamic resolution OFF'
report['publication']='Local evidence pending controller publication'
report_path.write_text(json.dumps(report,indent=2)+'\n')
print(report_path.read_text())
