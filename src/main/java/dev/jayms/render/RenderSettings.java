package dev.jayms.render;

/** Runtime quality switches. All paths retain the OpenGL 3.3 baseline. */
public final class RenderSettings {
    public PlanetAtmosphere.Quality atmosphereQuality = PlanetAtmosphere.Quality.MEDIUM;
    public float overviewHaze = 1;
    public boolean shadows = true, particles = true;
    public boolean taa = true, ao = true, contactShadows = true, reflections = true;
    public boolean screenGi = true, volumetrics = true, bloom = true, clouds = true;
    public boolean autoExposure = true, dynamicResolution = false;
    public float renderScale = 1, exposure = 1, saturation = 1, contrast = 1;
    public float fogDensity = .0025f, cloudCoverage = .45f;
    public float targetFrameMillis = 16.67f;
    public float minRenderScale=.5f,maxRenderScale=1;
    public boolean water=true;
    public int shadowDistance=256,shadowCascades=3;
    public void apply(GraphicsProfile p) {
        taa=p.get(GraphicsProfile.Key.AA).equals("TAA");
        ao=p.on(GraphicsProfile.Key.AO);contactShadows=p.on(GraphicsProfile.Key.CONTACT);screenGi=p.on(GraphicsProfile.Key.GI);
        reflections=p.on(GraphicsProfile.Key.REFLECTIONS);water=p.on(GraphicsProfile.Key.WATER);clouds=p.on(GraphicsProfile.Key.CLOUDS);bloom=p.on(GraphicsProfile.Key.BLOOM);
        autoExposure=p.on(GraphicsProfile.Key.AUTO_EXPOSURE);exposure=p.number(GraphicsProfile.Key.EXPOSURE);
        dynamicResolution=p.on(GraphicsProfile.Key.DYNAMIC);renderScale=p.number(GraphicsProfile.Key.SCALE);
        minRenderScale=p.number(GraphicsProfile.Key.MIN_SCALE);maxRenderScale=p.number(GraphicsProfile.Key.MAX_SCALE);targetFrameMillis=p.number(GraphicsProfile.Key.TARGET);
        if(dynamicResolution)renderScale=Math.max(minRenderScale,Math.min(maxRenderScale,renderScale));
        atmosphereQuality=PlanetAtmosphere.Quality.valueOf(p.get(GraphicsProfile.Key.ATMOSPHERE));overviewHaze=p.number(GraphicsProfile.Key.HAZE);
        shadows=!p.get(GraphicsProfile.Key.SHADOWS).equals("OFF");shadowDistance=p.integer(GraphicsProfile.Key.SHADOW_DISTANCE);
        shadowCascades=p.get(GraphicsProfile.Key.SHADOWS).equals("LOW")?1:p.get(GraphicsProfile.Key.SHADOWS).equals("MEDIUM")?2:3;
    }
    public static RenderSettings defaults() {
        RenderSettings s = new RenderSettings();
        s.overviewHaze=Float.parseFloat(System.getProperty("voxel.overviewHaze","1"));
        if(!Float.isFinite(s.overviewHaze)||s.overviewHaze<0||s.overviewHaze>1)throw new IllegalArgumentException("Overview haze must be in [0,1]");
        s.taa = !Boolean.getBoolean("voxel.noTaa");
        s.dynamicResolution = Boolean.getBoolean("voxel.dynamicResolution");
        s.atmosphereQuality = PlanetAtmosphere.Quality.valueOf(System.getProperty("voxel.atmosphereQuality","MEDIUM").toUpperCase(java.util.Locale.ROOT));
        s.renderScale = Math.max(.5f, Math.min(1, Float.parseFloat(System.getProperty("voxel.renderScale", "1"))));
        return s;
    }
}
