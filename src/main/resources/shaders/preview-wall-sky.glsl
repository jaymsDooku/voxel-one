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

// A visible wall can receive diffuse daylight through the same opening that
// exposes it to the observer. The depth-tested sightline supplies a clear path
// even under a cornice where the vertical atlas has compacted out the room below.
// A covered sample supplies no bounce. Never sample past the camera.
float previewOpeningLight(vec3 point,vec3 normal,vec3 eye,float sky){
    if(sky>=1.||abs(normal.y)>.001||uSkyColumns==0)return 0.;
    vec3 ray=eye-point;
    float reach=length(ray);
    if(reach<=.001)return 0.;
    ray/=reach;
    float facing=max(0.,dot(ray,normal));
    float nearSky=worldSkyVisibility(point+ray*min(2.,reach));
    float middleSky=worldSkyVisibility(point+ray*min(4.,reach));
    float farSky=worldSkyVisibility(point+ray*min(8.,reach));
    return facing*(1.-sky)*dot(vec3(nearSky,middleSky,farSky),vec3(.5,.3,.2));
}
