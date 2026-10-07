#version 330 core
layout(location=0)in vec3 aPosition;
layout(location=3)in vec3 aInstanceOffset;
layout(location=4)in vec3 aSurface;
flat out float vTransmission;
uniform mat4 uProjection,uView,uModel;
uniform int uInstanced;
void main(){vTransmission=(aSurface.z==9.||aSurface.z==-2.)?1.:0.;gl_Position=uProjection*uView*uModel*vec4(aPosition+(uInstanced==1?aInstanceOffset:vec3(0)),1);}
