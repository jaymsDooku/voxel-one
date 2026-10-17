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
    public final RenderSettings settings = new RenderSettings();
    private final PlanetAtmosphere atmosphere = new PlanetAtmosphere();
    private final TemporalPost temporal = new TemporalPost(settings);
    private final HiZ hiZ = new HiZ();
    private final WaterRenderer water = new WaterRenderer();
    private final ReflectionProbes probes;
    private final ShaderProgram sharedVoxel;
    private final ClusteredLights clusters = new ClusteredLights();
    private final GpuParticles particles = new GpuParticles();
    private VoxelTransport transport = new VoxelTransport();
    private final FrameBudget frameBudget = new FrameBudget();
    private final GpuDraw gpuDraw = new GpuDraw();
    private final Matrix4f viewProjection = new Matrix4f();
    private long worldRevision = -1,sceneGeneration;
    private record ShadowRevision(World world,long edits,long scene,long models,int cascades,int distance,int x,int z,float y,float sunX,float sunY,float sunZ,boolean night){}
    private ShadowRevision shadowRevision;
    private WorldSkyVisibility worldSky;
    private int skyBufferOffset;
    private World sceneWorld;
    private long sceneRevision=Long.MIN_VALUE;
    public int visibleChunks, occludedChunks;
    private final ShaderProgram shadow =
            new ShaderProgram("shaders/shadow.vert", "shaders/shadow.frag");
    private final MaterialTextures materials = new MaterialTextures();
    private final WorldLighting lighting = new WorldLighting();
    private final SkyVisibilityScheduler skyVisibility=new SkyVisibilityScheduler();
    private final int vao = glGenVertexArrays(),
            environment = glGenTextures(),
            shadowTexture = glGenTextures(),
            shadowFbo = glGenFramebuffers();
    private int irradiance = glGenTextures(),
            fineRoots = glGenTextures(),
            fineLight = glGenTextures(),
            fineBuffer = glGenBuffers();
    private int hdrFbo, hdrColor, hdrDepth, resolveFbo, resolveTexture, resolveDepth, width, height;
    private final boolean software =
            glGetString(GL_RENDERER)
                    .toLowerCase(java.util.Locale.ROOT)
                    .matches(".*(llvmpipe|softpipe|swrast|software).*");
    public int samples =
            Math.max(
                    1,
                    Math.min(
                            Math.min(4, Integer.getInteger("voxel.msaa", settings.taa ? 1 : software ? 1 : 4)),
                            Math.min(
                                    glGetInteger(GL_MAX_SAMPLES),
                                    glGetInteger(GL_MAX_COLOR_TEXTURE_SAMPLES))));
    private int shadowResolution = software ? 1024 : 2048;
    private boolean hasIrradiance,activeRootBuffer;
    // Reuse the existing integer-buffer sampler; all16 fragment units stay supported.
    private final boolean separateRootBuffer=glGetInteger(GL_MAX_TEXTURE_BUFFER_SIZE)>=1_048_576+96*128*96;
    private LightVolume volume,desiredVolume;
    private SkyVisibilityScheduler.Prepared desiredSky;
    private SceneLightingUpload lightingUpload,skyLightingUpload;
    private SkyVisibilityScheduler.Prepared activeSky;
    private long skyUploadStarted;public long skyGpuBytes,skyGpuNanos;
    private WorldLighting.Capture desiredVolumeFence,activeVolumeFence;
    private boolean lightingBootstrapped,initialLightingFrame;
    private int initialLightingUpdates;
    private World focusWorld;private float previousFocusX,previousFocusZ,motionX,motionZ,requestedX,requestedZ;
    public long focusNanos,ownerBudgetNanos=FrameBudget.STREAMING_CPU_NANOS;
    private boolean giFirst;
    private int lightingPriorityTurn;
    private long previousFocusTime,lastVolumeLatency;private boolean focusPrepared;
    public void prepareFocus(World world,float x,float z){
        long now=System.nanoTime();float dx=focusWorld==world?x-previousFocusX:0,dz=focusWorld==world?z-previousFocusZ:0;
        double elapsed=previousFocusTime==0?0:(now-previousFocusTime)/1e9;
        if(Math.abs(dx)>64||Math.abs(dz)>64){dx=dz=0;}
        motionX=dx;motionZ=dz;double horizon=Math.max(.25,lastVolumeLatency/1e9)*1.5;
        float vx=elapsed>0?(float)(dx/elapsed):0,vz=elapsed>0?(float)(dz/elapsed):0;
        requestedX=x+(Math.abs(vx)<.001f?0:Math.max(-80,Math.min(80,(float)(vx*horizon)+Math.copySign(24,vx))));
        requestedZ=z+(Math.abs(vz)<.001f?0:Math.max(-80,Math.min(80,(float)(vz*horizon)+Math.copySign(24,vz))));
        if(!SkyVisibilityScheduler.ceilingReady(desiredSky,world,requestedX,requestedZ)&&SkyVisibilityScheduler.ceilingReady(desiredSky,world,x,z)){requestedX=x;requestedZ=z;}
        // Bound lead relative to the current camera. Clamping against the OLD
        // field center can request terrain behind a moving camera near its edge.
        if(volume!=null&&x>=volume.x&&x<volume.x+volume.width&&z>=volume.z&&z<volume.z+volume.length&&activeVolumeFence!=null&&activeVolumeFence.valid(world,activeVolumeFence.x,activeVolumeFence.z)){
            requestedX=Math.max(x-32,Math.min(x+32,requestedX));
            requestedZ=Math.max(z-32,Math.min(z+32,requestedZ));
        }else{requestedX=x;requestedZ=z;} // Missing/stale coverage needs the camera's area first.
        focusWorld=world;previousFocusX=x;previousFocusZ=z;previousFocusTime=now;focusPrepared=true;
        boolean ready=nearLightingReady(world,x,z);
        // A stationary camera does not need a new anchor while its neighborhood
        // is already covered by a current field.
        if(ready&&motionX==0&&motionZ==0){requestedX=activeVolumeFence.x;requestedZ=activeVolumeFence.z;}
        boolean uploading=desiredVolume!=null&&desiredVolume!=volume||skyWorkPending();
        ownerBudgetNanos=initialLightingBudget()?FrameBudget.INITIAL_LIGHTING_CPU_NANOS:ready&&!uploading?FrameBudget.STREAMING_CPU_NANOS:FrameBudget.CRITICAL_LIGHTING_CPU_NANOS;
        focusNanos=System.nanoTime()-now;
    }
    /** First playable area and per-frame budget use the same bounded readiness check. */
    public boolean nearLightingReady(World world,float x,float z){
        if(!hasIrradiance||!nearCovered(volume,x,z)||activeVolumeFence==null||!activeVolumeFence.valid(world,activeVolumeFence.x,activeVolumeFence.z)||worldSky==null||activeSky!=null&&!activeSky.belongsTo(world))return false;
        for(int a=-1;a<=1;a++)for(int b=-1;b<=1;b++)if(worldSky.sampleKnown(x+a*24,110,z+b*24,-1)<0)return false;
        return true;
    }
    public void resetFocus(){focusWorld=null;previousFocusTime=0;focusPrepared=false;motionX=motionZ=0;}
    private boolean skyWorkPending(){return skyVisibility.pending()>0||skyLightingUpload!=null||desiredSky!=null&&desiredSky.data!=worldSky;}
    private static boolean nearCovered(LightVolume v,float x,float z){return v!=null&&x-24>=v.x&&x+24<v.x+v.width&&z-24>=v.z&&z+24<v.z+v.length;}
    public long skyUploadPreemptions,obsoleteVolumes,reusedCurrentVolumes;
    private final LightingStores lightingStores=new LightingStores();
    private long activeLightingBytes;
    public long lightingUploadBytes,lightingStoreAllocations,lightingStoreNanos;
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

    public RenderPipeline(){this(null);}
    public RenderPipeline(GraphicsProfile profile) {this(profile,null);}
    public RenderPipeline(GraphicsProfile profile,ShaderProgram sharedVoxel) {
        this.sharedVoxel=sharedVoxel;
        probes=new ReflectionProbes(sharedVoxel);
        try {
        lightingStores.warmDefaults();
        if(profile==null){var defaults=GraphicsProfile.launchDefaults().profile();settings.apply(defaults);samples=Math.max(1,Math.min(defaults.samples(),glGetInteger(GL_MAX_SAMPLES)));}
        if(profile!=null){settings.apply(profile);samples=profile.samples();shadowResolution=profile.shadowSize();materials.configure(profile.get(GraphicsProfile.Key.TEXTURE).equals("PIXEL"),profile.on(GraphicsProfile.Key.MIPMAP),profile.number(GraphicsProfile.Key.ANISOTROPY));}
        atmosphere.quality(settings.atmosphereQuality);
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
        } catch(RuntimeException e) { close(); throw e; }
    }

    private long atmosphereEnvironmentRevision;
    private void updateEnvironment() {
        atmosphere.environment(environment);
        atmosphereEnvironmentRevision=atmosphere.revision;
    }

    public boolean initialLightingBudget(){return !lightingBootstrapped&&initialLightingUpdates<FrameBudget.INITIAL_LIGHTING_UPDATES;}
    public boolean lightingReady() {
        return hasIrradiance;
    }

    /** World mutations carry a stamp; do not scan/copy every resident chunk per frame. */
    private boolean sceneChanged(World world) {
        if(sceneWorld==world&&sceneRevision==world.sceneRevision())return false;
        sceneWorld=world;sceneRevision=world.sceneRevision();sceneGeneration++;
        return true;
    }

    /** Diagnostic only: age is since source capture, not a claim of stale geometry. */
    public record LightingCoverage(int coveredOfNine,boolean skyKnown,long sourceAgeMillis,boolean current,long submitted,long completed,long rejected,long pendingAgeMillis,int initialBudgetUpdates,boolean initialBudgetActive,boolean bakeDone,long workerId,long workerNanos,long workerCpuNanos) {}
    public LightingCoverage lightingCoverage(World world,float x,float z){
        int covered=0;for(int a=-1;a<=1;a++)for(int b=-1;b<=1;b++)if(volume!=null&&x+a*24>=volume.x&&x+a*24<volume.x+volume.width&&z+b*24>=volume.z&&z+b*24<volume.z+volume.length)covered++;
        var timing=lighting.workerTiming();return new LightingCoverage(covered,worldSky!=null&&worldSky.sampleKnown(x,110,z,-1)>=0,activeVolumeFence==null?-1:(System.nanoTime()-activeVolumeFence.created)/1_000_000,activeVolumeFence!=null&&activeVolumeFence.valid(world,activeVolumeFence.x,activeVolumeFence.z),lighting.submitted,lighting.completed,lighting.rejected,lighting.pendingAgeNanos()/1_000_000,initialLightingUpdates,initialLightingFrame,lighting.bakeDone(),timing.id(),timing.wallNanos(),timing.cpuNanos());
    }
    public record SkyProgress(int cached,long published,long partial,long rejected,boolean localReady,long preemptions,boolean ceilingReady,long obsoleteVolumes,boolean gpuPending,long gpuAgeMillis,long gpuBytes,long sourceAgeMillis) {}
    public SkyProgress skyProgress(World world,float x,float z){return new SkyProgress(skyVisibility.cachedColumns(),skyVisibility.published,skyVisibility.partialPublications,skyVisibility.rejected,desiredSky!=null&&desiredSky.localReady(world,x,z),skyUploadPreemptions,SkyVisibilityScheduler.ceilingReady(desiredSky,world,x,z),obsoleteVolumes,skyLightingUpload!=null,skyLightingUpload==null?0:(System.nanoTime()-skyUploadStarted)/1_000_000,skyGpuBytes,activeSky==null?-1:(System.nanoTime()-activeSky.createdNanos)/1_000_000);}
    public Object lightingSkyKey(World world,float x,float z){
        if(motionX==0&&motionZ==0&&nearLightingReady(world,x,z))return SkyVisibilityScheduler.ceilingKey(desiredSky,world,activeVolumeFence.x,activeVolumeFence.z);
        return SkyVisibilityScheduler.ceilingKey(desiredSky,world,x,z);
    }
    public int skyPending(){return skyVisibility.pending();}
    public long skyCacheBytes(){return skyVisibility.cachedBytes();}
    public long skyWorkerNanos(){return skyVisibility.workerNanos;}
    public long skyBuildNanos, lightingNanos, sceneNanos, lightingUploadNanos, shadowCpuNanos, probeCpuNanos;
    public void update(World world,float x,float z){update(world,x,z,FrameBudget.STREAMING_CPU_NANOS);}
    public void update(World world, float x, float z,long ownerNanos) {
        if(!focusPrepared)prepareFocus(world,x,z);
        initialLightingFrame=initialLightingBudget();if(initialLightingFrame)initialLightingUpdates++;
        long start=System.nanoTime(),allocations=lightingStores.allocations,allocationNanos=lightingStores.allocationNanos;
        skyBuildNanos=lightingNanos=sceneNanos=lightingUploadNanos=0;
        try{updateScene(world,x,z,start+Math.max(0,ownerNanos));}finally{lightingUploadNanos=Math.max(0,System.nanoTime()-start-skyBuildNanos-lightingNanos-sceneNanos);sceneNanos+=focusNanos;lightingStoreAllocations=lightingStores.allocations-allocations;lightingStoreNanos=lightingStores.allocationNanos-allocationNanos;focusPrepared=false;}
    }
    private void updateScene(World world,float x,float z,long deadline) {
        int trace=dev.jayms.OwnerTrace.begin("render.prepare");try{
        long stage=System.nanoTime();
        if(desiredVolume!=volume&&desiredVolume!=null){
            float cx=desiredVolume.x+48,cz=desiredVolume.z+48;boolean outside=x<desiredVolume.x||x>=desiredVolume.x+desiredVolume.width||z<desiredVolume.z||z>=desiredVolume.z+desiredVolume.length;
            boolean toward=(cx-x)*motionX+(cz-z)*motionZ>0;boolean oldGood=nearCovered(volume,x,z)&&activeVolumeFence!=null&&activeVolumeFence.valid(world,activeVolumeFence.x,activeVolumeFence.z);
            if(Math.abs(cx-x)>80||Math.abs(cz-z)>80||(outside&&!toward)||(!toward&&!nearCovered(desiredVolume,x,z)&&oldGood)||(motionX==0&&motionZ==0&&!nearCovered(desiredVolume,x,z)&&oldGood)){
                if(lightingUpload!=null&&lightingUpload.replaceVolume){lightingUpload.close();lightingUpload=null;}
                desiredVolume=volume;desiredVolumeFence=null;lighting.invalidateAccepted();obsoleteVolumes++;
            }
        }
        if (clusters.update(world,x,z,Math.min(deadline,stage+250_000L))) { particles.emitters(clusters.emitters()); probes.invalidate(); }
        boolean changed=sceneChanged(world) || worldRevision != world.editsVersion();
        sceneNanos=System.nanoTime()-stage;
        SkyVisibilityScheduler.Prepared rebuilt=null;
        boolean uncovered=volume==null||x<volume.x||x>=volume.x+volume.width||z<volume.z||z>=volume.z+volume.length;
        boolean missingNearGi=desiredVolume!=volume&&pendingRootBuffer()&&!nearCovered(volume,x,z);
        // Once a complete bake is waiting on GPU transfer, far sky capture must
        // not consume the budget needed to restore the camera's GI coverage.
        long skyAllowance=initialLightingFrame?3_000_000L:missingNearGi?250_000L:(!lightingBootstrapped||uncovered||skyWorkPending())?1_000_000L:250_000L;
        // Reserve half the remaining allowance for GI preparation and GPU progress.
        if(System.nanoTime()<deadline){rebuilt=skyVisibility.update(world,(x+requestedX)*.5f,(z+requestedZ)*.5f,Math.min(skyAllowance,Math.max(0,(deadline-System.nanoTime())/2)),initialLightingFrame);skyBuildNanos=skyVisibility.ownerNanos;}
        if(rebuilt!=null)desiredSky=rebuilt;
        if (changed) {
            hiZ.invalidate();temporal.reset();worldRevision = world.editsVersion();
        }
        stage=System.nanoTime();
        // Coalesce changing sky requests while the one available GPU generation uploads.
        // Baking replacements that cannot be installed only extends readiness latency.
        boolean volumeSlotFree=desiredVolume==volume&&(lightingUpload==null||!lightingUpload.replaceVolume);
        boolean localSkyReady=System.nanoTime()<deadline&&SkyVisibilityScheduler.ceilingReady(desiredSky,world,requestedX,requestedZ);
        LightVolume next = System.nanoTime()<deadline?lighting.updatePredicted(world,requestedX,requestedZ,x,z,1,true,desiredSky,Math.min(deadline,stage+250_000L),localSkyReady&&volumeSlotFree,volumeSlotFree):null;
        lightingNanos=System.nanoTime()-stage;
        if(next!=null){
            // Same origin and unchanged local dependencies produce identical data.
            // Keep the GPU field and its real capture age, not another full upload.
            if(volume!=null&&next.x==volume.x&&next.y==volume.y&&next.z==volume.z&&next.width==volume.width&&next.height==volume.height&&next.length==volume.length&&activeVolumeFence!=null&&activeVolumeFence.valid(world,activeVolumeFence.x,activeVolumeFence.z)){
                desiredVolume=volume;desiredVolumeFence=activeVolumeFence;reusedCurrentVolumes++;
            }else{desiredVolume=next;desiredVolumeFence=lighting.acceptedFence();}
        }
        if(desiredVolume!=volume&&(desiredVolumeFence==null||!desiredVolumeFence.valid(world,desiredVolumeFence.x,desiredVolumeFence.z)))desiredVolume=volume;
        pumpLighting(world,x,z,deadline);
        if(hasIrradiance&&skyVisibility.pending()==0&&desiredSky!=null&&desiredSky.data==worldSky&&desiredVolume==volume&&lighting.pendingAgeNanos()==0&&skyLightingUpload==null)lightingBootstrapped=true;

        }finally{dev.jayms.OwnerTrace.end(trace,0);}
    }
    private boolean pendingRootBuffer(){return separateRootBuffer&&desiredVolume!=null&&desiredVolume.fine.length<=1_048_576;}
    /** Independent sky lane shares time, bytes, scratch and two prewarmed atlas generations.
     * A valid in-flight atlas finishes even when new GI or disjoint sky tiles arrive. */
    private int pumpSky(World world,long deadline,int bytes){
        if(skyLightingUpload!=null&&!skyLightingUpload.valid(world)){skyLightingUpload.close();skyLightingUpload=null;}
        if(skyLightingUpload==null&&desiredSky!=null&&desiredSky.data!=worldSky&&desiredSky.valid(world)&&lightingStores.available(false)){
            skyLightingUpload=new SceneLightingUpload(world,null,desiredSky,false,lightingStores);skyUploadStarted=System.nanoTime();
        }
        if(skyLightingUpload==null)return 0;
        skyLightingUpload.refreshSky(world,desiredSky);int sent=skyLightingUpload.pump(deadline,bytes,256*1024);
        if(skyLightingUpload.ready()&&skyLightingUpload.valid(world)&&skyLightingUpload.skyCurrent(world)){
            var data=skyLightingUpload.take();lightingStores.releaseTexture(fineRoots);fineRoots=data.atlas();worldSky=skyLightingUpload.sky.data;activeSky=skyLightingUpload.sky;skyBufferOffset=skyLightingUpload.offset;
            probes.invalidate();skyLightingUpload.close();skyLightingUpload=null;
        }
        return sent;
    }
    private void pumpLighting(World world,float x,float z,long deadline) {
        int trace=dev.jayms.OwnerTrace.begin("lighting.update");try{
        lightingUploadBytes=skyGpuBytes=skyGpuNanos=0;
        if(System.nanoTime()>=deadline)return;
        int byteBudget=(initialLightingFrame?8:4)*1024*1024;
        boolean independentSky=activeRootBuffer&&(desiredVolume==volume||pendingRootBuffer());
        if(!independentSky&&skyLightingUpload!=null){skyLightingUpload.close();skyLightingUpload=null;}
        // Alternate first service: a single non-preemptible GL copy may consume its
        // share, but cannot prevent the other lane from starting on the next frame.
        boolean missingNearGi=desiredVolume!=volume&&pendingRootBuffer()&&!nearCovered(volume,x,z);
        boolean nearGiPriority=independentSky&&prioritizeNearGi(missingNearGi,++lightingPriorityTurn);
        // Three urgent turns may use the remaining slice; every fourth starts
        // sky first. Both lanes keep the same shared deadline and byte ceiling.
        boolean mainFirst=independentSky&&(missingNearGi?nearGiPriority:(giFirst=!giFirst));
        if(mainFirst)pumpMainLighting(world,x,z,nearGiPriority?deadline:Math.min(deadline,System.nanoTime()+Math.max(0,(deadline-System.nanoTime())/2)),byteBudget);
        if(independentSky&&System.nanoTime()<deadline){
            long start=System.nanoTime();
            long skyDeadline=mainFirst||desiredVolume==volume?deadline:Math.min(deadline,start+Math.max(0,(deadline-start)/2));
            skyGpuBytes=pumpSky(world,skyDeadline,Math.max(0,byteBudget-(int)lightingUploadBytes));
            skyGpuNanos=System.nanoTime()-start;lightingUploadBytes+=skyGpuBytes;
        }
        if(System.nanoTime()<deadline)pumpMainLighting(world,x,z,deadline,byteBudget);

        }finally{dev.jayms.OwnerTrace.end(trace,0);}
    }
    static boolean prioritizeNearGi(boolean missing,int turn){return missing&&(turn&3)!=0;}
    private void pumpMainLighting(World world,float x,float z,long deadline,int byteBudget){
        if(System.nanoTime()>=deadline)return;
        if(desiredVolume!=volume&&lightingUpload!=null&&!lightingUpload.replaceVolume){lightingUpload.close();lightingUpload=null;skyUploadPreemptions++;}
        if(lightingUpload!=null&&!lightingUpload.valid(world)){lightingUpload.close();lightingUpload=null;}
        if(lightingUpload==null&&(desiredVolume!=volume||(!activeRootBuffer&&desiredSky!=null&&desiredSky.data!=worldSky))&&((pendingRootBuffer()&&desiredVolume!=volume)||desiredSky==null||desiredSky.valid(world))&&lightingStores.available(desiredVolume!=volume,!(pendingRootBuffer()&&desiredVolume!=volume)))
            lightingUpload=new SceneLightingUpload(world,activeRootBuffer&&desiredVolume==volume?null:desiredVolume,pendingRootBuffer()&&desiredVolume!=volume?null:desiredSky,desiredVolume!=volume,lightingStores,desiredVolume!=volume?desiredVolumeFence:null,pendingRootBuffer()&&desiredVolume!=volume);
        if(lightingUpload==null)return;
        lightingUpload.refreshSky(world,desiredSky);
        lightingUploadBytes+=lightingUpload.pump(deadline,Math.max(0,byteBudget-(int)lightingUploadBytes));
        if(!lightingUpload.ready())return;
        if(!lightingUpload.valid(world)){lightingUpload.close();lightingUpload=null;return;}
        if(!lightingUpload.skyCurrent(world))return;
        if(lightingUpload.replaceVolume&&lightingUpload.volume!=null){
            var next=lightingUpload.volume;if(x<next.x||x>=next.x+next.width||z<next.z||z>=next.z+next.length)return;
            if(!nearCovered(next,x,z)&&nearCovered(volume,x,z)&&activeVolumeFence!=null&&activeVolumeFence.valid(world,activeVolumeFence.x,activeVolumeFence.z))return;
        }
        var resources=lightingUpload.take();
        if(!lightingUpload.separateRoots){lightingStores.releaseTexture(fineRoots);fineRoots=resources.atlas();}
        if(lightingUpload.replaceVolume){
            lightingStores.releaseTexture(irradiance);lightingStores.releaseTexture(fineLight);lightingStores.releaseBuffer(fineBuffer);transport.close();
            irradiance=resources.irradiance();fineLight=resources.fineTexture();fineBuffer=resources.fineBuffer();transport=resources.transport();
            activeRootBuffer=lightingUpload.separateRoots;volume=lightingUpload.volume;activeVolumeFence=desiredVolumeFence;if(activeVolumeFence!=null)lastVolumeLatency=System.nanoTime()-activeVolumeFence.created;hasIrradiance=volume!=null;
        }
        if(!lightingUpload.separateRoots){worldSky=lightingUpload.sky==null?null:lightingUpload.sky.data;activeSky=lightingUpload.sky;skyBufferOffset=lightingUpload.offset;}
        activeLightingBytes=(long)lightingUpload.width*lightingUpload.height*lightingUpload.depth*4+(volume==null?0:volume.rgba.length+volume.fine.length*4L+transport.estimatedBytes());
        probes.invalidate();lightingUpload.close();lightingUpload=null;glActiveTexture(GL_TEXTURE0);
    }

    /** Start before lighting uploads, shadows and probes. begin() remains a fallback for isolated renderers. */
    public void startFrame() { frameBudget.begin(settings); }

    public void renderShadows(World world, VoxelModelRenderer models, Vector3f position) {
        long start=System.nanoTime();probeCpuNanos=0;
        try{renderShadowsImpl(world,models,position);}finally{shadowCpuNanos=Math.max(0,System.nanoTime()-start-probeCpuNanos);}
    }
    private void renderShadowsImpl(World world,VoxelModelRenderer models,Vector3f position) {
        frameBudget.shadowsBegin();
        if(!settings.shadows){frameBudget.shadowsEnd();captureProbes(world,models,position);return;}
        float nearTexel=(settings.shadowCascades==1?settings.shadowDistance:Math.min(32,settings.shadowDistance))*2f/shadowResolution;
        var key=new ShadowRevision(world,world.editsVersion(),sceneGeneration,world.models().revision(),settings.shadowCascades,settings.shadowDistance,(int)Math.floor(position.x/nearTexel),(int)Math.floor(position.z/nearTexel),position.y,sun.x,sun.y,sun.z,daylight<=.01f);
        if(key.equals(shadowRevision)){frameBudget.shadowsEnd();captureProbes(world,models,position);return;}
        shadowRevision=key;
        glBindFramebuffer(GL_FRAMEBUFFER, shadowFbo);
        glViewport(0, 0, shadowResolution, shadowResolution);
        glEnable(GL_DEPTH_TEST); glEnable(GL_CULL_FACE);
        shadow.bind(); shadow.setInt("uInstanced", 0);
        float[] extents = {Math.min(32,settings.shadowDistance),Math.min(96,settings.shadowDistance),settings.shadowDistance};
        for (int cascade = 0; cascade < settings.shadowCascades; cascade++) {
            float extent = cascade==settings.shadowCascades-1?settings.shadowDistance:extents[cascade], texel = extent * 2 / shadowResolution;
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
        frameBudget.shadowsEnd();
        captureProbes(world,models,position);
    }
    private void captureProbes(World world,VoxelModelRenderer models,Vector3f position){
        long start=System.nanoTime();try{captureProbesImpl(world,models,position);}finally{probeCpuNanos=System.nanoTime()-start;}
    }
    private void captureProbesImpl(World world,VoxelModelRenderer models,Vector3f position){
        frameBudget.probesBegin();
        if (hasIrradiance && settings.reflections) probes.capture(world,models,position,environment,this::bindSceneLighting);
        frameBudget.probesEnd();
    }

    /** Preserve borrowed program ownership through graphics apply/rollback candidates. */
    RenderPipeline replacement(GraphicsProfile profile){return new RenderPipeline(profile,sharedVoxel);}

    public void begin(
            int w,
            int h,
            Matrix4f projection,
            Matrix4f view,
            Vector3f camera,
            boolean isometric,
            ShaderProgram voxel) {
        temporal.atmosphereComposed=true;
        frameBudget.begin(settings);
        frameBudget.atmosphereMark(0);
        atmosphere.view(view,isometric,settings.overviewHaze);
        atmosphere.quality(settings.atmosphereQuality);
        atmosphere.update(camera,sun);
        if(atmosphereEnvironmentRevision!=atmosphere.revision) { updateEnvironment(); probes.invalidate(); temporal.reset(); }
        frameBudget.atmosphereMark(1);
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
        frameBudget.atmosphereMark(2);
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
        draw();frameBudget.atmosphereMark(3);
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        frameBudget.terrainBegin();
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
        // Reflection capture may share this program. Restore the camera and draw
        // modes before the main pass instead of relying on pre-capture uniforms.
        voxel.setMatrix4("uProjection",projection);voxel.setMatrix4("uView",view);
        voxel.setInt("uVertexColor",1);voxel.setInt("uInstanced",0);
        voxel.setInt("uDistantTerrain",0);voxel.setInt("uFog",1);
        voxel.setFloat("uTransparency",0);
        voxel.setFloat("uJitterX", temporal.jitterX(width));
        voxel.setFloat("uJitterY", temporal.jitterY(height));
        voxel.setVector3("uCameraPosition", camera.x, camera.y, camera.z);
        bindSceneLighting(voxel);
        probes.bind(voxel);
        if(!settings.reflections)voxel.setInt("uProbeReady",0);
        glActiveTexture(GL_TEXTURE0);
    }

    private void bindSceneLighting(ShaderProgram shader) {
        atmosphere.bind(shader);shader.setInt("uAtmosphereReflection",0);
        shader.setFloat("uDaylight", daylight);
        shader.setFloat("uAmbient", ambient);
        shader.setInt("uLightingEnabled", 1);
        shader.setInt("uVoxelAO",settings.ao?1:0);shader.setInt("uVoxelGI",settings.screenGi?1:0);
        shader.setInt("uContactTransport",settings.contactShadows?1:0);shader.setInt("uLocalReflection",settings.reflections?1:0);
        shader.setInt("uHasIrradiance", hasIrradiance ? 1 : 0);
        shader.setInt("uRootBuffer",activeRootBuffer?(volume!=null&&volume.uniformRoots()?2:1):0);shader.setInt("uFineOffset",activeRootBuffer&&volume!=null&&!volume.uniformRoots()?volume.roots.length:0);
        shader.setInt("uSkyColumns",worldSky==null?0:worldSky.columns);shader.setInt("uSkyOffset",skyBufferOffset);
        shader.setInt("uSkyIndexOrigin",worldSky==null?0:worldSky.indexOrigin);shader.setInt("uSkyIndexSpan",worldSky==null?0:worldSky.indexSpan);shader.setInt("uSkyIndexOffset",worldSky==null?0:worldSky.indexOffset);
        shader.setInt("uShadowEnabled",settings.shadows?1:0);
        shader.setInt("uShadowCascades",settings.shadowCascades);
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
        frameBudget.terrainEnd();frameBudget.waterBegin();
        water.optics(settings.water);
        water.capture(world,models,projection,view,eye,width,height,environment,sun,ambient,daylight,this::bindSceneLighting);
        if (!water.found()){frameBudget.waterEnd();return;}
        if(settings.water){glBindFramebuffer(GL_READ_FRAMEBUFFER,hdrFbo);glBindFramebuffer(GL_DRAW_FRAMEBUFFER,resolveFbo);
        glBlitFramebuffer(0,0,width,height,0,0,width,height,GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT,GL_NEAREST);}
        glBindFramebuffer(GL_FRAMEBUFFER,hdrFbo);glViewport(0,0,width,height);
        glEnable(GL_DEPTH_TEST);glEnable(GL_CULL_FACE);
        water.render(world,projection,view,eye,resolveTexture,resolveDepth,environment,width,height,temporal.jitterX(width),temporal.jitterY(height),this::bindSceneLighting);
        frameBudget.waterEnd();
    }

    public void finish() {
        frameBudget.terrainEnd();frameBudget.postBegin();
        if (settings.particles) particles.render(viewProjection,height);
        glBindFramebuffer(GL_READ_FRAMEBUFFER, hdrFbo);
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, resolveFbo);
        glBlitFramebuffer(
                0, 0, width, height, 0, 0, width, height,
                GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT, GL_NEAREST);
        // Mixed opaque/transparent depth cannot be a safe occluder. The measured CPU
        // readback is kept in the diagnostic fixture only; production uses frustum culling.
        hiZ.invalidate();
        temporal.finish(resolveTexture, resolveDepth, sun, daylight, ambient);
        frameBudget.postEnd();frameBudget.end();
    }

    public void setColourLut(ColourLut lut) { temporal.setLut(lut); temporal.reset(); }
    public boolean reflectionProbeReady() { return probes.ready(); }
    public void addDecal(Decal decal) { temporal.addDecal(decal); }
    public void clearDecals() { temporal.clearDecals(); }
    public int particleCount() { return particles.count(); }
    public float atmosphereGpuLast(){return frameBudget.atmosphereLast();}
    public float atmosphereTableGpuLast(){return frameBudget.tableLast();}
    public long atmosphereGpuSamples(){return frameBudget.atmosphereSamples();}
    public float atmosphereGpuMillis(){return frameBudget.atmosphereMilliseconds();}
    public float atmosphereTableGpuMillis(){return frameBudget.tableMilliseconds();}
    public float gpuMillis() { return frameBudget.milliseconds(); }
    public long gpuSamples() { return frameBudget.samples(); }
    /** NaN means no completed GPU measurement, never a zero-cost pass. */
    public float shadowsGpuMillis() { return frameBudget.shadowsMilliseconds(); }
    public float probesGpuMillis() { return frameBudget.probesMilliseconds(); }
    public float terrainGpuMillis(){return frameBudget.terrainMilliseconds();}
    public float waterGpuMillis(){return frameBudget.waterMilliseconds();}
    public float postGpuMillis(){return frameBudget.postMilliseconds();}
    public int actualWidth(){return width;}public int actualHeight(){return height;}
    public long estimatedTargetBytes(){return lightingStores.retainedBytes()+(long)width*height*(samples*12+12)+(long)width*height*24+(long)shadowResolution*shadowResolution*3*4+settings.atmosphereQuality.bytes();}
    public void prepareTargets(int w,int h){int rw=Math.max(1,Math.round(w*settings.renderScale)),rh=Math.max(1,Math.round(h*settings.renderScale));resize(rw,rh);temporal.prepare(w,h);}
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

    private void resize(int w,int h){
        int limit=Math.min(glGetInteger(GL_MAX_TEXTURE_SIZE),glGetInteger(GL_MAX_RENDERBUFFER_SIZE));
        if(w<=0||h<=0||w>limit||h>limit)throw new IllegalArgumentException("Render target exceeds device limits");
        int[] old={hdrFbo,hdrColor,hdrDepth,resolveFbo,resolveTexture,resolveDepth,width,height};
        hdrFbo=hdrColor=hdrDepth=resolveFbo=resolveTexture=resolveDepth=0;
        try{allocateTargets(w,h);}catch(RuntimeException e){deleteTargets();hdrFbo=old[0];hdrColor=old[1];hdrDepth=old[2];resolveFbo=old[3];resolveTexture=old[4];resolveDepth=old[5];width=old[6];height=old[7];glBindFramebuffer(GL_FRAMEBUFFER,0);throw e;}
        glDeleteFramebuffers(old[0]);glDeleteRenderbuffers(old[1]);glDeleteRenderbuffers(old[2]);glDeleteFramebuffers(old[3]);glDeleteTextures(old[4]);glDeleteTextures(old[5]);
    }
    private void allocateTargets(int w, int h) {
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
        if(lightingUpload!=null){lightingUpload.close();lightingUpload=null;}
        if(skyLightingUpload!=null){skyLightingUpload.close();skyLightingUpload=null;}activeSky=null;skyVisibility.close();desiredSky=null;desiredVolume=null;volume=null;worldSky=null;desiredVolumeFence=activeVolumeFence=null;lightingBootstrapped=false;initialLightingUpdates=0;initialLightingFrame=false;hasIrradiance=false;activeRootBuffer=false;focusPrepared=false;focusWorld=null;previousFocusTime=lastVolumeLatency=0;
        lighting.close();
        hiZ.close(); gpuDraw.close(); frameBudget.close(); transport.close(); clusters.close(); particles.close(); probes.close(); water.close();
        deleteTargets();
        glDeleteTextures(environment);
        lightingStores.releaseTexture(irradiance);
        lightingStores.releaseTexture(fineRoots);
        lightingStores.releaseTexture(fineLight);
        lightingStores.releaseBuffer(fineBuffer);lightingStores.close();
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
