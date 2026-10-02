#version 330 core
layout(location=0)in vec3 aPosition;
layout(location=3)in vec3 aInstanceOffset;
uniform mat4 uProjection,uView,uModel;
uniform int uInstanced;
void main(){gl_Position=uProjection*uView*uModel*vec4(aPosition+(uInstanced==1?aInstanceOffset:vec3(0)),1);}
