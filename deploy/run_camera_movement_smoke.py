#!/usr/bin/env python3
"""Run production free-camera GLFW checks on the assigned inherited X11 display.
First run: mvn -q -Dtest=CameraMovementTest,PlayerCameraTest,IsometricCameraTest test
No accounts, saves, or game profiles are loaded by this focused harness.
"""
import argparse
import os
from pathlib import Path
import subprocess

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--display', default=os.environ.get('DISPLAY'))
args = parser.parse_args()
if not args.display:
    parser.error('An assigned X11 display is required')
root = Path(__file__).resolve().parents[1]
os.chdir(root)
env = os.environ.copy()
env['DISPLAY'] = args.display
classes = root / 'target/camera-smoke'
classes.mkdir(parents=True, exist_ok=True)
home = root / 'target/camera-smoke-home'
home.mkdir(parents=True, exist_ok=True)
repository = Path.home() / '.m2/repository'
jars = [str(p) for group in ('org/lwjgl', 'org/joml')
        for p in (repository / group).rglob('*.jar')
        if 'natives-windows' not in p.name and 'natives-macos' not in p.name]
classpath = os.pathsep.join([str(root / 'target/classes'), str(classes), *jars])
subprocess.run(['javac', '-cp', classpath, '-d', str(classes),
                'deploy/CameraMovementSmoke.java'], env=env, check=True)
subprocess.run(['java', '--enable-native-access=ALL-UNNAMED',
                '-Duser.home=' + str(home), '-cp', classpath,
                'CameraMovementSmoke', 'dashboard/evidence'], env=env, check=True)
