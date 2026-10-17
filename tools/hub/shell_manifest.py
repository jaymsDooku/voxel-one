#!/usr/bin/env python3
"""Generate the separate verified shell release manifest; does not publish it."""
import argparse
import hashlib
from pathlib import Path
import re
import zipfile


def assemble(jar, revision, tag, output):
    if not re.fullmatch('[0-9a-f]{40}', revision) or not re.fullmatch(r'(?:playtest|hub-preview)-[1-9][0-9]*', tag):
        raise ValueError('Use a full revision and immutable playtest or hub-preview tag')
    payload = Path(jar).read_bytes()
    if not 0 < len(payload) <= 100*1024*1024:
        raise ValueError('Shell size out of bounds')
    with zipfile.ZipFile(jar) as z:
        if 'dev/jayms/launcher/Launcher.class' not in z.namelist() or 'dev.jayms.launcher.Launcher' not in z.read('META-INF/MANIFEST.MF').decode():
            raise ValueError('Invalid shell entry point')
    root = Path(output); root.mkdir(parents=True, exist_ok=True)
    (root / 'voxel-one-shell.jar').write_bytes(payload)
    lines = ['format=1', 'revision='+revision]
    for platform in ('windows', 'linux', 'mac-intel', 'mac-arm'):
        lines += [f'{platform}.url=https://github.com/jaymsDooku/voxel-one/releases/download/{tag}/voxel-one-shell.jar',
                  f'{platform}.sha256={hashlib.sha256(payload).hexdigest()}', f'{platform}.size={len(payload)}']
    (root / 'shell.properties').write_text('\n'.join(lines)+'\n')

if __name__ == '__main__':
    p=argparse.ArgumentParser();p.add_argument('--jar',default='target/voxel-one-1.0-SNAPSHOT-launcher.jar');p.add_argument('--revision',required=True);p.add_argument('--tag',required=True);p.add_argument('--output',default='target/release');a=p.parse_args()
    assemble(a.jar,a.revision,a.tag,a.output)
