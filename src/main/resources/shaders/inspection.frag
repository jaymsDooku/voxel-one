#version 330 core
in vec3 vColor,vNormal,vWorldPosition;
flat in vec3 vSurface;
out vec4 fragColor;
// Read-only geometry inspection: water stays visible and opaque. No complete
// lighting, reflection, simulation or gameplay readiness is implied.
void main(){float light=.4+.6*max(dot(normalize(vNormal),normalize(vec3(.3,1,.5))),0.);fragColor=vec4(vColor*light,1);}
