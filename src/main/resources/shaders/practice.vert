#version 330 core
layout(location=0) in vec3 position;
layout(location=1) in vec3 normal;
layout(location=2) in vec3 color;
uniform mat4 uProjection,uView,uModel;
out vec3 tint;
out vec3 surfaceNormal;
void main() {
    gl_Position=uProjection*uView*uModel*vec4(position,1);
    tint=color;
    surfaceNormal=mat3(transpose(inverse(uModel)))*normal;
}
