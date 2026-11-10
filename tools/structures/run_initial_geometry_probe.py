#!/usr/bin/env python3
"""Bounded synthetic startup geometry comparison; never city qualification."""
import datetime,hashlib,json,platform,statistics,subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[2]
folder=root/'target/initial-geometry-probe'/datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
folder.mkdir(parents=True)
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
source={str(p.relative_to(root)):sha(p) for p in sorted((root/'src/main/java').rglob('*.java'))}
cp=(root/'target/structure-limit-audit/diagnostic-classpath.txt').read_text().strip()
probe=root/'tools/structures/InitialGeometryProbe.java'
compile_command=['javac','--release','17','-cp',cp,'-d',str(folder),str(probe)]
subprocess.run(compile_command,cwd=root,check=True)
manifest=folder/'agent.mf';manifest.write_text('Premain-Class: dev.jayms.net.InitialGeometryProbe\n')
jar_command=['jar','cfm',str(folder/'agent.jar'),str(manifest),'-C',str(folder),'dev/jayms/net/InitialGeometryProbe.class'];subprocess.run(jar_command,cwd=root,check=True)
command=['java','-Xmx512m','--add-opens','java.base/java.util=ALL-UNNAMED','--add-opens','java.base/java.lang=ALL-UNNAMED','-javaagent:'+str(folder/'agent.jar'),'-cp',str(folder)+':'+cp,'dev.jayms.net.InitialGeometryProbe']
with (folder/'raw.json').open('w') as output:subprocess.run(command,cwd=root,stdout=output,check=True,timeout=240)
r=json.loads((folder/'raw.json').read_text())
for case in r['cases']:
 case['summary']={}
 for mode in ['per-row','current']:
  samples=[s for s in case['samples'] if s['mode']==mode];assert len(samples)==20
  case['summary'][mode]={}
  for metric in ['nanos','allocated_bytes','retained_geometry_bytes']:
   values=sorted(s[metric] for s in samples)
   case['summary'][mode][metric]=dict(median=statistics.median(values),p95=values[18],worst=values[-1])
assert all(sha(root/p)==v for p,v in source.items())
r.update(source_sha256=source,probe_sha256=sha(probe),driver_sha256=sha(Path(__file__)),commands=[compile_command,jar_command,command],os=platform.platform(),java=subprocess.run(['java','-version'],capture_output=True,text=True,check=True).stderr.strip(),cpu=next(line.split(':',1)[1].strip() for line in Path('/proc/cpuinfo').read_text().splitlines() if line.startswith('model name')),qualification=False,method='Same JVM, three warmups and 20 alternating-order samples per mode/fixture. Baseline uses unchanged production apply per row, current uses private initial replay. Every result checks all affected cell values against independent per-row world. Fixtures and world/terrain constructors, verification excluded from timing/allocation. Current includes packed image preparation for multi-row runs; one-row runs use ordinary replacement. Retained graph covers geometry chunk root only, excludes terrain/history/models and is not peak memory. Warm synthetic geometry only, not file decode, cold cache, full server startup, peak memory, networking, rendering or city qualification. Shared VPS/JIT/GC variation applies.')
(folder/'comparison.json').write_text(json.dumps(r,indent=2)+'\n')
print((folder/'comparison.json').relative_to(root))
