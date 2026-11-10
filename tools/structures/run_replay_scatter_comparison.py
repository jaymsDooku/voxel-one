#!/usr/bin/env python3
"""Compare exact prior/candidate private replay builds; never city qualification."""
import datetime
import hashlib
import json
import math
import os
import platform
import statistics
import tempfile
import threading
import subprocess
from pathlib import Path

root=Path(__file__).resolve().parents[2]
prior_head='f937294db8789d0bca7cdfb584354a0de89be3b7'
folder=root/'target/replay-scatter-comparison'/datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
folder.mkdir(parents=True)
report_path=root/'dashboard/evidence/structure-replay-scatter-comparison.json'
image_path=root/'dashboard/evidence/structure-replay-scatter-comparison.png'
assert not report_path.exists() and not image_path.exists(), 'Fresh evidence names required'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def git(*args):return subprocess.check_output(['git',*args],cwd=root)
source={str(p.relative_to(root)):sha(p) for p in sorted((root/'src/main/java').rglob('*.java'))+sorted((root/'src/test/java').rglob('*.java'))}
previous=json.loads((root/'dashboard/evidence/structure-replay-counting-copy.json').read_text())
# Current compilation is fenced by the fresh scatter CLI receipt below.
receipt_path=Path((root/'target/structure-replay-scatter-cli.stdout').read_text().strip().splitlines()[-1])
receipt=json.loads((root/receipt_path).read_text())
assert source==receipt['source_sha256'], 'Revalidate changed source first'
assert all(sha(root/p)==h for p,h in receipt['compiled_sha256'].items()), 'Compiled classes changed; rebuild/revalidate first'
changed={'src/main/java/dev/jayms/net/RegionReplayCells.java'}
for p in sorted((root/'src/main/java').rglob('*.java')):
 relative=str(p.relative_to(root))
 if relative not in changed:assert p.read_bytes()==git('show',prior_head+':'+relative), relative
cp=(root/'target/structure-limit-audit/diagnostic-classpath.txt').read_text().strip()
commands=[]
def run(command):
 commands.append(command)
 subprocess.run(command,cwd=root,check=True,capture_output=True,text=True,timeout=240)
prior_sources=folder/'prior-source';prior_sources.mkdir()
prior_classes=folder/'prior-classes';prior_classes.mkdir()
prior_hashes={}
for name in sorted(changed):
 data=git('show',prior_head+':'+name);p=prior_sources/Path(name).name;p.write_bytes(data);prior_hashes[name]=sha(p)
run(['javac','--release','17','-cp',cp,'-d',str(prior_classes)]+[str(p) for p in sorted(prior_sources.glob('*.java'))])
helpers=[root/'tools/structures'/n for n in ['ReplayBuildComparison.java','ReplayBuildComparisonImage.java']]
classes=folder/'classes';classes.mkdir()
run(['javac','--release','17','-cp',cp,'-d',str(classes)]+[str(p) for p in helpers])
variants={}
for label,passes,override in [('prior',1,str(prior_classes)+':'),('candidate',1,'')]:
 result=folder/(label+'.json')
 command=['java','-Xmx192m','-Djava.io.tmpdir='+str(root/'target/reviewer-tmp'),'-cp',override+str(classes)+':'+cp,'dev.jayms.net.ReplayBuildComparison',str(folder/(label+'-spool')),str(result),str(passes)]
 commands.append(command)
 with tempfile.TemporaryFile(dir=folder) as output:
  process=subprocess.Popen(command,cwd=root,stdout=output,stderr=output)
  timer=threading.Timer(240,process.kill);timer.daemon=True;timer.start()
  try:
   pid,status,usage=os.wait4(process.pid,0);process.returncode=os.waitstatus_to_exitcode(status)
  finally:timer.cancel()
  assert pid==process.pid and process.returncode==0, 'Diagnostic JVM failed; no benchmark pass'
  rss_kib=int(usage.ru_maxrss)
 data=json.loads(result.read_text());assert data['status']=='passed' and len(data['runs'])==20
 def summary(key,divisor=1):
  values=sorted(row[key]/divisor for row in data['runs'])
  return dict(median=statistics.median(values),p95=values[math.ceil(.95*len(values))-1],worst=max(values))
 variants[label]=dict(raw=data,build_ms=summary('build_ns',1e6),first_window_ms=summary('first_window_ns',1e6),heap_pool_peak_sum_bytes=summary('heap_pool_peak_sum_bytes'),process_max_rss_kib=rss_kib)
a,b=variants['prior'],variants['candidate']
assert a['raw']['fixture_sha256']==b['raw']['fixture_sha256']
args=[str(image_path)]+[f'{v:.3f}' for v in [a['build_ms']['p95'],b['build_ms']['p95'],a['first_window_ms']['p95'],b['first_window_ms']['p95']]]+[str(v['process_max_rss_kib']) for v in [a,b]]
run(['java','-Xmx64m','-cp',str(classes)+':'+cp,'dev.jayms.net.ReplayBuildComparisonImage']+args)
assert image_path.is_file() and image_path.stat().st_size<6000000
assert all(sha(root/p)==h for p,h in source.items())
assert all(sha(root/p)==h for p,h in receipt['compiled_sha256'].items())
base='71612f09bfec4549456b3317676e76cdced085a4'
restored=previous['audit']['restored_byte_exact']
for name in restored:assert (root/name).read_bytes()==git('show',base+':'+name)
old=previous['audit']['older_reports_byte_exact']+['structure-replay-counting-copy.json','structure-replay-build-comparison.json']
for name in old:assert (root/'dashboard/evidence'/name).read_bytes()==git('show','HEAD:dashboard/evidence/'+name)
assert not git('ls-files','-u').strip() and not git('diff','--name-only','--diff-filter=D',base).strip()
subprocess.run(['git','diff','--check'],cwd=root,check=True)
report=dict(change='Four reusable 64 KiB write-back scatter pages replace individual 28-byte writes; compare against the prior one-source-pass implementation. No cap or live startup/save change.',schema=1,status='passed',qualification=False,parent_head=git('rev-parse','HEAD').decode().strip(),prior_head=prior_head,review_base=base,commands=commands,source_sha256=source,compiled_sha256=receipt['compiled_sha256'],prior_override_sha256=prior_hashes,tool_sha256={str(p.relative_to(root)):sha(p) for p in helpers+[Path(__file__)]},prior_compiled_sha256={str(p.relative_to(root)):sha(p) for p in sorted(prior_classes.rglob('*.class'))},image_sha256=sha(image_path),variants=variants,
 environment=dict(os=platform.platform(),cpu=next(line.split(':',1)[1].strip() for line in Path('/proc/cpuinfo').read_text().splitlines() if line.startswith('model name')),logical_cpus=os.cpu_count(),java=subprocess.run(['java','-version'],capture_output=True,text=True,check=True).stderr.strip(),gpu='Not used; no renderer/driver qualification',network='Not used; no client/server synchronization'),
 method='Two separate sequential JVMs, prior first then candidate; same helper/byte-identical fixture. Two warmups plus twenty measured builds each. Staged source and initial fixture validation outside timing. Build includes final copied-root force and candidate raw cleanup. First-window timing excludes equality verification; last-window regression also checked. Per-run policy counts, original row order and scratch/root/source cleanup asserted. This is warm-cache CPU/file diagnostic only; process RSS is Linux os.wait4 ru_maxrss for each Java child (KiB), including setup/warmups and JVM overhead. Heap-pool peak sums are not simultaneous heap usage. Fixed run order, OS/JIT/cache/GC noise and different file work can affect comparisons. No cold-cache or causality claim.',
 execution_iteration='Uses direct Linux os.wait4 resource collection with a 240-second child kill timer. Prior and candidate both use one source traversal. No benchmark attempt counted before successful Java completion.',
 workflow='Playtest: python3 tools/structures/run_replay_build_comparison.py executes actual prior and candidate spool/cell-index code. First/last selected windows exercise boundaries and repeated overwritten rows. Source pass counts and cleanup are regressions. Fresh synthetic result image captured on inherited display; visual inspection remains separate. Browser playtesting does not apply to private CPU/file integration. Main/native workflow not rerun.',
 audit=dict(restored_byte_exact=restored,older_reports_byte_exact=old,no_path_deletions=True,no_unmerged_index=True,diff_check='passed',production_source_unchanged_this_phase=True),
 pending=['This is not the required 512x128x512 city or maximum fragmented fixture, reference environment, cold-cache, GPU, rendered clients or shaped network qualification. Those 20-run acceptance budgets remain pending.', 'Live paging, dirty overlays/global order, immutable generations/sidecars, atomic persistence, disk region directory and city/maximum admission remain unfinished. No caps raised. Supported limits and stable rollout stay gated.', 'Fresh exact-source hosted validation, latest-master integration and independent exact-head review remain required.'])
report_path.write_text(json.dumps(report,indent=2)+'\n');assert report_path.stat().st_size<6000000
print(json.dumps(dict(status='passed',qualification=False,prior_build_ms=a['build_ms'],candidate_build_ms=b['build_ms'],prior_window_ms=a['first_window_ms'],candidate_window_ms=b['first_window_ms'],prior_rss_kib=a['process_max_rss_kib'],candidate_rss_kib=b['process_max_rss_kib'])))
