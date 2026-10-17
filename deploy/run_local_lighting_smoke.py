#!/usr/bin/env python3
"""Assigned-display GL regression with synthetic edits; private logs stay under target."""
import argparse,os,subprocess
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--display',required=True);p.add_argument('--jar',required=True);p.add_argument('--label',required=True);a=p.parse_args()
r=Path(__file__).resolve().parents[1];out=r/'target/local-lighting'/a.label;out.mkdir(parents=True,exist_ok=True)
env=os.environ.copy();env.update(DISPLAY=a.display,LIBGL_ALWAYS_SOFTWARE='1',MESA_GL_VERSION_OVERRIDE='3.3',MESA_GLSL_VERSION_OVERRIDE='330')
cmd=['/usr/lib/jvm/jdk-21.0.5-oracle-x64/bin/java','-Xmx1536m','-Duser.home='+str(out/'home'),'-Djava.io.tmpdir='+str(r/'target/tmp'),'-cp',a.jar,'deploy/LocalLightingSmoke.java',str(out)]
with (out/'runtime-private.txt').open('w') as log:s=subprocess.run(cmd,cwd=r,env=env,stdout=log,stderr=log,timeout=300)
print('Playtest:100000-distant-edit GL cluster regression',a.label,'exit',s.returncode)
if (out/'result.json').exists():print((out/'result.json').read_text())
raise SystemExit(s.returncode)
