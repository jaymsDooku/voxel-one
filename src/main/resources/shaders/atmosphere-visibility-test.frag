#version 330 core
#include "shaders/atmosphere-common.glsl"
#include "shaders/irradiance.glsl"
uniform sampler2DArray uShadow;
#include "shaders/preview-wall-sky.glsl"
uniform vec3 uTestPosition,uTestNormal;
out vec4 fragColor;
void main(){
    float sky=irradiance(uTestPosition).a;
    float side=previewWallSky(uTestPosition,uTestNormal,sky);
    fragColor=vec4(sky,side,previewSideExposure(uTestPosition,uTestNormal),1.);
}
