package dev.jayms.physics;

import dev.jayms.*;
import dev.jayms.net.*;
import java.util.*;
import java.util.function.Consumer;
import org.joml.Vector3f;
import org.joml.Matrix4f;

/** Bounded, offline city cheat. Persistent edits belong to the caller; bodies are transient. */
public final class CityMissiles implements AutoCloseable {
    public static final int RADIUS = 6, MAX_DEBRIS = 192;
    private final PhysicsWorld physics = new PhysicsWorld();
    private RigidBody missile;
    private Mesh cube, puffMesh;
    private Vector3f blast;
    private float age, flightAge;
    public static final int MAX_SMOKE = 96;
    public record Smoke(Vector3f position, Vector3f velocity, float seedSize, float shade) {}
    private final List<Smoke> smoke = new ArrayList<>();
    public List<Smoke> smoke() { return List.copyOf(smoke); }
    public float effectAge() { return age; }
    public float smokeSize(Smoke puff) {
        return puff.seedSize() * (1 + age * .45f) * Math.min(1, Math.max(0, (8-age)/2));
    }
    public int impacts, removed;
    public boolean active() { return missile != null; }
    public List<RigidBody> debris() { return List.copyOf(physics.bodies); }

    public boolean launch(Vector3f target) {
        if (active() || !target.isFinite()) return false;
        physics.bodies.clear(); smoke.clear(); blast = null; age = 0; flightAge = 0;
        missile = new RigidBody(new Vector3f(target).add(0, 28, 0), new Vector3f(.35f, 1.2f, .35f), 10);
        missile.mask = 0; missile.velocity.y = -8; physics.bodies.add(missile);
        return true;
    }

    public void update(World world, float elapsed, Consumer<Vector3f> demolish, Consumer<Protocol.Edit> edit) {
        float dt = Math.min(Math.max(elapsed, 0), .25f);
        if (missile == null && blast == null) return;
        flightAge += dt; age += dt;
        for (var puff : smoke) {
            puff.position().fma(dt, puff.velocity());
            puff.velocity().mul((float)Math.exp(-dt * .22f));
            puff.velocity().y += dt * .45f;
        }
        // Small steps plus exact voxel sweeps stop both missile and shards at thin geometry.
        for (int step = 0; step < 6; step++) {
            var old = new IdentityHashMap<RigidBody, Vector3f>();
            for (var body : physics.bodies) old.put(body, new Vector3f(body.position));
            physics.step(dt / 6);
            boolean impact = false;
            for (var body : physics.bodies) {
                Vector3f desired = new Vector3f(body.position); body.position.set(old.get(body));
                for (int axis = 0; axis < 3; axis++) {
                    var p = body.position; var h = body.halfSize;
                    float delta = desired.get(axis) - p.get(axis);
                    float allowed = VoxelMotion.allowed(world, new VoxelQueries.Box(p.x-h.x,p.y-h.y,p.z-h.z,p.x+h.x,p.y+h.y,p.z+h.z), delta, axis);
                    p.setComponent(axis, p.get(axis) + allowed);
                    if (Math.abs(allowed-delta) > .00001f) {
                        if (body == missile) impact = true;
                        body.velocity.setComponent(axis, -body.velocity.get(axis) * .2f);
                    }
                }
            }
            if (impact) { detonate(world, demolish, edit); break; }
        }
        if (missile != null && flightAge > 8) { physics.bodies.clear(); missile = null; }
        if (blast != null && age > 8) { physics.bodies.clear(); smoke.clear(); blast = null; }
    }

    private void detonate(World world, Consumer<Vector3f> demolish, Consumer<Protocol.Edit> edit) {
        blast = new Vector3f(missile.position).add(0,-missile.halfSize.y,0); missile = null;
        physics.bodies.clear(); age = 0; impacts++; removed = 0;
        var cells = new ArrayList<Protocol.Edit>();
        var materials = new ArrayList<Integer>();
        int cx=(int)Math.floor(blast.x), cy=(int)Math.floor(blast.y), cz=(int)Math.floor(blast.z);
        for(int x=cx-RADIUS;x<=cx+RADIUS;x++) for(int y=cy-RADIUS;y<=cy+RADIUS;y++) for(int z=cz-RADIUS;z<=cz+RADIUS;z++) {
            if (!world.isLoaded(x,y,z) || new Vector3f(x+.5f,y+.5f,z+.5f).distanceSquared(blast)>RADIUS*RADIUS) continue;
            int material=world.sample(x,y,z);
            if(material==Blocks.AIR || material==Blocks.WATER) continue;
            cells.add(new Protocol.Edit(x,y,z,Blocks.AIR)); materials.add(material);
        }
        // Snapshot shards before demolition clears complete city blueprints and their records.
        demolish.accept(new Vector3f(blast));
        int stride=Math.max(1,(int)Math.ceil(cells.size()/(double)MAX_DEBRIS));
        for(int i=0;i<cells.size();i++) {
            var cell=cells.get(i); edit.accept(cell); removed++;
            if(i%stride!=0) continue;
            var body=new RigidBody(new Vector3f(cell.x()+.5f,cell.y()+.5f,cell.z()+.5f),new Vector3f(.42f),1);
            body.mask=0; body.material=materials.get(i); body.angularVelocity.set(i%3+1,2,1);
            physics.bodies.add(body);
        }
        physics.explode(blast,RADIUS*3,32);
        for(var body:physics.bodies) body.velocity.y+=12;
        smoke.clear();
        var random = new Random(0xB1A57L + impacts);
        for (int i=0; i<MAX_SMOKE; i++) {
            float angle=random.nextFloat()*(float)Math.PI*2;
            boolean dust=i<24, cap=i>=48;
            float radius=dust?2+random.nextFloat()*3:cap?random.nextFloat()*3:random.nextFloat()*1.2f;
            var offset=new Vector3f((float)Math.cos(angle)*radius, dust?.3f:cap?3+random.nextFloat()*2:random.nextFloat()*3, (float)Math.sin(angle)*radius);
            var velocity=new Vector3f(offset.x*(dust?2.4f:.5f),dust?.5f:cap?3:4,offset.z*(dust?2.4f:.5f));
            smoke.add(new Smoke(new Vector3f(blast).add(offset),velocity,dust?.45f:cap?.9f:.6f,.22f+random.nextFloat()*.2f));
        }
    }

    public void render(ShaderProgram shader, Vector3f camera) {
        if (physics.bodies.isEmpty() && blast == null) return;
        if(cube==null) { var chunk=new Chunk(); chunk.setBlock(0,0,0,Blocks.STONE); chunk.generateMesh(); cube=chunk.getMesh(); }
        shader.setInt("uVertexColor",0); shader.setInt("uInstanced",0); shader.setInt("uLightingEnabled",0);
        for(var body:physics.bodies) {
            float[] color=body==missile?new float[]{.9f,.16f,.15f}:Blocks.color(body.material);
            shader.setVector3("uColor",color[0],color[1],color[2]);
            shader.setMatrix4("uModel",new Matrix4f().translation(body.position).rotate(body.rotation).scale(new Vector3f(body.halfSize).mul(2)).translate(-.5f,-.5f,-.5f)); cube.render();
            if(body==missile) {
                // Nose points down; tail fins and exhaust stay above the falling body.
                box(shader,new Vector3f(body.position).add(0,-1.3f,0),new Vector3f(.18f,.25f,.18f),.25f,.25f,.28f);
                box(shader,new Vector3f(body.position).add(0,.7f,0),new Vector3f(.75f,.15f,.15f),.6f,.6f,.65f);
                box(shader,new Vector3f(body.position).add(0,.7f,0),new Vector3f(.15f,.15f,.75f),.6f,.6f,.65f);
                box(shader,new Vector3f(body.position).add(0,1.7f,0),new Vector3f(.2f,.5f,.2f),1,.55f,.05f);
            }
        }
        if(puffMesh==null) puffMesh=createPuffMesh();
        var sorted=new ArrayList<>(smoke);
        sorted.sort(Comparator.comparingDouble((Smoke p)->p.position().distanceSquared(camera)).reversed());
        boolean blending=org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_BLEND);
        boolean depthWrite=org.lwjgl.opengl.GL11.glGetBoolean(org.lwjgl.opengl.GL11.GL_DEPTH_WRITEMASK);
        int source=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_SRC_RGB);
        int destination=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_DST_RGB);
        int sourceAlpha=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_SRC_ALPHA);
        int destinationAlpha=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_DST_ALPHA);
        org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_BLEND);
        org.lwjgl.opengl.GL11.glBlendFunc(org.lwjgl.opengl.GL11.GL_SRC_ALPHA,org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA);
        org.lwjgl.opengl.GL11.glDepthMask(false);
        // Growing rounded clusters form rising smoke, a broad cap and an outward dust skirt.
        for (var puff : sorted) {
            float size=smokeSize(puff), heat=Math.max(0,1-age*2);
            float shade=puff.shade();
            float alpha=.22f*Math.min(1,Math.max(0,(8-age)/2));
            shader.setFloat("uTransparency",1-alpha);
            shader.setVector3("uColor",shade+heat*.65f,shade+heat*.25f,shade*(1-heat));
            shader.setMatrix4("uModel",new Matrix4f().translation(puff.position()).scale(size));
            puffMesh.render();
        }
        shader.setFloat("uTransparency",0);
        org.lwjgl.opengl.GL11.glDepthMask(depthWrite);
        org.lwjgl.opengl.GL14.glBlendFuncSeparate(source,destination,sourceAlpha,destinationAlpha);
        if(!blending) org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_BLEND);
        if(blast!=null && age<.4f) {
            float size=1+age*12;
            box(shader,new Vector3f(blast).add(0,1,0),new Vector3f(size),1,.8f,.2f);
        }
        shader.setInt("uVertexColor",1); shader.setInt("uLightingEnabled",1);
    }
    private void box(ShaderProgram shader,Vector3f position,Vector3f half,float r,float g,float b) {
        shader.setVector3("uColor",r,g,b);
        shader.setMatrix4("uModel",new Matrix4f().translation(position).scale(new Vector3f(half).mul(2)).translate(-.5f,-.5f,-.5f));
        cube.render();
    }
    private static Mesh createPuffMesh() {
        int rings=8, segments=12;
        float[] vertices=new float[(rings+1)*(segments+1)*9];
        int[] indices=new int[rings*segments*6];
        int v=0,k=0;
        for(int r=0;r<=rings;r++) for(int t=0;t<=segments;t++) {
            double latitude=Math.PI*r/rings, longitude=2*Math.PI*t/segments;
            float x=(float)(Math.sin(latitude)*Math.cos(longitude)), y=(float)Math.cos(latitude), z=(float)(Math.sin(latitude)*Math.sin(longitude));
            for(float value:new float[]{x,y,z,x,y,z,1,1,1}) vertices[v++]=value;
        }
        for(int r=0;r<rings;r++) for(int t=0;t<segments;t++) {
            int a=r*(segments+1)+t,b=a+segments+1;
            for(int index:new int[]{a,a+1,b,a+1,b+1,b}) indices[k++]=index;
        }
        return new Mesh(new MeshData(vertices,indices));
    }
    public void close() { if(cube!=null)cube.close(); if(puffMesh!=null)puffMesh.close(); }
}
