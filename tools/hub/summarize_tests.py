#!/usr/bin/env python3
"""Export counts only. Never export Surefire properties, test output or runtime logs."""
import argparse
import json
from pathlib import Path
import xml.etree.ElementTree as ET

p=argparse.ArgumentParser();p.add_argument('--source-sha',required=True);p.add_argument('--output',required=True);a=p.parse_args()
counts={k:0 for k in ('tests','failures','errors','skipped')};suites=[]
for file in sorted(Path('target/surefire-reports').glob('TEST-*.xml')):
    root=ET.parse(file).getroot();entry={k:int(root.get(k,'0')) for k in counts};entry['suite']=root.get('name');suites.append(entry)
    for k in counts:counts[k]+=entry[k]
destination=Path(a.output);destination.parent.mkdir(parents=True,exist_ok=True)
destination.write_text(json.dumps({'sourceSha':a.source_sha,'counts':counts,'suites':suites,'limitation':'Counts only; see per-platform package/playtest report for actual execution.'},indent=2)+'\n')
