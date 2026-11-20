#!/usr/bin/env python3
"""Actual isolated Nginx/TLS/Java game workflow; no production service or private profiles."""
import argparse
import hashlib
import http.client
import json
import math
from pathlib import Path
import socket
import ssl
import subprocess
import tempfile
import time

from render_proxy import render

ROOT=Path(__file__).resolve().parents[2]


def stop(process):
    if process is None:return
    if process.poll() is None:process.terminate()
    try:process.wait(timeout=10)
    except subprocess.TimeoutExpired:
        process.kill();process.wait(timeout=5)


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--nginx',type=Path,required=True)
    parser.add_argument('--report',type=Path,required=True)
    args=parser.parse_args()
    tests=[];fixture=None;nginx=None;completed=False
    def check(name,condition):
        tests.append({'name':name,'status':'passed' if condition else 'failed'})
        if not condition:raise AssertionError(name)
    (ROOT/'target').mkdir(exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='mobile-https-',dir=ROOT/'target') as folder:
        work=Path(folder);work.chmod(0o700)
        try:
            ready=work/'ready.json'
            fixture=subprocess.Popen(['java','-cp',str(ROOT/'target/classes'),
                'dev.jayms.net.mobile.MobileFixtureHost',str(work/'profile'),str(ready)],
                cwd=ROOT,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
            deadline=time.monotonic()+30
            while not ready.exists() and fixture.poll() is None and time.monotonic()<deadline:time.sleep(.1)
            check('Fresh isolated Java game servers start',ready.exists())
            upstream=int(json.loads(ready.read_text())['gateway'].rsplit(':',1)[1])
            # A generated localhost certificate is trusted only by this test context.
            cert=work/'certificate.pem';key=work/'private-key.pem'
            subprocess.run(['openssl','req','-x509','-newkey','rsa:2048','-nodes',
                '-keyout',str(key),'-out',str(cert),'-days','1','-subj','/CN=localhost',
                '-addext','subjectAltName=DNS:localhost'],check=True,timeout=15,
                stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
            key.chmod(0o600)
            with socket.socket() as listener:
                listener.bind(('127.0.0.1',0));port=listener.getsockname()[1]
            include=work/'mobile.conf'
            include.write_text(render('localhost',cert,key,upstream,port,True))
            config=work/'nginx.conf'
            config.write_text('daemon off; master_process off; worker_processes 1;\n'
                'error_log /dev/null; pid "'+str(work/'nginx.pid')+'";\n'
                'events { worker_connections 128; }\nhttp {\n'
                'client_body_temp_path "'+str(work/'body-temp')+'";\n'
                'proxy_temp_path "'+str(work/'proxy-temp')+'";\n'
                'fastcgi_temp_path "'+str(work/'fastcgi-temp')+'";\n'
                'uwsgi_temp_path "'+str(work/'uwsgi-temp')+'";\n'
                'scgi_temp_path "'+str(work/'scgi-temp')+'";\n'
                'include "'+str(include)+'";\n}\n')
            command=[str(args.nginx.resolve()),'-e','/dev/null','-p',str(work)+'/', '-c',str(config)]
            syntax=subprocess.run(command+['-t'],timeout=10,stdout=subprocess.DEVNULL,stderr=subprocess.PIPE)
            if syntax.returncode:
                # Configuration-compiler error only, never proxy/runtime request logs.
                errors=[line for line in syntax.stderr[:2048].decode(errors='replace').splitlines() if '[emerg]' in line]
                print('Nginx configuration error:',errors[0].replace(str(work),'FIXTURE')[:400] if errors else 'syntax check failed')
                check('Actual Nginx accepts rendered configuration',False)
            check('Actual Nginx accepts rendered configuration',True)
            nginx=subprocess.Popen(command,cwd=work,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
            context=ssl.create_default_context(cafile=str(cert))
            def request(path,body=None,method='POST',token=None,headers=None,tls=context):
                h={'Content-Type':'application/json'}
                if token:h['Authorization']='Bearer '+token
                if headers:h.update(headers)
                wire=json.dumps(body).encode() if isinstance(body,dict) else body
                connection=http.client.HTTPSConnection('localhost',port,context=tls,timeout=100)
                try:
                    connection.request(method,path,body=wire,headers=h)
                    response=connection.getresponse();data=response.read(8_000_001)
                    if len(data)>8_000_000:raise AssertionError('response bound')
                    return response.status,dict(response.getheaders()),data
                finally:connection.close()
            deadline=time.monotonic()+10
            while True:
                try:health=request('/mobile/v1/health',method='GET');break
                except (ConnectionError,OSError):
                    if nginx.poll() is not None or time.monotonic()>deadline:raise AssertionError('proxy startup') from None
                    time.sleep(.1)
            check('TLS health exposes only fixed process liveness',health[0]==200 and json.loads(health[2])=={
                'application':'voxel-one','component':'mobile-gateway','status':'alive','protocol':1})
            check('Health and API replies are no-store/nosniff',health[1].get('Cache-Control')=='no-store' and health[1].get('X-Content-Type-Options')=='nosniff')
            try:
                request('/mobile/v1/health',method='GET',tls=ssl.create_default_context())
                untrusted=False
            except ssl.SSLCertVerificationError:untrusted=True
            check('Default trust rejects the test-only certificate',untrusted)
            for version in (ssl.TLSVersion.TLSv1_2,ssl.TLSVersion.TLSv1_3):
                protocol=ssl.create_default_context(cafile=str(cert));protocol.minimum_version=version;protocol.maximum_version=version
                check('HTTPS supports '+version.name,request('/mobile/v1/health',method='GET',tls=protocol)[0]==200)
            for method,path,body,headers,status in [
                ('GET','/mobile/v1/state',None,None,405),
                ('POST','/mobile/v1/health',{},None,405),
                ('GET','/mobile/v1/health?probe=1',None,None,404),
                ('GET','/mobile/v1/health',None,{'Host':'wrong.invalid'},421),
                ('POST','/mobile/v1/unknown',{},None,404),
                ('POST','/mobile/v1/%73tate',{},None,404),
                ('POST','/mobile//v1/state',{},None,404),
                ('POST','/mobile/v1/state',{}, {'Origin':'https://example.invalid'},403),
                ('POST','/mobile/v1/state',{}, {'Origin':''},403),
                ('POST','/mobile/v1/login',b'x'*8193,None,413),
                ('POST','/mobile/v1/state',{}, {'X-Forwarded-For':'127.0.0.1','Authorization':'Bearer invalid'},401),
            ]:
                check('Reject '+method+' '+path+' status '+str(status),request(path,body,method,headers=headers)[0]==status)
            def post(route,body,token=None):
                code,headers,data=request('/mobile/v1/'+route,body,token=token)
                return code,json.loads(data)
            login_body={'game':'sandbox','username':'ios_fixture','password':'fixture-password-123','itemsVersion':1}
            code,login=post('login',login_body)
            check('HTTPS sign-in reaches pinned Java Sandbox transport',code==200 and login['state']['game']=='sandbox')
            token=login['token'];state=login['state']
            check('Fresh snapshot retains exact model/item schemas',state['schema']==2 and state['contract']['itemsVersion']==1 and len(state['contract']['models'])>0)
            pose=dict(zip(['x','y','z','yaw','pitch'],state['pose']));pose['sequence']=2
            check('Authoritative movement crosses HTTPS',post('move',pose,token)[0]==200)
            post('move',pose|{'sequence':1,'x':pose['x']+3},token)
            code,current=post('state',{},token)
            check('Delayed movement cannot rewind authoritative pose',code==200 and current['pose'][0]==pose['x'])
            coord=(math.floor(pose['x']),math.floor(pose['y'])-1,math.floor(pose['z']))
            edit={'kind':'edit','blockX':coord[0],'blockY':coord[1],'blockZ':coord[2],'type':0,'slot':0}
            code,changed=post('action',edit,token)
            check('HTTPS edit receives real server confirmation',code==200 and not any(tuple(cell[:3])==coord for cell in changed['cells']))
            _,rejected=post('action',edit|{'blockX':500,'blockZ':500},token)
            check('Server reach validation survives proxy', 'Placement rejected' in rejected['notice'])
            check('Logout and bearer revocation cross HTTPS',post('logout',{},token)[0]==200 and post('state',{},token)[0]==401)
            _,again=post('login',login_body)
            check('Reconnect resynchronizes edited authoritative world',not any(tuple(cell[:3])==coord for cell in again['state']['cells']))
            post('logout',{},again['token'])
            code,city=post('login',login_body|{'game':'city'})
            check('City sign-in remains usable through same HTTPS origin',code==200 and city['state']['game']=='city')
            token=city['token'];before=city['state']['city']
            code,road=post('action',{'kind':'city','command':1,'value':0,'points':[[40,10],[46,10]]},token)
            check('City road and treasury stay authoritative',code==200 and len(road['city']['roads'])>len(before['roads']) and road['city']['treasury']<before['treasury'])
            post('logout',{},token)
            stop(fixture);fixture=None
            check('Gateway loss fails health instead of reporting ready',request('/mobile/v1/health',method='GET')[0]==502)
            check('Gateway loss returns failed action',request('/mobile/v1/action',{})[0]==502)
            check('No request or proxy temp files remain after the workflow',not any(p.is_file() for folder in ('body-temp','proxy-temp') for p in (work/folder).rglob('*')))
            completed=True
        finally:
            stop(nginx);stop(fixture)
            args.report.parent.mkdir(parents=True,exist_ok=True)
            receipt={'sourceBase':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),
                'environment':'Linux; real unprivileged Nginx with test-only trusted localhost certificate; isolated Java Sandbox/City servers and fresh synthetic profiles',
                'command':'python3 deploy/mobile/test_proxy.py --nginx WORKTREE_NGINX_BINARY --report REPORT',
                'Playtest':'CLI: verify HTTPS trust and TLS1.2/1.3; process health, method/path/Origin/body rejection; real Sandbox sign-in, movement ordering, edit/reach validation, logout/revocation, reconnect resynchronization; City road/treasury regression; gateway failure. Browser playtesting does not apply to a native-only HTTP integration.',
                'tests':tests,'status':'passed' if completed and tests and all(t['status']=='passed' for t in tests) else 'failed',
                'limits':['Not live VPS, native Simulator or physical iPhone evidence.','Health proves gateway process liveness only, not game-server readiness.','No deployment, account access, production certificate or install was attempted.'],
                'sourceSha256':{name:hashlib.sha256((ROOT/name).read_bytes()).hexdigest() for name in ('deploy/mobile/nginx.conf.in','deploy/mobile/render_proxy.py','src/main/java/dev/jayms/net/mobile/MobileGateway.java')}}
            args.report.write_text(json.dumps(receipt,indent=2)+'\n')
    print('HTTPS mobile proxy playtest passed:',len(tests),'checks.')


if __name__=='__main__':main()
