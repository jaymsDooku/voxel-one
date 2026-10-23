#!/usr/bin/env python3
"""Exercise real Main dispatch, then existing Sandbox and City workflows on the assigned display."""
import argparse, os, subprocess, shutil, json, hashlib
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--display',required=True);p.add_argument('--evidence-prefix',default='music-regression');a=p.parse_args()
if not a.display or a.display!=os.environ.get('DISPLAY'):raise SystemExit('Inherited assigned DISPLAY required')
root=Path(__file__).resolve().parents[1];os.chdir(root)
evidence=root/'dashboard/evidence'
for suffix in ('music-catalog.txt','voxel-sandbox-smoke-result.txt','voxel-city-entry-result.txt','status.json'):
    (evidence/(a.evidence_prefix+'-'+suffix)).unlink(missing_ok=True)
status={'complete':False,'sourceHead':subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip(),'results':[]}
def record(label,code,artifacts=None):
    status['results'].append({'scenario':label,'exitCode':code,'artifactsSha256':artifacts or {}})
    (evidence/(a.evidence_prefix+'-status.json')).write_text(json.dumps(status,indent=2)+'\n')

for name in ('music-regressions','music-regression-home','music-regression-classes','tmp'):(root/'target'/name).mkdir(parents=True,exist_ok=True)
cp=os.pathsep.join([str(root/'target/classes'),str(root/'target/music-regression-classes'),*[str(p) for p in (root/'target/maven-cache').rglob('*.jar') if 'natives-windows' not in p.name]])
with (root/'target/music-regressions/private-compile.txt').open('w') as compile_output:
    compiled=subprocess.run(['javac','-sourcepath','','-cp',cp,'-d','target/music-regression-classes','deploy/MusicCatalogSmoke.java','deploy/SandboxPlaytest.java'],stdout=compile_output,stderr=compile_output)
record('compile',compiled.returncode)
if compiled.returncode:raise SystemExit('Regression harness compilation failed; no pass report exported')
env=os.environ.copy();env.update(DISPLAY=a.display,LIBGL_ALWAYS_SOFTWARE='1',MESA_SHADER_CACHE_DISABLE='true',ALSOFT_DRIVERS='null',TMPDIR=str(root/'target/tmp'),XDG_CACHE_HOME=str(root/'target/music-regression-home/cache'));env.pop('WAYLAND_DISPLAY',None)
shutil.rmtree(root/'target/sandbox-profile',ignore_errors=True)
for label,main,extra in [('catalog','MusicCatalogSmoke',[]),('sandbox','SandboxPlaytest',['sandbox']),('city','SandboxPlaytest',['city'])]:
    output=root/'target/music-regressions'/label;shutil.rmtree(output,ignore_errors=True);output.mkdir(exist_ok=True)
    try:
      with (output/'private-runtime.txt').open('w') as runtime:
        result=subprocess.run(['java','-Xmx768m','-Djava.io.tmpdir='+str(root/'target/tmp'),'-Duser.home='+str(root/'target/music-regression-home'),'-cp',cp,main,str(output),*extra],env=env,stdout=runtime,stderr=runtime,timeout=420)
    except subprocess.TimeoutExpired:
        record(label,124)
        raise SystemExit(label+' native regression timed out; no pass report exported')
    artifacts={}
    for name in ('music-catalog.txt','voxel-sandbox-smoke-result.txt','voxel-city-entry-result.txt'):
        report=output/name
        if report.exists():
            destination=evidence/(a.evidence_prefix+'-'+name)
            shutil.copyfile(report,destination)
            artifacts[destination.name]=hashlib.sha256(destination.read_bytes()).hexdigest()
    record(label,result.returncode,artifacts)
    print(label,'native regression exit:',result.returncode,flush=True)
    if result.returncode:
        for name in ('catalog-failure.txt','voxel-sandbox-smoke-result.txt','voxel-city-entry-result.txt'):
            if (output/name).exists():print((output/name).read_text())
        raise SystemExit(result.returncode)
    if not artifacts:raise SystemExit(label+' result report missing')
status['complete']=True
(evidence/(a.evidence_prefix+'-status.json')).write_text(json.dumps(status,indent=2)+'\n')
print('PASS: actual Main music dispatch/return/relaunch; existing Sandbox and City integration workflows')
