// Shared nearest-cell / exact fractional-leaf sky visibility and local light.
uniform int uHasIrradiance;
uniform float uAmbient;
uniform vec3 uVolumeOrigin,uVolumeSize;
uniform sampler3D uIrradiance;
uniform isampler3D uFineRoots;
uniform isamplerBuffer uFineLight;
uniform int uSkyColumns,uSkyOffset;
int skyInt(int index){
    ivec3 size=textureSize(uFineRoots,0);
    return texelFetch(uFineRoots,ivec3(index%size.x,(index/size.x)%size.y,index/(size.x*size.y)),0).r;
}
float worldSkyVisibility(vec3 point){
    ivec2 column=ivec2(floor(point.xz));int low=0,high=uSkyColumns-1;
    for(int search=0;search<32&&low<=high;search++){
        int middle=(low+high)/2,header=uSkyOffset+middle*4;
        ivec2 key=ivec2(skyInt(header),skyInt(header+1));
        if(column.x<key.x||(column.x==key.x&&column.y<key.y)){high=middle-1;continue;}
        if(column.x>key.x||(column.x==key.x&&column.y>key.y)){low=middle+1;continue;}
        int count=skyInt(header+3);
        if(count<0)return point.y>=intBitsToFloat(skyInt(header+2))?1.:0.;
        int start=uSkyOffset+skyInt(header+2);float visible=1.;
        for(int i=0;i<128;i++){
            if(i>=count)break;int a=start+i*7;
            vec3 lower=vec3(intBitsToFloat(skyInt(a)),intBitsToFloat(skyInt(a+1)),intBitsToFloat(skyInt(a+2)));
            vec3 upper=vec3(intBitsToFloat(skyInt(a+3)),intBitsToFloat(skyInt(a+4)),intBitsToFloat(skyInt(a+5)));
            if(point.y>=upper.y)break; // Segments sorted from highest to lowest.
            if(all(greaterThanEqual(point.xz,lower.xz))&&all(lessThan(point.xz,upper.xz)))visible*=intBitsToFloat(skyInt(a+6));
            if(visible==0.)return 0.;
        }
        return visible;
    }
    return 0.; // Unknown geometry stays conservative; never open an unseen roof.
}
vec4 irradiance(vec3 point){
    vec3 local=point-uVolumeOrigin;
    if(uHasIrradiance==0||any(lessThan(local,vec3(0)))||any(greaterThanEqual(local,uVolumeSize)))return uPlanetLighting==1?vec4(vec3(0),worldSkyVisibility(point)):vec4(vec3(.24,.32,.45)*uAmbient,1.);
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
