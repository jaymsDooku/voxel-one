#version 330 core
layout(location=0) in vec3 aPosition;
layout(location=1) in vec3 aNormal;
layout(location=2) in vec3 aColor;
layout(location=3) in vec3 aInstanceOffset;
layout(location=4) in vec3 aSurface;
uniform int uInstanced;
uniform mat4 uProjection,uView,uModel;
out vec3 vColor,vNormal,vWorldPosition;
flat out vec3 vSurface;
void main(){
    vec4 p=uModel*vec4(aPosition+(uInstanced==1?aInstanceOffset:vec3(0)),1);
    vWorldPosition=p.xyz;vNormal=mat3(transpose(inverse(uModel)))*aNormal;vColor=aColor;vSurface=aSurface;
    gl_Position=uProjection*uView*p;
}
