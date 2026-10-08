#!/usr/bin/env python3
"""Export fixed test names/counts only. Never publish XML, messages, logs or properties."""
import argparse
import json
import os
from pathlib import Path
import re
import xml.etree.ElementTree as ET

parser=argparse.ArgumentParser()
parser.add_argument('--reports', default='target/surefire-reports')
parser.add_argument('--output', default='target/maven-test-summary.json')
args=parser.parse_args()
head=os.environ.get('GITHUB_SHA','')
summary={'schema':1,'sourceHead':head if re.fullmatch('[0-9a-f]{40}',head) else None,'suites':[],'failures':[]}
identifier=re.compile(r'^[A-Za-z_$][A-Za-z0-9_.$]{0,180}$')
for path in sorted(Path(args.reports).glob('TEST-*.xml'))[:512]:
    if path.is_symlink() or path.stat().st_size>8_000_000:continue
    try:root=ET.parse(path).getroot()
    except (ET.ParseError,OSError):continue
    name=root.get('name','')
    if not identifier.fullmatch(name):continue
    counts={key:int(root.get(key,'0')) for key in ('tests','failures','errors','skipped')}
    summary['suites'].append({'name':name,**counts})
    for case in root.findall('testcase'):
        test=case.get('name','')
        if not identifier.fullmatch(test):continue
        for kind in ('failure','error'):
            if case.find(kind) is not None:
                summary['failures'].append({'suite':name,'test':test,'kind':kind})
output=Path(args.output);output.parent.mkdir(parents=True,exist_ok=True)
output.write_text(json.dumps(summary,indent=2)+'\n')
print('Sanitized Maven summary:',len(summary['suites']),'suites;',len(summary['failures']),'failed cases')
