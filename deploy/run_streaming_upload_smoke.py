#!/usr/bin/env python3
"""Compile the GL integration in the application class loader; inherited role display only."""
import argparse,os,subprocess
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--display',required=True);a=p.parse_args()
root=Path(__file__).resolve().parents[1];os.chdir(root);out=root/'target/streaming-upload';out.mkdir(exist_ok=True);home=out/'home';home.mkdir(exist_ok=True);classes=out/'classes';classes.mkdir(exist_ok=True)
for name in ('failure.txt','result.txt'):(out/name).unlink(missing_ok=True)
env=os.environ.copy();env.update(DISPLAY=a.display,LIBGL_ALWAYS_SOFTWARE='1',MESA_GL_VERSION_OVERRIDE='3.3',MESA_GLSL_VERSION_OVERRIDE='330')
jdk='/usr/lib/jvm/jdk-21.0.5-oracle-x64/bin/'
with (out/'runtime-private.txt').open('w') as log:
 r=subprocess.run([jdk+'javac','-cp','target/streaming-after-client.jar','-d',str(classes),'deploy/StreamingUploadSmoke.java'],env=env,stdout=log,stderr=log)
 if r.returncode==0:r=subprocess.run([jdk+'java','-Djava.io.tmpdir='+str(root/'target/tmp'),'-Duser.home='+str(home),'-cp',str(classes)+':target/streaming-after-client.jar','dev.jayms.render.StreamingUploadSmoke',str(out)],env=env,stdout=log,stderr=log,timeout=180)
print('Playtest: staged GL upload integration; exit',r.returncode)
for name in ('failure.txt','result.txt'):
 f=out/name
 if f.exists():print(f.read_text()[:1200])
raise SystemExit(r.returncode)
