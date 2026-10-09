#version 330 core
#include "shaders/atmosphere-common.glsl"
uniform vec3 uTestPosition,uTestSun;
out vec4 fragColor;
void main(){fragColor=vec4(atmosphereSunlight(uTestPosition,uTestSun),1.);}
