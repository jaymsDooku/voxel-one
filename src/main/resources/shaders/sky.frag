#version 330 core
in vec2 vUV;
out vec4 fragColor;
uniform mat4 uInverseViewProjection;
uniform vec3 uCameraPosition,uSunDirection;
uniform int uIsometric;
void main(){
    vec4 farPoint=uInverseViewProjection*vec4(vUV*2.-1.,1,1);vec3 ray=normalize(farPoint.xyz/farPoint.w-uCameraPosition);
    float t=max(0.,ray.y);vec3 color=mix(vec3(.32,.53,.8),vec3(.045,.18,.48),t);
    float angle=dot(ray,uSunDirection);
    float halo=pow(max(angle,0.),128.)*.7,disc=smoothstep(.9997,.99985,angle)*8.;
    if(uIsometric==1){color=vec3(.32,.53,.8);halo=0.;disc=0.;}
    fragColor=vec4(color+vec3(1.,.9,.65)*(halo+disc),1);
}
