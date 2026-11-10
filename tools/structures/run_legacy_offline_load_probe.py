#!/usr/bin/env python3
"""Exact-parent private legacy offline load comparison; no city qualification."""
import datetime,hashlib,json,platform,statistics,subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[2]
parent='cd84bc2251448cbd19577b731165dc395fb09f94'
folder=root/'target/legacy-offline-load-probe'/datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
folder.mkdir(parents=True);(folder/'baseline').mkdir();(folder/'current').mkdir()
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
sources={str(p.relative_to(root)):sha(p) for p in sorted((root/'src/main/java').rglob('*.java'))}
classes={str(p.relative_to(root)):sha(p) for p in sorted((root/'target/classes').rglob('*.class'))}
assert classes
baseline=folder/'LocalGame.java';baseline.write_bytes(subprocess.check_output(['git','show',parent+':src/main/java/dev/jayms/net/LocalGame.java'],cwd=root))
cp=(root/'target/structure-limit-audit/diagnostic-classpath.txt').read_text().strip()
probe=root/'tools/structures/LegacyOfflineLoadProbe.java'
commands=[['javac','--release','17','-cp',cp,'-d',str(folder/'baseline'),str(baseline)],['javac','--release','17','-cp',cp,'-d',str(folder/'current'),str(probe)]]
for cmd in commands:subprocess.run(cmd,cwd=root,check=True)
manifest=folder/'agent.mf';manifest.write_text('Premain-Class: dev.jayms.net.LegacyOfflineLoadProbe\n')
cmd=['jar','cfm',str(folder/'agent.jar'),str(manifest),'-C',str(folder/'current'),'dev/jayms/net/LegacyOfflineLoadProbe.class'];commands.append(cmd);subprocess.run(cmd,cwd=root,check=True)
results={}
for mode in ['baseline','current']:
 cmd=['java','-Xmx512m','--add-opens','java.base/java.lang=ALL-UNNAMED','--add-opens','java.base/java.util=ALL-UNNAMED','-javaagent:'+str(folder/'agent.jar'),'-cp',str(folder/'current')+':'+(str(folder/'baseline')+':' if mode=='baseline' else '')+cp,'dev.jayms.net.LegacyOfflineLoadProbe',str(folder/mode/'fixtures')];commands.append(cmd)
 with (folder/(mode+'.json')).open('w') as output:subprocess.run(cmd,cwd=root,stdout=output,check=True,timeout=240)
 results[mode]=json.loads((folder/(mode+'.json')).read_text())
 for case in results[mode]['cases']:
  assert len(case['samples'])==20
  case['summary']={}
  for metric in ['nanos','allocated_bytes']:
   v=sorted(s[metric] for s in case['samples']);case['summary'][metric]=dict(median=statistics.median(v),p95=v[18],worst=v[-1])
assert all(sha(root/p)==v for p,v in sources.items())
assert all(sha(root/p)==v for p,v in classes.items())
r=dict(parent=parent,parent_local_game_sha256=sha(baseline),source_sha256=sources,compiled_sha256=classes,probe_sha256=sha(probe),driver_sha256=sha(Path(__file__)),commands=commands,results=results,
 os=platform.platform(),cpu=next(line.split(':',1)[1].strip() for line in Path('/proc/cpuinfo').read_text().splitlines() if line.startswith('model name')),java=subprocess.run(['java','-version'],capture_output=True,text=True,check=True).stderr.strip(),
 method='Serial separate 512 MiB JVMs, three warmups and 20 samples per mode/fixture before owned full/native checks. Exact parent LocalGame compiled against same ordinary History paths. Measures actual warm-cache file load including version/model/inventory/tail decoding. Fixture/source creation, validation, full keyed reads, re-save/force/byte checks and graph scans excluded from load time. Each sample checks ordered history, revisions, dependencies and complete saved bytes. Retained graph includes loaded history, model library, inventory and drops, with and without a shared History snapshot; excludes fixture arrays, file/path objects, geometry/World/render state and peak staging/process memory. Shared VPS/JIT/GC limits timing claims. Not cold-cache/city/reference/GPU/network qualification',qualification=False)
(folder/'comparison.json').write_text(json.dumps(r,indent=2)+'\n')
print((folder/'comparison.json').relative_to(root))
