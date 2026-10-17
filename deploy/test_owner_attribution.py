#!/usr/bin/env python3
"""A reversed-event fixture guards JFR chunk ordering and avoids false GC attribution."""
import csv,json,subprocess,tempfile,unittest
from pathlib import Path
class AttributionTest(unittest.TestCase):
 def test_out_of_order_safepoints_and_counter_only_gc(self):
  root=Path(__file__).resolve().parents[1]
  with tempfile.TemporaryDirectory(dir=root/'target/tmp') as temp:
   p=Path(temp)
   for name in ('clock-start.json','clock-end.json'):(p/name).write_text(json.dumps({'monoNs':0,'epochNs':0,'uncertaintyNs':0}))
   (p/'owner-operations.csv').write_text('sample,operation,start_ns,wall_ns,thread_cpu_ns,allocated_bytes,gc_ms,gc_count,units,depth,dropped\n32,mesh.update,10000000,10000000,100000,0,99,1,0,0,0\n33,gi.update,30000000,5000000,100000,0,99,1,0,0,0\n')
   # End appears before Begin, as can happen in real JFR thread/chunk buffers.
   (p/'owner-events.csv').write_text('event,start_epoch_ns,duration_ns,thread,thread_id,safepoint_id,allocation_bytes,site\njdk.SafepointEnd,16000000,0,jvm,0,7,0,\njdk.GCPhasePause,13000000,2000000,jvm,0,0,0,\njdk.SafepointBegin,12000000,0,jvm,0,7,0,\n')
   subprocess.run(['python3',str(root/'deploy/summarize_owner_operations.py'),'--folder',str(p),'--prefix','test','--output-dir',str(p)],check=True,stdout=subprocess.DEVNULL)
   report=json.loads((p/'test.json').read_text());first,second=report['overruns']
   self.assertEqual(2,first['verifiedGcPauseOverlapMs']);self.assertEqual(4,first['verifiedSafepointOverlapMs'])
   self.assertEqual(0,second['verifiedGcPauseOverlapMs']);self.assertEqual(0,second['verifiedSafepointOverlapMs'])
   self.assertEqual(99,second['gcCounterDeltaMs'])
if __name__=='__main__':unittest.main()
