#version 330 core
in vec2 vUV;out vec4 fragColor;
#include "shaders/atmosphere-common.glsl"
void main(){float elevation=(vUV.y*2.-1.)*ATM_PI*.5,azimuth=vUV.x*2.*ATM_PI;
    vec3 d=vec3(cos(elevation)*sin(azimuth),sin(elevation),cos(elevation)*cos(azimuth));vec3 t,l;
    atmosphereIntegrate(uPlanetCamera,d,uAtmosphereSun,2.*(uPlanetRadius+uAtmosphereHeight),abs(d.y)<.15?max(64,uAtmosphereSamples):uAtmosphereSamples,t,l);
    fragColor=vec4(l+t*atmosphereGround(uPlanetCamera,d),1);}
