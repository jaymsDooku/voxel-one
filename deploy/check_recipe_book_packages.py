#!/usr/bin/env python3
"""Build/check each desktop package on Linux; publish nothing and claim no foreign OS run."""
from pathlib import Path
import hashlib, json, subprocess, zipfile
root=Path(__file__).resolve().parents[1]
platforms=[('linux','natives-linux'),('windows','natives-windows'),('mac-intel','natives-macos'),('mac-arm','natives-macos-arm64')]
report={'environment':'Linux host; Java 17 target; local synthetic release metadata','physicalWindowsRun':False,'physicalMacRun':False,'published':False,'platforms':[]}
expected=None
for platform,native in platforms:
 command=['mvn','-q','-Dmaven.repo.local=target/maven-cache','-Dlwjgl.natives='+native,'-DskipTests','package']
 with (root/'target'/('recipe-'+platform+'-package-build.txt')).open('w') as log:
  result=subprocess.run(command,cwd=root,stdout=log,stderr=log)
 if result.returncode:raise SystemExit('Package build failed: '+platform)
 check=['python3','deploy/check_sandbox_package.py','--platform',platform]
 result=subprocess.run(check,cwd=root,capture_output=True,text=True)
 if result.returncode:raise SystemExit('Package CLI validation failed: '+platform)
 with zipfile.ZipFile(root/'target/voxel-one-1.0-SNAPSHOT-client.jar') as jar:
  hashes={name:hashlib.sha256(jar.read(name)).hexdigest() for name in ['dev/jayms/net/Items.class','dev/jayms/net/Crafting.class','dev/jayms/net/Protocol.class','dev/jayms/ui/InventoryHud.class']}
  assert all(int.from_bytes(jar.read(name)[6:8],'big')==61 for name in hashes),'Java 17 bytecode required'
 if expected is None:expected=hashes
 assert hashes==expected,'All desktop packages must use the same recipe/item implementation'
 report['platforms'].append({'platform':platform,'buildCommand':command,'buildExit':0,'checkCommand':check,'checkExit':0,'observed':result.stdout.strip(),'classHashes':hashes})
 print('PASS:',platform,'package, native assets, Sandbox shortcut, Java 17 classes and server CLI rejection')
report['status']='passed'
(root/'dashboard/evidence/recipe-book-packages.json').write_text(json.dumps(report,indent=2)+'\n')
