#!/usr/bin/env python3
"""Exact-parent post-snapshot overwrite comparison; no city qualification."""
import datetime,hashlib,json,platform,statistics,subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[2]
parent='8209c39e4bea3a66283f805677a7f681c61a2618'
folder=root/'target/cell-identity-write-probe'/datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
folder.mkdir(parents=True);(folder/'baseline').mkdir();(folder/'current').mkdir()
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
sources={str(p.relative_to(root)):sha(p) for p in sorted((root/'src/main/java').rglob('*.java'))}
classes={str(p.relative_to(root)):sha(p) for p in sorted((root/'target/classes').rglob('*.class'))}
assert classes
baseline=folder/'SegmentedHistory.java';baseline.write_bytes(subprocess.check_output(['git','show',parent+':src/main/java/dev/jayms/net/SegmentedHistory.java'],cwd=root))
cp=(root/'target/structure-limit-audit/diagnostic-classpath.txt').read_text().strip()
probe=root/'tools/structures/CellIdentityWriteProbe.java'
commands=[['javac','--release','17','-cp',cp,'-d',str(folder/'baseline'),str(baseline)],['javac','--release','17','-cp',cp,'-d',str(folder/'current'),str(probe)]]
for cmd in commands:subprocess.run(cmd,cwd=root,check=True)
results={}
for mode in ['baseline','current']:
 cmd=['java','-Xmx512m','-cp',str(folder/'current')+':'+(str(folder/'baseline')+':' if mode=='baseline' else '')+cp,'dev.jayms.net.CellIdentityWriteProbe',mode];commands.append(cmd)
 with (folder/(mode+'.json')).open('w') as output:subprocess.run(cmd,cwd=root,stdout=output,check=True,timeout=240)
 results[mode]=json.loads((folder/(mode+'.json')).read_text())
 for case in results[mode]['cases']:
  assert len(case['samples'])==20
  case['summary']={}
  for metric in ['nanos','allocated_bytes']:
   v=sorted(s[metric] for s in case['samples']);case['summary'][metric]=dict(median=statistics.median(v),p95=v[18],worst=v[-1])
assert all(sha(root/p)==v for p,v in sources.items())
assert all(sha(root/p)==v for p,v in classes.items())
r=dict(parent=parent,parent_storage_sha256=sha(baseline),source_sha256=sources,compiled_sha256=classes,probe_sha256=sha(probe),driver_sha256=sha(Path(__file__)),commands=commands,results=results,
 os=platform.platform(),cpu=next(line.split(':',1)[1].strip() for line in Path('/proc/cpuinfo').read_text().splitlines() if line.startswith('model name')),java=subprocess.run(['java','-version'],capture_output=True,text=True,check=True).stderr.strip(),
 method='Serial separate 512 MiB JVMs; exact-parent SegmentedHistory rebuilt against unchanged storage signatures. Both modes execute identical 512 keyed overwrites after snapshot. Three warmups and 20 samples per fixture/mode. Verifies every old/new keyed value, row count and revision. Fixture/snapshot construction and verification excluded. Captures first ordinary-cell key-index detachment overhead. Timing/allocation only, not retained/peak memory, join locks, full server, network or city qualification. Shared VPS/JIT/GC variation applies.',qualification=False)
(folder/'comparison.json').write_text(json.dumps(r,indent=2)+'\n')
print((folder/'comparison.json').relative_to(root))
