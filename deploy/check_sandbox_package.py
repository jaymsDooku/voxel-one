#!/usr/bin/env python3
"""Check real local release packaging without uploading assets or starting servers."""
import argparse
from pathlib import Path
import subprocess
from types import SimpleNamespace
import zipfile
import package_release

parser = argparse.ArgumentParser()
parser.add_argument('--platform', choices=package_release.PLATFORMS, required=True)
args = parser.parse_args()
out = Path('target/sandbox-package-check') / args.platform
package_release.build(SimpleNamespace(platform=args.platform, revision='0'*40, tag='playtest-0', output=str(out)))
ext = 'cmd' if args.platform == 'windows' else 'command' if args.platform.startswith('mac') else 'sh'
with zipfile.ZipFile(out / f'Voxel-One-{args.platform}.zip') as bundle:
    original = f'Play Voxel One.{ext}'
    sandbox = f'Play Voxel Sandbox.{ext}'
    assert original in bundle.namelist() and sandbox in bundle.namelist()
    assert '--game sandbox' in bundle.read(sandbox).decode()
    assert '--game sandbox' not in bundle.read(original).decode()
    assert ('%*' if ext == 'cmd' else '"$@"') in bundle.read(sandbox).decode()
    assert bundle.read('voxel-one-launcher.jar') == Path('target/voxel-one-1.0-SNAPSHOT-launcher.jar').read_bytes()
with zipfile.ZipFile('target/voxel-one-1.0-SNAPSHOT-client.jar') as client:
    assert 'dev/jayms/GameIdentity.class' in client.namelist()
    native_ext = '.dll' if args.platform == 'windows' else '.dylib' if args.platform.startswith('mac') else '.so'
    assert any(name.endswith(native_ext) for name in client.namelist())
with zipfile.ZipFile('target/voxel-one-1.0-SNAPSHOT-server.jar') as server:
    assert 'dev/jayms/GameIdentity.class' in server.namelist()
result = subprocess.run(['java','-jar','target/voxel-one-1.0-SNAPSHOT-server.jar','--game','city','--port','0'], capture_output=True, text=True, timeout=20)
assert result.returncode != 0 and 'Port must be from 1 to 65535' in result.stderr
assert 'NoClassDefFoundError' not in result.stderr
print(f'PASS: {args.platform} release ZIP preserves Voxel One, adds Sandbox shortcut, forwards args, includes native libraries and game identity; packaged server rejects invalid port before opening accounts/network. Local synthetic release metadata; nothing published.')
