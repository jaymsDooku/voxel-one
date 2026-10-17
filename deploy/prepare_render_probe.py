#!/usr/bin/env python3
"""Create method-scope GL diagnostic overlays in target only; no baseline source/gameplay mutation."""
from pathlib import Path
import argparse,subprocess,shutil
p=argparse.ArgumentParser();p.add_argument('--phase',choices=['before','after'],required=True);a=p.parse_args();root=Path.cwd();out=root/'target'/('render-probe-'+a.phase);src=out/'src';classes=out/'classes';classes.mkdir(parents=True,exist_ok=True)
jar=root/'target'/('streaming-baseline-client.jar' if a.phase=='before' else 'streaming-after-client.jar')
def load(name):
 if a.phase=='after':return (root/'src/main/java'/name).read_text()
 overlay=root/'target/baseline-instrumented-src'/name
 return overlay.read_text() if overlay.exists() else subprocess.check_output(['git','show','4f8a09aca177062a50d0ab2b68e36735318bfb22:src/main/java/'+name],text=True)
def wrap(s,signature,label):
 start=s.index('{',s.index(signature));depth=1;i=start+1;state='code'
 while depth:
  c=s[i];n=s[i:i+2]
  if state=='code':
   if n=='//':state='line';i+=1
   elif n=='/*':state='comment';i+=1
   elif c=='"':state='string'
   elif c=="'":state='char'
   elif c=='{':depth+=1
   elif c=='}':depth-=1
  elif state=='line':
   if c=='\n':state='code'
  elif state=='comment':
   if n=='*/':state='code';i+=1
  elif c=='\\':i+=1
  elif (state=='string' and c=='"') or (state=='char' and c=="'"):state='code'
  i+=1
 end=i-1
 return s[:start+1]+'try(var diagnosticPass'+label.replace('.','_')+'=new dev.jayms.render.RenderPassProbe("'+label+'")){'+s[start+1:end]+'}'+s[end:]
for name,methods in {
 'dev/jayms/Mesh.java':[],
 'dev/jayms/render/ReflectionProbes.java':[('public void capture(','reflection.capture'),('private void warmPipeline(','reflection.warm')],
 'dev/jayms/Main.java':[('private void render()','main.render')],
 'dev/jayms/render/RenderPipeline.java':[(x,y) for x,y in [('public void begin(','pipeline.begin'),('public void distant(','pipeline.distant'),('public void water(','pipeline.water'),('public void finish()','pipeline.finish'),('public void renderShadows(','pipeline.shadows')]],
 'dev/jayms/render/TemporalPost.java':[('public void finish(','post.finish')],
}.items():
 s=load(name)
 if name.endswith('Mesh.java'):
  s=s.replace('        glDrawElements(GL_TRIANGLES, indexCount, GL_UNSIGNED_INT, 0L);','        dev.jayms.render.RenderPassProbe.descriptor();dev.jayms.render.RenderPassProbe.nativeDraw("indexed.count"+indexCount,()->glDrawElements(GL_TRIANGLES, indexCount, GL_UNSIGNED_INT, 0L));')
  s=s.replace('org.lwjgl.opengl.GL31.glDrawElementsInstanced(\n                GL_TRIANGLES, indexCount, GL_UNSIGNED_INT, 0L, positions.length / 3);','dev.jayms.render.RenderPassProbe.nativeDraw("instanced.count"+indexCount,()->org.lwjgl.opengl.GL31.glDrawElementsInstanced(GL_TRIANGLES,indexCount,GL_UNSIGNED_INT,0L,positions.length/3));')
 if name.endswith('ReflectionProbes.java'):
  s=s.replace('        if(pipelineWarmed)return;','        if(pipelineWarmed)return;RenderPassProbe.arm("warm");')
  s=s.replace('        for(var entry:world.getLoadedChunks().entrySet()){','        RenderPassProbe.arm("capture");for(var entry:world.getLoadedChunks().entrySet()){')
 if name.endswith('Main.java'):
  s=s.replace('        for (var entry : world.getLoadedChunks().entrySet()) {','        try(var drawPass=new dev.jayms.render.RenderPassProbe("world.chunks")){for (var entry : world.getLoadedChunks().entrySet()) {',1)
  s=s.replace('        modelRenderer.render(world, frustum, shader);','        }try(var modelPass=new dev.jayms.render.RenderPassProbe("world.models")){modelRenderer.render(world, frustum, shader);',1)
  s=s.replace('        rendering.water(world,modelRenderer,projection,view,','        }rendering.water(world,modelRenderer,projection,view,',1)
  s=wrap(s,'if (!isometric && !player.thirdPerson() && !(jeep != null && jeep.driving()))','world.held')
 if name.endswith('RenderPipeline.java'):
  if a.phase=='after':s=s.replace('        transport.bind(shader); clusters.bind(shader);','        transport.bind(shader); clusters.bind(shader);RenderPassProbe.lighting(activeRootBuffer?(volume!=null&&volume.uniformRoots()?2:1):0,worldSky==null?0:worldSky.columns);')
  else:s=s.replace('        transport.bind(shader); clusters.bind(shader);','        transport.bind(shader); clusters.bind(shader);RenderPassProbe.lighting(0,worldSky==null?0:worldSky.columns);')
 if name.endswith('TemporalPost.java'):
  k=0
  while '        draw();' in s:
   k+=1;s=s.replace('        draw();','        RenderPassProbe.draw("post.draw'+str(k)+'",this::draw);',1)
 for sig,label in methods:
  if sig in s:s=wrap(s,sig,label)
 dest=src/name;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_text(s)
if a.phase=='before':
 dest=src/'dev/jayms/render/PerformanceRecorder.java';dest.write_text(load('dev/jayms/render/PerformanceRecorder.java'))
dest=src/'dev/jayms/render/RenderPassProbe.java';shutil.copyfile(root/'deploy/RenderPassProbe.java',dest)
subprocess.run(['/usr/lib/jvm/jdk-21.0.5-oracle-x64/bin/javac','--release','17','-cp',str(jar),'-d',str(classes),*[str(f) for f in src.rglob('*.java')]],check=True)
route=(root/'deploy/StreamingRoute.java').read_text().replace('public class StreamingRoute','public class RenderStallRoute').replace('                ((Player)get("player")).driveSeat', '                dev.jayms.render.RenderPassProbe.sample=sample;\n                ((Player)get("player")).driveSeat').replace('                firstObserved=true;','                firstObserved=true;\n                dev.jayms.render.RenderPassProbe.flush(out.resolve("render-passes.csv"),first<0?-1:frame-first);')
(out/'RenderStallRoute.java').write_text(route)
print('Prepared diagnostic-only overlay',a.phase)
