#!/usr/bin/env python3
"""Compare an isolated rejected replay candidate with its unchanged production parent."""
import datetime
import hashlib
import json
import os
from pathlib import Path
import subprocess

root = Path(__file__).resolve().parents[2]
parent = 'acd476734c69f6e2405a5b68f896afee5e74faf3'
source = 'src/main/java/dev/jayms/net/WorldVoxels.java'
# Reuse compiled dependencies only when all production sources match the recorded parent.
subprocess.run(['git', 'diff', '--quiet', parent, '--', 'src/main/java'], cwd=root, check=True)
folder = root / 'target/replay-retention' / datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
folder.mkdir(parents=True)
temporary = folder / "tmp"
temporary.mkdir()
child_env = dict(os.environ, TMPDIR=str(temporary))
class_hashes = {str(p.relative_to(root)): hashlib.sha256(p.read_bytes()).hexdigest()
                for p in sorted((root / 'target/classes').rglob('*.class'))}
if not class_hashes:
    raise SystemExit('Compile the unchanged production source before running this probe')
parent_source = subprocess.check_output(['git', 'show', parent + ':' + source], cwd=root)
patch = root / 'tools/structures/primitive-cell-replay-candidate.patch'
probe_source = root / 'tools/structures/PrimitiveCellReplayProbe.java'
for mode in ('baseline', 'candidate'):
    staged = folder / mode / 'source'
    path = staged / source
    path.parent.mkdir(parents=True)
    path.write_bytes(parent_source)
    if mode == 'candidate':
        subprocess.run(['patch', '--batch', '--fuzz=0', '-p1', '-d', str(staged)],
                       input=patch.read_bytes(), cwd=root, env=child_env, check=True, stdout=subprocess.DEVNULL)
    classes = folder / mode / 'classes'
    classes.mkdir()
    subprocess.run(['javac', '--release', '17', '-cp', str(root / 'target/classes'), '-d', str(classes), str(path)], cwd=root, check=True)
probe_classes = folder / 'probe'
probe_classes.mkdir()
subprocess.run(['javac', '--release', '17', '-cp', str(root / 'target/classes'), '-d', str(probe_classes), str(probe_source)], cwd=root, check=True)
manifest = folder / 'agent.mf'
manifest.write_text('Premain-Class: dev.jayms.net.PrimitiveCellReplayProbe\n')
agent = folder / 'agent.jar'
subprocess.run(['jar', 'cfm', str(agent), str(manifest), '-C', str(probe_classes), 'dev/jayms/net/PrimitiveCellReplayProbe.class'], cwd=root, check=True)
results = {}
commands = []
for mode in ('baseline', 'candidate'):
    command = ['java', '-Xmx512m', '-Djava.io.tmpdir=' + str(temporary), '--add-opens', 'java.base/java.lang=ALL-UNNAMED', '--add-opens', 'java.base/java.util=ALL-UNNAMED',
               '-javaagent:' + str(agent), '-cp', ':'.join(map(str, [probe_classes, folder / mode / 'classes', root / 'target/classes'])), 'dev.jayms.net.PrimitiveCellReplayProbe']
    commands.append(command)
    with (folder / (mode + '.json')).open('w') as output:
        subprocess.run(command, cwd=root, stdout=output, check=True)
    results[mode] = json.loads((folder / (mode + '.json')).read_text())
assert all(hashlib.sha256((root / p).read_bytes()).hexdigest() == digest for p, digest in class_hashes.items())
subprocess.run(['git', 'diff', '--quiet', parent, '--', 'src/main/java'], cwd=root, check=True)
receipt = {'parent': parent, 'parent_source_sha256': hashlib.sha256(parent_source).hexdigest(),
           'candidate_patch_sha256': hashlib.sha256(patch.read_bytes()).hexdigest(),
           'probe_sha256': hashlib.sha256(probe_source.read_bytes()).hexdigest(),
           'compiled_dependencies_sha256': class_hashes, 'commands': commands, 'results': results,
           'production_changed': False, 'qualification': False}
(folder / 'comparison.json').write_text(json.dumps(receipt, indent=2) + '\n')
print(folder.relative_to(root) / 'comparison.json')
