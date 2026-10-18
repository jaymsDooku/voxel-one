#!/usr/bin/env python3
"""Separate matched steady-resident check. Keep first-use capture costs in warmup.csv, not hidden."""
import argparse,subprocess,os,json,hashlib
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--display',required=True);a=p.parse_args();root=Path.cwd();route=(root/'deploy/StreamingRoute.java').read_text().replace('public class StreamingRoute','public class ResidentSteadyRoute')
route=route.replace('        return true;\n    }\n    static String segment', '''        if(!((RenderPipeline)renderer).reflectionProbeReady())return waiting("reflection first capture");
        if((int)field(field(renderer,"probes"),"face")>=0)return waiting("reflection capture in progress");
        return true;
    }
    static String segment''')
assert 'reflection first capture' in route
route=route.replace('video inactive','video inactive; completed reflection capture; steady-only12')
route=route.replace('    static Object activeVolume;', '    static long frameWall,frameCpu;static final java.lang.management.ThreadMXBean ownerCpu=java.lang.management.ManagementFactory.getThreadMXBean();\n    static Object activeVolume;')
route=route.replace('            public void beforeFrame(Main main)throws Exception{','            public void beforeFrame(Main main)throws Exception{\n                frameWall=System.nanoTime();frameCpu=ownerCpu.getCurrentThreadCpuTime();')
route=route.replace('                firstObserved=true;', '''                long wall=System.nanoTime()-frameWall,cpu=ownerCpu.getCurrentThreadCpuTime()-frameCpu;
                Path warm=out.resolve("warmup-frames.csv");if(first<0){boolean header=!Files.exists(warm);Object pipeline=get("rendering");Files.writeString(warm,(header?"frame,wall_ms,thread_cpu_ms,probe_elapsed_ms,probe_ready,probe_face\\n":"")+frame+","+wall/1e6+","+cpu/1e6+","+stageMillis(pipeline,"probeCpuNanos")+","+((RenderPipeline)pipeline).reflectionProbeReady()+","+field(field(pipeline,"probes"),"face")+"\\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}
                firstObserved=true;''')
path=root/'target/ResidentSteadyRoute.java';path.write_text(route)
manifest={'method':'12 steady resident samples after identical latest-field, sky and completed reflection-capture readiness in both phases. Full166-sample original-readiness trials remain published, including startup transitions. Every regular render-loop frame before profiling retains wall/thread CPU and probe elapsed time; total loading/resident preparation is also recorded in warmup.json. No recorder/JFR/pass scopes.','routeSha256':hashlib.sha256(route.encode()).hexdigest(),'engineManifest':'resident-complete-source.json'}
(root/'dashboard/evidence/resident-steady-method.json').write_text(json.dumps(manifest,indent=2)+'\n')
env=os.environ.copy();env.update(DISPLAY=a.display,LIBGL_ALWAYS_SOFTWARE='1',MESA_GL_VERSION_OVERRIDE='3.3',MESA_GLSL_VERSION_OVERRIDE='330')
for trial in range(1,4):
 for phase,jar in [('before','target/baseline-instrumented-classes:target/streaming-baseline-client.jar'),('after','target/streaming-after-client.jar')]:
  if (root/'target/streaming-matrix-stop').exists():raise SystemExit('Stopped at trial boundary')
  out=root/'target/streaming-route'/f'resident-steady-{phase}-{trial}';out.mkdir();home=out/'home';home.mkdir()
  revision='4f8a09a+timing-overlay' if phase=='before' else json.loads((root/'dashboard/evidence/resident-complete-source.json').read_text())['sourceSha256']
  command=['/usr/lib/jvm/jdk-21.0.5-oracle-x64/bin/java','-Xmx1536m','-Dvoxel.route.samples=12','-Djava.io.tmpdir='+str(root/'target/tmp'),'-Duser.home='+str(home),'-cp',jar,str(path),str(out),revision]
  with (out/'runtime-private.txt').open('w') as log:r=subprocess.run(command,env=env,stdout=log,stderr=log,timeout=900)
  print('Playtest: steady resident',phase,trial,'exit',r.returncode,flush=True)
  if r.returncode:raise SystemExit(r.returncode)
