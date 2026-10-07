#!/usr/bin/env python3
"""Exercise restored browser workflows without replacing historical Space Invaders media."""
import argparse, functools, http.server, os, subprocess, threading
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--display',required=True);a=p.parse_args()
if a.display!=os.environ.get('DISPLAY'):raise SystemExit('Use the assigned inherited DISPLAY.')
root=Path(__file__).resolve().parents[1];os.chdir(root)
tmp=root/'target/rendering-space-browser';tmp.mkdir(parents=True,exist_ok=True)
class QuietHandler(http.server.SimpleHTTPRequestHandler):
 def log_message(self,*args):pass
handler=functools.partial(QuietHandler,directory=str(root/'website/dist'))
server=http.server.ThreadingHTTPServer(('127.0.0.1',0),handler)
thread=threading.Thread(target=server.serve_forever,daemon=True);thread.start()
env=os.environ.copy();env['TMPDIR']=str(tmp);env['DISPLAY']=a.display
env['PLAYWRIGHT_BROWSERS_PATH']=str(root/'target/playwright-browsers')
try:
 for name in ('test_space_invaders.cjs','test_space_invaders_layout.cjs'):
  source=(root/'deploy'/name).read_text().replace('http://127.0.0.1:8766',f'http://127.0.0.1:{server.server_port}').replace('dashboard/evidence/space-invaders-','dashboard/evidence/rendering-restored-space-')
  driver=tmp/name;driver.write_text(source)
  result=subprocess.run(['node',str(driver)],env=env,timeout=180,capture_output=True,text=True)
  (tmp/(name+'.results.txt')).write_text(result.stdout+result.stderr)
  print('Playtest:',name,'fresh WebKit synthetic profiles; exit',result.returncode)
  if result.returncode:
   print(result.stderr[-2500:]);raise SystemExit(result.returncode)
finally:
 server.shutdown();server.server_close();thread.join(timeout=5)
