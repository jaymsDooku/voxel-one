#!/usr/bin/env python3
"""Run the full Main application on Xvfb. All user settings, saves and natives are local."""
from pathlib import Path
import os,subprocess,sys,socket,time,secrets,shutil
root=Path(__file__).resolve().parents[1]
os.chdir(root)
tmp=root/'target/population-main'
tmp.mkdir(parents=True,exist_ok=True)
cp=os.pathsep.join([str(root/'target/classes'),str(root/'target/test-classes')]+[str(p) for p in (root/'target/m2').rglob('*.jar')])
subprocess.run(['javac','-cp',cp,'-d',str(root/'target/test-classes'),str(root/'deploy/SpecialBuildingsSmoke.java'),str(root/'deploy/PopulationMainPlaytest.java')],check=True)
env=dict(os.environ,TMPDIR=str(tmp),XDG_CACHE_HOME=str(tmp/'cache'),LIBGL_ALWAYS_SOFTWARE='1',MESA_SHADER_CACHE_DISABLE='true')
args=['java','--enable-native-access=ALL-UNNAMED','-Duser.home='+str(tmp/'home'),'-Djava.io.tmpdir='+str(tmp),'-Dorg.lwjgl.system.SharedLibraryExtractPath='+str(tmp/'natives'),'-cp',cp,'dev.jayms.PopulationMainPlaytest',sys.argv[1] if len(sys.argv)>1 else 'dashboard/evidence']
# Unix display sockets and /tmp lock files are outside the writable worktree.
# Use a short-lived authenticated TCP display. Never print its cookie.
display=secrets.randbelow(200)+100
while True:
    probe=socket.socket()
    try: probe.bind(('127.0.0.1',6000+display));break
    except OSError: display+=1
    finally: probe.close()
auth=tmp/'display.auth'
auth.touch(mode=0o600,exist_ok=True)
subprocess.run(['xauth','-f',str(auth),'source','-'],input='add 127.0.0.1:'+str(display)+' MIT-MAGIC-COOKIE-1 '+secrets.token_hex(16)+'\n',text=True,capture_output=True,check=True)
env['DISPLAY']='127.0.0.1:'+str(display)
env['XAUTHORITY']=str(auth)
# Preserve X server locking and authentication. Relocate only its fixed /tmp
# paths in a private executable copy; the system binary and sandbox are unchanged.
xvfb=tmp/'Xvfb.local'
xvfb.write_bytes(Path('/usr/bin/Xvfb').read_bytes().replace(b'/tmp',b'./xx'))
xvfb.chmod(0o700)
(tmp/'xx').mkdir(exist_ok=True)
shutil.copytree('/usr/share/X11/xkb',tmp/'xkb',dirs_exist_ok=True)
(tmp/'xkb/xx').mkdir(exist_ok=True)
with (tmp/'display-errors.txt').open('w') as errors:
    server=subprocess.Popen([str(xvfb),':'+str(display),'-screen','0','1280x720x24','-xkbdir',str(tmp/'xkb'),'-listen','tcp','-nolisten','unix','-auth',str(auth)],env=env,cwd=tmp,stdout=subprocess.DEVNULL,stderr=errors)
    try:
        for attempt in range(100):
            if server.poll() is not None: raise RuntimeError('Test display exited before startup')
            try:
                with socket.create_connection(('127.0.0.1',6000+display),timeout=.1): break
            except OSError: time.sleep(.1)
        else: raise RuntimeError('Test display startup timed out')
        subprocess.run(args,env=env,check=True)
    finally:
        server.terminate()
        try: server.wait(timeout=5)
        except subprocess.TimeoutExpired: server.kill();server.wait()
        auth.unlink(missing_ok=True)
