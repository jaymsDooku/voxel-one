#version 330 core
#include "shaders/atmosphere-common.glsl"
#include "shaders/irradiance.glsl"
#include "shaders/preview-wall-sky.glsl"
uniform vec3 uTestPosition,uTestNormal,uTestEye;
out vec4 fragColor;
void main(){
    float sky=irradiance(uTestPosition).a;
    float side=previewWallSky(uTestPosition,uTestNormal,sky);
    fragColor=vec4(sky,side,previewOpeningLight(uTestPosition,uTestNormal,uTestEye,side),1.);
}
