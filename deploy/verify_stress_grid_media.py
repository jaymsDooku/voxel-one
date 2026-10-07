#!/usr/bin/env python3
"""Decode this implementation's F10 clip without opening a display."""
import os, subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[1];os.chdir(root)
classes=root/'target/stress-media-check';classes.mkdir(exist_ok=True)
jars=[str(p) for p in Path('/home/debian/.m2/repository').rglob('*.jar') if 'natives-windows' not in p.name and 'natives-macos' not in p.name]
cp=os.pathsep.join([str(root/'target/classes'),str(classes),*jars])
subprocess.run(['javac','-cp',cp,'-d',str(classes),'deploy/VerifyStressGridMedia.java'],check=True)
subprocess.run(['java','-Xmx512m','-Djava.io.tmpdir='+str(root/'target/stress-unit-tmp'),'-cp',cp,
                'VerifyStressGridMedia','dashboard/evidence/stress-grid-main.mp4',
                'dashboard/evidence/stress-grid-media.json','target/stress-grid-video-middle.png'],check=True)
