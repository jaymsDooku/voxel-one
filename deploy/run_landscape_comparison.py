#!/usr/bin/env python3
"""Alternate original and updated source to reduce shared-host timing drift."""
import argparse,json,subprocess,sys
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--display',required=True);a=p.parse_args()
root=Path(__file__).resolve().parents[1]
for trial in range(1,4):
 for baseline,label in ((True,'matched-baseline'),(False,'matched-landscape')):
  command=[sys.executable,str(root/'deploy/run_landscape_bench.py'),'--display',a.display,'--label',label,'--runs','1','--start',str(trial)]
  if baseline:command.append('--baseline')
  subprocess.run(command,check=True,cwd=root)
results=[]
for trial in range(1,4):
 b=json.loads((root/f'dashboard/evidence/landscape-matched-baseline-{trial}.json').read_text())
 u=json.loads((root/f'dashboard/evidence/landscape-matched-landscape-{trial}.json').read_text())
 row={'trial':trial,'baseline':b,'landscape':u}
 for metric in ('medianMs','p95Ms'):row[metric+'ChangePercent']=(u[metric]/b[metric]-1)*100
 row['within10Percent']=all(u[metric]<=b[metric]*1.1 for metric in ('medianMs','p95Ms'))
 results.append(row)
report={'Playtest':'Linux assigned X11, llvmpipe software GL, OpenAL null output; 1100x720; sensitivity/motion 1; cyan; drift off; vsync off; 20 s per run, 5 s warm-up; original 24-second stereo fixture; baseline and landscape alternated three times. Browser playtesting does not apply to this native client.','baselineHead':json.loads((root/'dashboard/evidence/landscape-source-manifest.json').read_text())['baselineHead'],'trials':results,'allPairsWithin10Percent':all(r['within10Percent'] for r in results),'limitations':'Shared VPS CPU scheduling varies. Physical GPU/audio qualification remains pending. Heap/RSS samples are snapshots, not a soak or leak qualification.'}
(root/'dashboard/evidence/landscape-performance.json').write_text(json.dumps(report,indent=2)+'\n')
print('Matched performance budget:',report['allPairsWithin10Percent'])
if not report['allPairsWithin10Percent']:sys.exit(1)
