#!/usr/bin/env python3
"""Separate diagnostic render-pass run. Nonblocking per-frame queries and callsite sampling are attribution only, not performance trials."""
import argparse,os,subprocess
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--display',required=True);p.add_argument('--phase',choices=['before','after'],required=True);p.add_argument('--trial',default='1');p.add_argument('--driver-perf',action='store_true');a=p.parse_args();root=Path.cwd();label='resident-probe-'+a.phase+'-'+a.trial;out=root/'target'/label;out.mkdir(exist_ok=True);home=out/'home';home.mkdir(exist_ok=True)
probe=root/'target'/('render-probe-'+a.phase);jar=root/'target'/('streaming-baseline-client.jar' if a.phase=='before' else 'streaming-after-client.jar');env=os.environ.copy();env.update(DISPLAY=a.display,LIBGL_ALWAYS_SOFTWARE='1',MESA_GL_VERSION_OVERRIDE='3.3',MESA_GLSL_VERSION_OVERRIDE='330')
if a.driver_perf:env['GALLIVM_DEBUG']='perf'
cmd=['/usr/lib/jvm/jdk-21.0.5-oracle-x64/bin/java','-Xmx1536m','-Dvoxel.route.samples=12','-Djava.io.tmpdir='+str(root/'target/tmp'),'-Duser.home='+str(home),'-cp',str(probe/'classes')+os.pathsep+str(jar),str(probe/'RenderStallRoute.java'),str(out),'diagnostic-'+a.phase]
with (out/'runtime-private.txt').open('w') as log:r=subprocess.run(cmd,env=env,stdout=log,stderr=log,timeout=900)
print('Playtest: separate render attribution',label,'exit',r.returncode);raise SystemExit(r.returncode)
