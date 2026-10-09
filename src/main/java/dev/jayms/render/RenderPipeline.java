package dev.jayms.render;

import static org.lwjgl.opengl.GL33.*;

import dev.jayms.*;

import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

import java.nio.*;

/** OpenGL 3.3 HDR lighting with temporal reconstruction and screen-space effects. */
public final class RenderPipeline implements AutoCloseable {
    public static final Vector3f SUN = new Vector3f(.45f, .78f, -.45f).normalize();
    private final ShaderProgram sky =
            new ShaderProgram("shaders/fullscreen.vert", "shaders/sky.frag");
    private final ShaderProgram far =
            new ShaderProgram("shaders/voxel.vert", "shaders/distant.frag");
    public final RenderSettings settings = RenderSettings.defaults();
    private final PlanetAtmosphere atmosphere = new PlanetAtmosphere();
    private final TemporalPost temporal = new TemporalPost(settings);
    private final HiZ hiZ = new HiZ();
    private final WaterRenderer water = new WaterRenderer();
    private final ReflectionProbes probes = new ReflectionProbes();
    private final ClusteredLights clusters = new ClusteredLights();
    private final GpuParticles particles = new GpuParticles();
    private final VoxelTransport transport = new VoxelTransport();
    private final FrameBudget frameBudget = new FrameBudget();
    private final GpuDraw gpuDraw = new GpuDraw();
    private final Matrix4f viewProjection = new Matrix4f();
    private long worldRevision = -1;
    private record SceneChunk(Chunk chunk, Mesh mesh) {}
    private final java.util.Map<ChunkPos, SceneChunk> sceneChunks = new java.util.HashMap<>();
    public int visibleChunks, occludedChunks;
    private final ShaderProgram shadow =
            new ShaderProgram("shaders/shadow.vert", "shaders/shadow.frag");
    private final MaterialTextures materials = new MaterialTextures();
    private final WorldLighting lighting = new WorldLighting();
    private final int vao = glGenVertexArrays(),
            environment = glGenTextures(),
            shadowTexture = glGenTextures(),
            shadowFbo = glGenFramebuffers(),
            irradiance = glGenTextures(),
            fineRoots = glGenTextures(),
            fineLight = glGenTextures(),
            fineBuffer = glGenBuffers();
    private int hdrFbo, hdrColor, hdrDepth, resolveFbo, resolveTexture, resolveDepth, width, height;
    private final boolean software =
            glGetString(GL_RENDERER)
                    .toLowerCase(java.util.Locale.ROOT)
                    .matches(".*(llvmpipe|softpipe|swrast|software).*");
    public final int samples =
            Math.max(
                    1,
                    Math.min(
                            Math.min(4, Integer.getInteger("voxel.msaa", software ? 1 : 4)),
                            Math.min(
                                    glGetInteger(GL_MAX_SAMPLES),
                                    glGetInteger(GL_MAX_COLOR_TEXTURE_SAMPLES))));
    private final int shadowResolution = software ? 1024 : 2048;
    private boolean hasIrradiance;
    private LightVolume volume;
    private Vector3f sun = new Vector3f(SUN);
    private float daylight = 1, ambient = 1;
    private int environmentPhase = -1;

    public void time(dev.jayms.net.city.GameConfig config, double elapsed) {
        atmosphere.profile(config.atmosphere());
        var light = Daylight.at(config, elapsed);
        sun.set(light.sun());
        daylight = light.intensity();
        ambient = light.ambient();
        int phase = (int) (config.hour(elapsed) * 4);
        if (config.cycle() && phase != environmentPhase) {
            environmentPhase = phase;
            // Environment follows the next complete atmosphere view table.
        }
    }

    private final Matrix4f[] shadowMatrices = { new Matrix4f(), new Matrix4f(), new Matrix4f() };

    public RenderPipeline() {
        atmosphere.update(new Vector3f(0,26,0),sun);
        updateEnvironment();
        glBindTexture(GL_TEXTURE_2D_ARRAY, shadowTexture);
        glTexImage3D(GL_TEXTURE_2D_ARRAY, 0, GL_DEPTH_COMPONENT24, shadowResolution,
                shadowResolution, 3, 0, GL_DEPTH_COMPONENT, GL_FLOAT, (ByteBuffer)null);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glBindFramebuffer(GL_FRAMEBUFFER, shadowFbo);
        glFramebufferTextureLayer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, shadowTexture, 0, 0);
        glDrawBuffer(GL_NONE);
        glReadBuffer(GL_NONE);
        check();
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glBindTexture(GL_TEXTURE_3D, irradiance);
        glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_R, GL_CLAMP_TO_EDGE);
        System.out.println(
                "Rendering: "
                        + (settings.taa ? "TAA" : samples > 1 ? samples + "x MSAA" : "native resolve")
                        + ", HDR, cascaded shadows, voxel/screen GI, local reflections");
    }

    private long atmosphereEnvironmentRevision;
    private void updateEnvironment() {
        atmosphere.environment(environment);
        atmosphereEnvironmentRevision=atmosphere.revision;
    }

    public boolean lightingReady() {
        return hasIrradiance;
    }

    /** Mesh identities survive checkMesh clearing dirty; membership also catches streaming unloads. */
    private boolean sceneChanged(World world) {
        var loaded = world.getLoadedChunks();
        boolean changed = sceneChunks.size() != loaded.size();
        if (!changed) for (var entry : loaded.entrySet()) {
            SceneChunk previous = sceneChunks.get(entry.getKey());
            if (previous == null || previous.chunk() != entry.getValue()
                    || previous.mesh() != entry.getValue().getMesh()) {
                changed = true;
                break;
            }
        }
        if (changed) {
            sceneChunks.clear();
            loaded.forEach((pos, chunk) -> sceneChunks.put(pos, new SceneChunk(chunk, chunk.getMesh())));
        }
        return changed;
    }

    public void update(World world, float x, float z) {
        if (clusters.update(world,x,z)) { particles.emitters(clusters.emitters()); probes.invalidate(); }
        if (sceneChanged(world) || worldRevision != world.editsVersion() || world.getLoadedChunks().values().stream().anyMatch(Chunk::dirty)) {
            hiZ.invalidate(); worldRevision = world.editsVersion();
        }
        LightVolume next = lighting.update(world, x, z, Math.round(ambient * 10) / 10f);
        if (next == null) return;
        volume = next;
        probes.invalidate();
        transport.upload(next.transport);
        ByteBuffer buffer = MemoryUtil.memAlloc(next.rgba.length);
        try {
            buffer.put(next.rgba).flip();
            glActiveTexture(GL_TEXTURE3);
            glBindTexture(GL_TEXTURE_3D, irradiance);
            // CPU order x,y,z matches OpenGL volume storage.
            glTexImage3D(
                    GL_TEXTURE_3D,
                    0,
                    GL_RGBA8,
                    next.width,
                    next.height,
                    next.length,
                    0,
                    GL_RGBA,
                    GL_UNSIGNED_BYTE,
                    buffer);
            glActiveTexture(GL_TEXTURE4);
            glBindTexture(GL_TEXTURE_3D, fineRoots);
            glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glTexImage3D(
                    GL_TEXTURE_3D,
                    0,
                    GL_R32I,
                    next.width,
                    next.height,
                    next.length,
                    0,
                    GL_RED_INTEGER,
                    GL_INT,
                    next.roots);
            glActiveTexture(GL_TEXTURE5);
            glBindBuffer(GL_TEXTURE_BUFFER, fineBuffer);
            glBufferData(GL_TEXTURE_BUFFER, next.fine, GL_DYNAMIC_DRAW);
            glBindTexture(GL_TEXTURE_BUFFER, fineLight);
            glTexBuffer(GL_TEXTURE_BUFFER, GL_R32I, fineBuffer);
            glBindBuffer(GL_TEXTURE_BUFFER, 0);
            hasIrradiance = true;
        } finally {
            MemoryUtil.memFree(buffer);
        }
        glActiveTexture(GL_TEXTURE0);
    }

    public void renderShadows(World world, VoxelModelRenderer models, Vector3f position) {
        glBindFramebuffer(GL_FRAMEBUFFER, shadowFbo);
        glViewport(0, 0, shadowResolution, shadowResolution);
        glEnable(GL_DEPTH_TEST); glEnable(GL_CULL_FACE);
        shadow.bind(); shadow.setInt("uInstanced", 0);
        float[] extents = {32, 96, 256};
        for (int cascade = 0; cascade < 3; cascade++) {
            float extent = extents[cascade], texel = extent * 2 / shadowResolution;
            Vector3f center = new Vector3f((float)Math.floor(position.x/texel)*texel,
                    position.y, (float)Math.floor(position.z/texel)*texel);
            Vector3f direction = daylight > .01f ? sun : new Vector3f(sun).negate();
            Vector3f up = Math.abs(direction.y) > .98f ? new Vector3f(0,0,1) : new Vector3f(0,1,0);
            Matrix4f lightView = new Matrix4f().lookAt(new Vector3f(center).fma(384,direction), center, up);
            Matrix4f lightProjection = new Matrix4f().ortho(-extent, extent, -extent, extent, 1, 768);
            shadowMatrices[cascade].set(lightProjection).mul(lightView);
            FrustumIntersection f = new FrustumIntersection(shadowMatrices[cascade]);
            glFramebufferTextureLayer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, shadowTexture, 0, cascade);
            glClear(GL_DEPTH_BUFFER_BIT);
            shadow.setMatrix4("uProjection", lightProjection); shadow.setMatrix4("uView", lightView);
            for (var entry : world.getLoadedChunks().entrySet()) {
                var p = entry.getKey();
                if (entry.getValue().getMesh()==null || !f.testAab(p.chunkX()*16,p.chunkY()*16,p.chunkZ()*16,
                        p.chunkX()*16+16,p.chunkY()*16+16,p.chunkZ()*16+16)) continue;
                shadow.setMatrix4("uModel",new Matrix4f().translation(p.chunkX()*16,p.chunkY()*16,p.chunkZ()*16));
                entry.getValue().getMesh().render();
            }
            models.render(world, f, shadow);
        }
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        if (hasIrradiance) probes.capture(world,models,position,environment,this::bindSceneLighting);
    }

    public void begin(
            int w,
            int h,
            Matrix4f projection,
            Matrix4f view,
            Vector3f camera,
            boolean isometric,
            ShaderProgram voxel) {
        frameBudget.begin(settings);
        atmosphere.quality(settings.atmosphereQuality);
        atmosphere.update(camera,sun);
        if(atmosphereEnvironmentRevision!=atmosphere.revision) { updateEnvironment(); probes.invalidate(); temporal.reset(); }
        viewProjection.set(projection).mul(view);
        visibleChunks = occludedChunks = 0;
        int rw = Math.max(1, Math.round(w * settings.renderScale));
        int rh = Math.max(1, Math.round(h * settings.renderScale));
        if (rw != width || rh != height) { resize(rw, rh); temporal.reset(); }
        temporal.begin(projection, view, camera, width, height, w, h);
        glBindFramebuffer(GL_FRAMEBUFFER, hdrFbo);
        glViewport(0, 0, width, height);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);
        sky.bind();
        atmosphere.bind(sky);
        sky.setMatrix4("uInverseViewProjection", new Matrix4f().translation(temporal.jitterX(width), temporal.jitterY(height), 0).mul(projection).mul(view).invert());
        sky.setMatrix4("uInverseProjection",new Matrix4f().translation(temporal.jitterX(width),temporal.jitterY(height),0).mul(projection).invert());
        sky.setMatrix4("uInverseView",new Matrix4f(view).invert());
        sky.setVector3("uCameraPosition", camera.x, camera.y, camera.z);
        sky.setVector3("uSunDirection", sun.x, sun.y, sun.z);
        sky.setFloat("uDaylight", daylight);
        sky.setFloat("uAmbient", ambient);
        sky.setInt("uIsometric", isometric ? 1 : 0);
        sky.setInt("uClouds", settings.clouds ? 1 : 0);
        sky.setFloat("uCloudCoverage", settings.cloudCoverage);
        sky.setFloat("uTime", (float)(System.nanoTime()/1e9 % 10000));
        draw();
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        far.bind();
        far.setMatrix4("uProjection", projection);
        far.setMatrix4("uView", view);
        far.setVector3("uLightDirection", -sun.x, -sun.y, -sun.z);
        far.setVector3("uCameraPosition", camera.x, camera.y, camera.z);
        far.setFloat("uDaylight", daylight);
        far.setFloat("uAmbient", ambient);
        far.setInt("uFog", 1);
        atmosphere.bind(far);
        far.setInt("uEnvironment", 0);
        far.setFloat("uJitterX", temporal.jitterX(width));
        far.setFloat("uJitterY", temporal.jitterY(height));
        voxel.bind();
        voxel.setFloat("uJitterX", temporal.jitterX(width));
        voxel.setFloat("uJitterY", temporal.jitterY(height));
        voxel.setVector3("uCameraPosition", camera.x, camera.y, camera.z);
        bindSceneLighting(voxel);
        probes.bind(voxel);
        glActiveTexture(GL_TEXTURE0);
    }

    private void bindSceneLighting(ShaderProgram shader) {
        atmosphere.bind(shader);
        shader.setFloat("uDaylight", daylight);
        shader.setFloat("uAmbient", ambient);
        shader.setInt("uLightingEnabled", 1);
        shader.setInt("uHasIrradiance", hasIrradiance ? 1 : 0);
        shader.setInt("uShadowEnabled", 1);
        shader.setInt("uHeld", 0);shader.setInt("uOutputTone",0);
        shader.setFloat("uModelEmission", 0);
        shader.setVector3("uLightDirection", -sun.x, -sun.y, -sun.z);
        for (int i=0;i<3;i++) shader.setMatrix4("uShadowMatrix["+i+"]", shadowMatrices[i]);
        if (volume != null) {
            shader.setVector3("uVolumeOrigin", volume.x, volume.y, volume.z);
            shader.setVector3("uVolumeSize", volume.width, volume.height, volume.length);
        }
        bind(0, GL_TEXTURE_CUBE_MAP, environment);
        bind(1, GL_TEXTURE_2D_ARRAY, shadowTexture);
        bind(2, GL_TEXTURE_2D_ARRAY, materials.id);
        bind(3, GL_TEXTURE_3D, irradiance);
        shader.setInt("uEnvironment", 0);
        shader.setInt("uShadow", 1);
        shader.setInt("uMaterials", 2);
        shader.setInt("uIrradiance", 3);
        bind(4, GL_TEXTURE_3D, fineRoots);
        bind(5, GL_TEXTURE_BUFFER, fineLight);
        shader.setInt("uFineRoots", 4);
        shader.setInt("uFineLight", 5);
        transport.bind(shader); clusters.bind(shader);
    }

    public void distant(
            DistantTerrainRenderer renderer,
            FrustumIntersection frustum,
            java.util.Set<ChunkPos> detailed,
            ShaderProgram voxel) {
        far.bind();
        renderer.render(far, frustum, detailed);
        voxel.bind();
    }

    public void water(World world,VoxelModelRenderer models,Matrix4f projection,Matrix4f view,Vector3f eye) {
        water.capture(world,models,projection,view,eye,width,height,environment,sun,ambient,daylight);
        if (!water.found())return;
        glBindFramebuffer(GL_READ_FRAMEBUFFER,hdrFbo);glBindFramebuffer(GL_DRAW_FRAMEBUFFER,resolveFbo);
        glBlitFramebuffer(0,0,width,height,0,0,width,height,GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT,GL_NEAREST);
        glBindFramebuffer(GL_FRAMEBUFFER,hdrFbo);glViewport(0,0,width,height);
        glEnable(GL_DEPTH_TEST);glEnable(GL_CULL_FACE);
        water.render(world,projection,view,eye,resolveTexture,resolveDepth,environment,width,height,temporal.jitterX(width),temporal.jitterY(height));
    }

    public void finish() {
        particles.render(viewProjection,height);
        glBindFramebuffer(GL_READ_FRAMEBUFFER, hdrFbo);
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, resolveFbo);
        glBlitFramebuffer(
                0, 0, width, height, 0, 0, width, height,
                GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT, GL_NEAREST);
        hiZ.build(resolveDepth, width, height, viewProjection);
        temporal.finish(resolveTexture, resolveDepth, sun, daylight, ambient);
        frameBudget.end();
    }

    public void setColourLut(ColourLut lut) { temporal.setLut(lut); temporal.reset(); }
    public boolean reflectionProbeReady() { return probes.ready(); }
    public void addDecal(Decal decal) { temporal.addDecal(decal); }
    public void clearDecals() { temporal.clearDecals(); }
    public int particleCount() { return particles.count(); }
    public float gpuMillis() { return frameBudget.milliseconds(); }
    public boolean gpuDriven() { return gpuDraw.enabled(); }
    public void chunk(Chunk chunk, ChunkPos p) {
        if (chunk.getMesh() == null) return;
        float x=p.chunkX()*16,y=p.chunkY()*16,z=p.chunkZ()*16;
        if (!hiZ.visible(viewProjection,x,y,z)) { occludedChunks++; return; }
        visibleChunks++; gpuDraw.draw(chunk.getMesh(),viewProjection,x,y,z);
    }

    public void prepareHeld(ShaderProgram shader) {
        shader.bind();
        // The grading pass uses unit 4 for a floating-point LUT; restore integer fine roots.
        bind(3,GL_TEXTURE_3D,irradiance);bind(4,GL_TEXTURE_3D,fineRoots);bind(5,GL_TEXTURE_BUFFER,fineLight);
        glActiveTexture(GL_TEXTURE0);
        shader.setFloat("uJitterX",0);shader.setFloat("uJitterY",0);
        shader.setInt("uOutputTone",1);shader.setFloat("uOutputExposure",exposure());
    }

    public void atmosphere(dev.jayms.net.atmosphere.AtmosphereConfig config) { atmosphere.profile(config); temporal.reset(); }
    public double atmosphereRebuildMillis() { return atmosphere.rebuildCpuMillis; }
    public boolean atmosphereRebuilding() { return atmosphere.rebuilding(); }
    public void resetHistory() { temporal.reset(); }
    public float exposure() { return temporal.exposure(); }
    public int historyFrames() { return temporal.historyFrames(); }

    private void draw() {
        glBindVertexArray(vao);
        glDrawArrays(GL_TRIANGLES, 0, 3);
        glBindVertexArray(0);
    }

    private static void bind(int unit, int target, int texture) {
        glActiveTexture(GL_TEXTURE0 + unit);
        glBindTexture(target, texture);
    }

    private static void check() {
        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE)
            throw new IllegalStateException("Incomplete lighting framebuffer");
    }

    private void resize(int w, int h) {
        deleteTargets();
        width = w;
        height = h;
        hdrFbo = glGenFramebuffers();
        hdrColor = glGenRenderbuffers();
        hdrDepth = glGenRenderbuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, hdrFbo);
        glBindRenderbuffer(GL_RENDERBUFFER, hdrColor);
        if (samples > 1)
            glRenderbufferStorageMultisample(GL_RENDERBUFFER, samples, GL_RGBA16F, w, h);
        else glRenderbufferStorage(GL_RENDERBUFFER, GL_RGBA16F, w, h);
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_RENDERBUFFER, hdrColor);
        glBindRenderbuffer(GL_RENDERBUFFER, hdrDepth);
        if (samples > 1)
            glRenderbufferStorageMultisample(GL_RENDERBUFFER, samples, GL_DEPTH_COMPONENT24, w, h);
        else glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT24, w, h);
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, hdrDepth);
        check();
        resolveFbo = glGenFramebuffers();
        resolveTexture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, resolveTexture);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA16F, w, h, 0, GL_RGBA, GL_FLOAT, (ByteBuffer) null);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glBindFramebuffer(GL_FRAMEBUFFER, resolveFbo);
        glFramebufferTexture2D(
                GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, resolveTexture, 0);
        resolveDepth = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, resolveDepth);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_DEPTH_COMPONENT24, w, h, 0, GL_DEPTH_COMPONENT, GL_FLOAT, (ByteBuffer)null);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, resolveDepth, 0);
        check();
    }

    private void deleteTargets() {
        glDeleteFramebuffers(hdrFbo);
        glDeleteFramebuffers(resolveFbo);
        glDeleteRenderbuffers(hdrColor);
        glDeleteRenderbuffers(hdrDepth);
        glDeleteTextures(resolveTexture);
        glDeleteTextures(resolveDepth);
    }

    @Override
    public void close() {
        lighting.close();
        hiZ.close(); gpuDraw.close(); frameBudget.close(); transport.close(); clusters.close(); particles.close(); probes.close(); water.close();
        deleteTargets();
        glDeleteTextures(environment);
        glDeleteTextures(irradiance);
        glDeleteTextures(fineRoots);
        glDeleteTextures(fineLight);
        glDeleteBuffers(fineBuffer);
        glDeleteTextures(shadowTexture);
        glDeleteFramebuffers(shadowFbo);
        glDeleteVertexArrays(vao);
        materials.close();
        atmosphere.close();
        sky.close();
        far.close();
        temporal.close();
        shadow.close();
    }
}
