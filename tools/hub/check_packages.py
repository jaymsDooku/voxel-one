#!/usr/bin/env python3
"""Scoped exact-source package checks. No publication, secrets or runtime-log uploads."""
import argparse
import ctypes
import hashlib
import http.server
import json
import os
from pathlib import Path
import platform
import subprocess
import socketserver
import threading
import time
import zipfile


def generation(source, output, version):
    with zipfile.ZipFile(source) as old, zipfile.ZipFile(output, 'w', zipfile.ZIP_DEFLATED) as new:
        for entry in old.infolist():
            if entry.filename != 'hub-validation-generation.txt':
                new.writestr(entry, old.read(entry.filename))
        new.writestr('hub-validation-generation.txt', version)


def run(args):
    root=Path.cwd(); work=(root/args.work).resolve(); out=(root/args.output).resolve()
    if not work.is_relative_to(root) or not out.is_relative_to(root):
        raise ValueError('Work and evidence paths must remain inside this checkout')
    work.mkdir(parents=True, exist_ok=True);out.mkdir(parents=True, exist_ok=True)
    # All profiles, temporary files, fixtures and artifacts stay in this checkout.
    env=os.environ.copy();env['TMPDIR']=str(work);env['TEMP']=str(work);env['TMP']=str(work)
    if args.display:
        if args.display!=os.environ.get('DISPLAY'):raise ValueError('Use the inherited role display')
        env['DISPLAY']=args.display
    env.pop('JAVA_TOOL_OPTIONS',None);env.pop('JDK_JAVA_OPTIONS',None)
    profile=work/'synthetic-home';profile.mkdir(exist_ok=True);env['LOCALAPPDATA']=str(work/'local-app-data')
    cache=Path(env['LOCALAPPDATA'])/'VoxelOne' if platform.system()=='Windows' else profile/'.voxel-one/client'
    cache.mkdir(parents=True,exist_ok=True);(cache/'hub.properties').write_text('updates=false\n')
    report={'sourceSha':args.source_sha,'sourceTreeSha256':{},'platform':platform.system(),'architecture':platform.machine(),
            'checks':[],'limitations':['JVM UI scale 1/1.5/2 is not proof of changing Windows monitor DPI.',
            'No production release/site promotion occurs in this workflow.','Screenshots/video require human inspection.',
            'Native GPU sessions and MSI shortcuts must be validated separately if runner desktop access fails.']}
    dirty=subprocess.run(['git','status','--porcelain','--','src/main','tools/hub','.github/workflows/hub-validation.yml'],stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,check=True)
    report['sourceKind']='feature working tree' if dirty.stdout.strip() else 'exact commit checkout'
    current_check=None
    for base in ('src/main/java','src/main/resources','tools/hub'):
        for p in sorted(Path(base).rglob('*')):
            if p.is_file() and '__pycache__' not in p.parts:report['sourceTreeSha256'][str(p)]=hashlib.sha256(p.read_bytes()).hexdigest()
    (out/'report.json').write_text(json.dumps(report,indent=2)+'\n')
    def command(argv, child_env=env, timeout=240, output=False):
        if current_check is not None:current_check.setdefault('commands',[]).append([str(x) for x in argv])
        p=subprocess.run([str(x) for x in argv],env=child_env,stdout=subprocess.PIPE,
                         stderr=subprocess.DEVNULL,timeout=timeout)
        if p.returncode:
            failure=next((line for line in p.stdout.decode(errors='replace').splitlines() if line.startswith('HUB_CHECK_FAILURE: ')),None)
            raise AssertionError(failure[:240] if failure else Path(str(argv[0])).name+' exited '+str(p.returncode))
        if output=='playtest':
            return next((line[:400] for line in p.stdout.decode(errors='replace').splitlines() if line.startswith('Playtest: production native ')), 'Native smoke completed; detailed render line unavailable')
        return p.stdout.decode().strip() if output else None
    def check(name, expected, fn):
        nonlocal current_check
        entry={'name':name,'expected':expected,'status':'failed','playtest':'Playtest: '+name};report['checks'].append(entry)
        current_check=entry
        try:entry['observed']=fn() or 'Completed';entry['status']='passed'
        except Exception as error:entry['observed']=type(error).__name__+': '+str(error)[:220]
        finally:
            current_check=None
            (out/'report.json').write_text(json.dumps(report,indent=2)+'\n')
    java=Path(args.jdk)/'bin'/('java.exe' if os.name=='nt' else 'java')
    javac=Path(args.jdk)/'bin'/('javac.exe' if os.name=='nt' else 'javac')
    classes=work/'classes';classes.mkdir(exist_ok=True)
    source_client=root/'target/voxel-one-1.0-SNAPSHOT-client.jar'
    command([javac,'--release','17','-cp',str(source_client),'-d',classes,'tools/hub/HubPlaytest.java',
             'tools/hub/PackageUiProbe.java','tools/hub/UpdaterProbe.java','tools/hub/OnlineLoginPlaytest.java','tools/hub/NativeRenderProbe.java'])
    jars=[];apps=[]
    from package import package
    for i in (1,2):
        jar=work/('shell-'+str(i)+'.jar');generation(root/'target/voxel-one-1.0-SNAPSHOT-launcher.jar',jar,str(i));jars.append(jar)
        apps.append(package(work/('package-'+str(i)), '1.0.'+str(i), jdk=args.jdk, launcher_jar=jar))
    if os.name=='nt':runtime=apps[0]/'runtime/bin/java.exe';exe=lambda app:app/'Voxel One Hub.exe'
    elif platform.system()=='Darwin':runtime=apps[0]/'Contents/runtime/Contents/Home/bin/java';exe=lambda app:app/'Contents/MacOS/Voxel One Hub'
    else:runtime=apps[0]/'lib/runtime/bin/java';exe=lambda app:app/'bin/Voxel One Hub'
    cp=os.pathsep.join(map(str,(classes,source_client)))
    def probe(mode, path, uri, shell=False):
        return command([runtime,'-Djava.io.tmpdir='+str(work),'-cp',cp,'UpdaterProbe',mode,path,uri,str(shell).lower()],output=True)
    artifact={'file':source_client,'corrupt':False,'slow':False,'truncated':False,'revision':args.source_sha,'malformed':False}
    downloads=[0]
    class Handler(http.server.BaseHTTPRequestHandler):
        def log_message(self,*unused):pass
        def do_GET(self):
            if self.path=='/manifest':
                blob=artifact['file'].read_bytes();prefix={'Windows':'windows','Darwin':'mac-arm' if platform.machine().lower()=='arm64' else 'mac-intel'}.get(platform.system(),'linux')
                body=(('format=invalid\n' if artifact['malformed'] else 'format=1\n')+'revision='+artifact['revision']+'\n'+prefix+'.url='+base+'/blob\n'+prefix+'.size='+str(len(blob))+'\n'+prefix+'.sha256='+hashlib.sha256(blob).hexdigest()+'\n').encode()
            else:
                downloads[0]+=1;body=artifact['file'].read_bytes()
                if artifact['corrupt']:body=body[:-1]+bytes([body[-1]^1])
            self.send_response(200);self.send_header('Content-Length',str(len(body)));self.end_headers()
            try:
                if artifact['truncated'] and self.path!='/manifest':body=body[:len(body)//2]
                for pos in range(0,len(body),65536):
                    self.wfile.write(body[pos:pos+65536]);self.wfile.flush()
                    if artifact['slow'] and self.path!='/manifest':time.sleep(.025)
            except (BrokenPipeError,ConnectionResetError):pass
    class LoopbackServer(http.server.ThreadingHTTPServer):
        def server_bind(self):
            # The fixture needs no hostname discovery or LAN traffic.
            socketserver.TCPServer.server_bind(self)
            self.server_name='localhost';self.server_port=self.socket.getsockname()[1]
    server=LoopbackServer(('127.0.0.1',0),Handler);base='http://127.0.0.1:'+str(server.server_port)
    threading.Thread(target=server.serve_forever,daemon=True).start();uri=base+'/manifest'
    native_cache=work/'native-cache';first=None;second=None
    def updater_checks():
        nonlocal first,second
        first=Path(probe('update',native_cache,uri));first_hash=hashlib.sha256(first.read_bytes()).hexdigest()
        second_jar=work/'client-two.jar';generation(source_client,second_jar,'two');artifact['file']=second_jar
        # On Windows deny write/delete sharing on the actual active immutable JAR.
        handle=None
        if os.name=='nt':
            api=ctypes.WinDLL('kernel32',use_last_error=True);api.CreateFileW.restype=ctypes.c_void_p
            api.CreateFileW.argtypes=[ctypes.c_wchar_p,ctypes.c_uint32,ctypes.c_uint32,ctypes.c_void_p,ctypes.c_uint32,ctypes.c_uint32,ctypes.c_void_p]
            handle=api.CreateFileW(str(first),0x80000000,1,None,3,0,None)
            if handle==ctypes.c_void_p(-1).value:raise AssertionError('Cannot acquire Windows deny-write/delete handle')
        try:second=Path(probe('update',native_cache,uri))
        finally:
            if handle is not None:api.CloseHandle.argtypes=[ctypes.c_void_p];api.CloseHandle(handle)
        assert first!=second and hashlib.sha256(first.read_bytes()).hexdigest()==first_hash, "Immutable artifact retention"
        third_jar=work/'client-three.jar';generation(source_client,third_jar,'three')
        for field in ('corrupt','truncated','malformed'):
            artifact['file']=third_jar;artifact[field]=True
            assert Path(probe('update',native_cache,uri))==second, field+' fallback'
            artifact[field]=False
        artifact['slow']=True;probe('cancel',native_cache,uri);artifact['slow']=False
        assert Path(probe('installed',native_cache,uri))==second, 'Cancel retained installed artifact'
        concurrent=work/'concurrent-cache';before=downloads[0]
        argv=[str(runtime),'-Djava.io.tmpdir='+str(work),'-cp',cp,'UpdaterProbe','update',str(concurrent),uri,'false']
        children=[subprocess.Popen(argv,env=env,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL) for _ in range(2)]
        codes=[p.wait(timeout=120) for p in children]
        assert 0 in codes and all(c in (0,3) for c in codes), 'Concurrent process exit='+str(codes)
        # A competing launcher reports busy. Retry after the owner releases the lock.
        probe('update',concurrent,uri)
        assert downloads[0]-before==1, 'Concurrent download count='+str(downloads[0]-before)
        # Recreate the legacy flat layout and verify idempotent metadata migration.
        installed=native_cache/'installed.properties'
        legacy=work/'legacy-cache';legacy.mkdir();(legacy/'installed.properties').write_bytes(installed.read_bytes())
        old=legacy/'versions'/args.source_sha/(second.name);old.parent.mkdir(parents=True);old.write_bytes(second.read_bytes())
        artifact['file']=second_jar;count=downloads[0]
        a=probe('update',legacy,uri);b=probe('update',legacy,uri);assert a==b and downloads[0]==count and old.is_file(), 'Legacy migration duplicate download or file loss'
        return 'Verified immutable update under lock; corrupt/partial/malformed fallback; cancellation; two concurrent processes downloaded once; legacy migration reused bytes.'
    check('Updater failures, locks, concurrency and migration','Previous verified installation retained; no duplicate transfer',updater_checks)
    if args.artifacts_only:
        server.shutdown();server.server_close();report['scope']='Artifact integration only; GUI/package activation still pending'
        report['passed']=all(c['status']=='passed' for c in report['checks']);(out/'report.json').write_text(json.dumps(report,indent=2)+'\n')
        return 0 if report['passed'] else 1
    artifact['file']=jars[0]
    shell_cache=cache/'hub-shell';shell1=Path(probe('update',shell_cache,uri,True))
    empty=work/'empty-path';empty.mkdir(exist_ok=True)
    child_env=env.copy();child_env['PATH']=str(empty)
    child_env['JAVA_TOOL_OPTIONS']='-Duser.home='+str(profile)+' -Djava.io.tmpdir='+str(work)+' -Dvoxel.hub.shellManifest='+uri
    def native_start(app, expected, directory):
        directory.mkdir(parents=True,exist_ok=True)
        p=subprocess.Popen([str(exe(app))],env=child_env,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
        try:
            time.sleep(3)
            command([runtime,'-cp',cp,'PackageUiProbe',directory,profile,'process',p.pid,str(expected)],child_env)
            command([runtime,'-cp',cp,'PackageUiProbe',directory,profile,'screen'],child_env)
            return p
        except Exception:
            command([runtime,'-cp',cp,'PackageUiProbe',directory,profile,'stop',p.pid],child_env);p.wait(timeout=15);raise
    def shells():
        p=native_start(apps[0],shell1,out/'shell-one')
        try:
            # Use real bootstrap staging while the prior shell is still running.
            artifact['file']=jars[1];(cache/'hub.properties').write_text('updates=true\n')
            q=native_start(apps[1],shell1,out/'shell-stage')
            try:
                deadline=time.monotonic()+30;shell2=shell1
                while time.monotonic()<deadline and shell2==shell1:
                    time.sleep(.5);shell2=Path(probe('installed',shell_cache,uri,True))
                assert shell2!=shell1 and shell1.is_file()
            finally:command([runtime,'-cp',cp,'PackageUiProbe',out,profile,'stop',q.pid],child_env);q.wait(timeout=15)
        finally:command([runtime,'-cp',cp,'PackageUiProbe',out,profile,'stop',p.pid],child_env);p.wait(timeout=15)
        (cache/'hub.properties').write_text('updates=false\n')
        q=native_start(apps[1],shell2,out/'shell-two')
        command([runtime,'-cp',cp,'PackageUiProbe',out,profile,'stop',q.pid],child_env);q.wait(timeout=15)
        return 'Two packaged fixture generations: bundled runtime launched verified v1; bootstrap staged v2 while v1 active; next start executed v2. No public release claim.'
    check('Native executable and two-version bootstrap update','Empty PATH works; active shell retained; new shell activates next start',shells)
    if os.name=='nt':
        def installer():
            # Windows Installer resolves folders for the real ephemeral OS user.
            # Keep the app's synthetic Java home/cache separate from that context.
            installer_env=os.environ.copy()
            for key in ('TMPDIR','TEMP','TMP'):installer_env[key]=str(work)
            for key in ('JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS'):installer_env.pop(key,None)
            destination=work/'installer';destination.mkdir()
            command([Path(args.jdk)/'bin/jpackage.exe','--type','msi','--name','Voxel One Hub','--app-image',apps[0],
                     '--dest',destination,'--app-version','1.0.1','--win-shortcut','--win-menu','--win-menu-group','Voxel One','--win-per-user-install'],installer_env)
            msi=next(destination.glob('*.msi'));installed=work/'installed-app'
            sentinel=profile/'.voxel-one/save-preservation-sentinel';sentinel.parent.mkdir(exist_ok=True);sentinel.write_text('synthetic-save')
            command(['C:/Windows/System32/msiexec.exe','/i',msi,'/qn','/norestart','INSTALLDIR='+str(installed)],installer_env,timeout=240)
            try:
                if not (installed/'Voxel One Hub.exe').is_file():raise AssertionError('MSI did not honor scoped INSTALLDIR')
                process=subprocess.Popen([str(installed/'Voxel One Hub.exe')],env=child_env,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
                try:time.sleep(3);command([runtime,'-cp',cp,'PackageUiProbe',out/'msi',profile,'process',process.pid,'-'],child_env)
                finally:command([runtime,'-cp',cp,'PackageUiProbe',out,profile,'stop',process.pid],child_env);process.wait(timeout=15)
                # Inspect only shortcuts for this synthetic installation on the ephemeral CI desktop.
                powershell=Path(os.environ['SystemRoot'])/'System32/WindowsPowerShell/v1.0/powershell.exe'
                # Inspect only this application's expected per-user links, never
                # enumerate unrelated desktop or Start-menu contents.
                shortcut_script="$ErrorActionPreference='Stop';$s=New-Object -ComObject WScript.Shell;$links=@((Join-Path ([Environment]::GetFolderPath('DesktopDirectory')) 'Voxel One Hub.lnk'),(Join-Path ([Environment]::GetFolderPath('Programs')) 'Voxel One\\Voxel One Hub.lnk'));$present=0;$matching=0;foreach($link in $links){if(Test-Path -LiteralPath $link){$present++;$actual=$s.CreateShortcut($link).TargetPath;if($actual -and [String]::Equals([IO.Path]::GetFullPath($actual),[IO.Path]::GetFullPath($target),[StringComparison]::OrdinalIgnoreCase)){$matching++}}};if($matching -ne 2){Write-Output ('HUB_CHECK_FAILURE: MSI expected links present='+$present+'; matching target='+$matching);exit 1}"
                target_literal=str(installed/'Voxel One Hub.exe').replace("'","''")
                shortcut_script="$target='"+target_literal+"';"+shortcut_script
                command([powershell,'-NoProfile','-NonInteractive','-Command',shortcut_script],installer_env)
            finally:command(['C:/Windows/System32/msiexec.exe','/x',msi,'/qn','/norestart'],installer_env,timeout=240)
            assert sentinel.read_text()=='synthetic-save'
            return 'Scoped MSI installed, native executable ran, desktop/menu shortcuts verified, uninstall retained synthetic save.'
        check('Windows MSI, shortcuts and uninstall','Bundled runtime install/launch and saves survive uninstall',installer)
    for scale in ('1','1.5','2'):
        def ui(scale=scale):
            clean=env.copy();clean['PATH']=str(empty)
            command([runtime,'-Djava.io.tmpdir='+str(work),'-Dsun.java2d.uiScale='+scale,'-cp',cp,'PackageUiProbe',out/('scale-'+scale),work/('ui-'+scale)],clean)
            missing=out/('scale-'+scale)/'unavailable-extents.txt'
            unavailable=missing.read_text().strip() if missing.exists() else ''
            if unavailable:report.setdefault('pendingQualification',[]).append('JVM scale '+scale+': '+unavailable)
            return 'Production hub cards, available desktop extents and keyboard captured at JVM scale '+scale+('. Full-size qualification pending: '+unavailable if unavailable else '')
        check('UI at JVM scale '+scale,'900x600/1280x800 actual screenshots and bounded UI video',ui)
    def online():
        command([runtime,'-Djava.io.tmpdir='+str(work),'-cp',cp,'OnlineLoginPlaytest',work/'online-profile',out],timeout=120)
        return 'City/Sandbox locked selection; custom loopback port; invalid-port rejection; pinned TLS sign-in passed.'
    check('Synthetic online login handoff','Both native game identities authenticate to their selected TLS server',online)
    for mode in ('city','sandbox','editor'):
        def render(mode=mode):
            native_env=env.copy();native_env['PATH']=str(empty);native_env['ALSOFT_DRIVERS']='null'
            argv=[runtime,'-Djava.io.tmpdir='+str(work)]
            if platform.system()=='Darwin':argv.append('-XstartOnFirstThread')
            argv.extend(['-cp',os.pathsep.join(map(str,(classes,second))),'NativeRenderProbe',work/('native-'+mode),out/('native-'+mode),mode])
            text=command(argv,native_env,timeout=180,output='playtest')
            return text+' Renderer observer smoke; launcher CLI and interactive GPU performance remain separate checks.'
        check('Native rendered '+mode,'Verified platform client renders four real frames with bundled Java',render)
    server.shutdown();server.server_close()
    check('Offline native installation','Last verified client usable without service',lambda:probe('installed',native_cache,uri))
    for p in out.rglob('*'):
        if p.is_file() and p.suffix=='.partial':p.unlink();continue
        if p.is_file() and p.suffix in ('.png','.mp4') and p.stat().st_size>6*1024*1024:
            p.unlink();report['checks'].append({'name':'Artifact bound','status':'failed','observed':'Oversized media removed'})
    report['passed']=all(c['status']=='passed' for c in report['checks'])
    report['fullQualificationPassed']=False
    report['releaseScope']='Opt-in feature preview only; physical GPU/monitor qualification and published shell releases remain untested.'
    (out/'report.json').write_text(json.dumps(report,indent=2)+'\n')
    return 0 if report['passed'] else 1


if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--source-sha',required=True);parser.add_argument('--jdk',required=True)
    parser.add_argument('--artifacts-only',action='store_true',help='Partial local artifact checks; does not satisfy full desktop validation')
    parser.add_argument('--display',help='Inherited role display only; does not start a display server')
    parser.add_argument('--work',default='target/hub-scoped-check');parser.add_argument('--output',default='dashboard/evidence/hub-scoped')
    args=parser.parse_args()
    if len(args.source_sha)!=40 or any(c not in '0123456789abcdef' for c in args.source_sha):parser.error('Expected exact40hex source SHA')
    try:
        result=run(args)
    except Exception:
        # Never upload subprocess output, environment values or runtime logs.
        output=(Path.cwd()/args.output).resolve()
        if output.is_relative_to(Path.cwd()):
            output.mkdir(parents=True,exist_ok=True)
            file=output/'report.json'
            report=json.loads(file.read_text()) if file.exists() else {'sourceSha':args.source_sha,'checks':[]}
            report['passed']=False
            report['checks'].append({'name':'Validation preparation/execution','status':'failed',
                                    'observed':'Preparation or execution failed; no platform acceptance claimed.'})
            file.write_text(json.dumps(report,indent=2)+'\n')
        result=1
    raise SystemExit(result)
