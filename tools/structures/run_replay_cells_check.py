#!/usr/bin/env python3
"""Measure actual scan/index page workflows and capture the synthetic CLI result."""
import datetime
import hashlib
import json
import platform
import subprocess
from pathlib import Path

root = Path(__file__).resolve().parents[2]
folder = root / 'target/replay-cells-check' / datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
folder.mkdir(parents=True)

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

sources = {str(p.relative_to(root)): sha(p) for p in sorted((root / 'src/main/java').rglob('*.java'))}
classes = {str(p.relative_to(root)): sha(p) for p in sorted((root / 'target/classes').rglob('*.class'))}
assert classes, 'Compile production classes first'
cp = (root / 'target/structure-limit-audit/diagnostic-classpath.txt').read_text().strip()
assert (root / cp.split(':')[0]).resolve() == root / 'target/classes'
java_sources = [root / 'tools/structures' / name for name in ['RegionReplayWindowCheck.java', 'RegionReplayCellsCheck.java']]
image = root / 'dashboard/evidence/structure-replay-cells-cli.png'
assert not image.exists(), 'Preserve existing evidence; choose a fresh output name'
commands = [
    ['javac', '--release', '17', '-cp', cp, '-d', str(folder)] + [str(p) for p in java_sources],
    ['java', '-Xmx128m', '-Djava.io.tmpdir=' + str(root / 'target/reviewer-tmp'),
     '-cp', str(folder) + ':' + cp, 'dev.jayms.net.RegionReplayCellsCheck',
     str(folder / 'spool'), str(folder / 'result.json'), str(image)],
]
for command in commands:
    subprocess.run(command, cwd=root, check=True, timeout=240)
assert image.is_file()
assert all(sha(root / p) == v for p, v in sources.items())
assert all(sha(root / p) == v for p, v in classes.items())
receipt = dict(
    qualification=False, commands=commands, source_sha256=sources, compiled_sha256=classes,
    cli_sha256={str(p.relative_to(root)): sha(p) for p in java_sources}, driver_sha256=sha(Path(__file__)), image_sha256=sha(image),
    result=json.loads((folder / 'result.json').read_text()), os=platform.platform(),
    cpu=next(line.split(':', 1)[1].strip() for line in Path('/proc/cpuinfo').read_text().splitlines() if line.startswith('model name')),
    java=subprocess.run(['java', '-version'], capture_output=True, text=True, check=True).stderr.strip(),
    method='One128 MiB JVM,3 warmups and20 samples per fixture; alternating scan/index read-mode order. Shared source spool stage includes forces. Optional copied-index build includes2 full matching-region traversals, counting/scatter writes and force; reported separately and added to indexed read cost. Page-load sums exclude independent history/geometry verification, private installation, fixture creation and cleanup. Warm OS cache; shared VPS/JIT/GC/storage variation. Fixture/oracle state is materialized outside timings. Primitive array/data-file lengths are logical bounds; heap flag is not peak memory. Whole-region policy checked at build; reads check selected copied cells only and defer other-cell post-build corruption. No live paging, cold-cache, durable save, GPU, network or city qualification.',
)
(folder / 'receipt.json').write_text(json.dumps(receipt, indent=2) + '\n')
print((folder / 'receipt.json').relative_to(root))
