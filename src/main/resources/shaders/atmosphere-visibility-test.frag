#version 330 core
#include "shaders/atmosphere-common.glsl"
#include "shaders/irradiance.glsl"
uniform vec3 uTestPosition;
out vec4 fragColor;
void main(){fragColor=vec4(irradiance(uTestPosition).aaa,1.);}
