#!/usr/bin/env python3
"""Run IsometricOrbitSmoke after Maven test compilation, using target/maven-cache.
Requires Xvfb, xdotool, JDK 21 and local X11 socket access. Runtime logs and the
isolated offline game profile remain under target/. Prints only test exit status.
The launcher compiles its smoke driver and constructs the classpath from
target/classes and cached jar files. Runtime output is not public evidence.
"""
import subprocess,os,time
from pathlib import Path
root=Path(__file__).resolve().parents[1];os.chdir(root);env=os.environ.copy();env['DISPLAY']=':118';env['LIBGL_ALWAYS_SOFTWARE']='1';env['TMPDIR']=str(root/'target/xvfb-tmp');env.pop('WAYLAND_DISPLAY',None);env['XDG_SESSION_TYPE']='x11'
for folder in ('target/orbit-smoke', 'target/orbit-home', 'target/tmp', 'target/xvfb-tmp'):
 (root/folder).mkdir(parents=True,exist_ok=True)
jars=[str(p) for p in (root/'target/maven-cache').rglob('*.jar') if 'natives-windows' not in p.name]
if not jars or not (root/'target/classes/dev/jayms/Main.class').exists():
 raise SystemExit('First compile the engine with Maven using target/maven-cache.')
classpath=str(root/'target/classes')+':'+str(root/'target/orbit-smoke')+':'+':'.join(jars)
Path('target/orbit-classpath.txt').write_text(classpath)
subprocess.run(['/usr/lib/jvm/jdk-21.0.5-oracle-x64/bin/javac','-cp',classpath,'-d','target/orbit-smoke','deploy/IsometricOrbitSmoke.java'],check=True)
with open('target/orbit-xvfb.txt','w') as log, open('target/orbit-graphical-private.txt','w') as runtime:
 server=subprocess.Popen(['Xvfb',':118','-screen','0','1280x720x24','-nolisten','tcp'],env=env,stdout=log,stderr=log)
 engine=None
 Path('target/orbit-graphical/results.json').unlink(missing_ok=True)
 try:
  time.sleep(2)
  engine=subprocess.Popen(['/usr/lib/jvm/jdk-21.0.5-oracle-x64/bin/java','-Djava.io.tmpdir='+str(root/'target/tmp'),'-Duser.home='+str(root/'target/orbit-home'),'-cp',Path('target/orbit-classpath.txt').read_text(),'IsometricOrbitSmoke',str(root/'target/orbit-graphical')],env=env,stdout=runtime,stderr=runtime)
  result=engine.wait(timeout=100)
  print('Graphical harness exit:',result)
  if result:raise SystemExit(result)
 finally:
  if engine is not None and engine.poll() is None:engine.kill();engine.wait()
  server.terminate();server.wait()
