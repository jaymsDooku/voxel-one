#!/usr/bin/env python3
"""Run Main's explicit read-only viewer with a synthetic saved file/profile."""
import argparse
import datetime
import hashlib
import json
import os
from pathlib import Path
import platform
import re
import shutil
import subprocess
import time

parser=argparse.ArgumentParser()
parser.add_argument('--display',required=True)
parser.add_argument('--evidence-prefix',default='structure-saved-inspector')
args=parser.parse_args()
assert args.display==os.environ.get('DISPLAY'), 'Use inherited assigned role display'
assert re.fullmatch(r'[a-z0-9][a-z0-9-]{0,63}',args.evidence_prefix), 'Use a simple evidence prefix'
root=Path(__file__).resolve().parents[2]
evidence=root/'dashboard/evidence'
assert not any(evidence.glob(args.evidence_prefix+'-*')), 'Preserve existing evidence; choose a fresh prefix'
folder=root/'target/saved-inspector-playtest'/datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
folder.mkdir(parents=True)
for name in ['home','tmp','classes','synthetic']:(folder/name).mkdir()
sha=lambda f:hashlib.sha256(f.read_bytes()).hexdigest()
files=sorted((root/'src/main/java').rglob('*.java'))+sorted((root/'src/test/java').rglob('*.java'))+sorted(f for f in (root/'src/main/resources/shaders').rglob('*') if f.is_file())
helpers=[root/'tools/structures'/name for name in ['SavedWorldInspectorPlaytest.java','SavedWorldInspectorFixture.java']]
sources={str(f.relative_to(root)):sha(f) for f in files+helpers+[Path(__file__).resolve()]}
classes={str(f.relative_to(root)):sha(f) for f in sorted((root/'target/classes').rglob('*.class'))+sorted((root/'target/test-classes').rglob('*.class'))}
assert classes, 'Compile first'
cp='target/test-classes:'+(root/'target/structure-limit-audit/diagnostic-classpath.txt').read_text().strip()
commands=[['javac','--release','17','-cp',cp,'-d',str(folder/'classes')]+[str(f) for f in helpers],
          ['java','-Xmx256m','-Dvoxel.gl33=true','-Duser.home='+str(folder/'home'),'-Djava.io.tmpdir='+str(folder/'tmp'),'-cp',str(folder/'classes')+':'+cp,'dev.jayms.SavedWorldInspectorPlaytest',str(folder/'synthetic'),str(evidence),args.evidence_prefix]]
env=os.environ.copy()
env.update(LIBGL_ALWAYS_SOFTWARE='1',LP_NUM_THREADS='2',ALSOFT_DRIVERS='null')
start=time.monotonic()
with (folder/'runtime-private.txt').open('w') as log:
    for command in commands:subprocess.run(command,cwd=root,env=env,stdout=log,stderr=subprocess.STDOUT,timeout=240,check=True)
assert all(sha(root/f)==h for f,h in sources.items()), 'Source changed during playtest'
assert all(sha(root/f)==h for f,h in classes.items()), 'Compiled source changed during playtest'
recordings=list((folder/'home/.voxel-one/recordings').glob('*.mp4'))
assert len(recordings)==1 and recordings[0].stat().st_size<6_000_000, 'One bounded F10 recording required'
video=evidence/(args.evidence_prefix+'-workflow.mp4')
with recordings[0].open('rb') as incoming,video.open('xb') as outgoing:shutil.copyfileobj(incoming,outgoing)
images=[evidence/(args.evidence_prefix+'-'+suffix+'.png') for suffix in ['ready','moved','invalid']]
assert all(f.is_file() and f.stat().st_size<6_000_000 for f in images)
result=json.loads((folder/'synthetic/result.json').read_text())
assert result['status']=='passed' and result['qualification'] is False
receipt=dict(schema=1,commands=commands,elapsed_seconds=time.monotonic()-start,result=result,source_sha256=sources,compiled_sha256=classes,
             artifact_sha256={f.name:sha(f) for f in images+[video]},os=platform.platform(),
             cpu=next(line.split(':',1)[1].strip() for line in Path('/proc/cpuinfo').read_text().splitlines() if line.startswith('model name')),
             java=subprocess.run(['java','-version'],capture_output=True,text=True,check=True).stderr.strip(),
             method='Playtest: Main --inspect-world with a long synthetic saved-file path, isolated profile and inherited DISPLAY. Actual native viewer, shared keyboard/capture camera controls, progress/loading then GPU-ready saved-cell hit, visible geometry/models/water, crossing a chunk boundary, edit/capture rejection, F10 recorder, malformed-source error view, cancellation before first poll and observer-failure cleanup. Source bytes, zero resident chunks, bounded scene and complete scratch/Mesh cleanup asserted. Functional software rendering only; no ordinary-gameplay, city/maximum, cold-cache, physical-GPU/frame/network qualification pass.',
             qualification=False,publication='Local evidence pending controller publication')
(folder/'receipt.json').write_text(json.dumps(receipt,indent=2)+'\n')
print('Playtest: Main saved-world inspector passed; evidence prefix '+args.evidence_prefix)
