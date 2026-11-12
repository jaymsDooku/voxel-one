#!/usr/bin/env python3
"""Use only the assigned inherited display, with an isolated synthetic profile."""
import argparse
import json
import hashlib
import os
import re
from pathlib import Path
import shutil
import subprocess
import datetime
import signal
import time

parser=argparse.ArgumentParser();parser.add_argument('--display',required=True);parser.add_argument('--evidence-prefix');args=parser.parse_args()
if args.evidence_prefix is not None and not re.fullmatch(r'[a-z0-9][a-z0-9-]{0,63}',args.evidence_prefix):
    parser.error('Evidence prefix must be 1–64 lowercase letters, digits or hyphens, starting with a letter or digit')
root=Path(__file__).resolve().parents[1]
source_files=sorted((root/'src/main/java').rglob('*.java'))+[root/'deploy/StructurePlaytest.java',Path(__file__).resolve()]
source_hashes={str(p.relative_to(root)):hashlib.sha256(p.read_bytes()).hexdigest() for p in source_files}
source_manifest=dict(schema=1,source_sha256=source_hashes,scope='Production Java plus native workflow harness; isolated synthetic profile')
publication=root/'dashboard/evidence'
if args.evidence_prefix and any(publication.glob(args.evidence_prefix+'-*')):
    raise SystemExit('Preserve existing evidence; choose a fresh prefix')
folder=root/'target/structure-native'
if folder.exists():
    archive=root/'target/structure-native-archives'/datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
    archive.parent.mkdir(exist_ok=True);folder.rename(archive)
folder.mkdir(parents=True)
output=folder/'evidence' if args.evidence_prefix else publication
output.mkdir(exist_ok=True)
(output/'structure-native-source.json').write_text(json.dumps(source_manifest,indent=2)+'\n')
home=folder/'home';profile=home/'.voxel-one';profile.mkdir(parents=True)
(profile/'graphics.properties').write_text('version=2\npreset=LOW\nDETAIL=32\nHORIZON=512\nSHADOWS=OFF\nAA=OFF\nDYNAMIC=OFF\nSCALE=0.5\nMIN_SCALE=0.5\nMAX_SCALE=0.5\n')
for name in ('structure-native-result.json','structure-native-failure.txt'):
    (output/name).unlink(missing_ok=True)
classes=folder/'classes';classes.mkdir();temporary=folder/'tmp';temporary.mkdir()
jars=[]
for repo in (root/'target/structure-m2', Path('/home/debian/.m2/repository')):
    for group in ('org/lwjgl','org/joml','org/jcodec','com/googlecode/soundlibs'):
        jars.extend(str(p) for p in (repo/group).rglob('*.jar') if 'natives-windows' not in p.name and 'natives-macos' not in p.name)
classpath=str(root/'target/classes')+':'+':'.join(jars)
subprocess.run(['javac','--release','17','-cp',classpath,'-d',str(classes),'deploy/StructurePlaytest.java'],cwd=root,check=True)
env=os.environ.copy();env['DISPLAY']=args.display;env.pop('WAYLAND_DISPLAY',None);env['ALSOFT_DRIVERS']='null';env['MAGICK_TMPDIR']=str(temporary);env['LP_NUM_THREADS']='2';env['LIBGL_ALWAYS_SOFTWARE']='1'
command=['java','-Xmx1g','-Dvoxel.gl33=true','-Djava.io.tmpdir='+str(temporary),'-Duser.home='+str(home),'-cp',str(classes)+':'+classpath,'StructurePlaytest',str(output)]
execution=output/'structure-native-execution.json'
started=time.monotonic();process=None

def execution_receipt(state,code=None):
    data=dict(schema=1,state=state,return_code=code,elapsed_ms=round((time.monotonic()-started)*1000),timeout_seconds=600)
    temp=execution.with_suffix('.tmp');temp.write_text(json.dumps(data)+'\n');temp.replace(execution)

def interrupted(number,frame):
    execution_receipt('interrupted',128+number)
    raise SystemExit(128+number)

signal.signal(signal.SIGTERM,interrupted);signal.signal(signal.SIGINT,interrupted)
execution_receipt('running')
try:
    with (folder/'runtime-private.txt').open('w') as log:
        process=subprocess.Popen(command,cwd=root,env=env,stdout=log,stderr=log)
        try:
            code=process.wait(timeout=600)
            execution_receipt('completed',code)
        except subprocess.TimeoutExpired:
            process.kill();process.wait(timeout=5);execution_receipt('timed-out',124)
            raise SystemExit(124)
finally:
    if process is not None and process.poll() is None:
        process.terminate()
        try:process.wait(timeout=5)
        except subprocess.TimeoutExpired:process.kill();process.wait(timeout=5)
print('Playtest: production Main structure workflow; exit',code)
if code:
    failure=output/'structure-native-failure.txt'
    if failure.exists():print(failure.read_text())
    raise SystemExit(code)
recordings=list((profile/'recordings').glob('*.mp4'))
if recordings:
    clip=max(recordings,key=lambda p:p.stat().st_mtime)
    if clip.stat().st_size<6_000_000:shutil.copyfile(clip,output/'structure-workflow.mp4')
if any(hashlib.sha256(p.read_bytes()).hexdigest()!=source_hashes[str(p.relative_to(root))] for p in source_files):
    raise SystemExit('Native source changed during playtest; rerun required')
report_path=output/'structure-native-result.json'
report=json.loads(report_path.read_text())
report['source_manifest']=(args.evidence_prefix+'-' if args.evidence_prefix else '')+'structure-native-source.json'
report['source_manifest_sha256']=hashlib.sha256(json.dumps(source_hashes,sort_keys=True).encode()).hexdigest()
report['command']='python3 deploy/run_structure_playtest.py --display "$DISPLAY"'+(' --evidence-prefix '+args.evidence_prefix if args.evidence_prefix else '')
report['profile']='Synthetic offline profile; LOW with detail32/horizon512, scale0.5, shadows/AA/dynamic resolution OFF'
report['publication']='Local evidence pending controller publication'
report_path.write_text(json.dumps(report,indent=2)+'\n')
if args.evidence_prefix:
    artifacts=sorted(output.iterdir())
    if any(not p.is_file() or p.suffix not in ('.json','.png','.mp4') or p.stat().st_size>=6_000_000 for p in artifacts):
        raise SystemExit('Unexpected or oversized native evidence; preserve local output for repair')
    destinations=[publication/(args.evidence_prefix+'-'+p.name) for p in artifacts]
    if any(p.exists() for p in destinations):raise SystemExit('Preserve existing evidence; prefix collision')
    for source,destination in zip(artifacts,destinations):
        # Exclusive creation prevents accidental overwrite if a destination appears after preflight.
        with source.open('rb') as incoming,destination.open('xb') as outgoing:shutil.copyfileobj(incoming,outgoing)
    print('Native evidence prefix:',args.evidence_prefix)
print(report_path.read_text())
