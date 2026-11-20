#!/usr/bin/env python3
"""Create a native app image with a bundled runtime. No upload or publication."""
import argparse
from pathlib import Path
import shutil
import subprocess
import sys
import zipfile
import platform


def package(output, version, installer=False, jdk=None, launcher_jar=None):
    output = Path(output).resolve()
    output.mkdir(parents=True, exist_ok=True)
    source = Path(launcher_jar or 'target/voxel-one-1.0-SNAPSHOT-launcher.jar')
    if not source.is_file():
        raise ValueError('Run mvn package first')
    inputs = output / 'input'
    inputs.mkdir(exist_ok=True)
    shutil.copy2(source, inputs / 'voxel-one-launcher.jar')
    # Retain native java commands: the verified game client starts in a child JVM.
    temp = output / 'tmp'
    temp.mkdir(exist_ok=True)
    executable = str(Path(jdk) / 'bin' / ('jpackage.exe' if sys.platform == 'win32' else 'jpackage')) if jdk else 'jpackage'
    command = [executable, '-J-Djava.io.tmpdir=' + str(temp), '--type', 'app-image', '--name', 'Voxel One Hub',
               '--app-version', version, '--input', str(inputs), '--main-jar', 'voxel-one-launcher.jar',
               '--main-class', 'dev.jayms.launcher.Bootstrap', '--java-options', '-Dvoxel.hub.preview=true', '--dest', str(output),
               '--add-modules', 'java.base,java.desktop,java.net.http,java.logging,java.naming,java.management,jdk.httpserver,jdk.management,jdk.unsupported,jdk.crypto.ec',
               '--jlink-options', '--no-man-pages --no-header-files',
               '--vendor', 'Voxel One']
    subprocess.run(command, check=True)
    app = output / ('Voxel One Hub.app' if sys.platform == 'darwin' else 'Voxel One Hub')
    shutil.copy2('tools/hub/NOTICE.txt', app / 'NOTICE.txt')
    archive = output / ('Voxel-One-Hub-' + sys.platform + '-' + platform.machine().lower() + '.zip')
    with zipfile.ZipFile(archive, 'w', zipfile.ZIP_DEFLATED) as bundle:
        for file in sorted(app.rglob('*')):
            if file.is_file():
                bundle.write(file, file.relative_to(output))
    if installer:
        command = [executable, '-J-Djava.io.tmpdir=' + str(temp), '--type', 'msi' if sys.platform == 'win32' else 'dmg' if sys.platform == 'darwin' else 'deb',
                   '--name', 'Voxel One Hub', '--app-image', str(app), '--dest', str(output), '--app-version', version]
        if sys.platform == 'win32':
            command += ['--win-shortcut', '--win-menu', '--win-menu-group', 'Voxel One', '--win-per-user-install']
        subprocess.run(command, check=True)
    return app

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', default='target/hub-package')
    parser.add_argument('--version', default='1.0.0')
    parser.add_argument('--installer', action='store_true')
    parser.add_argument('--jdk', help='Path to a supported target-platform JDK (17+)')
    parser.add_argument('--launcher-jar', help='Exact verified source JAR for a package validation generation')
    args = parser.parse_args()
    print(package(args.output, args.version, args.installer, args.jdk, args.launcher_jar))
