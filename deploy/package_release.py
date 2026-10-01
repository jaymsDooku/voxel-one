#!/usr/bin/env python3
"""Package immutable desktop assets and assemble the automatic-update manifest."""
import argparse
import hashlib
from pathlib import Path
import re
import shutil
import zipfile

PLATFORMS = ('windows', 'linux', 'mac-intel', 'mac-arm')
REPO = 'https://github.com/jaymsDooku/voxel-one'


def build(args):
    if not re.fullmatch('[0-9a-f]{40}', args.revision):
        raise ValueError('Expected a full Git commit SHA')
    if not re.fullmatch('playtest-[0-9]+', args.tag):
        raise ValueError('Expected an immutable playtest release tag')
    out = Path(args.output)
    out.mkdir(parents=True, exist_ok=True)
    client = Path('target/voxel-one-1.0-SNAPSHOT-client.jar')
    launcher = Path('target/voxel-one-1.0-SNAPSHOT-launcher.jar')
    name = f'voxel-one-{args.platform}.jar'
    shutil.copy2(client, out / name)
    data = client.read_bytes()
    (out / f'{args.platform}.properties').write_text(
        f'format=1\nrevision={args.revision}\n'
        f'{args.platform}.url={REPO}/releases/download/{args.tag}/{name}\n'
        f'{args.platform}.sha256={hashlib.sha256(data).hexdigest()}\n'
        f'{args.platform}.size={len(data)}\n', encoding='ascii')
    windows = args.platform == 'windows'
    script_name = 'Play Voxel One.cmd' if windows else ('Play Voxel One.command' if args.platform.startswith('mac') else 'Play Voxel One.sh')
    script = ('@echo off\r\ncd /d "%~dp0"\r\njava -jar "%~dp0voxel-one-launcher.jar" %*\r\nif errorlevel 1 pause\r\n' if windows else
              '#!/bin/sh\ncd "$(dirname "$0")" || exit 1\nexec java -jar voxel-one-launcher.jar "$@"\n')
    with zipfile.ZipFile(out / f'Voxel-One-{args.platform}.zip', 'w', zipfile.ZIP_DEFLATED) as bundle:
        bundle.write(launcher, 'voxel-one-launcher.jar')
        entry = zipfile.ZipInfo(script_name)
        entry.external_attr = 0o100755 << 16
        bundle.writestr(entry, script)
        bundle.writestr('Read me.txt',
            'VOXEL ONE\n\nRequires Java 17+ and an OpenGL 3.3 desktop.\n'
            f'Extract this folder, then run {script_name}.\n'
            'The launcher checks GitHub on every start and downloads the latest client automatically.\n'
            'Sign in using your existing account. The default server is 198.100.154.156.\n'
            'If the update service is unavailable, a previously verified installation can still start.\n'
            'Game saves, controls, and server trust settings stay in your existing .voxel-one folder.\n')
    if args.platform == 'linux':
        shutil.copy2(launcher, out / 'voxel-one-launcher.jar')
        shutil.copy2('target/voxel-one-1.0-SNAPSHOT-server.jar', out / 'voxel-one-server.jar')


def assemble(args):
    out = Path(args.output)
    entries = []
    revision = None
    for platform in PLATFORMS:
        props = dict(line.split('=', 1) for line in (out / f'{platform}.properties').read_text().splitlines() if line and not line.startswith('#'))
        if props['format'] != '1' or (revision is not None and props['revision'] != revision):
            raise ValueError('Build revisions do not agree')
        revision = props['revision']
        artifact = out / f'voxel-one-{platform}.jar'
        data = artifact.read_bytes()
        if props[f'{platform}.sha256'] != hashlib.sha256(data).hexdigest() or int(props[f'{platform}.size']) != len(data):
            raise ValueError(f'{platform} asset checksum mismatch')
        entries.extend(f'{platform}.{key}={props[f"{platform}.{key}"]}' for key in ('url', 'sha256', 'size'))
    (out / 'update.properties').write_text(f'format=1\nrevision={revision}\n' + '\n'.join(entries) + '\n', encoding='ascii')


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    commands = parser.add_subparsers(dest='command', required=True)
    b = commands.add_parser('build')
    b.add_argument('--platform', choices=PLATFORMS, required=True)
    b.add_argument('--revision', required=True)
    b.add_argument('--tag', required=True)
    b.add_argument('--output', default='target/release')
    a = commands.add_parser('assemble')
    a.add_argument('--output', default='target/release')
    args = parser.parse_args()
    (build if args.command == 'build' else assemble)(args)
