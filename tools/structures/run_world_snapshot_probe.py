#!/usr/bin/env python3
"""Measure adaptive snapshots; never issue a renderer/city qualification pass."""
import datetime
import hashlib
import json
import math
import os
import platform
import statistics
import subprocess
import sys
from pathlib import Path

root = Path(__file__).resolve().parents[2]
folder = root / 'target/world-snapshot-probe' / datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
folder.mkdir(parents=True)
(folder / 'tmp').mkdir()
sources = sorted((root / 'src/main/java').rglob('*.java'))
hashes = {str(p.relative_to(root)): hashlib.sha256(p.read_bytes()).hexdigest() for p in sources}
class_hashes = {str(p.relative_to(root)): hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted((root / 'target/classes').rglob('*.class'))}
assert class_hashes, 'Compile production first'
jars = []
for group in ('org/lwjgl', 'org/joml', 'org/jcodec', 'com/googlecode/soundlibs'):
    jars.extend(str(p) for p in (root / 'target/structure-m2' / group).rglob('*.jar') if 'natives-windows' not in p.name and 'natives-macos' not in p.name)
classpath = ':'.join([str(root / 'target/classes')] + jars)
probe = root / 'tools/structures/WorldEditSnapshotProbe.java'
compile_command = ['javac', '--release', '17', '-cp', classpath, '-d', str(folder), str(probe)]
subprocess.run(compile_command, cwd=root, check=True)
manifest = folder / 'agent.mf'
manifest.write_text('Premain-Class: dev.jayms.WorldEditSnapshotProbe\n')
agent = folder / 'agent.jar'
subprocess.run(['jar','cfm',str(agent),str(manifest),'-C',str(folder),'dev/jayms/WorldEditSnapshotProbe.class'],cwd=root,check=True)
command = ['java', '-Xmx512m', '--add-opens', 'java.base/java.lang=ALL-UNNAMED', '--add-opens', 'java.base/java.util=ALL-UNNAMED', '-javaagent:' + str(agent), '-Djava.io.tmpdir=' + str(folder / 'tmp'), '-cp', str(folder) + ':' + classpath, 'dev.jayms.WorldEditSnapshotProbe']
if len(sys.argv) > 1:
    assert sys.argv[1:] == ['ordinary'], 'Only optional ordinary fixture is supported'
    command.append('ordinary')
result = subprocess.run(command, cwd=root, text=True, capture_output=True, check=True)
raw = json.loads(result.stdout)
assert len(raw['samples']) == 40
summary = {}
for mode in ('flat', 'current'):
    rows = [r for r in raw['samples'] if r['mode'] == mode]
    assert sorted(r['run'] for r in rows) == list(range(1,21))
    summary[mode] = {}
    for metric in ('nanos', 'allocated_bytes', 'scan_nanos', 'scan_allocated_bytes', 'lookup_nanos', 'lookup_allocated_bytes'):
        values = sorted(r[metric] for r in rows)
        summary[mode][metric] = dict(median=statistics.median(values), p95=values[math.ceil(.95*len(values))-1], worst=values[-1])
assert all(hashlib.sha256((root / p).read_bytes()).hexdigest() == value for p,value in hashes.items())
assert all(hashlib.sha256((root / p).read_bytes()).hexdigest() == value for p,value in class_hashes.items())
receipt = dict(source_sha256=hashes, compiled_sha256=class_hashes,
               probe_sha256=hashlib.sha256(probe.read_bytes()).hexdigest(),
               driver_sha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
               os=platform.platform(), logical_cpus=os.cpu_count(),
               cpu_model=next((line.split(':',1)[1].strip() for line in Path('/proc/cpuinfo').read_text().splitlines() if line.startswith('model name')), 'unknown'),
               java=subprocess.run(['java','-version'],capture_output=True,text=True,check=True).stderr.strip(),
               commands=[compile_command,command], raw=raw, summary=summary,
               method='Same synthetic world and alternating order. Baseline is previous flat snapshot algorithm over actual World histories; current is production editsSnapshot. Creation, complete ordered scan and keyed lookup measured separately. Equality outside timing. ThreadMXBean allocation is not peak memory. Reachability counts the union of source replay histories and one snapshot, sharing each object once; excludes geometry, other owners, players, class metadata and whole-process RSS.',
               qualification=False)
(folder / 'comparison.json').write_text(json.dumps(receipt,indent=2)+'\n')
print(folder.relative_to(root) / 'comparison.json')
