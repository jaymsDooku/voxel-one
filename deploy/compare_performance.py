#!/usr/bin/env python3
"""Compare sanitized engine reports. Refuse unlike routes/settings and empty warm-up-only captures."""
import argparse,json,statistics
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('reports',nargs='+',type=Path);p.add_argument('--output',type=Path);a=p.parse_args()
reports=[json.loads(path.read_text()) for path in a.reports]
if len(reports)<3:p.error('at least three comparable runs are required')
fields=('os','architecture','renderer','vendor','glVersion','javaVersion','sourceRevision','width','height','route','cache','settings','samples','warmupExcluded')
for report in reports:
 if report.get('samples',0)<1:p.error('empty capture: finish warm-up before stopping')
 if any(report.get(k)!=reports[0].get(k) for k in fields):p.error('hardware/source/resolution/settings/route/cache differ; compare these groups separately')
summary={'runs':len(reports),'comparison':'same quality and environment','environment':{k:reports[0].get(k) for k in fields},'frame':{}}
for key in ('p50FrameMs','p95FrameMs','p99FrameMs','onePercentLowFps'):
 values=[r.get(key) for r in reports]
 if not all(isinstance(v,(int,float)) for v in values):p.error('frame summaries missing')
 summary['frame'][key]={'median':statistics.median(values),'min':min(values),'max':max(values)}
text=json.dumps(summary,indent=2)+'\n'
if a.output:a.output.parent.mkdir(parents=True,exist_ok=True);a.output.write_text(text)
else:print(text,end='')
