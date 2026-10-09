package dev.jayms.render;

/** Runtime quality switches. All paths retain the OpenGL 3.3 baseline. */
public final class RenderSettings {
    public PlanetAtmosphere.Quality atmosphereQuality = PlanetAtmosphere.Quality.MEDIUM;
    public float overviewHaze = 1;
    public boolean taa = true, ao = true, contactShadows = true, reflections = true;
    public boolean screenGi = true, volumetrics = true, bloom = true, clouds = true;
    public boolean autoExposure = true, dynamicResolution = false;
    public float renderScale = 1, exposure = 1, saturation = 1, contrast = 1;
    public float fogDensity = .0025f, cloudCoverage = .45f;
    public float targetFrameMillis = 16.67f;
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
