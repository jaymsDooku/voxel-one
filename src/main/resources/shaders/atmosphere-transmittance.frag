#version 330 core
in vec2 vUV;out vec4 fragColor;
#include "shaders/atmosphere-common.glsl"
void main(){
    float h=vUV.y*vUV.y*uAtmosphereHeight,mapped=vUV.x*2.-1.,mu=sign(mapped)*mapped*mapped;vec3 p=vec3(0,uPlanetRadius+h,0),d=vec3(sqrt(max(0.,1.-mu*mu)),mu,0);
    vec2 span=atmospherePath(p,d,2.*(uPlanetRadius+uAtmosphereHeight));vec3 depth=vec3(0);
    if(span.y>span.x){float stepSize=(span.y-span.x)/64.;for(int i=0;i<64;i++)depth+=atmosphereExtinction(atmosphereDensity(length(p+d*(span.x+(float(i)+.5)*stepSize))-uPlanetRadius))*stepSize;}
    fragColor=vec4(exp(-depth),1);
}
