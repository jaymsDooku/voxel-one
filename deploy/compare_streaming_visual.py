#!/usr/bin/env python3
"""Compare resolved visible-surface coverage; this is not a frame timing report."""
import argparse,json,math,statistics
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--before',required=True);p.add_argument('--after',required=True);p.add_argument('--out',required=True);a=p.parse_args()
before=json.loads(Path(a.before).read_text());after=json.loads(Path(a.after).read_text())
if before['fixtureSha256']!=after['fixtureSha256']:raise SystemExit('Visual fixtures differ; no matched comparison')
def indexed(report):
 rows={r['sample']:r for r in report['samples']}
 if set(rows)!=set(range(166)):raise SystemExit('Incomplete dense route')
 return rows
b=indexed(before);c=indexed(after)
camera_mismatches=[]
for i in b:
 if b[i]['segment']!=c[i]['segment']:raise SystemExit('Segment mismatch at '+str(i))
 if any(abs(x-y)>.001 for x,y in zip(b[i]['eye'],c[i]['eye'])):
  if b[i]['segment'] in ('coldFlight','warmReversal'):raise SystemExit('Cold flight/reversal camera mismatch at '+str(i))
  camera_mismatches.append({'sample':i,'segment':b[i]['segment'],'before':b[i]['eye'],'after':c[i]['eye']})
def ratio(r):return r['near24Covered']/r['near24Eligible'] if r['near24Eligible'] else None
def stats(rows):
 eligible=sum(r['near24Eligible'] for r in rows);ratios=[ratio(r) for r in rows if ratio(r) is not None];longest=run=0
 for r in rows:
  run=run+1 if ratio(r)==0 else 0;longest=max(longest,run)
 return {'frames':len(rows),'zeroEligibleFrames':sum(r['near24Eligible']==0 for r in rows),'aggregateNearCoverage':sum(r['near24Covered'] for r in rows)/eligible if eligible else None,'medianFrameCoverage':statistics.median(ratios) if ratios else None,'minimumFrameCoverage':min(ratios) if ratios else None,'zeroCoverageFrames':sum(x==0 for x in ratios),'longestZeroRun':longest}
segments={}
for seg in sorted({r['segment'] for r in b.values()}):
 indices=[i for i in sorted(b) if b[i]['segment']==seg];drops=[];changes=[]
 for i in indices:
  if any(r['sample']==i for r in camera_mismatches):continue
  br,cr=ratio(b[i]),ratio(c[i])
  if br is not None and cr is not None:changes.append(cr-br)
  if br is not None and cr is not None and cr<br-.02:drops.append({'sample':i,'before':br,'after':cr,'difference':cr-br})
 segments[seg]={'before':stats([b[i] for i in indices]),'after':stats([c[i] for i in indices]),'worstMatchedFrameCoverageChange':min(changes) if changes else None,'matchedFramesWithLowerCoverage':sum(d<0 for d in changes),'framesDroppingMoreThanTwoPercentagePoints':sorted(drops,key=lambda r:r['difference'])}
out={'method':'Every frame uses actual resolved visible depth unprojected through its jittered camera. Near surface eligibility is within24m horizontally and fixed GI vertical range. Coverage is inside the complete active GPU field; exact GPU payloads checked separately. Frames with no eligible surface are reported, never counted as covered. No FPS claim.','fixtureSha256':before['fixtureSha256'],'beforeRevision':before['revision'],'afterRevision':after['revision'],'cameraMismatchesOutsideColdFlightAndReversal':camera_mismatches,'qualificationScope':'Cold flight80 and reversal16 frames have matched eye poses and fixture hashes. Jump/teleport safety-boundary camera differences are listed; those segments have raw statistics only, not matched visual qualification.','segments':segments}
Path(a.out).write_text(json.dumps(out,indent=2)+'\n')
for seg in ('coldFlight','warmReversal'):print(seg,json.dumps(segments[seg]))
