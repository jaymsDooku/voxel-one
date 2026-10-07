#version 330 core
in vec3 vColor;in float vAlpha;out vec4 fragColor;
void main(){vec2 p=gl_PointCoord*2.-1.;float r=dot(p,p);if(r>1.)discard;fragColor=vec4(vColor*exp(-r*4.)*vAlpha*.35,0);}
