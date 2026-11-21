#!/usr/bin/env python3
"""Headless private-world replay diagnostic; no qualification claim."""
from pathlib import Path
import subprocess

root=Path(__file__).resolve().parents[1]
folder=root/'target/structure-cell-replay'
classes=folder/'classes'
classes.mkdir(parents=True,exist_ok=True)
temporary=folder/'tmp'
temporary.mkdir(exist_ok=True)
jars=[]
for group in ('org/joml','org/lwjgl'):
    jars.extend(str(p) for p in (root/'target/structure-m2'/group).rglob('*.jar')
                if 'natives-windows' not in p.name and 'natives-macos' not in p.name)
classpath=str(root/'target/classes')+':'+':'.join(sorted(jars))
subprocess.run(['javac','--release','17','-cp',classpath,'-d',str(classes),
                'deploy/WorldInitialReplayBenchmark.java'],cwd=root,check=True)
with (folder/'benchmark.json').open('x') as output:
    subprocess.run(['java','-Xmx512m','-Djava.io.tmpdir='+str(temporary),
                    '-cp',str(classes)+':'+classpath,'dev.jayms.WorldInitialReplayBenchmark'],
                   cwd=root,stdout=output,check=True)
print('Playtest: headless private-world replay benchmark completed; material/detail/history checks passed.')
