#!/usr/bin/env python3
"""Rebuild the recorded baseline plus timing-only overlays inside this feature worktree.
No checkout, index change, external worktree, commit or publication.
"""
import io,os,shutil,subprocess,tarfile
from pathlib import Path
root=Path(__file__).resolve().parents[1];base='4f8a09aca177062a50d0ab2b68e36735318bfb22'
build=root/'target/baseline-build';build.mkdir(parents=True,exist_ok=True)
archive=subprocess.check_output(['git','archive',base,'pom.xml','src/main'],cwd=root)
with tarfile.open(fileobj=io.BytesIO(archive)) as tar:tar.extractall(build,filter='data')
env=os.environ.copy();env['JAVA_HOME']='/usr/lib/jvm/jdk-21.0.5-oracle-x64'
with (build/'build-private.txt').open('w') as log:
 subprocess.run(['mvn','-q','-Dmaven.repo.local='+str(root/'target/m2'),'-DskipTests','package'],cwd=build,env=env,stdout=log,stderr=log,check=True)
shutil.copyfile(build/'target/voxel-one-1.0-SNAPSHOT-client.jar',root/'target/streaming-baseline-client.jar')
src=root/'target/baseline-instrumented-src/dev/jayms';(src/'render').mkdir(parents=True,exist_ok=True)
def original(name):return (build/'src/main/java/dev/jayms'/name).read_text()
r=original('render/RenderPipeline.java')
r=r.replace('    public void update(World world, float x, float z) {','''    public long skyBuildNanos, lightingNanos, sceneNanos, lightingUploadNanos, shadowCpuNanos, probeCpuNanos;
    public void update(World world, float x, float z) {
        long start=System.nanoTime();skyBuildNanos=lightingNanos=sceneNanos=lightingUploadNanos=0;
        try{updateScene(world,x,z);}finally{lightingUploadNanos=Math.max(0,System.nanoTime()-start-skyBuildNanos-lightingNanos-sceneNanos);}
    }
    private void updateScene(World world,float x,float z) {
        long stage=System.nanoTime();''')
r=r.replace('        if (skyChanged) {\n            var rebuilt=','        sceneNanos=System.nanoTime()-stage;\n        if (skyChanged) {\n            stage=System.nanoTime();\n            var rebuilt=').replace('            skyModels=models;','            skyBuildNanos=System.nanoTime()-stage;\n            skyModels=models;').replace('        LightVolume next = lighting.update','        stage=System.nanoTime();\n        LightVolume next = lighting.update').replace('        if (next == null) return;','        lightingNanos=System.nanoTime()-stage;\n        if (next == null) return;')
r=r.replace('    public void renderShadows(World world, VoxelModelRenderer models, Vector3f position) {','''    public void renderShadows(World world, VoxelModelRenderer models, Vector3f position) {
        long start=System.nanoTime();probeCpuNanos=0;
        try{renderShadowsImpl(world,models,position);}finally{shadowCpuNanos=Math.max(0,System.nanoTime()-start-probeCpuNanos);}
    }
    private void renderShadowsImpl(World world,VoxelModelRenderer models,Vector3f position) {''').replace('    private void captureProbes(World world,VoxelModelRenderer models,Vector3f position){','''    private void captureProbes(World world,VoxelModelRenderer models,Vector3f position){
        long start=System.nanoTime();try{captureProbesImpl(world,models,position);}finally{probeCpuNanos=System.nanoTime()-start;}
    }
    private void captureProbesImpl(World world,VoxelModelRenderer models,Vector3f position){''')
(src/'render/RenderPipeline.java').write_text(r)
recorder=original('render/PerformanceRecorder.java');current=(root/'src/main/java/dev/jayms/render/PerformanceRecorder.java').read_text();start=current.index('    public enum Metric {');end=current.index('    public enum Route',start)
a=recorder.index('    public enum Metric {');b=recorder.index('    public enum Route',a);recorder=recorder[:a]+current[start:end]+recorder[b:];(src/'render/PerformanceRecorder.java').write_text(recorder)
main=original('Main.java');metrics=''
for key,field in [('SKY_BUILD_CPU_MS','skyBuildNanos'),('LIGHTING_CPU_MS','lightingNanos'),('LIGHTING_UPLOAD_CPU_MS','lightingUploadNanos'),('SCENE_CPU_MS','sceneNanos'),('SHADOW_CPU_MS','shadowCpuNanos'),('PROBE_CPU_MS','probeCpuNanos')]:metrics+=f'        metric(PerformanceRecorder.Metric.{key},rendering.{field}/1e6);\n'
main=main.replace('        metric(PerformanceRecorder.Metric.SNAPSHOT_CPU_MS,',metrics+'        metric(PerformanceRecorder.Metric.SNAPSHOT_CPU_MS,');(src/'Main.java').write_text(main)
classes=root/'target/baseline-instrumented-classes';classes.mkdir(exist_ok=True)
subprocess.run([env['JAVA_HOME']+'/bin/javac','-cp',str(root/'target/streaming-baseline-client.jar'),'-d',str(classes),*[str(p) for p in sorted(src.rglob('*.java'))]],cwd=root,env=env,check=True)
print('Prepared source baseline',base,'and timing-only overlays; no game check executed.')
