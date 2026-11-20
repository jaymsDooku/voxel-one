// A vertical sky ray misses daylight arriving from the open side of a wall.
// Trace a short horizontal segment in a packed sky column before using sky
// from its outer endpoint. Never carry light through a solid or unknown column.
float previewSkyPath(int header,vec3 from,vec3 to){
    if(header<0)return 0.;
    int count=skyInt(header+3);
    if(count<0)return from.y>=intBitsToFloat(skyInt(header+2))?1.:0.;
    vec2 low=min(from.xz,to.xz),high=max(from.xz,to.xz);
    int start=uSkyOffset+skyInt(header+2);
    float transmission=1.;
    for(int i=0;i<128;i++){
        if(i>=count)break;
        int a=start+i*7;
        vec3 upper=vec3(intBitsToFloat(skyInt(a+3)),intBitsToFloat(skyInt(a+4)),intBitsToFloat(skyInt(a+5)));
        if(from.y>=upper.y)break;
        vec3 lower=vec3(intBitsToFloat(skyInt(a)),intBitsToFloat(skyInt(a+1)),intBitsToFloat(skyInt(a+2)));
        if(any(lessThan(high,lower.xz))||any(greaterThanEqual(low,upper.xz)))continue;
        float materialTransmission=intBitsToFloat(skyInt(a+6));
        // Geometry below the highest full opaque ceiling is compacted out.
        bool compactCeiling=materialTransmission==0.&&all(equal(upper.xz-lower.xz,vec2(1)));
        if(from.y>=lower.y||compactCeiling)transmission*=materialTransmission;
        if(transmission==0.)break;
    }
    return transmission;
}
float previewWallSky(vec3 point,vec3 normal,float overhead){
    if(overhead>=1.||max(abs(normal.x),abs(normal.z))<.999||uSkyColumns==0)return overhead;
    vec3 offset=abs(normal.x)>abs(normal.z)?vec3(sign(normal.x),0,0):vec3(0,0,sign(normal.z));
    vec3 outside=point+offset*.875;
    float openSky=worldSkyVisibility(outside);
    if(openSky<=overhead)return overhead;
    ivec2 first=ivec2(floor(point.xz)),last=ivec2(floor(outside.xz));
    float path=previewSkyPath(skyHeader(first),point,outside);
    if(path>0.&&any(notEqual(first,last)))path*=previewSkyPath(skyHeader(last),point,outside);
    return max(overhead,openSky*path);
}

uniform vec3 uPreviewMin,uPreviewMax;
bool inPreviewBounds(vec3 point){
    return all(greaterThanEqual(point,uPreviewMin))&&all(lessThanEqual(point,uPreviewMax));
}
// Each wall sees the exterior along its own normal if it is the nearest
// opaque surface in that orthographic side view. Depth is linear in world units.
float previewSideExposure(vec3 point,vec3 normal){
    if(abs(normal.y)>.001||!inPreviewBounds(point))return 0.;
    vec3 span=uPreviewMax-uPreviewMin;
    vec3 p=(point-uPreviewMin)/span;
    bool xWall=abs(normal.x)>.999;
    if(!xWall&&abs(normal.z)<.999)return 0.;
    bool positive=xWall?normal.x>0.:normal.z>0.;
    float u=xWall?p.z:p.x;
    if(xWall==positive)u=1.-u;
    int side=xWall?(positive?0:1):(positive?2:3);
    float depth=xWall?p.x:p.z;
    if(positive)depth=1.-depth;
    float first=textureLod(uShadow,vec3(u,p.y,float(3+side)),0.).r;
    // Less than a sixteenth-block leaf, independent of building scale.
    float depthSpan=xWall?span.x:span.z;
    if((depth-first)*depthSpan<=.002)return 1.;
    // The horizon can be blocked by an entire opposite wing in a courtyard
    // while the sky above that wing remains open. Match skyProjection's shear.
    float rise=.5*depthSpan;
    float v=(span.y*p.y+rise*depth)/(span.y+rise);
    ivec2 tile=textureSize(uShadow,0).xy/2;
    ivec2 local=clamp(ivec2(vec2(u,v)*vec2(tile)),ivec2(0),tile-1);
    ivec2 texel=ivec2(side%2,side/2)*tile+local;
    first=texelFetch(uShadow,ivec3(texel,7),0).r;
    return (depth-first)*depthSpan<=.002?1.:0.;
}
