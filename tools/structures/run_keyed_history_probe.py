#!/usr/bin/env python3
"""Exact-parent keyed history and override comparison; no city qualification."""
import datetime,hashlib,json,platform,statistics,subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[2]
parent='bd80f5b76338ed069f59a533671143a98a3f871d'
folder=root/'target/keyed-history-probe'/datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
folder.mkdir(parents=True);(folder/'baseline').mkdir();(folder/'current').mkdir()
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
sources={str(p.relative_to(root)):sha(p) for p in sorted((root/'src/main/java').rglob('*.java'))}
classes={str(p.relative_to(root)):sha(p) for p in sorted((root/'target/classes').rglob('*.class'))}
assert classes
baseline=folder/'SegmentedHistory.java';baseline.write_bytes(subprocess.check_output(['git','show',parent+':src/main/java/dev/jayms/net/SegmentedHistory.java'],cwd=root))
cp=(root/'target/structure-limit-audit/diagnostic-classpath.txt').read_text().strip()
probe=root/'tools/structures/KeyedHistoryProbe.java'
commands=[['javac','--release','17','-cp',cp,'-d',str(folder/'baseline'),str(baseline)],['javac','--release','17','-cp',cp,'-d',str(folder/'current'),str(probe)]]
for cmd in commands:subprocess.run(cmd,cwd=root,check=True)
manifest=folder/'agent.mf';manifest.write_text('Premain-Class: dev.jayms.net.KeyedHistoryProbe\n')
cmd=['jar','cfm',str(folder/'agent.jar'),str(manifest),'-C',str(folder/'current'),'dev/jayms/net/KeyedHistoryProbe.class'];commands.append(cmd);subprocess.run(cmd,cwd=root,check=True)
results={}
for mode in ['baseline','current']:
 cmd=['java','-Xmx512m','--add-opens','java.base/java.lang=ALL-UNNAMED','--add-opens','java.base/java.util=ALL-UNNAMED','-javaagent:'+str(folder/'agent.jar'),'-cp',str(folder/'current')+':'+(str(folder/'baseline')+':' if mode=='baseline' else '')+cp,'dev.jayms.net.KeyedHistoryProbe',mode];commands.append(cmd)
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
 method='Serial separate 512 MiB JVMs, three warmups and 20 samples per mode/fixture before other owned full/native checks. Exact parent SegmentedHistory rebuilt against unchanged storage method signatures. Baseline server intake uses validated pure put; current uses bounded KeyedReplayLoader. Both overlay cases start with the same compact cell and time 512 keyed overwrites; baseline has parent whole-map override copies, current has bounded pages. Excludes fixture setup, validation of results, keyed reads and graph scans. Checks exact order/revisions/values, old snapshots and later full-cell coverage. Retained graph is history roots only; not combined server geometry/models or peak process/staging memory. No file decode, TLS, rendering, networking or city/reference qualification. Shared VPS/JIT/GC limits timing claims',qualification=False)
(folder/'comparison.json').write_text(json.dumps(r,indent=2)+'\n')
print((folder/'comparison.json').relative_to(root))
