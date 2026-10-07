#!/usr/bin/env python3
"""Decode only fresh synthetic test media; write sanitized reports and end frames."""
import argparse, json, os, subprocess
from pathlib import Path
parser=argparse.ArgumentParser()
parser.add_argument('--only', choices=('road-mixed','road-mixed-airport'))
args=parser.parse_args()
root=Path(__file__).resolve().parents[1]
os.chdir(root)
classes=root/'target/road-mixed-smoke'
classes.mkdir(parents=True,exist_ok=True)
jars=[str(p) for p in Path('/tmp/voxel-m2').rglob('*.jar') if 'natives-windows' not in p.name]
cp=os.pathsep.join([str(root/'target/classes'),str(classes),*jars])
subprocess.run(['javac','-cp',cp,'-d',str(classes),'deploy/VerifyRoadVideo.java','deploy/VerifyAviationMedia.java'],check=True)
for name in ([args.only] if args.only else ('road-mixed','road-mixed-airport')):
 clip=root/'dashboard/evidence'/f'{name}-playtest.mp4'
 if not 0<clip.stat().st_size<=6_000_000:raise AssertionError('Media outside 6 MB limit')
 cmd=['java','-Xmx512m','-Djava.io.tmpdir='+str(root/'target/tmp'),'-cp',cp]
 subprocess.run([*cmd,'VerifyRoadVideo',str(clip),str(root/'target'/f'{name}-end.png'),str(root/'dashboard/evidence'/f'{name}-video-check.json')],check=True)
 subprocess.run([*cmd,'VerifyAviationMedia',str(clip),str(root/'dashboard/evidence'/f'{name}-all-frames.json')],check=True)
 report=root/'dashboard/evidence'/f'{name}-all-frames.json'
 data=json.loads(report.read_text()); data['source']='production F10 recorder in native '+name+' playtest'
 report.write_text(json.dumps(data,indent=2)+'\n')
