#!/usr/bin/env python3
"""Finish uncompleted test classes through bounded Maven runs; record actual results."""
import argparse,json,os,shlex,subprocess,time,xml.etree.ElementTree as E
from pathlib import Path
parser=argparse.ArgumentParser();parser.add_argument('--group',type=int,required=True,choices=(0,1,2));args=parser.parse_args()
root=Path(__file__).resolve().parents[1];os.chdir(root)
plan_file=root/'target/road-shipping-final-completion.json'
if not plan_file.exists():plan_file=root/'dashboard/evidence/road-shipping-final-test-plan.json'
plan=json.loads(plan_file.read_text());names=plan['groups'][args.group]
cmd=['/tmp/apache-maven-3.9.11/bin/mvn','--batch-mode','-q','-Dmaven.repo.local=/tmp/voxel-m2','-Dlwjgl.natives=natives-linux','-DargLine=-Djava.io.tmpdir='+str(root/'target/tmp'),'-Dtest='+','.join(names),'test']
env=os.environ.copy();env['MAVEN_OPTS']='-Djava.io.tmpdir='+str(root/'target/tmp');started=time.time()
with (root/'target'/f'road-shipping-final-group-{args.group}.txt').open('w') as out:result=subprocess.run(cmd,env=env,stdout=out,stderr=out)
print('Completion group',args.group,'exit:',result.returncode)
if result.returncode:raise SystemExit(result.returncode)
rows=[]
for name in names:
 matches=list((root/'target/surefire-reports').glob(f'TEST-*.{name}.xml'))
 if len(matches)!=1:raise AssertionError('Ambiguous suite '+name)
 p=matches[0]
 if p.stat().st_mtime<started:raise AssertionError('Missing fresh suite '+name)
 r=E.parse(p).getroot();row={k:r.get(k) for k in ('name','tests','errors','failures','skipped','time')}
 if int(row['errors']) or int(row['failures']):raise AssertionError('Failed suite '+name)
 rows.append(row)
(root/'dashboard/evidence'/f'road-shipping-final-group-{args.group}.json').write_text(json.dumps({'exitCode':0,'command':'MAVEN_OPTS='+shlex.quote(env['MAVEN_OPTS'])+' '+shlex.join(cmd),'suites':rows},indent=2)+'\n')
print('PASS:',sum(int(x['tests']) for x in rows),'tests in completion group',args.group)
