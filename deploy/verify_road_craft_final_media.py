#!/usr/bin/env python3
"""Decode captured F10 videos and save sanitized checks, without opening a display."""
import argparse,json,os,subprocess
from pathlib import Path
parser=argparse.ArgumentParser();parser.add_argument('--scenario',required=True,choices=('road','mixed','shipping','airport'));args=parser.parse_args()
root=Path(__file__).resolve().parents[1];os.chdir(root);classes=root/'target/road-shipping-final-media';classes.mkdir(parents=True,exist_ok=True)
jars=[str(p) for p in Path('/tmp/voxel-m2').rglob('*.jar') if 'natives-windows' not in p.name];cp=os.pathsep.join([str(root/'target/classes'),str(classes),*jars])
subprocess.run(['javac','-cp',cp,'-d',str(classes),'deploy/VerifyRoadVideo.java','deploy/VerifyAviationMedia.java'],check=True)
prefix='road-craft-final-'+args.scenario;clip=root/'dashboard/evidence'/f'{prefix}-playtest.mp4'
base=['java','-Xmx512m','-Djava.io.tmpdir='+str(root/'target/tmp'),'-cp',cp]
subprocess.run([*base,'VerifyRoadVideo',str(clip),str(root/'target'/f'{prefix}-end.png'),str(root/'dashboard/evidence'/f'{prefix}-video-samples.json')],check=True)
report=root/'dashboard/evidence'/f'{prefix}-video-all-frames.json'
subprocess.run([*base,'VerifyAviationMedia',str(clip),str(report)],check=True)
data=json.loads(report.read_text());data['source']='production F10 recorder in native '+args.scenario+' playtest';report.write_text(json.dumps(data,indent=2)+'\n')
