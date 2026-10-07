#!/usr/bin/env python3
"""Check physics with the expanded renderer; preserve all historical physics artifacts."""
import argparse, os, shutil, subprocess
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--display',required=True);p.add_argument('--phase',choices=['lab','walker','all'],default='all');a=p.parse_args()
root=Path(__file__).resolve().parents[1];os.chdir(root)
out=root/'target/rendering-physics-validation';home=root/'target/rendering-physics-home';classes=root/'target/rendering-physics-smoke'
for folder in (out,home,classes,root/'target/tmp'):folder.mkdir(parents=True,exist_ok=True)
env=os.environ.copy();env['DISPLAY']=a.display;env['LIBGL_ALWAYS_SOFTWARE']='1';env['MESA_SHADER_CACHE_DISABLE']='true';env.pop('WAYLAND_DISPLAY',None)
env['TMPDIR']=str(root/'target/tmp');env['XDG_CACHE_HOME']=str(home/'cache')
jars=[str(f) for f in (root/'target/maven-cache').rglob('*.jar') if 'natives-windows' not in f.name]
cp=os.pathsep.join([str(root/'target/classes'),str(classes),*jars])
subprocess.run(['javac','-cp',cp,'-d',str(classes),'deploy/PhysicsSmoke.java','deploy/RenderingRestoredWalkerSmoke.java','deploy/VerifyPhysicsMedia.java'],check=True)
for phase in (['lab','walker'] if a.phase=='all' else [a.phase]):
    active=env.copy();command=['java','-Xmx512m','-Djava.io.tmpdir='+str(root/'target/tmp'),'-Duser.home='+str(home)]
    if phase=='walker':
        active['MESA_GL_VERSION_OVERRIDE']='3.3';active['MESA_GLSL_VERSION_OVERRIDE']='330';command+=['-Dvoxel.gl33=true']
    command+=['-cp',cp,'PhysicsSmoke' if phase=='lab' else 'RenderingRestoredWalkerSmoke',str(out)]
    with (out/(phase+'-private-runtime.txt')).open('w') as log:r=subprocess.run(command,env=active,stdout=log,stderr=log,timeout=240)
    print('Playtest:',phase,'exit',r.returncode)
    if r.returncode:
        failure=out/('failure.txt' if phase=='lab' else 'walker-failure.txt')
        if failure.exists():print(failure.read_text()[:500])
        raise SystemExit(r.returncode)
    for f in out.glob('physics-*.png'):shutil.copyfile(f,root/'dashboard/evidence'/('rendering-restored-'+f.name))
    reports=['results','wheel-results','contact-trigger-results','hinge-inertia-results','hinge-results','platform-results','rope-results','force-results'] if phase=='lab' else ['walker-results']
    for name in reports:shutil.copyfile(out/(name+'.json'),root/'dashboard/evidence'/('rendering-restored-physics-'+name+'.json'))
    if phase=='lab':
        clips=sorted((home/'.voxel-one/recordings').glob('*.mp4'));assert clips,'F10 clip missing'
        clip=clips[-1];assert clip.stat().st_size<6_000_000,'F10 clip exceeds 6 MB'
        destination=root/'dashboard/evidence/rendering-restored-physics-collapse.mp4';shutil.copyfile(clip,destination)
        subprocess.run(['java','-Djava.io.tmpdir='+str(root/'target/tmp'),'-cp',cp,'VerifyPhysicsMedia',str(destination),str(root/'dashboard/evidence/rendering-restored-physics-media-check.json')],check=True)
