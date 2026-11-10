#!/usr/bin/env python3
"""Exercise private replay cell windows; capture the actual synthetic CLI result."""
import datetime
import hashlib
import json
import platform
import subprocess
from pathlib import Path

root = Path(__file__).resolve().parents[2]
folder = root / 'target/replay-window-check' / datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
folder.mkdir(parents=True)

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

sources = {str(p.relative_to(root)): sha(p) for p in sorted((root / 'src/main/java').rglob('*.java'))}
classes = {str(p.relative_to(root)): sha(p) for p in sorted((root / 'target/classes').rglob('*.class'))}
assert classes, 'Compile production classes first'
cp = (root / 'target/structure-limit-audit/diagnostic-classpath.txt').read_text().strip()
assert (root / cp.split(':')[0]).resolve() == root / 'target/classes'
source = root / 'tools/structures/RegionReplayWindowCheck.java'
image = root / 'dashboard/evidence/structure-replay-window-cli.png'
assert not image.exists(), 'Preserve existing evidence; choose a fresh output name'
commands = [
    ['javac', '--release', '17', '-cp', cp, '-d', str(folder), str(source)],
    ['java', '-Xmx128m', '-Djava.io.tmpdir=' + str(root / 'target/reviewer-tmp'),
     '-cp', str(folder) + ':' + cp, 'dev.jayms.net.RegionReplayWindowCheck',
     str(folder / 'spool'), str(folder / 'result.json'), str(image)],
]
for command in commands:
    subprocess.run(command, cwd=root, check=True, timeout=240)
assert image.is_file()
assert all(sha(root / p) == v for p, v in sources.items())
assert all(sha(root / p) == v for p, v in classes.items())
receipt = dict(
    qualification=False, commands=commands, source_sha256=sources, compiled_sha256=classes,
    cli_sha256=sha(source), driver_sha256=sha(Path(__file__)), image_sha256=sha(image),
    result=json.loads((folder / 'result.json').read_text()), os=platform.platform(),
    cpu=next(line.split(':', 1)[1].strip() for line in Path('/proc/cpuinfo').read_text().splitlines() if line.startswith('model name')),
    java=subprocess.run(['java', '-version'], capture_output=True, text=True, check=True).stderr.strip(),
    method='One128 MiB JVM,3 warmups and20 samples per fixture. Synthetic in-memory sources; private disk stages include forces. Window-load sum times64 separate reads, each scanning the full index and decoding8192 region rows; warm OS cache. Batch additionally includes history checks and private image installation. Second fixture loads one fully fragmented cell, a clear plus4096 coloured leaves, retaining4097 source rows. Full-world/full-cell oracle validation and cleanup excluded. Fixture/oracle state materialized outside timings; not production memory. Heap setting is not peak memory. Shared VPS/JIT/GC/storage variation applies. No live paging, cold-cache, durable save, GPU, network or city qualification.',
)
(folder / 'receipt.json').write_text(json.dumps(receipt, indent=2) + '\n')
print((folder / 'receipt.json').relative_to(root))
