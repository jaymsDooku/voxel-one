#version 330 core
layout(location=0) in vec4 aPosition;
layout(location=1) in vec4 aVelocity;
uniform mat4 uVP;uniform float uHeight;uniform vec3 uColours[16];
out vec3 vColor;out float vAlpha;
void main(){gl_Position=uVP*vec4(aPosition.xyz,1);gl_PointSize=clamp(uHeight*.025/max(.1,gl_Position.w),2.,10.);vColor=uColours[clamp(int(aVelocity.w),0,15)];vAlpha=sin(clamp(aPosition.w/2.5,0.,1.)*3.14159);}
