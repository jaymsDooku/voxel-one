#version 330 core

uniform mat4 uClipFromWorld;
uniform vec3 uFeet;
uniform float uRadius;
uniform float uHeight;

const vec3 corners[8] = vec3[8](
    vec3(-1, 0, -1), vec3(1, 0, -1), vec3(1, 0, 1), vec3(-1, 0, 1),
    vec3(-1, 1, -1), vec3(1, 1, -1), vec3(1, 1, 1), vec3(-1, 1, 1)
);
const int edges[24] = int[24](
    0, 1, 1, 2, 2, 3, 3, 0,
    4, 5, 5, 6, 6, 7, 7, 4,
    0, 4, 1, 5, 2, 6, 3, 7
);

void main() {
    vec3 offset = corners[edges[gl_VertexID]] * vec3(uRadius, uHeight, uRadius);
    gl_Position = uClipFromWorld * vec4(uFeet + offset, 1.0);
}
