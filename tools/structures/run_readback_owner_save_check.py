#!/usr/bin/env python3
"""Exercise readback owner snapshots, worker staging and guarded commits; capture the synthetic CLI result."""
import datetime
import hashlib
import json
import platform
import subprocess
from pathlib import Path

root = Path(__file__).resolve().parents[2]
folder = root / 'target/readback-owner-save-check' / datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
folder.mkdir(parents=True)

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

sources = {str(p.relative_to(root)): sha(p) for p in sorted((root / 'src/main/java').rglob('*.java')) + sorted((root / 'src/test/java').rglob('*.java'))}
classes = {str(p.relative_to(root)): sha(p) for p in sorted((root / 'target/classes').rglob('*.class')) + sorted((root / 'target/test-classes').rglob('*.class'))}
assert classes, 'Compile production classes first'
cp = 'target/test-classes:' + (root / 'target/structure-limit-audit/diagnostic-classpath.txt').read_text().strip()
assert (root / cp.split(':')[0]).resolve() == root / 'target/test-classes'
java_sources = [root / 'tools/structures' / name for name in ['ReadbackOwnerSaveCheck.java']]
image = root / 'dashboard/evidence/structure-readback-owner-save-cli.png'
assert not image.exists(), 'Preserve existing evidence; choose a fresh output name'
commands = [
    ['javac', '--release', '17', '-cp', cp, '-d', str(folder)] + [str(p) for p in java_sources],
    ['java', '-Xmx256m', '-Djava.io.tmpdir=' + str(root / 'target/reviewer-tmp'),
     '-cp', str(folder) + ':' + cp, 'dev.jayms.net.ReadbackOwnerSaveCheck',
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
    method='Actual owner capture -> source worker staging -> owner guarded commit/normal LocalGame load CLI. Tail/model preservation and no partial promotion; changed health/inventory/drops/model identity/World rejected; identical-digest foreign metadata and changed worker terrain rejected; wrong-owner call preserves prefix; cancellation cleanup/retry checked. Browser does not apply to private noninteractive persistence helper. Owner commit blocks; not connected to Main. No cross-process CAS, atomic world-sidecars or city/GPU/network qualification.' ,
)
(folder / 'receipt.json').write_text(json.dumps(receipt, indent=2) + '\n')
print((folder / 'receipt.json').relative_to(root))
