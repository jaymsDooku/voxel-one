"""Run live travel integration and capture EGL images; full-window input is separate."""
from pathlib import Path
import os
import subprocess

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
subprocess.run([str(java / 'java'), '-Djava.io.tmpdir=' + str(root / 'target/tmp'),
                '-cp', classpath, 'dev.jayms.RoadTrafficSmoke', 'dashboard/evidence'],
               env=dict(os.environ, EGL_PLATFORM='surfaceless'), check=True, timeout=60)
