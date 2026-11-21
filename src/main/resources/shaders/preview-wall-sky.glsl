// A vertical sky ray misses daylight arriving from the open side of a wall.
// Trace a short horizontal segment in a packed sky column before using sky
// from its outer endpoint. Never carry light through a solid or unknown column.
float previewSkyPath(int header,vec3 from,vec3 to,bool underCornice,inout float clearance){
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
        if(from.y>=lower.y||(!underCornice&&compactCeiling))transmission*=materialTransmission;
        else if(underCornice && materialTransmission==0.)clearance=min(clearance,lower.y-from.y);
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
    float clearance=1e6;
    float path=previewSkyPath(skyHeader(first),point,outside,false,clearance);
    if(path>0.&&any(notEqual(first,last)))path*=previewSkyPath(skyHeader(last),point,outside,false,clearance);
    return max(overhead,openSky*path);
}

// Diffuse sky can reach the wall through an opening above a narrow courtyard,
// even if the opposite wing hides the horizon. Follow the wall normal past its
// lip, stopping at every opaque obstruction or unknown column.
float previewCourtyardSky(vec3 point,vec3 normal){
    if(uSkyColumns==0||max(abs(normal.x),abs(normal.z))<.999)return 0.;
    vec3 direction=abs(normal.x)>abs(normal.z)?vec3(sign(normal.x),0,0):vec3(0,0,sign(normal.z));
    ivec2 cell=ivec2(floor(point.xz));
    float coord=direction.x!=0.?point.x:point.z;
    float travel=direction.x+direction.z;
    float edge=travel>0.?floor(coord)+1.-coord:coord-floor(coord);
    float start=0.,transmission=1.,clearance=1e6;
    // Visit each crossed column once so that a pane multiplies transmission
    // once, even when a sample lies inside that pane.
    for(int i=0;i<5;i++){
        float end=min(edge,4.);
        int header=skyHeader(cell);
        transmission*=previewSkyPath(header,point+direction*start,point+direction*end,true,clearance);
        if(transmission==0.)break;
        float middle=(start+end)*.5;
        float sky=worldSkyVisibility(point+direction*middle);
        if(sky>0.){
            // The angle between the cornice lip and the horizontal bounds
            // the portion of sky visible underneath it. A high, shallow lip
            // leaves nearly the whole opening visible.
            float aperture=clearance/length(vec2(clearance,middle));
            return sky*transmission*aperture;
        }
        if(end>=4.)break;
        cell+=ivec2(direction.xz);
        start=edge;
        edge+=1.;
    }
    return 0.;
}

uniform vec3 uPreviewMin,uPreviewMax;
bool inPreviewBounds(vec3 point){
    return all(greaterThanEqual(point,uPreviewMin))&&all(lessThanEqual(point,uPreviewMax));
}
// Inclined views are baked with the landmark meshes and reused as the camera moves.
float previewInclinedExposure(int side,float u,float y,float depth,float height,float depthSpan,bool upward){
    float rise=.5*depthSpan;
    float v=(height*y+rise*depth)/(height+rise);
    ivec2 tile=textureSize(uShadow,0).xy/2;
    ivec2 local=clamp(ivec2(vec2(u,v)*vec2(tile)),ivec2(0),tile-1);
    ivec2 texel=ivec2(side%2,side/2)*tile+local;
    float first=texelFetch(uShadow,ivec3(texel,7),0).r;
    if(upward){
        // On a horizontal ledge the queried plane depth changes with atlas
        // row. Compare at the texel center used by the depth rasterizer.
        float centerV=(float(local.y)+.5)/float(tile.y);
        depth+=(centerV-v)*(height+rise)/rise;
    }
    return (depth-first)*depthSpan<=.002?1.:0.;
}
bool previewSunReachesLedge(vec3 point){
    // The last preview cascade includes the entire landmark, even when the
    // near shadow map only covers one wing. It can see an angled sun opening
    // between the four directions used to estimate diffuse sky.
    int layer=uShadowCascades-1;
    if(layer<0)return false;
    if(layer==0)return true; // The caller already has coverage from this map.
    vec4 projected=uShadowMatrix[layer]*vec4(point,1.);
    vec3 q=projected.xyz/projected.w*.5+.5;
    if(any(lessThan(q,vec3(.015)))||any(greaterThan(q,vec3(.985))))return false;
    ivec2 size=textureSize(uShadow,0).xy;
    vec2 slope=receiverDepthGradient(mat3(uShadowMatrix[layer]),vec3(0,1,0))/vec2(size);
    q.xy*=vec2(size);
    return shadowDepthGap(layer,q,slope,vec2(0),size)<=.00004;
}
vec2 previewLedgeVisibility(vec3 point,float overhead,float sunShadow,bool sunMapCovered){
    // Transparent roofs already carry an explicit sky transmission. The
    // depth views contain only opaque casters, so preserve that attenuation.
    if(overhead>0.||skyHeader(ivec2(floor(point.xz)))<0)return vec2(sunShadow*overhead,overhead);
    vec3 span=uPreviewMax-uPreviewMin,p=(point-uPreviewMin)/span;
    float east=previewInclinedExposure(0,1.-p.z,p.y,1.-p.x,span.y,span.x,true);
    float west=previewInclinedExposure(1,p.z,p.y,p.x,span.y,span.x,true);
    float south=previewInclinedExposure(2,p.x,p.y,1.-p.z,span.y,span.z,true);
    float north=previewInclinedExposure(3,1.-p.x,p.y,p.z,span.y,span.z,true);
    float opening=.25*(east+west+north+south);
    // An exterior opening admits diffuse illumination from its part of the sky.
    // The independent sun projection determines whether the solar disk is visible.
    bool sunPath=sunMapCovered && sunShadow>0. && previewSunReachesLedge(point);
    float sunGate=sunPath?1.:overhead;
    return vec2(sunGate*sunShadow,max(overhead,opening));
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
    return previewInclinedExposure(side,u,p.y,depth,span.y,depthSpan,false);
}
