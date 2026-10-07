#!/usr/bin/env python3
"""Run the existing regional Main workflow without overwriting its historical evidence."""
import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument('--display', required=True)
args = parser.parse_args()
if not args.display or args.display != os.environ.get('DISPLAY'):
    raise SystemExit('Use the assigned inherited DISPLAY.')
root = Path(__file__).resolve().parents[1]
os.chdir(root)
tmp = root / 'target/shipping-regional-playtest'
tmp.mkdir(parents=True, exist_ok=True)
profile = tmp / 'profile'
shutil.rmtree(profile, ignore_errors=True)
profile.mkdir()
for name in ('world.dat', 'world.dat.city', 'world.dat.jeep', 'failure.txt'):
    (tmp / name).unlink(missing_ok=True)
classes = tmp / 'classes'
classes.mkdir(exist_ok=True)
source = (root / 'deploy/RegionalMainPlaytest.java').read_text()
source = source.replace('target/regional-playtest', 'target/shipping-regional-playtest')
source = source.replace('regional-scale-', 'shipping-regional-')
# Wall-clock-only stopping can produce just two frames on the software renderer.
source = source.replace('if(recording && System.nanoTime()-recordStart>=6_000_000_000L)',
                        'if(recording && frames>=38 && System.nanoTime()-recordStart>=6_000_000_000L)')
source = source.replace('120_000_000_000L', '240_000_000_000L')
source = source.replace('        var game=new Main();',
                        '        glfwInitHint(GLFW_PLATFORM,GLFW_PLATFORM_X11);\n        var game=new Main();')
source = source.replace('        game.run(new RegionalMainPlaytest(out));', '''        try {
            game.run(new RegionalMainPlaytest(out));
        } catch (Throwable error) {
            Files.writeString(Path.of("target/shipping-regional-playtest/failure.txt"),
                    error.getClass().getSimpleName()+": "+error.getMessage());
            throw error;
        }''')
java_source = tmp / 'RegionalMainPlaytest.java'
java_source.write_text(source)
jars = sorted((root / 'target/m2').rglob('*.jar'))
cp = os.pathsep.join([str(classes), str(root / 'target/classes'), str(root / 'target/test-classes')]
                    + [str(j) for j in jars if 'natives-windows' not in j.name and 'natives-macos' not in j.name])
subprocess.run(['javac', '-cp', cp, '-d', str(classes),
                'deploy/SpecialBuildingsSmoke.java', str(java_source)], check=True)
env = dict(os.environ, LIBGL_ALWAYS_SOFTWARE='1', MESA_SHADER_CACHE_DISABLE='true',
           TMPDIR=str(tmp), XDG_CACHE_HOME=str(tmp / 'cache'))
env.pop('WAYLAND_DISPLAY', None)
out = root / 'dashboard/evidence'
command = ['java', '--enable-native-access=ALL-UNNAMED', '-Xmx2g',
           '-Duser.home=' + str(profile), '-Djava.io.tmpdir=' + str(tmp),
           '-Dorg.lwjgl.system.SharedLibraryExtractPath=' + str(tmp / 'natives'),
           '-cp', cp, 'dev.jayms.RegionalMainPlaytest', str(out)]
with (tmp / 'private-runtime.txt').open('w') as runtime:
    result = subprocess.run(command, env=env, stdout=runtime, stderr=runtime, timeout=300)
if result.returncode:
    failure = tmp / 'failure.txt'
    if failure.exists():
        print(failure.read_text())
    raise SystemExit('Regional native workflow failed; full runtime output remains private.')
clips = sorted(profile.rglob('*.mp4'))
if not clips:
    raise SystemExit('F10 recording missing.')
clip = clips[-1]
if clip.stat().st_size > 6_000_000:
    raise SystemExit('F10 recording exceeds 6 MB.')
shutil.copyfile(clip, out / 'shipping-regional-main.mp4')
report = {'status': 'passed', 'command': 'python3 deploy/run_shipping_regional_smoke.py --display "$DISPLAY"',
          'environment': 'Linux X11 inherited role DISPLAY and XAUTHORITY; Mesa; isolated synthetic profile under target/shipping-regional-playtest',
          'Playtest': (out / 'shipping-regional-playtest.txt').read_text(),
          'driver': 'Existing RegionalMainPlaytest production Main.run workflow; scripted input and dashboard click handlers; fresh output names',
          'historicalRegionalEvidence': 'Preserved; fresh media uses shipping-regional filenames',
          'videoBytes': clip.stat().st_size}
(out / 'shipping-regional-native-tests.json').write_text(json.dumps(report, indent=2) + '\n')
print('PASS: recovered district UI, nearby residents, starvation edge, local regression and save/reload; fresh F10 media saved.')
