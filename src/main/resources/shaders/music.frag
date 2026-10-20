#version 330 core
in vec3 normal;
uniform vec3 uColor;
uniform float uAlpha=1.;
out vec4 fragColor;
void main(){float light=.55+.45*max(dot(normalize(normal),normalize(vec3(-.5,1,.6))),0);fragColor=vec4(uColor*light,uAlpha);}
