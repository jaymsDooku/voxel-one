#version 330 core
in vec3 vColor,vNormal,vWorldPosition;flat in vec3 vSurface;out vec4 fragColor;
uniform vec3 uSun,uColor;uniform float uAmbient,uDaylight;uniform int uVertexColor;
void main(){if(vSurface.z==-2.)discard;vec3 albedo=uVertexColor==1?vColor:uColor;fragColor=vec4(pow(albedo,vec3(2.2))*(vec3(.24,.32,.45)*uAmbient+vec3(1.5,1.38,1.15)*uDaylight*max(dot(normalize(vNormal),uSun),0.)),1);}
