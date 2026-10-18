#!/usr/bin/env python3
"""Prepare a target-only timing overlay. Normal qualification JAR/source stays unchanged.
The overlay arms after graphics warmup and excludes accounting-probe I/O from body clocks.
No environment, system-property, process-list or heap-content JFR events are enabled.
"""
from pathlib import Path
import argparse,xml.etree.ElementTree as ET
p=argparse.ArgumentParser();p.add_argument('--output',required=True);p.add_argument('--native-counters',action='store_true');a=p.parse_args();out=Path(a.output);src=out/'src';(src/'dev/jayms').mkdir(parents=True,exist_ok=True)
s=Path('src/main/java/dev/jayms/OwnerTrace.java').read_text()
s=s.replace('int depth,count,dropped;','int depth,count,dropped;final boolean[] host=new boolean[32];')
s=s.replace('    private static long cpu(){','    private static volatile java.util.function.BooleanSupplier gate=()->false;\n    public static void setDiagnosticGate(java.util.function.BooleanSupplier value){gate=value;}\n    private static long cpu(){')
i=s.index('    public static int begin(');j=s.index('    /** Own-thread kernel accounting',i)
s=s[:i]+'''    public static int begin(String operation){
        if(!ENABLED||!gate.getAsBoolean())return -1;State s=STATE.get();if(s.depth==32)throw new IllegalStateException("Trace nesting");int i=s.depth++;
        s.stack[i]=operation;long[] a=s.starts[i];
        boolean host=HOST&&(operation.equals("sky.update")||operation.equals("lighting.update")||operation.equals("mesh.update"));s.host[i]=host;
        if(host){a[5]=schedulerWait();throttle(a,6);processorSteal(a,8);}else java.util.Arrays.fill(a,5,10,-1);
        a[2]=allocation();a[3]=gc(true);a[4]=gc(false);a[0]=System.nanoTime();a[1]=cpu();return i;
    }
'''+s[j:]
i=s.index('    public static void end(');j=s.index('    /** Observer calls',i)
s=s[:i]+'''    public static void end(int token,long units){
        if(token<0)return;State s=STATE.get();if(token!=--s.depth)throw new IllegalStateException("Trace scope order");
        long cpu=cpu(),now=System.nanoTime();
        if(s.count==s.rows.length){s.dropped++;return;}
        long allocated=allocation(),gcTime=gc(true),gcCount=gc(false),wait=-1;long[] r=s.rows[s.count];
        if(s.host[token]){wait=schedulerWait();throttle(r,9);processorSteal(r,11);}else java.util.Arrays.fill(r,9,13,-1);
        long[] a=s.starts[token];s.names[s.count++]=s.stack[token];
        r[0]=a[0];r[1]=now-a[0];r[2]=a[1]<0?-1:cpu-a[1];r[3]=a[2]<0?-1:allocated-a[2];r[4]=gcTime-a[3];r[5]=gcCount-a[4];r[6]=units;r[7]=token;r[8]=wait<0||a[5]<0?-1:wait-a[5];r[9]=r[9]<0||a[6]<0?-1:r[9]-a[6];r[10]=r[10]<0||a[7]<0?-1:r[10]-a[7];long processor=r[11],steal=r[12];r[11]=processor<0||processor!=a[8]||steal<a[9]?-1:steal-a[9];r[12]=processor;
    }

'''+s[j:]
s=s.replace('if(!ENABLED)return;State s=STATE.get();','if(!ENABLED||!gate.getAsBoolean())return;State s=STATE.get();')
if a.native_counters:
 s=s.replace('operation.equals("mesh.update"))','operation.equals("mesh.update")||operation.equals("gi.update")||operation.equals("render.prepare")||operation.equals("terrain.update"))')
 s=s.replace('a[5]=schedulerWait();throttle(a,6);processorSteal(a,8);','OwnerNativeCounters.read(a,5);a[9]=0;')
 s=s.replace('else java.util.Arrays.fill(r,9,13,-1);','else java.util.Arrays.fill(r,8,13,-1);')
 s=s.replace('a[2]=allocation();a[3]=gc(true);a[4]=gc(false);','a[2]=a[3]=a[4]=0;')
 s=s.replace('long allocated=allocation(),gcTime=gc(true),gcCount=gc(false),wait=-1;','long allocated=0,gcTime=0,gcCount=0,wait=-1;')
 s=s.replace('wait=schedulerWait();throttle(r,9);processorSteal(r,11);','OwnerNativeCounters.read(r,8);r[12]=0;')
 begin=s.index('r[8]=wait<0');end=s.index('\n    }',begin)
 s=s[:begin]+'for(int counter=0;counter<4;counter++)r[8+counter]=r[8+counter]<0||a[5+counter]<0?-1:r[8+counter]-a[5+counter];'+s[end:]
 s=s.replace('allocated_bytes,gc_ms,gc_count','allocation_unmeasured,gc_unmeasured,gc_count_unmeasured').replace('scheduler_wait_ns,cpu_throttled_count,cpu_throttled_us,cpu_steal_ticks,processor','minor_faults,major_faults,voluntary_switches,involuntary_switches,counter_reserved')
 (src/'dev/jayms/OwnerNativeCounters.java').write_text(Path('deploy/OwnerNativeCounters.java').read_text())
 world=Path('src/main/java/dev/jayms/World.java').read_text()
 start=world.index(' throws Exception {',world.index('    public void streamAsync('))+len(' throws Exception {')
 end=world.index('    private void requestNear(',start)
 body=world[start:end];close=body.rfind('    }')
 world=world[:start]+'\n        int diagnosticTrace=OwnerTrace.begin("terrain.update");try {'+body[:close]+'        }finally{OwnerTrace.end(diagnosticTrace,streamAttached); }\n    }\n'+world[end:]
 (src/'dev/jayms/World.java').write_text(world)

(src/'dev/jayms/OwnerTrace.java').write_text(s)
(src/'OwnerDiagnostic.java').write_text('''import java.nio.file.*;
public class OwnerDiagnostic {
 static void clock(Path out,String name)throws Exception{long before=System.nanoTime();var utc=java.time.Instant.now();long after=System.nanoTime();Files.writeString(out.resolve(name),"{\\"nano\\":"+(before+(after-before)/2)+",\\"utc\\":\\""+utc+"\\",\\"uncertaintyNs\\":"+(after-before)+"}\\n");}
 public static void main(String[] args)throws Exception{Path out=Path.of(args[0]);Files.createDirectories(out);clock(out,"clock-start.json");dev.jayms.OwnerTrace.setDiagnosticGate(()->StreamingRoute.first>=0&&StreamingRoute.firstObserved);try{StreamingRoute.main(args);}finally{clock(out,"clock-end.json");}}
}
''')
whitelist={'jdk.ThreadPark','jdk.JavaMonitorEnter','jdk.JavaMonitorWait','jdk.SafepointBegin','jdk.SafepointEnd','jdk.GCPhasePause','jdk.CPULoad','jdk.ThreadCPULoad','jdk.ExecutionSample','jdk.NativeMethodSample'}
if a.native_counters:whitelist-={'jdk.CPULoad','jdk.ThreadCPULoad','jdk.ExecutionSample','jdk.NativeMethodSample'}
tree=ET.parse('target/owner-attribution.jfc');root=tree.getroot()
for event in root.findall('event'):
 settings={v.get('name'):v for v in event.findall('setting')}
 enabled=settings.get('enabled')
 if enabled is None:enabled=ET.SubElement(event,'setting',name='enabled')
 enabled.attrib.pop('control',None);enabled.text='true' if event.get('name') in whitelist else 'false'
 if event.get('name') in whitelist:
  for name,value in [('stackTrace','true'),('threshold','1 ms')]:
   setting=settings.get(name)
   if setting is None:setting=ET.SubElement(event,'setting',name=name)
   setting.attrib.pop('control',None);setting.text=value
root.set('label','Owner body timing: selected events only');tree.write(out/'owner-safe.jfc',encoding='unicode')
print('Prepared diagnostic-only overlay and JFR whitelist:',out)
