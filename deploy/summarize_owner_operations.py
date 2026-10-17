#!/usr/bin/env python3
"""Correlate opt-in operation timings with sanitized JFR spans; never export raw JVM events."""
import argparse,csv,json,math,collections
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--folder',required=True);p.add_argument('--prefix',required=True);p.add_argument('--output-dir',default='dashboard/evidence');a=p.parse_args();folder=Path(a.folder);out=Path(a.output_dir);out.mkdir(parents=True,exist_ok=True)
rows=list(csv.DictReader((folder/'owner-operations.csv').open()));rows=[r for r in rows if int(r['sample'])>=0]
events=[];pauses=[];safepoints=[];begin={};finish={}
f=folder/'owner-events.csv'
if f.exists():
 anchors=[json.loads((folder/n).read_text()) for n in ('clock-start.json','clock-end.json')];anchors.sort(key=lambda x:x['epochNs'])
 def mono(epoch):
  lo,hi=anchors;t=max(0,min(1,(epoch-lo['epochNs'])/max(1,hi['epochNs']-lo['epochNs'])));offset=(lo['monoNs']-lo['epochNs'])*(1-t)+(hi['monoNs']-hi['epochNs'])*t;return int(epoch+offset)
 for e in csv.DictReader(f.open()):
  start=mono(int(e['start_epoch_ns']));end=start+int(e['duration_ns']);e['start']=start;e['end']=end;events.append(e)
  if e['event']=='jdk.GCPhasePause':pauses.append((start,end))
  if e['event']=='jdk.SafepointBegin':begin[e['safepoint_id']]=start
  if e['event']=='jdk.SafepointEnd':finish[e['safepoint_id']]=end
 for key,start in begin.items():
  if key in finish:safepoints.append((start,finish[key]))
def overlap(spans,start,end):
 clipped=sorted((max(start,x),min(end,y)) for x,y in spans if y>start and x<end);n=0;last=start
 for x,y in clipped:n+=max(0,y-max(last,x));last=max(last,y)
 return n/1e6
def stats(v):
 v=sorted(v);return {k:v[max(0,math.ceil(q*len(v))-1)] if v else None for k,q in [('p50',.5),('p95',.95),('p99',.99),('worst',1)]}
groups=collections.defaultdict(list)
for r in rows:groups[r['operation']].append(r)
summary={name:{'calls':len(g),'wallMs':stats([int(r['wall_ns'])/1e6 for r in g]),'threadCpuMs':stats([int(r['thread_cpu_ns'])/1e6 for r in g]),'maximumAllocatedBytes':max(int(r['allocated_bytes']) for r in g),'maximumUnits':max(int(r['units']) for r in g)} for name,g in groups.items()}
overruns=[]
for r in rows:
 if int(r['wall_ns'])<=2_000_000:continue
 start=int(r['start_ns']);end=start+int(r['wall_ns']);children=[c for c in rows if c['sample']==r['sample'] and int(c['depth'])>int(r['depth']) and int(c['start_ns'])>=start and int(c['start_ns'])+int(c['wall_ns'])<=end]
 sites=collections.Counter(e['site'] for e in events if e['thread']=='main' and e['event'] in ('jdk.ExecutionSample','jdk.NativeMethodSample') and start<=e['start']<=end and e['site'])
 overruns.append({'sample':int(r['sample']),'operation':r['operation'],'wallMs':int(r['wall_ns'])/1e6,'threadCpuMs':int(r['thread_cpu_ns'])/1e6,'allocatedBytes':int(r['allocated_bytes']),'gcCounterDeltaMs':int(r['gc_ms']),'namespaceCpuThrottleCount':int(r.get('cpu_throttled_count','-1')),'namespaceCpuThrottleMs':int(r.get('cpu_throttled_us','-1'))/1000 if int(r.get('cpu_throttled_us','-1'))>=0 else None,'verifiedSchedulerWaitMs':max(-1,int(r.get('scheduler_wait_ns','-1')))/1e6 if int(r.get('scheduler_wait_ns','-1'))>=0 else None,'verifiedGcPauseOverlapMs':overlap(pauses,start,end),'verifiedSafepointOverlapMs':overlap(safepoints,start,end),'ownerParkOverlapMs':overlap([(e['start'],e['end']) for e in events if e['event']=='jdk.ThreadPark' and e['thread']=='main'],start,end),'ownerMonitorOverlapMs':overlap([(e['start'],e['end']) for e in events if e['event']=='jdk.JavaMonitorEnter' and e['thread']=='main'],start,end),'largestChildByWall':max(children,key=lambda c:int(c['wall_ns']))['operation'] if children else None,'sampledOwnerSites':sites.most_common(3)})
alloc=collections.Counter()
for e in events:
 if e['event']=='jdk.ObjectAllocationSample' and e['site']:alloc[(e['thread'],e['site'])]+=int(e['allocation_bytes'])
report={'method':'Opt-in primitive owner spans. Timing counter reads add overhead; CPU/wall windows differ slightly in the first diagnostic. GC MXBean counter deltas alone do not prove stop-the-world overlap. Only JFR GCPhasePause and paired SafepointBegin/End provide overlap attribution; raw JFR stays under target. Monotonic/epoch clock anchors interpolate host clock offset. No recorded media or frame-performance claim.','folderLabel':folder.name,'jfrAvailable':bool(events),'operationStatistics':summary,'overruns':overruns,'sampledWorkerAllocationWeights':[{'thread':k[0],'site':k[1],'estimatedWeightBytes':v} for k,v in alloc.most_common(20)]}
(out/(a.prefix+'.json')).write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps({'operations':summary,'overruns':len(overruns),'jfr':bool(events)},indent=2))
