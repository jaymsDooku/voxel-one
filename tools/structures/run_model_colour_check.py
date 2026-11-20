#!/usr/bin/env python3
"""Capture controlled production-shader model colours on the assigned display."""
import argparse
import datetime
import hashlib
import json
import os
import platform
import re
import subprocess
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument('--display', required=True)
parser.add_argument('--evidence-prefix', default='structure-model-colour')
args = parser.parse_args()
if args.display != os.environ.get('DISPLAY'):
    parser.error('Use the inherited assigned DISPLAY')
if not re.fullmatch(r'[a-z0-9][a-z0-9-]{0,63}', args.evidence_prefix):
    parser.error('Use a fresh simple evidence prefix of 1–64 characters')
root = Path(__file__).resolve().parents[2]
image = root / 'dashboard/evidence' / (args.evidence_prefix + '.png')
report = image.with_suffix('.json')
if image.exists() or report.exists():
    raise SystemExit('Preserve existing evidence; choose a fresh prefix')
folder = root / 'target/model-colour-check' / datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
folder.mkdir(parents=True)


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


source_files = sorted((root / 'src/main/java').rglob('*.java'))
shaders = sorted((root / 'src/main/resources/shaders').glob('*'))
source_files += [p for p in shaders if p.is_file()]
helper = root / 'tools/structures/ModelColourCheck.java'
source_files += [helper, Path(__file__).resolve()]
sources = {str(p.relative_to(root)): sha(p) for p in source_files}
classes = {str(p.relative_to(root)): sha(p) for p in sorted((root / 'target/classes').rglob('*.class'))}
if not classes:
    raise SystemExit('Compile production classes first')
for shader in shaders:
    if shader.is_file():
        assert sha(shader) == sha(root / 'target/classes/shaders' / shader.name), 'Rebuild stale shader resources'
cp = (root / 'target/structure-limit-audit/diagnostic-classpath.txt').read_text().strip()
commands = [
    ['javac', '--release', '17', '-cp', cp, '-d', str(folder), str(helper)],
    ['java', '-Xmx256m', '-Djava.io.tmpdir=' + str(root / 'target/reviewer-tmp'),
     '-cp', str(folder) + ':' + cp, 'dev.jayms.net.ModelColourCheck',
     '--display', args.display, str(folder / 'result.json'), str(folder / 'capture.png')],
]
env = os.environ.copy()
env['LIBGL_ALWAYS_SOFTWARE'] = '1'
env['LP_NUM_THREADS'] = '2'
for command in commands:
    subprocess.run(command, cwd=root, env=env, check=True, timeout=180)
assert all(sha(root / p) == digest for p, digest in sources.items())
assert all(sha(root / p) == digest for p, digest in classes.items())
capture = folder / 'capture.png'
assert 0 < capture.stat().st_size < 6_000_000
receipt = dict(
    result=json.loads((folder / 'result.json').read_text()), qualification=False,
    command='python3 tools/structures/run_model_colour_check.py --display "$DISPLAY" --evidence-prefix ' + args.evidence_prefix,
    source_sha256=sources, compiled_sha256=classes, image_sha256=sha(capture),
    os=platform.platform(),
    java=subprocess.run(['java', '-version'], capture_output=True, text=True, check=True).stderr.strip(),
    rows=['unlit production shader', 'ambient/daylight, no irradiance', 'held path with known camera irradiance', 'held path with zero camera irradiance and zero sky'],
    columns=['instanced red8 custom model', 'direct blue32 custom model', 'ordinary block using uniform red colour'],
    method='Playtest: real GLFW/OpenGL on inherited DISPLAY; synthetic definitions and one ordinary block, shipped voxel vertex/fragment shaders. All three controls must show their expected red/blue colour in rows 1–3 and stay black with zero light in row 4. This isolates mesh/colour transfer from lighting coverage. It does not establish Main lighting readiness, diagnose its camera coverage, or qualify city/maximum/physical-GPU/network performance.',
    publication='Local evidence pending controller publication',
)
# Publish local artifacts only after every assertion and source check succeeds.
with image.open('xb') as output:
    output.write(capture.read_bytes())
with report.open('x') as output:
    json.dump(receipt, output, indent=2)
    output.write('\n')
print('Playtest: fresh production-shader colour control evidence saved.')
