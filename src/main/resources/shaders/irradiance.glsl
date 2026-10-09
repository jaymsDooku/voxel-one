// Shared nearest-cell / exact fractional-leaf sky visibility and local light.
uniform int uHasIrradiance;
uniform float uAmbient;
uniform vec3 uVolumeOrigin,uVolumeSize;
uniform sampler3D uIrradiance;
uniform isampler3D uFineRoots;
uniform isamplerBuffer uFineLight;
vec4 irradiance(vec3 point){
    vec3 local=point-uVolumeOrigin;
    if(any(lessThan(local,vec3(0)))||any(greaterThanEqual(local,uVolumeSize)))return uPlanetLighting==1?vec4(0):vec4(vec3(.24,.32,.45)*uAmbient,1.);
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
