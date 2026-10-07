"""Run the real native game with X11 input and F10. Requires approved local sockets.
All state and private output stay in this worktree's target/. Only sanitized captures
and a small assertion report are copied to dashboard/evidence/ after successful checks.
"""
from pathlib import Path
import os, subprocess, time, shutil, argparse, re
parser = argparse.ArgumentParser()
parser.add_argument('--artifact-prefix', default='')
parser.add_argument('--display', required=True)
args = parser.parse_args()
if not args.display or args.display != os.environ.get('DISPLAY'):
    parser.error('Use the inherited assigned DISPLAY.')
prefix = args.artifact_prefix
if not re.fullmatch(r'[A-Za-z0-9-]*', prefix):
    parser.error('artifact prefix must contain only letters, digits or hyphens')
root = Path(__file__).resolve().parents[1]
os.chdir(root)
java = Path('/usr/lib/jvm/jdk-21.0.5-oracle-x64/bin')
for folder in ('target/traffic-gameplay', 'target/traffic-home', 'target/tmp', 'target/xvfb-tmp'):
    (root / folder).mkdir(parents=True, exist_ok=True)
env = dict(os.environ, DISPLAY=args.display, LIBGL_ALWAYS_SOFTWARE='1',
           TMPDIR=str(root / 'target/xvfb-tmp'), XDG_SESSION_TYPE='x11')
env.pop('WAYLAND_DISPLAY', None)
classpath = 'target/classes:target/traffic-gameplay:' + ':'.join(
    str(p) for p in Path('/tmp/voxel-m2').rglob('*.jar') if 'natives-windows' not in p.name)
subprocess.run([str(java / 'javac'), '-cp', classpath, '-d', 'target/traffic-gameplay',
                'deploy/TrafficGameplaySmoke.java'], check=True)
out = root / ('target/traffic-gameplay/captures-' + prefix if prefix else 'target/traffic-gameplay/captures')
out.mkdir(exist_ok=True)
(out / 'results.json').unlink(missing_ok=True)
for old_capture in out.glob('road-traffic-game-*.png'):
    old_capture.unlink()
for generated_save in out.glob('synthetic-world.dat*'):
    if generated_save.is_file(): generated_save.unlink()
# Require three fresh clips from this synthetic run, never a previous recording.
shutil.rmtree(root/'target/traffic-home/.voxel-one/recordings', ignore_errors=True)
with open('target/traffic-x11-private.txt','w') as display_log, open('target/traffic-gameplay-private.txt','w') as runtime:
    engine = None
    try:
        probe = subprocess.run(['xdotool','getdisplaygeometry'], env=env, capture_output=True)
        print('X11 probe exit:', probe.returncode)
        if probe.returncode: raise SystemExit(probe.returncode)
        engine = subprocess.Popen([str(java / 'java'), '-Djava.io.tmpdir='+str(root/'target/tmp'),
                '-Duser.home='+str(root/'target/traffic-home'), '-cp', classpath,
                'TrafficGameplaySmoke', str(out)], env=env, stdout=runtime, stderr=runtime)
        result = engine.wait(timeout=240)
        print('Native gameplay harness exit:',result)
        if result: raise SystemExit(result)
        for p in out.glob('road-traffic-game-*.png'): shutil.copyfile(p,root/'dashboard/evidence'/(prefix+p.name))
        shutil.copyfile(out/'results.json',root/'dashboard/evidence'/(prefix+'road-traffic-native-gameplay.json'))
        videos = sorted((root/'target/traffic-home/.voxel-one/recordings').glob('*.mp4'), key=lambda p:p.stat().st_mtime)
        if len(videos) < 3: raise RuntimeError('All three F10 recordings required')
        for p, name in zip(videos[-3:], ('road-traffic-game-pedestrians.mp4','road-traffic-game-horses.mp4','road-traffic-game-crossing.mp4')):
            if p.stat().st_size >= 6*1024*1024: raise RuntimeError('F10 artifact exceeds 6 MB')
            shutil.copyfile(p,root/'dashboard/evidence'/(prefix+name))
        print('Saved locally:', len(list(out.glob('road-traffic-game-*.png'))), 'screenshots, three production F10 clips, one assertion report')
    finally:
        if engine is not None and engine.poll() is None: engine.kill();engine.wait()
