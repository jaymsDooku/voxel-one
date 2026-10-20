#!/usr/bin/env python3
"""Exact-source native qualification. No credentials, account profiles or raw logs are exported."""
import argparse,hashlib,json,os,subprocess,sys
import xml.etree.ElementTree as ET
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--display',required=True);p.add_argument('--expected-head',required=True);a=p.parse_args()
assert a.display and a.display==os.environ.get('DISPLAY')
root=Path(__file__).resolve().parents[1];os.chdir(root)
head=subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip()
if head!=a.expected_head:raise SystemExit('Exact-source checkout mismatch')
# Export fresh counts from this checkout; local evidence committed earlier is not a hosted result.
suites=[]
for name in ('MusicAnalysisTest','MusicLandscapeTest','MusicMp3Test','SandboxIdentityTest','ControlsTest'):
 report=ET.parse(root/('target/surefire-reports/TEST-dev.jayms.'+name+'.xml')).getroot()
 suites.append({'name':name,**{key:int(report.get(key,'0')) for key in ('tests','failures','errors','skipped')}})
(root/'dashboard/evidence/landscape-hosted-units.json').write_text(json.dumps({'sourceHead':head,'suites':suites},indent=2)+'\n')
if any(suite['failures'] or suite['errors'] for suite in suites):raise SystemExit('Exact-source unit qualification failed')
commands=[
 ['deploy/run_music_smoke.py','--display',a.display],
 ['deploy/check_music_media.py','--prefix','landscape-live'],
 ['deploy/run_music_mp3_smoke.py','--display',a.display,'--evidence-prefix','landscape-mp3'],
 ['deploy/run_music_regressions.py','--display',a.display,'--evidence-prefix','landscape-regression'],
 ['deploy/run_landscape_comparison.py','--display',a.display],
 ['deploy/run_landscape_soak.py','--display',a.display],
]
# A receipt is written only once every command succeeds. An uploaded old/local report is not a hosted pass.
receipt=root/'dashboard/evidence/landscape-hosted-receipt.json';receipt.unlink(missing_ok=True)
results=[]
for command in commands:
 result=subprocess.run([sys.executable,*command])
 results.append({'command':command,'exitCode':result.returncode})
(root/'dashboard/evidence/landscape-hosted-status.json').write_text(json.dumps({'head':head,'results':results},indent=2)+'\n')
if any(result['exitCode'] for result in results):raise SystemExit('Native qualification failed; no passed receipt. Independent checks were still attempted.')
files={p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in (root/'dashboard/evidence').glob('landscape-*') if p.is_file() and p!=receipt}
receipt.write_text(json.dumps({'head':head,'commands':commands,'allCommandsPassed':True,'artifactsSha256':files,'pendingQualification':['Physical GPU and audible speaker qualification','Manual inspection of hosted screenshots/video and soak resource trends']},indent=2)+'\n')
print('PASS: exact-source hosted native commands; manual media/resource review and physical qualification remain separate')
