#version 330 core
in vec3 vColor,vNormal,vWorldPosition;
flat in vec3 vSurface;
out vec4 fragColor;
uniform float uDaylight,uAmbient;
uniform int uVertexColor,uFog,uLightingEnabled,uHasIrradiance,uShadowEnabled,uHeld;
uniform vec3 uColor,uLightDirection,uCameraPosition,uVolumeOrigin,uVolumeSize;
uniform float uModelEmission;
uniform samplerCube uEnvironment;
uniform sampler2D uShadow;
uniform sampler3D uIrradiance;
uniform isampler3D uFineRoots;
uniform isamplerBuffer uFineLight;
uniform sampler2DArray uMaterials;
uniform mat4 uShadowMatrix;
float shadow(vec3 N,vec3 L){
    if(uShadowEnabled==0)return 1.;
    vec4 p=uShadowMatrix*vec4(vWorldPosition,1);vec3 q=p.xyz/p.w*.5+.5;
    if(any(lessThan(q,vec3(0)))||any(greaterThan(q,vec3(1))))return 1.;
    float bias=max(.000015*(1.-dot(N,L)),.000005),result=0.;vec2 texel=1./vec2(textureSize(uShadow,0));
    for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++)result+=q.z-bias<=texture(uShadow,q.xy+vec2(x,y)*texel).r?1.:0.;return result/9.;
}
vec4 irradiance(vec3 point){
    vec3 local=point-uVolumeOrigin;
    if(any(lessThan(local,vec3(0)))||any(greaterThanEqual(local,uVolumeSize)))return vec4(vec3(.24,.32,.45)*uAmbient,1.);
    ivec3 cell=ivec3(floor(local));
    int node=texelFetch(uFineRoots,cell,0).r;
    if(node==0){vec4 c=texelFetch(uIrradiance,cell,0);return vec4(c.rgb*(255./127.),(c.a*255.-128.)/127.);}
    if(node>0)node--;
    vec3 p=fract(local);
    for(int depth=0;depth<5&&node>=0;depth++){
        p*=2.;ivec3 octant=ivec3(floor(p));p=fract(p);
        node=texelFetch(uFineLight,node+octant.x+2*octant.y+4*octant.z).r;
    }
    return vec4(vec3(node&255,(node>>8)&255,(node>>16)&255)/127.,float((node>>24)&127)/127.);
}
void main(){
    vec3 N=normalize(vNormal),L=normalize(-uLightDirection);vec3 albedo=uVertexColor==1?vColor:uColor;
    if(uLightingEnabled==0){fragColor=vec4(albedo*(.35+.65*max(dot(N,L),0.)),1);return;}
    if(uVertexColor==1&&vSurface.z>=0.){
        vec2 uv=abs(N.y)>.5?vWorldPosition.xz:abs(N.x)>.5?vWorldPosition.zy:vWorldPosition.xy;
        albedo*=texture(uMaterials,vec3(uv,vSurface.z)).rgb;
    }
    albedo=pow(max(albedo,vec3(0)),vec3(2.2));
    vec3 point=uHeld==1?uCameraPosition:vWorldPosition+N*.001;
    vec3 indirect=vec3(.24,.32,.45)*uAmbient;
    float skyVisibility=1.;
    if(uHasIrradiance==1){vec4 lighting=irradiance(point);indirect=lighting.rgb;skyVisibility=lighting.a;}
    vec3 V=uHeld==1?normalize(-vWorldPosition):normalize(uCameraPosition-vWorldPosition);
    float rough=uVertexColor==1?vSurface.y:.85;
    vec3 F=vec3(.04)+(1.-vec3(.04))*pow(1.-max(dot(N,V),0.),5.);
    vec3 reflection=textureLod(uEnvironment,reflect(-V,N),rough*7.).rgb;
    float emission=uModelEmission+(uVertexColor==1?vSurface.x:0.);
    vec3 color=albedo*(indirect+vec3(1.5,1.38,1.15)*uDaylight*max(dot(N,L),0.)*shadow(N,L)*skyVisibility)+reflection*F*(1.-rough*.65)*skyVisibility+albedo*emission;
    if(uFog==1){float haze=smoothstep(640.,1920.,length(vWorldPosition.xz-uCameraPosition.xz));color=mix(color,vec3(.32,.53,.8)*uAmbient,haze);}
    fragColor=vec4(color,1);
}
