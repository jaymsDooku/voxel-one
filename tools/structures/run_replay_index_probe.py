#!/usr/bin/env python3
"""Exact-parent private replay index comparison; no city qualification."""
import datetime,hashlib,json,platform,statistics,subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[2]
parent='68fd93896ccc3411dfb0639e98539dc2ebe6526b'
folder=root/'target/replay-index-probe'/datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
folder.mkdir(parents=True);(folder/'baseline').mkdir();(folder/'current').mkdir()
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
sources={str(p.relative_to(root)):sha(p) for p in sorted((root/'src/main/java').rglob('*.java'))}
classes={str(p.relative_to(root)):sha(p) for p in sorted((root/'target/classes').rglob('*.class'))}
assert classes
baselines=[]
for name in ['RegionReplay','RegionReplaySpool']:
 p=folder/(name+'.java');p.write_bytes(subprocess.check_output(['git','show',parent+':src/main/java/dev/jayms/net/'+name+'.java'],cwd=root));baselines.append(p)
cp=(root/'target/structure-limit-audit/diagnostic-classpath.txt').read_text().strip()
probe=root/'tools/structures/ReplayIndexProbe.java'
commands=[['javac','--release','17','-cp',cp,'-d',str(folder/'baseline')]+[str(p) for p in baselines],['javac','--release','17','-cp',cp,'-d',str(folder/'current'),str(probe)]]
for cmd in commands:subprocess.run(cmd,cwd=root,check=True)
results={}
for mode in ['baseline','current']:
 cmd=['java','-Xmx512m','-cp',str(folder/'current')+':'+(str(folder/'baseline')+':' if mode=='baseline' else '')+cp,'dev.jayms.net.ReplayIndexProbe',mode,str(folder/(mode+'-spool'))];commands.append(cmd)
 with (folder/(mode+'.json')).open('w') as output:subprocess.run(cmd,cwd=root,stdout=output,check=True,timeout=240)
 results[mode]=json.loads((folder/(mode+'.json')).read_text())
 for case in results[mode]['cases']:
  assert len(case['samples'])==20
  case['summary']={}
  for metric in ['stage_nanos','stage_allocated_bytes','full_nanos','full_allocated_bytes','selected_nanos','selected_allocated_bytes','staging_bytes']:
   v=sorted(s[metric] for s in case['samples']);case['summary'][metric]=dict(median=statistics.median(v),p95=v[18],worst=v[-1])
assert all(sha(root/p)==v for p,v in sources.items())
assert all(sha(root/p)==v for p,v in classes.items())
r=dict(parent=parent,parent_source_sha256={p.name:sha(p) for p in baselines},source_sha256=sources,compiled_sha256=classes,probe_sha256=sha(probe),driver_sha256=sha(Path(__file__)),commands=commands,results=results,
 os=platform.platform(),cpu=next(line.split(':',1)[1].strip() for line in Path('/proc/cpuinfo').read_text().splitlines() if line.startswith('model name')),java=subprocess.run(['java','-version'],capture_output=True,text=True,check=True).stderr.strip(),
 method='Serial separate 512 MiB JVMs, exact-parent RegionReplay/RegionReplaySpool classes. Three warmups and 20 samples per fixture/mode. Stage includes private file forces; full/selected reads use warm OS cache. Counts and rolling row-order hashes included in read callbacks, checked after timing. Baseline selected case reads all and filters callbacks; current uses sealed disk index and selected payloads. Fixture construction and close/cleanup outside timing; verifies no leaked files each run. Measures thread allocation and combined staging file bytes, not retained/peak process memory. Private replay I/O only, not durable world save, live paging, cold cache, GPU, network or city qualification. Shared VPS/JIT/GC/storage variation applies.',qualification=False)
(folder/'comparison.json').write_text(json.dumps(r,indent=2)+'\n')
print((folder/'comparison.json').relative_to(root))
