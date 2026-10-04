#!/usr/bin/env python3
"""Check tested code content before submission: --revision HEAD after rebase.
Does not stage, commit, update the index, or continue the rebase.
"""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess

root = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--revision', help='Check committed contents, e.g. HEAD; default checks working files')
args = parser.parse_args()
manifest = json.loads((root/'dashboard/evidence/free-isometric-orbit-tested-tree.json').read_text())
failed = []
for name, expected in manifest['files'].items():
    if args.revision:
        result = subprocess.run(['git', 'show', args.revision+':'+name], cwd=root, capture_output=True)
        data = result.stdout if result.returncode == 0 else None
    else:
        path = root/name
        data = path.read_bytes() if path.is_file() else None
    if data is None or hashlib.sha256(data).hexdigest() != expected:
        failed.append(name)
for name, expected in manifest.get('baseBlobs', {}).items():
    command = (['git', 'rev-parse', args.revision+':'+name] if args.revision
               else ['git', 'hash-object', name])
    result = subprocess.run(command, cwd=root, capture_output=True)
    if result.returncode or result.stdout.decode().strip() != expected:
        failed.append(name)
if args.revision:
    result = subprocess.run(['git', 'show', args.revision+':dashboard/progress.json'],
                            cwd=root, capture_output=True)
    progress = json.loads(result.stdout) if result.returncode == 0 else {'items': []}
else:
    progress = json.loads((root/'dashboard/progress.json').read_text())
items = {item['id']: item for item in progress['items']}
for item_id, expected in manifest.get('progressEntryHashes', {}).items():
    actual = hashlib.sha256(json.dumps(items.get(item_id), sort_keys=True,
                           separators=(',', ':')).encode()).hexdigest()
    if actual != expected:
        failed.append('dashboard/progress.json entry '+item_id)
if failed:
    print('FAIL: tested code mismatch:', ', '.join(failed))
    raise SystemExit(1)
print('PASS: tested code fingerprint', manifest['fingerprint'], 'files', len(manifest['files']))
