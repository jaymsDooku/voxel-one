#!/usr/bin/env python3
"""Run the actual Linux app image without java on PATH. Capture only synthetic UI."""
import json
import os
from pathlib import Path
import subprocess
import time

root=Path.cwd();profile=root/'target/hub-packaged-profile';profile.mkdir(exist_ok=True)
(profile/'.voxel-one/client').mkdir(parents=True,exist_ok=True)
(profile/'.voxel-one/client/hub.properties').write_text('updates=false\n')
empty=root/'target/hub-empty-bin';empty.mkdir(exist_ok=True)
env=os.environ.copy();env['PATH']=str(empty);env.pop('WAYLAND_DISPLAY',None)
env['JAVA_TOOL_OPTIONS']='-Duser.home='+str(profile)+' -Djava.io.tmpdir='+str(root/'target/hub-tmp')
exe=root/'target/hub-package-recovered-1f3b/Voxel One Hub/bin/Voxel One Hub'
start=time.monotonic();process=subprocess.Popen([str(exe)],env=env,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
try:
    window=''
    for attempt in range(100):
        result=subprocess.run(['/usr/bin/xdotool','search','--onlyvisible','--name','^Voxel One Hub$'],capture_output=True,text=True)
        if result.returncode==0:window=result.stdout.strip().splitlines()[-1];break
        if process.poll() is not None:raise RuntimeError('Packaged hub exited before showing its window')
        time.sleep(.1)
    if not window:raise RuntimeError('Packaged hub window unavailable')
    startup=time.monotonic()-start;time.sleep(1)
    subprocess.run(['/usr/bin/xdotool','windowraise',window,'windowfocus',window],check=True)
    subprocess.run(['/usr/bin/import','-window',window,str(root/'dashboard/evidence/hub-packaged-linux.png')],check=True)
    key_start=time.monotonic();subprocess.run(['/usr/bin/xdotool','key','alt+2'],check=True);time.sleep(.25)
    subprocess.run(['/usr/bin/import','-window',window,str(root/'dashboard/evidence/hub-packaged-library.png')],check=True)
    rss='unavailable'
    for line in Path('/proc/'+str(process.pid)+'/status').read_text().splitlines():
        if line.startswith('VmRSS:'):rss=line.split(':',1)[1].strip()
    report={'playtest':'Playtest: start bundled-runtime Linux app image with an empty PATH and synthetic user.home; open Games with Alt+2; capture home and library; terminate only this test process.',
            'expected':'Hub opens without system java on PATH; Games opens through keyboard navigation.','observed':'Window opened and both UI views captured. Media must be inspected before acceptance.',
            'startupWindowSeconds':round(startup,3),'keyboardCaptureSeconds':round(time.monotonic()-key_start,3),'rss':rss,
            'environment':'Linux x86_64; assigned inherited X11 display; bundled Oracle JDK 21.0.5 runtime; synthetic profile; no system Java on PATH.',
            'limitation':'One Linux sample. No Windows timing/DPI/install proof and no gameplay claim.'}
    (root/'dashboard/evidence/hub-package-playtest.json').write_text(json.dumps(report,indent=2)+'\n')
finally:
    process.terminate()
    try:process.wait(timeout=10)
    except subprocess.TimeoutExpired:process.kill();process.wait()
