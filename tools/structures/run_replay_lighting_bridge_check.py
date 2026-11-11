#!/usr/bin/env python3
"""Run real GLFW/OpenGL replay upload checks and capture the rendered framebuffer."""
import datetime
import hashlib
import json
import platform
import subprocess
from pathlib import Path

import argparse
import os
parser=argparse.ArgumentParser()
parser.add_argument('--display',required=True)
args=parser.parse_args()
assert args.display==os.environ.get('DISPLAY'), 'Use inherited assigned role display'
root = Path(__file__).resolve().parents[2]
folder = root / 'target/replay-lighting-bridge-check' / datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
folder.mkdir(parents=True)

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

sources = {str(p.relative_to(root)): sha(p) for p in sorted((root / 'src/main/java').rglob('*.java')) + sorted((root / 'src/test/java').rglob('*.java'))}
classes = {str(p.relative_to(root)): sha(p) for p in sorted((root / 'target/classes').rglob('*.class')) + sorted((root / 'target/test-classes').rglob('*.class'))}
assert classes, 'Compile production classes first'
cp = 'target/test-classes:' + (root / 'target/structure-limit-audit/diagnostic-classpath.txt').read_text().strip()
assert (root / cp.split(':')[0]).resolve() == root / 'target/test-classes'
java_sources = [root / 'tools/structures' / name for name in ['ReplayLightingBridgeCheck.java', 'ReplayWaterCheck.java', 'ReplayRenderCheck.java', 'ReplaySceneCheck.java', 'ReplayChunkGpuCheck.java', 'ReplayModelGpuCheck.java', 'ReplayGpuUploadCheck.java']]
image = root / 'dashboard/evidence/structure-replay-lighting-bridge-optics.png'
assert not image.with_name('structure-replay-lighting-bridge-regression.png').exists(), 'Preserve regression evidence'
assert not image.exists(), 'Preserve existing evidence; choose a fresh output name'
commands = [
    ['javac', '--release', '17', '-cp', cp, '-d', str(folder)] + [str(p) for p in java_sources],
    ['java', '-Xmx256m', '-Djava.io.tmpdir=' + str(root / 'target/reviewer-tmp'),
     '-cp', str(folder) + ':' + cp, 'dev.jayms.net.ReplayLightingBridgeCheck',
     str(folder / 'spool'), str(folder / 'result.json'), str(image)],
]
for command in commands:
    subprocess.run(command, cwd=root, check=True, timeout=240)
assert image.is_file()
assert all(sha(root / p) == v for p, v in sources.items())
assert all(sha(root / p) == v for p, v in classes.items())
receipt = dict(
    qualification=False, commands=commands, source_sha256=sources, compiled_sha256=classes,
    cli_sha256={str(p.relative_to(root)): sha(p) for p in java_sources}, driver_sha256=sha(Path(__file__)), image_sha256=sha(image), regression_image_sha256=sha(image.with_name("structure-replay-lighting-bridge-regression.png")),
    result=json.loads((folder / 'result.json').read_text()), os=platform.platform(),
    cpu=next(line.split(':', 1)[1].strip() for line in Path('/proc/cpuinfo').read_text().splitlines() if line.startswith('model name')),
    java=subprocess.run(['java', '-version'], capture_output=True, text=True, check=True).stderr.strip(),
    method='Playtest: actual private scene exposes two complete frozen CPU lighting roots before GPU upload; mutation rejects, unknown 288-chunk lighting window stays pending with zero worker submissions, eviction updates coverage while detached roots survive. Real RenderPipeline lit planar binding/state/reference, two-chunk GPU lifecycle and ordinary/model/unlit-water regressions pass. Near lighting and lit appearance remain unavailable; no Main/city/physical-GPU/network qualification.',
)
(folder / 'receipt.json').write_text(json.dumps(receipt, indent=2) + '\n')
print((folder / 'receipt.json').relative_to(root))
