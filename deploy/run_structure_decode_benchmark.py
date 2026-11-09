#!/usr/bin/env python3
"""Serial same-fixture parent/current decoder diagnostic; no full qualification."""
from pathlib import Path
import subprocess

root=Path(__file__).resolve().parents[1]
folder=root/'target/structure-decode-single-pass'
classes=folder/'benchmark-classes'
classes.mkdir(exist_ok=False)
temporary=folder/'tmp'
temporary.mkdir(exist_ok=False)
jars=[]
for group in ('org/joml','org/lwjgl'):
    jars.extend(str(p) for p in (root/'target/structure-m2'/group).rglob('*.jar')
                if 'natives-windows' not in p.name and 'natives-macos' not in p.name)
classpath=str(root/'target/classes')+':'+':'.join(sorted(jars))
subprocess.run(['javac','--release','17','-cp',classpath,'-d',str(classes),
                'deploy/StructureDecodeBenchmark.java'],cwd=root,check=True)
def run(production,*args):
    subprocess.run(['java','-Xmx512m','-Djava.io.tmpdir='+str(temporary),'-cp',
                    str(classes)+':'+str(production)+':'+':'.join(sorted(jars)),
                    'StructureDecodeBenchmark',*map(str,args)],cwd=root,check=True)
run(folder/'baseline-classes','prepare',folder)
for fixture in ('uniform','dense'):
    for label,production in (('parent',folder/'baseline-classes'),('current',root/'target/classes')):
        run(production,folder/(fixture+'.vstr'),folder/(label+'-'+fixture+'.json'),label)
print('Playtest: serial parent/current decoder diagnostics completed; all roundtrip checks passed.')
