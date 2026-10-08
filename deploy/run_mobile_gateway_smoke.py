#!/usr/bin/env python3
"""Exercise the actual mobile gateway against isolated Java game servers; no private profiles."""
import argparse
import json
from pathlib import Path
import subprocess
import tempfile
import time
import urllib.error
import urllib.request

parser=argparse.ArgumentParser();parser.add_argument('--report',required=True);args=parser.parse_args()
root=Path(__file__).resolve().parents[1]
with tempfile.TemporaryDirectory(prefix='mobile-smoke-',dir=root/'target') as temp:
    temp=Path(temp);ready=temp/'ready.json'
    process=subprocess.Popen(['java','-cp',str(root/'target/classes'),'dev.jayms.net.mobile.MobileFixtureHost',str(temp/'profile'),str(ready)],cwd=root,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
    tests=[]
    try:
        for _ in range(300):
            if ready.exists():break
            if process.poll() is not None:raise RuntimeError('synthetic game host exited')
            time.sleep(.1)
        base=json.loads(ready.read_text())['gateway']
        def post(route,data,token=None):
            request=urllib.request.Request(base+'/mobile/v1/'+route,data=json.dumps(data).encode(),headers={'Content-Type':'application/json'},method='POST')
            if token:request.add_header('Authorization','Bearer '+token)
            try:
                with urllib.request.urlopen(request,timeout=30) as response:return response.status,json.load(response)
            except urllib.error.HTTPError as error:return error.code,json.load(error)
        def check(name,expected,observed):
            tests.append({'name':name,'expected':expected,'observed':observed,'status':'passed' if expected==observed else 'failed'})
            assert expected==observed,name+': '+str(observed)
        check('Unauthenticated world request',401,post('state',{})[0])
        _,login=post('login',{'game':'sandbox','username':'ios_fixture','password':'fixture-password-123'})
        token=login['token'];snapshot=login['state'];check('Sandbox uses actual generator and health',('sandbox',20),(snapshot['game'],snapshot['health']))
        check('Snapshot contains real solid terrain',True,len(snapshot['cells'])>100)
        x,y,z=snapshot['pose'][:3]
        code,moved=post('move',dict(zip(['x','y','z','yaw','pitch'],snapshot['pose'])),token)
        check('Native pose reaches existing multiplayer transport',200,code)
        current=dict(zip(['x','y','z','yaw','pitch'],snapshot['pose']));current['sequence']=2
        check('Ordered pose accepted',200,post('move',current,token)[0])
        old=current|{'sequence':1,'x':current['x']+3};post('move',old,token)
        _,after=post('state',{},token)
        check('Delayed pose cannot rewind player',current['x'],after['pose'][0])
        import math
        coord=(math.floor(x),math.floor(y)-1,math.floor(z))
        code,edit=post('action',{'kind':'edit','blockX':coord[0],'blockY':coord[1],'blockZ':coord[2],'type':0,'slot':0},token)
        check('Break is confirmed by game server',200,code)
        check('Broken ground removed from authoritative snapshot',False,any(tuple(cell[:3])==coord for cell in edit['cells']))
        _,rejected=post('action',{'kind':'edit','blockX':500,'blockY':coord[1],'blockZ':500,'type':0,'slot':0},token)
        check('Out-of-reach edit rejected',True,'Placement rejected' in rejected['notice'])
        check('Logout revokes bearer',200,post('logout',{},token)[0]);check('Revoked token denied',401,post('state',{},token)[0])
        _,login=post('login',{'game':'city','username':'ios_fixture','password':'fixture-password-123'})
        token=login['token'];city=login['state']['city'];count=len(city['roads']);budget=city['treasury']
        code,road=post('action',{'kind':'city','command':1,'value':0,'points':[[40,10],[46,10]]},token)
        check('City road command reaches authoritative simulation',200,code)
        tests.append({'name':'City road result','status':'observed','observed':road['notice']})
        check('Road extends actual city grid',True,len(road['city']['roads'])>count)
        check('Road debits actual treasury',True,road['city']['treasury']<budget)
        code,bad=post('action',{'kind':'city','command':1,'value':0,'points':[]},token)
        tests.append({'name':'Missing road endpoints result','status':'observed','observed':bad.get('notice',bad.get('error'))})
        check('Missing road points rejected',True,'endpoints' in bad.get('notice',''))
        check('City citizens populated',True,len(road['city']['citizens'])>0)
        post('logout',{},token)
    finally:
        process.terminate()
        try:process.wait(timeout=10)
        except subprocess.TimeoutExpired:process.kill();process.wait()
        report={'environment':'Linux; synthetic loopback Java TLS game servers and native-client gateway; fresh profiles in assigned worktree target directory','command':'python3 deploy/run_mobile_gateway_smoke.py --report '+args.report,'playtest':'CLI integration: actual sandbox login, server block edit, reach rejection, logout and token revocation; actual city road, treasury, endpoint rejection and citizens. Native iPhone touch workflow is pending the remote simulator run.','tests':tests}
        path=Path(args.report);path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(report,indent=2)+'\n')
print('Mobile gateway integration playtest passed:',len(tests),'checks/observations.')
