#!/usr/bin/env python3
"""Summarize measured startup and horizontal GI coverage; depth visuals qualify visible surfaces separately."""
import argparse,csv,json,math,shutil
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--prefix',required=True);p.add_argument('--trials',default='1,2,3');a=p.parse_args()
r=Path(__file__).resolve().parents[1];e=r/'dashboard/evidence';report={'method':'Horizontal3x3 grid at24m spacing. Center checks x/z bounds only; flight eye is above GI height. These are not screen-space coverage claims. Separate resolved-depth visual runs measure visible near surfaces. Source age is since immutable capture; static current data need not be rebaked solely due to age. Warmup includes startup/resident preparation.','trials':{}}
def stats(values):
 v=sorted(values)
 return {k:(v[max(0,math.ceil(q*len(v))-1)] if v else None) for k,q in [('p50',.5),('p95',.95),('p99',.99),('worst',1)]}
segments=[('resident',0,12),('coldEntry',12,16),('coldWalk',16,32),('coldFlight',32,112),('warmReversal',112,128),('teleport',128,134),('catchup',134,158),('transitionTail',158,166)]
for trial in map(int,a.trials.split(',')):
 for phase in ['before','after']:
  label=f'{a.prefix}-{phase}-{trial}';folder=r/'target/streaming-route'/label
  rows=list(csv.DictReader((folder/'coverage.csv').open()));samples=[x for x in rows if 0<=int(x['sample'])<166];assert len(samples)==166,label
  fenced=any(float(x['source_age_ms'])>=0 for x in rows)
  data={'warmup':json.loads((folder/'warmup.json').read_text()),'geometryFenceAvailable':fenced,'segments':{}}
  first=next((x for x in rows if int(x['horizontal_coverage_9'])==9 and int(x.get('sky_coverage_9','0'))==9 and (not fenced or x['current_geometry']=='true')),None)
  data['firstRenderedNearCoverageWallMs']=float(first['wall_ms']) if first is not None and 'wall_ms' in first else None
  data['initialLighting']=json.loads((folder/'initial-lighting.json').read_text()) if (folder/'initial-lighting.json').exists() else None
  data['readinessWaitsCsv']=(folder/'readiness-waits.csv').read_text() if (folder/'readiness-waits.csv').exists() else None
  for name,lo,hi in segments:
   group=[x for x in samples if lo<=int(x['sample'])<hi]
   center=lambda x:x['has_gi']=='true' and float(x['gi_x'])<=float(x['x'])<float(x['gi_x'])+96 and float(x['gi_z'])<=8<float(x['gi_z'])+96
   usable=[x for x in group if center(x) and x['current_geometry']=='true']
   data['segments'][name]={'frames':len(group),'centerHorizontalCovered':sum(center(x) for x in group),'currentCenterCovered':len(usable) if fenced else None,'nineHorizontalCovered':sum(int(x['horizontal_coverage_9'])==9 for x in group),'knownSkyAtRouteColumn':sum(x['sky_known']=='true' for x in group),'sourceAgeMsCurrentCenter':stats([float(x['source_age_ms']) for x in usable if float(x['source_age_ms'])>=0]),'pendingAgeMs':stats([float(x['pending_age_ms']) for x in group if float(x['pending_age_ms'])>=0])}
  for name,lo,hi in segments:
   group=[x for x in samples if lo<=int(x['sample'])<hi];part=data['segments'][name]
   part['skyGpuPendingFrames']=sum(x.get('sky_gpu_pending')=='true' for x in group)
   part['skyGpuPendingAgeMs']=stats([float(x['sky_gpu_age_ms']) for x in group if x.get('sky_gpu_pending')=='true' and float(x['sky_gpu_age_ms'])>=0])
   part['skyAtlasPublicationAgeMs']=stats([float(x['sky_source_age_ms']) for x in group if float(x.get('sky_source_age_ms','-1'))>=0])
   part['skyUploadedBytes']=sum(int(x.get('sky_gpu_bytes',0)) for x in group)
   part['skyOwnerComponentMs']=stats([float(x['sky_upload_owner_ms']) for x in group if float(x.get('sky_upload_owner_ms','-1'))>=0])
  workers={x['worker_id']:x for x in rows if float(x.get('worker_id','-1'))>0}
  data['uniqueWorkers']=[{k:x[k] for k in ['worker_id','worker_wall_nanos','worker_cpu_nanos']} for x in workers.values()]
  data['lastCounters']={k:rows[-1][k] for k in ['submitted_bakes','completed_bakes','rejected_bakes','obsolete_volumes']} if fenced else None
  report['trials'][label]=data
  shutil.copyfile(folder/'coverage.csv',e/f'{label}-coverage.csv')
(e/f'{a.prefix}-coverage.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps({label:{'warmup':v['warmup'],'coldFlight':v['segments']['coldFlight']} for label,v in report['trials'].items()},indent=2))
