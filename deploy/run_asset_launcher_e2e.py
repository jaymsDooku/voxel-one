#!/usr/bin/env python3
"""Full Launcher/browser/engine check. Own loopback release; assigned display only."""
import argparse, os, subprocess, shutil
from pathlib import Path
parser=argparse.ArgumentParser();parser.add_argument('--display',required=True);args=parser.parse_args()
if not args.display or args.display!=os.environ.get('DISPLAY'):raise SystemExit('Inherited assigned DISPLAY required')
root=Path(__file__).resolve().parents[1];os.chdir(root)
work=root/'target/asset-launcher-e2e'
if (work/'home').exists():shutil.rmtree(work/'home')
for folder in ('classes','home','tmp'):(work/folder).mkdir(parents=True,exist_ok=True)
jar=root/'target/voxel-one-1.0-SNAPSHOT-client.jar';cp=os.pathsep.join([str(jar),str(work/'classes')])
subprocess.run(['javac','-cp',cp,'-d',str(work/'classes'),'deploy/AssetLauncherEndToEndSmoke.java'],check=True)
browser=work/'browser';browser.write_bytes((root/'deploy/asset_test_browser.py').read_bytes());browser.chmod(0o700)
env=os.environ.copy();env.update(BROWSER=str(browser),ASSET_SMOKE_RESULT=str(work/'result.txt'),ASSET_SMOKE_FIXTURE=str(work/'fixture.vxm'),PLAYWRIGHT_BROWSERS_PATH='/tmp/voxel-web-qa/browsers',TMPDIR=str(work/'tmp'));env.pop('WAYLAND_DISPLAY',None)
with (work/'private-runtime.txt').open('w') as runtime:
    process=subprocess.run(['java','-Djava.io.tmpdir='+str(work/'tmp'),'-Duser.home='+str(work/'home'),'-cp',cp,'AssetLauncherEndToEndSmoke',str(work/'result.txt'),str(jar),str(work/'fixture.vxm')],env=env,stdout=runtime,stderr=runtime,timeout=75)
print('Full Launcher/browser/engine check exit:',process.returncode)
if process.returncode:
    if (work/'result.txt').exists():print('Browser workflow:',(work/'result.txt').read_text())
    raise SystemExit(process.returncode)
print('PASS: production Launcher -> local verified client -> Chromium -> game intake')
