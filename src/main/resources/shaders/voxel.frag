#version 330 core
in vec3 vColor,vNormal,vWorldPosition;
flat in vec3 vSurface;
out vec4 fragColor;
uniform int uVertexColor,uFog,uLightingEnabled,uHasIrradiance,uShadowEnabled,uHeld;
uniform vec3 uColor,uLightDirection,uCameraPosition,uVolumeOrigin,uVolumeSize;
uniform float uModelEmission;
uniform samplerCube uEnvironment;
uniform sampler2D uShadow;
uniform sampler3D uIrradiance;
uniform sampler2DArray uMaterials;
uniform mat4 uShadowMatrix;
float shadow(vec3 N,vec3 L){
    if(uShadowEnabled==0)return 1.;
    vec4 p=uShadowMatrix*vec4(vWorldPosition,1);vec3 q=p.xyz/p.w*.5+.5;
    if(any(lessThan(q,vec3(0)))||any(greaterThan(q,vec3(1))))return 1.;
    float bias=max(.0006*(1.-dot(N,L)),.00015),result=0.;vec2 texel=1./vec2(textureSize(uShadow,0));
    for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++)result+=q.z-bias<=texture(uShadow,q.xy+vec2(x,y)*texel).r?1.:0.;return result/9.;
}
void main(){
    vec3 N=normalize(vNormal),L=normalize(-uLightDirection);vec3 albedo=uVertexColor==1?vColor:uColor;
    if(uLightingEnabled==0){fragColor=vec4(albedo*(.35+.65*max(dot(N,L),0.)),1);return;}
    if(uVertexColor==1&&vSurface.z>=0.){
        vec2 uv=abs(N.y)>.5?vWorldPosition.xz:abs(N.x)>.5?vWorldPosition.zy:vWorldPosition.xy;
        albedo*=texture(uMaterials,vec3(uv,vSurface.z)).rgb;
    }
    albedo=pow(max(albedo,vec3(0)),vec3(2.2));
    vec3 point=uHeld==1?uCameraPosition:vWorldPosition+N*.55;
    vec3 coord=(point-uVolumeOrigin)/uVolumeSize;
    vec3 indirect=vec3(.24,.32,.45);
    if(uHasIrradiance==1&&all(greaterThanEqual(coord,vec3(0)))&&all(lessThanEqual(coord,vec3(1))))indirect=texture(uIrradiance,coord).rgb*2.;
    vec3 V=uHeld==1?normalize(-vWorldPosition):normalize(uCameraPosition-vWorldPosition);
    float rough=uVertexColor==1?vSurface.y:.85;
    vec3 F=vec3(.04)+(1.-vec3(.04))*pow(1.-max(dot(N,V),0.),5.);
    vec3 reflection=textureLod(uEnvironment,reflect(-V,N),rough*7.).rgb;
    float emission=uModelEmission+(uVertexColor==1?vSurface.x:0.);
    vec3 color=albedo*(indirect+vec3(1.5,1.38,1.15)*max(dot(N,L),0.)*shadow(N,L))+reflection*F*(1.-rough*.65)+albedo*emission;
    if(uFog==1){float haze=smoothstep(640.,1920.,length(vWorldPosition.xz-uCameraPosition.xz));color=mix(color,vec3(.32,.53,.8),haze);}
    fragColor=vec4(color,1);
}
