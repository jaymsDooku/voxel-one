#version 330 core
#include "shaders/atmosphere-common.glsl"
#include "shaders/stress-grid.glsl"
in vec3 vColor,vNormal,vWorldPosition;
out vec4 fragColor;
uniform float uDaylight,uAmbient,uLodFade;
uniform int uLodParent;
uniform vec3 uLightDirection,uCameraPosition,uDetailOrigin,uTerrainMinimum,uTerrainMaximum;
uniform vec3 uGridOrigin,uGridLayout,uGridGrass,uGridAsphalt,uGridPaint;
uniform usampler2D uDetailColumns;
uniform int uFog;
uniform samplerCube uEnvironment;
void main(){
    // Derivatives must be taken before any LOD/ownership discard.
    vec2 footprint=max(fwidth(vWorldPosition.xz),vec2(.001));
    float dither=fract(sin(dot(floor(gl_FragCoord.xy),vec2(12.9898,78.233)))*43758.5453);
    if(uLodParent==1?dither<uLodFade:dither>=uLodFade)discard;
    if(any(lessThan(vWorldPosition.xz,uTerrainMinimum.xz))||any(greaterThan(vWorldPosition.xz,uTerrainMaximum.xz)))discard;
    ivec2 c=ivec2(floor((vWorldPosition.xz-vNormal.xz*.125)/16.))-ivec2(uDetailOrigin.xz);
    ivec2 word=ivec2(c.x>>5,c.y);
    if(all(greaterThanEqual(c,ivec2(0)))&&all(lessThan(word,textureSize(uDetailColumns,0)))
            &&(texelFetch(uDetailColumns,word,0).r&(1u<<uint(c.x&31)))!=0u)discard;
    vec3 N=normalize(vNormal),V=normalize(uCameraPosition-vWorldPosition);
    vec3 solar=uPlanetLighting==1?atmosphereDirect(vWorldPosition-uAtmosphereWorldCamera)/ATM_PI:vec3(1.5,1.38,1.15)*uDaylight;
    vec3 sky=uPlanetLighting==1?textureLod(uEnvironment,N,7.).rgb:vec3(.24,.32,.45)*uAmbient;
    vec3 albedo=vColor.r<0.?gridAlbedo(vWorldPosition.xz,footprint,uGridOrigin.xz,uGridLayout,
                                       uGridGrass,uGridAsphalt,uGridPaint):pow(max(vColor,vec3(0)),vec3(2.2));
    vec3 color=albedo*(sky+solar*max(dot(N,normalize(-uLightDirection)),0.));
    color+=textureLod(uEnvironment,reflect(-V,N),6.).rgb*.018;
    if(uFog==1){
        vec3 t,l;atmosphereAerial(vWorldPosition,8,t,l);color=color*t+l;
    }

    fragColor=vec4(color,1);
}
