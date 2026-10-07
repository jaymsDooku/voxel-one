#version 330 core
in vec2 vUV;out vec4 fragColor;
uniform mat4 uInverseVP;uniform vec3 uCenter;uniform samplerCube uEnvironment;
void main(){vec4 p=uInverseVP*vec4(vUV*2.-1.,1,1);fragColor=vec4(texture(uEnvironment,normalize(p.xyz/p.w-uCenter)).rgb,1);}
