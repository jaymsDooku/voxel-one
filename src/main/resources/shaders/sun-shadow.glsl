vec2 disk(int i,int count){float angle=float(i)*2.39996323;return vec2(cos(angle),sin(angle))*sqrt((float(i)+.5)/float(count));}
vec2 receiverDepthGradient(mat3 worldToShadow,vec3 normal){
    // Cofactors transform the geometric plane normal into orthographic shadow space.
    // Its plane equation gives the change in receiver depth per unit of shadow UV.
    vec3 plane=cross(worldToShadow[1],worldToShadow[2])*normal.x
              +cross(worldToShadow[2],worldToShadow[0])*normal.y
              +cross(worldToShadow[0],worldToShadow[1])*normal.z;
    if(abs(plane.z)<=1e-5*length(plane))return vec2(0.);
    return -plane.xy/plane.z;
}
float shadowDepthGap(int layer,vec3 receiver,vec2 gradient,vec2 offset,ivec2 size){
    ivec2 tap=clamp(ivec2(floor(receiver.xy+offset)),ivec2(0),size-1);
    vec2 center=vec2(tap)+.5;
    float expected=clamp(receiver.z+dot(center-receiver.xy,gradient),0.,1.);
    return expected-texelFetch(uShadow,ivec3(tap,layer),0).r;
}
float cascadeShadow(int layer,vec3 q,vec3 normal,float bias){
    ivec2 size=textureSize(uShadow,0).xy;
    vec2 gradient=receiverDepthGradient(mat3(uShadowMatrix[layer]),normal)/vec2(size);
    q.xy*=vec2(size);
    float gap=0.,count=0.;
    for(int i=0;i<8;i++){
        float depthGap=shadowDepthGap(layer,q,gradient,disk(i,8)*5.,size);
        if(depthGap>bias){gap+=depthGap;count++;}}
    if(count==0.)return 1.;
    gap/=count;
    float radius=clamp(gap/max(.001,q.z-gap)*70.,1.,8.);
    float visible=0.;for(int i=0;i<16;i++)
        visible+=shadowDepthGap(layer,q,gradient,disk(i,16)*radius,size)<=bias?1.:0.;
    return visible/16.;
}
float shadowAt(vec3 worldPoint,vec3 N,vec3 L,out bool covered){
    covered=false;
    if(uShadowEnabled==0||uHeld==1)return 1.;
    for(int layer=0;layer<uShadowCascades;layer++){
        vec4 p=uShadowMatrix[layer]*vec4(worldPoint,1);vec3 q=p.xyz/p.w*.5+.5;
        if(any(lessThan(q,vec3(.015)))||any(greaterThan(q,vec3(.985))))continue;
        covered=true;
        float bias=max(.00012*(1.-dot(N,L)),.00004);
        float result=cascadeShadow(layer,q,N,bias);
        float edge=max(abs(q.x-.5),abs(q.y-.5))*2.;
        if(layer<uShadowCascades-1&&edge>.8){vec4 p2=uShadowMatrix[layer+1]*vec4(worldPoint,1);vec3 q2=p2.xyz/p2.w*.5+.5;
            result=mix(result,cascadeShadow(layer+1,q2,N,bias),smoothstep(.8,.97,edge));}
        return result;
    }
    return 1.;
}
float shadowAt(vec3 worldPoint,vec3 N,vec3 L){
    bool covered;
    return shadowAt(worldPoint,N,L,covered);
}
