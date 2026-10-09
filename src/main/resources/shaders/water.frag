#version 330 core
#define ATM_SCENE 1
#include "shaders/atmosphere-common.glsl"
#include "shaders/irradiance.glsl"
vec2 atmosphereVisibility(vec3 point){return uPlanetLighting==0?vec2(1):uHasIrradiance==0&&uSkyColumns==0?vec2(0):vec2(clamp(irradiance(point).a,0.,1.));}
in vec3 vWorldPosition,vNormal;flat in vec3 vSurface;out vec4 fragColor;
uniform sampler2D uScene,uDepth,uPlanar;uniform samplerCube uEnvironment;
uniform mat4 uInverseVP,uReflectionVP;uniform vec3 uCamera;uniform float uTime,uWidth,uHeight,uPlaneY;uniform int uPlanarReady;
vec3 world(vec2 uv,float d){vec4 p=uInverseVP*vec4(uv*2.-1.,d*2.-1.,1);return p.xyz/p.w;}
void main(){
    if(vSurface.z!=-2.)discard;
    vec3 p=vWorldPosition,N=normalize(vNormal+vec3(cos(p.x*1.3+uTime)*.055,0.,sin(p.z*1.7-uTime*.8)*.055));
    vec3 V=normalize(uCamera-p);vec2 uv=gl_FragCoord.xy/vec2(uWidth,uHeight);
    vec2 refractUV=clamp(uv+N.xz*.018,vec2(0),vec2(1));float d=texture(uDepth,refractUV).r;
    vec3 bottom=world(refractUV,d);
    // Distortion must not pull foreground objects in front of the water into the refraction.
    if(length(bottom-uCamera)<length(p-uCamera)){refractUV=uv;d=texture(uDepth,uv).r;bottom=world(uv,d);}
    float thickness=d>=.999999?30.:min(30.,length(bottom-p));vec3 transmittance=exp(-vec3(.2,.065,.035)*thickness);
    vec3 airT=vec3(1),airL=vec3(0);if(uPlanetLighting==1)atmosphereAerial(p,8,airT,airL);
    // Opaque refraction already includes air. Preserve foreground in-scattering once.
    vec3 refraction=texture(uScene,refractUV).rgb*transmittance+(airL+airT*vec3(.012,.075,.12))*(1.-transmittance);
    float caustic=pow(max(0.,sin(bottom.x*3.+uTime)+sin(bottom.z*3.7-uTime*1.1))*.5,8.);
    refraction+=airT*vec3(.12,.18,.13)*caustic*exp(-thickness*.2);
    vec3 reflection=textureLod(uEnvironment,reflect(-V,N),1.).rgb;
    if(uPlanarReady==1&&abs(p.y-uPlaneY)<.05){vec4 q=uReflectionVP*vec4(p,1);vec2 r=q.xy/q.w*.5+.5+N.xz*.008;
        if(all(greaterThan(r,vec2(0)))&&all(lessThan(r,vec2(1))))reflection=texture(uPlanar,r).rgb;}
    reflection=reflection*airT+airL;
    float fresnel=.02+.98*pow(1.-abs(dot(N,V)),5.);
    fragColor=vec4(mix(refraction,reflection,fresnel),1);
}
