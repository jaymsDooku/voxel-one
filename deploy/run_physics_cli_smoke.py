#!/usr/bin/env python3
"""Launch the packaged physics entry point on the inherited role display."""
import argparse, json, os, shutil, subprocess, time
from pathlib import Path
parser=argparse.ArgumentParser()
parser.add_argument('--display',required=True)
parser.add_argument('--java',default='java')
args=parser.parse_args()
root=Path(__file__).resolve().parents[1]
os.chdir(root)
env=os.environ.copy()
env['DISPLAY']=args.display
env['LIBGL_ALWAYS_SOFTWARE']='1'
env['MESA_SHADER_CACHE_DISABLE']='true'
env.pop('WAYLAND_DISPLAY',None)
home=root/'target/physics-cli-home'
shutil.rmtree(home,ignore_errors=True)
home.mkdir(parents=True)
(root/'target/tmp').mkdir(parents=True,exist_ok=True)
env['TMPDIR']=str(root/'target/tmp')
env['XDG_CACHE_HOME']=str(home/'cache')
command=[args.java,'-Djava.io.tmpdir='+str(root/'target/tmp'),'-Duser.home='+str(home),'-jar',str(root/'target/voxel-one-1.0-SNAPSHOT-client.jar'),'--physics-lab']
with (root/'target/physics-cli-private-runtime.txt').open('w') as runtime:
    process=subprocess.Popen(command,env=env,stdout=runtime,stderr=runtime)
    try:
        deadline=time.monotonic()+30
        window=None
        while time.monotonic()<deadline and process.poll() is None:
            search=subprocess.run(['xdotool','search','--pid',str(process.pid),'--name','^Voxel One Physics Lab$'],env=env,stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,text=True)
            if search.returncode==0 and search.stdout.strip():
                window=search.stdout.splitlines()[0]
                break
            time.sleep(.1)
        if window is None:raise RuntimeError('Packaged physics window did not open')
        time.sleep(1)
        subprocess.run(['xdotool','key','--window',window,'Escape'],env=env,check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
        code=process.wait(timeout=30)
        if code!=0:raise RuntimeError('Packaged physics entry point exited with code '+str(code))
    finally:
        if process.poll() is None:
            process.terminate()
            process.wait(timeout=10)
report={'Playtest':'Packaged Main --physics-lab entry point; Linux X11; inherited assigned DISPLAY/XAUTHORITY; isolated synthetic profile; Mesa software rendering','command':command,'steps':['Launch packaged client through Main --physics-lab','Find its own X11 window by process ID','Send Escape through X11 and wait for exit'],'expected':'Window opens and Escape exits with code 0','observed':'Window opened; Escape exited with code 0','browser':'Not applicable: native LWJGL application.'}
(root/'dashboard/evidence/physics-cli-playtest.json').write_text(json.dumps(report,indent=2)+'\n')
print('PASS: packaged Main --physics-lab opened its native window and exited through Escape')
