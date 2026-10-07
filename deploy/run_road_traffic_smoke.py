"""Run live travel integration and capture EGL images; full-window input is separate."""
from pathlib import Path
import os
import argparse
import subprocess

parser=argparse.ArgumentParser()
parser.add_argument('--display',required=True)
args=parser.parse_args()
if args.display != os.environ.get('DISPLAY'):
    parser.error('Use the inherited assigned DISPLAY.')
root = Path(__file__).resolve().parents[1]
os.chdir(root)
java = Path('/usr/lib/jvm/jdk-21.0.5-oracle-x64/bin')
classpath = 'target/classes:target/test-classes:' + ':'.join(
    str(p) for p in Path('/tmp/voxel-m2').rglob('*.jar')
    if 'natives-windows' not in p.name
)
subprocess.run([str(java / 'javac'), '-cp', classpath, '-d', 'target/test-classes',
                'deploy/RoadTrafficSmoke.java'], check=True)
(root / 'target/tmp').mkdir(parents=True, exist_ok=True)
(root / 'target/road-traffic-smoke').mkdir(parents=True, exist_ok=True)
subprocess.run([str(java / 'java'), '-Djava.io.tmpdir=' + str(root / 'target/tmp'),
                '-cp', classpath, 'dev.jayms.RoadTrafficSmoke', 'target/road-traffic-smoke'],
               env=dict(os.environ, EGL_PLATFORM='surfaceless'), check=True, timeout=60)
