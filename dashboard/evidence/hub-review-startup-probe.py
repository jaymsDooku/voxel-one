import os,time,json,subprocess
from pathlib import Path
root=Path.cwd();profile=root/'target/hub-review-startup-profile';cache=profile/'.voxel-one/client';cache.mkdir(parents=True,exist_ok=True)
(cache/'hub.properties').write_text('updates=false\n')
empty=root/'target/hub-review-empty-path';empty.mkdir(exist_ok=True)
env=os.environ.copy();env['PATH']=str(empty);env['JAVA_TOOL_OPTIONS']='-Duser.home='+str(profile)+' -Djava.io.tmpdir='+str(root/'target/hub-tmp')
app=root/'target/hub-review-final/package-1/Voxel One Hub/bin/Voxel One Hub'
start=time.monotonic();p=subprocess.Popen([str(app)],env=env,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
try:
 window=None
 for i in range(150):
  r=subprocess.run(['/usr/bin/xdotool','search','--all','--onlyvisible','--pid',str(p.pid),'--name','^Voxel One Hub$'],capture_output=True,text=True)
  if r.returncode==0 and r.stdout.strip():window=r.stdout.strip().splitlines()[0];break
  if p.poll() is not None:raise RuntimeError('Scoped preview exited')
  time.sleep(.1)
 if not window:raise RuntimeError('Scoped preview window unavailable')
 startup=time.monotonic()-start;time.sleep(1)
 subprocess.run(['/usr/bin/xdotool','windowraise',window,'windowfocus','--sync',window],check=True)
 subprocess.run(['/usr/bin/import','-window',window,str(root/'dashboard/evidence/hub-review-packaged-home.png')],check=True)
 subprocess.run(['/usr/bin/xdotool','mousemove','--window',window,'85','125','click','1'],check=True);time.sleep(.4)
 key=time.monotonic();subprocess.run(['/usr/bin/xdotool','key','--clearmodifiers','alt+2'],check=True);time.sleep(1.5)
 subprocess.run(['/usr/bin/import','-window',window,str(root/'dashboard/evidence/hub-review-packaged-games.png')],check=True)
 rss=next((x.split(':',1)[1].strip() for x in Path('/proc/'+str(p.pid)+'/status').read_text().splitlines() if x.startswith('VmRSS:')),'unavailable')
 report={'command':'python3 target/hub-review-startup.py','playtest':'Playtest: start freshly packaged preview executable with empty PATH and isolated synthetic profile; find only own PID window; Alt+2 opens Games; capture both views; terminate own test process','expected':'Bundled runtime opens Hub without system Java; keyboard switches to Games','observed':'Own window opened and both views captured; images require inspection','startupWindowSeconds':round(startup,3),'keyboardCaptureSeconds':round(time.monotonic()-key,3),'rss':rss,'reference':'Linux x86_64 VPS; inherited authenticated X11 display; bundled Oracle JDK21.0.5; one sample; physical CPU/GPU unspecified','limitation':'Window visibility timing, not fully loaded content latency. No Windows timing, monitor-DPI, physical-GPU or smooth-gameplay claim.'}
 (root/'dashboard/evidence/hub-review-startup.json').write_text(json.dumps(report,indent=2)+'\n')
 print('Scoped packaged startup probe passed')
finally:
 p.terminate()
 try:p.wait(timeout=10)
 except subprocess.TimeoutExpired:p.kill();p.wait()
