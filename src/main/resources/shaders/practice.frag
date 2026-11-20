#version 330 core
in vec3 tint;
in vec3 surfaceNormal;
out vec4 fragment;
void main() {
    float daylight=.52+.48*max(0.,dot(normalize(surfaceNormal),normalize(vec3(.35,.85,-.4))));
    fragment=vec4(tint*daylight,1);
}
