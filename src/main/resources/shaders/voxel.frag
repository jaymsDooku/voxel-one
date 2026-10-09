#version 330 core
#include "shaders/atmosphere-common.glsl"
in vec3 vColor,vNormal,vWorldPosition;
flat in vec3 vSurface;
out vec4 fragColor;
uniform float uDaylight,uAmbient;
uniform int uVertexColor,uFog,uLightingEnabled,uHasIrradiance,uShadowEnabled,uHeld;
uniform vec3 uColor,uLightDirection,uCameraPosition,uVolumeOrigin,uVolumeSize;
uniform float uModelEmission,uTransparency,uOutputExposure;
uniform int uOutputTone;
uniform samplerCube uEnvironment;
uniform sampler2DArray uShadow;
uniform sampler3D uIrradiance;
uniform isampler3D uFineRoots;
uniform isamplerBuffer uFineLight;
uniform sampler2DArray uMaterials;
uniform mat4 uShadowMatrix[3];
uniform int uTransportReady,uClusterReady,uProbeReady;
uniform vec3 uProbeCenter;
uniform samplerCube uReflectionProbe;
uniform vec3 uClusterOrigin;
uniform isampler3D uClusters;
uniform isamplerBuffer uLightIndices;
uniform samplerBuffer uLights;
uniform sampler3D uVoxelRadiance,uDistanceField,uCascadeProbes[3];
const float PI=3.14159265;
vec2 disk(int i,int count){float angle=float(i)*2.39996323;return vec2(cos(angle),sin(angle))*sqrt((float(i)+.5)/float(count));}
float cascadeShadow(int layer,vec3 q,float bias){
    vec2 texel=1./vec2(textureSize(uShadow,0).xy);float blocker=0.,count=0.;
    for(int i=0;i<8;i++){float z=texture(uShadow,vec3(q.xy+disk(i,8)*texel*5.,layer)).r;
        if(z<q.z-bias){blocker+=z;count++;}}
    if(count==0.)return 1.;
    blocker/=count;
    float radius=clamp((q.z-blocker)/max(.001,blocker)*70.,1.,8.);
    float visible=0.;for(int i=0;i<16;i++)
        visible+=q.z-bias<=texture(uShadow,vec3(q.xy+disk(i,16)*texel*radius,layer)).r?1.:0.;
    return visible/16.;
}
float shadow(vec3 N,vec3 L){
    if(uShadowEnabled==0||uHeld==1)return 1.;
    for(int layer=0;layer<3;layer++){
        vec4 p=uShadowMatrix[layer]*vec4(vWorldPosition,1);vec3 q=p.xyz/p.w*.5+.5;
        if(any(lessThan(q,vec3(.015)))||any(greaterThan(q,vec3(.985))))continue;
        float bias=max(.00012*(1.-dot(N,L)),.00004);
        float result=cascadeShadow(layer,q,bias);
        float edge=max(abs(q.x-.5),abs(q.y-.5))*2.;
        if(layer<2&&edge>.8){vec4 p2=uShadowMatrix[layer+1]*vec4(vWorldPosition,1);vec3 q2=p2.xyz/p2.w*.5+.5;
            result=mix(result,cascadeShadow(layer+1,q2,bias),smoothstep(.8,.97,edge));}
        return result;
    }
    return 1.;
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
bool volumeInside(vec3 p){return all(greaterThanEqual(p,uVolumeOrigin))&&all(lessThan(p,uVolumeOrigin+uVolumeSize));}
float sdf(vec3 p){return texture(uDistanceField,(p-uVolumeOrigin)/uVolumeSize).r;}
float distanceShadow(vec3 p,vec3 dir){
    float t=.7,result=1.;
    for(int i=0;i<20;i++){
        vec3 q=p+dir*t;if(!volumeInside(q))break;
        float d=sdf(q);if(d<.02)return 0.;
        result=min(result,8.*d/t);t+=max(.2,d*.8);if(t>24.)break;
    }
    return clamp(result,0.,1.);
}
// Trace only the biased receiver-to-emitter segment. The transport grid is coarse;
// its emitter cell must not shadow its own light. Exact cell traversal also avoids
// the emitter's interpolated SDF halo dimming otherwise unobstructed rays.
float pointShadow(vec3 p,vec3 target){
    vec3 delta=target-p;float limit=length(delta);
    if(limit<.0001)return 1.;
    vec3 dir=delta/limit;
    ivec3 cell=ivec3(floor(p-uVolumeOrigin));
    ivec3 emitter=ivec3(floor(target-uVolumeOrigin));
    ivec3 step=ivec3(sign(dir));
    vec3 stride=vec3(1e20),next=vec3(1e20);
    for(int axis=0;axis<3;axis++)if(abs(dir[axis])>.000001){
        stride[axis]=abs(1./dir[axis]);
        float edge=uVolumeOrigin[axis]+float(cell[axis])+(step[axis]>0?1.:0.);
        next[axis]=max(0.,(edge-p[axis])/dir[axis]);
    }
    for(int i=0;i<64;i++){
        if(all(equal(cell,emitter)))return 1.;
        if(any(lessThan(cell,ivec3(0)))||any(greaterThanEqual(cell,textureSize(uVoxelRadiance,0))))return 1.;
        if(texelFetch(uVoxelRadiance,cell,0).a>.5)return 0.;
        float t=min(next.x,min(next.y,next.z));
        if(t>=limit)return 1.;
        // Advance all tied axes; cells touched only at an edge are not blockers.
        for(int axis=0;axis<3;axis++)if(next[axis]<=t+.00001){cell[axis]+=step[axis];next[axis]+=stride[axis];}
    }
    return 0.; // Conservative bound; radius-12 clustered lights need fewer than 64 cells.
}
vec3 cone(vec3 p,vec3 direction){
    vec3 sum=vec3(0);float transmittance=1.,distance=1.5;
    for(int i=0;i<12;i++){
        vec3 q=p+direction*distance;if(!volumeInside(q))break;
        float diameter=max(1.,distance*.45),lod=log2(diameter);
        vec4 sampleValue=textureLod(uVoxelRadiance,(q-uVolumeOrigin)/uVolumeSize,lod);
        sum+=transmittance*sampleValue.rgb;transmittance*=1.-sampleValue.a;
        distance+=diameter*.7;if(transmittance<.05)break;
    }
    return sum;
}
vec3 probeIrradiance(vec3 p,vec3 N){
    // Angular slabs store the merged near/far intervals; diffuse probes integrate a cosine lobe.
    vec3 uv=(p-uVolumeOrigin)/uVolumeSize;vec3 total=vec3(0);float weight=0.;
    float spatialDepth=float(textureSize(uCascadeProbes[0],0).z)/16.;
    uv.z=clamp(uv.z,.5/spatialDepth,1.-.5/spatialDepth);
    for(int i=0;i<16;i++){
        float y=1.-2.*(float(i)+.5)/16.,r=sqrt(1.-y*y),angle=float(i)*2.39996323;
        vec3 direction=vec3(cos(angle)*r,y,sin(angle)*r);float cosine=max(0.,dot(N,direction));
        vec4 sampleValue=texture(uCascadeProbes[0],vec3(uv.xy,(float(i)+uv.z)/16.));
        total+=sampleValue.rgb*cosine;weight+=sampleValue.a*cosine;
    }
    return total/max(.1,weight);
}
void main(){
    if(vSurface.z==-2.)discard;
    vec3 N=normalize(vNormal),L=normalize(-uLightDirection);vec3 albedo=uVertexColor==1?vColor:uColor;
    if(uLightingEnabled==0){fragColor=vec4(albedo*(.35+.65*max(dot(N,L),0.)),1.-uTransparency);return;}
    if(uVertexColor==1&&vSurface.z>=0.){
        vec3 weight=pow(abs(N),vec3(4));weight/=max(.0001,weight.x+weight.y+weight.z);
        vec3 tx=texture(uMaterials,vec3(vWorldPosition.zy,vSurface.z)).rgb;
        vec3 ty=texture(uMaterials,vec3(vWorldPosition.xz,vSurface.z)).rgb;
        vec3 tz=texture(uMaterials,vec3(vWorldPosition.xy,vSurface.z)).rgb;
        vec3 tex=tx*weight.x+ty*weight.y+tz*weight.z;
        albedo*=tex;
        // Cotangent-frame detail normal from the material's height variation.
        float h=dot(tex,vec3(.2126,.7152,.0722));
        vec3 dx=dFdx(vWorldPosition),dy=dFdy(vWorldPosition);
        vec3 r1=cross(dy,N),r2=cross(N,dx);float det=dot(dx,r1);
        if(abs(det)>.000001)N=normalize(N-sign(det)*(dFdx(h)*r1+dFdy(h)*r2)*.12/max(abs(det),.000001));
    }
    albedo=pow(max(albedo,vec3(0)),vec3(2.2));
    vec3 point=uHeld==1?uCameraPosition:vWorldPosition+N*.001;
    vec3 indirect=vec3(.24,.32,.45)*uAmbient;
    float skyVisibility=1.;
    if(uHasIrradiance==1){vec4 lighting=irradiance(point);indirect=lighting.rgb;skyVisibility=lighting.a;}
    float sdfVisibility=1.;
    if(uTransportReady==1&&uHeld==0&&volumeInside(point)){
        float ao=0.;for(int i=1;i<=4;i++){float step=float(i)*.65;ao+=max(0.,step-sdf(point+N*step))/step*.09;}
        indirect*=1.-clamp(ao,0.,.5);
        vec3 tangent=normalize(abs(N.y)<.9?cross(N,vec3(0,1,0)):cross(N,vec3(1,0,0)));
        vec3 bitangent=cross(N,tangent);
        vec3 cones=cone(point+N*.6,N)+cone(point+N*.6,normalize(N+tangent))+cone(point+N*.6,normalize(N-tangent))
            +cone(point+N*.6,normalize(N+bitangent))+cone(point+N*.6,normalize(N-bitangent));
        indirect+=cones*.025+probeIrradiance(point,N)*.08;
        sdfVisibility=distanceShadow(point+N*.6,L);
    }
    vec3 V=uHeld==1?normalize(-vWorldPosition):normalize(uCameraPosition-vWorldPosition);
    float rough=uVertexColor==1?vSurface.y:.85;
    vec3 F=vec3(.04)+(1.-vec3(.04))*pow(1.-max(dot(N,V),0.),5.);
    vec3 reflection=textureLod(uEnvironment,reflect(-V,N),rough*7.).rgb;
    if(uProbeReady==1&&uHeld==0&&all(lessThan(abs(vWorldPosition-uProbeCenter),vec3(32)))){
        vec3 direction=reflect(-V,N),safe=sign(direction)*max(abs(direction),vec3(.0001));
        vec3 bounds=uProbeCenter+sign(direction)*32.;vec3 times=(bounds-vWorldPosition)/safe;
        float t=min(times.x,min(times.y,times.z));vec3 corrected=vWorldPosition+direction*t-uProbeCenter;
        float weight=1.-smoothstep(24.,32.,max(abs(vWorldPosition.x-uProbeCenter.x),max(abs(vWorldPosition.y-uProbeCenter.y),abs(vWorldPosition.z-uProbeCenter.z))));
        reflection=mix(reflection,textureLod(uReflectionProbe,corrected,rough*6.).rgb,weight);
    }
    float emission=uModelEmission+(uVertexColor==1?vSurface.x:0.);
    rough=clamp(rough,.08,1.);vec3 H=normalize(V+L);
    float NoL=max(dot(N,L),0.),NoV=max(dot(N,V),.001),NoH=max(dot(N,H),0.);
    float alpha=rough*rough,a2=alpha*alpha;
    float denom=NoH*NoH*(a2-1.)+1.;float D=a2/(PI*denom*denom);
    float k=(rough+1.)*(rough+1.)/8.;
    float G=NoV/(NoV*(1.-k)+k)*NoL/(NoL*(1.-k)+k);
    vec3 Fs=vec3(.04)+vec3(.96)*pow(1.-max(dot(H,V),0.),5.);
    vec3 brdf=(1.-Fs)*albedo/PI+D*G*Fs/max(.001,4.*NoL*NoV);
    vec3 solar=uPlanetLighting==1&&uHeld==0?atmosphereDirect(vWorldPosition-uAtmosphereWorldCamera):vec3(4.5,4.14,3.45)*uDaylight;
    vec3 color=albedo*indirect+brdf*solar*NoL*shadow(N,L)*sdfVisibility*skyVisibility
        +reflection*F*(1.-rough*.65)*skyVisibility+albedo*emission;
    if(uClusterReady==1&&uHeld==0){
        ivec3 cluster=ivec3(floor((vWorldPosition-uClusterOrigin)/8.));
        if(all(greaterThanEqual(cluster,ivec3(0)))&&all(lessThan(cluster,ivec3(12,16,12)))){
            ivec2 range=texelFetch(uClusters,cluster,0).rg;
            for(int i=0;i<32;i++){
                if(i>=range.y)break;int id=texelFetch(uLightIndices,range.x+i).r;
                vec4 light=texelFetch(uLights,id*2);vec3 delta=light.xyz-vWorldPosition;float d=length(delta);
                float attenuation=pow(clamp(1.-d/light.w,0.,1.),2.)/(1.+d*d);
                float visible=uTransportReady==1&&volumeInside(point)?pointShadow(point+N*.6,light.xyz):1.;
                color+=albedo*texelFetch(uLights,id*2+1).rgb*max(0.,dot(N,normalize(delta)))*attenuation*visible*3.;
            }
        }
    }
    if(uFog==1&&uHeld==0){
        vec3 delta=uWorldToPlanet*(vWorldPosition-uCameraPosition)*uBlockKm;
        float distance=length(delta);
        if(distance>1e-6){vec3 t,l;atmosphereIntegrate(uPlanetCamera,delta/distance,uAtmosphereSun,distance,8,t,l);color=color*t+l;}
    }

    if(uOutputTone==1){vec3 x=color*uOutputExposure;color=pow(clamp((x*(2.51*x+.03))/(x*(2.43*x+.59)+.14),0.,1.),vec3(1./2.2));}
    fragColor=vec4(color,1.-uTransparency);
}
