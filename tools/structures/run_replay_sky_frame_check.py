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
parser.add_argument('--evidence-prefix',default='structure-replay-sky-frame')
args=parser.parse_args()
assert all(c.isalnum() or c=='-' for c in args.evidence_prefix), 'Use a simple evidence filename prefix'
assert args.display==os.environ.get('DISPLAY'), 'Use inherited assigned role display'
root = Path(__file__).resolve().parents[2]
folder = root / 'target/replay-sky-frame-check' / datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
folder.mkdir(parents=True)

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

sources = {str(p.relative_to(root)): sha(p) for p in sorted((root / 'src/main/java').rglob('*.java')) + sorted((root / 'src/test/java').rglob('*.java'))}
classes = {str(p.relative_to(root)): sha(p) for p in sorted((root / 'target/classes').rglob('*.class')) + sorted((root / 'target/test-classes').rglob('*.class'))}
assert classes, 'Compile production classes first'
cp = 'target/test-classes:' + (root / 'target/structure-limit-audit/diagnostic-classpath.txt').read_text().strip()
assert (root / cp.split(':')[0]).resolve() == root / 'target/test-classes'
java_sources = [root / 'tools/structures' / name for name in ['ReplaySkyFrameCheck.java', 'ReplaySkyUploadAssertions.java', 'ReplayVolumeUploadAssertions.java', 'ReplayVolumePipelineAssertions.java', 'ReplayVolumeFrameAssertions.java', 'ReplaySkyPipelineAssertions.java', 'ReplaySkyFrameAssertions.java', 'ReplayWaterCheck.java', 'ReplayRenderCheck.java', 'ReplaySceneCheck.java', 'ReplayChunkGpuCheck.java', 'ReplayModelGpuCheck.java', 'ReplayGpuUploadCheck.java']]
image = root / ('dashboard/evidence/'+args.evidence_prefix+'-optics.png')
assert not image.with_name((args.evidence_prefix+'-regression.png')).exists(), 'Preserve regression evidence'
assert not image.with_name(args.evidence_prefix+'-pending-optics.png').exists(), 'Preserve pending optics evidence'
assert not image.with_name(args.evidence_prefix+'-pending.png').exists(), 'Preserve pending evidence'
assert not image.exists(), 'Preserve existing evidence; choose a fresh output name'
commands = [
    ['javac', '--release', '17', '-cp', cp, '-d', str(folder)] + [str(p) for p in java_sources],
    ['java', '-Xmx256m', '-Djava.io.tmpdir=' + str(root / 'target/reviewer-tmp'),
     '-cp', str(folder) + ':' + cp, 'dev.jayms.net.ReplaySkyFrameCheck',
     str(folder / 'spool'), str(folder / 'result.json'), str(image)],
]
for command in commands:
    subprocess.run(command, cwd=root, check=True, timeout=600)
assert image.is_file()
assert all(sha(root / p) == v for p, v in sources.items())
assert all(sha(root / p) == v for p, v in classes.items())
receipt = dict(
    pending_result=json.loads((folder / 'spool/pending-result.json').read_text()),
    pending_optics_image_sha256=sha(image.with_name(args.evidence_prefix+'-pending-optics.png')),
    pending_image_sha256=sha(image.with_name(args.evidence_prefix+'-pending.png')),
    qualification=False, commands=commands, source_sha256=sources, compiled_sha256=classes,
    cli_sha256={str(p.relative_to(root)): sha(p) for p in java_sources}, driver_sha256=sha(Path(__file__)), image_sha256=sha(image), regression_image_sha256=sha(image.with_name((args.evidence_prefix+'-regression.png'))),
    result=json.loads((folder / 'result.json').read_text()), os=platform.platform(),
    cpu=next(line.split(':', 1)[1].strip() for line in Path('/proc/cpuinfo').read_text().splitlines() if line.startswith('model name')),
    java=subprocess.run(['java', '-version'], capture_output=True, text=True, check=True).stderr.strip(),
    method='Playtest: synthetic offline v10 sources on inherited DISPLAY. Detached replay, save/publication, journal, cell/column, budget, cancellation and source-fence CLI checks run before real GLFW/OpenGL checks. Ordinary pending-overlay chunk rendering, complete pending-source lighting and sky coverage, lit planar reflection, shared upload budgets and lifecycle cleanup are exercised. Independent geometry and roof/clear sky samples verify pending composition; different overlays on the same physical source cannot mix receipts. Default empty-overlay lit planar and ordinary/model/water regressions also run. Four fresh framebuffer images and source/class/helper/wrapper hashes bind this run. Main startup, full city/maximum, physical-GPU and 32-client network qualification remain pending; qualification=false.',

)
(folder / 'receipt.json').write_text(json.dumps(receipt, indent=2) + '\n')
print((folder / 'receipt.json').relative_to(root))
