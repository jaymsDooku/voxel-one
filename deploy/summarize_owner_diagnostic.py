#!/usr/bin/env python3
"""Sanitize a completed owner-body diagnostic; raw JFR/runtime logs stay in target.
Usage: summarize_owner_diagnostic.py RUN_DIRECTORY OUTPUT_JSON
"""
import csv,json,datetime,re,sys,math
from pathlib import Path
out=Path(sys.argv[1]);clock=json.loads((out/'clock-start.json').read_text())
def stamp(s):return datetime.datetime.fromisoformat(s.replace('Z','+00:00')).timestamp()
def duration(s):
 if isinstance(s,(int,float)):return float(s)
 m=re.fullmatch(r'PT(?:(\d+(?:\.\d+)?)H)?(?:(\d+(?:\.\d+)?)M)?(?:(\d+(?:\.\d+)?)S)?',s)
 return sum(float(v or 0)*w for v,w in zip(m.groups(),[3600,60,1])) if m else 0
baseStart=stamp(clock['utc'])-clock['nano']/1e9
endClock=json.loads((out/'clock-end.json').read_text());base=stamp(endClock['utc'])-endClock['nano']/1e9
raw=json.loads((out/'selected-events-private.json').read_text())['recording']['events'];events=[]
for event in raw:
 v=event['values'];typ=event['type'];thread=v.get('eventThread') or {};name=thread.get('javaName','')
 if typ in ['jdk.ThreadPark','jdk.JavaMonitorEnter','jdk.JavaMonitorWait'] and name!='main':continue
 if typ not in ['jdk.ThreadPark','jdk.JavaMonitorEnter','jdk.JavaMonitorWait','jdk.SafepointBegin','jdk.SafepointEnd','jdk.GCPhasePause']:continue
 start=stamp(v['startTime']);end=start+duration(v.get('duration','PT0S'))
 monitor=v.get('monitorClass') or v.get('parkedClass') or {};frames=(v.get('stackTrace') or {}).get('frames',[]);method=(frames[0].get('method') or {}) if frames else {};owner=method.get('type') or {};detail={'monitor':monitor.get('name'),'topMethod':owner.get('name','')+'.'+method.get('name','') if method else None};events.append((start,end,typ,detail))
rows=[]
records=list(csv.DictReader((out/'owner-operations.csv').open()))
for r in records:
 if int(r['wall_ns'])<2_000_000:continue
 start=base+int(r['start_ns'])/1e9;end=start+int(r['wall_ns'])/1e9
 overlaps=[{'event':t,'overlapMs':round(max(0,min(end,b)-max(start,a))*1000,6),**detail} for a,b,t,detail in events if a<end and b>start]
 rows.append({'sample':int(r['sample']),'operation':r['operation'],'wallMs':int(r['wall_ns'])/1e6,'threadCpuMs':int(r['thread_cpu_ns'])/1e6,'units':int(r['units']),'allocatedBytes':int(r['allocated_bytes']) if 'allocated_bytes' in r else None,'schedulerWaitMs':int(r.get('scheduler_wait_ns',-1))/1e6 if int(r.get('scheduler_wait_ns',-1))>=0 else None,'cpuStealTicks':int(r.get('cpu_steal_ticks',-1)),'throttledUs':int(r.get('cpu_throttled_us',-1)),'gcMs':int(r['gc_ms']) if 'gc_ms' in r else None,'minorFaults':int(r['minor_faults']) if int(r.get('minor_faults',-1))>=0 else None,'majorFaults':int(r['major_faults']) if int(r.get('major_faults',-1))>=0 else None,'voluntarySwitches':int(r['voluntary_switches']) if int(r.get('voluntary_switches',-1))>=0 else None,'involuntarySwitches':int(r['involuntary_switches']) if int(r.get('involuntary_switches',-1))>=0 else None,'events':overlaps})
report={'scope':'Diagnostic-only target overlay; unchanged candidate application JAR; probes arm after graphics warmup. Thread CPU/body elapsed clocks exclude their own begin/end accounting I/O. Root scopes include nested body accounting. Host counters bracket body plus accounting probes; counter gaps alone do not prove a driver cause. JFR whitelist excludes environment, system properties, process lists and heap content. No recorder. This instrumented run is not a matched performance trial.','clockUncertaintyNs':endClock['uncertaintyNs'],'startEndCalibrationDriftNs':round((base-baseStart)*1e9),'outliersAbove2ms':rows,'mainThreadParkEvents':sum(t=='jdk.ThreadPark' for _,_,t,_ in events)}
operations={}
for record in records:operations.setdefault(record['operation'],[]).append(record)
def p99(values):return sorted(values)[math.ceil(len(values)*.99)-1]
report['operationTable']=[]
for operation in ['terrain.update','mesh.update','mesh.allocate','mesh.snapshot','mesh.upload','sky.update','sky.pack','sky.publish','sky.capture','sky.validate','gi.update','gi.capture','lighting.update','gl.pixels3D']:
 values=operations.get(operation,[])
 if not values:continue
 wall=[int(r['wall_ns'])/1e6 for r in values];cpu=[int(r['thread_cpu_ns'])/1e6 for r in values]
 report['operationTable'].append({'operation':operation,'observations':len(values),'wallP99Ms':p99(wall),'wallMaxMs':max(wall),'threadCpuP99Ms':p99(cpu),'threadCpuMaxMs':max(cpu),'maxReportedUnits':max(int(r['units']) for r in values)})
report['scopeManifest']=json.loads((out/'diagnostic-scope.json').read_text())
report['limits']=['JFR/trace observer changes timings; separate unrecorded matched trials establish performance.','Some low-CPU wall gaps have no matching Java wait or GC event and remain unattributed; no physical-driver cause is claimed.','Host counters bracket extra probe I/O and can exceed the timed body interval.','lighting.pump/mesh.upload units are their allowed byte budgets, not transferred bytes; mesh.allocate and gl.pixels3D report actual store/copy bytes.']
report['counterMode']='Linux64 own-thread getrusage snapshots; procfs/perf-bean probes disabled; allocation and GC-count columns unmeasured; JFR provides GC overlaps' if records and 'minor_faults' in records[0] else 'Procfs/perf-bean snapshots'
first=min(int(r['start_ns']) for r in records);last=max(int(r['start_ns'])+int(r['wall_ns']) for r in records)
with Path(sys.argv[2]).with_name(Path(sys.argv[2]).stem+'-events.csv').open('w',newline='') as stream:
 writer=csv.writer(stream);writer.writerow(['event','start_ns','duration_ns','monitor_class','top_method'])
 for start,end,kind,detail in events:
  begin=round((start-base)*1e9);finish=round((end-base)*1e9)
  if begin<last and finish>first:writer.writerow([kind,begin,finish-begin,detail['monitor'],detail['topMethod']])
Path(sys.argv[2]).write_text(json.dumps(report,indent=2)+'\n')
print('Body operation outliers',len(rows),'main parks',report['mainThreadParkEvents'])
for r in sorted(rows,key=lambda r:r['wallMs'],reverse=True)[:12]:print(r['sample'],r['operation'],round(r['wallMs'],3),round(r['threadCpuMs'],3),r['schedulerWaitMs'],r['cpuStealTicks'],r['events'])
