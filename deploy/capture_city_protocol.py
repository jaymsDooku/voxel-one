#!/usr/bin/env python3
"""Capture the real desktop login workflow against a synthetic pinned TLS server."""
from pathlib import Path
import os
import shutil
import subprocess
import time

root = Path(__file__).resolve().parents[1]
target = root / 'target'
# Xvfb hardcodes /tmp. Keep its locks and compiled keyboard maps in this checkout.
binary = Path(shutil.which('Xvfb')).read_bytes()
server = target / 'Xvfb-workspace'
server.write_bytes(binary.replace(b'/tmp', b'./tm'))
server.chmod(0o755)
(target / 'tm').mkdir(exist_ok=True)
shutil.copytree('/usr/share/X11/xkb', target / 'xkb', dirs_exist_ok=True)
(target / 'xkb/tm').mkdir(exist_ok=True)
classpath = os.pathsep.join(str(p) for p in (
    target / 'test-classes', target / 'classes',
    target / 'voxel-one-1.0-SNAPSHOT-client.jar'))
subprocess.run(['javac', '-cp', classpath, '-d', str(target / 'test-classes'),
                str(root / 'src/test/java/dev/jayms/CityProtocolMedia.java')], check=True)
with (target / 'city-xvfb.txt').open('w') as log:
    display = subprocess.Popen([str(server), ':191', '-screen', '0', '1280x900x24',
                                '-xkbdir', str(target / 'xkb'), '-nolisten', 'unix',
                                '-listen', 'tcp'], cwd=target, stdout=log, stderr=log)
    try:
        time.sleep(1)
        if display.poll() is not None:
            raise RuntimeError('Checkout-local Xvfb did not start; see target/city-xvfb.txt')
        env = dict(os.environ, DISPLAY='127.0.0.1:191', LIBGL_ALWAYS_SOFTWARE='1')
        for version, label in ((16, 'incompatible'), (14, 'legacy-login')):
            with (target / ('city-media-' + label + '.txt')).open('w') as output:
                subprocess.run(['java', '--enable-native-access=ALL-UNNAMED',
                    '-Djava.io.tmpdir=' + str(target / 'tmp'), '-cp', classpath,
                    'dev.jayms.CityProtocolMedia', str(version), str(target / 'city-media'),
                    str(root / 'dashboard/evidence' / ('city-protocol-' + label + '.png'))],
                    cwd=root, env=env, stdout=output, stderr=output, check=True, timeout=60)
    finally:
        display.terminate()
        display.wait(timeout=10)
print('Captured real legacy login and incompatible-version implementation images.')
