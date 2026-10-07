#!/usr/bin/env python3
"""Run the baseline spacing smoke driver on the inherited role display.
Use a fresh synthetic profile and separate sanitized railway regression evidence.
Historical spacing source and evidence remain unchanged.
"""
import subprocess,os,time,argparse,shutil,json
from pathlib import Path
parser=argparse.ArgumentParser();parser.add_argument('--display',required=True,help='Use an available X11 display instead of starting Xvfb');parser.add_argument('--pedestrian-spacing',default='1.0');parser.add_argument('--mounted-spacing',default='1.4');parser.add_argument('--output',default='target/railway-spacing-playtest');args=parser.parse_args()
if args.display != os.environ.get('DISPLAY'): raise SystemExit('Use the inherited assigned DISPLAY')
root=Path(__file__).resolve().parents[1];os.chdir(root);env=os.environ.copy();env['DISPLAY']=args.display;env['LIBGL_ALWAYS_SOFTWARE']='1';env['TMPDIR']=str(root/'target/xvfb-tmp');env.pop('WAYLAND_DISPLAY',None);env['XDG_SESSION_TYPE']='x11'
spacing_output=(root/args.output).resolve()
if not spacing_output.is_relative_to((root/'target').resolve()) or spacing_output==(root/'target').resolve(): raise SystemExit('Output must be a test directory inside target/')
shutil.rmtree(root/'target/spacing-home',ignore_errors=True)
shutil.rmtree(root/args.output,ignore_errors=True)
for folder in ('target/spacing-smoke', 'target/spacing-home', 'target/tmp', 'target/xvfb-tmp'):
 (root/folder).mkdir(parents=True,exist_ok=True)
jars=[str(p) for p in (root/'target/maven-cache').rglob('*.jar') if 'natives-windows' not in p.name]
if not jars or not (root/'target/classes/dev/jayms/Main.class').exists():
 raise SystemExit('First compile the engine with Maven using target/maven-cache.')
classpath=str(root/'target/classes')+':'+str(root/'target/spacing-smoke')+':'+':'.join(jars)
Path('target/spacing-classpath.txt').write_text(classpath)
subprocess.run(['/usr/lib/jvm/jdk-21.0.5-oracle-x64/bin/javac','-cp',classpath,'-d','target/spacing-smoke','deploy/RoadSpacingSmoke.java'],check=True)
with open('target/spacing-graphical-private.txt','w') as runtime:
 engine=None
 out=root/args.output;out.mkdir(parents=True,exist_ok=True)
 out.joinpath('results.json').unlink(missing_ok=True)
 try:
  time.sleep(2)
  engine=subprocess.Popen(['/usr/lib/jvm/jdk-21.0.5-oracle-x64/bin/java','-Djava.io.tmpdir='+str(root/'target/tmp'),'-Duser.home='+str(root/'target/spacing-home'),'-cp',Path('target/spacing-classpath.txt').read_text(),'RoadSpacingSmoke',str(out),args.pedestrian_spacing,args.mounted_spacing],env=env,stdout=runtime,stderr=runtime)
  result=engine.wait(timeout=600)
  print('Graphical harness exit:',result)
  if result:raise SystemExit(result)
 finally:
  if engine is not None and engine.poll() is None:engine.kill();engine.wait()

report=json.loads((out/'results.json').read_text()); report['platform']='Linux inherited assigned X11 display; Mesa software OpenGL'; report['profile']='fresh isolated synthetic'; report['publication']='Pending controller publication'
(root/'dashboard/evidence/railway-spacing-playtest.json').write_text(json.dumps(report,indent=2)+'\n')
clips=sorted((root/'target/spacing-home/.voxel-one/recordings').glob('*.mp4'))
if not clips: raise SystemExit('F10 spacing recording missing')
if clips[-1].stat().st_size>6_000_000: raise SystemExit('Spacing clip exceeds 6 MB')
shutil.copy2(clips[-1],root/'dashboard/evidence/railway-spacing-playtest.mp4')
