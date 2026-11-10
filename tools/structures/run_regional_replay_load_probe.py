#!/usr/bin/env python3
"""Compare exact parent regional decoder with current decoder; no world qualification."""
import datetime
import hashlib
import json
import math
import os
import platform
import statistics
import subprocess
from pathlib import Path

root=Path(__file__).resolve().parents[2]
parent='043b9f9d1beb0c35c475b717157b7e2f6c017e5d'
folder=root/'target/regional-replay-load-probe'/datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
folder.mkdir(parents=True);(folder/'tmp').mkdir()
sources={str(p.relative_to(root)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted((root/'src/main/java').rglob('*.java'))}
classes={str(p.relative_to(root)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted((root/'target/classes').rglob('*.class'))}
assert classes,'Compile production before the probe'
path='src/main/java/dev/jayms/net/RegionReplay.java'
baseline_source=subprocess.check_output(['git','show',parent+':'+path],cwd=root)
staged=folder/'baseline/source'/path;staged.parent.mkdir(parents=True);staged.write_bytes(baseline_source)
baseline=folder/'baseline/classes';baseline.mkdir(parents=True)
subprocess.run(['javac','--release','17','-cp',str(root/'target/classes'),'-d',str(baseline),str(staged)],cwd=root,check=True)
probe=root/'tools/structures/RegionalReplayLoadProbe.java';probe_classes=folder/'probe';probe_classes.mkdir()
subprocess.run(['javac','--release','17','-cp',str(root/'target/classes'),'-d',str(probe_classes),str(probe)],cwd=root,check=True)
manifest=folder/'agent.mf';manifest.write_text('Premain-Class: dev.jayms.net.RegionalReplayLoadProbe\n')
agent=folder/'agent.jar'
subprocess.run(['jar','cfm',str(agent),str(manifest),'-C',str(probe_classes),'dev/jayms/net/RegionalReplayLoadProbe.class'],cwd=root,check=True)
results={};commands=[]
for mode in ('baseline','current'):
    cp=[probe_classes]+([baseline] if mode=='baseline' else [])+[root/'target/classes']
    command=['java','-Xmx512m','--add-opens','java.base/java.lang=ALL-UNNAMED','--add-opens','java.base/java.util=ALL-UNNAMED','-javaagent:'+str(agent),'-Djava.io.tmpdir='+str(folder/'tmp'),'-cp',':'.join(map(str,cp)),'dev.jayms.net.RegionalReplayLoadProbe']
    commands.append(command)
    with (folder/(mode+'.json')).open('w') as output:
        subprocess.run(command,cwd=root,stdout=output,check=True,timeout=180)
    results[mode]=json.loads((folder/(mode+'.json')).read_text())
    for case in results[mode]['cases']:
        assert len(case['nanos'])==len(case['allocated_bytes'])==20
        case['summary']={}
        for metric in ('nanos','allocated_bytes','write_nanos','write_allocated_bytes','lookup_nanos','lookup_allocated_bytes'):
            values=sorted(case[metric]);case['summary'][metric]=dict(median=statistics.median(values),p95=values[math.ceil(.95*len(values))-1],worst=values[-1])
assert all(hashlib.sha256((root/p).read_bytes()).hexdigest()==v for p,v in sources.items())
assert all(hashlib.sha256((root/p).read_bytes()).hexdigest()==v for p,v in classes.items())
receipt=dict(parent=parent,parent_decoder_sha256=hashlib.sha256(baseline_source).hexdigest(),source_sha256=sources,compiled_sha256=classes,
             probe_sha256=hashlib.sha256(probe.read_bytes()).hexdigest(),driver_sha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),commands=commands,results=results,
             os=platform.platform(),logical_cpus=os.cpu_count(),cpu_model=next((line.split(':',1)[1].strip() for line in Path('/proc/cpuinfo').read_text().splitlines() if line.startswith('model name')),'unknown'),
             java=subprocess.run(['java','-version'],capture_output=True,text=True,check=True).stderr.strip(),
             method='Serial 512 MiB JVMs; exact parent RegionReplay decoder rebuilt against the same unchanged ordinary History paths. Measures section decode/validation/private target construction, full in-memory re-encoding and full keyed lookup separately. Excludes fixture creation, forced GC/10 ms pauses, equality/byte checks, geometry, disk/tail staging, live World replay, rendering and networking. Retained graph counts one history only; not whole-process peak memory.',qualification=False)
(folder/'comparison.json').write_text(json.dumps(receipt,indent=2)+'\n')
print(folder.relative_to(root)/'comparison.json')
