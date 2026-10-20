#!/usr/bin/env python3
"""Decode the exact finished local evidence clip. No graphical display is used."""
import os, subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[1];os.chdir(root)
(root/'target/music-smoke').mkdir(parents=True,exist_ok=True)
cp=os.pathsep.join(['target/classes','target/music-smoke',*[str(p) for p in Path('target/maven-cache').rglob('*.jar')]])
subprocess.run(['javac','-cp',cp,'-d','target/music-smoke','deploy/VerifyMusicMedia.java'],check=True)
result=subprocess.run(['java','-cp',cp,'VerifyMusicMedia','dashboard/evidence/music-visualizer.mp4','dashboard/evidence/music-media-check.json','dashboard/evidence/music-video-contact.png'],capture_output=True,text=True)
print(result.stdout.strip())
if result.returncode:
    print(result.stderr[:1000]);raise SystemExit(result.returncode)
