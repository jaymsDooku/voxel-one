#version 330 core
layout(location=0) in vec3 aPosition;
layout(location=1) in vec3 aNormal;
uniform mat4 uProjection,uView,uModel;
out vec3 normal;
void main(){normal=mat3(transpose(inverse(uModel)))*aNormal;gl_Position=uProjection*uView*uModel*vec4(aPosition,1);}
