#version 330 core
in vec2 vUV;out vec4 fragColor;
uniform int uFace;
uniform sampler2D uSkyView;
#include "shaders/atmosphere-common.glsl"
void main(){vec2 p=vUV*2.-1.;vec3 d;
    if(uFace==0)d=vec3(1,-p.y,-p.x);else if(uFace==1)d=vec3(-1,-p.y,p.x);
    else if(uFace==2)d=vec3(p.x,1,p.y);else if(uFace==3)d=vec3(p.x,-1,-p.y);
    else if(uFace==4)d=vec3(p.x,-p.y,1);else d=vec3(-p.x,-p.y,-1);
    d=normalize(uWorldToPlanet*d);vec2 uv=vec2(fract(atan(d.x,d.z)/(2.*ATM_PI)),asin(clamp(d.y,-1.,1.))/ATM_PI+.5);
    vec3 color=texture(uSkyView,uv).rgb;
    color+=uSolar*atmosphereSunlight(uPlanetCamera,uAtmosphereSun)*smoothstep(cos(uSolarRadius*1.1),cos(uSolarRadius*.9),dot(d,uAtmosphereSun));
    fragColor=vec4(color,1);}
