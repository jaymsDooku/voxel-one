// Integral of a periodic rectangular band in world blocks. Taking the
// difference at a pixel's edges averages every band crossed by that pixel.
vec2 gridIntegral(vec2 x,float width,float period){
    return floor(x/period)*width+min(mod(x,period),vec2(width));
}
vec2 gridCoverage(vec2 coord,vec2 footprint,float width,float period){
    vec2 phase=mod(coord+width*.5,period);
    vec2 halfPixel=.5*footprint;
    return clamp((gridIntegral(phase+halfPixel,width,period)-gridIntegral(phase-halfPixel,width,period))
                 /footprint,0.,1.);
}
float gridUnion(vec2 bands){return bands.x+bands.y-bands.x*bands.y;}
vec3 gridAlbedo(vec2 position,vec2 footprint,vec2 origin,vec3 layoutParams,
                vec3 grass,vec3 asphalt,vec3 paint){
    vec2 coord=position-origin-layoutParams.y*.5;
    float road=gridUnion(gridCoverage(coord,footprint,layoutParams.y,layoutParams.x));
    float stripe=gridUnion(gridCoverage(coord,footprint,layoutParams.z,layoutParams.x));
    return grass*(1.-road)+asphalt*max(road-stripe,0.)+paint*stripe;
}
