#!/usr/bin/env python3
"""Run preserved parcel workflow on assigned display; keep its historical evidence intact."""
import argparse, os, shutil, subprocess
from pathlib import Path
parser = argparse.ArgumentParser()
parser.add_argument('--display', required=True)
args = parser.parse_args()
if not args.display or args.display != os.environ.get('DISPLAY'):
    raise SystemExit('Use inherited assigned role DISPLAY.')
root = Path(__file__).resolve().parents[1]
os.chdir(root)
runtime = root/'target/populated-stress-parcel-runtime'
home = root/'target/populated-stress-parcel-home'
classes = root/'target/populated-stress-parcel-classes'
for folder in (runtime, home):
    shutil.rmtree(folder, ignore_errors=True)
for folder in (runtime, home, classes):
    folder.mkdir(parents=True, exist_ok=True)
jars = sorted(Path('/home/debian/.m2/repository').rglob('*.jar'))
cp = os.pathsep.join([str(root/'target/classes'), str(classes)] +
                    [str(p) for p in jars if 'natives-windows' not in p.name and 'natives-macos' not in p.name])
subprocess.run(['javac', '-cp', cp, '-d', str(classes), 'deploy/PopulatedStressParcelSmoke.java'], check=True)
env = dict(os.environ, LIBGL_ALWAYS_SOFTWARE='1', MESA_SHADER_CACHE_DISABLE='true',
           TMPDIR=str(runtime), XDG_CACHE_HOME=str(home/'cache'))
env.pop('WAYLAND_DISPLAY', None)
command = ['java', '--enable-native-access=ALL-UNNAMED', '-Xmx1g',
           '-Djava.io.tmpdir='+str(runtime), '-Duser.home='+str(home),
           '-Dorg.lwjgl.system.SharedLibraryExtractPath='+str(runtime/'natives'),
           '-cp', cp, 'PopulatedStressParcelSmoke', str(runtime)]
with (runtime/'private-runtime.txt').open('w') as output:
    try:
        result = subprocess.run(command, env=env, stdout=output, stderr=output, timeout=600)
    except subprocess.TimeoutExpired:
        raise SystemExit('Parcel workflow timed out; no pass claimed.')
if result.returncode:
    failure = runtime/'failure.txt'
    if failure.exists():
        print(failure.read_text())
    raise SystemExit('Parcel native workflow failed. Full runtime log remains private.')
evidence = root/'dashboard/evidence'
shutil.copyfile(runtime/'results.json', evidence/'populated-stress-parcel-playtest.json')
for path in runtime.glob('parcel-*.png'):
    shutil.copyfile(path, evidence/('populated-stress-'+path.name))
clips = sorted((home/'.voxel-one/recordings').glob('*.mp4'))
if not clips:
    raise SystemExit('Production F10 clip missing.')
clip = clips[-1]
if not 0 < clip.stat().st_size <= 6_000_000:
    raise SystemExit('F10 clip outside 6 MB limit.')
shutil.copyfile(clip, evidence/'populated-stress-parcel-main.mp4')
print('PASS: retained parcel UI, all layouts, edge cases, construction, saves and road regression; fresh F10 media saved.')
