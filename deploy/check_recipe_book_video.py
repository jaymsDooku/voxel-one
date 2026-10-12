#!/usr/bin/env python3
"""Cut and decode fresh F10 scenes. Run after the native offline recipe tour."""
import hashlib
import json
import os
from pathlib import Path
import subprocess

root = Path(__file__).resolve().parents[1]
os.chdir(root)
jars = [str(p) for p in (root / 'target/maven-cache').rglob('*.jar')
        if 'natives-' not in p.name or 'natives-linux' in p.name]
cp = ':'.join([str(root / 'target/classes'), str(root / 'target/recipe-smoke')] + jars)
commands = [
    ['javac', '--release', '17', '-cp', cp, '-d', 'target/recipe-smoke',
     'deploy/RecipeVideoHighlights.java', 'deploy/VerifyRenderingMedia.java'],
    ['java', '-Xmx768m', '-cp', cp, 'RecipeVideoHighlights', 'target/recipe-f10-full.mp4',
     'dashboard/evidence/recipe-book-tour.mp4', 'dashboard/evidence/recipe-video-scenes.tsv'],
    ['java', '-Xmx768m', '-cp', cp, 'VerifyRenderingMedia',
     'dashboard/evidence/recipe-book-tour.mp4', 'dashboard/evidence/recipe-book-video.json',
     'dashboard/evidence/recipe-book-video-frame.png'],
]
for command in commands:
    result = subprocess.run(command)
    if result.returncode:
        raise SystemExit(result.returncode)
report = root / 'dashboard/evidence/recipe-book-video.json'
data = json.loads(report.read_text())
data.update({
    'command': 'python3 deploy/check_recipe_book_video.py',
    'presentation': 'Seven scene cuts in source order; retained frame holds capped at one second. Linux software OpenGL, low frame rate.',
    'sourceSha256': hashlib.sha256((root / 'target/recipe-f10-full.mp4').read_bytes()).hexdigest(),
    'highlightSha256': hashlib.sha256((root / 'dashboard/evidence/recipe-book-tour.mp4').read_bytes()).hexdigest(),
    'scenes': [line.split('\t')[0] for line in (root / 'dashboard/evidence/recipe-video-scenes.tsv').read_text().splitlines()],
})
report.write_text(json.dumps(data, indent=2) + '\n')
