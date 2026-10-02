package dev.jayms.render;

import static org.lwjgl.opengl.GL33.*;

import dev.jayms.*;

import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

import java.nio.*;

/** OpenGL 3.3 HDR daylight, shadows, sky reflections and multisample/FXAA resolve. */
public final class RenderPipeline implements AutoCloseable {
    public static final Vector3f SUN = new Vector3f(.45f, .78f, -.45f).normalize();
    private final ShaderProgram sky =
            new ShaderProgram("shaders/fullscreen.vert", "shaders/sky.frag");
    private final ShaderProgram far =
            new ShaderProgram("shaders/voxel.vert", "shaders/distant.frag");
    private final ShaderProgram post =
            new ShaderProgram("shaders/fullscreen.vert", "shaders/post.frag");
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
    private int hdrFbo, hdrColor, hdrDepth, resolveFbo, resolveTexture, width, height;
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
        var light = Daylight.at(config, elapsed);
        sun.set(light.sun());
        daylight = light.intensity();
        ambient = light.ambient();
        int phase = (int) (config.hour(elapsed) * 4);
        if (config.cycle() && phase != environmentPhase) {
            environmentPhase = phase;
            updateEnvironment();
        }
    }

    private final Matrix4f shadowMatrix = new Matrix4f();

    public RenderPipeline() {
        updateEnvironment();
        glBindTexture(GL_TEXTURE_2D, shadowTexture);
        glTexImage2D(
                GL_TEXTURE_2D,
                0,
                GL_DEPTH_COMPONENT24,
                shadowResolution,
                shadowResolution,
                0,
                GL_DEPTH_COMPONENT,
                GL_FLOAT,
                (ByteBuffer) null);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glBindFramebuffer(GL_FRAMEBUFFER, shadowFbo);
        glFramebufferTexture2D(
                GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, shadowTexture, 0);
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
                        + (samples > 1 ? samples + "x MSAA" : "FXAA")
                        + ", HDR, voxel indirect lighting, sun shadows, sky reflections");
    }

    private void updateEnvironment() {
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_CUBE_MAP, environment);
        FloatBuffer data = MemoryUtil.memAllocFloat(128 * 128 * 3);
        try {
            for (int face = 0; face < 6; face++) {
                data.clear();
                for (int y = 0; y < 128; y++)
                    for (int x = 0; x < 128; x++) {
                        float a = (x + .5f) / 64 - 1, b = (y + .5f) / 64 - 1;
                        Vector3f d =
                                switch (face) {
                                    case 0 -> new Vector3f(1, -b, -a);
                                    case 1 -> new Vector3f(-1, -b, a);
                                    case 2 -> new Vector3f(a, 1, b);
                                    case 3 -> new Vector3f(a, -1, -b);
                                    case 4 -> new Vector3f(a, -b, 1);
                                    default -> new Vector3f(-a, -b, -1);
                                };
                        d.normalize();
                        float t = Math.max(0, d.y),
                                disc =
                                        (float) Math.pow(Math.max(0, d.dot(sun)), 512)
                                                * 3
                                                * daylight;
                        data.put((.32f * (1 - t) + .045f * t) * ambient + disc)
                                .put((.53f * (1 - t) + .18f * t) * ambient + disc * .9f)
                                .put((.8f * (1 - t) + .48f * t) * ambient + disc * .65f);
                    }
                data.flip();
                glTexImage2D(
                        GL_TEXTURE_CUBE_MAP_POSITIVE_X + face,
                        0,
                        GL_RGB16F,
                        128,
                        128,
                        0,
                        GL_RGB,
                        GL_FLOAT,
                        data);
            }
        } finally {
            MemoryUtil.memFree(data);
        }
        glGenerateMipmap(GL_TEXTURE_CUBE_MAP);
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_R, GL_CLAMP_TO_EDGE);
    }

    public boolean lightingReady() {
        return hasIrradiance;
    }

    public void update(World world, float x, float z) {
        LightVolume next = lighting.update(world, x, z, Math.round(ambient * 10) / 10f);
        if (next == null) return;
        volume = next;
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
        Vector3f center =
                new Vector3f(
                        (float) Math.floor(position.x * shadowResolution / 160)
                                * 160
                                / shadowResolution,
                        24,
                        (float) Math.floor(position.z * shadowResolution / 160)
                                * 160
                                / shadowResolution);
        Matrix4f lightView =
                new Matrix4f()
                        .lookAt(
                                new Vector3f(center)
                                        .fma(
                                                180,
                                                daylight > .01f ? sun : new Vector3f(sun).negate()),
                                center,
                                new Vector3f(0, 1, 0));
        Matrix4f lightProjection = new Matrix4f().ortho(-80, 80, -80, 80, 1, 360);
        shadowMatrix.set(lightProjection).mul(lightView);
        FrustumIntersection f = new FrustumIntersection(shadowMatrix);
        glBindFramebuffer(GL_FRAMEBUFFER, shadowFbo);
        glViewport(0, 0, shadowResolution, shadowResolution);
        glClear(GL_DEPTH_BUFFER_BIT);
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        shadow.bind();
        shadow.setMatrix4("uProjection", lightProjection);
        shadow.setMatrix4("uView", lightView);
        shadow.setInt("uInstanced", 0);
        for (var entry : world.getLoadedChunks().entrySet()) {
            var p = entry.getKey();
            if (entry.getValue().getMesh() == null
                    || !f.testAab(
                            p.chunkX() * 16,
                            p.chunkY() * 16,
                            p.chunkZ() * 16,
                            p.chunkX() * 16 + 16,
                            p.chunkY() * 16 + 16,
                            p.chunkZ() * 16 + 16)) continue;
            shadow.setMatrix4(
                    "uModel",
                    new Matrix4f().translation(p.chunkX() * 16, p.chunkY() * 16, p.chunkZ() * 16));
            entry.getValue().getMesh().render();
        }
        models.render(world, f, shadow);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    public void begin(
            int w,
            int h,
            Matrix4f projection,
            Matrix4f view,
            Vector3f camera,
            boolean isometric,
            ShaderProgram voxel) {
        if (w != width || h != height) resize(w, h);
        glBindFramebuffer(GL_FRAMEBUFFER, hdrFbo);
        glViewport(0, 0, w, h);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);
        sky.bind();
        sky.setMatrix4("uInverseViewProjection", new Matrix4f(projection).mul(view).invert());
        sky.setVector3("uCameraPosition", camera.x, camera.y, camera.z);
        sky.setVector3("uSunDirection", sun.x, sun.y, sun.z);
        sky.setFloat("uDaylight", daylight);
        sky.setFloat("uAmbient", ambient);
        sky.setInt("uIsometric", isometric ? 1 : 0);
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
        far.setInt("uFog", isometric ? 0 : 1);
        far.setInt("uEnvironment", 0);
        voxel.bind();
        voxel.setVector3("uCameraPosition", camera.x, camera.y, camera.z);
        voxel.setFloat("uDaylight", daylight);
        voxel.setFloat("uAmbient", ambient);
        voxel.setInt("uLightingEnabled", 1);
        voxel.setInt("uHasIrradiance", hasIrradiance ? 1 : 0);
        voxel.setInt("uShadowEnabled", 1);
        voxel.setInt("uHeld", 0);
        voxel.setFloat("uModelEmission", 0);
        voxel.setVector3("uLightDirection", -sun.x, -sun.y, -sun.z);
        voxel.setMatrix4("uShadowMatrix", shadowMatrix);
        if (volume != null) {
            voxel.setVector3("uVolumeOrigin", volume.x, volume.y, volume.z);
            voxel.setVector3("uVolumeSize", volume.width, volume.height, volume.length);
        }
        bind(0, GL_TEXTURE_CUBE_MAP, environment);
        bind(1, GL_TEXTURE_2D, shadowTexture);
        bind(2, GL_TEXTURE_2D_ARRAY, materials.id);
        bind(3, GL_TEXTURE_3D, irradiance);
        voxel.setInt("uEnvironment", 0);
        voxel.setInt("uShadow", 1);
        voxel.setInt("uMaterials", 2);
        voxel.setInt("uIrradiance", 3);
        bind(4, GL_TEXTURE_3D, fineRoots);
        bind(5, GL_TEXTURE_BUFFER, fineLight);
        voxel.setInt("uFineRoots", 4);
        voxel.setInt("uFineLight", 5);
        glActiveTexture(GL_TEXTURE0);
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

    public void finish() {
        glBindFramebuffer(GL_READ_FRAMEBUFFER, hdrFbo);
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, resolveFbo);
        glBlitFramebuffer(
                0, 0, width, height, 0, 0, width, height, GL_COLOR_BUFFER_BIT, GL_NEAREST);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glViewport(0, 0, width, height);
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);
        post.bind();
        bind(0, GL_TEXTURE_2D, resolveTexture);
        post.setInt("uScene", 0);
        post.setInt("uFXAA", samples == 1 ? 1 : 0);
        draw();
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
    }

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
        check();
    }

    private void deleteTargets() {
        glDeleteFramebuffers(hdrFbo);
        glDeleteFramebuffers(resolveFbo);
        glDeleteRenderbuffers(hdrColor);
        glDeleteRenderbuffers(hdrDepth);
        glDeleteTextures(resolveTexture);
    }

    @Override
    public void close() {
        lighting.close();
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
        sky.close();
        far.close();
        post.close();
        shadow.close();
    }
}
