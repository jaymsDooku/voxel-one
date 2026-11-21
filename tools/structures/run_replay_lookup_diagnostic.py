#!/usr/bin/env python3
"""Measure actual direct/bucket lookup, preserving all historical evidence."""
import datetime, hashlib, json, os, platform, subprocess, time
from pathlib import Path
root=Path(__file__).resolve().parents[2]
folder=root/'target/replay-lookup-diagnostic'/datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
folder.mkdir(parents=True)
image=root/'dashboard/evidence/structure-replay-lookup-diagnostic.png'
report=root/'dashboard/evidence/structure-replay-lookup-diagnostic.json'
assert not image.exists() and not report.exists(), 'Preserve existing evidence'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
sources={str(p.relative_to(root)):sha(p) for p in sorted((root/'src/main/java').rglob('*.java'))+sorted((root/'src/test/java').rglob('*.java'))}
classes={str(p.relative_to(root)):sha(p) for base in ['target/classes','target/test-classes'] for p in sorted((root/base).rglob('*.class'))}
prior=json.loads((root/'dashboard/evidence/structure-replay-buckets.json').read_text())
assert sources==prior['source_sha256']
assert classes==prior['cli']['compiled_sha256']
cp=(root/'target/structure-limit-audit/diagnostic-classpath.txt').read_text().strip()
helper=root/'tools/structures/ReplayLookupDiagnostic.java'
commands=[['javac','--release','17','-cp',cp,'-d',str(folder),str(helper)],['java','-Xmx192m','-Djava.io.tmpdir='+str(root/'target/reviewer-tmp'),'-cp',str(folder)+':'+cp,'dev.jayms.net.ReplayLookupDiagnostic',str(folder/'spool'),str(folder/'result.json')]]
subprocess.run(commands[0],cwd=root,check=True,timeout=120)
# Linux wait4 reports whole-process max RSS, including setup/warmup; no shell/time dependency.
with (folder/'child.stdout').open('w') as out:
 p=subprocess.Popen(commands[1],cwd=root,stdout=out,stderr=subprocess.STDOUT)
 import threading
 timer=threading.Timer(240,p.kill);timer.start()
 try:
  _,status,usage=os.wait4(p.pid,0);p.returncode=os.waitstatus_to_exitcode(status)
 finally:timer.cancel()
 assert p.returncode==0, 'Diagnostic failed; inspect bounded child output'
result=json.loads((folder/'result.json').read_text());assert result['status']=='passed' and len(result['runs'])==20
def stats(key):
 a=sorted(r[key]/1e6 for r in result['runs']);return dict(median_ms=(a[9]+a[10])/2,p95_ms=a[18],worst_ms=a[-1])
summary={k:stats(k) for k in ['direct_pass_ns','indexed_pass_ns']}
text='PASS: actual replay lookup diagnostic\n\n96 recurring regions; 49152 rows; 6144 sections\n97 lookups/pass, including empty region\n2 warmups; 20 measured runs; alternating method order\nExact source row order, policy counts and cleanup passed\n\n'
for key,label in [('direct_pass_ns','Direct'),('indexed_pass_ns','Bucket')]:
 s=summary[key];text+=f"{label} pass median/p95/worst ms: {s['median_ms']:.3f}/{s['p95_ms']:.3f}/{s['worst_ms']:.3f}\n"
text+=f"Initial bucket build + one lookup: {result['initial_bucket_build_and_one_lookup_ns']/1e6:.3f} ms\n{result['bucket_files']} private bucket files; {result['bucket_bytes']} metadata bytes\n\nWarm CPU/file diagnostic; shared JVM/JIT/cache effects\nNo cold-load, client/server, frame, tick or GPU result\nCity/maximum/32-client qualification remains pending.\n"
# Capture measured output on the inherited role display.
renderer=root/'tools/structures/ReplayLookupImage.java'
subprocess.run(['javac','--release','17','-d',str(folder),str(renderer)],cwd=root,check=True,timeout=120)
(folder/'image.txt').write_text(text)
image_command=['java','-cp',str(folder),'dev.jayms.net.ReplayLookupImage',str(folder/'image.txt'),str(image)]
subprocess.run(image_command,cwd=root,check=True,timeout=30)
assert all(sha(root/p)==h for p,h in sources.items()) and all(sha(root/p)==h for p,h in classes.items())
base='71612f09bfec4549456b3317676e76cdced085a4'
for p in prior['audit']['restored_byte_exact']:assert (root/p).read_bytes()==subprocess.check_output(['git','show',base+':'+p],cwd=root)
old=prior['audit']['older_reports_byte_exact']+['structure-replay-buckets.json']
for p in old:assert (root/'dashboard/evidence'/p).read_bytes()==subprocess.check_output(['git','show','HEAD:dashboard/evidence/'+p],cwd=root)
assert not subprocess.check_output(['git','diff','--name-only','--diff-filter=D',base],cwd=root).strip()
assert not subprocess.check_output(['git','ls-files','-u'],cwd=root).strip()
subprocess.run(['git','diff','--check'],cwd=root,check=True)
receipt=dict(schema=1,parent_head=subprocess.check_output(['git','rev-parse','HEAD'],cwd=root,text=True).strip(),review_base=base,qualification=False,commands=commands+[image_command],result=result,summary=summary,whole_process_max_rss_kib=usage.ru_maxrss,source_sha256=sources,compiled_sha256=classes,tool_sha256={str(p.relative_to(root)):sha(p) for p in [helper,renderer,Path(__file__)]},image_sha256=sha(image),os=platform.platform(),cpu=next(x.split(':',1)[1].strip() for x in Path('/proc/cpuinfo').read_text().splitlines() if x.startswith('model name')),logical_cpus=os.cpu_count(),java=subprocess.run(['java','-version'],capture_output=True,text=True,check=True).stderr.strip(),workflow='Playtest: python3 tools/structures/run_replay_lookup_diagnostic.py exited 0 against actual private spool files. Direct and indexed methods each read all 96 regions and an empty region in rotating order, twice for warmup and twenty measured passes. Exact row order, policy counts, metadata size/count and cleanup asserted. Image captured on inherited display. Browser playtesting does not apply to private CPU/file integration; no Main/native rerun.',method='One shared source/JVM. Initial indexed preparation plus one lookup measured separately before warmup; stage/fixture creation excluded. Two warmups; twenty measured passes with alternating direct/indexed method order and rotating region order. Timings include result equality/policy checks and allocation. p95 is nearest-rank sample19 of20; median averages samples10/11. Process RSS includes setup, both methods, warmup and all runs; cannot attribute it to either method. Warm file cache/shared JIT/GC/OS effects limit conclusions. No cold-cache or independent-process comparison.',audit=dict(restored_byte_exact=prior['audit']['restored_byte_exact'],older_reports_byte_exact=old,no_deleted_paths=True,no_unmerged_index=True,diff_check='passed'),pending=prior['pending'])
report.write_text(json.dumps(receipt,indent=2)+'\n');assert report.stat().st_size<6000000
print(json.dumps(dict(report=str(report.relative_to(root)),summary=summary,initial_ms=result['initial_bucket_build_and_one_lookup_ns']/1e6,rss_kib=usage.ru_maxrss)))
