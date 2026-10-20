#!/usr/bin/env python3
"""Native MP3 checks on the assigned display, using the shipped client JAR."""
import argparse
import os
from pathlib import Path
import shutil
import subprocess

p = argparse.ArgumentParser()
p.add_argument('--display', required=True)
a = p.parse_args()
if not a.display or a.display != os.environ.get('DISPLAY'):
    raise SystemExit('Inherited assigned DISPLAY required')
root = Path(__file__).resolve().parents[1]
os.chdir(root)
for name in ('music-mp3-home', 'music-mp3-runtime', 'music-mp3-smoke', 'tmp'):
    (root / 'target' / name).mkdir(parents=True, exist_ok=True)
out = root / 'target/music-mp3-runtime'
for old in out.glob('music-mp3-*'):
    if old.is_file(): old.unlink()
(out / 'failure.txt').unlink(missing_ok=True)
recordings = root/'target/music-mp3-home/.voxel-one/recordings'
previous_clips = set(recordings.glob('*.mp4'))
cp = os.pathsep.join([str(root/'target/voxel-one-1.0-SNAPSHOT-client.jar'), str(root/'target/music-mp3-smoke')])
subprocess.run(['javac', '--release', '17', '-cp', cp, '-d', 'target/music-mp3-smoke', 'deploy/MusicSmoke.java', 'deploy/MusicMp3Smoke.java'], check=True)
env = os.environ.copy()
env.update(DISPLAY=a.display, LIBGL_ALWAYS_SOFTWARE='1', MESA_SHADER_CACHE_DISABLE='true', ALSOFT_DRIVERS='null', TMPDIR=str(root/'target/tmp'), XDG_CACHE_HOME=str(root/'target/music-mp3-home/cache'))
env.pop('WAYLAND_DISPLAY', None)
with (out/'private-runtime.txt').open('w') as runtime:
    result = subprocess.run(['java', '-Xmx768m', '-Djava.io.tmpdir='+str(root/'target/tmp'), '-Duser.home='+str(root/'target/music-mp3-home'), '-cp', cp, 'MusicMp3Smoke', str(out)], env=env, stdout=runtime, stderr=runtime, timeout=120)
print('MP3 native playtest exit:', result.returncode)
if result.returncode:
    if (out/'failure.txt').exists(): print((out/'failure.txt').read_text())
    raise SystemExit(result.returncode)
for file in out.glob('music-mp3-*'):
    if file.suffix in ('.png', '.json'): shutil.copyfile(file, root/'dashboard/evidence'/file.name)
clips = sorted(set(recordings.glob('*.mp4')) - previous_clips)
if not clips: raise SystemExit('F10 clip missing')
if clips[-1].stat().st_size > 6_000_000: raise SystemExit('F10 clip exceeds 6 MB')
shutil.copyfile(clips[-1], root/'dashboard/evidence/music-mp3.mp4')
print('PASS: fresh MP3 F10 video bytes:', clips[-1].stat().st_size)
