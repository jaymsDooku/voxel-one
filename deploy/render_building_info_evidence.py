#!/usr/bin/env python3
"""Compile and capture the real inspector with an EGL software context, no game accounts.

Run Maven test-compile first. Pass the dependency repository as the first argument.
Defaults to the workspace-local target/maven-repository used for offline validation.
"""
from pathlib import Path
import os
import subprocess
import sys
import shutil
import zipfile

root = Path(__file__).resolve().parents[1]
repository = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else root / 'target/maven-repository'
jars = []
for module in ('lwjgl', 'lwjgl-glfw', 'lwjgl-opengl', 'lwjgl-stb'):
    jars.extend((repository / 'org/lwjgl' / module).rglob('*.jar'))
jars.extend((repository / 'org/joml/joml').rglob('*.jar'))
classpath = os.pathsep.join(str(p) for p in [root / 'target/classes', root / 'target/test-classes', *jars])
subprocess.run(['javac', '-cp', classpath, '-d', str(root / 'target/test-classes'),
                str(root / 'src/test/java/dev/jayms/BuildingInfoRenderEvidence.java')], check=True, cwd=root)
env = os.environ.copy()
env['EGL_PLATFORM'] = 'surfaceless'
env['LIBGL_ALWAYS_SOFTWARE'] = '1'
(root / 'target/tmp').mkdir(exist_ok=True)
capture_dir = root / 'target/building-info-render-evidence'
subprocess.run(['java', '--enable-native-access=ALL-UNNAMED',
                '-Djava.io.tmpdir=' + str(root / 'target/tmp'), '-cp', classpath,
                'dev.jayms.BuildingInfoRenderEvidence', str(capture_dir)],
               check=True, cwd=root, env=env)

evidence = root / 'dashboard/evidence'
with zipfile.ZipFile(evidence / 'building-info-rendered-screenshots.zip', 'w', zipfile.ZIP_DEFLATED) as archive:
    for screenshot in sorted(capture_dir.glob('*.png')):
        archive.write(screenshot, screenshot.name)
representatives = [
    'building-info-640x640-property-1-tab-0.png',
    'building-info-640x640-property-1-tab-1.png',
    'building-info-640x640-property-1-tab-1-scrolled.png',
    'building-info-640x640-property-2-tab-2.png',
    'building-info-640x640-property-2-tab-3-scrolled.png',
    'building-info-640x640-property-3-tab-2-scrolled.png',
    'building-info-640x640-property-5-tab-4.png',
    'building-info-640x640-property-7-tab-0.png',
    'building-info-640x640-property-8-tab-0.png',
    'building-info-640x640-property-9-tab-0.png',
    'building-info-640x640-property-1-demolition-confirm.png',
    'building-info-1280x720-property-2-demolition-confirm.png',
    'building-info-1280x720-property-2-tab-2.png',
    'building-info-1280x720-property-6-tab-4.png',
]
for filename in representatives:
    shutil.copyfile(capture_dir / filename, evidence / filename)
print('Packaged all renders and representative screenshots.')
