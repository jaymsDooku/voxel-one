package dev.jayms.render;

import dev.jayms.ShaderProgram;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;
import java.nio.ByteBuffer;
import static org.lwjgl.opengl.GL33.*;

/** Depth-reprojected HDR history and temporal upscaling. UI is drawn after this pass. */
public final class TemporalPost implements AutoCloseable {
    private final ShaderProgram effects = new ShaderProgram("shaders/fullscreen.vert", "shaders/effects.frag");
    private final ShaderProgram tone = new ShaderProgram("shaders/fullscreen.vert", "shaders/tonemap.frag");
    private final int vao = glGenVertexArrays(), lutTexture = glGenTextures();
    private int lutSize;
    private final int[] fbo = new int[2], color = new int[2], depth = new int[2];
    private final Matrix4f previous = new Matrix4f(), current = new Matrix4f();
    private final Vector3f camera = new Vector3f(), previousCamera = new Vector3f(Float.POSITIVE_INFINITY);
    private int width, height, index, frame;
    private boolean valid;
    private float exposure = 1;
    private long lastTime;
    public final RenderSettings settings;
    private final java.util.List<Decal> decals = new java.util.ArrayList<>();
    public void addDecal(Decal decal) { if(decals.size()==16)decals.remove(0);decals.add(java.util.Objects.requireNonNull(decal));reset(); }
    public void clearDecals() { decals.clear();reset(); }
    public TemporalPost(RenderSettings settings) { this.settings = settings; setLut(ColourLut.identity(16)); }
    public void setLut(ColourLut lut) {
        lutSize = lut.size(); glBindTexture(GL_TEXTURE_3D, lutTexture);
        glTexImage3D(GL_TEXTURE_3D,0,GL_RGB16F,lutSize,lutSize,lutSize,0,GL_RGB,GL_FLOAT,lut.rgb());
        glTexParameteri(GL_TEXTURE_3D,GL_TEXTURE_MIN_FILTER,GL_LINEAR); glTexParameteri(GL_TEXTURE_3D,GL_TEXTURE_MAG_FILTER,GL_LINEAR);
        glTexParameteri(GL_TEXTURE_3D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE); glTexParameteri(GL_TEXTURE_3D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE); glTexParameteri(GL_TEXTURE_3D,GL_TEXTURE_WRAP_R,GL_CLAMP_TO_EDGE);
    }
    public float jitterX(int w) { return settings.taa ? (halton(frame + 1, 2) - .5f) * 2 / w : 0; }
    public float jitterY(int h) { return settings.taa ? (halton(frame + 1, 3) - .5f) * 2 / h : 0; }
    private static float halton(int n, int base) {
        float value = 0, f = 1;
        for (; n > 0; n /= base) { f /= base; value += f * (n % base); }
        return value;
    }
    public void begin(Matrix4f projection, Matrix4f view, Vector3f eye, int w, int h, int outW, int outH) {
        if (outW != width || outH != height) resize(outW, outH);
        camera.set(eye);
        current.identity().translation(jitterX(w), jitterY(h), 0).mul(projection).mul(view);
        if (camera.distanceSquared(previousCamera) > 64) valid = false;
    }
    public void reset() { valid = false; }
    public float exposure() { return exposure; }
    public int historyFrames() { return frame; }
    public void finish(int scene, int sceneDepth, Vector3f sun, float daylight, float ambient) {
        int target = 1 - index;
        glBindFramebuffer(GL_FRAMEBUFFER, fbo[target]);
        glViewport(0, 0, width, height);
        glDisable(GL_DEPTH_TEST); glDisable(GL_CULL_FACE);
        effects.bind();
        bind(0, scene); bind(1, sceneDepth); bind(2, color[index]); bind(3, depth[index]);
        effects.setInt("uScene", 0); effects.setInt("uDepth", 1);
        effects.setInt("uHistory", 2); effects.setInt("uHistoryDepth", 3);
        effects.setMatrix4("uInverseVP", new Matrix4f(current).invert());
        effects.setMatrix4("uVP", current); effects.setMatrix4("uPreviousVP", previous);
        effects.setVector3("uCamera", camera.x, camera.y, camera.z);
        effects.setVector3("uSun", sun.x, sun.y, sun.z);
        effects.setFloat("uDaylight", daylight); effects.setFloat("uAmbient", ambient);
        effects.setFloat("uFogDensity", settings.fogDensity);
        effects.setInt("uDecalCount",decals.size());
        for(int i=0;i<decals.size();i++){
            Decal d=decals.get(i);Vector3f p=d.center(),n=d.normal(),c=d.color();
            effects.setVector3("uDecalCenter["+i+"]",p.x,p.y,p.z);effects.setVector3("uDecalNormal["+i+"]",n.x,n.y,n.z);
            effects.setVector3("uDecalColor["+i+"]",c.x,c.y,c.z);effects.setFloat("uDecalRadius["+i+"]",d.radius());
            effects.setFloat("uDecalDepth["+i+"]",d.depth());effects.setFloat("uDecalOpacity["+i+"]",d.opacity());
        }
        effects.setInt("uFrame", frame); effects.setInt("uTAA", settings.taa && valid ? 1 : 0);
        effects.setInt("uAO", settings.ao ? 1 : 0);
        effects.setInt("uContact", settings.contactShadows ? 1 : 0);
        effects.setInt("uSSR", settings.reflections ? 1 : 0);
        effects.setInt("uGI", settings.screenGi ? 1 : 0);
        effects.setInt("uVolume", settings.volumetrics ? 1 : 0);
        draw();
        glBindTexture(GL_TEXTURE_2D, color[target]); glGenerateMipmap(GL_TEXTURE_2D);
        long now = System.nanoTime();
        float dt = lastTime == 0 ? 1 / 60f : Math.min(.1f, (now - lastTime) / 1e9f);
        lastTime = now;
        if (settings.autoExposure) {
            int level = 31 - Integer.numberOfLeadingZeros(Math.max(width, height));
            try (MemoryStack stack = MemoryStack.stackPush()) {
                var rgb = stack.mallocFloat(4);
                glGetTexImage(GL_TEXTURE_2D, level, GL_RGBA, GL_FLOAT, rgb);
                float luminance = rgb.get(0) * .2126f + rgb.get(1) * .7152f + rgb.get(2) * .0722f;
                float goal = Math.max(.25f, Math.min(4, .35f / Math.max(.001f, luminance)));
                if (Float.isFinite(goal)) exposure += (goal - exposure) * (1 - (float)Math.exp(-dt * 1.5f));
            }
        } else exposure = settings.exposure;
        glBindFramebuffer(GL_FRAMEBUFFER, 0); glViewport(0, 0, width, height);
        tone.bind(); bind(0, color[target]);
        glActiveTexture(GL_TEXTURE4); glBindTexture(GL_TEXTURE_3D,lutTexture);
        tone.setInt("uLut",4); tone.setFloat("uLutSize",lutSize);
        tone.setInt("uScene", 0); tone.setInt("uBloom", settings.bloom ? 1 : 0);
        tone.setFloat("uExposure", exposure); tone.setFloat("uSaturation", settings.saturation);
        tone.setFloat("uContrast", settings.contrast);
        draw();
        index = target; previous.set(current); previousCamera.set(camera); valid = true; frame++;
        glEnable(GL_DEPTH_TEST); glEnable(GL_CULL_FACE); glActiveTexture(GL_TEXTURE0);
    }
    private static void bind(int unit, int texture) {
        glActiveTexture(GL_TEXTURE0 + unit); glBindTexture(GL_TEXTURE_2D, texture);
    }
    private void draw() { glBindVertexArray(vao); glDrawArrays(GL_TRIANGLES, 0, 3); glBindVertexArray(0); }
    private void resize(int w, int h) {
        delete(); width = w; height = h; valid = false;
        for (int i = 0; i < 2; i++) {
            fbo[i] = glGenFramebuffers(); glBindFramebuffer(GL_FRAMEBUFFER, fbo[i]);
            color[i] = texture(GL_RGBA16F, GL_RGBA, GL_FLOAT);
            depth[i] = texture(GL_R32F, GL_RED, GL_FLOAT);
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, color[i], 0);
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT1, GL_TEXTURE_2D, depth[i], 0);
            glDrawBuffers(new int[]{GL_COLOR_ATTACHMENT0, GL_COLOR_ATTACHMENT1});
            if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE)
                throw new IllegalStateException("Incomplete temporal framebuffer");
            glClearBufferfv(GL_COLOR, 0, new float[]{0,0,0,1});
            glClearBufferfv(GL_COLOR, 1, new float[]{1,1,1,1});
        }
    }
    private int texture(int internal, int format, int type) {
        int id = glGenTextures(); glBindTexture(GL_TEXTURE_2D, id);
        glTexImage2D(GL_TEXTURE_2D, 0, internal, width, height, 0, format, type, (ByteBuffer)null);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        return id;
    }
    private void delete() {
        for (int i = 0; i < 2; i++) { glDeleteFramebuffers(fbo[i]); glDeleteTextures(color[i]); glDeleteTextures(depth[i]); }
    }
    @Override public void close() { delete(); glDeleteTextures(lutTexture); effects.close(); tone.close(); glDeleteVertexArrays(vao); }
}
