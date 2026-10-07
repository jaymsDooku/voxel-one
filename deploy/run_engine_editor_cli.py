#!/usr/bin/env python3
"""Exercise the real editor CLI with an isolated synthetic profile on assigned X11."""
import argparse, os, subprocess, time
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--display',required=True);a=p.parse_args()
root=Path(__file__).resolve().parents[1];os.chdir(root)
env=os.environ.copy();env['DISPLAY']=a.display;env['LIBGL_ALWAYS_SOFTWARE']='1';env.pop('WAYLAND_DISPLAY',None)
for name in ('target/editor-cli-home','target/editor-cli-profile','target/tmp'):Path(name).mkdir(parents=True,exist_ok=True)
jars=[str(p) for p in Path('target/maven-cache').rglob('*.jar') if 'natives-windows' not in p.name]
cp=':'.join(['target/classes']+jars)
image=root/'dashboard/evidence/engine-editor-cli.png';image.unlink(missing_ok=True)
java='/usr/lib/jvm/jdk-21.0.5-oracle-x64/bin/java'
with open('target/editor-cli-runtime-private.txt','w') as log:
    app=subprocess.Popen([java,'-Djava.io.tmpdir='+str(root/'target/tmp'),'-Duser.home='+str(root/'target/editor-cli-home'),'-cp',cp,'dev.jayms.Main','--offline','--editor','--world',str(root/'target/editor-cli-profile/synthetic.dat')],env=env,stdout=log,stderr=log)
    try:
        deadline=time.monotonic()+120;window=None
        while time.monotonic()<deadline:
            if app.poll() is not None:raise RuntimeError('Editor CLI exited before readiness')
            search=subprocess.run(['xdotool','search','--pid',str(app.pid)],env=env,capture_output=True,text=True)
            for candidate in search.stdout.splitlines():
                title=subprocess.run(['xdotool','getwindowname',candidate],env=env,capture_output=True,text=True)
                if title.returncode==0 and title.stdout.startswith('Voxel One | Offline | Isometric'):
                    window=candidate;break
            if window:break
            time.sleep(1)
        if not window:raise RuntimeError('Editor CLI window did not become ready')
        time.sleep(6)
        subprocess.run(['import','-window',window,str(image)],env=env,check=True,timeout=20)
        if not image.exists():raise RuntimeError('CLI capture was not written')
        print('Editor CLI launch and fresh window capture: PASS')
    finally:
        # Launch check only. Stop the isolated synthetic app; clean shutdown is not asserted.
        app.terminate()
        try:app.wait(timeout=15)
        except subprocess.TimeoutExpired:app.kill();app.wait()
