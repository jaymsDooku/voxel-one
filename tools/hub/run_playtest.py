#!/usr/bin/env python3
"""Use only the inherited role display and isolated profiles; no X server startup."""
import argparse
import os
from pathlib import Path
import subprocess

p=argparse.ArgumentParser();p.add_argument('--display',required=True);p.add_argument('--workflow',action='store_true');p.add_argument('--no-record',action='store_true',help='Run native launch/updater checks without claiming F10 media validation');p.add_argument('--jdk',default='/usr/lib/jvm/jdk-21.0.5-oracle-x64');a=p.parse_args()
root=Path.cwd();temp=root/'target/hub-tmp';temp.mkdir(parents=True,exist_ok=True)
env=os.environ.copy();env['DISPLAY']=a.display;env.pop('WAYLAND_DISPLAY',None);env['LIBGL_ALWAYS_SOFTWARE']='1';env['ALSOFT_DRIVERS']='null'
java=Path(a.jdk)/'bin/java';javac=Path(a.jdk)/'bin/javac'
subprocess.run([str(javac),'--release','17','-cp','target/classes','-d',str(temp),'tools/hub/HubPlaytest.java','tools/hub/HubWorkflowPlaytest.java'],check=True)
main='HubWorkflowPlaytest' if a.workflow else 'HubPlaytest'
profile=root/('target/hub-final-workflow' if a.workflow else 'target/hub-final-visual')
subprocess.run([str(java),'-Djava.io.tmpdir='+str(temp),'-Dhub.playtest.skipRecording='+str(a.no_record).lower(),'-cp','target/classes:'+str(temp),main,'dashboard/evidence',str(profile)],check=True,env=env)
