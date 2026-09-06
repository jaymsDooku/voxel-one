#version 330 core

layout (location = 0) in vec3 aPosition;
layout (location = 1) in vec3 aNormal;

uniform mat4 uProjection;
uniform mat4 uView;
uniform mat4 uModel;

out vec3 vNormal;
out vec3 vWorldPosition;

void main() {
    vec4 worldPosition =
        uModel * vec4(aPosition, 1.0);

    vWorldPosition = worldPosition.xyz;

    vNormal = mat3(transpose(inverse(uModel)))
        * aNormal;

    gl_Position =
        uProjection
        * uView
        * worldPosition;
}