"""Bounded, local OpenGL check. Uses only synthetic state and worktree output."""
from pathlib import Path
import subprocess, os
root = Path(__file__).resolve().parents[1]
os.chdir(root)
cp = 'target/classes:target/test-classes:' + ':'.join(str(p) for p in Path('/tmp/voxel-m2/org').rglob('*.jar') if 'lwjgl' in str(p) or 'joml' in str(p))
subprocess.run(['javac', '-cp', cp, '-d', 'target/test-classes', 'deploy/BuildingGuideSmoke.java'], check=True, capture_output=True)
(root / 'target/tmp').mkdir(parents=True, exist_ok=True)
result = subprocess.run(['java', '-Djava.io.tmpdir=' + str(root / 'target/tmp'), '-cp', cp,
                         'BuildingGuideSmoke', 'dashboard/evidence'],
                        env=dict(os.environ, EGL_PLATFORM='surfaceless'),
                        capture_output=True, text=True, timeout=40)
print('Probe exit:', result.returncode)
print(result.stdout[:500])
print(result.stderr[:1200])
if result.returncode: raise SystemExit(result.returncode)
