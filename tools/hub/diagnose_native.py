#!/usr/bin/env python3
"""Keep only fixed error categories from an isolated native start; no raw logs."""
import os
from pathlib import Path
import subprocess
import threading

root=Path.cwd();profile=root/'target/hub-native-diagnostic';profile.mkdir(exist_ok=True)
env=os.environ.copy();env.pop('WAYLAND_DISPLAY',None);env['LIBGL_ALWAYS_SOFTWARE']='1'
command=['/usr/lib/jvm/jdk-21.0.5-oracle-x64/bin/java','-Duser.home='+str(profile),'-Djava.io.tmpdir='+str(root/'target/hub-tmp'),'-jar','target/voxel-one-1.0-SNAPSHOT-client.jar','--game','city','--offline']
process=subprocess.Popen(command,env=env,stdout=subprocess.DEVNULL,stderr=subprocess.PIPE,text=True)
categories=set()
def collect():
    for line in process.stderr:
        for text in ['OutOfMemoryError','UnsatisfiedLinkError','Read-only file system','Failed to create the GLFW window','Unable to initialize GLFW','Exception in thread','UnsupportedClassVersionError']:
            if text in line:categories.add(text)
thread=threading.Thread(target=collect,daemon=True);thread.start()
try:process.wait(timeout=25)
except subprocess.TimeoutExpired:process.terminate();process.wait(timeout=10)
thread.join(timeout=1)
print('Native start diagnostic categories:',sorted(categories))
print('Native process exit:',process.returncode)
