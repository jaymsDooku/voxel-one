#version 330 core
layout(location=0) in vec3 aPosition;
layout(location=1) in vec3 aNormal;
layout(location=5) in vec3 center;
layout(location=6) in vec3 scale;
layout(location=7) in vec3 color;
uniform mat4 uProjection,uView;
flat out vec3 tint;
void main(){
    // Boxes stay axis-aligned: face light is constant across each triangle.
    float light=.55+.45*max(dot(aNormal,normalize(vec3(-.5,1,.6))),0);
    tint=color*light;
    gl_Position=uProjection*uView*vec4(center+(aPosition-vec3(.5))*scale,1);
}
