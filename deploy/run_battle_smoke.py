#!/usr/bin/env python3
"""Use the assigned native role display; never create a display server or load a real profile."""
import argparse
import json
import datetime
import os
from pathlib import Path
import shutil
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument('--display', required=True)
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
os.chdir(root)
scratch = root / 'target/battle' / datetime.datetime.now(datetime.timezone.utc).strftime('run-%Y%m%dT%H%M%S%f')
subprocess.run(['git', 'check-ignore', str(scratch.relative_to(root)) + '/'], check=True)
for directory in ('home', 'runtime', 'classes', 'tmp'):
    (scratch / directory).mkdir(parents=True, exist_ok=True)
env = os.environ.copy()
env.update(DISPLAY=args.display, LIBGL_ALWAYS_SOFTWARE='1', MESA_SHADER_CACHE_DISABLE='true',
           TMPDIR=str(scratch / 'tmp'), XDG_CACHE_HOME=str(scratch / 'home/cache'))
env.pop('WAYLAND_DISPLAY', None)
jars = [str(p) for p in (root / 'target/maven-cache').rglob('*.jar') if 'natives-windows' not in p.name]
cp = os.pathsep.join([str(root / 'target/classes'), str(scratch / 'classes'), *jars])
def execute(command, runtime, timeout, label):
    try:
        return subprocess.run(command, env=env, stdout=runtime, stderr=runtime, timeout=timeout)
    except subprocess.TimeoutExpired:
        raise SystemExit(label + ' timed out; private diagnostics retained in ignored scratch') from None

subprocess.run(['javac', '-cp', cp, '-d', str(scratch / 'classes'), 'deploy/BattleSmoke.java',
                'deploy/BattleBaselineSmoke.java', 'deploy/BattleLaunchSmoke.java', 'deploy/VerifyPhysicsMedia.java'], check=True)
with (scratch / 'runtime/private-runtime.txt').open('w') as runtime:
    result = execute(['java', '-Xmx512m', '-Djava.io.tmpdir=' + str(scratch / 'tmp'),
                             '-Duser.home=' + str(scratch / 'home'), '-cp', cp, 'BattleSmoke',
                             str(scratch / 'runtime')], runtime, 150, 'Battle input playtest')
if result.returncode:
    failure = scratch / 'runtime/failure.txt'
    if failure.exists():
        print(failure.read_text())
    raise SystemExit('Battle playtest failed with exit ' + str(result.returncode))
evidence = root / 'dashboard/evidence'
for file in (scratch / 'runtime').glob('battle-*.png'):
    shutil.copyfile(file, evidence / file.name)
clips = sorted((scratch / 'home/.voxel-one/recordings').glob('*.mp4'))
if not clips:
    raise SystemExit('F10 recording missing')
clip = clips[-1]
if clip.stat().st_size > 6_000_000:
    raise SystemExit('F10 recording exceeds 6 MB')
shutil.copyfile(clip, evidence / 'battle-fight.mp4')
subprocess.run(['java', '-cp', cp, 'VerifyPhysicsMedia', str(evidence / 'battle-fight.mp4'),
                str(scratch / 'runtime/media-check.json')], check=True)
report_path = scratch / 'runtime/media-check.json'
report = json.loads(report_path.read_text())
report['source'] = 'Production F10 battle fight, knockdown and labelled symmetric breakup fixtures'
report_path.write_text(json.dumps(report, indent=2) + '\n')
print('PASS: native battle input, combat, symmetric breakup fixtures, F10 and repeated reset; fresh media saved', flush=True)
print((scratch / 'runtime/results.json').read_text(), flush=True)
for mode in ('cli', 'catalog'):
    with (scratch / ('runtime/' + mode + '-private-runtime.txt')).open('w') as runtime:
        result = execute(['java', '-Xmx512m', '-Djava.io.tmpdir=' + str(scratch / 'tmp'),
                                 '-Duser.home=' + str(scratch / 'home'), '-cp', cp, 'BattleLaunchSmoke', mode],
                                runtime, 90, 'Main ' + mode + ' launch')
    if result.returncode:
        raise SystemExit('Main ' + mode + ' launch failed with exit ' + str(result.returncode))
    print('PASS: Main ' + mode + ' launch, R and Escape', flush=True)
for game in ('sandbox', 'city'):
    with (scratch / ('runtime/' + game + '-private-runtime.txt')).open('w') as runtime:
        result = execute(['java', '-Xmx512m', '-Djava.io.tmpdir=' + str(scratch / 'tmp'),
                                 '-Duser.home=' + str(scratch / 'home'), '-cp', cp, 'BattleBaselineSmoke',
                                 str(scratch / 'runtime'), game], runtime, 150, game + ' desktop reference')
    if result.returncode:
        raise SystemExit(game + ' desktop reference failed with exit ' + str(result.returncode))
    print((scratch / ('runtime/' + game + '-baseline.json')).read_text(), flush=True)
