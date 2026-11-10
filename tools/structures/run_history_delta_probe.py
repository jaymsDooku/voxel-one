#!/usr/bin/env python3
"""Warm synthetic history-delta comparison; never city qualification."""
import datetime,hashlib,json,platform,statistics,subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[2]
folder=root/'target/history-delta-probe'/datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
folder.mkdir(parents=True)
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
source={str(p.relative_to(root)):sha(p) for p in sorted((root/'src/main/java').rglob('*.java'))}
cp=(root/'target/structure-limit-audit/diagnostic-classpath.txt').read_text().strip()
probe=root/'tools/structures/HistoryDeltaProbe.java'
compile_command=['javac','--release','17','-cp',cp,'-d',str(folder),str(probe)]
subprocess.run(compile_command,cwd=root,check=True)
command=['java','-Xmx512m','-cp',str(folder)+':'+cp,'dev.jayms.net.HistoryDeltaProbe']
with (folder/'raw.json').open('w') as output:subprocess.run(command,cwd=root,stdout=output,check=True,timeout=240)
r=json.loads((folder/'raw.json').read_text())
for case in r['cases']:
 case['summary']={}
 for mode in ['row-union','indexed']:
  samples=[s for s in case['samples'] if s['mode']==mode];assert len(samples)==20
  case['summary'][mode]={}
  for metric in ['nanos','allocated_bytes']:
   values=sorted(s[metric] for s in samples)
   case['summary'][mode][metric]=dict(median=statistics.median(values),p95=values[18],worst=values[-1])
assert all(sha(root/p)==v for p,v in source.items())
r.update(source_sha256=source,probe_sha256=sha(probe),driver_sha256=sha(Path(__file__)),commands=[compile_command,command],os=platform.platform(),java=subprocess.run(['java','-version'],capture_output=True,text=True,check=True).stderr.strip(),cpu=next(line.split(':',1)[1].strip() for line in Path('/proc/cpuinfo').read_text().splitlines() if line.startswith('model name')),qualification=False,method='Same JVM, three warmups and 20 alternating-order samples per mode/fixture. Baseline reproduces the parent row-union algorithm on production histories; indexed mode calls production changedCellPositions. Every result matches exact changed-cell sets. Excludes fixture/snapshot preparation and equality verification. Warm history-only timing/allocation, not authority-lock duration, server ticks, peak memory, transport, rendering or city/reference qualification. Shared VPS/JIT/GC variation applies.')
(folder/'comparison.json').write_text(json.dumps(r,indent=2)+'\n')
print((folder/'comparison.json').relative_to(root))
