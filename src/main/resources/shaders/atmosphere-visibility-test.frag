#version 330 core
#include "shaders/atmosphere-common.glsl"
#include "shaders/irradiance.glsl"
#include "shaders/preview-wall-sky.glsl"
uniform vec3 uTestPosition,uTestNormal;
out vec4 fragColor;
void main(){
    float sky=irradiance(uTestPosition).a;
    fragColor=vec4(sky,previewWallSky(uTestPosition,uTestNormal,sky),0.,1.);
}
